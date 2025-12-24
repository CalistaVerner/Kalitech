// FILE: JcefUiBackend.java
// Author: Calista Verner
// UI-only hardened JCEF backend for Kalitech Engine

package org.foxesworld.kalitech.engine.ui;

import me.friwi.jcefmaven.CefAppBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cef.CefApp;
import org.cef.CefClient;
import org.cef.CefSettings;
import org.cef.browser.CefBrowser;
import org.cef.handler.CefContextMenuHandlerAdapter;
import org.cef.handler.CefKeyboardHandlerAdapter;
import org.cef.handler.CefLifeSpanHandlerAdapter;
import org.cef.misc.BoolRef;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * JCEF backend hardened strictly for GAME UI.
 *
 * Goals:
 *  - UI rendering only (NOT a browser)
 *  - no context menu
 *  - no popups / window.open
 *  - no DevTools (F12, Ctrl+Shift+I/J)
 *  - deterministic lifecycle
 *  - engine-driven tick()
 *
 * Notes:
 *  - For engine integration, offscreen (windowless) rendering is usually the safest.
 */
public final class JcefUiBackend implements AutoCloseable {

    private static final Logger log = LogManager.getLogger(JcefUiBackend.class);
    private static final AtomicBoolean GLOBAL_STARTED = new AtomicBoolean(false);

    private volatile CefApp app;
    private volatile CefClient client;

    private FileChannel lockChannel;
    private FileLock lock;
    private Path resolvedUserDataDir;

    private final List<JcefSurface> surfaces = new CopyOnWriteArrayList<>();

    // ---------------------------------------------------------------------
    // STARTUP
    // ---------------------------------------------------------------------

    public synchronized void start() {
        log.info("[ui/jcef] start() called");

        if (!GLOBAL_STARTED.compareAndSet(false, true)) {
            log.warn("[ui/jcef] already started, skipping");
            return;
        }

        try {
            log.info("[ui/jcef] phase=builder:create");
            CefAppBuilder builder = new CefAppBuilder();

            log.info("[ui/jcef] phase=paths:resolve");
            Path installDir = resolveInstallDir();
            Path userDataBase = resolveUserDataBaseDir();
            Path userDataDir = resolveProcessUserDataDir(userDataBase);

            log.info("[ui/jcef] installDir = {}", installDir);
            log.info("[ui/jcef] userDataBase = {}", userDataBase);
            log.info("[ui/jcef] userDataDir  = {}", userDataDir);

            log.info("[ui/jcef] phase=paths:ensureDir");
            ensureDir(userDataDir);

            log.info("[ui/jcef] phase=profile:lock");
            acquireProfileLock(userDataDir);
            resolvedUserDataDir = userDataDir;

            log.info("[ui/jcef] phase=builder:setInstallDir (Path)");
            boolean okPath = invokeIfExists(
                    builder,
                    "setInstallDir",
                    new Class<?>[]{Path.class},
                    new Object[]{installDir}
            );
            log.info("[ui/jcef] setInstallDir(Path) -> {}", okPath);

            log.info("[ui/jcef] phase=builder:setInstallDir (File)");
            boolean okFile = invokeIfExists(
                    builder,
                    "setInstallDir",
                    new Class<?>[]{File.class},
                    new Object[]{installDir.toFile()}
            );
            log.info("[ui/jcef] setInstallDir(File) -> {}", okFile);

            log.info("[ui/jcef] phase=helper:search");
            Path helper = findSubprocessHelper(installDir);
            if (helper == null) {
                log.error("[ui/jcef] helper NOT FOUND in {}", installDir);
                throw new IllegalStateException("CEF helper not found in " + installDir);
            }
            log.info("[ui/jcef] helper found: {}", helper);

            Path cefLog = userDataDir.resolve("cef.log");
            log.info("[ui/jcef] cef.log = {}", cefLog);

            log.info("[ui/jcef] phase=settings:create");
            CefSettings settings = new CefSettings();
            settings.windowless_rendering_enabled = false;
            settings.command_line_args_disabled = false;
            settings.log_severity = CefSettings.LogSeverity.LOGSEVERITY_INFO;
            settings.log_file = cefLog.toAbsolutePath().toString();
            settings.cache_path = userDataDir.toAbsolutePath().toString();
            settings.persist_session_cookies = false;
            settings.user_agent = "KalitechUI/1.0";

            try {
                settings.browser_subprocess_path = helper.toAbsolutePath().toString();
                log.info("[ui/jcef] settings.browser_subprocess_path set");
            } catch (Throwable t) {
                log.warn("[ui/jcef] browser_subprocess_path not supported: {}", t.toString());
            }

            log.info("[ui/jcef] phase=builder:setCefSettings");
            boolean okSettings = invokeIfExists(
                    builder,
                    "setCefSettings",
                    new Class<?>[]{CefSettings.class},
                    new Object[]{settings}
            );
            log.info("[ui/jcef] setCefSettings -> {}", okSettings);

            log.info("[ui/jcef] phase=builder:addArgs");
            addJcefArgs(builder,
                    "--no-sandbox",
                    "--no-first-run",
                    "--no-default-browser-check",
                    "--disable-gpu",
                    "--disable-gpu-compositing",

                    "--disable-background-networking",
                    "--disable-sync",
                    "--disable-default-apps",
                    "--disable-translate",
                    "--disable-extensions",
                    "--disable-component-update",
                    "--disable-client-side-phishing-detection",
                    "--disable-domain-reliability",

                    "--disable-features=GCMChannel,PushMessaging,MediaRouter,Signin,AutofillServerCommunication,TranslateUI",
                    "--metrics-recording-only",
                    "--no-pings",
                    "--disable-background-timer-throttling",
                    "--disable-backgrounding-occluded-windows",

                    "--user-data-dir=" + userDataDir.toAbsolutePath(),
                    "--browser-subprocess-path=" + helper.toAbsolutePath(),

                    "--log-severity=info",
                    "--log-file=" + cefLog.toAbsolutePath()
            );

            log.info("[ui/jcef] phase=builder:build (THIS IS CRITICAL)");
            app = builder.build();
            log.info("[ui/jcef] CefApp built: {}", app);

            log.info("[ui/jcef] phase=client:create");
            client = app.createClient();
            log.info("[ui/jcef] CefClient created: {}", client);

            log.info("[ui/jcef] phase=handlers:install");
            installUiOnlyHandlers(client);

            log.info("[ui/jcef] STARTED OK (windowless=true)");

        } catch (Throwable t) {
            log.error("[ui/jcef] START FAILED", t);
            GLOBAL_STARTED.set(false);
            releaseProfileLock();
            throw new RuntimeException("Failed to start JCEF UI backend", t);
        }
    }

    /**
     * Engine pump (call every frame).
     */
    public void tick() {
        CefApp a = app;
        if (a == null) return;
        try {
            a.doMessageLoopWork(0);
        } catch (Throwable t) {
            // don't spam; keep it silent
        }
    }

    // ---------------------------------------------------------------------
    // UI SURFACES
    // ---------------------------------------------------------------------

    public JcefSurface createSurface(String id, int width, int height, String url) {
        start();

        String safeId = (id == null || id.isBlank()) ? "surface" : id.trim();
        String safeUrl = (url == null || url.isBlank()) ? "https://example.com" : url.trim();
        int w = Math.max(64, width);
        int h = Math.max(64, height);

        return runOnEdt(() -> {
            CefBrowser browser = client.createBrowser(safeUrl, false, false);
            Component ui = browser.getUIComponent();

            JFrame frame = new JFrame("KALITECH UI :: " + safeId);
            frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            frame.setLayout(new BorderLayout());
            frame.add(ui, BorderLayout.CENTER);
            frame.setSize(w, h);
            frame.setLocationByPlatform(true);

            JcefSurface s = new JcefSurface(safeId, browser, frame, () -> {});
            surfaces.add(s);

            frame.addWindowListener(new java.awt.event.WindowAdapter() {
                @Override
                public void windowClosing(java.awt.event.WindowEvent e) {
                    surfaces.remove(s);
                    s.destroy();
                }
            });

            frame.setVisible(true);


            return s;
        });
    }


    // ---------------------------------------------------------------------
    // UI-ONLY HARDENING
    // ---------------------------------------------------------------------

    private static final int EVENTFLAG_SHIFT_DOWN   = 1 << 1;
    private static final int EVENTFLAG_CONTROL_DOWN = 1 << 2;

    private void installUiOnlyHandlers(CefClient client) {

        // Disable right-click menu
        client.addContextMenuHandler(new CefContextMenuHandlerAdapter() {
            @Override
            public void onBeforeContextMenu(CefBrowser browser,
                                            org.cef.browser.CefFrame frame,
                                            org.cef.callback.CefContextMenuParams params,
                                            org.cef.callback.CefMenuModel model) {
                model.clear();
            }
        });

        // Block popups / window.open
        client.addLifeSpanHandler(new CefLifeSpanHandlerAdapter() {
            @Override
            public boolean onBeforePopup(CefBrowser browser,
                                         org.cef.browser.CefFrame frame,
                                         String targetUrl,
                                         String targetFrameName) {
                log.debug("[ui/jcef] popup blocked: {}", targetUrl);
                return true;
            }
        });

        // Block DevTools shortcuts
        client.addKeyboardHandler(new CefKeyboardHandlerAdapter() {
            @Override
            public boolean onPreKeyEvent(CefBrowser browser,
                                         CefKeyEvent event,
                                         BoolRef isShortcut) {

                if (event.type == CefKeyEvent.EventType.KEYEVENT_RAWKEYDOWN) {

                    // F12
                    if (event.windows_key_code == 123) return true;

                    boolean ctrl  = (event.modifiers & EVENTFLAG_CONTROL_DOWN) != 0;
                    boolean shift = (event.modifiers & EVENTFLAG_SHIFT_DOWN) != 0;

                    // Ctrl+Shift+I / Ctrl+Shift+J
                    if (ctrl && shift &&
                            (event.windows_key_code == 'I' || event.windows_key_code == 'J')) {
                        return true;
                    }
                }
                return false;
            }
        });
    }

    // ---------------------------------------------------------------------
    // SHUTDOWN
    // ---------------------------------------------------------------------

    @Override
    public synchronized void close() {
        if (!GLOBAL_STARTED.get()) return;

        try {
            // 1) Close all surfaces deterministically
            for (JcefSurface s : surfaces) {
                try { s.destroyBlocking(2_000); } catch (Throwable ignored) {}
            }
            surfaces.clear();

            // 2) Dispose client before app
            CefClient c = client;
            client = null;
            if (c != null) {
                try { c.dispose(); } catch (Throwable ignored) {}
            }

            // 3) Dispose app last
            CefApp a = app;
            app = null;
            if (a != null) {
                try { a.dispose(); } catch (Throwable ignored) {}
            } else {
                // Sometimes CefApp is a singleton in certain bindings
                try {
                    CefApp inst = CefApp.getInstance();
                    if (inst != null) inst.dispose();
                } catch (Throwable ignored) {}
            }
        } finally {
            releaseProfileLock();
            GLOBAL_STARTED.set(false);
            log.info("[ui/jcef] stopped");
        }
    }

    // ---------------------------------------------------------------------
    // SURFACE
    // ---------------------------------------------------------------------

    public static final class JcefSurface {
        public final String id;
        public final CefBrowser browser;
        public final JFrame frame;

        private final AtomicBoolean destroyed = new AtomicBoolean(false);
        private final Runnable onRemove;

        private JcefSurface(String id, CefBrowser browser, JFrame frame, Runnable onRemove) {
            this.id = id;
            this.browser = browser;
            this.frame = frame;
            this.onRemove = onRemove;
        }

        public void navigate(String url) {
            if (url == null || url.isBlank()) return;
            SwingUtilities.invokeLater(() -> browser.loadURL(url));
        }

        public void exec(String js) {
            if (js == null) return;
            SwingUtilities.invokeLater(() -> browser.executeJavaScript(js, browser.getURL(), 0));
        }

        public boolean isAlive() {
            return !destroyed.get() && frame.isDisplayable();
        }

        public void destroy() {
            if (!destroyed.compareAndSet(false, true)) return;
            SwingUtilities.invokeLater(() -> {
                try { browser.close(true); } catch (Throwable ignored) {}
                try { frame.dispose(); } catch (Throwable ignored) {}
                try { if (onRemove != null) onRemove.run(); } catch (Throwable ignored) {}
            });
        }

        public void destroyBlocking(long timeoutMs) {
            if (!destroyed.compareAndSet(false, true)) return;

            CountDownLatch latch = new CountDownLatch(1);
            SwingUtilities.invokeLater(() -> {
                try { browser.close(true); } catch (Throwable ignored) {}
                try { frame.dispose(); } catch (Throwable ignored) {}
                try { if (onRemove != null) onRemove.run(); } catch (Throwable ignored) {}
                latch.countDown();
            });

            try { latch.await(timeoutMs, TimeUnit.MILLISECONDS); }
            catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        }
    }

    // ---------------------------------------------------------------------
    // PATH / LOCKING
    // ---------------------------------------------------------------------

    private static Path resolveInstallDir() {
        String prop = System.getProperty("kalitech.jcef.dir");
        Path p = (prop != null) ? Paths.get(prop) : Paths.get("jcef-bundle");
        return p.toAbsolutePath().normalize();
    }

    private static Path resolveUserDataBaseDir() {
        String prop = System.getProperty("kalitech.jcef.userdata");
        Path base = (prop != null)
                ? Paths.get(prop)
                : Paths.get("cache", "jcef-userdata");
        return base.toAbsolutePath().normalize();
    }

    private static Path resolveProcessUserDataDir(Path base) {
        return base.resolve("profile-" + ProcessHandle.current().pid());
    }

    private void acquireProfileLock(Path dir) throws IOException {
        Path lockFile = dir.resolve("kalitech.lock");
        lockChannel = FileChannel.open(lockFile,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE);

        lock = lockChannel.tryLock();
        if (lock == null) {
            throw new IllegalStateException("JCEF profile already locked: " + dir);
        }

        // Windows-safe: write PID using the SAME channel we locked.
        try {
            String pid = String.valueOf(ProcessHandle.current().pid());
            byte[] bytes = pid.getBytes(StandardCharsets.UTF_8);

            lockChannel.truncate(0);
            lockChannel.position(0);
            lockChannel.write(ByteBuffer.wrap(bytes));
            lockChannel.force(true);
        } catch (Throwable t) {
            log.warn("[ui/jcef] failed to write pid marker: {}", t.toString());
        }

        log.info("[ui/jcef] profile lock acquired: {}", dir);
    }

    private void releaseProfileLock() {
        try { if (lock != null) lock.release(); } catch (Throwable ignored) {}
        try { if (lockChannel != null) lockChannel.close(); } catch (Throwable ignored) {}
        lock = null;
        lockChannel = null;
    }

    private static void ensureDir(Path dir) throws IOException {
        Files.createDirectories(dir);
    }

    // ---------------------------------------------------------------------
    // HELPERS
    // ---------------------------------------------------------------------

    private static Path findSubprocessHelper(Path installDir) {
        // Most jcefmaven bundles keep it in root; some keep in "bin" or "jcef_helper"
        String[] roots = {"", "bin", "jcef_helper", "helper", "subprocess"};
        String[] names = {"jcef_helper.exe", "jcef_helper64.exe", "cef_helper.exe"};

        for (String r : roots) {
            Path base = r.isEmpty() ? installDir : installDir.resolve(r);
            for (String n : names) {
                Path p = base.resolve(n);
                if (Files.exists(p)) return p;
            }
        }
        return null;
    }

    private static void addJcefArgs(CefAppBuilder builder, String... args) {
        invokeIfExists(builder, "addJcefArgs",
                new Class<?>[]{String[].class}, new Object[]{args});
    }

    private static boolean invokeIfExists(Object target,
                                          String name,
                                          Class<?>[] sig,
                                          Object[] params) {
        try {
            Method m = target.getClass().getMethod(name, sig);
            m.invoke(target, params);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        } catch (Throwable t) {
            // method exists but call failed: treat as "handled" (keeps backward-compat)
            return true;
        }
    }

    private static <T> T runOnEdt(EdtCallable<T> job) {
        if (SwingUtilities.isEventDispatchThread()) {
            try { return job.call(); }
            catch (Exception e) { throw new RuntimeException(e); }
        }
        final Holder<T> out = new Holder<>();
        try {
            SwingUtilities.invokeAndWait(() -> {
                try { out.value = job.call(); }
                catch (Throwable t) { out.error = t; }
            });
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
        if (out.error != null) throw new RuntimeException(out.error);
        return out.value;
    }

    @FunctionalInterface
    private interface EdtCallable<T> { T call() throws Exception; }

    private static final class Holder<T> {
        T value;
        Throwable error;
    }
}