// FILE: Scripts/player/PlayerUI.js
// Author: Calista Verner
"use strict";

function _isObj(x) { return x && typeof x === "object"; }

class PlayerUI {
    constructor(playerOrCtx, cfgMaybe) {
        const isPlayer = !!(playerOrCtx && typeof playerOrCtx === "object" && (playerOrCtx.cfg || playerOrCtx.getCfg || playerOrCtx.ctx));
        this.player = isPlayer ? playerOrCtx : null;

        this.ctx = isPlayer ? (this.player && this.player.ctx) : playerOrCtx;
        this.cfg = isPlayer ? ((this.player && this.player.cfg && this.player.cfg.ui) || {}) : (cfgMaybe || {});

        // HtmlView из @builtin/Hud (имеет patch/render/destroy)
        this.view = null;

        this._last = { hp: null, armor: null, ammo: null, reserve: null, questTitle: null, questText: null };
        this._uiPath = (this.cfg && this.cfg.file) ? String(this.cfg.file) : "ui/player_hud.html";
    }

    _readState() {
        const st = (this.player && this.player.state) ? this.player.state : null;

        return {
            player: {
                hp: (st && st.hp != null) ? st.hp : 100,
                armor: (st && st.armor != null) ? st.armor : 50,
            },
            weapon: {
                ammo: (st && st.ammo != null) ? st.ammo : 30,
                reserve: (st && st.reserve != null) ? st.reserve : 120,
            },
            quest: {
                title: (st && st.questTitle) ? String(st.questTitle) : "Quest",
                text: (st && st.questText) ? String(st.questText) : "Find the exit",
            }
        };
    }

    create() {

        // Грузим HTML из файла и создаём view (с шаблоном {{...}})
        const model = this._readState();

        // На всякий случай — проверим assets доступность, чтобы ошибка была понятной
        const assets = engine.assets && engine.assets();
        if (!assets || typeof assets.readText !== "function") {
            throw new Error("[PlayerUI] engine.assets().readText is required to load UI file");
        }

        // Используем viewHtml с шаблоном из файла (чтобы patch работал без ручного setTextById)
        const html = assets.readText(this._uiPath);
        if (!html) throw new Error("[PlayerUI] UI file is empty or not found: " + this._uiPath);

        this.view = HUD.viewHtml({
            anchor: "topLeft",
            pivot: "topLeft",
            offset: { x: 0, y: 0 },
            html,
            model
        });

        // начальный render уже сделан внутри viewHtml()
        this._remember(model);
        return this;
    }

    _remember(m) {
        this._last.hp = m.player.hp;
        this._last.armor = m.player.armor;
        this._last.ammo = m.weapon.ammo;
        this._last.reserve = m.weapon.reserve;
        this._last.questTitle = m.quest.title;
        this._last.questText = m.quest.text;
    }

    refresh(force = false) {
        if (!this.view) return;

        const m = this._readState();

        // AAA: обновляем только когда реально изменилось
        const changed =
            force ||
            this._last.hp !== m.player.hp ||
            this._last.armor !== m.player.armor ||
            this._last.ammo !== m.weapon.ammo ||
            this._last.reserve !== m.weapon.reserve ||
            this._last.questTitle !== m.quest.title ||
            this._last.questText !== m.quest.text;

        if (!changed) return;

        // patch -> перерендер шаблона (пока нет точечного setTextById на Java стороне)
        this.view.model(m).render(true);
        this._remember(m);
    }

    destroy() {
        if (this.view) {
            try { this.view.destroy(); }
            catch (e) {
                if (typeof LOG !== "undefined" && LOG && LOG.error) LOG.error("[ui] view.destroy failed: " + (e && (e.stack || e.message) ? (e.stack || e.message) : e));
                else throw e;
            }
            this.view = null;
        }
    }
}

module.exports = PlayerUI;