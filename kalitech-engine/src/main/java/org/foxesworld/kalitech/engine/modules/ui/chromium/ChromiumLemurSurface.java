package org.foxesworld.kalitech.engine.modules.ui.chromium;

import com.jme3.math.Vector3f;
import com.jme3.texture.Texture;
import com.simsilica.lemur.Panel;
import com.simsilica.lemur.component.QuadBackgroundComponent;

import java.util.Objects;

/**
 * Lemur surface that displays Chromium texture as a background.
 */
public final class ChromiumLemurSurface {

    private final Panel panel;
    private final QuadBackgroundComponent bg;

    public ChromiumLemurSurface(float width, float height, Texture texture) {
        Objects.requireNonNull(texture, "texture");

        this.panel = new Panel();
        this.panel.setLocalTranslation(0, height, 0);
        this.panel.setPreferredSize(new Vector3f(width, height, 0));

        this.bg = new QuadBackgroundComponent(texture);
        this.panel.setBackground(bg);
    }

    public Panel panel() {
        return panel;
    }

    public void setTexture(Texture texture) {
        bg.setTexture(texture);
    }

    public void resize(float width, float height) {
        panel.setLocalTranslation(0, height, 0);
        panel.setPreferredSize(new Vector3f(width, height, 0));
    }
}