/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.jme3.texture.Texture$MagFilter
 *  com.jme3.texture.Texture$MinFilter
 *  com.jme3.texture.Texture$WrapMode
 */
package org.foxesworld.kalitech.engine.modules.material;

import com.jme3.texture.Texture;
import java.util.Objects;
import java.util.function.Consumer;

public final class MaterialTypes {
    private MaterialTypes() {
    }

    public record TextureKey(String path, Texture.WrapMode wrap, Texture.MinFilter min, Texture.MagFilter mag, int aniso, int hash) {
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
            return this.hash;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof TextureKey)) {
                return false;
            }
            TextureKey k = (TextureKey)o;
            return this.aniso == k.aniso && Objects.equals(this.path, k.path) && this.wrap == k.wrap && this.min == k.min && this.mag == k.mag;
        }
    }

    public record RenderThreadScheduler(Consumer<Runnable> enqueue) {
        public void onRenderThread(Runnable r) {
            this.enqueue.accept(r);
        }
    }

    public record ParsedTex(String path, Texture.WrapMode wrap) {
    }

    public record TextureDesc(String texture, Texture.WrapMode wrap, Texture.MinFilter minFilter, Texture.MagFilter magFilter, int anisotropy, TileWorld tileWorld) {
    }

    public record TileWorld(float x, float z) {
    }
}

