local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local LuaClass = luaRuntime.LuaClass
local Error = luaRuntime.Error
local LuaConstruct = luaRuntime.LuaConstruct
MovementSystem = require("../systems/MovementSystem.lua")
ShootSystem = require("../systems/ShootSystem.lua")
PlayerGameplayController = LuaClass()
PlayerGameplayController.name = "PlayerGameplayController"
function PlayerGameplayController.prototype.lua_constructor(self)
    self.ctx = nil
    self.entity = nil
    self.movement = nil
    self.shoot = nil
end
function PlayerGameplayController.prototype.bind(self, ctx, entity)
    self.ctx = ctx
    self.entity = entity
    return self
end
function PlayerGameplayController.prototype.onStart(self)
    local pawn = self.entity
    if not pawn or KTypeOf(pawn.beginFrame) ~= "function" then
        error(
            LuaConstruct(Error, "[PlayerGameplay] entity must be PlayerPawn (beginFrame)"),
            0
        )
    end
    if KTypeOf(pawn.syncPose) ~= "function" then
        error(
            LuaConstruct(Error, "[PlayerGameplay] entity must be PlayerPawn (syncPose)"),
            0
        )
    end
    if not pawn.frame then
        error(
            LuaConstruct(Error, "[PlayerGameplay] pawn.frame is required"),
            0
        )
    end
    if bit32.bor(pawn.bodyId, 0) <= 0 then
        error(
            LuaConstruct(Error, "[PlayerGameplay] pawn.bodyId must be > 0"),
            0
        )
    end
    self.movement = LuaConstruct(MovementSystem, pawn.cfg and pawn.cfg.movement or nil)
    self.shoot = LuaConstruct(ShootSystem, pawn)
end
function PlayerGameplayController.prototype.onUpdate(self, dt)
    local pawn = self.entity
    pawn:beginFrame(dt)
    pawn:syncPose()
    local frame = pawn.frame
    if not frame.input or KTypeOf(frame.input) ~= "table" then
        error(
            LuaConstruct(Error, "[PlayerGameplay] frame.input must be an object (InputRouter state)"),
            0
        )
    end
    self.movement:update(frame, pawn.characterCfg)
    self.shoot:update(
        frame,
        bit32.bor(pawn.bodyId, 0)
    )
end
function PlayerGameplayController.prototype.onStop(self)
    if self.shoot and KTypeOf(self.shoot.destroy) == "function" then
        self.shoot:destroy()
    end
    self.shoot = nil
    self.movement = nil
end
M = {PlayerGameplayController = PlayerGameplayController}

return M
