local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local LuaNumber = luaRuntime.LuaNumber
local LuaNumberIsFinite = luaRuntime.LuaNumberIsFinite
local LuaArrayIsArray = luaRuntime.LuaArrayIsArray
function num(self, v, fb)
    local n = LuaNumber(v)
    local lua_Number_isFinite_result_0
    if LuaNumberIsFinite(n) then
        lua_Number_isFinite_result_0 = n
    else
        lua_Number_isFinite_result_0 = fb or 0
    end
    return lua_Number_isFinite_result_0
end
function isObj(self, v)
    return v ~= nil and KTypeOf(v) == "table" and not LuaArrayIsArray(v)
end
function normalizePos(self, p)
    if LuaArrayIsArray(p) then
        return {
            num(_G, p[1], 0),
            num(_G, p[2], 0),
            num(_G, p[3], 0)
        }
    end
    if isObj(_G, p) then
        local lua_temp_1
        if p.x ~= nil then
            lua_temp_1 = p.x
        else
            lua_temp_1 = p[0]
        end
        local x = lua_temp_1
        local lua_temp_2
        if p.y ~= nil then
            lua_temp_2 = p.y
        else
            lua_temp_2 = p[1]
        end
        local y = lua_temp_2
        local lua_temp_3
        if p.z ~= nil then
            lua_temp_3 = p.z
        else
            lua_temp_3 = p[2]
        end
        local z = lua_temp_3
        return {
            num(_G, x, 0),
            num(_G, y, 0),
            num(_G, z, 0)
        }
    end
    return nil
end
M = KObject:freeze({num = num, isObj = isObj, normalizePos = normalizePos})

return M
