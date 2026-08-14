local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local Classes = luaRuntime.class
local Error = luaRuntime.Error
function requireEngineApi(self, engine, methodName, moduleTag)
    if not engine or KTypeOf(engine[methodName]) ~= "function" then
        error(
            Classes:construct(
                Error,
                ((("[" .. tostring(moduleTag)) .. "] engine.") .. tostring(methodName)) .. "() is required"
            ),
            0
        )
    end
    local api = engine[methodName](engine)
    if not api then
        error(
            Classes:construct(
                Error,
                ((("[" .. tostring(moduleTag)) .. "] engine.") .. tostring(methodName)) .. "() returned null"
            ),
            0
        )
    end
    return api
end
function normalizeCfgObject(self, cfg)
    local lua_temp_0
    if cfg and KTypeOf(cfg) == "table" then
        lua_temp_0 = cfg
    else
        lua_temp_0 = {}
    end
    return lua_temp_0
end
function normalizeNullableObject(self, cfg, moduleTag, label)
    if cfg == nil or cfg == nil then
        return nil
    end
    if KTypeOf(cfg) == "table" then
        return cfg
    end
    error(
        Classes:construct(
            Error,
            ((("[" .. tostring(moduleTag)) .. "] ") .. tostring(label)) .. " must be an object or null"
        ),
        0
    )
end
M = KObject:freeze({normalizeCfgObject = normalizeCfgObject, normalizeNullableObject = normalizeNullableObject, requireEngineApi = requireEngineApi})

return M
