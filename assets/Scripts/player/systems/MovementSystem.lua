local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local LuaClass = luaRuntime.LuaClass
local Error = luaRuntime.Error
local LuaConstruct = luaRuntime.LuaConstruct
local LuaNumberIsFinite = luaRuntime.LuaNumberIsFinite
local LuaNumber = luaRuntime.LuaNumber
U = require("../util.lua")
function hypot2(self, x, z)
    return math.sqrt(x * x + z * z)
end
function moveTowards(self, cur, target, maxDelta)
    if cur < target then
        local lua_temp_0
        if cur + maxDelta < target then
            lua_temp_0 = cur + maxDelta
        else
            lua_temp_0 = target
        end
        return lua_temp_0
    end
    if cur > target then
        local lua_temp_1
        if cur - maxDelta > target then
            lua_temp_1 = cur - maxDelta
        else
            lua_temp_1 = target
        end
        return lua_temp_1
    end
    return target
end
function rotateByYaw(self, localX, localZ, yaw, out)
    local s = math.sin(yaw)
    local c = math.cos(yaw)
    out.x = localX * c + localZ * s
    out.z = localZ * c - localX * s
    return out
end
function norm2(self, x, z, out)
    local l2 = x * x + z * z
    if l2 < 1e-12 then
        out.x = 0
        out.z = 0
        return out
    end
    local inv = 1 / math.sqrt(l2)
    out.x = x * inv
    out.z = z * inv
    return out
end
function canTeleport(self, bodyAccess)
    return not not (bodyAccess and KTypeOf(bodyAccess.teleport) == "function")
end
function teleportBody(self, bodyAccess, x, y, z)
    bodyAccess:teleport(x, y, z)
end
DEFAULT_CFG = KObject:freeze({
    enabled = true,
    walkSpeed = 4.4,
    runSpeed = 7.2,
    accelGround = 38,
    decelGround = 42,
    accelAir = 10,
    decelAir = 6,
    jumpSpeed = 6.6,
    coyoteTime = 0.12,
    jumpBuffer = 0.1,
    maxHorizSpeed = 11,
    maxFallSpeed = 60
})
function cfgNum(self, cfg, k, fb)
    local v = cfg and cfg[k]
    local lua_temp_2
    if v == nil or v == nil then
        lua_temp_2 = fb
    else
        lua_temp_2 = U:num(v, fb)
    end
    return lua_temp_2
end
function cfgBool(self, cfg, k, fb)
    local v = cfg and cfg[k]
    local lua_temp_3
    if v == nil or v == nil then
        lua_temp_3 = fb
    else
        lua_temp_3 = not not v
    end
    return lua_temp_3
end
MovementSystem = LuaClass()
MovementSystem.name = "MovementSystem"
function MovementSystem.prototype.lua_constructor(self, movCfg)
    local lua_temp_4
    if movCfg and KTypeOf(movCfg) == "table" then
        lua_temp_4 = movCfg
    else
        lua_temp_4 = KObject:create(nil)
    end
    local cfg = lua_temp_4
    self.enabled = cfgBool(_G, cfg, "enabled", DEFAULT_CFG.enabled)
    self.walkSpeed = cfgNum(_G, cfg, "walkSpeed", DEFAULT_CFG.walkSpeed)
    self.runSpeed = cfgNum(_G, cfg, "runSpeed", DEFAULT_CFG.runSpeed)
    self.accelGround = cfgNum(_G, cfg, "accelGround", DEFAULT_CFG.accelGround)
    self.decelGround = cfgNum(_G, cfg, "decelGround", DEFAULT_CFG.decelGround)
    self.accelAir = cfgNum(_G, cfg, "accelAir", DEFAULT_CFG.accelAir)
    self.decelAir = cfgNum(_G, cfg, "decelAir", DEFAULT_CFG.decelAir)
    self.jumpSpeed = cfgNum(_G, cfg, "jumpSpeed", DEFAULT_CFG.jumpSpeed)
    self.coyoteTime = cfgNum(_G, cfg, "coyoteTime", DEFAULT_CFG.coyoteTime)
    self.jumpBuffer = cfgNum(_G, cfg, "jumpBuffer", DEFAULT_CFG.jumpBuffer)
    self.maxHorizSpeed = cfgNum(_G, cfg, "maxHorizSpeed", DEFAULT_CFG.maxHorizSpeed)
    self.maxFallSpeed = cfgNum(_G, cfg, "maxFallSpeed", DEFAULT_CFG.maxFallSpeed)
    self._coyote = 0
    self._jumpBuf = 0
    self._wishLocal = {x = 0, z = 0}
    self._wishWorld = {x = 0, z = 0}
    self._wishDir = {x = 0, z = 0}
    self._stepUpCd = 0
    self._stepDownCd = 0
end
function MovementSystem.prototype._raycastEx(self, frame, fx, fy, fz, tx, ty, tz, ignoreBodyId)
    local PHYS = frame.physics
    if not PHYS or KTypeOf(PHYS.raycastEx) ~= "function" then
        error(
            LuaConstruct(Error, "[move] frame.physics.raycastEx required"),
            0
        )
    end
    return PHYS:raycastEx({
        from = {fx, fy, fz},
        to = {tx, ty, tz},
        ignoreBodyId = bit32.bor(ignoreBodyId, 0)
    })
end
function MovementSystem.prototype._afterStepUpImpulse(self, bodyAccess, cc, dt)
    local su = cc.stepUp
    local snapUpSpeed = U:num(su.snapUpSpeed, 0)
    if snapUpSpeed <= 0 then
        return
    end
    if bodyAccess.mode == "SET_VEL" then
        local v = bodyAccess:getVel()
        local vy = U:vy(v, 0)
        if vy < snapUpSpeed then
            bodyAccess:setVel({
                x = U:vx(v, 0),
                y = snapUpSpeed,
                z = U:vz(v, 0)
            })
        end
        return
    end
    local m = U:num(cc.mass, 80)
    local dv = snapUpSpeed
    bodyAccess:applyImpulse(
        0,
        dv * m * U:clamp(dt, 0.001, 0.05),
        0
    )
end
function MovementSystem.prototype._tryStepUp(self, frame, bodyAccess, cc, wishDirWorld, dt)
    local su = cc.stepUp
    if not su or not su.enabled then
        return false
    end
    if not canTeleport(_G, bodyAccess) then
        return false
    end
    if self._stepUpCd > 0 then
        self._stepUpCd = math.max(0, self._stepUpCd - dt)
        return false
    end
    local dirx = wishDirWorld.x
    local dirz = wishDirWorld.z
    if dirx * dirx + dirz * dirz < 1e-8 then
        return false
    end
    local p = bodyAccess:position()
    local px = U:vx(p, 0)
    local py = U:vy(p, 0)
    local pz = U:vz(p, 0)
    local r = U:num(cc.radius, 0.35)
    local h = U:num(cc.height, 1.8)
    local footY = py - (h * 0.5 - r)
    local ignoreId = bit32.bor(frame.bodyId, 0) or 0
    local fwd = U:num(su.forwardProbe, 0.35)
    local up = U:num(su.upProbe, 0.6)
    local maxH = U:num(su.maxHeight, 0.4)
    local minH = math.max(
        0,
        U:num(su.minHeight, 0.04)
    )
    local minNy = U:num(su.minClearNormalY, 0.25)
    local probeX = px + dirx * (r + fwd)
    local probeZ = pz + dirz * (r + fwd)
    local yLow = footY + 0.06
    local yHigh = footY + maxH
    local hitLow = self:_raycastEx(
        frame,
        px,
        yLow,
        pz,
        probeX,
        yLow,
        probeZ,
        ignoreId
    )
    if not hitLow or hitLow.hit ~= true then
        return false
    end
    local hitHigh = self:_raycastEx(
        frame,
        px,
        yHigh,
        pz,
        probeX,
        yHigh,
        probeZ,
        ignoreId
    )
    if hitHigh and hitHigh.hit == true then
        return false
    end
    local downFromY = footY + maxH + up
    local downToY = footY - 0.12
    local hitTop = self:_raycastEx(
        frame,
        probeX,
        downFromY,
        probeZ,
        probeX,
        downToY,
        probeZ,
        ignoreId
    )
    if not hitTop or hitTop.hit ~= true then
        return false
    end
    local lua_hitTop_normal_5
    if hitTop.normal then
        lua_hitTop_normal_5 = U:num(hitTop.normal.y, 1)
    else
        lua_hitTop_normal_5 = 1
    end
    local ny = lua_hitTop_normal_5
    if ny < minNy then
        return false
    end
    local dist = U:num(hitTop.distance, 0 / 0)
    if not LuaNumberIsFinite(dist) then
        return false
    end
    local hitY = downFromY - dist
    local targetFootY = hitY + 0.012
    local targetCenterY = targetFootY + (h * 0.5 - r)
    local dy = targetCenterY - py
    if dy <= 0 or dy > maxH + 0.12 then
        return false
    end
    if dy < minH then
        return false
    end
    teleportBody(
        _G,
        bodyAccess,
        px,
        py + dy,
        pz
    )
    self:_afterStepUpImpulse(bodyAccess, cc, dt)
    self._stepUpCd = U:clamp(
        U:num(su.warpCooldown, 0.07),
        0,
        0.25
    )
    self._stepDownCd = self._stepUpCd
    return true
end
function MovementSystem.prototype.update(self, frame, characterCfg)
    if not self.enabled then
        return
    end
    local body = frame.bodyAccess
    if not body then
        error(
            LuaConstruct(Error, "[move] frame.bodyAccess required"),
            0
        )
    end
    local dt = U:clamp(
        U:num(frame.dt, 1 / 60),
        0,
        0.05
    )
    local input = frame.input
    local yaw = U:num(frame.view.yaw, 0)
    local grounded = not not frame.pose.grounded
    local lua_grounded_6
    if grounded then
        lua_grounded_6 = self.coyoteTime
    else
        lua_grounded_6 = math.max(0, self._coyote - dt)
    end
    self._coyote = lua_grounded_6
    local lua_input_jump_7
    if input.jump then
        lua_input_jump_7 = self.jumpBuffer
    else
        lua_input_jump_7 = math.max(0, self._jumpBuf - dt)
    end
    self._jumpBuf = lua_input_jump_7
    local v0 = body:getVel()
    local vx = U:vx(v0, 0)
    local vy = U:vy(v0, 0)
    local vz = U:vz(v0, 0)
    if vy < LuaNumber(-self.maxFallSpeed) then
        vy = LuaNumber(-self.maxFallSpeed)
    end
    self._wishLocal.x = bit32.bor(input.ax, 0)
    self._wishLocal.z = bit32.bor(input.az, 0)
    norm2(_G, self._wishLocal.x, self._wishLocal.z, self._wishDir)
    rotateByYaw(
        _G,
        self._wishDir.x,
        self._wishDir.z,
        yaw,
        self._wishWorld
    )
    local hasMove = self._wishDir.x ~= 0 or self._wishDir.z ~= 0
    local lua_input_run_8
    if input.run then
        lua_input_run_8 = self.runSpeed
    else
        lua_input_run_8 = self.walkSpeed
    end
    local targetSpeed = lua_input_run_8
    local lua_hasMove_9
    if hasMove then
        lua_hasMove_9 = self._wishWorld.x * targetSpeed
    else
        lua_hasMove_9 = 0
    end
    local targetVx = lua_hasMove_9
    local lua_hasMove_10
    if hasMove then
        lua_hasMove_10 = self._wishWorld.z * targetSpeed
    else
        lua_hasMove_10 = 0
    end
    local targetVz = lua_hasMove_10
    local lua_grounded_11
    if grounded then
        lua_grounded_11 = self.accelGround
    else
        lua_grounded_11 = self.accelAir
    end
    local accel = lua_grounded_11
    local lua_grounded_12
    if grounded then
        lua_grounded_12 = self.decelGround
    else
        lua_grounded_12 = self.decelAir
    end
    local decel = lua_grounded_12
    if hasMove then
        vx = moveTowards(_G, vx, targetVx, accel * dt)
        vz = moveTowards(_G, vz, targetVz, accel * dt)
    else
        vx = moveTowards(_G, vx, 0, decel * dt)
        vz = moveTowards(_G, vz, 0, decel * dt)
    end
    local hs = hypot2(_G, vx, vz)
    if hs > self.maxHorizSpeed then
        local k = self.maxHorizSpeed / hs
        vx = vx * k
        vz = vz * k
    end
    local jumpedThisTick = false
    if self._jumpBuf > 0 and self._coyote > 0 then
        self._jumpBuf = 0
        self._coyote = 0
        if vy < 0 then
            vy = 0
        end
        vy = self.jumpSpeed
        jumpedThisTick = true
    end
    local cc = characterCfg or frame.character
    local g = frame.ground
    if not jumpedThisTick and grounded and hasMove and cc and cc.stepUp and cc.stepUp.enabled then
        local stepped = self:_tryStepUp(
            frame,
            body,
            cc,
            self._wishWorld,
            dt
        )
        if stepped then
            local probe = frame.probeGroundCapsule
            if KTypeOf(probe) ~= "function" then
                error(
                    LuaConstruct(Error, "[move] frame.probeGroundCapsule required"),
                    0
                )
            end
            KFunction:call(
                probe,
                frame,
                body,
                cc,
                bit32.bor(frame.bodyId, 0)
            )
            frame.pose.grounded = not not frame.ground.grounded
        end
    end
    if self._stepDownCd > 0 then
        self._stepDownCd = math.max(0, self._stepDownCd - dt)
    end
    local lua_temp_13
    if cc and cc.stepDown then
        lua_temp_13 = cc.stepDown
    else
        lua_temp_13 = nil
    end
    local sd = lua_temp_13
    local lua_sd_14
    if sd then
        lua_sd_14 = not not sd.enabled
    else
        lua_sd_14 = true
    end
    local stepDownEnabled = lua_sd_14
    local lua_sd_15
    if sd then
        lua_sd_15 = U:num(sd.stickVel, 1.6)
    else
        lua_sd_15 = 1.6
    end
    local stickVel = lua_sd_15
    local lua_sd_16
    if sd then
        lua_sd_16 = U:num(sd.max, 0.28)
    else
        lua_sd_16 = 0.28
    end
    local stepDownMax = lua_sd_16
    local lua_sd_17
    if sd then
        lua_sd_17 = U:num(sd.deadZone, 0.015)
    else
        lua_sd_17 = 0.015
    end
    local deadZone = lua_sd_17
    local allowStick = stepDownEnabled and grounded and not g.steep and not jumpedThisTick and vy <= 0 and g.hasHit and self._stepDownCd <= 0
    if allowStick then
        if vy > LuaNumber(-stickVel) then
            vy = LuaNumber(-stickVel)
        end
        local fd = U:num(g.footDistance, 0)
        if fd < LuaNumber(-deadZone) and canTeleport(_G, body) then
            local down = LuaNumber(-fd)
            if down <= stepDownMax then
                local p = body:position()
                teleportBody(
                    _G,
                    body,
                    U:vx(p, 0),
                    U:vy(p, 0) + fd,
                    U:vz(p, 0)
                )
                vy = LuaNumber(-stickVel)
            end
        end
    end
    if body.mode == "SET_VEL" then
        body:setVel({x = vx, y = vy, z = vz})
    else
        local cur = body:getVel()
        local cvx = U:vx(cur, 0)
        local cvy = U:vy(cur, 0)
        local cvz = U:vz(cur, 0)
        local lua_temp_18
        if cc and cc.mass ~= nil then
            lua_temp_18 = U:num(cc.mass, 80)
        else
            lua_temp_18 = 80
        end
        local m = lua_temp_18
        body:applyImpulse((vx - cvx) * m, (vy - cvy) * m, (vz - cvz) * m)
    end
    frame.pose.vx = vx
    frame.pose.vy = vy
    frame.pose.vz = vz
    frame.pose.speed = KMath:hypot(vx, vy, vz)
    local lua_frame_pose_20 = frame.pose
    local lua_temp_19
    if vy < 0 then
        lua_temp_19 = LuaNumber(-vy)
    else
        lua_temp_19 = 0
    end
    lua_frame_pose_20.fallSpeed = lua_temp_19
end
M = MovementSystem

return M
