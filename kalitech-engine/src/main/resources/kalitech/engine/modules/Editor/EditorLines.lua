local M = {}
local lua_require_result_0 = require("../helpers/ModuleCommon.lua")
requireEngineApi = lua_require_result_0.requireEngineApi
normalizeCfgObject = lua_require_result_0.normalizeCfgObject
create = setmetatable(
    {},
    {__call = function(lua_, self, engine)
        local api = requireEngineApi(_G, engine, "editorLines", "EDITOR_LINES")
        return KObject:freeze({
            createGridPlane = function(self, cfg)
                return api:createGridPlane(normalizeCfgObject(_G, cfg))
            end,
            destroy = function(self, handle)
                api:destroy(handle)
                return true
            end,
            api = api
        })
    end}
)
create.META = {
    moduleId = "editorLines",
    version = "1.0.0",
    description = "Editor lines wrapper for grid plane helpers",
    engineMin = "0.1.0"
}
M = create

return M
