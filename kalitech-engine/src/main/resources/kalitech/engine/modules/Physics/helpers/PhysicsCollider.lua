local M = {}
local lua_require_result_0 = require("./PhysicsMath.lua")
num = lua_require_result_0.num
vec3Obj = lua_require_result_0.vec3Obj
M = KObject:freeze({
    box = function(lua_, halfExtents) return {
        type = "box",
        halfExtents = vec3Obj(
            _G,
            halfExtents,
            0.5,
            0.5,
            0.5
        )
    } end,
    sphere = function(lua_, radius) return {
        type = "sphere",
        radius = num(_G, radius, 0.5)
    } end,
    capsule = function(lua_, radius, height) return {
        type = "capsule",
        radius = num(_G, radius, 0.35),
        height = num(_G, height, 1.8)
    } end,
    cylinder = function(lua_, radius, height) return {
        type = "cylinder",
        radius = num(_G, radius, 0.5),
        height = num(_G, height, 1)
    } end,
    mesh = function() return {type = "mesh"} end,
    dynamicMesh = function() return {type = "dynamicMesh"} end
})

return M
