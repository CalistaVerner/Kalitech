package org.foxesworld.kalitech.engine.modules.ui.chromium;

import com.jme3.texture.Image;
import com.jme3.texture.Texture2D;
import com.jme3.util.BufferUtils;

import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * Uploads Chromium RGBA buffer to a JME Texture2D.
 * Call updateIfDirty() only from the JME render thread.
 */
public final class ChromiumTextureBridge {

    private final ChromiumView view;

    private Texture2D texture;
    private Image image;

    private ByteBuffer gpuBuffer;
    private int w;
    private int h;

    // Debug: show a test pattern until the first real Chromium frame arrives.
    private boolean debugUntilFirstFrame = true;
    private boolean hasRealFrame = false;
    private int updateTicks = 0;

    // Debug frequency (in updateIfDirty() calls)
    private int debugEveryTicks = 15;

    public ChromiumTextureBridge(ChromiumView view) {
        this.view = Objects.requireNonNull(view, "view");
        this.w = Math.max(1, view.width());
        this.h = Math.max(1, view.height());
        allocate(w, h);
    }

    public Texture2D texture() {
        return texture;
    }

    /**
     * Enable/disable debug pattern.
     * When enabled, pattern is shown until the first real Chromium frame arrives.
     */
    public void setDebugUntilFirstFrame(boolean enabled) {
        this.debugUntilFirstFrame = enabled;
        if (!enabled) {
            this.hasRealFrame = true;
        }
    }

    /**
     * Set how often (in ticks) to refresh debug pattern when no frames are available.
     */
    public void setDebugEveryTicks(int ticks) {
        this.debugEveryTicks = Math.max(1, ticks);
    }

    public void updateIfDirty() {
        updateTicks++;

        int nw = Math.max(1, view.width());
        int nh = Math.max(1, view.height());
        if (nw != w || nh != h) {
            w = nw;
            h = nh;
            allocate(w, h);
        }

        int bytes = w * h * 4;

        if (view.isDirty()) {
            // Atomic copy + clearDirty inside ChromiumView lock
            if (view.consumeFrameTo(gpuBuffer, bytes)) {
                hasRealFrame = true;
                image.setUpdateNeeded();
            }
            return;
        }

        // No real frames yet -> show debug test pattern to verify quad/material/GUI path.
        if (debugUntilFirstFrame && !hasRealFrame && (updateTicks % debugEveryTicks) == 0) {
            fillDebugPattern(gpuBuffer, w, h, updateTicks);
            image.setUpdateNeeded();
        }
    }

    private void allocate(int w, int h) {
        gpuBuffer = BufferUtils.createByteBuffer(w * h * 4);
        image = new Image(Image.Format.RGBA8, w, h, gpuBuffer);
        texture = new Texture2D(image);

        // Ensure first visible content if Chromium is slow to paint.
        if (debugUntilFirstFrame && !hasRealFrame) {
            fillDebugPattern(gpuBuffer, w, h, 0);
            image.setUpdateNeeded();
        }
    }

    private static void fillDebugPattern(ByteBuffer buf, int w, int h, int tick) {
        // Deterministic checker + moving stripe, alpha forced to 255 to avoid transparency issues.
        int stripeX = (tick * 4) % Math.max(1, w);
        int stripeW = Math.max(2, w / 32);

        int pixels = w * h;
        for (int i = 0; i < pixels; i++) {
            int x = i % w;
            int y = i / w;

            boolean checker = (((x >> 5) ^ (y >> 5)) & 1) == 0;
            boolean stripe = x >= stripeX && x < (stripeX + stripeW);

            int o = i * 4;

            byte r;
            byte g;
            byte b;
            byte a = (byte) 255;

            if (stripe) {
                // Bright green stripe
                r = 0;
                g = (byte) 255;
                b = 0;
            } else if (checker) {
                // Magenta
                r = (byte) 255;
                g = 0;
                b = (byte) 255;
            } else {
                // Dark gray
                r = 32;
                g = 32;
                b = 32;
            }

            buf.put(o, r);
            buf.put(o + 1, g);
            buf.put(o + 2, b);
            buf.put(o + 3, a);
        }
    }
}