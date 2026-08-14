local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local Classes = luaRuntime.class
local Error = luaRuntime.Error
function createEngineProxy(self, javaEngine)
    if not javaEngine then
        error(
            Classes:construct(Error, "[bootstrap] createEngineProxy: javaEngine is required"),
            0
        )
    end
    local store = KObject:create(nil)
    local modules = KObject:create(nil)
    local function hostHas(self, prop)
        do
            local function lua_catch(_)
                return true, false
            end
            local lua_try, lua_hasReturned, lua_returnValue = pcall(function()
                return true, javaEngine[prop] ~= nil
            end)
            if not lua_try then
                lua_hasReturned, lua_returnValue = lua_catch(lua_hasReturned)
            end
            if lua_hasReturned then
                return lua_returnValue
            end
        end
    end
    local function isSafeKey(self, prop)
        if KTypeOf(prop) ~= "string" then
            return false
        end
        if #prop == 0 then
            return false
        end
        if prop == "__proto__" or prop == "prototype" or prop == "constructor" then
            return false
        end
        return true
    end
    local function getHostFn(self, name)
        do
            local function lua_catch(_)
                return true, nil
            end
            local lua_try, lua_hasReturned, lua_returnValue = pcall(function()
                local v = javaEngine[name]
                local lua_temp_0
                if KTypeOf(v) == "function" then
                    lua_temp_0 = KFunction:bind(v, javaEngine)
                else
                    lua_temp_0 = nil
                end
                return true, lua_temp_0
            end)
            if not lua_try then
                lua_hasReturned, lua_returnValue = lua_catch(lua_hasReturned)
            end
            if lua_hasReturned then
                return lua_returnValue
            end
        end
    end
    local function api(self, id)
        id = tostring(id)
        local direct = getHostFn(_G, id)
        if direct then
            return direct(_G)
        end
        local hostApi = getHostFn(_G, "api")
        if hostApi then
            return hostApi(_G, id)
        end
        local regFn = getHostFn(_G, "registry")
        if regFn then
            local reg = regFn(_G)
            if reg then
                do
                    local lua_try, lua_hasReturned, lua_returnValue = pcall(function()
                        local ra = reg.api
                        if KTypeOf(ra) == "function" then
                            return true, KFunction:call(ra, reg, id)
                        end
                    end)
                    if lua_try and lua_hasReturned then
                        return lua_returnValue
                    end
                end
                do
                    local lua_try, lua_hasReturned, lua_returnValue = pcall(function()
                        local ge = reg.get
                        if KTypeOf(ge) == "function" then
                            local e = KFunction:call(ge, reg, id)
                            if e and e.api then
                                return true, e.api
                            end
                        end
                    end)
                    if lua_try and lua_hasReturned then
                        return lua_returnValue
                    end
                end
            end
        end
        return nil
    end
    local function apiGetterFn(self, key)
        return function(self)
            local out = api(_G, key)
            if not out then
                error(
                    Classes:construct(
                        Error,
                        ("[ENGINE] api missing: '" .. tostring(key)) .. "'"
                    ),
                    0
                )
            end
            return out
        end
    end
    local function setModule(self, key, value)
        key = tostring(key)
        if rawget(modules, key) ~= nil then
            error(
                Classes:construct(
                    Error,
                    ("[ENGINE] duplicate module key '" .. tostring(key)) .. "'"
                ),
                0
            )
        end
        modules[key] = value
        if not hostHas(_G, key) then
            store[key] = value
        end
        return value
    end
    local function getModule(self, key)
        return rawget(modules, tostring(key))
    end
    local function hasModule(self, key)
        return rawget(modules, tostring(key)) ~= nil
    end
    store.modules = modules
    store.setModule = setModule
    store.getModule = getModule
    store.hasModule = hasModule
    store.__host__ = javaEngine
    store.api = api
    return KProxy(
        _G,
        javaEngine,
        {
            has = function(self, target, prop)
                if store[prop] ~= nil then
                    return true
                end
                if modules[prop] ~= nil then
                    return true
                end
                return target[prop] ~= nil
            end,
            get = function(self, target, prop)
                if store[prop] ~= nil then
                    return store[prop]
                end
                if modules[prop] ~= nil then
                    return modules[prop]
                end
                local v
                do
                    local function lua_catch(_)
                        v = nil
                    end
                    local lua_try, lua_hasReturned = pcall(function()
                        v = target[prop]
                    end)
                    if not lua_try then
                        lua_catch(lua_hasReturned)
                    end
                end
                if KTypeOf(v) == "function" and KTypeOf(prop) == "string" then
                    local bound = KFunction:bind(v, target)
                    if prop and prop ~= "api" and prop ~= "registry" and prop ~= "getRegistry" then
                        return function(self, ...)
                            local args = {...}
                            local out = KFunction:apply(v, target, args)
                            if out ~= nil and out ~= nil then
                                return out
                            end
                            local lua_store_api_1
                            if store.api then
                                lua_store_api_1 = store:api(prop)
                            else
                                lua_store_api_1 = nil
                            end
                            local a = lua_store_api_1
                            if a ~= nil and a ~= nil then
                                return a
                            end
                            return out
                        end
                    end
                    return bound
                end
                if v ~= nil then
                    return v
                end
                if KTypeOf(prop) == "string" and #prop > 0 then
                    return function(self)
                        local out = store:api(prop)
                        if not out then
                            error(
                                Classes:construct(Error, ("[ENGINE] api missing: '" .. prop) .. "'"),
                                0
                            )
                        end
                        return out
                    end
                end
                return nil
            end,
            set = function(self, _target, prop, value)
                if hostHas(_G, prop) then
                    modules[prop] = value
                    return true
                end
                store[prop] = value
                return true
            end,
            ownKeys = function(self, target)
                local hostKeys = KObject:keys(target)
                local storeKeys = KObject:keys(store)
                local modKeys = KObject:keys(modules)
                local out = KArrayOps.slice(hostKeys)
                do
                    local i = 0
                    while i < KLength(storeKeys) do
                        if KArrayOps.indexOf(out, KIndex(storeKeys, i)) < 0 then
                            KArrayOps.push(out, KIndex(storeKeys, i))
                        end
                        i = i + 1
                    end
                end
                do
                    local i = 0
                    while i < KLength(modKeys) do
                        if KArrayOps.indexOf(out, KIndex(modKeys, i)) < 0 then
                            KArrayOps.push(out, KIndex(modKeys, i))
                        end
                        i = i + 1
                    end
                end
                return out
            end,
            getOwnPropertyDescriptor = function(self, target, prop)
                if store[prop] ~= nil then
                    return {configurable = true, enumerable = true, writable = true, value = store[prop]}
                end
                if modules[prop] ~= nil then
                    return {configurable = true, enumerable = true, writable = true, value = modules[prop]}
                end
                if not (target[prop] ~= nil) and isSafeKey(_G, prop) then
                    return {
                        configurable = true,
                        enumerable = true,
                        writable = false,
                        value = apiGetterFn(_G, prop)
                    }
                end
                return KObject:getOwnPropertyDescriptor(target, prop)
            end
        }
    )
end
local BootstrapEngineProxyApi = Classes:create()
BootstrapEngineProxyApi.name = "BootstrapEngineProxyApi"
BootstrapEngineProxyApi.prototype.createEngineProxy = createEngineProxy
return Classes:construct(BootstrapEngineProxyApi)
