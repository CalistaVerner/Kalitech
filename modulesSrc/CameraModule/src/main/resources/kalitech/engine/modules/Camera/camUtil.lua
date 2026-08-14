local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local LuaNumber = luaRuntime.LuaNumber
local LuaNumberIsFinite = luaRuntime.LuaNumberIsFinite
function num(self, v, fb)
    v = LuaNumber(v)
    local lua_Number_isFinite_result_0
    if LuaNumberIsFinite(v) then
        lua_Number_isFinite_result_0 = v
    else
        lua_Number_isFinite_result_0 = fb
    end
    return lua_Number_isFinite_result_0
end
function vget(self, v, k, fb)
    local m = v and v[k]
    local lua_temp_1
    if KTypeOf(m) == "function" then
        lua_temp_1 = num(
            _G,
            KFunction:call(m, v),
            fb
        )
    else
        lua_temp_1 = num(_G, m, fb)
    end
    local n = lua_temp_1
    local lua_Number_isFinite_result_2
    if LuaNumberIsFinite(n) then
        lua_Number_isFinite_result_2 = n
    else
        lua_Number_isFinite_result_2 = fb
    end
    return lua_Number_isFinite_result_2
end
function vx(self, v, fb)
    if fb == nil then
        fb = 0
    end
    return vget(_G, v, "x", fb)
end
function vy(self, v, fb)
    if fb == nil then
        fb = 0
    end
    return vget(_G, v, "y", fb)
end
function vz(self, v, fb)
    if fb == nil then
        fb = 0
    end
    return vget(_G, v, "z", fb)
end
function clamp(self, v, lo, hi)
    local lua_temp_4
    if v < lo then
        lua_temp_4 = lo
    else
        local lua_temp_3
        if v > hi then
            lua_temp_3 = hi
        else
            lua_temp_3 = v
        end
        lua_temp_4 = lua_temp_3
    end
    return lua_temp_4
end
function expSmooth(self, cur, target, smooth, dt)
    local lua_temp_5
    if smooth > 0 then
        lua_temp_5 = smooth
    else
        lua_temp_5 = 0
    end
    local s = lua_temp_5
    if s == 0 then
        return target
    end
    local a = 1 - math.exp(LuaNumber(-s) * dt)
    return cur + (target - cur) * a
end
M = {
    num = num,
    vx = vx,
    vy = vy,
    vz = vz,
    clamp = clamp,
    expSmooth = expSmooth
}

return M
