local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local Tables = luaRuntime.table
local Classes = luaRuntime.class
local Error = luaRuntime.Error
local lua_require_result_0 = require("./helpers/PhysicsMath.lua")
vec3Obj = lua_require_result_0.vec3Obj
vec3Arr = lua_require_result_0.vec3Arr
num = lua_require_result_0.num
warn = lua_require_result_0.warn
local lua_require_result_1 = require("./helpers/PhysicsIds.lua")
bodyIdOf = lua_require_result_1.bodyIdOf
surfaceIdOf = lua_require_result_1.surfaceIdOf
PhysicsOrchestrator = Classes:create()
PhysicsOrchestrator.name = "PhysicsOrchestrator"
function PhysicsOrchestrator.prototype.lua_constructor(self, backend)
    if not backend then
        error(
            Classes:construct(Error, "[ENGINE.physics] backend is required"),
            0
        )
    end
    local phys = self:_resolveBackend(backend)
    self._phys = phys
    self:_reqFn(phys, "body", "[ENGINE.physics] backend.body(cfg) missing")
    self:_reqFn(phys, "remove", "[ENGINE.physics] backend.remove(handleOrId) missing")
    self:_reqFn(phys, "position", "[ENGINE.physics] backend.position(handleOrId) missing")
    self:_reqFn(phys, "applyImpulse", "[ENGINE.physics] backend.applyImpulse(handleOrId,vec3) missing")
    self:_reqFn(phys, "lockRotation", "[ENGINE.physics] backend.lockRotation(handleOrId,bool) missing")
    local hasWarp = KTypeOf(phys.warp) == "function"
    local hasTeleport = KTypeOf(phys.teleport) == "function"
    if not hasWarp and not hasTeleport then
        error(
            Classes:construct(Error, "[ENGINE.physics] backend.warp(handleOrId,vec3) missing"),
            0
        )
    end
    local lua_hasTeleport_2
    if hasTeleport then
        lua_hasTeleport_2 = function(lua_, id, v) return phys:teleport(id, v) end
    else
        lua_hasTeleport_2 = function(lua_, id, v) return phys:warp(id, v) end
    end
    self._teleportImpl = lua_hasTeleport_2
    local hasVelocity = KTypeOf(phys.velocity) == "function"
    local hasGetSetVelocity = KTypeOf(phys.getVelocity) == "function" and KTypeOf(phys.setVelocity) == "function"
    if not hasVelocity and not hasGetSetVelocity then
        error(
            Classes:construct(Error, "[ENGINE.physics] backend.velocity(handleOrId[,vec3]) or getVelocity/setVelocity missing"),
            0
        )
    end
    local lua_hasVelocity_3
    if hasVelocity then
        lua_hasVelocity_3 = function(lua_, id) return phys:velocity(id) end
    else
        lua_hasVelocity_3 = function(lua_, id) return phys:getVelocity(id) end
    end
    self._velGetImpl = lua_hasVelocity_3
    local lua_hasVelocity_4
    if hasVelocity then
        lua_hasVelocity_4 = function(lua_, id, v) return phys:velocity(id, v) end
    else
        lua_hasVelocity_4 = function(lua_, id, v) return phys:setVelocity(id, v) end
    end
    self._velSetImpl = lua_hasVelocity_4
    local hasAngVel = KTypeOf(phys.angularVelocity) == "function"
    local hasGetSetAngVel = KTypeOf(phys.getAngularVelocity) == "function" and KTypeOf(phys.setAngularVelocity) == "function"
    local lua_hasAngVel_6
    if hasAngVel then
        lua_hasAngVel_6 = function(lua_, id) return phys:angularVelocity(id) end
    else
        local lua_hasGetSetAngVel_5
        if hasGetSetAngVel then
            lua_hasGetSetAngVel_5 = function(lua_, id) return phys:getAngularVelocity(id) end
        else
            lua_hasGetSetAngVel_5 = nil
        end
        lua_hasAngVel_6 = lua_hasGetSetAngVel_5
    end
    self._angGetImpl = lua_hasAngVel_6
    local lua_hasAngVel_8
    if hasAngVel then
        lua_hasAngVel_8 = function(lua_, id, v) return phys:angularVelocity(id, v) end
    else
        local lua_hasGetSetAngVel_7
        if hasGetSetAngVel then
            lua_hasGetSetAngVel_7 = function(lua_, id, v) return phys:setAngularVelocity(id, v) end
        else
            lua_hasGetSetAngVel_7 = nil
        end
        lua_hasAngVel_8 = lua_hasGetSetAngVel_7
    end
    self._angSetImpl = lua_hasAngVel_8
end
function PhysicsOrchestrator.prototype._resolveBackend(self, backend)
    if KTypeOf(backend.physics) == "function" then
        local p = backend:physics()
        if not p or KTypeOf(p) ~= "table" then
            error(
                Classes:construct(Error, "[ENGINE.physics] backend.physics() returned invalid object"),
                0
            )
        end
        return p
    end
    if KTypeOf(backend.api) == "function" then
        local api = backend:api()
        if api and KTypeOf(api.physics) == "function" then
            local p = api:physics()
            if not p or KTypeOf(p) ~= "table" then
                error(
                    Classes:construct(Error, "[ENGINE.physics] backend.api().physics() returned invalid object"),
                    0
                )
            end
            return p
        end
    end
    if backend and KTypeOf(backend) == "table" then
        return backend
    end
    error(
        Classes:construct(Error, "[ENGINE.physics] invalid backend"),
        0
    )
end
function PhysicsOrchestrator.prototype._reqFn(self, obj, name, msg)
    if KTypeOf(obj[name]) ~= "function" then
        error(
            Classes:construct(Error, msg),
            0
        )
    end
end
function PhysicsOrchestrator.prototype.raw(self)
    return self._phys
end
function PhysicsOrchestrator.prototype.body(self, cfg)
    return self._phys:body(cfg)
end
function PhysicsOrchestrator.prototype.remove(self, h)
    local id = bodyIdOf(_G, h)
    if id <= 0 then
        error(
            Classes:construct(Error, "[ENGINE.physics] remove(): invalid body id"),
            0
        )
    end
    return self._phys:remove(id)
end
function PhysicsOrchestrator.prototype.removeById(self, id)
    id = bit32.bor(id, 0)
    if id <= 0 then
        error(
            Classes:construct(Error, "[ENGINE.physics] removeById(): invalid body id"),
            0
        )
    end
    return self._phys:remove(id)
end
function PhysicsOrchestrator.prototype.bodyOfSurface(self, surfaceHandleOrId)
    if KTypeOf(self._phys.bodyOfSurface) ~= "function" then
        return 0
    end
    local sid = surfaceIdOf(_G, surfaceHandleOrId)
    if sid <= 0 then
        return 0
    end
    return bit32.bor(
        self._phys:bodyOfSurface(sid),
        0
    )
end
function PhysicsOrchestrator.prototype.handle(self, h)
    if KTypeOf(self._phys.handle) ~= "function" then
        return nil
    end
    local id = bodyIdOf(_G, h)
    if id <= 0 then
        return nil
    end
    return self._phys:handle(id)
end
function PhysicsOrchestrator.prototype.exists(self, h)
    if KTypeOf(self._phys.exists) ~= "function" then
        return bodyIdOf(_G, h) > 0
    end
    local id = bodyIdOf(_G, h)
    if id <= 0 then
        return false
    end
    return not not self._phys:exists(id)
end
function PhysicsOrchestrator.prototype.position(self, h)
    local id = bodyIdOf(_G, h)
    if id <= 0 then
        error(
            Classes:construct(Error, "[ENGINE.physics] position(): invalid body id"),
            0
        )
    end
    return self._phys:position(id)
end
function PhysicsOrchestrator.prototype.teleport(self, h, v)
    local id = bodyIdOf(_G, h)
    if id <= 0 then
        error(
            Classes:construct(Error, "[ENGINE.physics] teleport(): invalid body id"),
            0
        )
    end
    return self:_teleportImpl(
        id,
        vec3Obj(
            _G,
            v,
            0,
            0,
            0
        )
    )
end
function PhysicsOrchestrator.prototype.warp(self, h, v)
    local id = bodyIdOf(_G, h)
    if id <= 0 then
        error(
            Classes:construct(Error, "[ENGINE.physics] warp(): invalid body id"),
            0
        )
    end
    if KTypeOf(self._phys.warp) == "function" then
        return self._phys:warp(
            id,
            vec3Obj(
                _G,
                v,
                0,
                0,
                0
            )
        )
    end
    return self:_teleportImpl(
        id,
        vec3Obj(
            _G,
            v,
            0,
            0,
            0
        )
    )
end
function PhysicsOrchestrator.prototype.velocity(self, h, v)
    local id = bodyIdOf(_G, h)
    if id <= 0 then
        error(
            Classes:construct(Error, "[ENGINE.physics] velocity(): invalid body id"),
            0
        )
    end
    if v == nil then
        return self:_velGetImpl(id)
    end
    return self:_velSetImpl(
        id,
        vec3Obj(
            _G,
            v,
            0,
            0,
            0
        )
    )
end
function PhysicsOrchestrator.prototype.angularVelocity(self, h, v)
    local id = bodyIdOf(_G, h)
    if id <= 0 then
        error(
            Classes:construct(Error, "[ENGINE.physics] angularVelocity(): invalid body id"),
            0
        )
    end
    if not self._angGetImpl or not self._angSetImpl then
        error(
            Classes:construct(Error, "[ENGINE.physics] backend.angularVelocity(handleOrId[,vec3]) missing"),
            0
        )
    end
    if v == nil then
        return self:_angGetImpl(id)
    end
    return self:_angSetImpl(
        id,
        vec3Obj(
            _G,
            v,
            0,
            0,
            0
        )
    )
end
function PhysicsOrchestrator.prototype.yaw(self, h, yawRad)
    local id = bodyIdOf(_G, h)
    if id <= 0 then
        error(
            Classes:construct(Error, "[ENGINE.physics] yaw(): invalid body id"),
            0
        )
    end
    if KTypeOf(self._phys.yaw) ~= "function" then
        error(
            Classes:construct(Error, "[ENGINE.physics] backend.yaw(handleOrId,yawRad) missing"),
            0
        )
    end
    return self._phys:yaw(
        id,
        num(_G, yawRad, 0)
    )
end
function PhysicsOrchestrator.prototype.applyImpulse(self, h, impulse)
    local id = bodyIdOf(_G, h)
    if id <= 0 then
        error(
            Classes:construct(Error, "[ENGINE.physics] applyImpulse(): invalid body id"),
            0
        )
    end
    return self._phys:applyImpulse(
        id,
        vec3Obj(
            _G,
            impulse,
            0,
            0,
            0
        )
    )
end
function PhysicsOrchestrator.prototype.applyCentralForce(self, h, force)
    local id = bodyIdOf(_G, h)
    if id <= 0 then
        error(
            Classes:construct(Error, "[ENGINE.physics] applyCentralForce(): invalid body id"),
            0
        )
    end
    if KTypeOf(self._phys.applyCentralForce) ~= "function" then
        error(
            Classes:construct(Error, "[ENGINE.physics] backend.applyCentralForce(handleOrId,vec3) missing"),
            0
        )
    end
    return self._phys:applyCentralForce(
        id,
        vec3Obj(
            _G,
            force,
            0,
            0,
            0
        )
    )
end
function PhysicsOrchestrator.prototype.applyTorque(self, h, torque)
    local id = bodyIdOf(_G, h)
    if id <= 0 then
        error(
            Classes:construct(Error, "[ENGINE.physics] applyTorque(): invalid body id"),
            0
        )
    end
    if KTypeOf(self._phys.applyTorque) ~= "function" then
        error(
            Classes:construct(Error, "[ENGINE.physics] backend.applyTorque(handleOrId,vec3) missing"),
            0
        )
    end
    return self._phys:applyTorque(
        id,
        vec3Obj(
            _G,
            torque,
            0,
            0,
            0
        )
    )
end
function PhysicsOrchestrator.prototype.clearForces(self, h)
    local id = bodyIdOf(_G, h)
    if id <= 0 then
        error(
            Classes:construct(Error, "[ENGINE.physics] clearForces(): invalid body id"),
            0
        )
    end
    if KTypeOf(self._phys.clearForces) ~= "function" then
        error(
            Classes:construct(Error, "[ENGINE.physics] backend.clearForces(handleOrId) missing"),
            0
        )
    end
    return self._phys:clearForces(id)
end
function PhysicsOrchestrator.prototype.lockRotation(self, h, lock)
    local id = bodyIdOf(_G, h)
    if id <= 0 then
        error(
            Classes:construct(Error, "[ENGINE.physics] lockRotation(): invalid body id"),
            0
        )
    end
    return self._phys:lockRotation(id, not not lock)
end
function PhysicsOrchestrator.prototype.setKinematic(self, h, kinematic)
    local id = bodyIdOf(_G, h)
    if id <= 0 then
        error(
            Classes:construct(Error, "[ENGINE.physics] setKinematic(): invalid body id"),
            0
        )
    end
    if KTypeOf(self._phys.setKinematic) ~= "function" then
        error(
            Classes:construct(Error, "[ENGINE.physics] backend.setKinematic(handleOrId,bool) missing"),
            0
        )
    end
    return self._phys:setKinematic(id, not not kinematic)
end
function PhysicsOrchestrator.prototype.collisionGroups(self, h, group, mask)
    local id = bodyIdOf(_G, h)
    if id <= 0 then
        error(
            Classes:construct(Error, "[ENGINE.physics] collisionGroups(): invalid body id"),
            0
        )
    end
    if KTypeOf(self._phys.collisionGroups) ~= "function" then
        error(
            Classes:construct(Error, "[ENGINE.physics] backend.collisionGroups(handleOrId,group,mask) missing"),
            0
        )
    end
    return self._phys:collisionGroups(
        id,
        bit32.bor(group, 0),
        bit32.bor(mask, 0)
    )
end
function PhysicsOrchestrator.prototype._ray(self, cfg)
    local c = Tables:merge({}, cfg)
    c.from = vec3Arr(
        _G,
        c.from,
        0,
        0,
        0
    )
    c.to = vec3Arr(
        _G,
        c.to,
        0,
        -1,
        0
    )
    return c
end
function PhysicsOrchestrator.prototype._sweep(self, cfg)
    local c = Tables:merge({}, cfg)
    c.from = vec3Arr(
        _G,
        c.from,
        0,
        0,
        0
    )
    c.to = vec3Arr(
        _G,
        c.to,
        0,
        -1,
        0
    )
    if c.mask ~= nil then
        c.mask = bit32.bor(c.mask, 0)
    end
    if c.group ~= nil then
        c.group = bit32.bor(c.group, 0)
    end
    if c.ignoreBody ~= nil then
        c.ignoreBody = bit32.bor(c.ignoreBody, 0)
    end
    if c.ignoreSurface ~= nil then
        c.ignoreSurface = bit32.bor(c.ignoreSurface, 0)
    end
    return c
end
function PhysicsOrchestrator.prototype.raycast(self, cfg)
    if KTypeOf(self._phys.raycast) ~= "function" then
        error(
            Classes:construct(Error, "[ENGINE.physics] backend.raycast(cfg) missing"),
            0
        )
    end
    return self._phys:raycast(self:_ray(cfg))
end
function PhysicsOrchestrator.prototype.raycastEx(self, cfg)
    if KTypeOf(self._phys.raycastEx) ~= "function" then
        error(
            Classes:construct(Error, "[ENGINE.physics] backend.raycastEx(cfg) missing"),
            0
        )
    end
    return self._phys:raycastEx(self:_ray(cfg))
end
function PhysicsOrchestrator.prototype.raycastAll(self, cfg)
    if KTypeOf(self._phys.raycastAll) ~= "function" then
        error(
            Classes:construct(Error, "[ENGINE.physics] backend.raycastAll(cfg) missing"),
            0
        )
    end
    return self._phys:raycastAll(self:_ray(cfg))
end
function PhysicsOrchestrator.prototype.sweepSphere(self, cfg)
    if KTypeOf(self._phys.sweepSphere) ~= "function" then
        error(
            Classes:construct(Error, "[ENGINE.physics] backend.sweepSphere(cfg) missing"),
            0
        )
    end
    local c = self:_sweep(cfg)
    c.radius = num(_G, c.radius, 0)
    if not (c.radius > 0) then
        error(
            Classes:construct(Error, "[ENGINE.physics] sweepSphere: cfg.radius must be > 0"),
            0
        )
    end
    return self._phys:sweepSphere(c)
end
function PhysicsOrchestrator.prototype.sweepCapsule(self, cfg)
    if KTypeOf(self._phys.sweepCapsule) ~= "function" then
        error(
            Classes:construct(Error, "[ENGINE.physics] backend.sweepCapsule(cfg) missing"),
            0
        )
    end
    local c = self:_sweep(cfg)
    c.radius = num(_G, c.radius, 0)
    c.height = num(_G, c.height, 0)
    if not (c.radius > 0) then
        error(
            Classes:construct(Error, "[ENGINE.physics] sweepCapsule: cfg.radius must be > 0"),
            0
        )
    end
    if not (c.height >= 0) then
        error(
            Classes:construct(Error, "[ENGINE.physics] sweepCapsule: cfg.height must be >= 0"),
            0
        )
    end
    c.up = vec3Arr(
        _G,
        c.up,
        0,
        1,
        0
    )
    return self._phys:sweepCapsule(c)
end
function PhysicsOrchestrator.prototype.debug(self, enable)
    if KTypeOf(self._phys.debug) ~= "function" then
        return
    end
    do
        local function lua_catch(e)
            warn(_G, e)
        end
        local lua_try, lua_hasReturned = pcall(function()
            self._phys:debug(not not enable)
        end)
        if not lua_try then
            lua_catch(lua_hasReturned)
        end
    end
end
function PhysicsOrchestrator.prototype.gravity(self, g)
    if KTypeOf(self._phys.gravity) ~= "function" then
        return
    end
    do
        local function lua_catch(e)
            warn(_G, e)
        end
        local lua_try, lua_hasReturned = pcall(function()
            self._phys:gravity(vec3Obj(
                _G,
                g,
                0,
                -9.81,
                0
            ))
        end)
        if not lua_try then
            lua_catch(lua_hasReturned)
        end
    end
end
function PhysicsOrchestrator.prototype.idOf(self, h)
    return bodyIdOf(_G, h)
end
function PhysicsOrchestrator.prototype.surfaceIdOf(self, h)
    return surfaceIdOf(_G, h)
end
function PhysicsOrchestrator.prototype.ensureBodyForSurface(self, surfaceHandleOrId, cfg)
    local sid = surfaceIdOf(_G, surfaceHandleOrId)
    if sid <= 0 then
        error(
            Classes:construct(Error, "[ENGINE.physics] ensureBodyForSurface(): invalid surface id"),
            0
        )
    end
    return self:body(Tables:merge({}, cfg, {surface = sid}))
end
function PhysicsOrchestrator.prototype.on(self, topic, fn)
    if KTypeOf(self._phys.on) ~= "function" then
        error(
            Classes:construct(Error, "[ENGINE.physics] backend.on(topic,fn) missing"),
            0
        )
    end
    return self._phys:on(topic, fn)
end
function PhysicsOrchestrator.prototype.ref(self, h)
    local id = bodyIdOf(_G, h)
    if id <= 0 then
        error(
            Classes:construct(Error, "[ENGINE.physics] ref(): invalid body id"),
            0
        )
    end
    local lua_self = self
    return KObject:freeze({
        id = function() return id end,
        exists = function() return lua_self:exists(id) end,
        handle = function() return lua_self:handle(id) end,
        position = function() return lua_self:position(id) end,
        velocity = function(lua_, v) return lua_self:velocity(id, v) end,
        angularVelocity = function(lua_, v) return lua_self:angularVelocity(id, v) end,
        teleport = function(lua_, v) return lua_self:teleport(id, v) end,
        warp = function(lua_, v) return lua_self:warp(id, v) end,
        yaw = function(lua_, y) return lua_self:yaw(id, y) end,
        applyImpulse = function(lua_, i) return lua_self:applyImpulse(id, i) end,
        applyCentralForce = function(lua_, f) return lua_self:applyCentralForce(id, f) end,
        applyTorque = function(lua_, t) return lua_self:applyTorque(id, t) end,
        clearForces = function() return lua_self:clearForces(id) end,
        lockRotation = function(lua_, l) return lua_self:lockRotation(id, l) end,
        setKinematic = function(lua_, k) return lua_self:setKinematic(id, k) end,
        collisionGroups = function(lua_, group, mask) return lua_self:collisionGroups(id, group, mask) end,
        remove = function() return lua_self:remove(id) end
    })
end
M = {PhysicsOrchestrator = PhysicsOrchestrator}

return M
