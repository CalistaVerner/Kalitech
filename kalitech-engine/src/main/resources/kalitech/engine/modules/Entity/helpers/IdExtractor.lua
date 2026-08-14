local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local Strings = luaRuntime.string
local Numbers = luaRuntime.number
function toInt32(self, v)
    if v == nil then
        return 0
    end
    if KTypeOf(v) == "number" then
        local lua_Number_isFinite_result_0
        if Numbers:isFinite(v) then
            lua_Number_isFinite_result_0 = bit32.bor(v, 0)
        else
            lua_Number_isFinite_result_0 = 0
        end
        return lua_Number_isFinite_result_0
    end
    if KTypeOf(v) == "bigint" then
        local n = Numbers:coerce(v)
        local lua_Number_isFinite_result_1
        if Numbers:isFinite(n) then
            lua_Number_isFinite_result_1 = bit32.bor(n, 0)
        else
            lua_Number_isFinite_result_1 = 0
        end
        return lua_Number_isFinite_result_1
    end
    if KTypeOf(v) == "string" then
        local s = Strings:trim(v)
        if not s then
            return 0
        end
        local n = Numbers:coerce(s)
        if Numbers:isFinite(n) then
            return bit32.bor(n, 0)
        end
        local p = Numbers:parseInt(s, 10)
        local lua_Number_isFinite_result_2
        if Numbers:isFinite(p) then
            lua_Number_isFinite_result_2 = bit32.bor(p, 0)
        else
            lua_Number_isFinite_result_2 = 0
        end
        return lua_Number_isFinite_result_2
    end
    local nativeType = type(v)
    if nativeType ~= "table" and nativeType ~= "userdata" then
        return 0
    end
    if KTypeOf(v.valueOf) == "function" then
        local vv = v:valueOf()
        if vv ~= v then
            return toInt32(_G, vv)
        end
    end
    if KTypeOf(v.intValue) == "function" then
        local n = Numbers:coerce(v:intValue())
        local lua_Number_isFinite_result_3
        if Numbers:isFinite(n) then
            lua_Number_isFinite_result_3 = bit32.bor(n, 0)
        else
            lua_Number_isFinite_result_3 = 0
        end
        return lua_Number_isFinite_result_3
    end
    if KTypeOf(v.longValue) == "function" then
        local n = Numbers:coerce(v:longValue())
        local lua_Number_isFinite_result_4
        if Numbers:isFinite(n) then
            lua_Number_isFinite_result_4 = bit32.bor(n, 0)
        else
            lua_Number_isFinite_result_4 = 0
        end
        return lua_Number_isFinite_result_4
    end
    local n = Numbers:coerce(v)
    local lua_Number_isFinite_result_5
    if Numbers:isFinite(n) then
        lua_Number_isFinite_result_5 = bit32.bor(n, 0)
    else
        lua_Number_isFinite_result_5 = 0
    end
    return lua_Number_isFinite_result_5
end
function idOf(self, h, kind)
    if h == nil then
        return 0
    end
    local direct = toInt32(_G, h)
    if direct > 0 then
        return direct
    end
    local nativeType = type(h)
    if nativeType ~= "table" and nativeType ~= "userdata" then
        return 0
    end
    if KTypeOf(h.id) == "number" then
        return bit32.bor(h.id, 0)
    end
    if KTypeOf(h.bodyId) == "number" then
        return bit32.bor(h.bodyId, 0)
    end
    if KTypeOf(h.surfaceId) == "number" then
        return bit32.bor(h.surfaceId, 0)
    end
    local props = {
        "id",
        "bodyId",
        "surfaceId",
        "handleId",
        "nativeId"
    }
    do
        local i = 0
        while i < #props do
            local p = props[i + 1]
            if h[p] ~= nil then
                local n = toInt32(_G, h[p])
                if n > 0 then
                    return n
                end
            end
            i = i + 1
        end
    end
    local bodyFns = {
        "id",
        "getId",
        "bodyId",
        "getBodyId",
        "handleId",
        "getHandleId",
        "nativeId",
        "getNativeId",
        "handle"
    }
    local surfFns = {
        "id",
        "getId",
        "surfaceId",
        "getSurfaceId",
        "handleId",
        "getHandleId",
        "nativeId",
        "getNativeId",
        "handle"
    }
    local lua_temp_6
    if kind == "body" then
        lua_temp_6 = bodyFns
    else
        lua_temp_6 = surfFns
    end
    local fnNames = lua_temp_6
    do
        local i = 0
        while i < #fnNames do
            do
                local name = fnNames[i + 1]
                local fn = h[name]
                if KTypeOf(fn) ~= "function" then
                    goto lua_continue24
                end
                local v = KFunction:call(fn, h)
                local n = toInt32(_G, v)
                if n > 0 then
                    return n
                end
            end
            ::lua_continue24::
            i = i + 1
        end
    end
    return 0
end
function surfaceId(self, handleOrId)
    return idOf(_G, handleOrId, "surface")
end
function bodyId(self, handleOrId)
    return idOf(_G, handleOrId, "body")
end
M = {idOf = idOf, surfaceId = surfaceId, bodyId = bodyId}

return M
