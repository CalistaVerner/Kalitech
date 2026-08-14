local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local LuaClass = luaRuntime.LuaClass
local Error = luaRuntime.Error
local LuaConstruct = luaRuntime.LuaConstruct
local LuaArraySetLength = luaRuntime.LuaArraySetLength
U = require("./util.lua")
PlayerEvents = LuaClass()
PlayerEvents.name = "PlayerEvents"
function PlayerEvents.prototype.lua_constructor(self, player)
    self.player = player
    local cfg = player.cfg and player.cfg.events or KObject:create(nil)
    local lua_temp_0
    if cfg.enabled ~= nil then
        lua_temp_0 = not not cfg.enabled
    else
        lua_temp_0 = true
    end
    self.enabled = lua_temp_0
    local lua_table_enabled_1
    if self.enabled then
        lua_table_enabled_1 = player.d.bus
    else
        lua_table_enabled_1 = nil
    end
    self.bus = lua_table_enabled_1
    if self.enabled and not self.bus then
        error(
            LuaConstruct(Error, "[PlayerEvents] enabled but bus missing"),
            0
        )
    end
    self._subs = {}
    self._wasGrounded = false
end
function PlayerEvents.prototype.on(self, topic, fn)
    if not self.enabled then
        return 0
    end
    local id = bit32.bor(
        self.bus:on(topic, fn),
        0
    )
    if id then
        local lua_self__subs_2 = self._subs
        lua_self__subs_2[#lua_self__subs_2 + 1] = id
    end
    return id
end
function PlayerEvents.prototype.emit(self, topic, payload)
    if not self.enabled then
        return
    end
    self.bus:emit(topic, payload)
end
function PlayerEvents.prototype.tick(self, frame)
    if not self.enabled then
        return
    end
    local grounded = not not frame.pose.grounded
    if grounded ~= self._wasGrounded then
        if grounded then
            self:emit(
                "player.land",
                {fallSpeed = U:num(frame.pose.fallSpeed, 0)}
            )
        else
            self:emit("player.air", {})
        end
        self._wasGrounded = grounded
    end
    if frame.input.jump then
        self:emit("player.jump", {})
    end
end
function PlayerEvents.prototype.destroy(self)
    if not self.enabled then
        return
    end
    if KTypeOf(self.bus.off) ~= "function" then
        error(
            LuaConstruct(Error, "[PlayerEvents] bus.off(id) required"),
            0
        )
    end
    do
        local i = 0
        while i < #self._subs do
            self.bus:off(bit32.bor(self._subs[i + 1], 0))
            i = i + 1
        end
    end
    LuaArraySetLength(self._subs, 0)
    self._wasGrounded = false
end
M = PlayerEvents

return M
