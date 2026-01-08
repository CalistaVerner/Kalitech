// FILE: resources/kalitech/engine/bootstrap/DataConfig.js
"use strict";

const {isPlainObj} = require("./Util.js");
const {readTextAsset} = require("./Assets.js");

function buildDataConfigApi(engine, cfgSection) {
    const cfg = isPlainObj(cfgSection) ? cfgSection : Object.create(null);

    const cacheText = Object.create(null);
    const cacheJson = Object.create(null);

    function list() {
        return Object.keys(cfg);
    }

    function pathOf(name) {
        const k = String(name || "");
        const e = cfg[k];
        if (!e) return "";
        if (typeof e === "string") return e;
        if (e && typeof e.path === "string") return e.path;
        return "";
    }

    function readText(name) {
        const p = pathOf(name);
        if (!p) return null;
        if (cacheText[p] != null) return cacheText[p];
        const txt = readTextAsset(engine, p);
        cacheText[p] = (txt != null) ? String(txt) : null;
        return cacheText[p];
    }

    function readJson(name) {
        const p = pathOf(name);
        if (!p) return null;
        if (cacheJson[p] != null) return cacheJson[p];
        const txt = readText(name);
        if (!txt) {
            cacheJson[p] = null;
            return null;
        }
        try {
            const obj = JSON.parse(String(txt));
            cacheJson[p] = obj;
            return obj;
        } catch (_) {
            cacheJson[p] = null;
            return null;
        }
    }

    function reload(name) {
        const p = pathOf(name);
        if (!p) return false;
        delete cacheText[p];
        delete cacheJson[p];
        return true;
    }

    function reloadAll() {
        const ks = list();
        for (let i = 0; i < ks.length; i++) reload(ks[i]);
        return true;
    }

    function get(name) {
        const k = String(name || "");
        if (!cfg[k]) return null;
        return {
            name: k,
            get path() {
                return pathOf(k);
            },
            text: function () {
                return readText(k);
            },
            json: function () {
                return readJson(k);
            },
            reload: function () {
                return reload(k);
            }
        };
    }

    const api = {list, get, pathOf, readText, readJson, reload, reloadAll};

    const keys = Object.keys(cfg);
    for (let i = 0; i < keys.length; i++) {
        const k = keys[i];
        api[k] = get(k);
    }

    return api;
}

module.exports = {buildDataConfigApi};