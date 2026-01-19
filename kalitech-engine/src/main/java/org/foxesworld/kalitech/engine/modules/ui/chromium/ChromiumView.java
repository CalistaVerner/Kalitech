package org.foxesworld.kalitech.engine.modules.ui.chromium;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cef.CefClient;
import org.cef.browser.CefBrowser;
import org.cef.handler.CefRenderHandlerAdapter;
import org.cef.handler.CefScreenInfo;

import java.awt.Point;
import java.awt.Rectangle;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ChromiumView
 *
 * OSR view backed by double-buffered RGBA frames.
 *
 * Important notes for your JCEF build:
 * - CefClient does NOT expose addRenderHandler()/setRenderHandler()
 * - We must bind CefRenderHandler via CefBrowser internal API (CefOsrBinder)
 * - Do NOT call browser.createImmediately() (may deadlock EDT)
 * - Do NOT block the render thread while EDT creates the browser
 */
public final class ChromiumView {
    private static final Logger log = LogManager.getLogger(ChromiumView.class);

    private final ChromiumService service;

    private long paintCount = 0;
    private volatile CefClient client;
    private volatile CefBrowser browser;

    private volatile int width;
    private volatile int height;

    // Double-buffer: CEF paints -> writeBuf, JME reads -> readBuf
    private volatile ByteBuffer readBuf;
    private volatile ByteBuffer writeBuf;

    private final Object swapLock = new Object();
    private final AtomicBoolean dirty = new AtomicBoolean(false);

    private final OsrRenderHandler handler;

    public ChromiumView(ChromiumService service, int width, int height, String initialUrl, boolean transparent) {
        this.service = Objects.requireNonNull(service, "service");
        if (service.app() == null) throw new IllegalStateException("CEF not ready (service.app()==null)");

        this.width = Math.max(1, width);
        this.height = Math.max(1, height);

        this.handler = new OsrRenderHandler();
        ensureBuffers(this.width, this.height);

        // Async init on EDT: never block render thread
        service.runOnAwt(() -> {
            try {
                CefClient c = service.app().createClient();
                CefBrowser b = c.createBrowser(initialUrl, true, transparent);
                // DO NOT call b.createImmediately();

                // Bind render handler via legacy/internal setters
                boolean ok = CefOsrBinder.ensureRenderHandler(b, handler);
                if (!ok) {
                    try { b.close(true); } catch (Throwable ignored) {}
                    try { c.dispose(); } catch (Throwable ignored) {}
                    throw new IllegalStateException(
                            "OSR is not supported by this JCEF build: failed to bind CefRenderHandler to CefBrowser"
                    );
                }

                this.client = c;
                this.browser = b;

                log.info("[chromium] browser created (OSR bound) url={} size={}x{}", initialUrl, this.width, this.height);
            } catch (Throwable t) {
                log.error("[chromium] browser init failed", t);
            }
        });
    }

    public boolean isReady() {
        return browser != null && client != null;
    }

    public CefBrowser browser() {
        return browser;
    }

    public void loadUrl(String url) {
        if (url == null || url.isBlank()) return;
        service.runOnAwt(() -> {
            try {
                CefBrowser b = browser;
                if (b != null) b.loadURL(url);
            } catch (Throwable t) {
                log.warn("[chromium] loadUrl failed", t);
            }
        });
    }

    public int width() { return width; }
    public int height() { return height; }

    public boolean isDirty() { return dirty.get(); }
    public void clearDirty() { dirty.set(false); }

    /** Optional legacy access */
    public ByteBuffer rgbaBuffer() { return readBuf; }

    public boolean consumeFrameTo(ByteBuffer dst, int expectedBytes) {
        if (!dirty.get()) return false;
        if (dst == null) return false;

        synchronized (swapLock) {
            if (!dirty.get()) return false;

            ByteBuffer src = readBuf;
            if (src == null) return false;
            if (src.capacity() < expectedBytes) return false;
            if (dst.capacity() < expectedBytes) return false;

            ByteBuffer srcDup = src.duplicate();
            srcDup.position(0).limit(expectedBytes);

            dst.position(0);
            dst.put(srcDup);
            dst.position(0);

            dirty.set(false);
            return true;
        }
    }

    public void resize(int w, int h) {
        int nw = Math.max(1, w);
        int nh = Math.max(1, h);
        if (nw == width && nh == height) return;

        width = nw;
        height = nh;
        ensureBuffers(nw, nh);
        // no wasResized() in your build -> rely on getViewRect()
    }

    public void dispose() {
        service.runOnAwt(() -> {
            try {
                CefBrowser b = browser;
                if (b != null) b.close(true);
            } catch (Throwable ignored) {}

            try {
                CefClient c = client;
                if (c != null) c.dispose();
            } catch (Throwable ignored) {}

            browser = null;
            client = null;
        });
    }

    private void ensureBuffers(int w, int h) {
        int cap = w * h * 4;
        synchronized (swapLock) {
            if (readBuf == null || readBuf.capacity() < cap) readBuf = ByteBuffer.allocateDirect(cap);
            if (writeBuf == null || writeBuf.capacity() < cap) writeBuf = ByteBuffer.allocateDirect(cap);
        }
    }

    private void swapBuffersUnsafe() {
        ByteBuffer tmp = readBuf;
        readBuf = writeBuf;
        writeBuf = tmp;
    }

    private final class OsrRenderHandler extends CefRenderHandlerAdapter {

        @Override
        public Rectangle getViewRect(CefBrowser browser) {
            return new Rectangle(0, 0, width, height);
        }

        @Override
        public boolean getScreenInfo(CefBrowser browser, CefScreenInfo screenInfo) {
            Rectangle rect = new Rectangle(0, 0, width, height);
            screenInfo.Set(1.0, 32, 8, false, rect, rect);
            return true;
        }

        @Override
        public Point getScreenPoint(CefBrowser browser, Point viewPoint) {
            return viewPoint;
        }

        @Override
        public void onPaint(CefBrowser browser, boolean popup, Rectangle[] dirtyRects, ByteBuffer buffer, int w, int h) {

            paintCount++;
            if (paintCount == 1 || (paintCount % 60) == 0) {
                log.info("[chromium] onPaint count={} size={}x{} popup={} dirtyRects={}",
                        paintCount, w, h, popup, (dirtyRects != null ? dirtyRects.length : -1));
            }
            try {
                if (w <= 0 || h <= 0) return;

                if (w != width || h != height) {
                    width = w;
                    height = h;
                    ensureBuffers(w, h);
                }

                synchronized (swapLock) {
                    final int pixels = w * h;
                    int src = 0;
                    int dst = 0;

                    // BGRA -> RGBA
                    for (int i = 0; i < pixels; i++) {
                        byte b = buffer.get(src);
                        byte g = buffer.get(src + 1);
                        byte r = buffer.get(src + 2);
                        byte a = buffer.get(src + 3);

                        writeBuf.put(dst, r);
                        writeBuf.put(dst + 1, g);
                        writeBuf.put(dst + 2, b);
                        writeBuf.put(dst + 3, a);

                        src += 4;
                        dst += 4;
                    }

                    swapBuffersUnsafe();
                    dirty.set(true);
                }
            } catch (Throwable t) {
                log.error("[chromium] onPaint failed", t);
            }
        }
    }
}