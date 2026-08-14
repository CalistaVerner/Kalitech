local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local Tables = luaRuntime.table
local Classes = luaRuntime.class
local Error = luaRuntime.Error
local lua_require_result_0 = require("./ControllerRegistry")
ControllerRegistry = lua_require_result_0.ControllerRegistry
function req(self, v, msg)
    if v == nil then
        error(
            Classes:construct(Error, msg),
            0
        )
    end
    return v
end
function reqStr(self, s, msg)
    if KTypeOf(s) ~= "string" or not s then
        error(
            Classes:construct(Error, msg),
            0
        )
    end
    return s
end
EngineControllers = Classes:create()
EngineControllers.name = "EngineControllers"
function EngineControllers.prototype.lua_constructor(self)
    self._registries = KObject:create(nil)
end
function EngineControllers.prototype.get(self, name)
    name = reqStr(_G, name, "[EngineControllers] name is required")
    return self._registries[name] or nil
end
function EngineControllers.prototype.set(self, registry)
    registry = req(_G, registry, "[EngineControllers] registry is required")
    local name = reqStr(_G, registry.name, "[EngineControllers] registry.name is required")
    local existing = self._registries[name]
    if existing and existing ~= registry then
        self._registries[name] = registry
        return registry
    end
    self._registries[name] = registry
    return registry
end
function EngineControllers.prototype.controllers(self, name)
    name = reqStr(_G, name, "[EngineControllers] name is required")
    local r = self._registries[name]
    if not r then
        r = Classes:construct(ControllerRegistry, name)
        self._registries[name] = r
    end
    return r
end
function EngineControllers.prototype.reset(self, name)
    name = reqStr(_G, name, "[EngineControllers] name is required")
    local r = self._registries[name]
    if not r then
        return nil
    end
    if KTypeOf(r.clear) == "function" then
        do
            pcall(function()
                r:clear()
            end)
        end
        return r
    end
    local nr = Classes:construct(ControllerRegistry, name)
    self._registries[name] = nr
    return nr
end
function EngineControllers.prototype.resetAll(self)
    local keys = Tables:keys(self._registries)
    do
        local i = 0
        while i < #keys do
            do
                local k = keys[i + 1]
                local r = self._registries[k]
                if not r then
                    goto lua_continue18
                end
                if KTypeOf(r.clear) == "function" then
                    do
                        pcall(function()
                            r:clear()
                        end)
                    end
                else
                    self._registries[k] = Classes:construct(ControllerRegistry, k)
                end
            end
            ::lua_continue18::
            i = i + 1
        end
    end
    return true
end
function ensureControllersHub(self, engine, K)
    req(_G, engine, "[ensureControllersHub] engine is required")
    K = req(_G, K, "[ensureControllersHub] K is required")
    if not K.services then
        K.services = KObject:create(nil)
    end
    if not K.services.controllersHub then
        K.services.controllersHub = Classes:construct(EngineControllers)
    end
    return K.services.controllersHub
end
M = {EngineControllers = EngineControllers, ensureControllersHub = ensureControllersHub}

return M
