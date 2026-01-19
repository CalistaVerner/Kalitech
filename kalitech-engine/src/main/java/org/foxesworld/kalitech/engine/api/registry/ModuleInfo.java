package org.foxesworld.kalitech.engine.api.registry;

import java.util.Objects;

public final class ModuleInfo {
    private final String id;
    private final String version;
    private final ModuleVersion parsedVersion;
    private final boolean deprecated;
    private final String replacedBy;
    private final String notes;

    public ModuleInfo(String id, String version, boolean deprecated, String replacedBy, String notes) {
        this.id = Objects.requireNonNull(id, "id");
        String trimmedId = id.trim();
        if (trimmedId.isEmpty()) {
            throw new IllegalArgumentException("ModuleInfo.id is blank");
        }
        this.version = (version == null || version.trim().isEmpty()) ? "0.0.0" : version.trim();
        this.parsedVersion = ModuleVersion.parse(this.version);
        this.deprecated = deprecated;
        this.replacedBy = (replacedBy == null || replacedBy.trim().isEmpty()) ? null : replacedBy.trim();
        this.notes = (notes == null || notes.trim().isEmpty()) ? null : notes.trim();
    }

    public static ModuleInfo of(String id, String version) {
        return new ModuleInfo(id, version, false, null, null);
    }

    public static ModuleInfo deprecated(String id, String version, String replacedBy, String notes) {
        return new ModuleInfo(id, version, true, replacedBy, notes);
    }

    public String id() {
        return id;
    }

    public String version() {
        return version;
    }

    public ModuleVersion parsedVersion() {
        return parsedVersion;
    }

    public boolean deprecated() {
        return deprecated;
    }

    public String replacedBy() {
        return replacedBy;
    }

    public String notes() {
        return notes;
    }
}
