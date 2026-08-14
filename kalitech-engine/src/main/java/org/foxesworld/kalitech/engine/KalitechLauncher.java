package org.foxesworld.kalitech.engine;

import com.jme3.system.AppSettings;
import org.foxesworld.kalitech.core.ICOParser;
import org.foxesworld.kalitech.core.KalitechVersion;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.foxesworld.kalitech.core.Theme.setupTheme;

public final class KalitechLauncher {

    private static final String RELAUNCH_FLAG = "kalitech.relaunched";
    private static final String VMOPTIONS_PROP = "kalitech.vmoptions";

    public static void main(String[] args) {
        // Relaunch with vmoptions once, if configured.
        if (!Boolean.getBoolean(RELAUNCH_FLAG)) {
            Path vmoptions = resolveVmOptionsPath();
            if (Files.isRegularFile(vmoptions)) {
                List<String> opts = readVmOptions(vmoptions);
                if (!opts.isEmpty()) {
                    if (relaunchWithVmOptions(opts, args)) {
                        return;
                    }
                }
            }
        }
        setupTheme(System.getProperty("theme.path"));
        KalitechApplication app = new KalitechApplication();
        AppSettings settings = KalitechWindowSettings.build(KalitechLauncher.class.getClassLoader());
        settings.setResolution(1440, 900);
        settings.setRenderer(AppSettings.LWJGL_OPENGL45);
        //settings.setCustomRenderer(AWTSettingsDialog.class);
        settings.setSettingsDialogImage(System.getProperty("banner.path"));
        app.setShowSettings(!isSmokeRun());
        app.setSettings(settings);

        app.start();
    }

    static final class KalitechWindowSettings {

        private KalitechWindowSettings() {}

        static AppSettings build(ClassLoader cl) {
            AppSettings s = new AppSettings(true);
            s.setTitle(KalitechVersion.NAME + " " + KalitechVersion.VERSION);
            s.setResizable(true);
            s.setVSync(true);
            s.setGammaCorrection(true);

            try {
                ICOParser ico = new ICOParser();
                var icons = ico.pickBestIcons(ico.parse(KalitechLauncher.class.getClassLoader().getResourceAsStream("engine/engineIco.ico")));
                s.setIcons(icons);
            } catch (Exception e) {
                System.out.println("Window icon not set (no ico/png found).");
            }

            return s;
        }
    }

    private static boolean isSmokeRun() {
        try {
            return Float.parseFloat(System.getProperty("kalitech.smokeExitAfterSeconds", "0").trim()) > 0f;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static Path resolveVmOptionsPath() {
        String explicit = System.getProperty(VMOPTIONS_PROP);
        if (explicit != null && !explicit.isBlank()) {
            return Path.of(explicit).toAbsolutePath().normalize();
        }
        Path local = Path.of("Kalitech.vmoptions").toAbsolutePath().normalize();
        return local;
    }

    private static List<String> readVmOptions(Path file) {
        List<String> out = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(file.toFile()))) {
            String line;
            while ((line = br.readLine()) != null) {
                String s = line.trim();
                if (s.isEmpty()) continue;
                if (s.startsWith("#")) continue;

                int hash = s.indexOf('#');
                if (hash >= 0) s = s.substring(0, hash).trim();
                if (s.isEmpty()) continue;

                out.add(s);
            }
        } catch (Exception e) {
            System.err.println("[KalitechLauncher] Failed to read vmoptions: " + file);
            e.printStackTrace(System.err);
        }
        return out;
    }

    private static boolean relaunchWithVmOptions(List<String> vmopts, String[] args) {
        Process child = null;
        try {
            String javaHome = System.getProperty("java.home");
            boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
            File javaExe = Path.of(javaHome, "bin", isWindows ? "java.exe" : "java").toFile();

            String classPath = System.getProperty("java.class.path");
            String mainClass = KalitechLauncher.class.getName();

            List<String> cmd = new ArrayList<>();
            cmd.add(javaExe.getAbsolutePath());
            cmd.addAll(vmopts);
            cmd.add("-D" + RELAUNCH_FLAG + "=true");
            cmd.add("-cp");
            cmd.add(classPath);
            cmd.add(mainClass);
            cmd.addAll(Arrays.asList(args));

            System.out.println("[KalitechLauncher] Relaunching JVM with VMOPTIONS:");
            for (String option : vmopts) {
                System.out.println("  " + option);
            }

            ProcessBuilder processBuilder = new ProcessBuilder(cmd);
            processBuilder.inheritIO();
            child = processBuilder.start();

            int exitCode = child.waitFor();
            System.exit(exitCode);
            return true;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            if (child != null) child.destroy();
            System.err.println("[KalitechLauncher] Relaunched JVM wait interrupted.");
            return true;
        } catch (Exception failure) {
            System.err.println("[KalitechLauncher] Relaunch failed; continuing without vmoptions.");
            failure.printStackTrace(System.err);
            return false;
        }
    }

}
