local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local LuaNumber = luaRuntime.LuaNumber
local LuaNumberIsFinite = luaRuntime.LuaNumberIsFinite
local LuaArrayIsArray = luaRuntime.LuaArrayIsArray
local LuaClass = luaRuntime.LuaClass
local Error = luaRuntime.Error
local LuaConstruct = luaRuntime.LuaConstruct
local LuaTableRemove = luaRuntime.LuaTableRemove
U = require("../util.lua")
function clamp(self, v, a, b)
    v = LuaNumber(v)
    local lua_temp_1
    if v < a then
        lua_temp_1 = a
    else
        local lua_temp_0
        if v > b then
            lua_temp_0 = b
        else
            lua_temp_0 = v
        end
        lua_temp_1 = lua_temp_0
    end
    return lua_temp_1
end
function normalize3_into(self, x, y, z, out)
    x = LuaNumber(x)
    y = LuaNumber(y)
    z = LuaNumber(z)
    local l2 = x * x + y * y + z * z
    if not (l2 > 1e-12) or not LuaNumberIsFinite(l2) then
        out.x = 0
        out.y = 0
        out.z = 1
        return out
    end
    local inv = 1 / math.sqrt(l2)
    out.x = x * inv
    out.y = y * inv
    out.z = z * inv
    return out
end
function vec3FromAny(self, v)
    if not v then
        return nil
    end
    if LuaArrayIsArray(v) and #v >= 3 then
        local x = LuaNumber(v[1])
        local y = LuaNumber(v[2])
        local z = LuaNumber(v[3])
        if not LuaNumberIsFinite(x) or not LuaNumberIsFinite(y) or not LuaNumberIsFinite(z) then
            return nil
        end
        return {x = x, y = y, z = z}
    end
    local x = LuaNumber(v.x)
    local y = LuaNumber(v.y)
    local z = LuaNumber(v.z)
    if not LuaNumberIsFinite(x) or not LuaNumberIsFinite(y) or not LuaNumberIsFinite(z) then
        return nil
    end
    return {x = x, y = y, z = z}
end
function idOfSurfaceHandle(self, g)
    if not g then
        return 0
    end
    if KTypeOf(g.surfaceId) == "number" then
        return bit32.bor(g.surfaceId, 0)
    end
    if KTypeOf(g.id) == "number" then
        return bit32.bor(g.id, 0)
    end
    if KTypeOf(g.id) == "function" then
        return bit32.bor(
            g:id(),
            0
        ) or 0
    end
    if KTypeOf(g.getId) == "function" then
        return bit32.bor(
            g:getId(),
            0
        ) or 0
    end
    if g.handle and KTypeOf(g.handle.id) == "number" then
        return bit32.bor(g.handle.id, 0)
    end
    return 0
end
function mulberry32(self, seedU32)
    local a = bit32.rshift(seedU32, 0)
    return function(self)
        a = bit32.rshift(a + 1831565813, 0)
        local t = a
        t = KMath:imul(
            bit32.bxor(
                t,
                bit32.rshift(t, 15)
            ),
            bit32.bor(t, 1)
        )
        t = bit32.bxor(
            t,
            t + KMath:imul(
                bit32.bxor(
                    t,
                    bit32.rshift(t, 7)
                ),
                bit32.bor(t, 61)
            )
        )
        return bit32.rshift(
            bit32.bxor(
                t,
                bit32.rshift(t, 14)
            ),
            0
        ) / 4294967296
    end
end
function hashStrU32(self, s)
    s = tostring(s == nil and "" or s)
    local h = bit32.rshift(2166136261, 0)
    do
        local i = 0
        while i < KLength(s) do
            h = bit32.bxor(
                h,
                bit32.band(
                    (string.byte(s, i + 1) or 0),
                    65535
                )
            )
            h = KMath:imul(h, 16777619)
            i = i + 1
        end
    end
    return bit32.rshift(h, 0)
end
function mixU32(self, a, b)
    a = bit32.bxor(
        bit32.rshift(a, 0),
        bit32.rshift(b, 0)
    )
    a = KMath:imul(
        bit32.bxor(
            a,
            bit32.rshift(a, 16)
        ),
        2146121005
    )
    a = KMath:imul(
        bit32.bxor(
            a,
            bit32.rshift(a, 15)
        ),
        2221713035
    )
    return bit32.rshift(
        bit32.bxor(
            a,
            bit32.rshift(a, 16)
        ),
        0
    )
end
DEFAULT_CFG = KObject:freeze({
    enabled = true,
    spawnOffset = 0,
    speed = 460,
    invertPitch = true,
    radiusMin = 0.05,
    radiusMax = 1.85,
    density = 18,
    massMin = 1,
    massMax = 1200,
    impactFilter = {minImpulse = 0.25, minRelSpeed = 0.2},
    impact = {enabled = true, soundEvent = "world.impact", particles = {
        enabled = true,
        template = "impact",
        burst = 24,
        ttlMs = 900,
        override = nil
    }},
    events = {fire = "game.shoot.fire", hit = "game.shoot.hit"},
    deterministic = {enabled = true, seed = 0}
})
function mergeCfg(self, rootCfg)
    local b = DEFAULT_CFG
    local s = rootCfg and rootCfg.shoot or ({})
    local impact = s.impact or ({})
    local particles = impact.particles or ({})
    local filter = s.impactFilter or ({})
    local ev = s.events or ({})
    local det = s.deterministic or ({})
    local lua_temp_2
    if s.enabled ~= nil then
        lua_temp_2 = not not s.enabled
    else
        lua_temp_2 = b.enabled
    end
    local lua_temp_3
    if s.spawnOffset ~= nil then
        lua_temp_3 = U:num(s.spawnOffset, b.spawnOffset)
    else
        lua_temp_3 = b.spawnOffset
    end
    local lua_temp_4
    if s.speed ~= nil then
        lua_temp_4 = U:num(s.speed, b.speed)
    else
        lua_temp_4 = b.speed
    end
    local lua_temp_5
    if s.invertPitch ~= nil then
        lua_temp_5 = not not s.invertPitch
    else
        lua_temp_5 = b.invertPitch
    end
    local lua_temp_6
    if s.radiusMin ~= nil then
        lua_temp_6 = U:num(s.radiusMin, b.radiusMin)
    else
        lua_temp_6 = b.radiusMin
    end
    local lua_temp_7
    if s.radiusMax ~= nil then
        lua_temp_7 = U:num(s.radiusMax, b.radiusMax)
    else
        lua_temp_7 = b.radiusMax
    end
    local lua_temp_8
    if s.density ~= nil then
        lua_temp_8 = U:num(s.density, b.density)
    else
        lua_temp_8 = b.density
    end
    local lua_temp_9
    if s.massMin ~= nil then
        lua_temp_9 = U:num(s.massMin, b.massMin)
    else
        lua_temp_9 = b.massMin
    end
    local lua_temp_10
    if s.massMax ~= nil then
        lua_temp_10 = U:num(s.massMax, b.massMax)
    else
        lua_temp_10 = b.massMax
    end
    local lua_temp_11
    if filter.minImpulse ~= nil then
        lua_temp_11 = U:num(filter.minImpulse, b.impactFilter.minImpulse)
    else
        lua_temp_11 = b.impactFilter.minImpulse
    end
    local lua_temp_12
    if filter.minRelSpeed ~= nil then
        lua_temp_12 = U:num(filter.minRelSpeed, b.impactFilter.minRelSpeed)
    else
        lua_temp_12 = b.impactFilter.minRelSpeed
    end
    local lua_temp_24 = {minImpulse = lua_temp_11, minRelSpeed = lua_temp_12}
    local lua_temp_13
    if impact.enabled ~= nil then
        lua_temp_13 = not not impact.enabled
    else
        lua_temp_13 = b.impact.enabled
    end
    local lua_temp_14
    if impact.soundEvent ~= nil then
        lua_temp_14 = tostring(impact.soundEvent or "")
    else
        lua_temp_14 = b.impact.soundEvent
    end
    local lua_temp_15
    if particles.enabled ~= nil then
        lua_temp_15 = not not particles.enabled
    else
        lua_temp_15 = b.impact.particles.enabled
    end
    local lua_temp_16
    if particles.template ~= nil then
        lua_temp_16 = tostring(particles.template or "")
    else
        lua_temp_16 = b.impact.particles.template
    end
    local lua_temp_17
    if particles.burst ~= nil then
        lua_temp_17 = bit32.bor(
            U:num(particles.burst, b.impact.particles.burst),
            0
        )
    else
        lua_temp_17 = b.impact.particles.burst
    end
    local lua_temp_18
    if particles.ttlMs ~= nil then
        lua_temp_18 = bit32.bor(
            U:num(particles.ttlMs, b.impact.particles.ttlMs),
            0
        )
    else
        lua_temp_18 = b.impact.particles.ttlMs
    end
    local lua_temp_19
    if particles.override ~= nil then
        lua_temp_19 = particles.override
    else
        lua_temp_19 = b.impact.particles.override
    end
    local lua_temp_25 = {enabled = lua_temp_13, soundEvent = lua_temp_14, particles = {
        enabled = lua_temp_15,
        template = lua_temp_16,
        burst = lua_temp_17,
        ttlMs = lua_temp_18,
        override = lua_temp_19
    }}
    local lua_temp_20
    if ev.fire ~= nil then
        lua_temp_20 = tostring(ev.fire or "")
    else
        lua_temp_20 = b.events.fire
    end
    local lua_temp_21
    if ev.hit ~= nil then
        lua_temp_21 = tostring(ev.hit or "")
    else
        lua_temp_21 = b.events.hit
    end
    local lua_temp_26 = {fire = lua_temp_20, hit = lua_temp_21}
    local lua_temp_22
    if det.enabled ~= nil then
        lua_temp_22 = not not det.enabled
    else
        lua_temp_22 = b.deterministic.enabled
    end
    local lua_temp_23
    if det.seed ~= nil then
        lua_temp_23 = bit32.bor(
            U:num(det.seed, b.deterministic.seed),
            0
        )
    else
        lua_temp_23 = b.deterministic.seed
    end
    return {
        enabled = lua_temp_2,
        spawnOffset = lua_temp_3,
        speed = lua_temp_4,
        invertPitch = lua_temp_5,
        radiusMin = lua_temp_6,
        radiusMax = lua_temp_7,
        density = lua_temp_8,
        massMin = lua_temp_9,
        massMax = lua_temp_10,
        impactFilter = lua_temp_24,
        impact = lua_temp_25,
        events = lua_temp_26,
        deterministic = {enabled = lua_temp_22, seed = lua_temp_23}
    }
end
ShootSystem = LuaClass()
ShootSystem.name = "ShootSystem"
function ShootSystem.prototype.lua_constructor(self, player)
    self.player = player
    self.cfg = mergeCfg(_G, player and player.cfg)
    self._shotId = 0
    self._subImpact = 0
    self._subCollBegin = 0
    self._dir = {x = 0, y = 0, z = 1}
    self._origin = {x = 0, y = 0, z = 0}
    self._spawn = {x = 0, y = 0, z = 0}
    self._vel = {x = 0, y = 0, z = 0}
    self._shotsBySurface = KObject:create(nil)
    self._P = ENGINE.particles
    self._rng = nil
    self._rngSeedU32 = 0
    self._rngReady = false
    self._sysTagU32 = hashStrU32(_G, "ShootSystem.v1")
end
function ShootSystem.prototype.configure(self, cfg)
    if self.player then
        self.player.cfg = cfg
    end
    self.cfg = mergeCfg(_G, cfg)
    local lua_temp_27
    if KTypeOf(ENGINE.particles) ~= "nil" and ENGINE.particles then
        lua_temp_27 = ENGINE.particles
    else
        lua_temp_27 = nil
    end
    self._P = lua_temp_27
    self._rng = nil
    self._rngReady = false
    return self
end
function ShootSystem.prototype._bus(self)
    local lua_temp_28
    if self.player and self.player.d then
        lua_temp_28 = self.player.d.bus
    else
        lua_temp_28 = nil
    end
    return lua_temp_28
end
function ShootSystem.prototype._emit(self, topic, payload)
    local bus = self:_bus()
    if bus and KTypeOf(bus.emit) == "function" then
        bus:emit(topic, payload)
    end
end
function ShootSystem.prototype._bindPhysicsFx(self)
    local bus = self:_bus()
    if not bus then
        error(
            LuaConstruct(Error, "[shoot] bus missing"),
            0
        )
    end
    if not self._subImpact then
        self._subImpact = bit32.bor(
            bus:on(
                "engine.physics.impact",
                function(lua_, p) return self:_onImpact(p) end
            ),
            0
        )
    end
    if not self._subCollBegin then
        self._subCollBegin = bit32.bor(
            bus:on(
                "engine.physics.collision.begin",
                function(lua_, p) return self:_onCollisionBegin(p) end
            ),
            0
        )
    end
end
function ShootSystem.prototype._dirFromYawPitch_into(self, yaw, pitch, outDir)
    local c = self.cfg
    yaw = U:num(yaw, 0)
    pitch = U:num(pitch, 0)
    local LIM = math.pi * 0.5 - 0.0001
    pitch = clamp(_G, pitch, -LIM, LIM)
    if c.invertPitch then
        pitch = LuaNumber(-pitch)
    end
    local sy = math.sin(yaw)
    local cy = math.cos(yaw)
    local sp = math.sin(pitch)
    local cp = math.cos(pitch)
    return normalize3_into(
        _G,
        sy * cp,
        sp,
        cy * cp,
        outDir
    )
end
function ShootSystem.prototype._readOrigin_into(self, frame, outOrigin)
    outOrigin.x = U:num(frame.pose.x, 0)
    outOrigin.y = U:num(frame.pose.y, 0) + U:num(frame.character.eyeHeight, 1.55)
    outOrigin.z = U:num(frame.pose.z, 0)
    return outOrigin
end
function ShootSystem.prototype._ensureRng(self, frame)
    local det = self.cfg.deterministic
    if not det or not det.enabled then
        self._rng = nil
        self._rngReady = false
        return
    end
    local base = bit32.bor(det.seed, 0)
    local P = self._P
    if not base and P then
        do
            pcall(function()
                if KTypeOf(P.frameSeed) == "function" then
                    base = bit32.bor(
                        P:frameSeed(),
                        0
                    ) or 0
                elseif KTypeOf(P.frameSeed) == "number" then
                    base = bit32.bor(P.frameSeed, 0) or 0
                end
            end)
        end
    end
    if not base then
        local owner = frame and frame.owner and bit32.bor(frame.owner.id, 0) or 0
        base = mixU32(_G, self._sysTagU32, owner)
        if not base then
            base = 305419896
        end
    end
    local seedU32 = mixU32(
        _G,
        bit32.rshift(base, 0),
        bit32.rshift(self._shotId + 1, 0)
    )
    if self._rngReady and seedU32 == self._rngSeedU32 and self._rng then
        return
    end
    self._rngSeedU32 = bit32.rshift(seedU32, 0)
    self._rng = mulberry32(_G, self._rngSeedU32)
    self._rngReady = true
end
function ShootSystem.prototype._rand01(self)
    if self._rngReady and self._rng then
        return self:_rng()
    end
    return KMath:random()
end
function ShootSystem.prototype._randBetween(self, a, b)
    a = LuaNumber(a)
    b = LuaNumber(b)
    if not LuaNumberIsFinite(a) then
        a = 0
    end
    if not LuaNumberIsFinite(b) then
        b = 0
    end
    if b < a then
        local t = a
        a = b
        b = t
    end
    return a + self:_rand01() * (b - a)
end
function ShootSystem.prototype._massFromRadius(self, r, density)
    r = LuaNumber(r)
    density = LuaNumber(density)
    if not LuaNumberIsFinite(r) or r <= 0 then
        r = 0.1
    end
    if not LuaNumberIsFinite(density) or density <= 0 then
        density = 1
    end
    return density * (4 / 3) * math.pi * r * r * r
end
function ShootSystem.prototype._contactPoint(self, payload)
    local p = payload and payload.contact and payload.contact.point
    return vec3FromAny(_G, p)
end
function ShootSystem.prototype._contactNormal(self, payload)
    local n = payload and payload.contact and payload.contact.normal
    return vec3FromAny(_G, n)
end
function ShootSystem.prototype._contactImpulse(self, payload)
    local c = payload and payload.contact
    local v = c and c.maxImpulse
    local x = LuaNumber(v)
    local lua_Number_isFinite_result_29
    if LuaNumberIsFinite(x) then
        lua_Number_isFinite_result_29 = x
    else
        lua_Number_isFinite_result_29 = 0
    end
    return lua_Number_isFinite_result_29
end
function ShootSystem.prototype._relSpeed(self, payload)
    local v = payload and payload.relSpeed
    local x = LuaNumber(v)
    local lua_Number_isFinite_result_30
    if LuaNumberIsFinite(x) then
        lua_Number_isFinite_result_30 = x
    else
        lua_Number_isFinite_result_30 = 0
    end
    return lua_Number_isFinite_result_30
end
function ShootSystem.prototype._isMyShotPair(self, payload)
    local lua_temp_31
    if payload and payload.a and KTypeOf(payload.a.surfaceId) == "number" then
        lua_temp_31 = bit32.bor(payload.a.surfaceId, 0)
    else
        lua_temp_31 = 0
    end
    local aS = lua_temp_31
    local lua_temp_32
    if payload and payload.b and KTypeOf(payload.b.surfaceId) == "number" then
        lua_temp_32 = bit32.bor(payload.b.surfaceId, 0)
    else
        lua_temp_32 = 0
    end
    local bS = lua_temp_32
    if aS <= 0 or bS <= 0 then
        return 0
    end
    local lua_table__shotsBySurface_aS_34
    if self._shotsBySurface[aS] then
        lua_table__shotsBySurface_aS_34 = aS
    else
        local lua_table__shotsBySurface_bS_33
        if self._shotsBySurface[bS] then
            lua_table__shotsBySurface_bS_33 = bS
        else
            lua_table__shotsBySurface_bS_33 = 0
        end
        lua_table__shotsBySurface_aS_34 = lua_table__shotsBySurface_bS_33
    end
    return lua_table__shotsBySurface_aS_34
end
function ShootSystem.prototype._passesImpactFilter(self, payload)
    local f = self.cfg.impactFilter
    local imp = self:_contactImpulse(payload)
    local rel = self:_relSpeed(payload)
    if imp < LuaNumber(f.minImpulse) then
        return false
    end
    if rel > 0 and rel < LuaNumber(f.minRelSpeed) then
        return false
    end
    return true
end
function ShootSystem.prototype._impactFx(self, pos, payload, shotSurfaceId, source)
    local c = self.cfg
    local impCfg = c.impact
    if not impCfg or not impCfg.enabled or not pos then
        return
    end
    ENGINE.sound:playSound({
        event = impCfg.soundEvent,
        is3D = true,
        random = true,
        x = pos.x,
        y = pos.y,
        z = pos.z
    })
    ENGINE.particles:spawn("impact", {
        pos = {x = pos.x, y = pos.y, z = pos.z},
        burst = 320,
        ttlMs = 650,
        seed = 12345,
        override = {velocity = {min = 4, max = 9, coneDeg = 20}, color = {start = {r = 0.6, g = 0.9, b = 1, a = 1}}}
    })
    self:_emit(
        c.events.hit,
        {
            surfaceId = bit32.bor(shotSurfaceId, 0),
            pos = pos,
            impulse = payload and payload.impulse,
            relSpeed = payload and payload.relSpeed,
            energyApprox = payload and payload.energyApprox,
            hardSide = payload and payload.hardSide,
            normal = self:_contactNormal(payload),
            source = source
        }
    )
end
function ShootSystem.prototype._onImpact(self, payload)
    local shotSurfaceId = self:_isMyShotPair(payload)
    if not shotSurfaceId then
        return
    end
    LuaTableRemove(self._shotsBySurface, shotSurfaceId)
    local pos = self:_contactPoint(payload)
    if not pos then
        return
    end
    self:_impactFx(pos, payload, shotSurfaceId, "impact")
end
function ShootSystem.prototype._onCollisionBegin(self, payload)
    local shotSurfaceId = self:_isMyShotPair(payload)
    if not shotSurfaceId then
        return
    end
    local pos = self:_contactPoint(payload)
    if not pos then
        return
    end
    if not self:_passesImpactFilter(payload) then
        return
    end
    LuaTableRemove(self._shotsBySurface, shotSurfaceId)
    self:_impactFx(pos, payload, shotSurfaceId, "collision.begin")
end
function ShootSystem.prototype._fire(self, frame, ownerBodyId)
    local sound = ENGINE.sound:getSound("world.debris")
    sound:setRandom(true)
    sound:play()
    local c = self.cfg
    if not c.enabled or not ownerBodyId then
        return
    end
    self:_bindPhysicsFx()
    self:_ensureRng(frame)
    self:_readOrigin_into(frame, self._origin)
    self:_dirFromYawPitch_into(frame.view.yaw, frame.view.pitch, self._dir)
    local off = c.spawnOffset
    self._spawn.x = self._origin.x + self._dir.x * off
    self._spawn.y = self._origin.y + self._dir.y * off
    self._spawn.z = self._origin.z + self._dir.z * off
    local r = self:_randBetween(c.radiusMin, c.radiusMax)
    local mass = self:_massFromRadius(r, c.density)
    if mass < c.massMin then
        mass = LuaNumber(c.massMin)
    end
    if mass > c.massMax then
        mass = LuaNumber(c.massMax)
    end
    local lua_self_35, lua_shotId_36 = self, "_shotId"
    local lua_self__shotId_37 = lua_self_35[lua_shotId_36] + 1
    lua_self_35[lua_shotId_36] = lua_self__shotId_37
    local name = "shot-" .. tostring(lua_self__shotId_37)
    local lua_self_38 = ENGINE.mesh
    local g = lua_self_38["sphere$"](lua_self_38):size(r):name(name):pos(self._spawn.x, self._spawn.y, self._spawn.z):material(ENGINE.material:getMaterial("box")):physics(mass, {lockRotation = false}):create()
    self._vel.x = self._dir.x * c.speed
    self._vel.y = self._dir.y * c.speed
    self._vel.z = self._dir.z * c.speed
    g:velocity(self._vel)
    local surfaceId = idOfSurfaceHandle(_G, g)
    if surfaceId > 0 then
        self._shotsBySurface[surfaceId] = 1
    end
    self:_emit(
        c.events.fire,
        {
            surfaceId = bit32.bor(surfaceId, 0),
            ownerBodyId = bit32.bor(ownerBodyId, 0)
        }
    )
end
function ShootSystem.prototype.update(self, frame, ownerBodyId)
    if not self.cfg.enabled then
        return
    end
    if not frame or not frame.input or not frame.input.lmbJustPressed then
        return
    end
    self:_fire(
        frame,
        bit32.bor(ownerBodyId, 0)
    )
end
function ShootSystem.prototype.destroy(self)
    local bus = self:_bus()
    if bus and KTypeOf(bus.off) == "function" then
        if self._subImpact then
            bus:off(bit32.bor(self._subImpact, 0))
        end
        if self._subCollBegin then
            bus:off(bit32.bor(self._subCollBegin, 0))
        end
    end
    self._subImpact = 0
    self._subCollBegin = 0
    self._shotsBySurface = KObject:create(nil)
    self._rng = nil
    self._rngReady = false
end
M = ShootSystem

return M
