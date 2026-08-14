local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local LuaNumber = luaRuntime.LuaNumber
local LuaNumberIsFinite = luaRuntime.LuaNumberIsFinite
local LuaTableKeys = luaRuntime.LuaTableKeys
function num(self, v, fb)
    if fb == nil then
        fb = 0
    end
    v = LuaNumber(v)
    local lua_Number_isFinite_result_0
    if LuaNumberIsFinite(v) then
        lua_Number_isFinite_result_0 = v
    else
        lua_Number_isFinite_result_0 = fb
    end
    return lua_Number_isFinite_result_0
end
function clamp(self, v, a, b)
    local lua_temp_2
    if v < a then
        lua_temp_2 = a
    else
        local lua_temp_1
        if v > b then
            lua_temp_1 = b
        else
            lua_temp_1 = v
        end
        lua_temp_2 = lua_temp_1
    end
    return lua_temp_2
end
function vget(self, v, key, fb)
    if fb == nil then
        fb = 0
    end
    local m = v and v[key]
    local lua_temp_3
    if KTypeOf(m) == "function" then
        lua_temp_3 = num(
            _G,
            KFunction:call(m, v),
            fb
        )
    else
        lua_temp_3 = num(_G, m, fb)
    end
    local n = lua_temp_3
    local lua_Number_isFinite_result_4
    if LuaNumberIsFinite(n) then
        lua_Number_isFinite_result_4 = n
    else
        lua_Number_isFinite_result_4 = fb
    end
    return lua_Number_isFinite_result_4
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
function isPlainObj(self, x)
    if not x or KTypeOf(x) ~= "table" then
        return false
    end
    local p = KObject:getPrototypeOf(x)
    return p == KObject.prototype or p == nil
end
function deepMerge(self, dst, src)
    if not isPlainObj(_G, src) then
        local lua_isPlainObj_result_5
        if isPlainObj(_G, dst) then
            lua_isPlainObj_result_5 = dst
        else
            lua_isPlainObj_result_5 = KObject:create(nil)
        end
        return lua_isPlainObj_result_5
    end
    local lua_isPlainObj_result_6
    if isPlainObj(_G, dst) then
        lua_isPlainObj_result_6 = dst
    else
        lua_isPlainObj_result_6 = KObject:create(nil)
    end
    local out = lua_isPlainObj_result_6
    local keys = LuaTableKeys(src)
    do
        local i = 0
        while i < #keys do
            local k = keys[i + 1]
            local sv = src[k]
            local dv = out[k]
            if isPlainObj(_G, sv) and isPlainObj(_G, dv) then
                out[k] = deepMerge(_G, dv, sv)
            elseif isPlainObj(_G, sv) then
                out[k] = deepMerge(
                    _G,
                    KObject:create(nil),
                    sv
                )
            else
                out[k] = sv
            end
            i = i + 1
        end
    end
    return out
end
M = {
    num = num,
    clamp = clamp,
    vx = vx,
    vy = vy,
    vz = vz,
    isPlainObj = isPlainObj,
    deepMerge = deepMerge
}

return M
