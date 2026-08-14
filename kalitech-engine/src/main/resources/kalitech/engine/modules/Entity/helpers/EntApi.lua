local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local LuaStringTrim = luaRuntime.LuaStringTrim
local LuaClass = luaRuntime.LuaClass
local LuaConstruct = luaRuntime.LuaConstruct
local Error = luaRuntime.Error
local LuaTableKeys = luaRuntime.LuaTableKeys
local LuaTableRemove = luaRuntime.LuaTableRemove
local lua_require_result_0 = require("./EntUtil.lua")
req = lua_require_result_0.req
vec3 = lua_require_result_0.vec3
deepMerge = lua_require_result_0.deepMerge
subsystem = lua_require_result_0.subsystem
local lua_require_result_1 = require("./IdExtractor.lua")
idOf = lua_require_result_1.idOf
local lua_require_result_2 = require("./PhysicsBinding.lua")
PhysicsBinding = lua_require_result_2.PhysicsBinding
local lua_require_result_3 = require("./EntityHandle.lua")
EntityHandle = lua_require_result_3.EntityHandle
local lua_require_result_4 = require("./EntBuilder.lua")
EntBuilder = lua_require_result_4.EntBuilder
local lua_require_result_5 = require("./EntityCore.lua")
EntityCore = lua_require_result_5.EntityCore
local lua_require_result_6 = require("./BodyAccessResolver.lua")
resolveBodyAccess = lua_require_result_6.resolveBodyAccess
function isUuidString(self, s)
    if KTypeOf(s) ~= "string" then
        return false
    end
    local x = LuaStringTrim(s)
    return #x >= 32 and (string.find(x, "-", nil, true) or 0) - 1 > 0
end
function safeCall(self, fn)
    do
        pcall(function()
            fn(_G)
        end)
    end
end
EntApi = LuaClass()
EntApi.name = "EntApi"
function EntApi.prototype.lua_constructor(self, engine, K)
    self.engine = engine
    self.K = K or (_G.__kalitech or KObject:create(nil))
    req(_G, engine, "[ENT] engine is required")
    subsystem(_G, engine, "entity")
    subsystem(_G, engine, "mesh")
    subsystem(_G, engine, "surface")
    subsystem(_G, engine, "physics")
    req(
        _G,
        engine.log and KTypeOf(engine.log) == "function",
        "[ENT] engine.log() is required"
    )
    self._log = engine:log()
    req(_G, self._log and self._log.info and self._log.warn and self._log.error, "[ENT] engine.log() must provide info/warn/error")
    self._physBind = LuaConstruct(PhysicsBinding, engine)
    self._presets = KObject:create(nil)
    self._presets.capsule = {name = "entity", surface = {
        type = "capsule",
        name = "entity.capsule",
        radius = 0.35,
        height = 1.8,
        pos = {0, 3, 0},
        attach = true
    }, attachSurface = true}
    self._presets.box = {name = "entity", surface = {
        type = "box",
        name = "entity.box",
        size = 1,
        pos = {0, 3, 0},
        attach = true
    }, attachSurface = true}
    self._presets.sphere = {name = "entity", surface = {
        type = "sphere",
        name = "entity.sphere",
        radius = 0.5,
        pos = {0, 3, 0},
        attach = true
    }, attachSurface = true}
    self._bodyDefaults = {
        mass = 1,
        friction = 0.9,
        restitution = 0,
        damping = {linear = 0.15, angular = 0.95},
        lockRotation = false
    }
end
function EntApi.prototype.preset(self, name, cfg)
    local n = tostring(name or "")
    if not n then
        error(
            LuaConstruct(Error, "[ENT] preset(name,cfg): name is required"),
            0
        )
    end
    if not cfg or KTypeOf(cfg) ~= "table" then
        error(
            LuaConstruct(Error, "[ENT] preset(name,cfg): cfg object is required"),
            0
        )
    end
    self._presets[n] = deepMerge(
        _G,
        deepMerge(_G, {}, self._presets[n] or ({})),
        cfg
    )
    return self
end
function EntApi.prototype.bodyDefaults(self, cfg)
    self._bodyDefaults = deepMerge(
        _G,
        deepMerge(_G, {}, self._bodyDefaults),
        cfg or ({})
    )
    return self
end
function EntApi.prototype.presets(self)
    return LuaTableKeys(self._presets)
end
EntApi.prototype["$"] = function(self, presetName)
    local lua_EntBuilder_8 = EntBuilder
    local lua_presetName_7
    if presetName then
        lua_presetName_7 = tostring(presetName)
    else
        lua_presetName_7 = ""
    end
    return LuaConstruct(lua_EntBuilder_8, self, lua_presetName_7)
end
EntApi.prototype["player$"] = function(self, cfg)
    return self["$"](self, "player"):merge(cfg)
end
EntApi.prototype["capsule$"] = function(self, cfg)
    return self["$"](self, "capsule"):merge(cfg)
end
EntApi.prototype["box$"] = function(self, cfg)
    return self["$"](self, "box"):merge(cfg)
end
EntApi.prototype["sphere$"] = function(self, cfg)
    return self["$"](self, "sphere"):merge(cfg)
end
function EntApi.prototype.create(self, cfg)
    local lua_temp_9
    if cfg and KTypeOf(cfg) == "table" then
        lua_temp_9 = cfg
    else
        lua_temp_9 = {}
    end
    cfg = lua_temp_9
    local lua_debug = not not cfg.debug
    local engine = self.engine
    local ent = subsystem(_G, engine, "entity")
    local mesh = subsystem(_G, engine, "mesh")
    local surfApi = subsystem(_G, engine, "surface")
    local phys = subsystem(_G, engine, "physics")
    local ctx = {
        uuid = "",
        surface = nil,
        body = nil,
        surfaceId = 0,
        bodyId = 0,
        _destroyers = {}
    }
    local createdUuid = ""
    local createdSurfaceId = 0
    local createdBodyId = 0
    do
        local function lua_catch(e)
            safeCall(
                _G,
                function()
                    if bit32.bor(createdBodyId, 0) > 0 and KTypeOf(phys.remove) == "function" then
                        phys:remove(bit32.bor(createdBodyId, 0))
                    end
                end
            )
            safeCall(
                _G,
                function()
                    if bit32.bor(createdSurfaceId, 0) > 0 and KTypeOf(surfApi.drop) == "function" then
                        surfApi:drop(
                            bit32.bor(createdSurfaceId, 0),
                            true
                        )
                    end
                end
            )
            safeCall(
                _G,
                function()
                    if createdUuid and KTypeOf(ent.destroy) == "function" then
                        ent:destroy(createdUuid)
                    end
                end
            )
            error(e, 0)
        end
        local lua_try, lua_hasReturned, lua_returnValue = pcall(function()
            local name = tostring(cfg.name or "entity")
            local created = ent:create(name)
            if KTypeOf(created) ~= "string" or not isUuidString(_G, created) then
                error(
                    LuaConstruct(
                        Error,
                        "[ENT] engine.entity().create() must return UUID string, got: " .. tostring(created)
                    ),
                    0
                )
            end
            createdUuid = LuaStringTrim(created)
            ctx.uuid = createdUuid
            local surfCfg = cfg.surface or nil
            local bodyCfg = cfg.body or nil
            local surfaceHadPhysics = false
            if surfCfg then
                local sCfg = deepMerge(_G, {}, surfCfg)
                if sCfg.pos ~= nil then
                    sCfg.pos = vec3(
                        _G,
                        sCfg.pos,
                        0,
                        0,
                        0
                    )
                end
                if sCfg.physics ~= nil then
                    surfaceHadPhysics = true
                    if bodyCfg then
                        LuaTableRemove(sCfg, "physics")
                        surfaceHadPhysics = false
                    end
                end
                ctx.surface = mesh:create(sCfg)
                ctx.surfaceId = bit32.bor(
                    idOf(_G, ctx.surface, "surface"),
                    0
                )
                createdSurfaceId = bit32.bor(ctx.surfaceId, 0)
                local lua_temp_10
                if cfg.attachSurface ~= nil then
                    lua_temp_10 = not not cfg.attachSurface
                else
                    lua_temp_10 = true
                end
                local attachSurface = lua_temp_10
                if attachSurface then
                    if KTypeOf(surfApi.attachEntity) ~= "function" then
                        error(
                            LuaConstruct(Error, "[ENT] surface attach missing: engine.surface().attachEntity(surfaceHandle, uuid)"),
                            0
                        )
                    end
                    surfApi:attachEntity(ctx.surface, ctx.uuid)
                end
            end
            if bodyCfg then
                local made = self._physBind:createBody(self._bodyDefaults, bodyCfg, ctx.surface, surfCfg)
                ctx.body = made.body or nil
                ctx.bodyId = bit32.bor(made.bodyId, 0)
                createdBodyId = bit32.bor(ctx.bodyId, 0)
            elseif surfaceHadPhysics and ctx.surface then
                local bid = self._physBind:resolveBodyIdBySurface(ctx.surfaceId or ctx.surface)
                if bit32.bor(bid, 0) > 0 then
                    ctx.bodyId = bit32.bor(bid, 0)
                    ctx.body = nil
                    createdBodyId = bit32.bor(ctx.bodyId, 0)
                end
            end
            local requireCore = cfg.requireCore ~= false
            if requireCore and bit32.bor(ctx.bodyId, 0) <= 0 then
                if not ctx.surface then
                    error(
                        LuaConstruct(Error, "[ENT] core requires bodyId>0. Provide cfg.body or cfg.surface with collider. uuid=" .. ctx.uuid),
                        0
                    )
                end
                local made = self._physBind:createBody(self._bodyDefaults, {}, ctx.surface, surfCfg)
                ctx.body = made.body or nil
                ctx.bodyId = bit32.bor(made.bodyId, 0)
                createdBodyId = bit32.bor(ctx.bodyId, 0)
                if bit32.bor(ctx.bodyId, 0) <= 0 then
                    error(
                        LuaConstruct(Error, "[ENT] core auto-body failed (physics.body returned invalid id). uuid=" .. ctx.uuid),
                        0
                    )
                end
            end
            local comps = cfg.components
            if comps and KTypeOf(comps) == "table" then
                if KTypeOf(ent.setComponent) ~= "function" then
                    error(
                        LuaConstruct(Error, "[ENT] engine.entity().setComponent(uuid,type,value) missing"),
                        0
                    )
                end
                for lua_, key in ipairs(LuaTableKeys(comps)) do
                    local v = comps[key]
                    local lua_temp_11
                    if KTypeOf(v) == "function" then
                        lua_temp_11 = v(_G, {
                            uuid = ctx.uuid,
                            surface = ctx.surface,
                            body = ctx.body,
                            surfaceId = ctx.surfaceId,
                            bodyId = ctx.bodyId,
                            cfg = cfg
                        })
                    else
                        lua_temp_11 = v
                    end
                    local data = lua_temp_11
                    ent:setComponent(
                        ctx.uuid,
                        tostring(key),
                        data
                    )
                end
            end
            local handle = LuaConstruct(EntityHandle, engine, ctx)
            local core = nil
            if requireCore then
                local bodyAccess = resolveBodyAccess(
                    _G,
                    phys,
                    ctx.body,
                    bit32.bor(ctx.bodyId, 0)
                )
                core = LuaConstruct(EntityCore):attach(handle, ctx.body, bodyAccess)
                core.uuid = ctx.uuid
                core.bodyId = bit32.bor(ctx.bodyId, 0)
                core.surfaceId = bit32.bor(ctx.surfaceId, 0)
                if core.state and KTypeOf(core.state) == "table" then
                    core.state.uuid = core.uuid
                end
                if KTypeOf(core.hydrate) == "function" and KTypeOf(ent.snapshot) == "function" then
                    local snap = ent:snapshot(ctx.uuid)
                    core:hydrate(snap)
                end
                if cfg.shape then
                    local sh = cfg.shape or ({})
                    core:configureShape(sh.mass, sh.radius, sh.height)
                end
                if KTypeOf(cfg.groundProbe) == "function" then
                    core:setGroundProbe(cfg.groundProbe)
                end
            end
            handle.core = core
            if lua_debug then
                self._log:info((((((((("[ENT] created name=" .. name) .. " uuid=") .. ctx.uuid) .. " surfaceId=") .. tostring(bit32.bor(ctx.surfaceId, 0))) .. " bodyId=") .. tostring(bit32.bor(ctx.bodyId, 0))) .. " core=") .. (core and "yes" or "no"))
            end
            return true, KObject:freeze({
                core = core,
                handle = handle,
                uuid = handle:uuidString(),
                surfaceId = handle:surfaceHandleId(),
                bodyId = handle:bodyHandleId()
            })
        end)
        if not lua_try then
            lua_hasReturned, lua_returnValue = lua_catch(lua_hasReturned)
        end
        if lua_hasReturned then
            return lua_returnValue
        end
    end
end
function EntApi.prototype.idOf(self, h, kind)
    return idOf(_G, h, kind)
end
function EntApi.prototype.uuidOf(self, ref)
    if ref == nil then
        return ""
    end
    if KTypeOf(ref) == "string" then
        local lua_isUuidString_result_12
        if isUuidString(_G, ref) then
            lua_isUuidString_result_12 = LuaStringTrim(ref)
        else
            lua_isUuidString_result_12 = ""
        end
        return lua_isUuidString_result_12
    end
    if KTypeOf(ref) == "table" then
        if KTypeOf(ref.uuidString) == "function" then
            return tostring(ref:uuidString() or "")
        end
        if KTypeOf(ref.uuid) == "function" then
            return tostring(ref:uuid() or "")
        end
        if KTypeOf(ref.uuid) == "string" then
            return tostring(ref.uuid or "")
        end
    end
    return ""
end
M = {EntApi = EntApi}

return M
