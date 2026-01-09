"use strict";

const {num, vec3Obj} = require("./PhysicsMath.js");

module.exports = Object.freeze({
    box: (halfExtents) => ({type: "box", halfExtents: vec3Obj(halfExtents, 0.5, 0.5, 0.5)}),
    sphere: (radius) => ({type: "sphere", radius: num(radius, 0.5)}),
    capsule: (radius, height) => ({type: "capsule", radius: num(radius, 0.35), height: num(height, 1.8)}),
    cylinder: (radius, height) => ({type: "cylinder", radius: num(radius, 0.5), height: num(height, 1.0)}),
    mesh: () => ({type: "mesh"}),
    dynamicMesh: () => ({type: "dynamicMesh"}),
});