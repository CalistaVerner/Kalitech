local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local LuaClass = luaRuntime.LuaClass
local LuaArrayIsArray = luaRuntime.LuaArrayIsArray
local Error = luaRuntime.Error
local LuaConstruct = luaRuntime.LuaConstruct
local LuaArrayJoin = luaRuntime.LuaArrayJoin
local lua_require_result_0 = require("./EntUtil.lua")
req = lua_require_result_0.req
subsystem = lua_require_result_0.subsystem
local lua_require_result_1 = require("./BodyAccessResolver.lua")
resolveBodyAccess = lua_require_result_1.resolveBodyAccess
function pushErr(self, list, op, e)
    local lua_temp_2
    if e and e.stack then
        lua_temp_2 = e.stack
    else
        lua_temp_2 = tostring(e)
    end
    local msg = lua_temp_2
    KArrayOps.push(list, (tostring(op) .. " :: ") .. tostring(msg))
end
EntityHandle = LuaClass()
EntityHandle.name = "EntityHandle"
function EntityHandle.prototype.lua_constructor(self, engine, ctx)
    self._engine = engine
    local lua_temp_3
    if ctx.uuid ~= nil then
        lua_temp_3 = tostring(ctx.uuid)
    else
        lua_temp_3 = ""
    end
    self.uuid = lua_temp_3
    self.surface = ctx.surface or nil
    self.body = ctx.body or nil
    self.surfaceId = bit32.bor(ctx.surfaceId, 0)
    self.bodyId = bit32.bor(ctx.bodyId, 0)
    local lua_Array_isArray_result_4
    if LuaArrayIsArray(ctx._destroyers) then
        lua_Array_isArray_result_4 = ctx._destroyers
    else
        lua_Array_isArray_result_4 = {}
    end
    self._destroyers = lua_Array_isArray_result_4
    self._bodyAccess = nil
    self._bodyAccessId = 0
    req(
        _G,
        engine and engine.log and KTypeOf(engine.log) == "function",
        "[ENT] engine.log() is required"
    )
    self._log = engine:log()
    req(_G, self._log and self._log.info and self._log.warn and self._log.error, "[ENT] engine.log() must provide info/warn/error")
    if not self.uuid then
        error(
            LuaConstruct(Error, "[ENT] EntityHandle missing uuid (UUID-only)"),
            0
        )
    end
    self.core = nil
end
function EntityHandle.prototype.id(self)
    error(
        LuaConstruct(Error, "[ENT] EntityHandle.id() removed (UUID-only)"),
        0
    )
end
function EntityHandle.prototype.uuidString(self)
    return self.uuid or ""
end
function EntityHandle.prototype.uuid(self)
    return self:uuidString()
end
function EntityHandle.prototype.surfaceHandleId(self)
    return bit32.bor(self.surfaceId, 0)
end
function EntityHandle.prototype.bodyHandleId(self)
    return bit32.bor(self.bodyId, 0)
end
function EntityHandle.prototype.valueOf(self)
    return self:uuidString()
end
function EntityHandle.prototype.__tostring(self)
    return self:uuidString()
end
function EntityHandle.prototype.setVisible(self, v)
    local sid = bit32.bor(self.surfaceId, 0)
    if not sid then
        error(
            LuaConstruct(
                Error,
                "[ENT] setVisible: surfaceId=0 uuid=" .. tostring(self.uuid)
            ),
            0
        )
    end
    local s = subsystem(_G, self._engine, "surface")
    req(
        _G,
        KTypeOf(s.setVisible) == "function",
        "[ENT] setVisible: engine.surface().setVisible(surfaceId,bool) missing"
    )
    s:setVisible(sid, not not v)
    return self
end
function EntityHandle.prototype.setCull(self, hint)
    local sid = bit32.bor(self.surfaceId, 0)
    if not sid then
        error(
            LuaConstruct(
                Error,
                "[ENT] setCull: surfaceId=0 uuid=" .. tostring(self.uuid)
            ),
            0
        )
    end
    local s = subsystem(_G, self._engine, "surface")
    req(
        _G,
        KTypeOf(s.setCull) == "function",
        "[ENT] setCull: engine.surface().setCull(surfaceId,string) missing"
    )
    s:setCull(
        sid,
        tostring(hint)
    )
    return self
end
function EntityHandle.prototype.hasBody(self)
    return bit32.bor(self.bodyId, 0) > 0
end
function EntityHandle.prototype.requireBodyId(self, opName)
    local id = bit32.bor(self.bodyId, 0)
    if id <= 0 then
        error(
            LuaConstruct(
                Error,
                ((("[ENT] " .. tostring(opName)) .. ": entity has no bodyId (uuid=") .. tostring(self.uuid)) .. ")"
            ),
            0
        )
    end
    return id
end
function EntityHandle.prototype.physApi(self)
    local p = subsystem(_G, self._engine, "physics")
    req(_G, p, "[ENT] engine.physics() returned null")
    return p
end
function EntityHandle.prototype.bodyAccess(self)
    local id = self:requireBodyId("bodyAccess()")
    if self._bodyAccess and bit32.bor(self._bodyAccessId, 0) == id then
        return self._bodyAccess
    end
    local phys = self:physApi()
    local ba = resolveBodyAccess(_G, phys, self.body, id)
    self._bodyAccess = ba
    self._bodyAccessId = id
    return ba
end
function EntityHandle.prototype.bodyRef(self)
    return self:bodyAccess()
end
function EntityHandle.prototype.snapshot(self)
    local ent = subsystem(_G, self._engine, "entity")
    local uuid = self.uuid
    if not uuid then
        error(
            LuaConstruct(Error, "[ENT] snapshot: uuid empty"),
            0
        )
    end
    req(
        _G,
        KTypeOf(ent.snapshot) == "function",
        "[ENT] engine.entity().snapshot(uuid) missing"
    )
    return ent:snapshot(uuid)
end
function EntityHandle.prototype.destroy(self)
    local errors = {}
    local engine = self._engine
    local uuid = self.uuid
    local sid = bit32.bor(self.surfaceId, 0)
    local bid = bit32.bor(self.bodyId, 0)
    do
        local i = 0
        while i < KLength(self._destroyers) do
            do
                local function lua_catch(e)
                    pushErr(
                        _G,
                        errors,
                        ("destroyer[" .. tostring(i)) .. "]",
                        e
                    )
                end
                local lua_try, lua_hasReturned = pcall(function()
                    local lua_self_5 = self._destroyers
                    KIndex(lua_self_5, i)(lua_self_5)
                end)
                if not lua_try then
                    lua_catch(lua_hasReturned)
                end
            end
            i = i + 1
        end
    end
    KArrayClear(self._destroyers)
    if bid > 0 then
        do
            local function lua_catch(e)
                pushErr(
                    _G,
                    errors,
                    ("physics.remove(" .. tostring(bid)) .. ")",
                    e
                )
            end
            local lua_try, lua_hasReturned = pcall(function()
                local phys = subsystem(_G, engine, "physics")
                if KTypeOf(phys.remove) ~= "function" then
                    error(
                        LuaConstruct(Error, "engine.physics().remove(bodyId) missing"),
                        0
                    )
                end
                phys:remove(bid)
            end)
            if not lua_try then
                lua_catch(lua_hasReturned)
            end
        end
    end
    if sid > 0 then
        do
            local function lua_catch(e)
                pushErr(
                    _G,
                    errors,
                    ("surface.drop(" .. tostring(sid)) .. ")",
                    e
                )
            end
            local lua_try, lua_hasReturned = pcall(function()
                local surf = subsystem(_G, engine, "surface")
                if KTypeOf(surf.drop) ~= "function" then
                    error(
                        LuaConstruct(Error, "engine.surface().drop(surfaceId,recursive) missing"),
                        0
                    )
                end
                surf:drop(sid, true)
            end)
            if not lua_try then
                lua_catch(lua_hasReturned)
            end
        end
    end
    if uuid then
        do
            local function lua_catch(e)
                pushErr(
                    _G,
                    errors,
                    ("entity.destroy(" .. tostring(uuid)) .. ")",
                    e
                )
            end
            local lua_try, lua_hasReturned = pcall(function()
                local ent = subsystem(_G, engine, "entity")
                if KTypeOf(ent.destroy) ~= "function" then
                    error(
                        LuaConstruct(Error, "engine.entity().destroy(uuid) missing"),
                        0
                    )
                end
                ent:destroy(uuid)
            end)
            if not lua_try then
                lua_catch(lua_hasReturned)
            end
        end
    end
    self.bodyId = 0
    self.surfaceId = 0
    self.body = nil
    self.surface = nil
    self._bodyAccess = nil
    self._bodyAccessId = 0
    self.uuid = ""
    self.core = nil
    if #errors > 0 then
        local err = LuaConstruct(
            Error,
            "[ENT] destroy failed:\n- " .. LuaArrayJoin(errors, "\n- ")
        )
        error(err, 0)
    end
end
function EntityHandle.prototype.snapshot(self)
    local lua_temp_6
    if self.engine and self.engine.entity then
        lua_temp_6 = self.engine:entity()
    else
        lua_temp_6 = nil
    end
    local ent = lua_temp_6
    local uuid = self:uuidString()
    if not ent or not uuid or KTypeOf(ent.snapshot) ~= "function" then
        return nil
    end
    return ent:snapshot(uuid)
end
function EntityHandle.prototype.hydrateCore(self)
    if not self.core then
        return nil
    end
    local snap = self:snapshot()
    if snap then
        self.core:hydrate(snap)
    end
    return self.core
end
function EntityHandle.prototype.addDestroyer(self, fn)
    if KTypeOf(fn) ~= "function" then
        error(
            LuaConstruct(Error, "[ENT] addDestroyer(fn): fn must be a function"),
            0
        )
    end
    KArrayOps.push(self._destroyers, fn)
    return self
end
function EntityHandle.prototype.setComponent(self, lua_type, value)
    local ent = subsystem(_G, self._engine, "entity")
    local uuid = self.uuid
    if not uuid then
        error(
            LuaConstruct(Error, "[ENT] setComponent: uuid empty"),
            0
        )
    end
    req(
        _G,
        KTypeOf(ent.setComponent) == "function",
        "[ENT] setComponent(uuid,type,value) missing"
    )
    ent:setComponent(
        uuid,
        tostring(lua_type),
        value
    )
    return self
end
function EntityHandle.prototype.getComponent(self, lua_type)
    local ent = subsystem(_G, self._engine, "entity")
    local uuid = self.uuid
    if not uuid then
        error(
            LuaConstruct(Error, "[ENT] getComponent: uuid empty"),
            0
        )
    end
    req(
        _G,
        KTypeOf(ent.getComponent) == "function",
        "[ENT] getComponent(uuid,type) missing"
    )
    return ent:getComponent(
        uuid,
        tostring(lua_type)
    )
end
function EntityHandle.prototype.hasComponent(self, lua_type)
    local ent = subsystem(_G, self._engine, "entity")
    local uuid = self.uuid
    if not uuid then
        error(
            LuaConstruct(Error, "[ENT] hasComponent: uuid empty"),
            0
        )
    end
    req(
        _G,
        KTypeOf(ent.hasComponent) == "function",
        "[ENT] hasComponent(uuid,type) missing"
    )
    return not not ent:hasComponent(
        uuid,
        tostring(lua_type)
    )
end
function EntityHandle.prototype.logInfo(self, msg)
    self._log:info(tostring(msg))
    return self
end
function EntityHandle.prototype.logWarn(self, msg)
    self._log:warn(tostring(msg))
    return self
end
function EntityHandle.prototype.logError(self, msg)
    self._log:error(tostring(msg))
    return self
end
M = {EntityHandle = EntityHandle}

return M
