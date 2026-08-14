local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local Arrays = luaRuntime.array
local Numbers = luaRuntime.number
function num(self, x, def)
    if def == nil then
        def = 0
    end
    x = Numbers:coerce(x)
    local lua_Number_isFinite_result_0
    if Numbers:isFinite(x) then
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
    if Arrays:isArray(v) then
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
    if Arrays:isArray(v) then
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
