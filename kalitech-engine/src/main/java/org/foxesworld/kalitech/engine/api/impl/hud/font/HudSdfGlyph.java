// FILE: org/foxesworld/kalitech/engine/api/impl/hud/HudSdfGlyph.java
package org.foxesworld.kalitech.engine.api.impl.hud.font;

public final class HudSdfGlyph {
    final int codepoint;

    // glyph quad size in atlas pixels (bitmap/SDF space)
    public final int w;
    public final int h;

    // glyph offset from pen position to top-left in pixels (bitmap/SDF space)
    public final int xOff;
    public final int yOff;

    // advance after drawing glyph in pixels (bitmap/SDF space)
    public final int xAdvance;

    // UV in atlas [0..1]
    public final float u0;
    public final float v0;
    public final float u1;
    public final float v1;

    HudSdfGlyph(int codepoint, int w, int h, int xOff, int yOff, int xAdvance,
                float u0, float v0, float u1, float v1) {
        this.codepoint = codepoint;
        this.w = w;
        this.h = h;
        this.xOff = xOff;
        this.yOff = yOff;
        this.xAdvance = xAdvance;
        this.u0 = u0;
        this.v0 = v0;
        this.u1 = u1;
        this.v1 = v1;
    }
}
