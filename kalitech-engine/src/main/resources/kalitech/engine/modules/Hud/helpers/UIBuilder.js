// FILE: resources/kalitech/builtin/helpers/hud/UIBuilder.js
"use strict";

class UIBuilder {
    constructor(layer) {
        this.layer = layer;
        this.panel = null;
    }

    panel(id, cfg) {
        const p = this.layer.panel(Object.assign({}, cfg || {}, {id}));
        this.panel = p;
        return this;
    }

    use(panelOrId) {
        if (!panelOrId) throw new Error("[HUD] ui().use requires panel or id");
        const p = (typeof panelOrId === "string" || typeof panelOrId === "number")
            ? this.layer.get(panelOrId)
            : panelOrId;
        if (!p || p.kind !== "panel") throw new Error("[HUD] ui().use expects panel");
        this.panel = p;
        return this;
    }

    stack(id, text, cfg) {
        if (!this.panel || this.panel.kind !== "panel") {
            throw new Error("[HUD] ui().stack requires active panel. Call ui().panel(...) first.");
        }
        this.panel.stack(id, text, cfg);
        return this;
    }

    done() {
        return this.panel;
    }
}

module.exports = {UIBuilder};
