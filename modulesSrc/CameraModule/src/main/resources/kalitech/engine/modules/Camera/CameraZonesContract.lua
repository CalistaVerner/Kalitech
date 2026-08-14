local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local Arrays = luaRuntime.array
local Strings = luaRuntime.string
local Numbers = luaRuntime.number
local Classes = luaRuntime.class
local Error = luaRuntime.Error
function fail(self, msg)
    error(
        Classes:construct(Error, msg),
        0
    )
end
function isObj(self, x)
    return x and KTypeOf(x) == "table"
end
function isNum(self, x)
    return KTypeOf(x) == "number" and Numbers:isFinite(x)
end
function isInt(self, x)
    return isNum(_G, x) and bit32.bor(x, 0) == x
end
function isBool(self, x)
    return KTypeOf(x) == "boolean"
end
function isStr(self, x)
    return KTypeOf(x) == "string" and not not Strings:trim(x)
end
function req(self, v, msg)
    if v == nil then
        fail(_G, msg)
    end
    return v
end
function asNum(self, v, name)
    if not isNum(_G, v) then
        fail(
            _G,
            ("[camera][zones] " .. tostring(name)) .. " must be number"
        )
    end
    return v
end
function asInt(self, v, name)
    if not isInt(_G, v) then
        fail(
            _G,
            ("[camera][zones] " .. tostring(name)) .. " must be int"
        )
    end
    return bit32.bor(v, 0)
end
function asBool(self, v, name)
    if not isBool(_G, v) then
        fail(
            _G,
            ("[camera][zones] " .. tostring(name)) .. " must be boolean"
        )
    end
    return not not v
end
function asStr(self, v, name)
    if not isStr(_G, v) then
        fail(
            _G,
            ("[camera][zones] " .. tostring(name)) .. " must be non-empty string"
        )
    end
    return KString.trim(v)
end
function asVec3(self, v, name)
    if Arrays:isArray(v) then
        if #v ~= 3 then
            fail(
                _G,
                ("[camera][zones] " .. tostring(name)) .. " must have 3 elements"
            )
        end
        local x = asNum(
            _G,
            v[1],
            tostring(name) .. "[0]"
        )
        local y = asNum(
            _G,
            v[2],
            tostring(name) .. "[1]"
        )
        local z = asNum(
            _G,
            v[3],
            tostring(name) .. "[2]"
        )
        return {x = x, y = y, z = z}
    end
    if isObj(_G, v) then
        return {
            x = asNum(
                _G,
                v.x,
                tostring(name) .. ".x"
            ),
            y = asNum(
                _G,
                v.y,
                tostring(name) .. ".y"
            ),
            z = asNum(
                _G,
                v.z,
                tostring(name) .. ".z"
            )
        }
    end
    fail(
        _G,
        ("[camera][zones] " .. tostring(name)) .. " must be vec3 (array[3] or {x,y,z})"
    )
end
function validateAabb(self, aabb, zoneId)
    req(
        _G,
        aabb,
        ("[camera][zones] zone '" .. tostring(zoneId)) .. "' shape.aabb is required"
    )
    local min = asVec3(
        _G,
        req(
            _G,
            aabb.min,
            ("[camera][zones] zone '" .. tostring(zoneId)) .. "' aabb.min required"
        ),
        "aabb.min"
    )
    local max = asVec3(
        _G,
        req(
            _G,
            aabb.max,
            ("[camera][zones] zone '" .. tostring(zoneId)) .. "' aabb.max required"
        ),
        "aabb.max"
    )
    if not (max.x > min.x and max.y > min.y and max.z > min.z) then
        fail(
            _G,
            ("[camera][zones] zone '" .. tostring(zoneId)) .. "' aabb must satisfy max>min on all axes"
        )
    end
    return {min = min, max = max}
end
function validateOverrides(self, over, zoneId)
    if not isObj(_G, over) then
        fail(
            _G,
            ("[camera][zones] zone '" .. tostring(zoneId)) .. "' overrides must be an object"
        )
    end
    local allowed = {
        pivotOffset = 1,
        verticalLift = 1,
        minPitch = 1,
        maxPitch = 1,
        zoomMin = 1,
        zoomMax = 1,
        collisionEnabled = 1,
        camRadius = 1,
        surfacePadding = 1,
        floorPadding = 1,
        shoulderX = 1
    }
    for k in pairs(over) do
        if not allowed[k] then
            fail(
                _G,
                (("[camera][zones] zone '" .. tostring(zoneId)) .. "' overrides has unknown key: ") .. k
            )
        end
    end
    local out = KObject:create(nil)
    if over.pivotOffset ~= nil then
        out.pivotOffset = asVec3(_G, over.pivotOffset, "overrides.pivotOffset")
    end
    if over.verticalLift ~= nil then
        out.verticalLift = asNum(_G, over.verticalLift, "overrides.verticalLift")
    end
    if over.minPitch ~= nil then
        out.minPitch = asNum(_G, over.minPitch, "overrides.minPitch")
    end
    if over.maxPitch ~= nil then
        out.maxPitch = asNum(_G, over.maxPitch, "overrides.maxPitch")
    end
    if over.zoomMin ~= nil then
        out.zoomMin = asNum(_G, over.zoomMin, "overrides.zoomMin")
    end
    if over.zoomMax ~= nil then
        out.zoomMax = asNum(_G, over.zoomMax, "overrides.zoomMax")
    end
    if over.collisionEnabled ~= nil then
        out.collisionEnabled = asBool(_G, over.collisionEnabled, "overrides.collisionEnabled")
    end
    if over.camRadius ~= nil then
        out.camRadius = asNum(_G, over.camRadius, "overrides.camRadius")
    end
    if over.surfacePadding ~= nil then
        out.surfacePadding = asNum(_G, over.surfacePadding, "overrides.surfacePadding")
    end
    if over.floorPadding ~= nil then
        out.floorPadding = asNum(_G, over.floorPadding, "overrides.floorPadding")
    end
    if over.shoulderX ~= nil then
        out.shoulderX = asNum(_G, over.shoulderX, "overrides.shoulderX")
    end
    if out.zoomMin ~= nil and out.zoomMax ~= nil and not (out.zoomMax >= out.zoomMin) then
        fail(
            _G,
            ("[camera][zones] zone '" .. tostring(zoneId)) .. "' overrides zoomMax must be >= zoomMin"
        )
    end
    if out.minPitch ~= nil and out.maxPitch ~= nil and not (out.maxPitch >= out.minPitch) then
        fail(
            _G,
            ("[camera][zones] zone '" .. tostring(zoneId)) .. "' overrides maxPitch must be >= minPitch"
        )
    end
    return out
end
function validateZone(self, z, idx)
    if not isObj(_G, z) then
        fail(
            _G,
            ("[camera][zones] zones[" .. tostring(idx)) .. "] must be an object"
        )
    end
    local id = asStr(
        _G,
        z.id,
        ("zones[" .. tostring(idx)) .. "].id"
    )
    local priority = asInt(
        _G,
        z.priority,
        ("zones[" .. tostring(idx)) .. "].priority"
    )
    local lua_temp_0
    if z.blend ~= nil then
        lua_temp_0 = asNum(
            _G,
            z.blend,
            ("zones[" .. tostring(idx)) .. "].blend"
        )
    else
        lua_temp_0 = 0
    end
    local blend = lua_temp_0
    if blend < 0 then
        fail(
            _G,
            ("[camera][zones] zone '" .. tostring(id)) .. "' blend must be >= 0"
        )
    end
    local shape = req(
        _G,
        z.shape,
        ("[camera][zones] zone '" .. tostring(id)) .. "' shape is required (object)"
    )
    if not isObj(_G, shape) then
        fail(
            _G,
            ("[camera][zones] zone '" .. tostring(id)) .. "' shape must be object"
        )
    end
    local allowedShape = {aabb = 1}
    for k in pairs(shape) do
        if not allowedShape[k] then
            fail(
                _G,
                (("[camera][zones] zone '" .. tostring(id)) .. "' shape has unknown key: ") .. k
            )
        end
    end
    local aabb = validateAabb(_G, shape.aabb, id)
    local overrides = req(
        _G,
        z.overrides,
        ("[camera][zones] zone '" .. tostring(id)) .. "' overrides is required"
    )
    local o = validateOverrides(_G, overrides, id)
    return {
        id = id,
        priority = priority,
        blend = blend,
        shape = {aabb = aabb},
        overrides = o
    }
end
function validateZonesConfig(self, cfg)
    if not isObj(_G, cfg) then
        fail(_G, "[camera][zones] cfg must be object")
    end
    if not isBool(_G, cfg.enabled) then
        fail(_G, "[camera][zones] cfg.enabled must be boolean")
    end
    if not cfg.enabled then
        return {enabled = false, zones = {}}
    end
    local zones = req(_G, cfg.zones, "[camera][zones] cfg.zones is required when enabled=true")
    if not Arrays:isArray(zones) or #zones == 0 then
        fail(_G, "[camera][zones] cfg.zones must be a non-empty array")
    end
    local out = {}
    local seen = KObject:create(nil)
    do
        local i = 0
        while i < KLength(zones) do
            local z = validateZone(_G, KIndex(zones, i), i)
            if seen[z.id] then
                fail(
                    _G,
                    "[camera][zones] duplicate zone id: " .. tostring(z.id)
                )
            end
            seen[z.id] = true
            out[#out + 1] = z
            i = i + 1
        end
    end
    return {enabled = true, zones = out}
end
M = {validateZonesConfig = validateZonesConfig}

return M
