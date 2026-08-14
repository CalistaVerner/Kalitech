local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local Error = luaRuntime.Error
local LuaConstruct = luaRuntime.LuaConstruct
function surfaceId(self, handle)
    if not handle or KTypeOf(handle.id) ~= "function" then
        error(
            LuaConstruct(Error, "[MSH] SurfaceHandle must provide id()"),
            0
        )
    end
    local sid = bit32.bor(
        handle:id(),
        0
    )
    if sid <= 0 then
        error(
            LuaConstruct(
                Error,
                "[MSH] invalid surfaceId=" .. tostring(sid)
            ),
            0
        )
    end
    return sid
end
function requireSurface(self, engine)
    local s = engine:surface()
    if not s or KTypeOf(s.attachedBody) ~= "function" then
        error(
            LuaConstruct(Error, "[MSH] ENGINE.surface().attachedBody(surfaceId) is required"),
            0
        )
    end
    return s
end
function requirePhysics(self, engine)
    local p = engine:physics()
    if not p then
        error(
            LuaConstruct(Error, "[MSH] ENGINE.physics() is required"),
            0
        )
    end
    return p
end
function resolveBodyId(self, engine, sid)
    local s = requireSurface(_G, engine)
    local bid = bit32.bor(
        s:attachedBody(sid),
        0
    )
    if bid <= 0 then
        error(
            LuaConstruct(Error, "[MSH] surface has no physics body (bodyId=0)"),
            0
        )
    end
    return bid
end
M = KObject:freeze({surfaceId = surfaceId, requireSurface = requireSurface, requirePhysics = requirePhysics, resolveBodyId = resolveBodyId})

return M
