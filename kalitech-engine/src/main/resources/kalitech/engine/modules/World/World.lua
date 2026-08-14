local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local Classes = luaRuntime.class
local lua_require_result_0 = require("./helpers/WorldUtil.lua")
req = lua_require_result_0.req
local lua_require_result_1 = require("./helpers/WorldApi.lua")
WorldApi = lua_require_result_1.WorldApi
create = setmetatable(
    {},
    {__call = function(lua_, self, engine, K)
        req(_G, engine, "[WORLD] engine is required")
        local api = Classes:construct(WorldApi, engine, K)
        local publicApi = KObject:freeze({
            create = KFunction:bind(api.create, api),
            boot = KFunction:bind(api.boot, api),
            ["$"] = KFunction:bind(api["$"], api),
            env = KFunction:bind(api.env, api),
            getWorldTime = KFunction:bind(api.getWorldTime, api),
            normalize = KFunction:bind(api.normalize, api),
            builder = KFunction:bind(api.builder, api)
        })
        return publicApi
    end}
)
create.META = {
    moduleId = "world",
    id = "world",
    version = "2.5.0",
    description = ("World bootstrap DSL. Object-mode via ENGINE.world.$(). " .. "Pure env seed via ENGINE.world.env() (no IO). ") .. "Explicit systems only. Read-only access to world time via ENGINE.world.getWorldTime().",
    engineMin = "0.2.0",
    changelog = {"2.5.0: world time snapshots now include frame/tick indices and dt/interpolation fields."},
}
M = create
M.META = create.META

return M
