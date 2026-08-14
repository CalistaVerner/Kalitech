local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local Error = luaRuntime.Error
local LuaConstruct = luaRuntime.LuaConstruct
local LuaClass = luaRuntime.LuaClass
local lua_require_result_0 = require("./PlayerPawn.lua")
PlayerPawn = lua_require_result_0.PlayerPawn
function req(self, v, msg)
    if v == nil then
        error(
            LuaConstruct(Error, msg),
            0
        )
    end
    return v
end
PlayerController = LuaClass()
PlayerController.name = "PlayerController"
function PlayerController.prototype.lua_constructor(self, ctx, cfg)
    self.ctx = req(_G, ctx, "[PlayerController] ctx required")
    self.cfg = cfg or nil
    self.pawn = LuaConstruct(PlayerPawn, self.ctx, self.cfg):init()
    local ENGINE = req(_G, _G.ENGINE, "[PlayerController] ENGINE required")
    req(_G, ENGINE.controllers, "[PlayerController] ENGINE.controllers required")
    self.ec = ENGINE.controllers:entity("player", self.ctx, self.pawn, self.cfg)
    if not self.ec then
        error(
            LuaConstruct(Error, "[PlayerController] ENGINE.controllers.entity(...) returned null"),
            0
        )
    end
end
function PlayerController.prototype.update(self, dt)
    local ec = self.ec
    if ec then
        ec:update(dt)
    end
end
function PlayerController.prototype.dispose(self)
    local ec = self.ec
    local pawn = self.pawn
    self.ec = nil
    self.pawn = nil
    self.ctx = nil
    self.cfg = nil
    if ec and KTypeOf(ec.dispose) == "function" then
        do
            pcall(function()
                ec:dispose()
            end)
        end
    end
    if pawn and KTypeOf(pawn.destroy) == "function" then
        do
            pcall(function()
                pawn:destroy()
            end)
        end
    end
end
M = {PlayerController = PlayerController}

return M
