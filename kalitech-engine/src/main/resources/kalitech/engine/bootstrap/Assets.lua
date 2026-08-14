local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local Classes = luaRuntime.class

local function readTextAsset(_, engine, path)
    if not engine or KTypeOf(engine.assets) ~= "function" then
        error("[bootstrap] engine.assets() is required", 0)
    end
    local assets = engine:assets()
    if not assets or KTypeOf(assets.readText) ~= "function" then
        error("[bootstrap] assets.readText(path) is required", 0)
    end
    return assets:readText(path)
end

local BootstrapAssetsApi = Classes:create()
BootstrapAssetsApi.name = "BootstrapAssetsApi"
BootstrapAssetsApi.prototype.readTextAsset = readTextAsset
return Classes:construct(BootstrapAssetsApi)
