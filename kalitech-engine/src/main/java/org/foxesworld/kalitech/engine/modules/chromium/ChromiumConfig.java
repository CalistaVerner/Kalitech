package org.foxesworld.kalitech.engine.modules.chromium;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Chromium configuration for JCEF OSR integration.
 */
public final class ChromiumConfig {

    public final int width;
    public final int height;
    public final int fps;

    public final boolean transparent;
    public final boolean disableGpu;

    public final Path installDir;

    public final List<String> extraArgs;

    private ChromiumConfig(Builder b) {
        this.width = b.width;
        this.height = b.height;
        this.fps = b.fps;
        this.transparent = b.transparent;
        this.disableGpu = b.disableGpu;
        this.installDir = b.installDir;
        this.extraArgs = List.copyOf(b.extraArgs);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private int width = 1024;
        private int height = 640;
        private int fps = 60;

        private boolean transparent = true;
        private boolean disableGpu = true;

        private Path installDir = null;

        private final List<String> extraArgs = new ArrayList<>();

        public Builder size(int width, int height) {
            this.width = Math.max(1, width);
            this.height = Math.max(1, height);
            return this;
        }

        public Builder fps(int fps) {
            this.fps = Math.max(1, fps);
            return this;
        }

        public Builder transparent(boolean transparent) {
            this.transparent = transparent;
            return this;
        }

        public Builder disableGpu(boolean disableGpu) {
            this.disableGpu = disableGpu;
            return this;
        }

        public Builder installDir(Path installDir) {
            this.installDir = Objects.requireNonNull(installDir, "installDir");
            return this;
        }

        public Builder addArg(String arg) {
            this.extraArgs.add(Objects.requireNonNull(arg, "arg"));
            return this;
        }

        public ChromiumConfig build() {
            return new ChromiumConfig(this);
        }
    }
}