local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local LuaClass = luaRuntime.LuaClass
local LuaConstruct = luaRuntime.LuaConstruct
local LuaArraySetLength = luaRuntime.LuaArraySetLength
local LuaStringTrim = luaRuntime.LuaStringTrim
local Error = luaRuntime.Error
local LuaNumber = luaRuntime.LuaNumber
local LuaNumberIsFinite = luaRuntime.LuaNumberIsFinite
local LuaArrayIsArray = luaRuntime.LuaArrayIsArray
U = require("./camUtil.lua")
C = require("./CameraContract.lua")
CameraZoomController = require("./CameraZoomController.lua")
CameraCollisionSolver = require("./CameraCollisionSolver.lua")
CameraVolumeZones = require("./CameraVolumeZones.lua")
function arrHas(self, arr, code)
    do
        local i = 0
        local n = bit32.bor(KLength(arr), 0)
        while i < n do
            if bit32.bor(KIndex(arr, i), 0) == code then
                return true
            end
            i = i + 1
        end
    end
    return false
end
function smoothstep01(self, t)
    t = t < 0 and 0 or (t > 1 and 1 or t)
    return t * t * (3 - 2 * t)
end
CameraOrchestrator = LuaClass()
CameraOrchestrator.name = "CameraOrchestrator"
function CameraOrchestrator.prototype.lua_constructor(self, player)
    C:validatePlayer(player)
    self.player = player
    self.d = player.d
    self._byId = KObject:create(nil)
    self._modes = {}
    self._active = nil
    self._keyV = bit32.bor(
        self.d.input:keyCode("V"),
        0
    )
    self._vPrev = false
    self._switchCd = 0
    self._switchCdTime = 0.18
    self._tr = {
        active = false,
        t = 0,
        dur = 0.22,
        fromX = 0,
        fromY = 0,
        fromZ = 0
    }
    self.look = {
        yaw = 0,
        pitch = 0,
        sensitivity = 0.0002,
        pitchLimit = math.pi * 0.49,
        invertX = false,
        invertY = false
    }
    self.zoom = LuaConstruct(CameraZoomController, {
        steps = {
            2,
            4,
            8,
            16,
            32
        },
        index = 2,
        smooth = 18,
        cooldown = 0.08,
        min = 1.2,
        max = 60
    })
    self._zoomBaseMin = self.zoom.min
    self._zoomBaseMax = self.zoom.max
    self.collision = LuaConstruct(CameraCollisionSolver)
    self.zones = LuaConstruct(CameraVolumeZones, player)
    self._lastZonesCfgRef = nil
    self.postSmooth = 22
    self._sm = {x = 0, y = 0, z = 0}
    self._smInit = false
    self._ctx = {
        orchestrator = self,
        mode = nil,
        cam = self.d.camera,
        physics = self.d.physics,
        terrain = nil,
        dt = 0,
        snap = nil,
        input = nil,
        bodyId = 0,
        bodyPos = nil,
        look = self.look,
        zoom = self.zoom,
        target = {x = 0, y = 0, z = 0},
        outPos = {x = 0, y = 0, z = 0},
        zoneState = nil,
        zoneOverridesRaw = nil,
        modeConfig = nil,
        zoneOverrides = nil,
        _camMinY = -math.huge
    }
    self:register(require("./modes/first.lua"))
    self:register(require("./modes/third.lua"))
    local lua_temp_0
    if player.cfg and player.cfg.camera and player.cfg.camera.type then
        lua_temp_0 = tostring(player.cfg.camera.type)
    else
        lua_temp_0 = "third"
    end
    local initial = lua_temp_0
    self:setType(initial, true)
end
function CameraOrchestrator.prototype.destroy(self)
    self._active = nil
    LuaArraySetLength(self._modes, 0)
    self._byId = KObject:create(nil)
    self._ctx.mode = nil
    self._ctx.snap = nil
    self._ctx.input = nil
    self._ctx.bodyPos = nil
    self._ctx.zoneState = nil
    self._ctx.zoneOverridesRaw = nil
    self._ctx.zoneOverrides = nil
    self._ctx.modeConfig = nil
    self._ctx.terrain = nil
    self._smInit = false
    self.player = nil
    self.d = nil
end
function CameraOrchestrator.prototype.register(self, modeOrCtor)
    local lua_temp_1
    if KTypeOf(modeOrCtor) == "function" then
        lua_temp_1 = LuaConstruct(modeOrCtor, self)
    else
        lua_temp_1 = modeOrCtor
    end
    local m = lua_temp_1
    local mode = C:validateMode(m)
    local id = string.lower(LuaStringTrim(tostring(mode.id)))
    if self._byId[id] then
        error(
            LuaConstruct(Error, "[camera] duplicate mode id: " .. id),
            0
        )
    end
    self._byId[id] = mode
    local lua_self__modes_2 = self._modes
    lua_self__modes_2[#lua_self__modes_2 + 1] = mode
    if not self._active then
        self._active = mode
    end
    return id
end
function CameraOrchestrator.prototype.getType(self)
    local m = self._active
    if not m or not m.id then
        error(
            LuaConstruct(Error, "[camera] active mode is not set"),
            0
        )
    end
    return m.id
end
function CameraOrchestrator.prototype.setType(self, lua_type, instant)
    local id = string.lower(LuaStringTrim(tostring(lua_type or "")))
    local next = self._byId[id]
    if not next then
        error(
            LuaConstruct(
                Error,
                "[camera] unknown mode: " .. tostring(lua_type)
            ),
            0
        )
    end
    if self._active == next then
        return
    end
    local cam = self.d.camera
    if not instant and cam and KTypeOf(cam.location) == "function" then
        local p = cam:location()
        if p then
            self._tr.active = true
            self._tr.t = 0
            self._tr.dur = 0.22
            self._tr.fromX = U:vx(p, 0)
            self._tr.fromY = U:vy(p, 0)
            self._tr.fromZ = U:vz(p, 0)
        else
            self._tr.active = false
        end
    else
        self._tr.active = false
    end
    self._active = next
    local cfg = self.player and self.player.cfg
    if cfg then
        local lua_cfg_camera_4 = cfg.camera
        if not lua_cfg_camera_4 then
            local lua_temp_3 = {}
            cfg.camera = lua_temp_3
            lua_cfg_camera_4 = lua_temp_3
        end
        local c = lua_cfg_camera_4
        c.type = next.id
    end
    if self.player and self.player.dom and self.player.dom.view then
        self.player.dom.view.type = next.id
    end
    self._smInit = false
end
function CameraOrchestrator.prototype.next(self)
    local n = bit32.bor(#self._modes, 0)
    if n <= 1 then
        return
    end
    local cur = self._active
    local idx = 0
    do
        local i = 0
        while i < n do
            if self._modes[i + 1] == cur then
                idx = i
                break
            end
            i = i + 1
        end
    end
    self:setType(self._modes[(idx + 1) % n + 1].id, false)
end
function CameraOrchestrator.prototype.setTerrainSource(self, src)
    if src == nil then
        self._ctx.terrain = nil
        return
    end
    if KTypeOf(src.heightAt) ~= "function" then
        error(
            LuaConstruct(Error, "[camera] terrain source must provide heightAt(x,z)"),
            0
        )
    end
    if KTypeOf(src.normalAt) ~= "function" then
        error(
            LuaConstruct(Error, "[camera] terrain source must provide normalAt(x,z)"),
            0
        )
    end
    self._ctx.terrain = src
end
function CameraOrchestrator.prototype.setTerrainHandle(self, terrainApi, terrainHandle, world)
    if not terrainApi or KTypeOf(terrainApi.heightAt) ~= "function" then
        error(
            LuaConstruct(Error, "[camera] setTerrainHandle: terrainApi.heightAt(handle,x,z,world) is required"),
            0
        )
    end
    if KTypeOf(terrainApi.normalAt) ~= "function" then
        error(
            LuaConstruct(Error, "[camera] setTerrainHandle: terrainApi.normalAt(handle,x,z,world) is required"),
            0
        )
    end
    if not terrainHandle then
        error(
            LuaConstruct(Error, "[camera] setTerrainHandle: terrainHandle is required"),
            0
        )
    end
    local useWorld = world ~= false
    self._ctx.terrain = KObject:freeze({
        heightAt = function(lua_, x, z) return terrainApi:heightAt(terrainHandle, x, z, useWorld) end,
        normalAt = function(lua_, x, z)
            local m = terrainApi:normalAt(terrainHandle, x, z, useWorld)
            return {
                x = LuaNumber(m.x),
                y = LuaNumber(m.y),
                z = LuaNumber(m.z)
            }
        end
    })
end
function CameraOrchestrator.prototype._zonesCfgRef(self)
    local lua_temp_5
    if self.player and self.player.cfg and self.player.cfg.camera then
        lua_temp_5 = self.player.cfg.camera
    else
        lua_temp_5 = nil
    end
    local c = lua_temp_5
    local lua_c_6
    if c then
        lua_c_6 = c.volumeZones
    else
        lua_c_6 = nil
    end
    return lua_c_6
end
function CameraOrchestrator.prototype._syncZonesIfNeeded(self)
    local ref = self:_zonesCfgRef()
    if ref == self._lastZonesCfgRef then
        return
    end
    self._lastZonesCfgRef = ref
    self.zones:configureFromPlayerCfg()
end
function CameraOrchestrator.prototype._applyLook(self, snap)
    local dx = U:num(snap.dx, 0)
    local dy = U:num(snap.dy, 0)
    if self.look.invertX then
        dx = LuaNumber(-dx)
    end
    if self.look.invertY then
        dy = LuaNumber(-dy)
    end
    local lua_self_look_7, lua_yaw_8 = self.look, "yaw"
    lua_self_look_7[lua_yaw_8] = lua_self_look_7[lua_yaw_8] - dx * self.look.sensitivity
    local lua_self_look_9, lua_pitch_10 = self.look, "pitch"
    lua_self_look_9[lua_pitch_10] = lua_self_look_9[lua_pitch_10] - dy * self.look.sensitivity
end
function CameraOrchestrator.prototype._smoothOutPos(self, out, dt, enabled, minY)
    if not enabled or not (self.postSmooth > 0) then
        self._smInit = false
        if LuaNumberIsFinite(minY) then
            out.y = math.max(out.y, minY)
        end
        return out
    end
    if not self._smInit then
        self._smInit = true
        self._sm.x = out.x
        self._sm.y = out.y
        self._sm.z = out.z
    else
        self._sm.x = U:expSmooth(self._sm.x, out.x, self.postSmooth, dt)
        self._sm.y = U:expSmooth(self._sm.y, out.y, self.postSmooth, dt)
        self._sm.z = U:expSmooth(self._sm.z, out.z, self.postSmooth, dt)
    end
    if LuaNumberIsFinite(minY) then
        self._sm.y = math.max(self._sm.y, minY)
    end
    return self._sm
end
function CameraOrchestrator.prototype._applyTransition(self, pos, dt)
    local tr = self._tr
    if not tr.active then
        return pos
    end
    local lua_tr_12, lua_t_13 = tr, "t"
    local lua_temp_11
    if dt > 0 then
        lua_temp_11 = dt
    else
        lua_temp_11 = 0
    end
    lua_tr_12[lua_t_13] = lua_tr_12[lua_t_13] + lua_temp_11
    local a = smoothstep01(
        _G,
        tr.t / math.max(0.000001, tr.dur)
    )
    local x = tr.fromX + (pos.x - tr.fromX) * a
    local y = tr.fromY + (pos.y - tr.fromY) * a
    local z = tr.fromZ + (pos.z - tr.fromZ) * a
    if a >= 0.999 then
        tr.active = false
    end
    self._smInit = false
    return {x = x, y = y, z = z}
end
function CameraOrchestrator.prototype._applyPitchLimits(self, zoneOverrides)
    local baseMin = -self.look.pitchLimit
    local baseMax = self.look.pitchLimit
    local lua_temp_14
    if zoneOverrides and zoneOverrides.minPitch ~= nil then
        lua_temp_14 = LuaNumber(zoneOverrides.minPitch)
    else
        lua_temp_14 = baseMin
    end
    local minPitch = lua_temp_14
    local lua_temp_15
    if zoneOverrides and zoneOverrides.maxPitch ~= nil then
        lua_temp_15 = LuaNumber(zoneOverrides.maxPitch)
    else
        lua_temp_15 = baseMax
    end
    local maxPitch = lua_temp_15
    local lo = math.min(minPitch, maxPitch)
    local hi = math.max(minPitch, maxPitch)
    self.look.pitch = U:clamp(self.look.pitch, lo, hi)
end
function CameraOrchestrator.prototype._applyZoomLimits(self, zoneOverrides)
    self.zoom.min = self._zoomBaseMin
    self.zoom.max = self._zoomBaseMax
    if not zoneOverrides then
        return
    end
    local hasMin = zoneOverrides.zoomMin ~= nil
    local hasMax = zoneOverrides.zoomMax ~= nil
    if not hasMin and not hasMax then
        return
    end
    local lua_hasMin_16
    if hasMin then
        lua_hasMin_16 = LuaNumber(zoneOverrides.zoomMin)
    else
        lua_hasMin_16 = self.zoom.min
    end
    local zmin = lua_hasMin_16
    local lua_hasMax_17
    if hasMax then
        lua_hasMax_17 = LuaNumber(zoneOverrides.zoomMax)
    else
        lua_hasMax_17 = self.zoom.max
    end
    local zmax = lua_hasMax_17
    self.zoom.min = zmin
    self.zoom.max = math.max(zmin, zmax)
end
function CameraOrchestrator.prototype._handleModeSwitch(self, dt, snap)
    self._switchCd = math.max(0, self._switchCd - dt)
    local lua_temp_18
    if snap and LuaArrayIsArray(snap.keysDown) then
        lua_temp_18 = snap.keysDown
    else
        lua_temp_18 = nil
    end
    local kd = lua_temp_18
    if not kd then
        self._vPrev = false
        return
    end
    local vDown = self._keyV > 0 and arrHas(_G, kd, self._keyV)
    local pressed = self._switchCd == 0 and vDown and not self._vPrev
    self._vPrev = vDown
    if pressed then
        self._switchCd = self._switchCdTime
        self:next()
    end
end
function CameraOrchestrator.prototype.update(self, dt, frame)
    if not frame or not frame.snap then
        return
    end
    dt = U:clamp(
        U:num(dt, 1 / 60),
        0,
        0.05
    )
    local snap = frame.snap
    self:_applyLook(snap)
    local phys = self.d.physics
    local bodyId = bit32.bor(
        self.player:getBodyId(),
        0
    )
    local bodyPos = phys:position(bodyId)
    if not bodyPos then
        error(
            LuaConstruct(
                Error,
                "[camera] physics.position(bodyId) returned null bodyId=" .. tostring(bodyId)
            ),
            0
        )
    end
    self:_syncZonesIfNeeded()
    local zoneState = self.zones:update(bodyPos)
    local zoneOverridesRaw = self.zones:blendedOverrides(nil)
    self:_applyPitchLimits(zoneOverridesRaw)
    local cam = self.d.camera
    cam:setYawPitch(self.look.yaw, self.look.pitch)
    self:_handleModeSwitch(dt, snap)
    local mode = self._active
    local ctx = self._ctx
    ctx.mode = mode
    ctx.dt = dt
    ctx.snap = snap
    ctx.input = frame.input
    ctx.bodyId = bodyId
    ctx.bodyPos = bodyPos
    ctx.zoneState = zoneState
    ctx.zoneOverridesRaw = zoneOverridesRaw
    ctx.modeConfig = nil
    ctx.zoneOverrides = zoneOverridesRaw
    ctx._camMinY = -math.huge
    if mode.meta.supportsZoom then
        self:_applyZoomLimits(zoneOverridesRaw)
        self.zoom:update(dt, ctx)
    else
        self:_applyZoomLimits(nil)
    end
    mode:update(ctx)
    local effective = self.zones:blendedOverrides(ctx.modeConfig)
    ctx.zoneOverrides = effective
    if mode.meta.hasCollision and self.collision.enabled then
        self.collision:solve(ctx)
    end
    local sm = self:_smoothOutPos(ctx.outPos, dt, mode.id == "third", ctx._camMinY)
    local p = self:_applyTransition(sm, dt)
    cam:setLocation(p.x, p.y, p.z)
end
M = CameraOrchestrator

return M
