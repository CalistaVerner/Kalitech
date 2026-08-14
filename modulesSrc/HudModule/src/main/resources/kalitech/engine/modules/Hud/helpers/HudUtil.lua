local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local LuaNumber = luaRuntime.LuaNumber
local LuaNumberIsFinite = luaRuntime.LuaNumberIsFinite
function isObj(self, o)
    return not not o and KTypeOf(o) == "table" and (KObject:getPrototypeOf(o) == KObject.prototype or KObject:getPrototypeOf(o) == nil)
end
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
function bool(self, v, fb)
    if fb == nil then
        fb = true
    end
    local lua_temp_1
    if KTypeOf(v) == "boolean" then
        lua_temp_1 = v
    else
        lua_temp_1 = fb
    end
    return lua_temp_1
end
function idOf(self, h)
    local lua_temp_3
    if h and KTypeOf(h.id) == "function" then
        lua_temp_3 = h:id()
    else
        local lua_temp_2
        if h and h.id ~= nil then
            lua_temp_2 = h.id
        else
            lua_temp_2 = 0
        end
        lua_temp_3 = lua_temp_2
    end
    return lua_temp_3
end
function round(self, v)
    return bit32.bor(v + 0.5, 0)
end
M = {
    isObj = isObj,
    num = num,
    bool = bool,
    idOf = idOf,
    round = round
}

return M
