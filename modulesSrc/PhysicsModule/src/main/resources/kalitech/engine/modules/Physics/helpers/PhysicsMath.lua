local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local LuaNumber = luaRuntime.LuaNumber
local LuaNumberIsFinite = luaRuntime.LuaNumberIsFinite
local LuaArrayIsArray = luaRuntime.LuaArrayIsArray
function num(self, x, def)
    if def == nil then
        def = 0
    end
    x = LuaNumber(x)
    local lua_Number_isFinite_result_0
    if LuaNumberIsFinite(x) then
        lua_Number_isFinite_result_0 = x
    else
        lua_Number_isFinite_result_0 = def
    end
    return lua_Number_isFinite_result_0
end
function isObj(self, x)
    return x and KTypeOf(x) == "table"
end
function vec3Obj(self, v, dx, dy, dz)
    if LuaArrayIsArray(v) then
        return {
            x = num(_G, v[1], dx),
            y = num(_G, v[2], dy),
            z = num(_G, v[3], dz)
        }
    end
    if isObj(_G, v) then
        return {
            x = num(_G, v.x, dx),
            y = num(_G, v.y, dy),
            z = num(_G, v.z, dz)
        }
    end
    return {x = dx, y = dy, z = dz}
end
function vec3Arr(self, v, dx, dy, dz)
    if LuaArrayIsArray(v) then
        return {
            num(_G, v[1], dx),
            num(_G, v[2], dy),
            num(_G, v[3], dz)
        }
    end
    if isObj(_G, v) then
        return {
            num(_G, v.x, dx),
            num(_G, v.y, dy),
            num(_G, v.z, dz)
        }
    end
    return {dx, dy, dz}
end
function warn(self, s)
    if ENGINE and ENGINE.log and KTypeOf(ENGINE.log.warn) == "function" then
        ENGINE.log:warn(tostring(s))
    end
end
M = KObject:freeze({num = num, vec3Obj = vec3Obj, vec3Arr = vec3Arr, warn = warn})

return M
