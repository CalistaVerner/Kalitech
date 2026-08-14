local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local LuaClass = luaRuntime.LuaClass
local LuaConstruct = luaRuntime.LuaConstruct
PlayerEvents = require("../PlayerEvents.lua")
PlayerEventsController = LuaClass()
PlayerEventsController.name = "PlayerEventsController"
function PlayerEventsController.prototype.lua_constructor(self)
    self.ctx = nil
    self.entity = nil
    self.impl = nil
end
function PlayerEventsController.prototype.bind(self, ctx, entity)
    self.ctx = ctx
    self.entity = entity
    return self
end
function PlayerEventsController.prototype.onStart(self)
    self.impl = LuaConstruct(PlayerEvents, self.entity)
    if self.impl and KTypeOf(self.impl.emit) == "function" then
        local lua_temp_1
        if self.entity and KTypeOf(self.entity.uuidString) == "function" then
            lua_temp_1 = self.entity:uuidString()
        else
            local lua_temp_0
            if self.entity and KTypeOf(self.entity.uuid) == "function" then
                lua_temp_0 = self.entity:uuid()
            else
                lua_temp_0 = self.entity and self.entity.uuid
            end
            lua_temp_1 = lua_temp_0
        end
        local uuid = lua_temp_1
        self.impl:emit("player.spawn", {uuid = uuid, bodyId = self.entity.bodyId})
    end
    local ctx = self.entity.ctx
    if ctx and KTypeOf(ctx.state) == "function" then
        local lua_self_4 = ctx:state()
        local lua_self_4_set_5 = lua_self_4.set
        local lua_temp_3
        if self.entity and KTypeOf(self.entity.uuidString) == "function" then
            lua_temp_3 = self.entity:uuidString()
        else
            local lua_temp_2
            if self.entity and KTypeOf(self.entity.uuid) == "function" then
                lua_temp_2 = self.entity:uuid()
            else
                lua_temp_2 = self.entity and self.entity.uuid
            end
            lua_temp_3 = lua_temp_2
        end
        lua_self_4_set_5(lua_self_4, "player", {alive = true, uuid = lua_temp_3, surfaceId = self.entity.surfaceId, bodyId = self.entity.bodyId})
    end
end
function PlayerEventsController.prototype.onUpdate(self, dt)
    if self.impl and KTypeOf(self.impl.tick) == "function" then
        self.impl:tick(self.entity.frame)
    end
end
function PlayerEventsController.prototype.onStop(self)
    if self.impl and KTypeOf(self.impl.destroy) == "function" then
        self.impl:destroy()
    end
    self.impl = nil
    local ctx = self.entity.ctx
    if ctx and KTypeOf(ctx.state) == "function" then
        ctx:state():remove("player")
    end
end
M = {PlayerEventsController = PlayerEventsController}

return M
