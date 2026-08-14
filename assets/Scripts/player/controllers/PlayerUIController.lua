local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local Error = luaRuntime.Error
local LuaConstruct = luaRuntime.LuaConstruct
local LuaClass = luaRuntime.LuaClass
PlayerUI = require("../PlayerUI.lua")
function req(self, v, msg)
    if v == nil then
        error(
            LuaConstruct(Error, msg),
            0
        )
    end
    return v
end
PlayerUIController = LuaClass()
PlayerUIController.name = "PlayerUIController"
function PlayerUIController.prototype.lua_constructor(self)
    self.ctx = nil
    self.entity = nil
    self.impl = nil
    self._started = false
end
function PlayerUIController.prototype.bind(self, ctx, entity)
    self.ctx = req(_G, ctx, "[PlayerUIController] ctx is required")
    self.entity = req(_G, entity, "[PlayerUIController] entity is required")
    return self
end
function PlayerUIController.prototype.onStart(self)
    if self._started and self.impl then
        return
    end
    local pawn = req(_G, self.entity, "[PlayerUIController] pawn is required")
    local d = req(_G, pawn.d, "[PlayerUIController] pawn.d is required")
    req(_G, d.hud, "[PlayerUIController] domains.hud is required for PlayerUI")
    if d.hudNative and KTypeOf(d.hudNative.setCursorEnabled) == "function" then
        d.hudNative:setCursorEnabled(false, true)
    end
    local ui = LuaConstruct(PlayerUI, pawn):create()
    self.impl = ui
    self._started = true
end
function PlayerUIController.prototype.onUpdate(self, dt)
    if not self.impl then
        self:onStart()
    end
    if not self.impl then
        error(
            LuaConstruct(Error, "[PlayerUIController] UI impl is null after onStart()"),
            0
        )
    end
    self.impl:refresh()
    self.entity:endFrame()
end
function PlayerUIController.prototype.onStop(self)
    if self.impl and KTypeOf(self.impl.destroy) == "function" then
        self.impl:destroy()
    end
    self.impl = nil
    self._started = false
end
M = {PlayerUIController = PlayerUIController}

return M
