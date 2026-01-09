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
        const p = (typeof panelOrId === "string") ? this.layer.get(panelOrId) : panelOrId;
        this.panel = p || null;
        return this;
    }

    text(id, text, cfg) {
        if (this.panel && this.panel.kind === "panel") this.panel.text(id, text, cfg);
        else this.layer.text(Object.assign({}, cfg || {}, {id, text}));
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