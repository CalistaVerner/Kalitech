local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local Classes = luaRuntime.class
local Error = luaRuntime.Error
local lua_require_result_0 = require("../helpers/ModuleCommon.lua")
requireEngineApi = lua_require_result_0.requireEngineApi
function requireHandle(self, handle, method)
    if not handle then
        error(
            Classes:construct(
                Error,
                ("[SURFACE] " .. tostring(method)) .. " requires a SurfaceHandle"
            ),
            0
        )
    end
    return handle
end
create = setmetatable(
    {},
    {__call = function(lua_, self, engine)
        local api = requireEngineApi(_G, engine, "surface", "SURFACE")
        return KObject:freeze({
            setMaterial = function(self, handle, materialHandleOrCfg)
                api:setMaterial(
                    requireHandle(_G, handle, "setMaterial"),
                    materialHandleOrCfg
                )
                return handle
            end,
            applyMaterialToChildren = function(self, handle, materialHandle)
                api:applyMaterialToChildren(
                    requireHandle(_G, handle, "applyMaterialToChildren"),
                    materialHandle
                )
                return handle
            end,
            setTransform = function(self, handle, cfg)
                api:setTransform(
                    requireHandle(_G, handle, "setTransform"),
                    cfg
                )
                return handle
            end,
            setShadowMode = function(self, handle, mode)
                api:setShadowMode(
                    requireHandle(_G, handle, "setShadowMode"),
                    tostring(mode)
                )
                return handle
            end,
            attachToRoot = function(self, handle)
                api:attachToRoot(requireHandle(_G, handle, "attachToRoot"))
                return handle
            end,
            detach = function(self, handle)
                api:detach(requireHandle(_G, handle, "detach"))
                return handle
            end,
            destroy = function(self, handle)
                api:destroy(requireHandle(_G, handle, "destroy"))
                return true
            end,
            exists = function(self, handle)
                return not not api:exists(requireHandle(_G, handle, "exists"))
            end,
            setCull = function(self, handle, hint)
                api:setCull(
                    requireHandle(_G, handle, "setCull"),
                    tostring(hint)
                )
                return handle
            end,
            setVisible = function(self, handle, visible)
                api:setVisible(
                    requireHandle(_G, handle, "setVisible"),
                    not not visible
                )
                return handle
            end,
            attachedBody = function(self, surfaceId)
                return api:attachedBody(bit32.bor(surfaceId, 0))
            end,
            getWorldBounds = function(self, handle)
                return api:getWorldBounds(requireHandle(_G, handle, "getWorldBounds"))
            end,
            raycast = function(self, handle, cfg)
                return api:raycast(
                    requireHandle(_G, handle, "raycast"),
                    cfg
                )
            end,
            pickUnderCursor = function(self, handle)
                return api:pickUnderCursor(requireHandle(_G, handle, "pickUnderCursor"))
            end,
            pickUnderCursorCfg = function(self, handle, cfg)
                return api:pickUnderCursorCfg(
                    requireHandle(_G, handle, "pickUnderCursorCfg"),
                    cfg
                )
            end,
            pickWorldUnderCursor = function(self)
                return api:pickUnderCursor()
            end,
            pickWorldUnderCursorCfg = function(self, cfg)
                return api:pickUnderCursorCfg(cfg)
            end,
            attachEntity = function(self, handle, uuid)
                api:attachEntity(
                    requireHandle(_G, handle, "attachEntity"),
                    uuid
                )
                return handle
            end,
            detachFromEntity = function(self, handle)
                api:detachFromEntity(requireHandle(_G, handle, "detachFromEntity"))
                return handle
            end,
            attachedEntityUuid = function(self, handle)
                return api:attachedEntityUuid(requireHandle(_G, handle, "attachedEntityUuid"))
            end,
            api = api
        })
    end}
)
create.META = {
    moduleId = "surface",
    version = "1.0.0",
    description = "Surface wrapper for handle-based operations and picking helpers",
    engineMin = "0.1.0"
}
M = create

return M
