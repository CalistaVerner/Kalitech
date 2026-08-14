local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local Classes = luaRuntime.class
function createDeferredProxy(self, resolveFn, label)
    local state = {resolved = nil}
    local function ensureResolved(self)
        if state.resolved then
            return state.resolved
        end
        local api = resolveFn(_G)
        if api then
            state.resolved = api
        end
        return state.resolved
    end
    local function makeChain(self, steps)
        return KProxy(
            _G,
            function(self)
            end,
            {
                get = function(self, _t, prop)
                    if prop == "__isDeferred" then
                        return true
                    end
                    if prop == "__label" then
                        return label
                    end
                    if prop == "then" then
                        return nil
                    end
                    return makeChain(
                        _G,
                        KArrayOps.concat(steps, {{type = "get", key = prop}})
                    )
                end,
                apply = function(self, _t, _thisArg, args)
                    return makeChain(
                        _G,
                        KArrayOps.concat(steps, {{type = "call", args = args or ({})}})
                    )
                end
            }
        )
    end
    return KProxy(
        _G,
        KObject:create(nil),
        {get = function(self, _t, prop)
            local api = ensureResolved(_G)
            if api then
                local v = api[prop]
                if KTypeOf(v) == "function" then
                    return KFunction:bind(v, api)
                end
                return v
            end
            return makeChain(_G, {{type = "get", key = prop}})
        end}
    )
end
local BootstrapDeferredApi = Classes:create()
BootstrapDeferredApi.name = "BootstrapDeferredApi"
BootstrapDeferredApi.prototype.createDeferredProxy = createDeferredProxy
return Classes:construct(BootstrapDeferredApi)
