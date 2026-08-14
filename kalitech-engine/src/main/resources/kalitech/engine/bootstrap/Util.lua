local M = {}
local json = require("@builtin/json")
local luaRuntime = require("@builtin/lua_runtime")
local LuaArrayIsArray = luaRuntime.LuaArrayIsArray
local LuaTableKeys = luaRuntime.LuaTableKeys
local LuaStringTrim = luaRuntime.LuaStringTrim
function safeJson(self, v)
    do
        local function lua_catch(_)
            return true, tostring(v)
        end
        local lua_try, lua_hasReturned, lua_returnValue = pcall(function()
            return true, json:encode(v)
        end)
        if not lua_try then
            lua_hasReturned, lua_returnValue = lua_catch(lua_hasReturned)
        end
        if lua_hasReturned then
            return lua_returnValue
        end
    end
end
function deepMergePlain(self, dst, src)
    if not src or KTypeOf(src) ~= "table" then
        return dst
    end
    if not dst or KTypeOf(dst) ~= "table" then
        dst = {}
    end
    for lua_, k in ipairs(LuaTableKeys(src)) do
        local sv = src[k]
        local dv = dst[k]
        if sv and KTypeOf(sv) == "table" and not LuaArrayIsArray(sv) then
            dst[k] = deepMergePlain(_G, dv, sv)
        else
            dst[k] = sv
        end
    end
    return dst
end
function parseSemver(self, v)
    if not v or KTypeOf(v) ~= "string" then
        return nil
    end
    local m = KString:parseSemver(LuaStringTrim(v))
    if not m then
        return nil
    end
    return m
end
function semverGte(self, a, b)
    local A = parseSemver(
        _G,
        tostring(a or "")
    )
    local B = parseSemver(
        _G,
        tostring(b or "")
    )
    if not A or not B then
        return true
    end
    if A[0] ~= B[0] then
        return A[0] > B[0]
    end
    if A[1] ~= B[1] then
        return A[1] > B[1]
    end
    return A[2] >= B[2]
end
function readEngineVersion(self, engine)
    do
        local lua_try, lua_hasReturned, lua_returnValue = pcall(function()
            if not engine then
                return true, nil
            end
            if KTypeOf(engine.version) == "function" then
                return true, tostring(engine:version())
            end
            if KTypeOf(engine.version) == "string" then
                return true, engine.version
            end
            if engine.info and KTypeOf(engine.info) == "function" then
                local info = engine:info()
                if info and info.version then
                    return true, tostring(info.version)
                end
            end
        end)
        if lua_try and lua_hasReturned then
            return lua_returnValue
        end
    end
    return nil
end
function isPlainObj(self, x)
    if not x or KTypeOf(x) ~= "table" then
        return false
    end
    local p = KObject:getPrototypeOf(x)
    return p == KObject.prototype or p == nil
end
function isObj(self, x)
    return x and KTypeOf(x) == "table"
end
function readJsonSafe(self, text)
    do
        local function lua_catch(_)
            return true, nil
        end
        local lua_try, lua_hasReturned, lua_returnValue = pcall(function()
            return true, json:decode(tostring(text == nil and "" or text))
        end)
        if not lua_try then
            lua_hasReturned, lua_returnValue = lua_catch(lua_hasReturned)
        end
        if lua_hasReturned then
            return lua_returnValue
        end
    end
end
function dirOf(self, p)
    p = tostring(p or "")
    local a = KString:lastIndexOf(p, "/")
    local b = KString:lastIndexOf(p, "\\")
    local i = math.max(a, b)
    local lua_temp_0
    if i >= 0 then
        lua_temp_0 = KArrayOps.slice(p, 0, i)
    else
        lua_temp_0 = ""
    end
    return lua_temp_0
end
M = {
    safeJson = safeJson,
    deepMergePlain = deepMergePlain,
    parseSemver = parseSemver,
    semverGte = semverGte,
    readEngineVersion = readEngineVersion,
    isPlainObj = isPlainObj,
    isObj = isObj,
    readJsonSafe = readJsonSafe,
    dirOf = dirOf
}

return M
