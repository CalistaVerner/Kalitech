local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local Numbers = luaRuntime.number
local Classes = luaRuntime.class
U = require("../camUtil.lua")
function clamp(self, v, lo, hi)
    local lua_temp_1
    if v < lo then
        lua_temp_1 = lo
    else
        local lua_temp_0
        if v > hi then
            lua_temp_0 = hi
        else
            lua_temp_0 = v
        end
        lua_temp_1 = lua_temp_0
    end
    return lua_temp_1
end
function lerp(self, a, b, t)
    return a + (b - a) * t
end
function expAlpha(self, rate, dt)
    return 1 - math.exp(-math.max(0, rate) * math.max(0, dt))
end
function smoothstep(self, t)
    t = clamp(_G, t, 0, 1)
    return t * t * (3 - 2 * t)
end
FirstPersonCameraMode = Classes:create()
FirstPersonCameraMode.name = "FirstPersonCameraMode"
function FirstPersonCameraMode.prototype.lua_constructor(self)
    self.id = "first"
    self.meta = {supportsZoom = true, hasCollision = true, numRays = 0, playerModelVisible = false}
    self.headOffset = {x = 0, y = 1.65, z = 0}
    self.cfg = {
        sensX = 1,
        sensY = 1,
        fov = 78,
        adsSensMul = 0.72,
        adsFov = 62,
        adsBlendRate = 16,
        pitchMin = -1.35,
        pitchMax = 1.2,
        pitchSoft = 0.18,
        bobEnabled = true,
        bobRate = 10,
        bobAmpY = 0.03,
        bobAmpX = 0.015,
        swayEnabled = true,
        swayAmpX = 0.01,
        swayAmpY = 0.006,
        swayRate = 10,
        offsetSmooth = 22
    }
    self._init = false
    self._ads = 0
    self._t = 0
    self._off = {x = 0, y = 0, z = 0}
end
function FirstPersonCameraMode.prototype._ensureModeConfig(self, ctx)
    local mc = ctx.modeConfig
    if not mc then
        mc = {}
        ctx.modeConfig = mc
    end
    return mc
end
function FirstPersonCameraMode.prototype._readMoveSpeed01(self, ctx)
    local mv = ctx.moveDir or ctx.move or ctx.motion or nil
    local vel = ctx.bodyVel or ctx.vel or nil
    if mv then
        local s = Numbers:coerce(mv.speed) or Numbers:coerce(mv.mag) or Numbers:coerce(KLength(mv)) or 0
        return clamp(_G, s, 0, 1)
    end
    if vel then
        local vx = Numbers:coerce(U:vx(vel, 0)) or 0
        local vz = Numbers:coerce(U:vz(vel, 0)) or 0
        local len = KMath:hypot(vx, vz)
        return clamp(_G, len / 6, 0, 1)
    end
    return 0
end
function FirstPersonCameraMode.prototype._softClampPitch(self, p)
    local c = self.cfg
    local lo = c.pitchMin
    local hi = c.pitchMax
    p = clamp(_G, p, lo - 0.35, hi + 0.35)
    if p < lo then
        local t = clamp(
            _G,
            (lo - p) / math.max(0.000001, c.pitchSoft),
            0,
            1
        )
        p = lo - (1 - smoothstep(_G, 1 - t)) * c.pitchSoft
    elseif p > hi then
        local t = clamp(
            _G,
            (p - hi) / math.max(0.000001, c.pitchSoft),
            0,
            1
        )
        p = hi + (1 - smoothstep(_G, 1 - t)) * c.pitchSoft
    end
    return clamp(_G, p, lo, hi)
end
function FirstPersonCameraMode.prototype.update(self, ctx)
    local dt = math.max(
        0,
        Numbers:coerce(ctx.dt) or 0
    )
    local aiming = not not (ctx.aiming or ctx.input and ctx.input.aiming)
    local yaw = Numbers:coerce(ctx.look.yaw) or 0
    local pitch = Numbers:coerce(ctx.look.pitch) or 0
    pitch = self:_softClampPitch(pitch)
    local a = expAlpha(_G, self.cfg.adsBlendRate, dt)
    self._ads = lerp(_G, self._ads, aiming and 1 or 0, a)
    local mc = self:_ensureModeConfig(ctx)
    local ads = self._ads
    local sensMul = lerp(_G, 1, self.cfg.adsSensMul, ads)
    mc.sensX = self.cfg.sensX * sensMul
    mc.sensY = self.cfg.sensY * sensMul
    mc.fov = lerp(_G, self.cfg.fov, self.cfg.adsFov, ads)
    mc.sensitivityX = mc.sensX
    mc.sensitivityY = mc.sensY
    mc.fovDeg = mc.fov
    local p = ctx.bodyPos
    local x = U:vx(p, 0) + self.headOffset.x
    local y = U:vy(p, 0) + self.headOffset.y
    local z = U:vz(p, 0) + self.headOffset.z
    local sp01 = self:_readMoveSpeed01(ctx)
    if not self._init then
        self._init = true
        self._t = 0
        self._off.x = 0
        self._off.y = 0
        self._off.z = 0
    else
        self._t = self._t + dt
    end
    local sinY = math.sin(yaw)
    local cosY = math.cos(yaw)
    local rx = cosY
    local rz = -sinY
    local fx = sinY
    local fz = cosY
    local ox = 0
    local oy = 0
    if self.cfg.bobEnabled and sp01 > 0.01 then
        local w = self.cfg.bobRate
        local t = self._t * w
        local bobScale = (1 - 0.65 * ads) * sp01
        oy = oy + math.sin(t) * self.cfg.bobAmpY * bobScale
        ox = ox + math.sin(t * 0.5 + 1.1) * self.cfg.bobAmpX * bobScale
    end
    if self.cfg.swayEnabled then
        local w = self.cfg.swayRate
        local t = self._t * w
        local swayScale = 1 - 0.75 * ads
        ox = ox + math.sin(t * 0.35) * self.cfg.swayAmpX * swayScale
        oy = oy + math.sin(t * 0.27 + 0.7) * self.cfg.swayAmpY * swayScale
    end
    local oA = expAlpha(_G, self.cfg.offsetSmooth, dt)
    self._off.x = lerp(_G, self._off.x, ox, oA)
    self._off.y = lerp(_G, self._off.y, oy, oA)
    x = x + rx * self._off.x
    z = z + rz * self._off.x
    y = y + self._off.y
    ctx.outPos.x = x
    ctx.outPos.y = y
    ctx.outPos.z = z
    ctx.target.x = x + fx
    ctx.target.y = y + math.sin(pitch)
    ctx.target.z = z + fz
    if ctx.outLook then
        ctx.outLook.yaw = yaw
        ctx.outLook.pitch = pitch
    end
end
M = FirstPersonCameraMode

return M
