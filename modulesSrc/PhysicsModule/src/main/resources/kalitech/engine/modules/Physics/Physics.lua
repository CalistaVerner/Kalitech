local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local LuaClass = luaRuntime.LuaClass
local Error = luaRuntime.Error
local LuaConstruct = luaRuntime.LuaConstruct
local lua_require_result_0 = require("./PhysicsOrchestrator.lua")
PhysicsOrchestrator = lua_require_result_0.PhysicsOrchestrator
local lua_require_result_1 = require("./helpers/PhysicsEvents.lua")
createPhysicsEvents = lua_require_result_1.createPhysicsEvents
EnginePhysics = LuaClass()
EnginePhysics.name = "EnginePhysics"
function EnginePhysics.prototype.lua_constructor(self, ENGINE)
    if not ENGINE then
        error(
            LuaConstruct(Error, "[ENGINE.physics] ENGINE is required"),
            0
        )
    end
    self._orch = LuaConstruct(PhysicsOrchestrator, ENGINE)
    self.events = createPhysicsEvents(_G, ENGINE, self)
    KObject:freeze(self.events)
    KObject:freeze(self)
end
function EnginePhysics.prototype.raw(self)
    return self._orch:raw()
end
function EnginePhysics.prototype.body(self, cfg)
    return self._orch:body(cfg)
end
function EnginePhysics.prototype.remove(self, h)
    return self._orch:remove(h)
end
function EnginePhysics.prototype.removeById(self, id)
    return self._orch:removeById(id)
end
function EnginePhysics.prototype.bodyOfSurface(self, surface)
    return self._orch:bodyOfSurface(surface)
end
function EnginePhysics.prototype.handle(self, h)
    return self._orch:handle(h)
end
function EnginePhysics.prototype.exists(self, h)
    return self._orch:exists(h)
end
function EnginePhysics.prototype.ensureBodyForSurface(self, surface, cfg)
    return self._orch:ensureBodyForSurface(surface, cfg)
end
function EnginePhysics.prototype.position(self, h)
    return self._orch:position(h)
end
function EnginePhysics.prototype.teleport(self, h, v)
    return self._orch:teleport(h, v)
end
function EnginePhysics.prototype.warp(self, h, v)
    return self._orch:warp(h, v)
end
function EnginePhysics.prototype.velocity(self, h, v)
    return self._orch:velocity(h, v)
end
function EnginePhysics.prototype.angularVelocity(self, h, v)
    return self._orch:angularVelocity(h, v)
end
function EnginePhysics.prototype.yaw(self, h, y)
    return self._orch:yaw(h, y)
end
function EnginePhysics.prototype.applyImpulse(self, h, i)
    return self._orch:applyImpulse(h, i)
end
function EnginePhysics.prototype.applyCentralForce(self, h, f)
    return self._orch:applyCentralForce(h, f)
end
function EnginePhysics.prototype.applyTorque(self, h, t)
    return self._orch:applyTorque(h, t)
end
function EnginePhysics.prototype.clearForces(self, h)
    return self._orch:clearForces(h)
end
function EnginePhysics.prototype.lockRotation(self, h, l)
    return self._orch:lockRotation(h, l)
end
function EnginePhysics.prototype.setKinematic(self, h, k)
    return self._orch:setKinematic(h, k)
end
function EnginePhysics.prototype.collisionGroups(self, h, group, mask)
    return self._orch:collisionGroups(h, group, mask)
end
function EnginePhysics.prototype.raycast(self, cfg)
    return self._orch:raycast(cfg)
end
function EnginePhysics.prototype.raycastEx(self, cfg)
    return self._orch:raycastEx(cfg)
end
function EnginePhysics.prototype.raycastAll(self, cfg)
    return self._orch:raycastAll(cfg)
end
function EnginePhysics.prototype.sweepSphere(self, cfg)
    return self._orch:sweepSphere(cfg)
end
function EnginePhysics.prototype.sweepCapsule(self, cfg)
    return self._orch:sweepCapsule(cfg)
end
function EnginePhysics.prototype.debug(self, e)
    return self._orch:debug(e)
end
function EnginePhysics.prototype.gravity(self, g)
    return self._orch:gravity(g)
end
function EnginePhysics.prototype.on(self, topic, fn)
    return self._orch:on(topic, fn)
end
function EnginePhysics.prototype.idOf(self, h)
    return self._orch:idOf(h)
end
function EnginePhysics.prototype.surfaceIdOf(self, h)
    return self._orch:surfaceIdOf(h)
end
function EnginePhysics.prototype.ref(self, h)
    return self._orch:ref(h)
end
function create(self, ENGINE)
    if not ENGINE then
        error(
            LuaConstruct(Error, "[ENGINE.physics] ENGINE is required"),
            0
        )
    end
    return LuaConstruct(EnginePhysics, ENGINE)
end
M = setmetatable({
    EnginePhysics = EnginePhysics,
    META = KObject:freeze({moduleId = "physics", version = "2.0.7", description = "ENGINE-only physics module (PhysicsApiImpl-aligned)", engineMin = "0.2.0"})
}, {
    __call = function(_, ...)
        return create(...)
    end
})

return M
