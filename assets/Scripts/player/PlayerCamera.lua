local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local Error = luaRuntime.Error
local LuaConstruct = luaRuntime.LuaConstruct
local LuaClass = luaRuntime.LuaClass
function requireEngineCamera(self)
    local E = _G.ENGINE
    if not E then
        error(
            LuaConstruct(Error, "[PlayerCamera] global ENGINE is not available"),
            0
        )
    end
    local cam = E.camera
    if not cam then
        error(
            LuaConstruct(Error, "[PlayerCamera] ENGINE.camera is not registered (camera module missing in manifest)"),
            0
        )
    end
    if KTypeOf(cam.createOrchestrator) ~= "function" then
        error(
            LuaConstruct(Error, "[PlayerCamera] ENGINE.camera.createOrchestrator(player) is required"),
            0
        )
    end
    return cam
end
PlayerCamera = LuaClass()
PlayerCamera.name = "PlayerCamera"
function PlayerCamera.prototype.lua_constructor(self, player)
    if not player then
        error(
            LuaConstruct(Error, "[PlayerCamera] player is required"),
            0
        )
    end
    self.player = player
    self.orch = nil
end
function PlayerCamera.prototype.attach(self)
    if self.orch then
        return
    end
    local cam = requireEngineCamera(_G)
    self.orch = cam:createOrchestrator(self.player)
end
function PlayerCamera.prototype.getType(self)
    local lua_table_orch_0
    if self.orch then
        lua_table_orch_0 = self.orch:getType()
    else
        lua_table_orch_0 = "third"
    end
    return lua_table_orch_0
end
function PlayerCamera.prototype.getYaw(self)
    local lua_table_orch_1
    if self.orch then
        lua_table_orch_1 = self.orch.look.yaw
    else
        lua_table_orch_1 = 0
    end
    return lua_table_orch_1
end
function PlayerCamera.prototype.getPitch(self)
    local lua_table_orch_2
    if self.orch then
        lua_table_orch_2 = self.orch.look.pitch
    else
        lua_table_orch_2 = 0
    end
    return lua_table_orch_2
end
function PlayerCamera.prototype.update(self, frame)
    if self.orch and frame then
        self.orch:update(frame.dt, frame)
    end
end
function PlayerCamera.prototype.destroy(self)
    if not self.orch then
        return
    end
    self.orch:destroy()
    self.orch = nil
end
M = PlayerCamera

return M
