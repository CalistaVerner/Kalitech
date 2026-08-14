package org.foxesworld.kalitech.engine;

import com.jme3.system.AppSettings;
import org.foxesworld.kalitech.core.ICOParser;
import org.foxesworld.kalitech.core.KalitechVersion;
import org.foxesworld.kalitech.engine.project.ProjectCatalog;
import org.foxesworld.kalitech.engine.project.ProjectDescriptor;
import org.foxesworld.kalitech.engine.project.ProjectLaunchDialog;
import org.foxesworld.kalitech.engine.project.ProjectLaunchDialog.LaunchSelection;

import java.awt.GraphicsEnvironment;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.foxesworld.kalitech.core.Theme.setupTheme;

public final class KalitechLauncher {

    private static final String RELAUNCH_FLAG = "kalitech.relaunched";
    private static final String VMOPTIONS_PROP = "kalitech.vmoptions";
    private static final String PROJECT_PROP = "kalitech.project";
    private static final String PROJECTS_ROOT_PROP = "kalitech.projectsRoot";
    private static final String LAUNCH_DIALOG_PROP = "kalitech.launchDialog";
    private static final String RENDERER_PROP = "kalitech.renderer";
    private static final String SMOKE_EXIT_PROP = "kalitech.smokeExitAfterSeconds";
    private static final String THEME_PROP = "theme.path";

    public static void main(String[] args) {
        if (!Boolean.getBoolean(RELAUNCH_FLAG)) {
            Path vmoptions = resolveVmOptionsPath();
            if (Files.isRegularFile(vmoptions)) {
                List<String> opts = readVmOptions(vmoptions);
                if (!opts.isEmpty() && relaunchWithVmOptions(opts, args)) {
                    return;
                }
            }
        }

        setupTheme(System.getProperty(THEME_PROP, "engine/calista.properties"));
        LaunchSelection launch = selectLaunch(args);
        if (launch == null) {
            return;
        }

        ProjectDescriptor project = launch.project();
        System.setProperty(PROJECT_PROP, project.descriptorFile().toString());

        KalitechApplication app = new KalitechApplication(project);
        AppSettings settings = KalitechWindowSettings.build(KalitechLauncher.class.getClassLoader());
        settings.setResolution(launch.width(), launch.height());
        settings.setFullscreen(launch.fullscreen());
        settings.setVSync(launch.vsync());
        settings.setSamples(launch.samples());
        settings.setRenderer(resolveRenderer(launch.renderer()));

        // The compact engine-owned launcher already collected the pre-start settings.
        app.setShowSettings(false);
        app.setSettings(settings);
        app.start();
    }

    static final class KalitechWindowSettings {

        private KalitechWindowSettings() {}

        static AppSettings build(ClassLoader cl) {
            AppSettings settings = new AppSettings(true);
            settings.setTitle(KalitechVersion.NAME + " " + KalitechVersion.VERSION);
            settings.setResizable(true);
            settings.setVSync(true);
            settings.setGammaCorrection(true);

            try {
                ICOParser ico = new ICOParser();
                var stream = cl.getResourceAsStream("engine/engineIco.ico");
                if (stream != null) {
                    settings.setIcons(ico.pickBestIcons(ico.parse(stream)));
                }
            } catch (Exception e) {
                System.out.println("Window icon not set (no ico/png found).");
            }

            return settings;
        }
    }

    static LaunchSelection selectLaunch(String[] args) {
        Path explicit = resolveProjectArgument(args);
        if (explicit != null) {
            return ProjectLaunchDialog.defaults(
                    ProjectDescriptor.load(ProjectCatalog.descriptorPath(explicit))
            );
        }

        Path preferred = resolveConfiguredProjectDescriptor();
        Path projectsRoot = resolveProjectsRoot();
        ProjectCatalog.DiscoveryResult catalog = ProjectCatalog.discover(projectsRoot, preferred);

        if (!shouldShowLaunchDialog()) {
            return ProjectLaunchDialog.defaults(selectNonInteractive(catalog, preferred));
        }

        Optional<LaunchSelection> selected = ProjectLaunchDialog.choose(
                catalog,
                projectsRoot,
                preferred
        );
        return selected.orElse(null);
    }

    static Path resolveProjectDescriptor(String[] args) {
        Path explicit = resolveProjectArgument(args);
        if (explicit != null) {
            return ProjectCatalog.descriptorPath(explicit);
        }
        Path configured = resolveConfiguredProjectDescriptor();
        if (configured != null) {
            return ProjectCatalog.descriptorPath(configured);
        }
        return resolveProjectsRoot().resolve(ProjectDescriptor.DEFAULT_FILE_NAME)
                .toAbsolutePath()
                .normalize();
    }

    private static ProjectDescriptor selectNonInteractive(
            ProjectCatalog.DiscoveryResult catalog,
            Path preferred
    ) {
        if (preferred != null) {
            Path target = ProjectCatalog.descriptorPath(preferred);
            for (ProjectDescriptor project : catalog.projects()) {
                if (project.descriptorFile().equals(target)) {
                    return project;
                }
            }
        }
        if (catalog.projects().size() == 1) {
            return catalog.projects().get(0);
        }
        if (catalog.projects().isEmpty()) {
            throw new IllegalStateException(
                    "No valid Kalitech projects found under " + resolveProjectsRoot()
                            + ". Pass --project <project.json>."
            );
        }
        throw new IllegalStateException(
                "Multiple Kalitech projects found in non-interactive mode. "
                        + "Pass --project <project.json> or -D" + PROJECT_PROP + "=<project.json>."
        );
    }

    private static boolean shouldShowLaunchDialog() {
        if (isSmokeRun() || GraphicsEnvironment.isHeadless()) {
            return false;
        }
        return Boolean.parseBoolean(System.getProperty(LAUNCH_DIALOG_PROP, "true"));
    }

    private static Path resolveProjectArgument(String[] args) {
        if (args == null) {
            return null;
        }
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg == null) continue;
            if (arg.startsWith("--project=")) {
                String value = arg.substring("--project=".length()).trim();
                if (value.isEmpty()) {
                    throw new IllegalArgumentException("--project requires a descriptor path");
                }
                return Path.of(value).toAbsolutePath().normalize();
            }
            if ("--project".equals(arg)) {
                if (i + 1 >= args.length || args[i + 1] == null || args[i + 1].isBlank()) {
                    throw new IllegalArgumentException("--project requires a descriptor path");
                }
                return Path.of(args[i + 1].trim()).toAbsolutePath().normalize();
            }
        }
        return null;
    }

    private static Path resolveConfiguredProjectDescriptor() {
        String configured = System.getProperty(PROJECT_PROP);
        if (configured == null || configured.isBlank()) {
            return null;
        }
        return Path.of(configured.trim()).toAbsolutePath().normalize();
    }

    private static Path resolveProjectsRoot() {
        String configured = System.getProperty(PROJECTS_ROOT_PROP);
        Path root = configured == null || configured.isBlank()
                ? Path.of("project")
                : Path.of(configured.trim());
        return root.toAbsolutePath().normalize();
    }

    private static String resolveRenderer(String requested) {
        String configured = requested == null || requested.isBlank()
                ? System.getProperty(RENDERER_PROP, "opengl45").trim().toLowerCase()
                : requested.trim().toLowerCase();
        return switch (configured) {
            case "opengl3", "opengl33", "gl3", "gl33", "compat", "compatibility" -> AppSettings.LWJGL_OPENGL33;
            case "opengl45", "gl45", "performance", "default" -> AppSettings.LWJGL_OPENGL45;
            default -> {
                System.err.println("[KalitechLauncher] Unknown renderer '" + configured
                        + "'; using OpenGL 4.5. Use -D" + RENDERER_PROP
                        + "=opengl33 for compatibility mode.");
                yield AppSettings.LWJGL_OPENGL45;
            }
        };
    }

    private static boolean isSmokeRun() {
        try {
            return Float.parseFloat(System.getProperty(SMOKE_EXIT_PROP, "0").trim()) > 0f;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static Path resolveVmOptionsPath() {
        String explicit = System.getProperty(VMOPTIONS_PROP);
        if (explicit != null && !explicit.isBlank()) {
            return Path.of(explicit).toAbsolutePath().normalize();
        }
        return Path.of("Kalitech.vmoptions").toAbsolutePath().normalize();
    }

    private static List<String> readVmOptions(Path file) {
        List<String> out = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(file.toFile()))) {
            String line;
            while ((line = br.readLine()) != null) {
                String value = line.trim();
                if (value.isEmpty() || value.startsWith("#")) continue;

                int hash = value.indexOf('#');
                if (hash >= 0) value = value.substring(0, hash).trim();
                if (!value.isEmpty()) out.add(value);
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
            copySystemProperty(cmd, PROJECT_PROP);
            copySystemProperty(cmd, PROJECTS_ROOT_PROP);
            copySystemProperty(cmd, LAUNCH_DIALOG_PROP);
            copySystemProperty(cmd, RENDERER_PROP);
            copySystemProperty(cmd, "kalitech.width");
            copySystemProperty(cmd, "kalitech.height");
            copySystemProperty(cmd, "kalitech.fullscreen");
            copySystemProperty(cmd, "kalitech.vsync");
            copySystemProperty(cmd, "kalitech.samples");
            copySystemProperty(cmd, SMOKE_EXIT_PROP);
            copySystemProperty(cmd, THEME_PROP);
            cmd.add("-D" + RELAUNCH_FLAG + "=true");
            cmd.add("-cp");
            cmd.add(classPath);
            cmd.add(mainClass);
            if (args != null) {
                cmd.addAll(Arrays.asList(args));
            }

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

    private static void copySystemProperty(List<String> command, String name) {
        String value = System.getProperty(name);
        if (value != null && !value.isBlank()) {
            command.add("-D" + name + "=" + value.trim());
        }
    }
}
