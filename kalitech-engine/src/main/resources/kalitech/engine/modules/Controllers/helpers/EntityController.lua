local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local Error = luaRuntime.Error
local LuaConstruct = luaRuntime.LuaConstruct
local LuaClass = luaRuntime.LuaClass
function req(self, v, msg)
    if v == nil then
        error(
            LuaConstruct(Error, msg),
            0
        )
    end
    return v
end
EntityController = LuaClass()
EntityController.name = "EntityController"
function EntityController.prototype.lua_constructor(self, ctx, entity, stack)
    self.ctx = req(_G, ctx, "[EntityController] ctx is required")
    self.entity = req(_G, entity, "[EntityController] entity is required")
    self.stack = req(_G, stack, "[EntityController] stack is required")
    self._alive = true
    if KTypeOf(self.stack.bind) == "function" then
        self.stack:bind(self.ctx, self.entity)
    end
end
function EntityController.prototype.update(self, dt)
    if not self._alive then
        error(
            LuaConstruct(Error, "[EntityController] update on disposed"),
            0
        )
    end
    if self.stack then
        self.stack:_tick(dt)
    end
end
function EntityController.prototype.dispose(self)
    if not self._alive then
        return
    end
    self._alive = false
    if self.stack and KTypeOf(self.stack._shutdown) == "function" then
        self.stack:_shutdown()
    end
    self.stack = nil
    self.ctx = nil
    self.entity = nil
end
M = {EntityController = EntityController}

return M
