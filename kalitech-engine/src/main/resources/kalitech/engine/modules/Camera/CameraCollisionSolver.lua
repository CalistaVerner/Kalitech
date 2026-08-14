local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local Numbers = luaRuntime.number
local Classes = luaRuntime.class
local SparseArrays = luaRuntime.sparseArray
local Error = luaRuntime.Error
U = require("./camUtil.lua")
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
function invSqrt(self, x)
    local lua_temp_2
    if x > 0 then
        lua_temp_2 = 1 / math.sqrt(x)
    else
        lua_temp_2 = 0
    end
    return lua_temp_2
end
function getDbg(self, enabled)
    if not enabled then
        return nil
    end
    local lua_temp_3
    if KTypeOf(ENGINE) ~= "nil" then
        lua_temp_3 = ENGINE
    else
        lua_temp_3 = nil
    end
    local e = lua_temp_3
    if not e then
        return nil
    end
    local d = e.debug
    if not d then
        return nil
    end
    local lua_temp_4
    if KTypeOf(d) == "function" then
        lua_temp_4 = d(_G)
    else
        lua_temp_4 = d
    end
    local dbg = lua_temp_4
    if not dbg then
        return nil
    end
    if KTypeOf(dbg.scope) == "function" then
        return dbg:scope("camera"):scope("collision")
    end
    if KTypeOf(dbg.child) == "function" then
        return dbg:child("camera"):child("collision")
    end
    return dbg
end
function requireTerrainApi(self)
    local lua_temp_5
    if KTypeOf(ENGINE) ~= "nil" then
        lua_temp_5 = ENGINE
    else
        lua_temp_5 = nil
    end
    local e = lua_temp_5
    if not e or not e.terrain then
        error(
            Classes:construct(Error, "[camera][collision] ENGINE.terrain is required"),
            0
        )
    end
    local terr = e.terrain
    if KTypeOf(terr.heightAt) ~= "function" or KTypeOf(terr.normalAt) ~= "function" then
        error(
            Classes:construct(Error, "[camera][collision] ENGINE.terrain.heightAt/normalAt are required"),
            0
        )
    end
    return terr
end
function requireTerrainHandle(self)
    local lua_temp_6
    if KTypeOf(ENGINE.sceneTerrain) ~= "nil" then
        lua_temp_6 = ENGINE.sceneTerrain
    else
        lua_temp_6 = nil
    end
    local t = lua_temp_6
    if not t then
        error(
            Classes:construct(Error, "[camera][collision] ENGINE.sceneTerrain global is required when terrain sampling is enabled"),
            0
        )
    end
    local lua_temp_7
    if t and KTypeOf(t) == "table" and t.surface then
        lua_temp_7 = t.surface
    else
        lua_temp_7 = t
    end
    return lua_temp_7
end
function normalizeDir(self, dx, dy, dz)
    local l2 = dx * dx + dy * dy + dz * dz
    if l2 < 1e-12 then
        return {
            x = 0,
            y = 1,
            z = 0,
            len = 0,
            inv = 0
        }
    end
    local len = math.sqrt(l2)
    local inv = 1 / len
    return {
        x = dx * inv,
        y = dy * inv,
        z = dz * inv,
        len = len,
        inv = inv
    }
end
function orthonormalBasisFromDir(self, dx, dy, dz)
    local ax = 0
    local ay = 1
    local az = 0
    if math.abs(dy) > 0.95 then
        ax = 1
        ay = 0
        az = 0
    end
    local rx = dy * az - dz * ay
    local ry = dz * ax - dx * az
    local rz = dx * ay - dy * ax
    local invR = invSqrt(_G, rx * rx + ry * ry + rz * rz)
    rx = rx * invR
    ry = ry * invR
    rz = rz * invR
    local ux = ry * dz - rz * dy
    local uy = rz * dx - rx * dz
    local uz = rx * dy - ry * dx
    local invU = invSqrt(_G, ux * ux + uy * uy + uz * uz)
    ux = ux * invU
    uy = uy * invU
    uz = uz * invU
    return {
        rx = rx,
        ry = ry,
        rz = rz,
        ux = ux,
        uy = uy,
        uz = uz
    }
end
function dbgSphere(self, dbg, p, r, col, ttl, depth, a, seg)
    if dbg and KTypeOf(dbg.sphere) == "function" then
        dbg:sphere(
            p,
            r,
            col,
            ttl,
            depth,
            a,
            seg or 10
        )
    end
end
function dbgLine(self, dbg, a, b, col, ttl, depth, alpha)
    if dbg and KTypeOf(dbg.line) == "function" then
        dbg:line(
            a,
            b,
            col,
            ttl,
            depth,
            alpha
        )
    end
end
function dbgRay(self, dbg, o, d, len, col, ttl, depth, alpha, arrow, arrowLen)
    if dbg and KTypeOf(dbg.ray) == "function" then
        local lua_dbg_10 = dbg
        local lua_dbg_ray_11 = dbg.ray
        local lua_array_9 = SparseArrays:new(
            o,
            d,
            len,
            col,
            ttl,
            depth,
            alpha,
            not not arrow
        )
        local lua_temp_8
        if arrowLen ~= nil then
            lua_temp_8 = arrowLen
        else
            lua_temp_8 = 0.12
        end
        SparseArrays:push(lua_array_9, lua_temp_8)
        lua_dbg_ray_11(
            lua_dbg_10,
            SparseArrays:spread(lua_array_9)
        )
    end
end
function isObj(self, v)
    return not not v and KTypeOf(v) == "table"
end
function normalizeRayHit(self, hit)
    if not hit or hit.hit ~= true then
        return nil
    end

    local point = hit.point
    if not point or not isObj(_G, point) then
        return nil
    end

    local x = Numbers:coerce(point.x)
    local y = Numbers:coerce(point.y)
    local z = Numbers:coerce(point.z)
    if not (Numbers:isFinite(x) and Numbers:isFinite(y) and Numbers:isFinite(z)) then
        return nil
    end

    local nx, ny, nz = 0, 1, 0
    local normal = hit.normal
    if normal and isObj(_G, normal) then
        nx = Numbers:coerce(normal.x)
        ny = Numbers:coerce(normal.y)
        nz = Numbers:coerce(normal.z)
        if not (Numbers:isFinite(nx) and Numbers:isFinite(ny) and Numbers:isFinite(nz)) then
            nx, ny, nz = 0, 1, 0
        end

        local inverseLength = invSqrt(_G, nx * nx + ny * ny + nz * nz)
        if inverseLength > 0 then
            nx = nx * inverseLength
            ny = ny * inverseLength
            nz = nz * inverseLength
        else
            nx, ny, nz = 0, 1, 0
        end
    end

    return {
        hit = true,
        x = x,
        y = y,
        z = z,
        normal = {x = nx, y = ny, z = nz}
    }
end

function physicsRay(self, physics, ox, oy, oz, dx, dy, dz, length, ignoreBodyId)
    if not physics or KTypeOf(physics.raycastEx) ~= "function" then
        error(
            Classes:construct(Error, "[camera][collision] physics.raycastEx(cfg) is required"),
            0
        )
    end

    local direction = normalizeDir(_G, dx, dy, dz)
    if direction.len <= 1e-8 then
        return nil
    end

    local rayLength = math.max(0.01, Numbers:coerce(length))
    local hit = physics:raycastEx({
        from = {ox, oy, oz},
        to = {
            ox + direction.x * rayLength,
            oy + direction.y * rayLength,
            oz + direction.z * rayLength
        },
        ignoreBodyId = bit32.bor(ignoreBodyId, 0),
        staticOnly = false
    })
    return normalizeRayHit(_G, hit)
end
function bundleHit(self, phys, from, dirN, len, radius, ignoreBodyId)
    local B = orthonormalBasisFromDir(_G, dirN.x, dirN.y, dirN.z)
    local ox1 = B.rx * radius
    local oy1 = B.ry * radius
    local oz1 = B.rz * radius
    local ox2 = B.ux * radius
    local oy2 = B.uy * radius
    local oz2 = B.uz * radius
    local origins = {
        {from.x, from.y, from.z},
        {from.x + ox1, from.y + oy1, from.z + oz1},
        {from.x - ox1, from.y - oy1, from.z - oz1},
        {from.x + ox2, from.y + oy2, from.z + oz2},
        {from.x - ox2, from.y - oy2, from.z - oz2}
    }
    local best = nil
    local bestD = math.huge
    do
        local i = 0
        while i < #origins do
            do
                local o = origins[i + 1]
                local h = physicsRay(
                    _G,
                    phys,
                    o[1],
                    o[2],
                    o[3],
                    dirN.x,
                    dirN.y,
                    dirN.z,
                    len,
                    ignoreBodyId
                )
                if not h or not h.hit then
                    goto lua_continue56
                end
                local hx = h.x
                local hy = h.y
                local hz = h.z
                local dx = hx - o[1]
                local dy = hy - o[2]
                local dz = hz - o[3]
                local d = dx * dirN.x + dy * dirN.y + dz * dirN.z
                if d >= 0 and d < bestD then
                    bestD = d
                    best = h
                end
            end
            ::lua_continue56::
            i = i + 1
        end
    end
    return best
end
function pearRadius(self, nearR, farR, t, k)
    t = clamp(_G, t, 0, 1)
    local w = t ^ k
    return nearR + (farR - nearR) * w
end
function resolvePearObstacle(self, phys, dbg, from, to, farRadius, nearRadius, pearK, pad, ttl, depth, axisLen, samples, ignoreBodyId)
    local dx = to.x - from.x
    local dy = to.y - from.y
    local dz = to.z - from.z
    local nd = normalizeDir(_G, dx, dy, dz)
    if nd.len <= 0.000001 then
        return false
    end
    local len = nd.len
    local best = nil
    local bestR = farRadius
    local N = math.max(
        3,
        bit32.bor(samples, 0)
    )
    do
        local i = 1
        while i <= N do
            local t = i / N
            local r = pearRadius(
                _G,
                nearRadius,
                farRadius,
                t,
                pearK
            )
            local skin = r + pad
            local sx = from.x + dx * t
            local sy = from.y + dy * t
            local sz = from.z + dz * t
            local remain = len * (1 - t) + skin
            local h = bundleHit(
                _G,
                phys,
                {x = sx, y = sy, z = sz},
                nd,
                remain,
                r,
                ignoreBodyId
            )
            if h and h.hit then
                best = h
                bestR = r
                break
            end
            i = i + 1
        end
    end
    if not best then
        return false
    end
    local n = best.normal or ({x = 0, y = 1, z = 0})
    local nx = n.x
    local ny = n.y
    local nz = n.z
    if not (Numbers:isFinite(nx) and Numbers:isFinite(ny) and Numbers:isFinite(nz)) then
        nx = 0
        ny = 1
        nz = 0
    end
    local invN = invSqrt(_G, nx * nx + ny * ny + nz * nz)
    if invN > 0 then
        nx = nx * invN
        ny = ny * invN
        nz = nz * invN
    else
        nx = 0
        ny = 1
        nz = 0
    end
    local skin = bestR + pad
    local hx = best.x
    local hy = best.y
    local hz = best.z
    local cx = hx - nx * skin
    local cy = hy - ny * skin
    local cz = hz - nz * skin
    local dot = dx * nx + dy * ny + dz * nz
    local sx = dx - nx * dot
    local sy = dy - ny * dot
    local sz = dz - nz * dot
    if dbg then
        dbgSphere(
            _G,
            dbg,
            {hx, hy, hz},
            0.08,
            {1, 0.4, 0.2, 0.95},
            ttl,
            depth,
            0.95,
            12
        )
        dbgRay(
            _G,
            dbg,
            {hx, hy, hz},
            {nx, ny, nz},
            axisLen,
            {1, 0.4, 0.2, 0.95},
            ttl,
            depth,
            0.95,
            true,
            0.12
        )
        dbgSphere(
            _G,
            dbg,
            {cx, cy, cz},
            0.06,
            {1, 0.85, 0.1, 0.95},
            ttl,
            depth,
            0.95,
            10
        )
    end
    to.x = cx + sx * 0.25
    to.y = cy + sy * 0.25
    to.z = cz + sz * 0.25
    return true
end
function sampleGround(self, ctx, x, yHint, z, lift, len, useTerr, terrWorld, ignoreBodyId)
    local phys = ctx.physics
    local yPhys = 0 / 0
    local nxP = 0
    local nyP = 1
    local nzP = 0
    local haveP = false
    local startY = yHint + math.max(0.25, lift)
    local down = physicsRay(
        _G,
        phys,
        x,
        startY,
        z,
        0,
        -1,
        0,
        math.max(0.01, len),
        ignoreBodyId
    )
    if down and down.hit then
        yPhys = down.y
        local n = down.normal
        if n then
            nxP = n.x
            nyP = n.y
            nzP = n.z
            haveP = Numbers:isFinite(nxP) and Numbers:isFinite(nyP) and Numbers:isFinite(nzP)
        end
    end
    local yTerr = 0 / 0
    local nxT = 0
    local nyT = 1
    local nzT = 0
    local haveT = false
    local terrChosenWorld = not not terrWorld
    if useTerr then
        local terrApi = requireTerrainApi(_G)
        local terrainH = requireTerrainHandle(_G)
        local yW = Numbers:coerce(terrApi:heightAt(terrainH, x, z, true))
        local yL = Numbers:coerce(terrApi:heightAt(terrainH, x, z, false))
        local lua_Number_isFinite_result_14
        if Numbers:isFinite(yW) then
            lua_Number_isFinite_result_14 = math.abs(yW - yHint)
        else
            lua_Number_isFinite_result_14 = math.huge
        end
        local dw = lua_Number_isFinite_result_14
        local lua_Number_isFinite_result_15
        if Numbers:isFinite(yL) then
            lua_Number_isFinite_result_15 = math.abs(yL - yHint)
        else
            lua_Number_isFinite_result_15 = math.huge
        end
        local dl = lua_Number_isFinite_result_15
        terrChosenWorld = dw <= dl
        local lua_terrChosenWorld_16
        if terrChosenWorld then
            lua_terrChosenWorld_16 = yW
        else
            lua_terrChosenWorld_16 = yL
        end
        yTerr = lua_terrChosenWorld_16
        local n = terrApi:normalAt(terrainH, x, z, terrChosenWorld)
        if n then
            nxT = Numbers:coerce(n.x)
            nyT = Numbers:coerce(n.y)
            nzT = Numbers:coerce(n.z)
            haveT = Numbers:isFinite(nxT) and Numbers:isFinite(nyT) and Numbers:isFinite(nzT)
        end
    end
    local y = 0 / 0
    local nx = 0
    local ny = 1
    local nz = 0
    local haveN = false
    if Numbers:isFinite(yPhys) then
        y = yPhys
        if haveP then
            nx = nxP
            ny = nyP
            nz = nzP
            haveN = true
        elseif haveT then
            nx = nxT
            ny = nyT
            nz = nzT
            haveN = true
        end
    elseif Numbers:isFinite(yTerr) then
        y = yTerr
        if haveT then
            nx = nxT
            ny = nyT
            nz = nzT
            haveN = true
        end
    end
    if haveN then
        local invN = invSqrt(_G, nx * nx + ny * ny + nz * nz)
        if invN > 0 then
            nx = nx * invN
            ny = ny * invN
            nz = nz * invN
        else
            nx = 0
            ny = 1
            nz = 0
            haveN = false
        end
    end
    return {
        y = y,
        nx = nx,
        ny = ny,
        nz = nz,
        haveN = haveN,
        terrChosenWorld = terrChosenWorld
    }
end
function pearGroundClamp(self, ctx, dbg, from, to, nearR, farR, pearK, baseFloorPad, slopePadScale, samples, lift, lenDown, ttl, depth, debugMinYSpan, ignoreBodyId)
    local dx = to.x - from.x
    local dy = to.y - from.y
    local dz = to.z - from.z
    local N = math.max(
        3,
        bit32.bor(samples, 0)
    )
    local useTerr = not not ctx._useTerrainHeight
    local terrWorld = not not ctx._terrainWorld
    local needLift = 0
    local minYAtCam = -math.huge
    do
        local i = 0
        while i <= N do
            do
                local t = i / N
                local px = from.x + dx * t
                local py = from.y + dy * t
                local pz = from.z + dz * t
                local r = pearRadius(
                    _G,
                    nearR,
                    farR,
                    t,
                    pearK
                )
                local g = sampleGround(
                    _G,
                    ctx,
                    px,
                    py,
                    pz,
                    lift,
                    lenDown,
                    useTerr,
                    terrWorld,
                    ignoreBodyId
                )
                if not Numbers:isFinite(g.y) then
                    goto lua_continue85
                end
                local nyClamped = clamp(_G, g.ny, 0, 1)
                local slope = 1 - nyClamped
                local floorPadEff = baseFloorPad + slope * slopePadScale
                local reqCenterY = g.y + r + floorPadEff
                local pen = reqCenterY - py
                if pen > needLift then
                    needLift = pen
                end
                if i == N then
                    minYAtCam = reqCenterY
                    if dbg then
                        local s = debugMinYSpan
                        dbgLine(
                            _G,
                            dbg,
                            {to.x - s, reqCenterY, to.z},
                            {to.x + s, reqCenterY, to.z},
                            {1, 0.2, 0.85, 0.75},
                            ttl,
                            depth,
                            0.75
                        )
                    end
                end
                if dbg and i % 2 == 0 then
                    dbgSphere(
                        _G,
                        dbg,
                        {px, py, pz},
                        math.max(0.02, r),
                        {0.25, 1, 0.6, 0.12},
                        ttl,
                        depth,
                        0.12,
                        10
                    )
                    dbgSphere(
                        _G,
                        dbg,
                        {px, g.y, pz},
                        0.06,
                        {0.8, 0.95, 0.55, 0.35},
                        ttl,
                        depth,
                        0.35,
                        10
                    )
                end
            end
            ::lua_continue85::
            i = i + 1
        end
    end
    if needLift > 0 then
        to.y = to.y + needLift
    end
    return {minYAtCam = minYAtCam, lifted = needLift}
end
CameraCollisionSolver = Classes:create()
CameraCollisionSolver.name = "CameraCollisionSolver"
function CameraCollisionSolver.prototype.lua_constructor(self)
    self.enabled = true
    self.radius = 0.25
    self.nearRadius = 0.05
    self.pearK = 1.9
    self.pearSamples = 8
    self.surfacePadding = 0.08
    self.floorPadding = 0.2
    self.maxRayLenDown = 10
    self.groundRayLift = 1.2
    self.smooth = 18
    self.groundSnapPen = 0.55
    self.useTerrainHeight = true
    self.terrainWorld = true
    self.slopePadScale = 0.45
    self.slopeSlide = 0.35
    self.slopeMinNy = 0.35
    self.debugDraw = false
    self.debugTTL = 0.06
    self.debugDepth = false
    self.debugAxisLen = 0.45
    self.debugMinYSpan = 0.55
    self.debugPear = true
    self.debugPearStep = 2
    self.obstaclePasses = 2
end
function CameraCollisionSolver.prototype.solve(self, ctx)
    if not self.enabled then
        return
    end
    ctx._camMinY = -math.huge
    local phys = ctx and ctx.physics
    if not phys or KTypeOf(phys.raycastEx) ~= "function" and KTypeOf(phys.raycast) ~= "function" then
        error(
            Classes:construct(Error, "[camera][collision] ctx.physics.raycastEx(cfg) or raycast(cfg) is required"),
            0
        )
    end
    local zo = ctx.zoneOverrides
    if zo and zo.collisionEnabled == false then
        return
    end
    local from = ctx.target
    local to = ctx.outPos
    if not from or not to then
        error(
            Classes:construct(Error, "[camera][collision] ctx.target and ctx.outPos are required"),
            0
        )
    end
    local ignoreBodyId = bit32.bor(ctx.bodyId, 0) or 0
    local lua_temp_17
    if zo and zo.camRadius ~= nil then
        lua_temp_17 = Numbers:coerce(zo.camRadius)
    else
        lua_temp_17 = self.radius
    end
    local farR = lua_temp_17
    local lua_temp_18
    if zo and zo.nearRadius ~= nil then
        lua_temp_18 = Numbers:coerce(zo.nearRadius)
    else
        lua_temp_18 = self.nearRadius
    end
    local nearR = lua_temp_18
    local lua_temp_19
    if zo and zo.pearK ~= nil then
        lua_temp_19 = Numbers:coerce(zo.pearK)
    else
        lua_temp_19 = self.pearK
    end
    local pearK = lua_temp_19
    local lua_temp_20
    if zo and zo.pearSamples ~= nil then
        lua_temp_20 = bit32.bor(zo.pearSamples, 0)
    else
        lua_temp_20 = self.pearSamples
    end
    local pearSamples = lua_temp_20
    local lua_temp_21
    if zo and zo.surfacePadding ~= nil then
        lua_temp_21 = Numbers:coerce(zo.surfacePadding)
    else
        lua_temp_21 = self.surfacePadding
    end
    local pad = lua_temp_21
    local lua_temp_22
    if zo and zo.floorPadding ~= nil then
        lua_temp_22 = Numbers:coerce(zo.floorPadding)
    else
        lua_temp_22 = self.floorPadding
    end
    local baseFloorPad = lua_temp_22
    local lua_temp_23
    if zo and zo.slopePadScale ~= nil then
        lua_temp_23 = Numbers:coerce(zo.slopePadScale)
    else
        lua_temp_23 = self.slopePadScale
    end
    local slopePadScale = lua_temp_23
    local lua_temp_24
    if zo and zo.slopeSlide ~= nil then
        lua_temp_24 = Numbers:coerce(zo.slopeSlide)
    else
        lua_temp_24 = self.slopeSlide
    end
    local slopeSlide = lua_temp_24
    local dt = clamp(
        _G,
        U:num(ctx.dt, 1 / 60),
        0,
        0.05
    )
    local dbg = getDbg(_G, self.debugDraw or zo and zo.debugDraw == true)
    local lua_temp_25
    if zo and zo.debugTTL ~= nil then
        lua_temp_25 = Numbers:coerce(zo.debugTTL)
    else
        lua_temp_25 = self.debugTTL
    end
    local ttl = lua_temp_25
    local lua_temp_26
    if zo and zo.debugDepth ~= nil then
        lua_temp_26 = not not zo.debugDepth
    else
        lua_temp_26 = self.debugDepth
    end
    local depth = lua_temp_26
    local lua_ctx_28 = ctx
    local lua_temp_27
    if zo and zo.useTerrainHeight ~= nil then
        lua_temp_27 = not not zo.useTerrainHeight
    else
        lua_temp_27 = not not self.useTerrainHeight
    end
    lua_ctx_28._useTerrainHeight = lua_temp_27
    local lua_ctx_30 = ctx
    local lua_temp_29
    if zo and zo.terrainWorld ~= nil then
        lua_temp_29 = not not zo.terrainWorld
    else
        lua_temp_29 = not not self.terrainWorld
    end
    lua_ctx_30._terrainWorld = lua_temp_29
    do
        local lua_clamp_33 = clamp
        local lua_G_32 = _G
        local lua_temp_31
        if zo and zo.obstaclePasses ~= nil then
            lua_temp_31 = bit32.bor(zo.obstaclePasses, 0)
        else
            lua_temp_31 = self.obstaclePasses
        end
        local passes = lua_clamp_33(lua_G_32, lua_temp_31, 1, 3)
        do
            local i = 0
            while i < passes do
                local changed = resolvePearObstacle(
                    _G,
                    phys,
                    dbg,
                    from,
                    to,
                    farR,
                    nearR,
                    pearK,
                    pad,
                    ttl,
                    depth,
                    self.debugAxisLen * (i and 0.8 or 1),
                    pearSamples,
                    ignoreBodyId
                )
                if not changed then
                    break
                end
                i = i + 1
            end
        end
        if dbg and (self.debugPear or zo and zo.debugPear == true) then
            local N = math.max(
                3,
                bit32.bor(pearSamples, 0)
            )
            local step = math.max(
                1,
                bit32.bor(self.debugPearStep, 0)
            )
            do
                local i = 0
                while i <= N do
                    do
                        if i % step ~= 0 then
                            goto lua_continue104
                        end
                        local t = i / N
                        local r = pearRadius(
                            _G,
                            nearR,
                            farR,
                            t,
                            pearK
                        )
                        local px = from.x + (to.x - from.x) * t
                        local py = from.y + (to.y - from.y) * t
                        local pz = from.z + (to.z - from.z) * t
                        dbgSphere(
                            _G,
                            dbg,
                            {px, py, pz},
                            r,
                            {0.2, 0.9, 1, 0.08},
                            ttl,
                            depth,
                            0.08,
                            12
                        )
                    end
                    ::lua_continue104::
                    i = i + 1
                end
            end
        end
    end
    local lua_temp_34
    if zo and zo.groundRayLift ~= nil then
        lua_temp_34 = Numbers:coerce(zo.groundRayLift)
    else
        lua_temp_34 = self.groundRayLift
    end
    local lift = lua_temp_34
    local lua_temp_35
    if zo and zo.maxRayLenDown ~= nil then
        lua_temp_35 = Numbers:coerce(zo.maxRayLenDown)
    else
        lua_temp_35 = self.maxRayLenDown
    end
    local lenDown = lua_temp_35
    local lua_pearGroundClamp_39 = pearGroundClamp
    local lua_G_37 = _G
    local lua_ctx_38 = ctx
    local lua_temp_36
    if zo and zo.debugMinYSpan ~= nil then
        lua_temp_36 = Numbers:coerce(zo.debugMinYSpan)
    else
        lua_temp_36 = self.debugMinYSpan
    end
    local clampRes = lua_pearGroundClamp_39(
        lua_G_37,
        lua_ctx_38,
        dbg,
        from,
        to,
        nearR,
        farR,
        pearK,
        baseFloorPad,
        slopePadScale,
        pearSamples,
        lift,
        lenDown,
        ttl,
        depth,
        lua_temp_36,
        ignoreBodyId
    )
    if Numbers:isFinite(clampRes.minYAtCam) then
        ctx._camMinY = clampRes.minYAtCam
    end
    if Numbers:isFinite(ctx._camMinY) and to.y < ctx._camMinY then
        local pen = ctx._camMinY - to.y
        local lua_temp_40
        if zo and zo.groundSnapPen ~= nil then
            lua_temp_40 = Numbers:coerce(zo.groundSnapPen)
        else
            lua_temp_40 = self.groundSnapPen
        end
        local snapPen = lua_temp_40
        if pen > snapPen then
            to.y = ctx._camMinY
        else
            local lua_temp_41
            if zo and zo.smooth ~= nil then
                lua_temp_41 = Numbers:coerce(zo.smooth)
            else
                lua_temp_41 = self.smooth
            end
            local smooth = lua_temp_41
            local a = smooth <= 0 and 1 or 1 - math.exp(-smooth * dt)
            to.y = to.y + (ctx._camMinY - to.y) * a
        end
    end
    if slopeSlide > 0 and Numbers:isFinite(ctx._camMinY) then
        local gEnd = sampleGround(
            _G,
            ctx,
            to.x,
            to.y,
            to.z,
            lift,
            lenDown,
            ctx._useTerrainHeight,
            ctx._terrainWorld,
            ignoreBodyId
        )
        if gEnd.haveN then
            local nyClamped = clamp(_G, gEnd.ny, 0, 1)
            if nyClamped < self.slopeMinNy then
                local hx = gEnd.nx
                local hz = gEnd.nz
                local invH = invSqrt(_G, hx * hx + hz * hz)
                if invH > 0 then
                    local ux = hx * invH
                    local uz = hz * invH
                    local k = slopeSlide * clamp(
                        _G,
                        math.max(0, ctx._camMinY - to.y),
                        0,
                        1.5
                    )
                    to.x = to.x + ux * k
                    to.z = to.z + uz * k
                    if dbg then
                        dbgRay(
                            _G,
                            dbg,
                            {to.x, ctx._camMinY, to.z},
                            {ux, 0, uz},
                            0.45,
                            {1, 0.2, 0.85, 0.75},
                            ttl,
                            depth,
                            0.75,
                            true,
                            0.1
                        )
                    end
                end
            end
        end
    end
end
M = CameraCollisionSolver

return M
