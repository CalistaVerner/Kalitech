local M = {}
local lua_require_result_0 = require("../helpers/ModuleCommon.lua")
requireEngineApi = lua_require_result_0.requireEngineApi
normalizeCfgObject = lua_require_result_0.normalizeCfgObject
create = setmetatable(
    {},
    {__call = function(lua_, self, engine)
        local api = requireEngineApi(_G, engine, "light", "LIGHT")
        return KObject:freeze({
            create = function(self, cfg)
                return api:create(normalizeCfgObject(_G, cfg))
            end,
            set = function(self, handle, cfg)
                api:set(
                    handle,
                    normalizeCfgObject(_G, cfg)
                )
                return handle
            end,
            enable = function(self, handle, enabled)
                api:enable(handle, not not enabled)
                return handle
            end,
            exists = function(self, handle)
                return not not api:exists(handle)
            end,
            destroy = function(self, handle)
                api:destroy(handle)
                return true
            end,
            get = function(self, handle)
                return api:get(handle)
            end,
            list = function(self)
                return api:list()
            end,
            api = api
        })
    end}
)
create.META = {
    moduleId = "light",
    version = "1.0.0",
    description = "Light wrapper for create/set/enable/destroy operations",
    engineMin = "0.1.0"
}
M = create

return M
