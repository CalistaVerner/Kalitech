local M = {}
local lua_require_result_0 = require("../helpers/ModuleCommon.lua")
requireEngineApi = lua_require_result_0.requireEngineApi
create = setmetatable(
    {},
    {__call = function(lua_, self, engine)
        local api = requireEngineApi(_G, engine, "editor", "EDITOR")
        return KObject:freeze({
            enabled = function() return not not api:enabled() end,
            setEnabled = function(lua_, enabled)
                api:setEnabled(not not enabled)
                return true
            end,
            toggle = function()
                api:toggle()
                return true
            end,
            setFlyCam = function(lua_, enabled)
                api:setFlyCam(not not enabled)
                return true
            end,
            setStatsView = function(lua_, enabled)
                api:setStatsView(not not enabled)
                return true
            end,
            api = api
        })
    end}
)
create.META = {
    moduleId = "editor",
    version = "1.0.0",
    description = "Editor wrapper for toggling fly cam and stats overlays",
    engineMin = "0.1.0"
}
M = create

return M
