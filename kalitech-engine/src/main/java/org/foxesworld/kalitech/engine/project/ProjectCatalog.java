package org.foxesworld.kalitech.engine.project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Fast, shallow discovery for project-owned roots. A project is a direct child
 * of the configured projects directory and is identified by project.json.
 */
public final class ProjectCatalog {

    private ProjectCatalog() {}

    public static DiscoveryResult discover(Path projectsRoot, Path preferredDescriptor) {
        Path root = Objects.requireNonNull(projectsRoot, "projectsRoot")
                .toAbsolutePath()
                .normalize();
        Map<Path, ProjectDescriptor> projects = new LinkedHashMap<>();
        List<RejectedProject> rejected = new ArrayList<>();

        if (preferredDescriptor != null) {
            addCandidate(preferredDescriptor, projects, rejected);
        }

        if (Files.isDirectory(root)) {
            try (Stream<Path> children = Files.list(root)) {
                children
                        .filter(Files::isDirectory)
                        .map(path -> path.resolve(ProjectDescriptor.DEFAULT_FILE_NAME))
                        .filter(Files::isRegularFile)
                        .sorted(Comparator.comparing(path -> path.toAbsolutePath().normalize().toString(),
                                String.CASE_INSENSITIVE_ORDER))
                        .forEach(path -> addCandidate(path, projects, rejected));
            } catch (IOException failure) {
                rejected.add(new RejectedProject(root,
                        "Failed to enumerate projects directory: " + failure.getMessage()));
            }
        }

        List<ProjectDescriptor> ordered = projects.values().stream()
                .sorted(Comparator.comparing(ProjectDescriptor::id, String.CASE_INSENSITIVE_ORDER))
                .toList();
        return new DiscoveryResult(ordered, List.copyOf(rejected));
    }

    public static Path descriptorPath(Path source) {
        Path normalized = Objects.requireNonNull(source, "source")
                .toAbsolutePath()
                .normalize();
        if (Files.isDirectory(normalized)) {
            return normalized.resolve(ProjectDescriptor.DEFAULT_FILE_NAME).normalize();
        }
        return normalized;
    }

    private static void addCandidate(
            Path source,
            Map<Path, ProjectDescriptor> projects,
            List<RejectedProject> rejected
    ) {
        Path descriptor = descriptorPath(source);
        if (projects.containsKey(descriptor)) {
            return;
        }
        try {
            ProjectDescriptor loaded = ProjectDescriptor.load(descriptor);
            projects.put(loaded.descriptorFile(), loaded);
        } catch (RuntimeException failure) {
            rejected.add(new RejectedProject(descriptor, messageOf(failure)));
        }
    }

    private static String messageOf(RuntimeException failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank()
                ? failure.getClass().getSimpleName()
                : message;
    }

    public record DiscoveryResult(
            List<ProjectDescriptor> projects,
            List<RejectedProject> rejected
    ) {
        public DiscoveryResult {
            projects = List.copyOf(projects);
            rejected = List.copyOf(rejected);
        }
    }

    public record RejectedProject(Path descriptorFile, String reason) {}
}
