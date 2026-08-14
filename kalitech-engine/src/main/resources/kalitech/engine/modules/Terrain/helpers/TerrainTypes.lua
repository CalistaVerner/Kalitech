local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local Arrays = luaRuntime.array
local Numbers = luaRuntime.number
function isObj(self, v)
    return not not v and KTypeOf(v) == "table" and not Arrays:isArray(v)
end
function num(self, v, def)
    local n = Numbers:coerce(v)
    local lua_Number_isFinite_result_0
    if Numbers:isFinite(n) then
        lua_Number_isFinite_result_0 = n
    else
        lua_Number_isFinite_result_0 = def
    end
    return lua_Number_isFinite_result_0
end
function i32(self, v, def)
    local n = Numbers:coerce(v)
    if not Numbers:isFinite(n) then
        n = 0
    end
    n = bit32.bor(n, 0)
    if n ~= 0 then
        return n
    end
    local fallback = Numbers:coerce(def)
    if not Numbers:isFinite(fallback) then
        fallback = 0
    end
    return bit32.bor(fallback, 0)
end
M = {isObj = isObj, num = num, i32 = i32}

return M
