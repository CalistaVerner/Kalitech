package org.foxesworld.kalitech.engine.modules.ui.chromium;

import me.friwi.jcefmaven.CefAppBuilder;
import me.friwi.jcefmaven.MavenCefAppHandlerAdapter;
import me.friwi.jcefmaven.impl.progress.ConsoleProgressHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cef.CefApp;
import org.cef.CefSettings;

import java.awt.EventQueue;
import java.io.File;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * ChromiumService
 *
 * Owns CEF lifecycle and provides a strict AWT-EDT executor for JCEF operations.
 */
public final class ChromiumService {
    private static final Logger log = LogManager.getLogger(ChromiumService.class);

    private final File installDir;
    private final AtomicReference<CefApp> appRef = new AtomicReference<>();
    private CompletableFuture<CefApp> startFuture;

    public ChromiumService(File installDir) {
        this.installDir = Objects.requireNonNull(installDir, "installDir");
    }

    public CefApp app() {
        return appRef.get();
    }

    public boolean isReady() {
        return appRef.get() != null;
    }

    /**
     * Start CEF. Safe to call multiple times.
     * CEF init is forced onto AWT EDT.
     */
    public synchronized CompletableFuture<CefApp> startAsync() {
        if (startFuture != null) return startFuture;

        startFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return callOnAwtAndWait(this::startOnAwt);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        return startFuture;
    }

    /**
     * Execute callable on AWT EDT and wait for result.
     */
    public <T> T callOnAwtAndWait(CheckedCallable<T> c) {
        Objects.requireNonNull(c, "callable");
        try {
            if (EventQueue.isDispatchThread()) {
                return c.call();
            }

            final Object lock = new Object();
            final AtomicReference<T> out = new AtomicReference<>();
            final AtomicReference<Throwable> err = new AtomicReference<>();
            final java.util.concurrent.atomic.AtomicBoolean done = new java.util.concurrent.atomic.AtomicBoolean(false);

            EventQueue.invokeLater(() -> {
                try {
                    out.set(c.call());
                } catch (Throwable t) {
                    err.set(t);
                } finally {
                    done.set(true);
                    synchronized (lock) {
                        lock.notifyAll();
                    }
                }
            });

            synchronized (lock) {
                while (!done.get()) {
                    lock.wait();
                }
            }

            if (err.get() != null) {
                Throwable t = err.get();
                if (t instanceof RuntimeException re) throw re;
                throw new RuntimeException(t);
            }
            return out.get();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for AWT EDT", ie);
        } catch (Exception e) {
            throw (e instanceof RuntimeException re) ? re : new RuntimeException(e);
        }
    }


    /**
     * Execute runnable on AWT EDT (fire-and-forget).
     */
    public void runOnAwt(Runnable r) {
        Objects.requireNonNull(r, "runnable");
        if (EventQueue.isDispatchThread()) {
            r.run();
            return;
        }
        EventQueue.invokeLater(r);
    }

    private CefApp startOnAwt() {
        CefApp existing = appRef.get();
        if (existing != null) return existing;

        try {
            installDir.mkdirs();

            CefAppBuilder builder = new CefAppBuilder();
            builder.setInstallDir(installDir);
            builder.setProgressHandler(new ConsoleProgressHandler());
            builder.setAppHandler(new MavenCefAppHandlerAdapter() {
                @Override
                public void stateHasChanged(org.cef.CefApp.CefAppState state) {
                    log.info("[chromium] state={}", state);
                }
            });

            CefSettings settings = builder.getCefSettings();
            settings.windowless_rendering_enabled = true;
            settings.background_color = settings.new ColorType(0, 0, 0, 0);

            CefApp cefApp = builder.build();
            if (cefApp == null) {
                throw new IllegalStateException("CEF startup failed: builder.build() returned null");
            }

            appRef.set(cefApp);
            log.info("[chromium] CefApp ready dir={}", installDir.getAbsolutePath());
            return cefApp;
        } catch (Throwable t) {
            log.error("[chromium] CEF init failed", t);
            throw (t instanceof RuntimeException) ? (RuntimeException) t : new RuntimeException(t);
        }
    }

    public synchronized void dispose() {
        CefApp a = appRef.getAndSet(null);
        if (a != null) {
            try {
                callOnAwtAndWait(() -> {
                    a.dispose();
                    return null;
                });
            } catch (Throwable t) {
                log.warn("[chromium] dispose failed", t);
            }
        }
    }

    @FunctionalInterface
    public interface CheckedCallable<T> {
        T call() throws Exception;
    }
}