local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local Strings = luaRuntime.string
local Classes = luaRuntime.class
local Error = luaRuntime.Error
local lua_require_result_0 = require("../helpers/ModuleCommon.lua")
requireEngineApi = lua_require_result_0.requireEngineApi
normalizeNullableObject = lua_require_result_0.normalizeNullableObject
function normalizePath(self, path, label)
    local p = Strings:trim(tostring(path or ""))
    if not p then
        error(
            Classes:construct(
                Error,
                ("[ASSETS] " .. tostring(label)) .. " is required"
            ),
            0
        )
    end
    return p
end
function normalizeCfg(self, cfg)
    return normalizeNullableObject(_G, cfg, "ASSETS", "cfg")
end
create = setmetatable(
    {},
    {__call = function(lua_, self, engine)
        if not engine then
            error(
                Classes:construct(Error, "[ASSETS] engine is required"),
                0
            )
        end
        local api = requireEngineApi(_G, engine, "assets", "ASSETS")
        if KTypeOf(api.readText) ~= "function" then
            error(
                Classes:construct(Error, "[ASSETS] engine.assets() must provide readText(path)"),
                0
            )
        end
        if KTypeOf(api.loadModel) ~= "function" then
            error(
                Classes:construct(Error, "[ASSETS] engine.assets() must provide loadModel(path, cfg)"),
                0
            )
        end
        local function readText(self, path)
            return api:readText(normalizePath(_G, path, "path"))
        end
        local function readLuaVerified(self, path)
            local p = normalizePath(_G, path, "path")
            if KTypeOf(api.readLuaVerified) == "function" then
                return api:readLuaVerified(p)
            end
            return api:readText(p)
        end
        local function loadModel(self, path, cfg)
            local p = normalizePath(_G, path, "path")
            local c = normalizeCfg(_G, cfg)
            return api:loadModel(p, c)
        end
        local function enabled(self)
            return not not api
        end
        local function host(self)
            return api
        end
        return KObject:freeze({
            enabled = enabled,
            readText = readText,
            readLuaVerified = readLuaVerified,
            loadModel = loadModel,
            host = host
        })
    end}
)
create.META = {
    moduleId = "assets",
    version = "1.1.0",
    description = "Assets wrapper for readText/readLuaVerified/loadModel with strict argument checks and safe arity",
    engineMin = "0.1.0"
}
M = create

return M
