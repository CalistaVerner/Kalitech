local luaRuntime = require("@builtin/lua_runtime")
local LuaTableMerge = luaRuntime.LuaTableMerge
local LuaClass = luaRuntime.LuaClass
local Error = luaRuntime.Error
local LuaConstruct = luaRuntime.LuaConstruct
local LuaNumber = luaRuntime.LuaNumber
local LuaStringTrim = luaRuntime.LuaStringTrim
local LuaArrayIsArray = luaRuntime.LuaArrayIsArray
local MeshMath = require("./helpers/MeshMath.lua")
local MeshCfg = require("./helpers/MeshCfg.lua")
local MeshIds = require("./helpers/MeshIds.lua")
function warpArgs(self, x, y, z)
    local n = MeshMath:normalizePos(x)
    if n then
        return n
    end
    return {
        MeshMath:num(x, 0),
        MeshMath:num(y, 0),
        MeshMath:num(z, 0)
    }
end
function hostMethod(self, target, name)
    local m = target[name]
    if KTypeOf(m) ~= "function" then
        return nil
    end
    return function(self, ...)
        local args = {...}
        return KFunction:apply(m, target, args)
    end
end
function cloneCfgShallow(self, state)
    local out = LuaTableMerge({}, state)
    if state.physics then
        out.physics = LuaTableMerge({}, state.physics)
    end
    return out
end
MeshOrchestrator = LuaClass()
MeshOrchestrator.name = "MeshOrchestrator"
function MeshOrchestrator.prototype.lua_constructor(self, ENGINE)
    if not ENGINE then
        error(
            LuaConstruct(Error, "[MESH] ENGINE is required"),
            0
        )
    end
    self.ENGINE = ENGINE
    local mesh = ENGINE:mesh()
    if not mesh then
        error(
            LuaConstruct(Error, "[MESH] ENGINE.mesh() is required"),
            0
        )
    end
    if KTypeOf(mesh.create) ~= "function" then
        error(
            LuaConstruct(Error, "[MESH] ENGINE.mesh().create(cfg) is required"),
            0
        )
    end
    MeshIds:requireSurface(ENGINE)
    MeshIds:requirePhysics(ENGINE)
    self._mesh = mesh
    self._decorated = nil
end
function MeshOrchestrator.prototype.wrapSurface(self, handle)
    if handle and handle.__isMeshWrapper then
        return handle
    end
    local ENGINE = self.ENGINE
    local sid = MeshIds:surfaceId(handle)
    local cachedBodyId = 0
    local function bodyId()
        if cachedBodyId > 0 then
            return cachedBodyId
        end
        cachedBodyId = MeshIds:resolveBodyId(ENGINE, sid)
        return cachedBodyId
    end
    local proxy = KProxy(
        _G,
        KObject:create(nil),
        {get = function(self, _t, prop)
            if prop == "__isMeshWrapper" then
                return true
            end
            if prop == "__surface" then
                return handle
            end
            if prop == "surfaceId" then
                return function() return sid end
            end
            if prop == "bodyId" then
                return function() return bodyId(_G) end
            end
            if prop == "warp" then
                return function(lua_, x, y, z)
                    local p = MeshIds:requirePhysics(ENGINE)
                    return p:warp(
                        bodyId(_G),
                        warpArgs(_G, x, y, z)
                    )
                end
            end
            if prop == "position" then
                return function(lua_, v)
                    local p = MeshIds:requirePhysics(ENGINE)
                    if v == nil then
                        return p:position(bodyId(_G))
                    end
                    return p:warp(
                        bodyId(_G),
                        v
                    )
                end
            end
            if prop == "velocity" then
                return function(lua_, v)
                    local p = MeshIds:requirePhysics(ENGINE)
                    if v == nil then
                        return p:velocity(bodyId(_G))
                    end
                    return p:velocity(
                        bodyId(_G),
                        v
                    )
                end
            end
            if prop == "yaw" then
                return function(lua_, yawRad)
                    local p = MeshIds:requirePhysics(ENGINE)
                    return p:yaw(
                        bodyId(_G),
                        LuaNumber(yawRad)
                    )
                end
            end
            if prop == "applyImpulse" then
                return function(lua_, v3)
                    local p = MeshIds:requirePhysics(ENGINE)
                    return p:applyImpulse(
                        bodyId(_G),
                        v3
                    )
                end
            end
            if prop == "applyCentralForce" then
                return function(lua_, v3)
                    local p = MeshIds:requirePhysics(ENGINE)
                    return p:applyCentralForce(
                        bodyId(_G),
                        v3
                    )
                end
            end
            if prop == "applyTorque" then
                return function(lua_, v3)
                    local p = MeshIds:requirePhysics(ENGINE)
                    return p:applyTorque(
                        bodyId(_G),
                        v3
                    )
                end
            end
            if prop == "angularVelocity" then
                return function(lua_, v3)
                    local p = MeshIds:requirePhysics(ENGINE)
                    if v3 == nil then
                        return p:angularVelocity(bodyId(_G))
                    end
                    return p:angularVelocity(
                        bodyId(_G),
                        v3
                    )
                end
            end
            if prop == "clearForces" then
                return function()
                    local p = MeshIds:requirePhysics(ENGINE)
                    return p:clearForces(bodyId(_G))
                end
            end
            if prop == "collisionGroups" then
                return function(lua_, group, mask)
                    local p = MeshIds:requirePhysics(ENGINE)
                    return p:collisionGroups(
                        bodyId(_G),
                        bit32.bor(group, 0),
                        bit32.bor(mask, 0)
                    )
                end
            end
            if prop == "lockRotation" then
                return function(lua_, lock)
                    local p = MeshIds:requirePhysics(ENGINE)
                    return p:lockRotation(
                        bodyId(_G),
                        not not lock
                    )
                end
            end
            if prop == "setKinematic" then
                return function(lua_, k)
                    local p = MeshIds:requirePhysics(ENGINE)
                    return p:setKinematic(
                        bodyId(_G),
                        not not k
                    )
                end
            end
            if prop == "setVisible" then
                return function(lua_, v)
                    local s = MeshIds:requireSurface(ENGINE)
                    return s:setVisible(sid, not not v)
                end
            end
            if prop == "setCull" then
                return function(lua_, hint)
                    local s = MeshIds:requireSurface(ENGINE)
                    return s:setCull(
                        sid,
                        tostring(hint)
                    )
                end
            end
            local v = handle[prop]
            if KTypeOf(v) == "function" then
                return KFunction:bind(v, handle)
            end
            return v
        end}
    )
    return proxy
end
function MeshOrchestrator.prototype.decorateMeshApi(self)
    if self._decorated then
        return self._decorated
    end
    local orch = self
    local mesh = self._mesh
    local decorated
    decorated = KProxy(
        _G,
        mesh,
        {get = function(self, target, prop)
            if prop == "create" then
                local lua_hostMethod_result_1 = hostMethod(_G, target, "create")
                if not lua_hostMethod_result_1 then
                    local lua_temp_0
                    if KTypeOf(target.create) == "function" then
                        lua_temp_0 = KFunction:bind(target.create, target)
                    else
                        lua_temp_0 = nil
                    end
                    lua_hostMethod_result_1 = lua_temp_0
                end
                local createFn = lua_hostMethod_result_1
                if not createFn then
                    error(
                        LuaConstruct(Error, "[MESH] ENGINE.mesh().create(cfg) is required"),
                        0
                    )
                end
                return function(lua_, cfg)
                    local c = MeshCfg:normalizeCfg(cfg)
                    local h = createFn(_G, c)
                    return orch:wrapSurface(h)
                end
            end
            if prop == "loadModel" then
                return function(lua_, pathOrCfg, cfg)
                    local c
                    if KTypeOf(pathOrCfg) == "string" then
                        c = MeshCfg:normalizeCfg(cfg)
                        c.type = "model"
                        c.path = tostring(pathOrCfg)
                    else
                        c = MeshCfg:normalizeCfg(pathOrCfg)
                        c.type = "model"
                    end
                    if not c.path or LuaStringTrim(tostring(c.path)) == "" then
                        error(
                            LuaConstruct(Error, "[MESH] loadModel: path is required"),
                            0
                        )
                    end
                    return decorated:create(c)
                end
            end
            if prop == "many" and KTypeOf(target.many) == "function" then
                local manyFn = hostMethod(_G, target, "many") or KFunction:bind(target.many, target)
                return function(lua_, list)
                    local arr = manyFn(_G, list)
                    if not LuaArrayIsArray(arr) then
                        return arr
                    end
                    do
                        local i = 0
                        while i < #arr do
                            arr[i + 1] = orch:wrapSurface(arr[i + 1])
                            i = i + 1
                        end
                    end
                    return arr
                end
            end
            if prop == "builder" then
                return function(lua_, lua_type)
                    local state = MeshCfg:normalizeCfg({type = tostring(lua_type)})
                    local b
                    b = {
                        size = function(self, v)
                            local lua_M_num_4 = MeshMath.num
                            local lua_v_3 = v
                            local lua_temp_2
                            if state.type == "sphere" then
                                lua_temp_2 = state.radius
                            else
                                lua_temp_2 = state.size
                            end
                            local n = lua_M_num_4(M, lua_v_3, lua_temp_2)
                            if state.type == "sphere" then
                                state.radius = n
                            else
                                state.size = n
                            end
                            return b
                        end,
                        name = function(self, v)
                            state.name = tostring(v)
                            return b
                        end,
                        pos = function(self, x, y, z)
                            if LuaArrayIsArray(x) or MeshMath:isObj(x) then
                                state.pos = MeshMath:normalizePos(x)
                            else
                                state.pos = {
                                    MeshMath:num(x, 0),
                                    MeshMath:num(y, 0),
                                    MeshMath:num(z, 0)
                                }
                            end
                            return b
                        end,
                        material = function(self, m)
                            state.material = m
                            return b
                        end,
                        path = function(self, v)
                            state.path = tostring(v)
                            return b
                        end,
                        model = function(self, v)
                            state.path = tostring(v)
                            return b
                        end,
                        physics = function(self, mass, opts)
                            local o = opts or ({})
                            local lua_temp_5
                            if mass ~= nil then
                                lua_temp_5 = mass
                            else
                                lua_temp_5 = 0
                            end
                            local p = {mass = lua_temp_5}
                            if o.enabled ~= nil then
                                p.enabled = not not o.enabled
                            end
                            if o.lockRotation ~= nil then
                                p.lockRotation = not not o.lockRotation
                            end
                            if o.kinematic ~= nil then
                                p.kinematic = not not o.kinematic
                            end
                            if o.friction ~= nil then
                                p.friction = o.friction
                            end
                            if o.restitution ~= nil then
                                p.restitution = o.restitution
                            end
                            if o.damping ~= nil then
                                p.damping = o.damping
                            end
                            if o.collider ~= nil then
                                p.collider = o.collider
                            end
                            state.physics = p
                            return b
                        end,
                        cfg = function(self)
                            return cloneCfgShallow(_G, state)
                        end,
                        create = function(self)
                            local out = cloneCfgShallow(_G, state)
                            if out.type == "sphere" and out.physics and out.physics.enabled ~= false and not out.physics.collider then
                                local r = MeshMath:num(
                                    out.radius,
                                    MeshMath:num(out.size, 1)
                                )
                                out.physics.collider = {type = "sphere", radius = r}
                            end
                            return decorated:create(out)
                        end
                    }
                    return b
                end
            end
            if prop == "box$" then
                return function() return decorated:builder("box") end
            end
            if prop == "cube$" then
                return function() return decorated:builder("box") end
            end
            if prop == "sphere$" then
                return function() return decorated:builder("sphere") end
            end
            if prop == "cylinder$" then
                return function() return decorated:builder("cylinder") end
            end
            if prop == "capsule$" then
                return function() return decorated:builder("capsule") end
            end
            if prop == "model$" then
                return function() return decorated:builder("model") end
            end
            local v = target[prop]
            if KTypeOf(v) == "function" then
                local fn = hostMethod(_G, target, prop)
                return fn or v
            end
            return v
        end}
    )
    self._decorated = decorated
    return decorated
end
return MeshOrchestrator
