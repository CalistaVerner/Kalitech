local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local Classes = luaRuntime.class
local lua_require_result_0 = require("./helpers/EntUtil.lua")
req = lua_require_result_0.req
local lua_require_result_1 = require("./helpers/EntApi.lua")
EntApi = lua_require_result_1.EntApi
create = setmetatable(
    {},
    {__call = function(lua_, self, engine, K)
        req(_G, engine, "[ENT] engine is required")
        local api = Classes:construct(EntApi, engine, K)
        return KObject:freeze({
            create = KFunction:bind(api.create, api),
            ["$"] = KFunction:bind(api["$"], api),
            ["player$"] = KFunction:bind(api["player$"], api),
            ["capsule$"] = KFunction:bind(api["capsule$"], api),
            ["box$"] = KFunction:bind(api["box$"], api),
            ["sphere$"] = KFunction:bind(api["sphere$"], api),
            preset = KFunction:bind(api.preset, api),
            bodyDefaults = KFunction:bind(api.bodyDefaults, api),
            presets = KFunction:bind(api.presets, api),
            idOf = KFunction:bind(api.idOf, api),
            uuidOf = KFunction:bind(api.uuidOf, api)
        })
    end}
)
create.META = {
    moduleId = "entity",
    version = "2.0.2",
    description = "Declarative entity builder (UUID-only). Returns {core, handle}. Core mirrors Java snapshot/components.",
    engineMin = "0.2.0"
}
M = create
M.META = create.META

return M
