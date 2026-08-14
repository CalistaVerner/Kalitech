local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local Error = luaRuntime.Error
local LuaConstruct = luaRuntime.LuaConstruct
SkySystem = require("./SkySystem.lua")
SYS = nil
function isFn(self, f)
    return KTypeOf(f) == "function"
end
function resolveEngineApi(self, ctx)
    if not ctx or not ctx.engine or not isFn(_G, ctx.engine.api) then
        error(
            LuaConstruct(Error, "[sky] ctx.engine.api() is required"),
            0
        )
    end
    local api = ctx.engine:api()
    if not api then
        error(
            LuaConstruct(Error, "[sky] ctx.engine.api() returned null"),
            0
        )
    end
    return api
end
M = {
    init = function(self, ctx)
        local E = resolveEngineApi(_G, ctx)
        SYS = LuaConstruct(SkySystem, E)
        SYS:init(ctx)
    end,
    update = function(self, ctx, tpf)
        if not SYS then
            error(
                LuaConstruct(Error, "[sky] not initialized"),
                0
            )
        end
        SYS:update(ctx, tpf)
    end,
    destroy = function(self)
        if not SYS then
            return
        end
        SYS:destroy()
        SYS = nil
    end
}

return M
