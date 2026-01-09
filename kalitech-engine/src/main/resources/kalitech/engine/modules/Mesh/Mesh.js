// FILE: resources/kalitech/engine/modules/Mesh/Mesh.js
// Author: Calista Verner
"use strict";

const META = Object.freeze({
    moduleId: "mesh",
    globalName: "MESH",
    version: "1.0.2",
    engineMin: "0.1.5",
    description: "RootKit wrapper over ENGINE.mesh(): create() returns object-mesh with physics methods via ENGINE.physics()"
});

const MeshOrchestrator = require("./MeshOrchestrator.js");

function create(ENGINE /*, K */) {
    if (!ENGINE) throw new Error("[MESH] ENGINE is required");
    const orch = new MeshOrchestrator(ENGINE);
    return orch.decorateMeshApi();
}

module.exports = create;
module.exports.META = META;