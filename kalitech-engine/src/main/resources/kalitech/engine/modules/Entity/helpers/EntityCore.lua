local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local Numbers = luaRuntime.number
local Tables = luaRuntime.table
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
function isObj(self, x)
    return x ~= nil and KTypeOf(x) == "table"
end
EntityCore = Classes:create()
EntityCore.name = "EntityCore"
function EntityCore.prototype.lua_constructor(self)
    self.handle = nil
    self.body = nil
    self.bodyAccess = nil
    self.uuid = ""
    self.surfaceId = 0
    self.bodyId = 0
    self.snapshot = nil
    self.components = KObject:create(nil)
    self._getPos = nil
    self._getVel = nil
    self._getRot = nil
    self._getAngVel = nil
    self._groundProbe = nil
    self.state = {
        alive = false,
        uuid = "",
        mass = 0,
        radius = 0,
        height = 0,
        x = 0,
        y = 0,
        z = 0,
        rx = 0,
        ry = 0,
        rz = 0,
        rw = 1,
        vx = 0,
        vy = 0,
        vz = 0,
        avx = 0,
        avy = 0,
        avz = 0,
        speed = 0,
        grounded = false,
        flags = 0
    }
end
function EntityCore.prototype.configureShape(self, mass, radius, height)
    self.state.mass = Numbers:coerce(mass) or 0
    self.state.radius = Numbers:coerce(radius) or 0
    self.state.height = Numbers:coerce(height) or 0
    return self
end
function EntityCore.prototype.setGroundProbe(self, fn)
    if fn ~= nil and KTypeOf(fn) ~= "function" then
        error(
            Classes:construct(Error, "[EntityCore] groundProbe must be a function or null"),
            0
        )
    end
    self._groundProbe = fn or nil
    return self
end
function EntityCore.prototype.hydrate(self, snapshot)
    if not isObj(_G, snapshot) then
        return self
    end
    self.snapshot = snapshot
    if isObj(_G, snapshot.components) then
        local c = snapshot.components
        for lua_, k in ipairs(Tables:keys(c)) do
            self.components[k] = c[k]
        end
    end
    if KTypeOf(snapshot.uuid) == "string" and snapshot.uuid then
        self.uuid = snapshot.uuid
        self.state.uuid = self.uuid
    end
    if snapshot.surfaceId ~= nil then
        self.surfaceId = bit32.bor(snapshot.surfaceId, 0)
    end
    if snapshot.bodyId ~= nil then
        self.bodyId = bit32.bor(snapshot.bodyId, 0)
    end
    return self
end
function EntityCore.prototype.attach(self, handle, body, bodyAccess)
    self.handle = req(_G, handle, "[EntityCore] handle is required")
    self.body = body or nil
    local ba = req(_G, bodyAccess, "[EntityCore] bodyAccess is required")
    if KTypeOf(ba.position) ~= "function" then
        error(
            Classes:construct(Error, "[EntityCore] bodyAccess.position() is required"),
            0
        )
    end
    if KTypeOf(ba.getVel) ~= "function" then
        error(
            Classes:construct(Error, "[EntityCore] bodyAccess.getVel() is required"),
            0
        )
    end
    if KTypeOf(ba.rotation) ~= "function" then
        error(
            Classes:construct(Error, "[EntityCore] bodyAccess.rotation() is required"),
            0
        )
    end
    if KTypeOf(ba.getAngVel) ~= "function" then
        error(
            Classes:construct(Error, "[EntityCore] bodyAccess.getAngVel() is required"),
            0
        )
    end
    self.bodyAccess = ba
    local lua_temp_0
    if KTypeOf(handle.uuidString) == "function" then
        lua_temp_0 = handle:uuidString()
    else
        lua_temp_0 = handle.uuid
    end
    local u = lua_temp_0 or ""
    self.uuid = tostring(u or "")
    if not self.uuid then
        error(
            Classes:construct(Error, "[EntityCore] missing uuid (UUID-only)"),
            0
        )
    end
    self.surfaceId = bit32.bor(handle.surfaceId, 0) or 0
    self.bodyId = bit32.bor(handle.bodyId, 0) or 0
    if self.bodyId <= 0 then
        error(
            Classes:construct(
                Error,
                "[EntityCore] invalid bodyId=" .. tostring(self.bodyId)
            ),
            0
        )
    end
    self._getPos = ba.position
    self._getVel = ba.getVel
    self._getRot = ba.rotation
    self._getAngVel = ba.getAngVel
    self.state.uuid = self.uuid
    self.state.alive = true
    return self
end
function EntityCore.prototype.syncPhysics(self)
    if not self.state.alive then
        error(
            Classes:construct(Error, "[EntityCore] syncPhysics on dead entity"),
            0
        )
    end
    local p = self:_getPos()
    local v = self:_getVel()
    local q = self:_getRot()
    local av = self:_getAngVel()
    local px = self:_num(p.x)
    local py = self:_num(p.y)
    local pz = self:_num(p.z)
    local vx = self:_num(v.x)
    local vy = self:_num(v.y)
    local vz = self:_num(v.z)
    local rx = self:_comp(q, "x")
    local ry = self:_comp(q, "y")
    local rz = self:_comp(q, "z")
    local rw = self:_comp(q, "w")
    local avx = self:_num(av.x)
    local avy = self:_num(av.y)
    local avz = self:_num(av.z)
    local s = self.state
    s.x = px
    s.y = py
    s.z = pz
    s.vx = vx
    s.vy = vy
    s.vz = vz
    s.speed = KMath:hypot(vx, vy, vz)
    s.rx = rx
    s.ry = ry
    s.rz = rz
    s.rw = rw
    s.avx = avx
    s.avy = avy
    s.avz = avz
    local lua_table__groundProbe_1
    if self._groundProbe then
        lua_table__groundProbe_1 = not not self:_groundProbe(self)
    else
        lua_table__groundProbe_1 = false
    end
    s.grounded = lua_table__groundProbe_1
    return s
end
function EntityCore.prototype.destroy(self)
    if not self.state.alive then
        return
    end
    self.handle:destroy()
    self.handle = nil
    self.body = nil
    self.bodyAccess = nil
    self.uuid = ""
    self.surfaceId = 0
    self.bodyId = 0
    self.snapshot = nil
    self.components = KObject:create(nil)
    self._getPos = nil
    self._getVel = nil
    self._getRot = nil
    self._getAngVel = nil
    self._groundProbe = nil
    self.state.uuid = ""
    self.state.alive = false
end
function EntityCore.prototype._num(self, v)
    local lua_temp_2
    if KTypeOf(v) == "function" then
        lua_temp_2 = Numbers:coerce(v())
    else
        lua_temp_2 = Numbers:coerce(v)
    end
    return lua_temp_2
end
function EntityCore.prototype._comp(self, o, k)
    local lua_o_3
    if o then
        lua_o_3 = o[k]
    else
        lua_o_3 = nil
    end
    local v = lua_o_3
    local lua_temp_4
    if KTypeOf(v) == "function" then
        lua_temp_4 = Numbers:coerce(KFunction:call(v, o))
    else
        lua_temp_4 = Numbers:coerce(v)
    end
    return lua_temp_4
end
M = {EntityCore = EntityCore}

return M
