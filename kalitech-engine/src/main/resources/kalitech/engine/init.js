// FILE: resources/kalitech/builtin/bootstrap.js
// Author: Calista Verner
"use strict";


const Boot = require("@builtin/bootstrap/Bootstrap");

const boot = Boot.createDefault().init();

//const ControllersFactory = require("@builtin/controllers/Controllers.js");
//const ENGINE = controllersFactory(engine, K);

module.exports = {
    config: boot.config,
    attachEngine: boot.attachEngine.bind(boot),
    whenEngine: boot.whenEngine.bind(boot),
    whenEngineOnce: boot.whenEngineOnce.bind(boot),
    safeJson: Boot.safeJson
};