local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local Classes = luaRuntime.class
local Error = luaRuntime.Error
META = KObject:freeze({
    moduleId = "mesh",
    version = "1.0.2",
    engineMin = "0.1.5",
    description = "RootKit wrapper over ENGINE.mesh(): create() returns object-mesh with physics methods via ENGINE.physics()"
})
MeshOrchestrator = require("./MeshOrchestrator.lua")
function create(self, ENGINE)
    if not ENGINE then
        error(
            Classes:construct(Error, "[MESH] ENGINE is required"),
            0
        )
    end
    local orch = Classes:construct(MeshOrchestrator, ENGINE)
    return orch:decorateMeshApi()
end
M = setmetatable({META = META}, {
    __call = function(_, ...)
        return create(...)
    end
})

return M
