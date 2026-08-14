package org.foxesworld.kalitech.engine.project;

import org.foxesworld.kalitech.core.ICOParser;
import org.foxesworld.kalitech.core.KalitechVersion;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.border.Border;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * UE-style engine-owned project browser.
 *
 * The browser stays deliberately shallow and cheap: it displays descriptors
 * discovered by ProjectCatalog and only probes a few explicit thumbnail files
 * in each project root. Heavy project assets are never scanned or loaded here.
 */
public final class ProjectLaunchDialog {

    private static final Dimension WINDOW_SIZE = new Dimension(980, 620);
    private static final Dimension ENGINE_ICON_SIZE = new Dimension(42, 42);
    private static final Dimension PROJECT_PREVIEW_SIZE = new Dimension(190, 108);
    private static final Dimension DETAILS_PREVIEW_SIZE = new Dimension(238, 134);

    private static final ResolutionOption[] RESOLUTIONS = {
            new ResolutionOption(1280, 720),
            new ResolutionOption(1366, 768),
            new ResolutionOption(1440, 900),
            new ResolutionOption(1600, 900),
            new ResolutionOption(1920, 1080),
            new ResolutionOption(2560, 1440),
            new ResolutionOption(3840, 2160)
    };

    private static final RendererOption[] RENDERERS = {
            new RendererOption("OpenGL 4.5 — Performance", "opengl45"),
            new RendererOption("OpenGL 3.3 — Compatibility", "opengl33")
    };

    private static final SamplesOption[] SAMPLES = {
            new SamplesOption("Off", 0),
            new SamplesOption("2x", 2),
            new SamplesOption("4x", 4),
            new SamplesOption("8x", 8)
    };

    private ProjectLaunchDialog() {}

    public static Optional<LaunchSelection> choose(
            ProjectCatalog.DiscoveryResult catalog,
            Path projectsRoot,
            Path preferredDescriptor
    ) {
        if (GraphicsEnvironment.isHeadless()) {
            return Optional.empty();
        }

        AtomicReference<Optional<LaunchSelection>> result =
                new AtomicReference<>(Optional.empty());
        Runnable show = () -> result.set(showDialog(catalog, projectsRoot, preferredDescriptor));

        if (SwingUtilities.isEventDispatchThread()) {
            show.run();
        } else {
            try {
                SwingUtilities.invokeAndWait(show);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            } catch (InvocationTargetException failure) {
                Throwable cause = failure.getCause();
                if (cause instanceof RuntimeException runtime) {
                    throw runtime;
                }
                throw new IllegalStateException("Failed to open project browser", cause);
            }
        }
        return result.get();
    }

    public static LaunchSelection defaults(ProjectDescriptor project) {
        int width = positiveIntProperty("kalitech.width", 1440);
        int height = positiveIntProperty("kalitech.height", 900);
        boolean fullscreen = Boolean.parseBoolean(System.getProperty("kalitech.fullscreen", "false"));
        boolean vsync = Boolean.parseBoolean(System.getProperty("kalitech.vsync", "true"));
        int samples = nonNegativeIntProperty("kalitech.samples", 0);
        String renderer = normalizeRenderer(System.getProperty("kalitech.renderer", "opengl45"));
        return new LaunchSelection(project, width, height, fullscreen, vsync, samples, renderer);
    }

    private static Optional<LaunchSelection> showDialog(
            ProjectCatalog.DiscoveryResult catalog,
            Path projectsRoot,
            Path preferredDescriptor
    ) {
        JDialog dialog = new JDialog((JFrame) null,
                KalitechVersion.NAME + " Project Browser", true);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setMinimumSize(new Dimension(860, 540));
        dialog.setResizable(true);
        dialog.setLayout(new BorderLayout());

        List<ProjectDescriptor> projects = new ArrayList<>(catalog.projects());
        ProjectDescriptor preferred = preferredProject(projects, preferredDescriptor);
        AtomicReference<ProjectDescriptor> selectedProject = new AtomicReference<>(preferred);
        AtomicReference<ProjectCard> selectedCard = new AtomicReference<>();
        AtomicReference<LaunchSelection> selectedLaunch = new AtomicReference<>();

        JPanel header = buildHeader();
        dialog.add(header, BorderLayout.NORTH);

        JPanel navigation = new JPanel();
        navigation.setBorder(BorderFactory.createEmptyBorder(14, 12, 12, 8));
        navigation.setLayout(new BoxLayout(navigation, BoxLayout.Y_AXIS));
        navigation.setPreferredSize(new Dimension(168, 0));

        JLabel navCaption = new JLabel("PROJECTS");
        navCaption.setFont(navCaption.getFont().deriveFont(Font.BOLD, 11f));
        navCaption.setAlignmentX(0f);
        navigation.add(navCaption);
        navigation.add(Box.createVerticalStrut(8));

        JToggleButton recentButton = navButton("Recent Projects");
        JToggleButton allButton = navButton("All Projects");
        ButtonGroup navigationGroup = new ButtonGroup();
        navigationGroup.add(recentButton);
        navigationGroup.add(allButton);
        recentButton.setSelected(true);
        navigation.add(recentButton);
        navigation.add(Box.createVerticalStrut(4));
        navigation.add(allButton);
        navigation.add(Box.createVerticalStrut(14));

        JButton browseButton = new JButton("Open Other Project…");
        browseButton.setAlignmentX(0f);
        browseButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        navigation.add(browseButton);
        navigation.add(Box.createVerticalGlue());

        JLabel rootLabel = new JLabel("Project root");
        rootLabel.setFont(rootLabel.getFont().deriveFont(Font.BOLD, 10f));
        rootLabel.setAlignmentX(0f);
        navigation.add(rootLabel);
        navigation.add(Box.createVerticalStrut(3));
        JLabel rootPath = new JLabel(shortPath(projectsRoot));
        rootPath.setToolTipText(projectsRoot.toString());
        rootPath.setFont(rootPath.getFont().deriveFont(10f));
        rootPath.setAlignmentX(0f);
        navigation.add(rootPath);

        dialog.add(navigation, BorderLayout.WEST);

        JPanel projectGrid = new JPanel(new GridLayout(0, 2, 14, 14));
        projectGrid.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 8));
        JPanel gridHolder = new JPanel(new BorderLayout());
        gridHolder.add(projectGrid, BorderLayout.NORTH);

        JScrollPane projectScroll = new JScrollPane(gridHolder);
        projectScroll.setBorder(BorderFactory.createEmptyBorder());
        projectScroll.getVerticalScrollBar().setUnitIncrement(20);

        JLabel sectionTitle = new JLabel("Recent Projects");
        sectionTitle.setFont(sectionTitle.getFont().deriveFont(Font.BOLD, 17f));
        JLabel sectionSubtitle = new JLabel("Select a project to open with " + KalitechVersion.NAME + ".");
        sectionSubtitle.setFont(sectionSubtitle.getFont().deriveFont(11f));

        JPanel center = new JPanel(new BorderLayout());
        center.setBorder(BorderFactory.createEmptyBorder(14, 12, 12, 8));
        JPanel centerHeader = new JPanel();
        centerHeader.setLayout(new BoxLayout(centerHeader, BoxLayout.Y_AXIS));
        sectionTitle.setAlignmentX(0f);
        sectionSubtitle.setAlignmentX(0f);
        centerHeader.add(sectionTitle);
        centerHeader.add(Box.createVerticalStrut(2));
        centerHeader.add(sectionSubtitle);
        centerHeader.add(Box.createVerticalStrut(12));
        center.add(centerHeader, BorderLayout.NORTH);
        center.add(projectScroll, BorderLayout.CENTER);

        LaunchSettingsPanel settingsPanel = new LaunchSettingsPanel(defaults(
                preferred != null ? preferred : projects.isEmpty() ? null : projects.get(0)
        ));
        JPanel settingsViewport = new JPanel(new BorderLayout());
        settingsViewport.add(settingsPanel.panel(), BorderLayout.NORTH);

        JScrollPane settingsScroll = new JScrollPane(settingsViewport);
        settingsScroll.setBorder(BorderFactory.createEmptyBorder());
        settingsScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        settingsScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        settingsScroll.getVerticalScrollBar().setUnitIncrement(18);
        settingsScroll.setPreferredSize(new Dimension(300, 0));
        settingsScroll.setMinimumSize(new Dimension(286, 0));
        settingsScroll.getViewport().setViewPosition(new java.awt.Point(0, 0));
        dialog.add(settingsScroll, BorderLayout.EAST);
        dialog.add(center, BorderLayout.CENTER);

        JButton cancelButton = new JButton("Cancel");
        JButton launchButton = new JButton("Open Project");
        launchButton.setEnabled(selectedProject.get() != null);
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBorder(BorderFactory.createEmptyBorder(7, 12, 10, 12));

        String statusText = catalog.rejected().isEmpty()
                ? projects.size() + " project" + (projects.size() == 1 ? "" : "s") + " found"
                : projects.size() + " projects found · " + catalog.rejected().size() + " invalid skipped";
        JLabel status = new JLabel(statusText);
        status.setFont(status.getFont().deriveFont(10f));
        footer.add(status, BorderLayout.WEST);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.add(cancelButton);
        actions.add(launchButton);
        footer.add(actions, BorderLayout.EAST);
        dialog.add(footer, BorderLayout.SOUTH);

        Runnable launchSelected = () -> {
            ProjectDescriptor project = selectedProject.get();
            if (project == null) {
                return;
            }
            selectedLaunch.set(settingsPanel.selection(project));
            dialog.dispose();
        };

        Consumer<List<ProjectDescriptor>> renderProjects = visibleProjects -> {
            projectGrid.removeAll();
            selectedCard.set(null);
            for (ProjectDescriptor project : visibleProjects) {
                ProjectCard card = new ProjectCard(project, p -> {
                    ProjectCard oldCard = selectedCard.getAndSet(null);
                    if (oldCard != null) {
                        oldCard.setSelected(false);
                    }
                    ProjectCard clicked = findCard(projectGrid, p);
                    if (clicked != null) {
                        clicked.setSelected(true);
                        selectedCard.set(clicked);
                    }
                    selectedProject.set(p);
                    settingsPanel.updateProject(p);
                    launchButton.setEnabled(true);
                }, p -> {
                    selectedProject.set(p);
                    launchSelected.run();
                });
                projectGrid.add(card);
                if (project == selectedProject.get()) {
                    card.setSelected(true);
                    selectedCard.set(card);
                }
            }
            if (visibleProjects.isEmpty()) {
                JPanel empty = new JPanel(new BorderLayout());
                empty.setBorder(BorderFactory.createEmptyBorder(32, 16, 16, 16));
                JLabel emptyLabel = new JLabel("No projects in this view.", SwingConstants.CENTER);
                empty.add(emptyLabel, BorderLayout.CENTER);
                projectGrid.add(empty);
            }
            projectGrid.revalidate();
            projectGrid.repaint();
        };

        allButton.addActionListener(event -> {
            sectionTitle.setText("All Projects");
            sectionSubtitle.setText("Projects discovered under " + shortPath(projectsRoot) + ".");
            renderProjects.accept(projects);
        });

        recentButton.addActionListener(event -> {
            sectionTitle.setText("Recent Projects");
            sectionSubtitle.setText("The last configured project is shown first.");
            List<ProjectDescriptor> recent = new ArrayList<>();
            ProjectDescriptor current = preferredProject(projects, preferredDescriptor);
            if (current != null) {
                recent.add(current);
            }
            renderProjects.accept(recent);
        });

        browseButton.addActionListener(event -> {
            ProjectDescriptor loaded = browseProject(dialog, projectsRoot);
            if (loaded == null) {
                return;
            }
            if (projects.stream().noneMatch(p -> p.descriptorFile().equals(loaded.descriptorFile()))) {
                projects.add(0, loaded);
            }
            selectedProject.set(loaded);
            settingsPanel.updateProject(loaded);
            recentButton.setSelected(true);
            sectionTitle.setText("All Projects");
            sectionSubtitle.setText("Projects discovered under " + shortPath(projectsRoot) + ".");
            renderProjects.accept(projects);
            launchButton.setEnabled(true);
        });

        cancelButton.addActionListener(event -> dialog.dispose());
        launchButton.addActionListener(event -> launchSelected.run());
        dialog.getRootPane().setDefaultButton(launchButton);

        List<ProjectDescriptor> initialRecent = new ArrayList<>();
        ProjectDescriptor initialPreferred = preferredProject(projects, preferredDescriptor);
        if (initialPreferred != null) {
            initialRecent.add(initialPreferred);
        }
        renderProjects.accept(initialRecent);

        dialog.setSize(WINDOW_SIZE);
        centerOnUsableScreen(dialog);
        dialog.setVisible(true);
        dialog.dispose();
        return Optional.ofNullable(selectedLaunch.get());
    }

    private static JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout(12, 0));
        header.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));

        JLabel engineMark = new JLabel("N", SwingConstants.CENTER);
        engineMark.setPreferredSize(ENGINE_ICON_SIZE);
        engineMark.setMinimumSize(ENGINE_ICON_SIZE);
        engineMark.setMaximumSize(ENGINE_ICON_SIZE);
        engineMark.setFont(engineMark.getFont().deriveFont(Font.BOLD, 18f));
        engineMark.setBorder(BorderFactory.createLineBorder(new Color(92, 92, 92), 1));
        ImageIcon icon = engineIcon(ENGINE_ICON_SIZE);
        if (icon != null) {
            engineMark.setText("");
            engineMark.setBorder(BorderFactory.createEmptyBorder());
            engineMark.setIcon(icon);
        }
        header.add(engineMark, BorderLayout.WEST);

        JPanel text = new JPanel();
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        JLabel title = new JLabel(KalitechVersion.NAME);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        JLabel subtitle = new JLabel("Project Browser  ·  Engine " + KalitechVersion.VERSION);
        subtitle.setFont(subtitle.getFont().deriveFont(11f));
        title.setAlignmentX(0f);
        subtitle.setAlignmentX(0f);
        text.add(Box.createVerticalGlue());
        text.add(title);
        text.add(Box.createVerticalStrut(2));
        text.add(subtitle);
        text.add(Box.createVerticalGlue());
        header.add(text, BorderLayout.CENTER);
        return header;
    }

    private static JToggleButton navButton(String text) {
        JToggleButton button = new JToggleButton(text);
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setAlignmentX(0f);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        button.setFocusable(false);
        return button;
    }

    private static ProjectDescriptor browseProject(Window owner, Path projectsRoot) {
        JFileChooser chooser = new JFileChooser(projectsRoot.toFile());
        chooser.setDialogTitle("Open Kalitech Project");
        chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        chooser.setAcceptAllFileFilterUsed(true);
        if (chooser.showOpenDialog(owner) != JFileChooser.APPROVE_OPTION) {
            return null;
        }
        try {
            Path descriptor = ProjectCatalog.descriptorPath(chooser.getSelectedFile().toPath());
            return ProjectDescriptor.load(descriptor);
        } catch (RuntimeException failure) {
            JOptionPane.showMessageDialog(
                    owner,
                    failure.getMessage(),
                    "Invalid project",
                    JOptionPane.ERROR_MESSAGE
            );
            return null;
        }
    }

    private static ProjectCard findCard(JPanel grid, ProjectDescriptor descriptor) {
        for (var component : grid.getComponents()) {
            if (component instanceof ProjectCard card
                    && card.project().descriptorFile().equals(descriptor.descriptorFile())) {
                return card;
            }
        }
        return null;
    }

    private static ProjectDescriptor preferredProject(
            List<ProjectDescriptor> projects,
            Path preferredDescriptor
    ) {
        if (preferredDescriptor != null) {
            Path target = ProjectCatalog.descriptorPath(preferredDescriptor);
            for (ProjectDescriptor project : projects) {
                if (project.descriptorFile().equals(target)) {
                    return project;
                }
            }
        }
        return projects.isEmpty() ? null : projects.get(0);
    }

    private static ImageIcon projectThumbnail(ProjectDescriptor project, Dimension size) {
        Path root = project.projectOwnedRoot();
        for (String candidate : List.of(
                "project.png",
                "thumbnail.png",
                "preview.png",
                ".kalitech/thumbnail.png"
        )) {
            Path file = root.resolve(candidate).normalize();
            if (file.startsWith(root) && Files.isRegularFile(file)) {
                ImageIcon icon = scaledFileImage(file, size);
                if (icon != null) {
                    return icon;
                }
            }
        }
        return null;
    }

    private static void applyProjectPreview(
            JLabel label,
            ProjectDescriptor project,
            Dimension size
    ) {
        label.setPreferredSize(size);
        label.setMinimumSize(size);
        label.setMaximumSize(size);
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setVerticalAlignment(SwingConstants.CENTER);
        label.setOpaque(false);
        label.setText("");
        label.setIcon(null);

        if (project != null) {
            ImageIcon preview = projectThumbnail(project, size);
            if (preview != null) {
                label.setIcon(preview);
                return;
            }
        }

        label.setText("No Project Preview");
        label.setOpaque(true);
        label.setBackground(new Color(38, 40, 43));
        label.setForeground(new Color(150, 154, 160));
        label.setFont(label.getFont().deriveFont(Font.PLAIN, 10f));
        label.setBorder(BorderFactory.createLineBorder(new Color(68, 71, 76), 1));
    }

    private static ImageIcon engineIcon(Dimension size) {
        try (InputStream stream = ProjectLaunchDialog.class
                .getClassLoader()
                .getResourceAsStream("engine/engineIco.ico")) {
            if (stream == null) {
                return null;
            }
            ICOParser parser = new ICOParser(4);
            BufferedImage[] frames = parser.parse(stream);
            BufferedImage best = parser.getBestMatchingIcon(
                    frames,
                    size.width,
                    size.height
            );
            return best == null ? null : scaledImage(best, size);
        } catch (IOException ignored) {
            return null;
        }
    }

    private static ImageIcon scaledFileImage(Path file, Dimension size) {
        try {
            BufferedImage image = ImageIO.read(file.toFile());
            if (image == null) {
                return null;
            }
            return scaledImage(image, size);
        } catch (IOException ignored) {
            return null;
        }
    }

    private static ImageIcon scaledIcon(ImageIcon source, Dimension size) {
        if (source == null || source.getIconWidth() <= 0 || source.getIconHeight() <= 0) {
            return null;
        }
        return scaledImage(source.getImage(), size);
    }

    private static ImageIcon scaledImage(Image source, Dimension size) {
        int sourceWidth = source.getWidth(null);
        int sourceHeight = source.getHeight(null);
        if (sourceWidth <= 0 || sourceHeight <= 0) {
            return null;
        }
        double scale = Math.min(
                size.getWidth() / sourceWidth,
                size.getHeight() / sourceHeight
        );
        int width = Math.max(1, (int) Math.round(sourceWidth * scale));
        int height = Math.max(1, (int) Math.round(sourceHeight * scale));
        Image scaled = source.getScaledInstance(width, height, Image.SCALE_SMOOTH);
        return new ImageIcon(scaled);
    }

    private static String displayName(ProjectDescriptor project) {
        Path name = project.projectOwnedRoot().getFileName();
        return name == null ? project.id() : name.toString();
    }

    private static String shortPath(Path path) {
        String value = path.toAbsolutePath().normalize().toString();
        if (value.length() <= 34) {
            return value;
        }
        return "…" + value.substring(value.length() - 33);
    }

    private static int positiveIntProperty(String name, int fallback) {
        try {
            int value = Integer.parseInt(System.getProperty(name, Integer.toString(fallback)).trim());
            return value > 0 ? value : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static int nonNegativeIntProperty(String name, int fallback) {
        try {
            int value = Integer.parseInt(System.getProperty(name, Integer.toString(fallback)).trim());
            return value >= 0 ? value : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static String normalizeRenderer(String value) {
        String renderer = value == null ? "opengl45" : value.trim().toLowerCase();
        return switch (renderer) {
            case "opengl33", "gl33", "compat", "compatibility" -> "opengl33";
            default -> "opengl45";
        };
    }

    private static void centerOnUsableScreen(Window window) {
        GraphicsConfiguration configuration = GraphicsEnvironment
                .getLocalGraphicsEnvironment()
                .getDefaultScreenDevice()
                .getDefaultConfiguration();
        window.setLocationRelativeTo(null);
        if (configuration != null) {
            var bounds = configuration.getBounds();
            int x = bounds.x + Math.max(0, (bounds.width - window.getWidth()) / 2);
            int y = bounds.y + Math.max(0, (bounds.height - window.getHeight()) / 2);
            window.setLocation(x, y);
        }
    }

    public record LaunchSelection(
            ProjectDescriptor project,
            int width,
            int height,
            boolean fullscreen,
            boolean vsync,
            int samples,
            String renderer
    ) {}

    private static final class ProjectCard extends JPanel {
        private static final Border NORMAL_BORDER = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(80, 80, 80), 1),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)
        );
        private static final Border SELECTED_BORDER = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(240, 145, 35), 2),
                BorderFactory.createEmptyBorder(7, 7, 7, 7)
        );

        private final ProjectDescriptor project;

        ProjectCard(
                ProjectDescriptor project,
                Consumer<ProjectDescriptor> onSelect,
                Consumer<ProjectDescriptor> onOpen
        ) {
            super(new BorderLayout(0, 8));
            this.project = project;
            setBorder(NORMAL_BORDER);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            JLabel preview = new JLabel("", SwingConstants.CENTER);
            applyProjectPreview(preview, project, PROJECT_PREVIEW_SIZE);
            add(preview, BorderLayout.CENTER);

            JPanel info = new JPanel();
            info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
            JLabel name = new JLabel(displayName(project));
            name.setFont(name.getFont().deriveFont(Font.BOLD, 13f));
            JLabel id = new JLabel(project.id());
            id.setFont(id.getFont().deriveFont(10f));
            JLabel path = new JLabel(shortPath(project.projectOwnedRoot()));
            path.setFont(path.getFont().deriveFont(9f));
            path.setToolTipText(project.projectOwnedRoot().toString());
            name.setAlignmentX(0f);
            id.setAlignmentX(0f);
            path.setAlignmentX(0f);
            info.add(name);
            info.add(Box.createVerticalStrut(2));
            info.add(id);
            info.add(Box.createVerticalStrut(2));
            info.add(path);
            add(info, BorderLayout.SOUTH);

            MouseAdapter listener = new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent event) {
                    onSelect.accept(project);
                    if (event.getClickCount() >= 2 && SwingUtilities.isLeftMouseButton(event)) {
                        onOpen.accept(project);
                    }
                }
            };
            addMouseListener(listener);
            preview.addMouseListener(listener);
            info.addMouseListener(listener);
            name.addMouseListener(listener);
            id.addMouseListener(listener);
            path.addMouseListener(listener);
        }

        ProjectDescriptor project() {
            return project;
        }

        void setSelected(boolean selected) {
            setBorder(selected ? SELECTED_BORDER : NORMAL_BORDER);
            repaint();
        }
    }

    private static final class LaunchSettingsPanel {
        private final JPanel panel;
        private final JLabel projectPreview;
        private final JLabel projectName;
        private final JLabel projectId;
        private final JLabel projectPath;
        private final JLabel projectEntry;
        private final JComboBox<ResolutionOption> resolution;
        private final JComboBox<RendererOption> renderer;
        private final JComboBox<SamplesOption> samples;
        private final JCheckBox fullscreen;
        private final JCheckBox vsync;

        LaunchSettingsPanel(LaunchSelection defaults) {
            panel = new JPanel();
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
            panel.setBorder(BorderFactory.createEmptyBorder(14, 10, 12, 14));

            JLabel selectedTitle = new JLabel("Selected Project");
            selectedTitle.setFont(selectedTitle.getFont().deriveFont(Font.BOLD, 15f));
            selectedTitle.setAlignmentX(0f);
            panel.add(selectedTitle);
            panel.add(Box.createVerticalStrut(9));

            projectPreview = new JLabel("", SwingConstants.CENTER);
            projectPreview.setAlignmentX(0f);
            panel.add(projectPreview);
            panel.add(Box.createVerticalStrut(10));

            projectName = detailValue(true, 13f);
            projectId = detailValue(false, 9.5f);
            projectPath = detailValue(false, 9.5f);
            projectEntry = detailValue(false, 9.5f);
            panel.add(detailField("Name", projectName));
            panel.add(Box.createVerticalStrut(5));
            panel.add(detailField("Project ID", projectId));
            panel.add(Box.createVerticalStrut(5));
            panel.add(detailField("Location", projectPath));
            panel.add(Box.createVerticalStrut(5));
            panel.add(detailField("Entry Point", projectEntry));
            panel.add(Box.createVerticalStrut(12));

            JPanel separator = new JPanel();
            separator.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
            separator.setPreferredSize(new Dimension(1, 1));
            separator.setBackground(new Color(68, 71, 76));
            separator.setAlignmentX(0f);
            panel.add(separator);
            panel.add(Box.createVerticalStrut(12));

            JLabel title = new JLabel("Launch Settings");
            title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));
            title.setAlignmentX(0f);
            panel.add(title);
            panel.add(Box.createVerticalStrut(3));
            JLabel subtitle = new JLabel("Applied before the render context starts.");
            subtitle.setFont(subtitle.getFont().deriveFont(9.5f));
            subtitle.setAlignmentX(0f);
            panel.add(subtitle);
            panel.add(Box.createVerticalStrut(10));

            resolution = new JComboBox<>(RESOLUTIONS);
            selectResolution(resolution, defaults.width(), defaults.height());
            renderer = new JComboBox<>(RENDERERS);
            selectRenderer(renderer, defaults.renderer());
            samples = new JComboBox<>(SAMPLES);
            selectSamples(samples, defaults.samples());
            fullscreen = new JCheckBox("Fullscreen", defaults.fullscreen());
            vsync = new JCheckBox("Vertical Sync", defaults.vsync());

            panel.add(field("Resolution", resolution));
            panel.add(Box.createVerticalStrut(7));
            panel.add(field("Renderer", renderer));
            panel.add(Box.createVerticalStrut(7));
            panel.add(field("Anti-aliasing", samples));
            panel.add(Box.createVerticalStrut(8));
            fullscreen.setAlignmentX(0f);
            vsync.setAlignmentX(0f);
            panel.add(fullscreen);
            panel.add(Box.createVerticalStrut(2));
            panel.add(vsync);
            panel.add(Box.createVerticalGlue());

            JLabel hint = new JLabel("Compatibility: OpenGL 3.3 / GLSL 330");
            hint.setFont(hint.getFont().deriveFont(9f));
            hint.setAlignmentX(0f);
            panel.add(hint);

            updateProject(defaults.project());
        }

        JPanel panel() {
            return panel;
        }

        void updateProject(ProjectDescriptor project) {
            applyProjectPreview(projectPreview, project, DETAILS_PREVIEW_SIZE);
            if (project == null) {
                projectName.setText("No project selected");
                projectId.setText("—");
                projectPath.setText("—");
                projectPath.setToolTipText(null);
                projectEntry.setText("—");
                return;
            }

            projectName.setText(displayName(project));
            projectId.setText(project.id());
            projectPath.setText(shortPath(project.projectOwnedRoot()));
            projectPath.setToolTipText(project.projectOwnedRoot().toString());
            projectEntry.setText(project.scripts().moduleId());
        }

        LaunchSelection selection(ProjectDescriptor project) {
            ResolutionOption mode = (ResolutionOption) resolution.getSelectedItem();
            RendererOption renderMode = (RendererOption) renderer.getSelectedItem();
            SamplesOption aa = (SamplesOption) samples.getSelectedItem();
            if (mode == null || renderMode == null || aa == null) {
                return defaults(project);
            }
            return new LaunchSelection(
                    project,
                    mode.width(),
                    mode.height(),
                    fullscreen.isSelected(),
                    vsync.isSelected(),
                    aa.samples(),
                    renderMode.key()
            );
        }

        private static JLabel detailValue(boolean strong, float size) {
            JLabel label = new JLabel("—");
            label.setFont(label.getFont().deriveFont(strong ? Font.BOLD : Font.PLAIN, size));
            label.setAlignmentX(0f);
            return label;
        }

        private static JPanel detailField(String label, JLabel value) {
            JPanel wrapper = new JPanel();
            wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
            wrapper.setAlignmentX(0f);
            wrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
            JLabel caption = new JLabel(label.toUpperCase());
            caption.setFont(caption.getFont().deriveFont(Font.BOLD, 8.5f));
            caption.setForeground(new Color(145, 149, 155));
            caption.setAlignmentX(0f);
            wrapper.add(caption);
            wrapper.add(Box.createVerticalStrut(1));
            wrapper.add(value);
            return wrapper;
        }

        private static JPanel field(String label, JComboBox<?> control) {
            JPanel wrapper = new JPanel();
            wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
            wrapper.setAlignmentX(0f);
            wrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, 54));
            JLabel caption = new JLabel(label);
            caption.setFont(caption.getFont().deriveFont(Font.BOLD, 10f));
            caption.setAlignmentX(0f);
            control.setAlignmentX(0f);
            control.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
            wrapper.add(caption);
            wrapper.add(Box.createVerticalStrut(3));
            wrapper.add(control);
            return wrapper;
        }

        private static void selectResolution(JComboBox<ResolutionOption> combo, int width, int height) {
            for (int i = 0; i < combo.getItemCount(); i++) {
                ResolutionOption option = combo.getItemAt(i);
                if (option.width() == width && option.height() == height) {
                    combo.setSelectedIndex(i);
                    return;
                }
            }
            combo.addItem(new ResolutionOption(width, height));
            combo.setSelectedIndex(combo.getItemCount() - 1);
        }

        private static void selectRenderer(JComboBox<RendererOption> combo, String key) {
            String normalized = normalizeRenderer(key);
            for (int i = 0; i < combo.getItemCount(); i++) {
                if (combo.getItemAt(i).key().equals(normalized)) {
                    combo.setSelectedIndex(i);
                    return;
                }
            }
            combo.setSelectedIndex(0);
        }

        private static void selectSamples(JComboBox<SamplesOption> combo, int samples) {
            for (int i = 0; i < combo.getItemCount(); i++) {
                if (combo.getItemAt(i).samples() == samples) {
                    combo.setSelectedIndex(i);
                    return;
                }
            }
            combo.setSelectedIndex(0);
        }
    }

    private record ResolutionOption(int width, int height) {
        @Override
        public String toString() {
            return width + " × " + height;
        }
    }

    private record RendererOption(String label, String key) {
        @Override
        public String toString() {
            return label;
        }
    }

    private record SamplesOption(String label, int samples) {
        @Override
        public String toString() {
            return label;
        }
    }
}
