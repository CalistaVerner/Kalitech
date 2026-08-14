local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local LuaClass = luaRuntime.LuaClass
local LuaConstruct = luaRuntime.LuaConstruct
PlayerCamera = require("../PlayerCamera.lua")
PlayerCameraController = LuaClass()
PlayerCameraController.name = "PlayerCameraController"
function PlayerCameraController.prototype.lua_constructor(self)
    self.ctx = nil
    self.entity = nil
    self.impl = nil
end
function PlayerCameraController.prototype.bind(self, ctx, entity)
    self.ctx = ctx
    self.entity = entity
    return self
end
function PlayerCameraController.prototype.onStart(self)
    self.impl = LuaConstruct(PlayerCamera, self.entity)
    self.impl:attach()
end
function PlayerCameraController.prototype.onUpdate(self, dt)
    local pawn = self.entity
    self.impl:update(pawn.frame)
    pawn.frame.view.yaw = self.impl:getYaw()
    pawn.frame.view.pitch = self.impl:getPitch()
    pawn.frame.view.type = self.impl:getType()
end
function PlayerCameraController.prototype.onStop(self)
    if self.impl and KTypeOf(self.impl.destroy) == "function" then
        self.impl:destroy()
    end
    self.impl = nil
end
M = {PlayerCameraController = PlayerCameraController}

return M
