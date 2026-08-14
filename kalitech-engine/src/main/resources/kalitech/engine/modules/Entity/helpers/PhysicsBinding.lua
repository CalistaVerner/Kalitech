local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local Numbers = luaRuntime.number
local Classes = luaRuntime.class
local lua_require_result_0 = require("./EntUtil.lua")
req = lua_require_result_0.req
subsystem = lua_require_result_0.subsystem
deepMerge = lua_require_result_0.deepMerge
local lua_require_result_1 = require("./IdExtractor.lua")
idOf = lua_require_result_1.idOf
surfaceId = lua_require_result_1.surfaceId
PhysicsBinding = Classes:create()
PhysicsBinding.name = "PhysicsBinding"
function PhysicsBinding.prototype.lua_constructor(self, engine)
    self.engine = engine
end
function PhysicsBinding.prototype.resolveBodyIdBySurface(self, surfaceHandleOrId)
    local sid = surfaceId(_G, surfaceHandleOrId)
    if not sid then
        return 0
    end
    local s = subsystem(_G, self.engine, "surface")
    if KTypeOf(s.attachedBody) == "function" then
        local bid = s:attachedBody(sid)
        if KTypeOf(bid) == "number" and Numbers:isFinite(Numbers:coerce(bid)) and bid > 0 then
            return bit32.bor(bid, 0)
        end
    end
    local p = subsystem(_G, self.engine, "physics")
    if KTypeOf(p.bodyOfSurface) == "function" then
        local bid = p:bodyOfSurface(sid)
        if KTypeOf(bid) == "number" and Numbers:isFinite(Numbers:coerce(bid)) and bid > 0 then
            return bit32.bor(bid, 0)
        end
    end
    return 0
end
function PhysicsBinding.prototype.deriveColliderFromSurfaceCfg(self, surfaceCfg)
    if not surfaceCfg or not surfaceCfg.type then
        return nil
    end
    local t = tostring(surfaceCfg.type)
    if t == "capsule" then
        local lua_temp_2
        if surfaceCfg.radius ~= nil then
            lua_temp_2 = surfaceCfg.radius
        else
            lua_temp_2 = 0.35
        end
        local lua_temp_3
        if surfaceCfg.height ~= nil then
            lua_temp_3 = surfaceCfg.height
        else
            lua_temp_3 = 1.8
        end
        return {type = "capsule", radius = lua_temp_2, height = lua_temp_3}
    end
    if t == "sphere" then
        local lua_temp_4
        if surfaceCfg.radius ~= nil then
            lua_temp_4 = surfaceCfg.radius
        else
            lua_temp_4 = 0.5
        end
        return {type = "sphere", radius = lua_temp_4}
    end
    if t == "box" then
        local lua_temp_5
        if surfaceCfg.size ~= nil then
            lua_temp_5 = surfaceCfg.size
        else
            lua_temp_5 = 1
        end
        return {type = "box", size = lua_temp_5}
    end
    return nil
end
function PhysicsBinding.prototype.createBody(self, bodyDefaults, bodyCfg, surfaceHandle, surfaceCfg)
    req(
        _G,
        bodyCfg and KTypeOf(bodyCfg) == "table",
        "[ENT] createBody: bodyCfg object is required"
    )
    local p = subsystem(_G, self.engine, "physics")
    local bCfg = deepMerge(
        _G,
        deepMerge(_G, {}, bodyDefaults or ({})),
        bodyCfg
    )
    if not bCfg.surface and surfaceHandle then
        bCfg.surface = surfaceHandle
    end
    if not bCfg.collider then
        local inferred = self:deriveColliderFromSurfaceCfg(surfaceCfg)
        if inferred then
            bCfg.collider = inferred
        end
    end
    local bodyHandle = p:body(bCfg)
    local bid = idOf(_G, bodyHandle, "body")
    return {body = bodyHandle, bodyId = bid}
end
M = {PhysicsBinding = PhysicsBinding}

return M
