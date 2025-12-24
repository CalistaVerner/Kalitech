// FILE: Scripts/main.js
// Author: Calista Verner
//
// World entrypoint (compatibility shim).
// Keeps engine configuration stable while internal structure evolves.
//
// Actual content lives in:
//   - Scripts/world/main.world.js
//   - Scripts/world/main.bootstrap.js

module.exports.world = require("./world/main.world.js").world;

module.exports.bootstrap = require("./world/main.bootstrap.js").bootstrap;
