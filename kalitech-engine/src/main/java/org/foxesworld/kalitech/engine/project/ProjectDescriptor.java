package org.foxesworld.kalitech.engine.project;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.foxesworld.kalitech.engine.script.ScriptEntryPoint;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Strict project descriptor. Its parent directory is the authoritative
 * project-owned root.
 */
public final class ProjectDescriptor {

    public static final int SCHEMA_VERSION = 1;
    public static final String DEFAULT_FILE_NAME = "project.json";

    private static final Pattern PROJECT_ID_PATTERN =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

    private final Path descriptorFile;
    private final Path projectOwnedRoot;
    private final String id;
    private final ScriptEntryPoint scripts;

    private ProjectDescriptor(
            Path descriptorFile,
            Path projectOwnedRoot,
            String id,
            ScriptEntryPoint scripts
    ) {
        this.descriptorFile = descriptorFile;
        this.projectOwnedRoot = projectOwnedRoot;
        this.id = id;
        this.scripts = scripts;
    }

    public static ProjectDescriptor load(Path source) {
        Path descriptor = Objects.requireNonNull(source, "source")
                .toAbsolutePath()
                .normalize();
        if (!Files.isRegularFile(descriptor)) {
            throw new IllegalArgumentException("Project descriptor not found: " + descriptor);
        }

        final Dto dto;
        try {
            dto = MAPPER.readValue(Files.readString(descriptor), Dto.class);
        } catch (JsonProcessingException malformed) {
            throw new IllegalArgumentException(
                    "Invalid project descriptor '" + descriptor + "': "
                            + malformed.getOriginalMessage(),
                    malformed
            );
        } catch (IOException failure) {
            throw new IllegalStateException(
                    "Failed to read project descriptor: " + descriptor,
                    failure
            );
        }

        if (dto.schemaVersion == null || dto.schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported project schemaVersion=" + dto.schemaVersion
                            + "; expected " + SCHEMA_VERSION);
        }

        String id = requireProjectId(dto.id);
        if (dto.scripts == null) {
            throw new IllegalArgumentException("project descriptor missing 'scripts'");
        }

        ScriptEntryPoint scripts = new ScriptEntryPoint(
                dto.scripts.namespace,
                dto.scripts.root,
                dto.scripts.entry
        );

        Path projectOwnedRoot = descriptor.getParent();
        if (projectOwnedRoot == null || !Files.isDirectory(projectOwnedRoot)) {
            throw new IllegalArgumentException(
                    "Project-owned root not found for descriptor: " + descriptor);
        }

        String entryProjectPath = scripts.projectOwnedPath(scripts.moduleId());
        Path entryFile = projectOwnedRoot.resolve(entryProjectPath).normalize();
        if (!entryFile.startsWith(projectOwnedRoot) || !Files.isRegularFile(entryFile)) {
            throw new IllegalArgumentException(
                    "Project Lua entrypoint not found: " + entryFile
                            + " (module " + scripts.moduleId() + ")");
        }

        return new ProjectDescriptor(
                descriptor,
                projectOwnedRoot,
                id,
                scripts
        );
    }

    public Path descriptorFile() {
        return descriptorFile;
    }

    public Path projectOwnedRoot() {
        return projectOwnedRoot;
    }

    public String id() {
        return id;
    }

    public ScriptEntryPoint scripts() {
        return scripts;
    }

    private static String requireProjectId(String value) {
        String id = requireNonBlank(value, "id");
        if (!PROJECT_ID_PATTERN.matcher(id).matches()) {
            throw new IllegalArgumentException(
                    "project descriptor 'id' must match " + PROJECT_ID_PATTERN.pattern());
        }
        return id;
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("project descriptor missing '" + field + "'");
        }
        return value.trim();
    }

    @SuppressWarnings("FieldMayBeFinal")
    private static final class Dto {
        public Integer schemaVersion;
        public String id;
        public ScriptsDto scripts;
    }

    @SuppressWarnings("FieldMayBeFinal")
    private static final class ScriptsDto {
        public String namespace;
        public String root;
        public String entry;
    }
}
