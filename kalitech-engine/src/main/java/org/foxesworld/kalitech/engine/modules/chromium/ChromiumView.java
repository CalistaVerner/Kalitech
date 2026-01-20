package org.foxesworld.kalitech.engine.modules.chromium;

import org.cef.CefClient;
import org.cef.browser.CefBrowser;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import com.jme3.texture.Texture2D;

/**
 * One OSR browser instance producing BGRA frames into a jME texture.
 */
public final class ChromiumView implements AutoCloseable {

    private final ChromiumService service;
    private final ChromiumConfig cfg;

    private final CefBrowser browser;

    private final JmeBufferUtilsTextureFactory factory = new JmeBufferUtilsTextureFactory();
    private final ChromiumPixelBuffer pixelBuffer;
    private final ChromiumOsrPaintSink paintSink;
    private final ChromiumJmeTextureBridge bridge;

    private final Texture2D texture;

    private final AtomicInteger fpsCounter = new AtomicInteger();
    private volatile long lastFpsMs;

    ChromiumView(ChromiumService service, CefClient client, ChromiumConfig cfg, String url) {
        this.service = Objects.requireNonNull(service, "service");
        this.cfg = Objects.requireNonNull(cfg, "cfg");
        Objects.requireNonNull(client, "client");

        this.pixelBuffer = new ChromiumPixelBuffer(cfg.width, cfg.height, factory);
        this.paintSink = new ChromiumOsrPaintSink(pixelBuffer, factory);
        this.bridge = new ChromiumJmeTextureBridge(pixelBuffer);
        this.texture = bridge.initOrRecreate(factory);

        this.browser = createBrowserOnAwt(client, url);

        // Attach paint hook AFTER browser creation but BEFORE it starts producing frames.
        CefPaintHook.attach(browser, (ByteBuffer bgra, int w, int h) -> {
            paintSink.onPaint(bgra, w, h);

            int f = fpsCounter.incrementAndGet();
            long now = System.currentTimeMillis();
            long last = lastFpsMs;
            if (last == 0L) {
                lastFpsMs = now;
            } else if (now - last >= 1000L) {
                System.out.println("[chromium][osr] fps=" + f + " size=" + w + "x" + h);
                fpsCounter.set(0);
                lastFpsMs = now;
            }
        });

        // Force first paint
        service.runOnAwt(() -> {
            tryInvoke(browser, "setWindowlessFrameRate", new Class<?>[]{int.class}, new Object[]{cfg.fps});
            tryInvoke(browser, "createImmediately", new Class<?>[]{}, new Object[]{});
            tryInvoke(browser, "invalidate", new Class<?>[]{}, new Object[]{});
        });
    }

    public CefBrowser browser() {
        return browser;
    }

    public Texture2D texture() {
        return texture;
    }

    /**
     * Must be called from jME render/update thread.
     */
    public void updateTexture() {
        bridge.updateIfDirty();
    }

    public void loadHtml(String html, String baseUrl) {
        Objects.requireNonNull(html, "html");
        Objects.requireNonNull(baseUrl, "baseUrl");
        service.runOnAwt(() -> browser.loadURL(html));
    }

    public void loadUrl(String url) {
        Objects.requireNonNull(url, "url");
        service.runOnAwt(() -> browser.loadURL(url));
    }

    @Override
    public void close() {
        service.runOnAwt(() -> {
            try {
                browser.close(true);
            } catch (Exception ignored) {
            }
        });
    }

    private CefBrowser createBrowserOnAwt(CefClient client, String url) {
        final CefBrowser[] out = new CefBrowser[1];
        service.runOnAwtAndWait(() -> {
            boolean osr = true;
            boolean transparent = cfg.transparent;
            CefBrowser b = client.createBrowser(url, osr, transparent);
            out[0] = b;
            // Important: OSR often needs this to start producing frames
            b.createImmediately();
            try {
                b.setWindowlessFrameRate(cfg.fps);
            } catch (Throwable ignored) {
            }
        });
        return out[0];
    }

    private static void tryInvoke(Object target, String name, Class<?>[] types, Object[] args) {
        try {
            var m = target.getClass().getMethod(name, types);
            m.setAccessible(true);
            m.invoke(target, args);
        } catch (Throwable ignored) {
        }
    }
}