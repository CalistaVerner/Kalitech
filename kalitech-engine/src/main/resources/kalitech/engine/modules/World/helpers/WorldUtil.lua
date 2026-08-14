local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local LuaArrayIsArray = luaRuntime.LuaArrayIsArray
local Error = luaRuntime.Error
local LuaConstruct = luaRuntime.LuaConstruct
local LuaTableKeys = luaRuntime.LuaTableKeys
local LuaStringTrim = luaRuntime.LuaStringTrim
local LuaNumber = luaRuntime.LuaNumber
local LuaNumberIsFinite = luaRuntime.LuaNumberIsFinite
function isObj(self, v)
    return not not v and KTypeOf(v) == "table" and not LuaArrayIsArray(v)
end
function req(self, cond, msg)
    if not cond then
        error(
            LuaConstruct(Error, msg),
            0
        )
    end
end
function deepMerge(self, dst, src)
    local lua_temp_0
    if dst and KTypeOf(dst) == "table" then
        lua_temp_0 = dst
    else
        lua_temp_0 = {}
    end
    dst = lua_temp_0
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
            ("[WORLD] engine." .. tostring(name)) .. " missing"
        ),
        0
    )
end
function str(self, v, fb)
    local s = v == nil and "" or tostring(v)
    local t = LuaStringTrim(s)
    local lua_t_2
    if t then
        lua_t_2 = t
    else
        local lua_temp_1
        if fb ~= nil then
            lua_temp_1 = tostring(fb)
        else
            lua_temp_1 = ""
        end
        lua_t_2 = lua_temp_1
    end
    return lua_t_2
end
function bool(self, v, fb)
    local lua_temp_3
    if v == nil then
        lua_temp_3 = not not fb
    else
        lua_temp_3 = not not v
    end
    return lua_temp_3
end
function numInt(self, v, fb)
    local n = LuaNumber(v)
    if not LuaNumberIsFinite(n) then
        return bit32.bor(fb, 0)
    end
    return bit32.bor(n, 0)
end
M = {
    isObj = isObj,
    req = req,
    deepMerge = deepMerge,
    subsystem = subsystem,
    str = str,
    bool = bool,
    numInt = numInt
}

return M
