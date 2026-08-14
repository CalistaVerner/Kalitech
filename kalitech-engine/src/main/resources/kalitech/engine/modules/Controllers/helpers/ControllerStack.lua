local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local Error = luaRuntime.Error
local LuaConstruct = luaRuntime.LuaConstruct
local LuaClass = luaRuntime.LuaClass
local LuaArrayIsArray = luaRuntime.LuaArrayIsArray
function req(self, v, msg)
    if v == nil then
        error(
            LuaConstruct(Error, msg),
            0
        )
    end
    return v
end
function reqFn(self, fn, msg)
    if KTypeOf(fn) ~= "function" then
        error(
            LuaConstruct(Error, msg),
            0
        )
    end
    return fn
end
ControllerStack = LuaClass()
ControllerStack.name = "ControllerStack"
function ControllerStack.prototype.lua_constructor(self, modules, ids)
    self.ctx = nil
    self.entity = nil
    local lua_Array_isArray_result_0
    if LuaArrayIsArray(modules) then
        lua_Array_isArray_result_0 = modules
    else
        lua_Array_isArray_result_0 = {}
    end
    self.modules = lua_Array_isArray_result_0
    local lua_Array_isArray_result_1
    if LuaArrayIsArray(ids) then
        lua_Array_isArray_result_1 = ids
    else
        lua_Array_isArray_result_1 = KArray(_G, #self.modules)
    end
    self.ids = lua_Array_isArray_result_1
    self._started = false
    self._modsStarted = KArrayFilled(_G, #self.modules, false)
end
function ControllerStack.fromRegistry(self, registry, ctx, entity, cfg)
    local built = registry:build(ctx, entity, cfg)
    local stack = LuaConstruct(ControllerStack, built.list, built.ids)
    return stack:bind(ctx, entity)
end
function ControllerStack.prototype.bind(self, ctx, entity)
    self.ctx = req(_G, ctx, "[Stack] ctx is required")
    self.entity = req(_G, entity, "[Stack] entity is required")
    do
        local i = 0
        while i < #self.modules do
            local m = req(
                _G,
                self.modules[i + 1],
                "[Stack] module is null at index " .. tostring(i)
            )
            reqFn(
                _G,
                m.bind,
                "[Stack] module.bind(ctx,entity) required at index " .. tostring(i)
            )
            m:bind(self.ctx, self.entity)
            i = i + 1
        end
    end
    return self
end
function ControllerStack.prototype._start(self)
    if self._started then
        return
    end
    self._started = true
    do
        local i = 0
        while i < #self.modules do
            local m = self.modules[i + 1]
            KSetIndex(self._modsStarted, i, true)
            if KTypeOf(m.onStart) == "function" then
                m:onStart()
            end
            i = i + 1
        end
    end
end
function ControllerStack.prototype._tick(self, dt)
    if not self._started then
        self:_start()
    end
    do
        local i = 0
        while i < #self.modules do
            local m = self.modules[i + 1]
            if KTypeOf(m.onUpdate) == "function" then
                m:onUpdate(dt)
            end
            i = i + 1
        end
    end
end
function ControllerStack.prototype._shutdown(self)
    if not self._started then
        return
    end
    do
        local i = #self.modules - 1
        while i >= 0 do
            do
                local m = self.modules[i + 1]
                if not KIndex(self._modsStarted, i) then
                    goto lua_continue24
                end
                KSetIndex(self._modsStarted, i, false)
                if KTypeOf(m.onStop) == "function" then
                    m:onStop()
                end
            end
            ::lua_continue24::
            i = i - 1
        end
    end
    self._started = false
end
function ControllerStack.prototype.rebuildFromRegistry(self, registry, cfg)
    registry = req(_G, registry, "[Stack] registry is required")
    if not self.ctx or not self.entity then
        error(
            LuaConstruct(Error, "[Stack] rebuild requires bound stack"),
            0
        )
    end
    self:_shutdown()
    local built = registry:build(self.ctx, self.entity, cfg)
    self.modules = built.list
    self.ids = built.ids
    self._modsStarted = KArrayFilled(_G, #self.modules, false)
    do
        local i = 0
        while i < #self.modules do
            local m = req(
                _G,
                self.modules[i + 1],
                "[Stack] module is null at index " .. tostring(i)
            )
            reqFn(
                _G,
                m.bind,
                "[Stack] module.bind(ctx,entity) required at index " .. tostring(i)
            )
            m:bind(self.ctx, self.entity)
            i = i + 1
        end
    end
end
M = {ControllerStack = ControllerStack}

return M
