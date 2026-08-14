local M = {}
local lua_require_result_0 = require("@builtin/modules/helpers/ModuleCommon.lua")
requireEngineApi = lua_require_result_0.requireEngineApi
normalizeCfgObject = lua_require_result_0.normalizeCfgObject
create = setmetatable(
    {},
    {__call = function(lua_, self, engine)
        local api = requireEngineApi(_G, engine, "render", "RENDER")
        return KObject:freeze({
            ensureScene = function(self)
                api:ensureScene()
                return true
            end,
            ambient = function(self, cfg)
                api:ambientCfg(normalizeCfgObject(_G, cfg))
                return true
            end,
            sun = function(self, cfg)
                api:sunCfg(normalizeCfgObject(_G, cfg))
                return true
            end,
            sunShadows = function(self, cfg)
                api:sunShadowsCfg(normalizeCfgObject(_G, cfg))
                return true
            end,
            fog = function(self, cfg)
                api:fogCfg(normalizeCfgObject(_G, cfg))
                return true
            end,
            post = function(self, cfg)
                api:postCfg(normalizeCfgObject(_G, cfg))
                return true
            end,
            api = api
        })
    end}
)
create.META = {
    moduleId = "render",
    version = "1.0.0",
    description = "Render wrapper for scene setup, lighting, fog and post-processing",
    engineMin = "0.1.0"
}
M = create

return M
