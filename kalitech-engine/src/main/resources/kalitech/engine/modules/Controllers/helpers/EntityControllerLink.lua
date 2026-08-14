local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local Classes = luaRuntime.class
local Error = luaRuntime.Error
function req(self, v, msg)
    if v == nil then
        error(
            Classes:construct(Error, msg),
            0
        )
    end
    return v
end
EntityControllerLink = Classes:create()
EntityControllerLink.name = "EntityControllerLink"
function EntityControllerLink.prototype.lua_constructor(self, id, entity, controller)
    self.id = tostring(id or "")
    self.entity = req(_G, entity, "[EntityControllerLink] entity is required")
    self.controller = req(_G, controller, "[EntityControllerLink] controller is required")
    self._alive = true
end
function EntityControllerLink.prototype.setController(self, controller)
    if not self._alive then
        error(
            Classes:construct(Error, "[EntityControllerLink] setController on disposed"),
            0
        )
    end
    self.controller = req(_G, controller, "[EntityControllerLink] controller is required")
    return self
end
function EntityControllerLink.prototype.update(self, dt)
    if not self._alive then
        error(
            Classes:construct(Error, "[EntityControllerLink] update on disposed"),
            0
        )
    end
    local c = self.controller
    if c and KTypeOf(c.update) == "function" then
        c:update(dt)
    end
end
function EntityControllerLink.prototype.dispose(self)
    if not self._alive then
        return
    end
    self._alive = false
    local c = self.controller
    self.controller = nil
    if c and KTypeOf(c.dispose) == "function" then
        c:dispose()
    end
    self.entity = nil
    self.id = ""
end
M = {EntityControllerLink = EntityControllerLink}

return M
