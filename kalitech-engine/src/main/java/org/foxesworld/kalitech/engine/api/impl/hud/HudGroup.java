package org.foxesworld.kalitech.engine.api.impl.hud;

final class HudGroup extends HudElement {

    HudGroup(int id) {
        super(id, "hud:group:" + id);
        this.w = 0f;
        this.h = 0f;
    }
}