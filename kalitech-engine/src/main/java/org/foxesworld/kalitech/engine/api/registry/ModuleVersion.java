package org.foxesworld.kalitech.engine.api.registry;

import java.util.Objects;

public final class ModuleVersion implements Comparable<ModuleVersion> {
    public static final ModuleVersion ZERO = new ModuleVersion(0, 0, 0, "0.0.0");

    private final int major;
    private final int minor;
    private final int patch;
    private final String raw;

    private ModuleVersion(int major, int minor, int patch, String raw) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.raw = raw;
    }

    public static ModuleVersion parse(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return ZERO;
        }
        String trimmed = raw.trim();
        String base = trimmed;
        int dash = base.indexOf('-');
        if (dash >= 0) {
            base = base.substring(0, dash);
        }
        int plus = base.indexOf('+');
        if (plus >= 0) {
            base = base.substring(0, plus);
        }
        String[] parts = base.split("\\.");
        int major = parsePart(parts, 0);
        int minor = parsePart(parts, 1);
        int patch = parsePart(parts, 2);
        return new ModuleVersion(major, minor, patch, trimmed);
    }

    private static int parsePart(String[] parts, int index) {
        if (parts.length <= index) return 0;
        String part = parts[index];
        if (part == null || part.isEmpty()) return 0;
        String cleaned = part.replaceAll("[^0-9]", "");
        if (cleaned.isEmpty()) return 0;
        try {
            return Integer.parseInt(cleaned);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    public int major() {
        return major;
    }

    public int minor() {
        return minor;
    }

    public int patch() {
        return patch;
    }

    public String raw() {
        return raw;
    }

    @Override
    public int compareTo(ModuleVersion other) {
        if (other == null) return 1;
        int c = Integer.compare(this.major, other.major);
        if (c != 0) return c;
        c = Integer.compare(this.minor, other.minor);
        if (c != 0) return c;
        return Integer.compare(this.patch, other.patch);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ModuleVersion that)) return false;
        return major == that.major && minor == that.minor && patch == that.patch && raw.equals(that.raw);
    }

    @Override
    public int hashCode() {
        return Objects.hash(major, minor, patch, raw);
    }

    @Override
    public String toString() {
        return raw;
    }
}
