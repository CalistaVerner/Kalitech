local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local Arrays = luaRuntime.array
local Tables = luaRuntime.table
local lua_require_result_0 = require("./MeshMath.lua")
isObj = lua_require_result_0.isObj
num = lua_require_result_0.num
normalizePos = lua_require_result_0.normalizePos
function normalizeCfg(self, cfg)
    local lua_isObj_result_1
    if isObj(_G, cfg) then
        lua_isObj_result_1 = cfg
    else
        lua_isObj_result_1 = {}
    end
    cfg = lua_isObj_result_1
    local out = Tables:merge({}, cfg)
    if out.type ~= nil then
        out.type = tostring(out.type)
    end
    if out.name ~= nil then
        out.name = tostring(out.name)
    end
    if out.path == nil then
        if out.model ~= nil then
            out.path = out.model
        elseif out.asset ~= nil then
            out.path = out.asset
        elseif out.url ~= nil then
            out.path = out.url
        end
    end
    if out.path ~= nil then
        out.path = tostring(out.path)
    end
    local lua_temp_5
    if out.pos ~= nil then
        lua_temp_5 = out.pos
    else
        local lua_temp_4
        if out.position ~= nil then
            lua_temp_4 = out.position
        else
            local lua_temp_3
            if out.loc ~= nil then
                lua_temp_3 = out.loc
            else
                local lua_temp_2
                if out.location ~= nil then
                    lua_temp_2 = out.location
                else
                    lua_temp_2 = nil
                end
                lua_temp_3 = lua_temp_2
            end
            lua_temp_4 = lua_temp_3
        end
        lua_temp_5 = lua_temp_4
    end
    local p = lua_temp_5
    local posN = normalizePos(_G, p)
    if posN ~= nil then
        out.pos = posN
    end
    if out.radius == nil and out.r ~= nil then
        out.radius = out.r
    end
    if out.height == nil and out.h ~= nil then
        out.height = out.h
    end
    if out.radius ~= nil then
        out.radius = num(_G, out.radius, out.radius)
    end
    if out.height ~= nil then
        out.height = num(_G, out.height, out.height)
    end
    if out.physics ~= nil and KTypeOf(out.physics) == "number" then
        out.physics = {mass = out.physics}
    end
    return out
end
function withType(self, lua_type, cfg)
    local c = normalizeCfg(_G, cfg)
    c.type = tostring(lua_type)
    return c
end
function unshadedColor(self, rgba)
    local lua_Array_isArray_result_6
    if Arrays:isArray(rgba) then
        lua_Array_isArray_result_6 = rgba
    else
        lua_Array_isArray_result_6 = {1, 1, 1, 1}
    end
    local c = lua_Array_isArray_result_6
    return {def = "Common/MatDefs/Misc/Unshaded.j3md", params = {Color = c}}
end
function physics(self, mass, opts)
    local o = opts or ({})
    local lua_temp_7
    if mass ~= nil then
        lua_temp_7 = mass
    else
        lua_temp_7 = 0
    end
    local p = {mass = lua_temp_7}
    if o.enabled ~= nil then
        p.enabled = not not o.enabled
    end
    if o.lockRotation ~= nil then
        p.lockRotation = not not o.lockRotation
    end
    if o.kinematic ~= nil then
        p.kinematic = not not o.kinematic
    end
    if o.friction ~= nil then
        p.friction = o.friction
    end
    if o.restitution ~= nil then
        p.restitution = o.restitution
    end
    if o.damping ~= nil then
        p.damping = o.damping
    end
    if o.collider ~= nil then
        p.collider = o.collider
    end
    return p
end
M = KObject:freeze({normalizeCfg = normalizeCfg, withType = withType, unshadedColor = unshadedColor, physics = physics})

return M
