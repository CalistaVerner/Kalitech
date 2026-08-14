local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local LuaClass = luaRuntime.LuaClass
local LuaNumber = luaRuntime.LuaNumber
local LuaNumberToFixed = luaRuntime.LuaNumberToFixed
local LuaNumberIsFinite = luaRuntime.LuaNumberIsFinite
SkyMath = LuaClass()
SkyMath.name = "SkyMath"
function SkyMath.prototype.lua_constructor(self)
end
function SkyMath.clamp(self, v, a, b)
    return math.max(
        a,
        math.min(b, v)
    )
end
function SkyMath.wrap(self, v, a, b)
    local span = b - a
    if span <= 0 then
        return a
    end
    local x = (v - a) % span
    if x < 0 then
        x = x + span
    end
    return a + x
end
function SkyMath.lerp(self, a, b, t)
    return a + (b - a) * t
end
function SkyMath.smoothstep(self, edge0, edge1, x)
    local t = SkyMath:clamp((x - edge0) / (edge1 - edge0), 0, 1)
    return t * t * (3 - 2 * t)
end
function SkyMath.degToRad(self, deg)
    return deg * (math.pi / 180)
end
function SkyMath.dirFromAltAz(self, alt, az)
    local ca = math.cos(alt)
    local x = math.cos(az) * ca
    local y = math.sin(alt)
    local z = math.sin(az) * ca
    local len = math.sqrt(x * x + y * y + z * z) or 1
    return {x = x / len, y = y / len, z = z / len}
end
function SkyMath.rgbKey(self, r, g, b)
    return (((LuaNumberToFixed(
        LuaNumber(r),
        4
    ) .. "|") .. LuaNumberToFixed(
        LuaNumber(g),
        4
    )) .. "|") .. LuaNumberToFixed(
        LuaNumber(b),
        4
    )
end
function SkyMath.kelvinToRgb01(self, kelvin)
    local k = LuaNumber(kelvin)
    if not LuaNumberIsFinite(k) then
        k = 6500
    end
    k = SkyMath:clamp(k, 1000, 40000)
    local t = k / 100
    local r
    local g
    local b
    if t <= 66 then
        r = 255
        g = 99.4708025861 * math.log(t) - 161.1195681661
        b = t <= 19 and 0 or 138.5177312231 * math.log(t - 10) - 305.0447927307
    else
        r = 329.698727446 * (t - 60) ^ (-0.1332047592)
        g = 288.1221695283 * (t - 60) ^ (-0.0755148492)
        b = 255
    end
    r = SkyMath:clamp(r, 0, 255) / 255
    g = SkyMath:clamp(g, 0, 255) / 255
    b = SkyMath:clamp(b, 0, 255) / 255
    return {r = r, g = g, b = b}
end
M = SkyMath

return M
