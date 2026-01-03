// FILE: org/foxesworld/kalitech/engine/api/impl/hud/HudGroup.java
package org.foxesworld.kalitech.engine.api.impl.hud;

class HudGroup extends HudElement {

    HudGroup(int id) {
        super(id, "hud:group:" + id);
        this.kind = Kind.GROUP;
        this.w = 0f;
        this.h = 0f;
    }
}