local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local LuaTypeOf = luaRuntime.LuaTypeOf
local LuaArrayIsArray = luaRuntime.LuaArrayIsArray
local LuaArrayEvery = luaRuntime.LuaArrayEvery
local LuaTableKeys = luaRuntime.LuaTableKeys
local LuaClass = luaRuntime.LuaClass
local Error = luaRuntime.Error
local LuaConstruct = luaRuntime.LuaConstruct
local LuaStringTrim = luaRuntime.LuaStringTrim
function _isFn(self, x)
    return KTypeOf(x) == "function"
end
function _isPlainObj(self, x)
    if not x or KTypeOf(x) ~= "table" then
        return false
    end
    local p = KObject:getPrototypeOf(x)
    return p == KObject.prototype or p == nil
end
function _isJsonValue(self, x, depth)
    if depth == nil then
        depth = 0
    end
    if depth > 24 then
        return false
    end
    if x == nil then
        return true
    end
    local t = LuaTypeOf(x)
    if t == "string" or t == "number" or t == "boolean" then
        return true
    end
    if LuaArrayIsArray(x) then
        return LuaArrayEvery(
            x,
            function(lua_, v) return _isJsonValue(_G, v, depth + 1) end
        )
    end
    if _isPlainObj(_G, x) then
        for lua_, k in ipairs(LuaTableKeys(x)) do
            if not _isJsonValue(_G, x[k], depth + 1) then
                return false
            end
        end
        return true
    end
    return false
end
function _typeOfValue(self, v)
    if v == nil then
        return "null"
    end
    if LuaArrayIsArray(v) then
        return "array"
    end
    return LuaTypeOf(v)
end
function _safeCall(self, fn, fb)
    do
        local function lua_catch(_)
            return true, fb
        end
        local lua_try, lua_hasReturned, lua_returnValue = pcall(function()
            return true, fn(_G)
        end)
        if not lua_try then
            lua_hasReturned, lua_returnValue = lua_catch(lua_hasReturned)
        end
        if lua_hasReturned then
            return lua_returnValue
        end
    end
end
function _getBus(self, engine)
    if not engine then
        return nil
    end
    return _safeCall(
        _G,
        function()
            local lua_isFn_result_0
            if _isFn(_G, engine.bus) then
                lua_isFn_result_0 = engine:bus()
            else
                lua_isFn_result_0 = nil
            end
            return lua_isFn_result_0
        end,
        nil
    )
end
function _busOn(self, bus, topic, fn)
    if not bus then
        return 0
    end
    if _isFn(_G, bus.on) then
        return bit32.bor(
            bus:on(topic, fn),
            0
        )
    end
    if _isFn(_G, bus.addListener) then
        return bit32.bor(
            bus:addListener(topic, fn),
            0
        )
    end
    if _isFn(_G, bus.addEventListener) then
        return bit32.bor(
            bus:addEventListener(topic, fn),
            0
        )
    end
    if _isFn(_G, bus.subscribe) then
        return bit32.bor(
            bus:subscribe(topic, fn),
            0
        )
    end
    return 0
end
function _busOffToken(self, bus, token, topicMaybe)
    if not bus then
        return false
    end
    local tok = bit32.bor(token, 0)
    if not tok then
        return false
    end
    if _isFn(_G, bus.off) then
        do
            local function lua_catch(_)
                if topicMaybe ~= nil then
                    do
                        local lua_try, lua_hasReturned, lua_returnValue = pcall(function()
                            local r2 = bus:off(
                                tostring(topicMaybe or ""),
                                tok
                            )
                            local lua_temp_2
                            if r2 == nil then
                                lua_temp_2 = true
                            else
                                lua_temp_2 = not not r2
                            end
                            return true, lua_temp_2
                        end)
                        if lua_try and lua_hasReturned then
                            return true, lua_returnValue
                        end
                    end
                end
            end
            local lua_try, lua_hasReturned, lua_returnValue = pcall(function()
                local r = bus:off(tok)
                local lua_temp_1
                if r == nil then
                    lua_temp_1 = true
                else
                    lua_temp_1 = not not r
                end
                return true, lua_temp_1
            end)
            if not lua_try then
                lua_hasReturned, lua_returnValue = lua_catch(lua_hasReturned)
            end
            if lua_hasReturned then
                return lua_returnValue
            end
        end
    end
    if _isFn(_G, bus.offToken) then
        do
            local lua_try, lua_hasReturned, lua_returnValue = pcall(function()
                return true, not not bus:offToken(tok)
            end)
            if lua_try and lua_hasReturned then
                return lua_returnValue
            end
        end
    end
    if _isFn(_G, bus.unsubscribe) then
        do
            local lua_try, lua_hasReturned, lua_returnValue = pcall(function()
                bus:unsubscribe(tok)
                return true, true
            end)
            if lua_try and lua_hasReturned then
                return lua_returnValue
            end
        end
    end
    return false
end
function _busOffClassic(self, bus, topic, fn)
    if not bus then
        return false
    end
    if not _isFn(_G, fn) then
        return false
    end
    if _isFn(_G, bus.off) then
        do
            local lua_try, lua_hasReturned, lua_returnValue = pcall(function()
                bus:off(topic, fn)
                return true, true
            end)
            if lua_try and lua_hasReturned then
                return lua_returnValue
            end
        end
    end
    if _isFn(_G, bus.removeListener) then
        do
            local lua_try, lua_hasReturned, lua_returnValue = pcall(function()
                bus:removeListener(topic, fn)
                return true, true
            end)
            if lua_try and lua_hasReturned then
                return lua_returnValue
            end
        end
    end
    if _isFn(_G, bus.removeEventListener) then
        do
            local lua_try, lua_hasReturned, lua_returnValue = pcall(function()
                bus:removeEventListener(topic, fn)
                return true, true
            end)
            if lua_try and lua_hasReturned then
                return lua_returnValue
            end
        end
    end
    if _isFn(_G, bus.unsubscribe) then
        do
            local lua_try, lua_hasReturned, lua_returnValue = pcall(function()
                bus:unsubscribe(topic, fn)
                return true, true
            end)
            if lua_try and lua_hasReturned then
                return lua_returnValue
            end
        end
    end
    return false
end
function _busEmit(self, bus, topic, payload)
    if not bus then
        return false
    end
    if _isFn(_G, bus.emit) then
        do
            local lua_try, lua_hasReturned, lua_returnValue = pcall(function()
                bus:emit(topic, payload)
                return true, true
            end)
            if lua_try and lua_hasReturned then
                return lua_returnValue
            end
        end
    end
    if _isFn(_G, bus.publish) then
        do
            local lua_try, lua_hasReturned, lua_returnValue = pcall(function()
                bus:publish(topic, payload)
                return true, true
            end)
            if lua_try and lua_hasReturned then
                return lua_returnValue
            end
        end
    end
    if _isFn(_G, bus.dispatch) then
        do
            local lua_try, lua_hasReturned, lua_returnValue = pcall(function()
                bus:dispatch(topic, payload)
                return true, true
            end)
            if lua_try and lua_hasReturned then
                return lua_returnValue
            end
        end
    end
    return false
end
EventsApi = LuaClass()
EventsApi.name = "EventsApi"
function EventsApi.prototype.lua_constructor(self, engine, K)
    self.engineRef = engine
    self.K = K or (_G.__kalitech or KObject:create(nil))
    self._bus = nil
    self._defaultSeparator = "."
    self._throwIfNoBus = true
    self._validate = not not (self.K and self.K.config and self.K.config.dev)
    self._schemas = KObject:create(nil)
    self._lastResolveAt = 0
    self._resolveCooldownMs = 50
end
function EventsApi.prototype._resolveBus(self, force)
    local timeApi = self.engineRef:time()
    local now = timeApi:now() * 1000
    if not force and self._bus then
        return self._bus
    end
    if not force and now and now - self._lastResolveAt < self._resolveCooldownMs then
        return self._bus
    end
    self._lastResolveAt = now
    local b = _getBus(_G, self.engineRef)
    if b then
        self._bus = b
    end
    return self._bus
end
function EventsApi.prototype.bus(self)
    return self:_resolveBus(false)
end
function EventsApi.prototype.enabled(self)
    return not not self:bus()
end
function EventsApi.prototype.configure(self, cfg)
    local lua_temp_4
    if cfg and KTypeOf(cfg) == "table" then
        lua_temp_4 = cfg
    else
        lua_temp_4 = {}
    end
    cfg = lua_temp_4
    if cfg.separator ~= nil then
        self._defaultSeparator = tostring(cfg.separator)
    end
    if cfg.throwIfNoBus ~= nil then
        self._throwIfNoBus = not not cfg.throwIfNoBus
    end
    if cfg.validate ~= nil then
        self._validate = not not cfg.validate
    end
    if cfg.resolveCooldownMs ~= nil then
        self._resolveCooldownMs = math.max(
            0,
            bit32.bor(cfg.resolveCooldownMs, 0)
        )
    end
    return self
end
function EventsApi.prototype._needBus(self)
    local b = self:_resolveBus(false)
    if not b and self._throwIfNoBus then
        error(
            LuaConstruct(Error, "[EVENTS] bus is not available (yet)"),
            0
        )
    end
    return b
end
function EventsApi.prototype.on(self, topic, handler)
    local t = tostring(topic or "")
    if not t then
        error(
            LuaConstruct(Error, "[EVENTS] topic is required"),
            0
        )
    end
    if not _isFn(_G, handler) then
        error(
            LuaConstruct(Error, "[EVENTS] handler must be a function"),
            0
        )
    end
    local bus = self:_needBus()
    if not bus then
        return function(self)
            return false
        end
    end
    local token = _busOn(_G, bus, t, handler)
    return function() return _busOffToken(_G, bus, token, t) end
end
function EventsApi.prototype.once(self, topic, handler)
    local t = tostring(topic or "")
    if not t then
        error(
            LuaConstruct(Error, "[EVENTS] topic is required"),
            0
        )
    end
    if not _isFn(_G, handler) then
        error(
            LuaConstruct(Error, "[EVENTS] handler must be a function"),
            0
        )
    end
    local offFn = nil
    local function wrapped(lua_, data)
        do
            pcall(function()
                if offFn then
                    offFn(_G)
                end
            end)
        end
        return handler(_G, data)
    end
    offFn = self:on(t, wrapped)
    return offFn
end
function EventsApi.prototype.off(self, topic, handler)
    local t = tostring(topic or "")
    if not t then
        return false
    end
    if not _isFn(_G, handler) then
        return false
    end
    local bus = self:bus()
    if not bus then
        return false
    end
    return _busOffClassic(_G, bus, t, handler)
end
function EventsApi.prototype.offToken(self, token, topicMaybe)
    local bus = self:bus()
    if not bus then
        return false
    end
    return _busOffToken(_G, bus, token, topicMaybe)
end
function EventsApi.prototype.emit(self, topic, payload)
    local t = tostring(topic or "")
    if not t then
        error(
            LuaConstruct(Error, "[EVENTS] topic is required"),
            0
        )
    end
    local bus = self:_needBus()
    if not bus then
        return false
    end
    return _busEmit(_G, bus, t, payload)
end
function EventsApi.prototype.register(self, def)
    local lua_temp_5
    if def and KTypeOf(def) == "table" then
        lua_temp_5 = def
    else
        lua_temp_5 = {}
    end
    def = lua_temp_5
    local id = LuaStringTrim(tostring(def.id or ""))
    if not id then
        error(
            LuaConstruct(Error, "[EVENTS] schema id is required"),
            0
        )
    end
    local lua_temp_6
    if def.version ~= nil then
        lua_temp_6 = tostring(def.version)
    else
        lua_temp_6 = "1.0.0"
    end
    local version = lua_temp_6
    local lua_isPlainObj_result_7
    if _isPlainObj(_G, def.schema) then
        lua_isPlainObj_result_7 = def.schema
    else
        lua_isPlainObj_result_7 = KObject:create(nil)
    end
    local schema = lua_isPlainObj_result_7
    local delivery = def.delivery or def.frequency or "at-most-once"
    local order = def.order or "none"
    self._schemas[id] = {
        id = id,
        version = version,
        schema = schema,
        delivery = delivery,
        order = order
    }
    return self._schemas[id]
end
function EventsApi.prototype.schema(self, id)
    return self._schemas[tostring(id or "")]
end
function EventsApi.prototype.evt(self, id, payload, meta)
    local topic = tostring(id or "")
    if not topic then
        error(
            LuaConstruct(Error, "[EVENTS] evt id is required"),
            0
        )
    end
    local def = self._schemas[topic]
    if self._validate then
        if not def then
            error(
                LuaConstruct(Error, "[EVENTS] missing schema for event: " .. topic),
                0
            )
        end
        if not _isJsonValue(_G, payload) then
            error(
                LuaConstruct(Error, "[EVENTS] event payload must be JSON-safe (no host objects): " .. topic),
                0
            )
        end
        local schema = def.schema or KObject:create(nil)
        if _isPlainObj(_G, schema) then
            for lua_, key in ipairs(LuaTableKeys(schema)) do
                do
                    local rule = schema[key]
                    local lua_isPlainObj_result_8
                    if _isPlainObj(_G, rule) then
                        lua_isPlainObj_result_8 = tostring(rule.type or "any")
                    else
                        lua_isPlainObj_result_8 = tostring(rule or "any")
                    end
                    local expected = lua_isPlainObj_result_8
                    local lua_isPlainObj_result_9
                    if _isPlainObj(_G, rule) then
                        lua_isPlainObj_result_9 = not not rule.optional
                    else
                        lua_isPlainObj_result_9 = false
                    end
                    local optional = lua_isPlainObj_result_9
                    local lua_typeOfValue_12 = _typeOfValue
                    local lua_G_11 = _G
                    local lua_payload_10
                    if payload then
                        lua_payload_10 = payload[key]
                    else
                        lua_payload_10 = nil
                    end
                    local actual = lua_typeOfValue_12(lua_G_11, lua_payload_10)
                    if actual == "nil" then
                        if not optional then
                            error(
                                LuaConstruct(Error, (("[EVENTS] missing field '" .. key) .. "' in ") .. topic),
                                0
                            )
                        end
                        goto lua_continue105
                    end
                    if expected ~= "any" and expected ~= actual then
                        error(
                            LuaConstruct(Error, ((((((("[EVENTS] field '" .. key) .. "' type mismatch in ") .. topic) .. " (expected ") .. expected) .. ", got ") .. actual) .. ")"),
                            0
                        )
                    end
                end
                ::lua_continue105::
            end
        end
    end
    local bus = self:_needBus()
    if not bus then
        return false
    end
    local metaObj = meta or nil
    if _isFn(_G, bus.emitEvent) then
        do
            local lua_try, lua_hasReturned, lua_returnValue = pcall(function()
                bus:emitEvent(topic, payload, metaObj)
                return true, true
            end)
            if lua_try and lua_hasReturned then
                return lua_returnValue
            end
        end
    end
    return _busEmit(_G, bus, topic, payload)
end
function EventsApi.prototype.scope(self, scopeName, separator)
    local scope = LuaStringTrim(tostring(scopeName or ""))
    local lua_temp_13
    if separator == nil then
        lua_temp_13 = self._defaultSeparator
    else
        lua_temp_13 = tostring(separator)
    end
    local sep = lua_temp_13
    local lua_scope_14
    if scope then
        lua_scope_14 = scope .. sep
    else
        lua_scope_14 = ""
    end
    local prefix = lua_scope_14
    local lua_self = self
    return KObject:freeze({
        scope = scope,
        on = function(lua_, topic, handler) return lua_self:on(
            prefix .. tostring(topic or ""),
            handler
        ) end,
        once = function(lua_, topic, handler) return lua_self:once(
            prefix .. tostring(topic or ""),
            handler
        ) end,
        off = function(lua_, topic, handler) return lua_self:off(
            prefix .. tostring(topic or ""),
            handler
        ) end,
        emit = function(lua_, topic, payload) return lua_self:emit(
            prefix .. tostring(topic or ""),
            payload
        ) end,
        evt = function(lua_, topic, payload, meta) return lua_self:evt(
            prefix .. tostring(topic or ""),
            payload,
            meta
        ) end,
        offToken = function(lua_, token, topicMaybe)
            local lua_self_offToken_17 = lua_self.offToken
            local lua_token_16 = token
            local lua_temp_15
            if topicMaybe ~= nil then
                lua_temp_15 = prefix .. tostring(topicMaybe)
            else
                lua_temp_15 = nil
            end
            return lua_self_offToken_17(lua_self, lua_token_16, lua_temp_15)
        end
    })
end
function EventsApi.prototype.child(self, scopeName, separator)
    return self:scope(scopeName, separator)
end
create = setmetatable(
    {},
    {__call = function(lua_, self, engine, K)
        if not engine then
            error(
                LuaConstruct(Error, "[EVENTS] engine is required"),
                0
            )
        end
        return LuaConstruct(EventsApi, engine, K)
    end}
)
create.META = {
    moduleId = "events",
    id = "events",
    version = "2.0.0",
    description = "Event bus v2: schema-aware evt() with optional validation and JSON-only payloads.",
    engineMin = "0.1.0",
    changelog = {"2.0.0: added schema registry + evt() with dev-mode validation, JSON-only payload enforcement."},
}
M = create

return M
