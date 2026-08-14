local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local LuaClass = luaRuntime.LuaClass
local Error = luaRuntime.Error
local LuaConstruct = luaRuntime.LuaConstruct
local LuaNumberIsFinite = luaRuntime.LuaNumberIsFinite
U = require("./util.lua")
FrameContext = LuaClass()
FrameContext.name = "FrameContext"
function FrameContext.prototype.lua_constructor(self)
    self.dt = 0
    self.snap = nil
    self.physics = nil
    self.bodyAccess = nil
    self.bodyId = 0
    self.input = {
        ax = 0,
        az = 0,
        run = false,
        jump = false,
        lmbDown = false,
        lmbJustPressed = false,
        dx = 0,
        dy = 0,
        wheel = 0
    }
    self.view = {yaw = 0, pitch = 0, type = "third"}
    self.pose = {
        x = 0,
        y = 0,
        z = 0,
        vx = 0,
        vy = 0,
        vz = 0,
        speed = 0,
        fallSpeed = 0,
        grounded = false,
        rx = 0,
        ry = 0,
        rz = 0,
        rw = 1,
        avx = 0,
        avy = 0,
        avz = 0
    }
    self.ground = {
        hasHit = false,
        grounded = false,
        steep = false,
        nx = 0,
        ny = 1,
        nz = 0,
        distance = 9999,
        footDistance = 9999
    }
    self.character = {radius = 0.35, height = 1.8, eyeHeight = 1.65}
    self._rc = {from = {0, 0, 0}, to = {0, 0, 0}, ignoreBodyId = 0}
end
function FrameContext.prototype.begin(self, player, dt, snap)
    self.dt = U:num(dt, 0)
    self.snap = snap or nil
    local P = player and player.d and player.d.physics
    if not P then
        error(
            LuaConstruct(Error, "[FrameContext] domains.physics is required"),
            0
        )
    end
    self.physics = P
    local cc = player.characterCfg or player.cfg and player.cfg.character or nil
    self.character.radius = U:num(cc and cc.radius, 0.35)
    self.character.height = U:num(cc and cc.height, 1.8)
    self.character.eyeHeight = U:num(cc and cc.eyeHeight, 1.65)
    return self
end
function FrameContext.prototype._raycastEx(self, fx, fy, fz, tx, ty, tz, ignoreBodyId)
    local rc = self._rc
    local from = rc.from
    local to = rc.to
    from[1] = fx
    from[2] = fy
    from[3] = fz
    to[1] = tx
    to[2] = ty
    to[3] = tz
    rc.ignoreBodyId = bit32.bor(ignoreBodyId, 0)
    return self.physics:raycastEx(rc)
end
function FrameContext.prototype.probeGroundCapsule(self, bodyAccess, cfg, ignoreBodyId)
    local g = self.ground
    g.hasHit = false
    g.grounded = false
    g.steep = false
    g.nx = 0
    g.ny = 1
    g.nz = 0
    g.distance = 9999
    g.footDistance = 9999
    if not bodyAccess or KTypeOf(bodyAccess.position) ~= "function" then
        return false
    end
    local p = bodyAccess:position()
    if not p then
        return false
    end
    local px = U:vx(p, 0)
    local py = U:vy(p, 0)
    local pz = U:vz(p, 0)
    local r = U:num(cfg and cfg.radius, self.character.radius)
    local h = U:num(cfg and cfg.height, self.character.height)
    local footY = py - (h * 0.5 - r)
    local rayDown = U:num(cfg and cfg.groundRay, 0.55)
    local startUp = U:num(cfg and cfg.groundStart, 0.2)
    local eps = U:num(cfg and cfg.groundEps, 0.08)
    local maxSlopeDot = U:num(cfg and cfg.maxSlopeDot, 0.55)
    local ring = U:clamp(
        U:num(cfg and cfg.probeRing, 0.85),
        0.1,
        1.2
    ) * r
    local startY = footY + startUp
    local endY = footY - rayDown
    local ignoreId = bit32.bor(ignoreBodyId, 0) or 0
    local bestWalk = nil
    local bestWalkDist = 9999
    local bestAny = nil
    local bestAnyDist = 9999
    local function test(lua_, ox, oz)
        local hx = px + ox
        local hz = pz + oz
        local hit = self:_raycastEx(
            hx,
            startY,
            hz,
            hx,
            endY,
            hz,
            ignoreId
        )
        if not hit or hit.hit ~= true then
            return
        end
        local dist = U:num(hit.distance, 0 / 0)
        if not LuaNumberIsFinite(dist) then
            return
        end
        local n = hit.normal
        local lua_n_0
        if n then
            lua_n_0 = U:num(n.y, 1)
        else
            lua_n_0 = 1
        end
        local ny = lua_n_0
        if dist < bestAnyDist then
            bestAny = hit
            bestAnyDist = dist
        end
        if ny >= maxSlopeDot and dist < bestWalkDist then
            bestWalk = hit
            bestWalkDist = dist
        end
    end
    test(_G, 0, 0)
    test(_G, ring, 0)
    test(_G, -ring, 0)
    test(_G, 0, ring)
    test(_G, 0, -ring)
    local chosen = bestWalk or bestAny
    if not chosen then
        return false
    end
    local n = chosen.normal or ({x = 0, y = 1, z = 0})
    local lua_temp_1
    if chosen == bestWalk then
        lua_temp_1 = bestWalkDist
    else
        lua_temp_1 = bestAnyDist
    end
    local dist = lua_temp_1
    g.hasHit = true
    g.distance = dist
    g.nx = U:num(n.x, 0)
    g.ny = U:num(n.y, 1)
    g.nz = U:num(n.z, 0)
    local footDist = startUp - dist
    g.footDistance = footDist
    local inContact = dist <= startUp + eps
    local walkable = inContact and g.ny >= maxSlopeDot
    g.grounded = walkable
    g.steep = inContact and not walkable
    return g.grounded
end
M = FrameContext

return M
