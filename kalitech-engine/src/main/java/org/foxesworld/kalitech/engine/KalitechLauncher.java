package org.foxesworld.kalitech.engine;

import com.jme3.system.AppSettings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.core.ICOParser;
import org.foxesworld.kalitech.core.KalitechVersion;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class KalitechLauncher {

    private static final String RELAUNCH_FLAG = "kalitech.relaunched";
    private static final String VMOPTIONS_PROP = "kalitech.vmoptions";

    public static void main(String[] args) {
        // 1) Если ещё не перезапускались — читаем vmoptions и перезапускаем JVM
        if (!Boolean.getBoolean(RELAUNCH_FLAG)) {
            Path vmoptions = resolveVmOptionsPath();
            if (vmoptions != null && Files.isRegularFile(vmoptions)) {
                List<String> opts = readVmOptions(vmoptions);
                if (!opts.isEmpty()) {
                    relaunchWithVmOptions(opts, args);
                    return; // важно: текущий процесс уходит
                }
            }
        }

        KalitechApplication app = new KalitechApplication();
        AppSettings settings = KalitechWindowSettings.build(KalitechLauncher.class.getClassLoader());
        var screen = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
        settings.setResolution((int)(screen.width * 0.85), (int)(screen.height * 0.85)
        );
        app.setShowSettings(true);
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
                var icons = ico.loadAppIcons(
                        "theme/icon/engineLogo.ico",
                        Path.of(KalitechVersion.ASSETSDIR),
                        cl
                );
                s.setIcons(icons);
            } catch (Exception e) {
                System.out.println("Window icon not set (no ico/png found).");
            }

            return s;
        }
    }

    private static Path resolveVmOptionsPath() {
        // 1) Если пользователь явно указал: -Dkalitech.vmoptions=path
        String explicit = System.getProperty(VMOPTIONS_PROP);
        if (explicit != null && !explicit.isBlank()) {
            return Path.of(explicit).toAbsolutePath().normalize();
        }

        // 2) По умолчанию: ./Kalitech.vmoptions рядом с запуском
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

                // IntelliJ vmoptions иногда использует комментарии после опции: "-Xmx2g # comment"
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

    private static void relaunchWithVmOptions(List<String> vmopts, String[] args) {
        try {
            // java executable
            String javaHome = System.getProperty("java.home");
            boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
            File javaExe = Path.of(javaHome, "bin", isWindows ? "java.exe" : "java").toFile();

            // classpath и main class
            String classPath = System.getProperty("java.class.path");
            String mainClass = KalitechLauncher.class.getName();

            // Собираем команду
            List<String> cmd = new ArrayList<>();
            cmd.add(javaExe.getAbsolutePath());

            // Добавляем vmoptions из файла
            cmd.addAll(vmopts);

            // Маркер, чтобы не уйти в бесконечный перезапуск
            cmd.add("-D" + RELAUNCH_FLAG + "=true");

            // Сохраняем полезные системные свойства, если нужно (можно расширить)
            // cmd.add("-Dlog.level=" + System.getProperty("log.level", "DEBUG"));

            cmd.add("-cp");
            cmd.add(classPath);

            cmd.add(mainClass);

            // Прокидываем args приложения
            for (String a : args) cmd.add(a);

            System.out.println("[KalitechLauncher] Relaunching JVM with VMOPTIONS:");
            for (String o : vmopts) System.out.println("  " + o);

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.inheritIO(); // чтобы лог/консоль оставались теми же
            Process p = pb.start();

            // Не ждём завершения — просто выходим, новый процесс продолжит
            System.exit(0);
        } catch (Exception e) {
            System.err.println("[KalitechLauncher] Relaunch failed; continuing without vmoptions.");
            e.printStackTrace(System.err);
        }
    }
}
