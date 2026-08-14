local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local LuaArrayIsArray = luaRuntime.LuaArrayIsArray
local LuaNumber = luaRuntime.LuaNumber
local LuaNumberIsFinite = luaRuntime.LuaNumberIsFinite
local LuaTableKeys = luaRuntime.LuaTableKeys
local Error = luaRuntime.Error
local LuaConstruct = luaRuntime.LuaConstruct
function isObj(self, v)
    return not not v and KTypeOf(v) == "table" and not LuaArrayIsArray(v)
end
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
function bool(self, v, fb)
    local lua_temp_1
    if v == nil then
        lua_temp_1 = not not fb
    else
        lua_temp_1 = not not v
    end
    return lua_temp_1
end
function vec3(self, v, fbX, fbY, fbZ)
    if LuaArrayIsArray(v) then
        return {
            num(_G, v[1], fbX),
            num(_G, v[2], fbY),
            num(_G, v[3], fbZ)
        }
    end
    if isObj(_G, v) then
        local lua_temp_2
        if v.x ~= nil then
            lua_temp_2 = v.x
        else
            lua_temp_2 = v[0]
        end
        local x = lua_temp_2
        local lua_temp_3
        if v.y ~= nil then
            lua_temp_3 = v.y
        else
            lua_temp_3 = v[1]
        end
        local y = lua_temp_3
        local lua_temp_4
        if v.z ~= nil then
            lua_temp_4 = v.z
        else
            lua_temp_4 = v[2]
        end
        local z = lua_temp_4
        return {
            num(_G, x, fbX),
            num(_G, y, fbY),
            num(_G, z, fbZ)
        }
    end
    return {fbX, fbY, fbZ}
end
function deepMerge(self, dst, src)
    local lua_temp_5
    if dst and KTypeOf(dst) == "table" then
        lua_temp_5 = dst
    else
        lua_temp_5 = {}
    end
    dst = lua_temp_5
    if not src or KTypeOf(src) ~= "table" then
        return dst
    end
    for lua_, k in ipairs(LuaTableKeys(src)) do
        local sv = src[k]
        local dv = dst[k]
        if sv and KTypeOf(sv) == "table" and not LuaArrayIsArray(sv) then
            dst[k] = deepMerge(_G, dv, sv)
        else
            dst[k] = sv
        end
    end
    return dst
end
function req(self, cond, msg)
    if not cond then
        error(
            LuaConstruct(Error, msg),
            0
        )
    end
end
function errCtx(self, msg, e)
    local lua_temp_6
    if e and e.stack then
        lua_temp_6 = e.stack
    else
        lua_temp_6 = tostring(e)
    end
    local m = lua_temp_6
    return (tostring(msg) .. " :: ") .. tostring(m)
end
function subsystem(self, engine, name)
    local v = engine[name]
    if KTypeOf(v) == "function" then
        return KFunction:call(v, engine)
    end
    if v and KTypeOf(v) == "table" then
        return v
    end
    error(
        LuaConstruct(
            Error,
            ("[ENT] engine." .. tostring(name)) .. " missing"
        ),
        0
    )
end
M = {
    isObj = isObj,
    num = num,
    bool = bool,
    vec3 = vec3,
    deepMerge = deepMerge,
    req = req,
    errCtx = errCtx,
    subsystem = subsystem
}

return M
