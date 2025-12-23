package org.foxesworld.kalitech.engine;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.lang.management.ManagementFactory;
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

        // 2) Тут мы уже во "взрослой" JVM (опции применены при старте процесса)

        System.setProperty("java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager");
        System.setProperty("log.dir", System.getProperty("user.dir"));

        // В log4j2.xml у тебя дефолт уже есть, но пусть будет:
        System.setProperty("log.level", "DEBUG");

        // Глушим JUL хендлеры (теперь оно будет моститься в log4j2 через log4j-jul)
        java.util.logging.LogManager.getLogManager().reset();

        KalitechApplication app = new KalitechApplication();
        app.start();
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
