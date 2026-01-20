package org.foxesworld.kalitech.engine.modules.chromium;

import me.friwi.jcefmaven.CefAppBuilder;
import me.friwi.jcefmaven.CefInitializationException;
import me.friwi.jcefmaven.MavenCefAppHandlerAdapter;
import me.friwi.jcefmaven.UnsupportedPlatformException;
import me.friwi.jcefmaven.impl.progress.ConsoleProgressHandler;
import org.cef.CefApp;
import org.cef.CefClient;
import org.cef.CefSettings;

import java.awt.EventQueue;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Owns CEF lifecycle and pumps external message loop from the game thread.
 */
public final class ChromiumService implements AutoCloseable {

    private final ChromiumConfig cfg;

    private final AtomicReference<CefApp> appRef = new AtomicReference<>();
    private final AtomicReference<CefClient> clientRef = new AtomicReference<>();
    private final AtomicBoolean started = new AtomicBoolean(false);

    public ChromiumService(ChromiumConfig cfg) {
        this.cfg = Objects.requireNonNull(cfg, "cfg");
    }

    public void init() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        runOnAwtAndWait(() -> {
            if (appRef.get() != null) {
                return;
            }

            CefAppBuilder builder = new CefAppBuilder();

            Path dir = cfg.installDir != null
                    ? cfg.installDir
                    : Path.of(System.getProperty("user.home"), "AppData", "Local", "kalitech-jcef");

            builder.setInstallDir(dir.toFile());
            builder.setProgressHandler(new ConsoleProgressHandler());
            builder.setAppHandler(new MavenCefAppHandlerAdapter() {
                @Override
                public void stateHasChanged(CefApp.CefAppState state) {
                    System.out.println("[chromium] state=" + state);
                }
            });

            CefSettings s = builder.getCefSettings();
            s.windowless_rendering_enabled = true;



            if (!cfg.transparent) {
                s.background_color = s.new ColorType(0, 0, 0, 255);
            }

            if (cfg.disableGpu) {
                builder.addJcefArgs("--disable-gpu");
                builder.addJcefArgs("--disable-gpu-compositing");
                builder.addJcefArgs("--disable-software-rasterizer");
            }

            for (String a : cfg.extraArgs) {
                builder.addJcefArgs(a);
            }

            CefApp app = null;
            try {
                app = builder.build();
            } catch (IOException e) {
                throw new RuntimeException(e);
            } catch (UnsupportedPlatformException e) {
                throw new RuntimeException(e);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } catch (CefInitializationException e) {
                throw new RuntimeException(e);
            }
            appRef.set(app);

            CefClient client = app.createClient();
            clientRef.set(client);

            System.out.println("[chromium] ready installDir=" + dir);
        });
    }

    public ChromiumView createView(String url) {
        CefClient client = clientRef.get();
        if (client == null) {
            throw new IllegalStateException("ChromiumService not initialized");
        }
        return new ChromiumView(this, client, cfg, url);
    }

    /**
     * Pump CEF. Call from jME update thread every frame.
     */
    public void update() {
        CefApp app = appRef.get();
        if (app == null) {
            return;
        }
        // Do NOT call from AWT thread; pump from game thread.
        try {
            app.doMessageLoopWork(0);
        } catch (Throwable t) {
            // Keep running; errors here will be visible in console anyway.
        }
    }

    @Override
    public void close() {
        CefApp app = appRef.getAndSet(null);
        CefClient client = clientRef.getAndSet(null);

        if (client != null) {
            // client is owned by app, but close it early to reduce surprises
        }

        if (app != null) {
            runOnAwtAndWait(app::dispose);
        }
    }

    void runOnAwt(Runnable r) {
        if (EventQueue.isDispatchThread()) {
            r.run();
            return;
        }
        EventQueue.invokeLater(r);
    }

    void runOnAwtAndWait(Runnable r) {
        try {
            if (EventQueue.isDispatchThread()) {
                r.run();
                return;
            }
            EventQueue.invokeAndWait(r);
        } catch (Exception e) {
            throw new RuntimeException("Failed to run on AWT EDT", e);
        }
    }
}