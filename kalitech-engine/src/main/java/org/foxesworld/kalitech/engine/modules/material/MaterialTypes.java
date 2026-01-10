// FILE: org/foxesworld/kalitech/engine/modules/material/MaterialTypes.java
package org.foxesworld.kalitech.engine.modules.material;

import com.jme3.texture.Texture;

import java.util.Objects;
import java.util.function.Consumer;

public final class MaterialTypes {

    private MaterialTypes() {
    }

    public record TileWorld(float x, float z) {
    }

    public record TextureDesc(
            String texture,
            Texture.WrapMode wrap,
            Texture.MinFilter minFilter,
            Texture.MagFilter magFilter,
            int anisotropy,
            TileWorld tileWorld
    ) {
    }

    public record ParsedTex(String path, Texture.WrapMode wrap) {
    }

    public record RenderThreadScheduler(Consumer<Runnable> enqueue) {
        public void onRenderThread(Runnable r) {
            enqueue.accept(r);
        }
    }

    public record TextureKey(
            String path,
            Texture.WrapMode wrap,
            Texture.MinFilter min,
            Texture.MagFilter mag,
            int aniso,
            int hash
    ) {
        public static TextureKey of(TextureDesc td) {
            String p = td.texture().trim();
            Texture.WrapMode w = td.wrap();
            Texture.MinFilter mi = td.minFilter();
            Texture.MagFilter ma = td.magFilter();
            int a = Math.max(0, td.anisotropy());
            int h = Objects.hash(p, w, mi, ma, a);
            return new TextureKey(p, w, mi, ma, a, h);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof TextureKey k)) return false;
            return aniso == k.aniso &&
                    Objects.equals(path, k.path) &&
                    wrap == k.wrap &&
                    min == k.min &&
                    mag == k.mag;
        }
    }
}