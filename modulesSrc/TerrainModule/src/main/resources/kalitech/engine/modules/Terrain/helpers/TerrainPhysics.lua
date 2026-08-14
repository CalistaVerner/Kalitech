local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local Tables = luaRuntime.table
local Classes = luaRuntime.class
local Error = luaRuntime.Error
local lua_require_result_0 = require("./TerrainTypes.lua")
isObj = lua_require_result_0.isObj
function surfaceIdOf(self, h)
    if KTypeOf(h) == "number" then
        return bit32.bor(h, 0)
    end
    if not h then
        return 0
    end
    if KTypeOf(h.id) == "function" then
        return bit32.bor(
            h:id(),
            0
        )
    end
    if KTypeOf(h.id) == "number" then
        return bit32.bor(h.id, 0)
    end
    if KTypeOf(h.surfaceId) == "number" then
        return bit32.bor(h.surfaceId, 0)
    end
    return 0
end
function bodyIdOf(self, h)
    if KTypeOf(h) == "number" then
        return bit32.bor(h, 0)
    end
    if not h then
        return 0
    end
    if KTypeOf(h.id) == "function" then
        return bit32.bor(
            h:id(),
            0
        )
    end
    if KTypeOf(h.id) == "number" then
        return bit32.bor(h.id, 0)
    end
    if KTypeOf(h.bodyId) == "number" then
        return bit32.bor(h.bodyId, 0)
    end
    return 0
end
TerrainPhysics = Classes:create()
TerrainPhysics.name = "TerrainPhysics"
function TerrainPhysics.prototype.lua_constructor(self, engine)
    self.engine = engine
end
function TerrainPhysics.prototype.resolveBodyId(self, surfaceHandleOrId, maybeBodyHandleOrId)
    local sid = surfaceIdOf(_G, surfaceHandleOrId)
    if sid <= 0 then
        return 0
    end
    local E = self.engine
    if E.surface and KTypeOf(E.surface) == "function" then
        local s = E:surface()
        if s and KTypeOf(s.attachedBody) == "function" then
            local bid = bodyIdOf(
                _G,
                s:attachedBody(sid)
            )
            if bid > 0 then
                return bid
            end
        end
    end
    if E.physics and KTypeOf(E.physics) == "function" then
        local p = E:physics()
        if p and KTypeOf(p.bodyOfSurface) == "function" then
            local bid = bodyIdOf(
                _G,
                p:bodyOfSurface(sid)
            )
            if bid > 0 then
                return bid
            end
        end
    end
    local bid = bodyIdOf(_G, maybeBodyHandleOrId)
    local lua_temp_1
    if bid > 0 then
        lua_temp_1 = bid
    else
        lua_temp_1 = 0
    end
    return lua_temp_1
end
function TerrainPhysics.prototype.ensureStaticBody(self, surfaceHandleOrId, physCfg, defaultColliderType)
    local sid = surfaceIdOf(_G, surfaceHandleOrId)
    if sid <= 0 then
        return {bodyId = 0, bodyHandle = nil}
    end
    local existing = self:resolveBodyId(sid, nil)
    if existing > 0 then
        return {bodyId = existing, bodyHandle = nil}
    end
    local base = {surface = sid, mass = 0, kinematic = true, collider = {type = defaultColliderType or "mesh"}}
    local lua_isObj_result_2
    if isObj(_G, physCfg) then
        lua_isObj_result_2 = Tables:merge(base, physCfg)
    else
        lua_isObj_result_2 = base
    end
    local cfg = lua_isObj_result_2
    local bodyHandle = nil
    if KTypeOf(ENGINE.physics) ~= "nil" and ENGINE.physics and KTypeOf(ENGINE.physics.body) == "function" then
        bodyHandle = ENGINE.physics:body(cfg)
    else
        local E = self.engine
        local lua_temp_3
        if E.physics and KTypeOf(E.physics) == "function" then
            lua_temp_3 = E:physics()
        else
            lua_temp_3 = nil
        end
        local p = lua_temp_3
        if not p or KTypeOf(p.body) ~= "function" then
            error(
                Classes:construct(Error, "[TERR] physics.body(cfg) is required"),
                0
            )
        end
        bodyHandle = p:body(cfg)
    end
    local bodyId = self:resolveBodyId(sid, bodyHandle)
    return {bodyId = bodyId, bodyHandle = bodyHandle}
end
function TerrainPhysics.prototype.withBody(self, terrNative, surface, physCfg, defaultColliderType)
    if physCfg == nil then
        return surface
    end
    local bodyHandle = nil
    if terrNative and KTypeOf(terrNative.physics) == "function" then
        bodyHandle = terrNative:physics(surface, physCfg)
    end
    local sid = surfaceIdOf(_G, surface)
    local bodyId = self:resolveBodyId(sid, bodyHandle)
    if bodyId <= 0 then
        local made = self:ensureStaticBody(surface, physCfg, defaultColliderType or "mesh")
        bodyId = made.bodyId
        if not bodyHandle then
            bodyHandle = made.bodyHandle
        end
    end
    local lua_temp_4
    if bodyId > 0 and KTypeOf(ENGINE.physics) ~= "nil" and ENGINE.physics and KTypeOf(ENGINE.physics.ref) == "function" then
        lua_temp_4 = ENGINE.physics:ref(bodyId)
    else
        lua_temp_4 = nil
    end
    local bodyRef = lua_temp_4
    return KObject:freeze({surface = surface, bodyId = bodyId, body = bodyRef, handle = bodyHandle})
end
M = {TerrainPhysics = TerrainPhysics, surfaceIdOf = surfaceIdOf, bodyIdOf = bodyIdOf}

return M
