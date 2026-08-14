local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local LuaClass = luaRuntime.LuaClass
local LuaNumber = luaRuntime.LuaNumber
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
function smoothstep(self, t)
    t = clamp(_G, t, 0, 1)
    return t * t * (3 - 2 * t)
end
function expAlpha(self, rate, dt)
    return 1 - math.exp(-math.max(0, rate) * math.max(0, dt))
end
ThirdPersonCameraMode = LuaClass()
ThirdPersonCameraMode.name = "ThirdPersonCameraMode"
function ThirdPersonCameraMode.prototype.lua_constructor(self)
    self.id = "third"
    self.meta = {supportsZoom = true, hasCollision = true, numRays = 8, playerModelVisible = true}
    self.pivotOffset = {x = 0, y = 1.46, z = 0}
    self.shoulderX = 0.42
    self.shoulderAimX = 0.26
    self.verticalLift = 0.14
    self.forwardLead = 0.28
    self.forwardLeadAim = 0.08
    self.leadSmooth = 12
    self.pivotSmoothPos = 22
    self.pivotSmoothY = 26
    self.camSmoothPos = 18
    self.camSmoothY = 20
    self.recenterEnabled = true
    self.recenterRate = 2.2
    self.recenterDeadZone = 0.38
    self.recenterMax = 1.25
    self.pitchOrbitScale = 1
    self.pitchMin = -1.15
    self.pitchMax = 0.9
    self.pitchSoft = 0.18
    self.collisionEnabled = true
    self.camRadius = 0.28
    self.nearRadius = 0.06
    self.pearK = 1.9
    self.pearSamples = 9
    self.surfacePadding = 0.08
    self.obstaclePasses = 2
    self.useTerrainHeight = true
    self.terrainWorld = true
    self.floorPadding = 0.22
    self.slopePadScale = 0.45
    self.groundRayLift = 1.25
    self.maxRayLenDown = 12
    self.groundSnapPen = 0.6
    self.zoomRadiusBoost = 0.1
    self.zoomNearBoost = 0.06
    self.zoomFloorBoost = 0.2
    self.zoomBoostStart = 6
    self.zoomBoostFull = 26
    self.debugCapsule = true
    self.debugGroundCapsule = true
    self._init = false
    self._pivot = {x = 0, y = 0, z = 0}
    self._cam = {x = 0, y = 0, z = 0}
    self._lead = {x = 0, y = 0, z = 0}
    self._shoulderSide = 1
end
function ThirdPersonCameraMode.prototype._ensureModeConfig(self, ctx)
    local mc = ctx.modeConfig
    if not mc then
        mc = {}
        ctx.modeConfig = mc
    end
    return mc
end
function ThirdPersonCameraMode.prototype._applyCollisionOverrides(self, ctx, dist)
    local mc = self:_ensureModeConfig(ctx)
    local zoRaw = ctx.zoneOverridesRaw
    if zoRaw and zoRaw.collisionEnabled == false then
        mc.collisionEnabled = false
        return
    end
    mc.collisionEnabled = not not self.collisionEnabled
    local z0 = self.zoomBoostStart
    local z1 = math.max(z0 + 0.001, self.zoomBoostFull)
    local k = clamp(_G, (dist - z0) / (z1 - z0), 0, 1)
    local camR = self.camRadius * (1 + self.zoomRadiusBoost * k)
    local nearR = self.nearRadius * (1 + self.zoomNearBoost * k)
    local floorPad = self.floorPadding * (1 + self.zoomFloorBoost * k)
    mc.camRadius = camR
    mc.nearRadius = nearR
    mc.pearK = self.pearK
    mc.pearSamples = self.pearSamples
    mc.surfacePadding = self.surfacePadding
    mc.obstaclePasses = self.obstaclePasses
    mc.useTerrainHeight = not not self.useTerrainHeight
    mc.terrainWorld = not not self.terrainWorld
    mc.floorPadding = floorPad
    mc.slopePadScale = self.slopePadScale
    mc.groundRayLift = self.groundRayLift
    mc.maxRayLenDown = self.maxRayLenDown
    mc.groundSnapPen = self.groundSnapPen
    mc.debugCapsule = not not self.debugCapsule
    mc.debugGroundCapsule = not not self.debugGroundCapsule
end
function ThirdPersonCameraMode.prototype._readMove(self, ctx)
    local mv = ctx.moveDir or ctx.move or ctx.motion or nil
    local vel = ctx.bodyVel or ctx.vel or nil
    local dx = 0
    local dz = 0
    local sp = 0
    if mv then
        dx = LuaNumber(U:vx(mv, 0)) or 0
        dz = LuaNumber(U:vz(mv, 0)) or 0
        local len = KMath:hypot(dx, dz)
        if len > 0.000001 then
            dx = dx / len
            dz = dz / len
        end
        sp = clamp(
            _G,
            LuaNumber(mv.speed) or LuaNumber(mv.mag) or LuaNumber(KLength(mv)) or 0,
            0,
            1
        )
    elseif vel then
        local vx = LuaNumber(U:vx(vel, 0)) or 0
        local vz = LuaNumber(U:vz(vel, 0)) or 0
        local len = KMath:hypot(vx, vz)
        if len > 0.000001 then
            dx = vx / len
            dz = vz / len
        end
        sp = clamp(_G, len / 6, 0, 1)
    end
    return {dx = dx, dz = dz, sp = sp}
end
function ThirdPersonCameraMode.prototype._softClampPitch(self, p)
    local lo = self.pitchMin
    local hi = self.pitchMax
    p = clamp(_G, p, lo - 0.35, hi + 0.35)
    if p < lo then
        local t = clamp(
            _G,
            (lo - p) / math.max(0.000001, self.pitchSoft),
            0,
            1
        )
        p = lo - (1 - smoothstep(_G, 1 - t)) * self.pitchSoft
    elseif p > hi then
        local t = clamp(
            _G,
            (p - hi) / math.max(0.000001, self.pitchSoft),
            0,
            1
        )
        p = hi + (1 - smoothstep(_G, 1 - t)) * self.pitchSoft
    end
    return clamp(_G, p, lo, hi)
end
function ThirdPersonCameraMode.prototype._updateShoulderSide(self, ctx)
    local inp = ctx.input or nil
    local swap = not not (ctx.shoulderSwap or inp and inp.shoulderSwap)
    if swap and not self._swapHeld then
        self._shoulderSide = -self._shoulderSide
        self._swapHeld = true
    elseif not swap then
        self._swapHeld = false
    end
    local zo = ctx.zoneOverrides
    if zo and (zo.shoulderSide == -1 or zo.shoulderSide == 1) then
        self._shoulderSide = zo.shoulderSide
    end
end
function ThirdPersonCameraMode.prototype.update(self, ctx)
    local dt = math.max(
        0,
        LuaNumber(ctx.dt) or 0
    )
    local p = ctx.bodyPos
    local zo = ctx.zoneOverrides
    local isAiming = not not (ctx.aiming or ctx.input and ctx.input.aiming)
    local lua_temp_2
    if zo and zo.pivotOffset then
        lua_temp_2 = zo.pivotOffset
    else
        lua_temp_2 = self.pivotOffset
    end
    local po = lua_temp_2
    local lua_temp_3
    if zo and zo.shoulderX ~= nil then
        lua_temp_3 = LuaNumber(zo.shoulderX)
    else
        lua_temp_3 = self.shoulderX
    end
    local baseShoulder = lua_temp_3
    local lua_temp_4
    if zo and zo.shoulderAimX ~= nil then
        lua_temp_4 = LuaNumber(zo.shoulderAimX)
    else
        lua_temp_4 = self.shoulderAimX
    end
    local aimShoulder = lua_temp_4
    local lua_temp_5
    if zo and zo.verticalLift ~= nil then
        lua_temp_5 = LuaNumber(zo.verticalLift)
    else
        lua_temp_5 = self.verticalLift
    end
    local lift = lua_temp_5
    self:_updateShoulderSide(ctx)
    local yaw = LuaNumber(ctx.look.yaw) or 0
    local pitch = LuaNumber(ctx.look.pitch) or 0
    pitch = self:_softClampPitch(pitch)
    if self.recenterEnabled then
        local mv = self:_readMove(ctx)
        if mv.sp > 0.08 then
            local moveYaw = math.atan2(mv.dx, mv.dz)
            local dy = yaw - moveYaw
            while dy > math.pi do
                dy = dy - math.pi * 2
            end
            while dy < -math.pi do
                dy = dy + math.pi * 2
            end
            local abs = math.abs(dy)
            if abs > self.recenterDeadZone then
                local over = clamp(_G, abs - self.recenterDeadZone, 0, self.recenterMax)
                local pull = smoothstep(
                    _G,
                    over / math.max(0.000001, self.recenterMax)
                )
                local a = expAlpha(_G, self.recenterRate * (0.35 + 0.65 * mv.sp), dt) * pull
                yaw = lerp(_G, yaw, moveYaw, a)
            end
        end
    end
    local sinY = math.sin(yaw)
    local cosY = math.cos(yaw)
    local rx = cosY
    local rz = -sinY
    local fx = sinY
    local fz = cosY
    local basePx = U:vx(p, 0) + po.x
    local basePy = U:vy(p, 0) + po.y
    local basePz = U:vz(p, 0) + po.z
    local mv = self:_readMove(ctx)
    local lua_isAiming_6
    if isAiming then
        lua_isAiming_6 = self.forwardLeadAim
    else
        lua_isAiming_6 = self.forwardLead
    end
    local leadMax = lua_isAiming_6
    local leadK = leadMax * mv.sp
    local leadAx = expAlpha(_G, self.leadSmooth, dt)
    local lua_temp_7
    if mv.sp > 0.0001 then
        lua_temp_7 = mv.dx * leadK
    else
        lua_temp_7 = fx * (leadK * 0.35)
    end
    local desiredLeadX = lua_temp_7
    local lua_temp_8
    if mv.sp > 0.0001 then
        lua_temp_8 = mv.dz * leadK
    else
        lua_temp_8 = fz * (leadK * 0.35)
    end
    local desiredLeadZ = lua_temp_8
    if not self._init then
        self._lead.x = desiredLeadX
        self._lead.z = desiredLeadZ
    else
        self._lead.x = lerp(_G, self._lead.x, desiredLeadX, leadAx)
        self._lead.z = lerp(_G, self._lead.z, desiredLeadZ, leadAx)
    end
    local aimBlend = isAiming and 1 or 0
    local shoulder = lerp(_G, baseShoulder, aimShoulder, aimBlend) * self._shoulderSide
    local rawPx = basePx + rx * shoulder + self._lead.x
    local rawPy = basePy
    local rawPz = basePz + rz * shoulder + self._lead.z
    if not self._init then
        self._init = true
        self._pivot.x = rawPx
        self._pivot.y = rawPy
        self._pivot.z = rawPz
        self._cam.x = rawPx
        self._cam.y = rawPy + lift
        self._cam.z = rawPz
    else
        local ax = expAlpha(_G, self.pivotSmoothPos, dt)
        local ay = expAlpha(_G, self.pivotSmoothY, dt)
        self._pivot.x = lerp(_G, self._pivot.x, rawPx, ax)
        self._pivot.y = lerp(_G, self._pivot.y, rawPy, ay)
        self._pivot.z = lerp(_G, self._pivot.z, rawPz, ax)
    end
    ctx.target.x = self._pivot.x
    ctx.target.y = self._pivot.y
    ctx.target.z = self._pivot.z
    local dist = math.max(
        0.05,
        LuaNumber(ctx.zoom:value())
    )
    self:_applyCollisionOverrides(ctx, dist)
    local p2 = pitch * self.pitchOrbitScale
    local cp = math.cos(p2)
    local sp = math.sin(p2)
    local horiz = dist * cp
    local outX = self._pivot.x - sinY * horiz
    local outZ = self._pivot.z - cosY * horiz
    local outY = self._pivot.y + lift + sp * dist
    local cx = expAlpha(_G, self.camSmoothPos, dt)
    local cy = expAlpha(_G, self.camSmoothY, dt)
    self._cam.x = lerp(_G, self._cam.x, outX, cx)
    self._cam.y = lerp(_G, self._cam.y, outY, cy)
    self._cam.z = lerp(_G, self._cam.z, outZ, cx)
    ctx.outPos.x = self._cam.x
    ctx.outPos.y = self._cam.y
    ctx.outPos.z = self._cam.z
    if ctx.outLook then
        ctx.outLook.yaw = yaw
        ctx.outLook.pitch = pitch
    end
end
M = ThirdPersonCameraMode

return M
