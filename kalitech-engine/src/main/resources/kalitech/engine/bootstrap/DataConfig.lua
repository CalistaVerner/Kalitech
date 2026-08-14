local M = {}
local json = require("@builtin/json")
local luaRuntime = require("@builtin/lua_runtime")
local Classes = luaRuntime.class
local Tables = luaRuntime.table
local lua_require_result_0 = require("./Util.lua")
isPlainObj = lua_require_result_0.isPlainObj
local lua_require_result_1 = require("./Assets.lua")
readTextAsset = lua_require_result_1.readTextAsset
function buildDataConfigApi(self, engine, cfgSection)
    local lua_isPlainObj_result_2
    if isPlainObj(_G, cfgSection) then
        lua_isPlainObj_result_2 = cfgSection
    else
        lua_isPlainObj_result_2 = KObject:create(nil)
    end
    local cfg = lua_isPlainObj_result_2
    local cacheText = KObject:create(nil)
    local cacheJson = KObject:create(nil)
    local function list(self)
        return Tables:keys(cfg)
    end
    local function pathOf(self, name)
        local k = tostring(name or "")
        local e = cfg[k]
        if not e then
            return ""
        end
        if KTypeOf(e) == "string" then
            return e
        end
        if e and KTypeOf(e.path) == "string" then
            return e.path
        end
        return ""
    end
    local function readText(self, name)
        local p = pathOf(_G, name)
        if not p then
            return nil
        end
        if cacheText[p] ~= nil then
            return cacheText[p]
        end
        local txt = readTextAsset(_G, engine, p)
        local lua_temp_3
        if txt ~= nil then
            lua_temp_3 = tostring(txt)
        else
            lua_temp_3 = nil
        end
        cacheText[p] = lua_temp_3
        return cacheText[p]
    end
    local function readJson(self, name)
        local p = pathOf(_G, name)
        if not p then
            return nil
        end
        if cacheJson[p] ~= nil then
            return cacheJson[p]
        end
        local txt = readText(_G, name)
        if not txt then
            cacheJson[p] = nil
            return nil
        end
        do
            local function lua_catch(_)
                cacheJson[p] = nil
                return true, nil
            end
            local lua_try, lua_hasReturned, lua_returnValue = pcall(function()
                local obj = json:decode(tostring(txt))
                cacheJson[p] = obj
                return true, obj
            end)
            if not lua_try then
                lua_hasReturned, lua_returnValue = lua_catch(lua_hasReturned)
            end
            if lua_hasReturned then
                return lua_returnValue
            end
        end
    end
    local function reload(self, name)
        local p = pathOf(_G, name)
        if not p then
            return false
        end
        Tables:remove(cacheText, p)
        Tables:remove(cacheJson, p)
        return true
    end
    local function reloadAll(self)
        local ks = list(_G)
        do
            local i = 0
            while i < #ks do
                reload(_G, ks[i + 1])
                i = i + 1
            end
        end
        return true
    end
    local function get(self, name)
        local k = tostring(name or "")
        if not cfg[k] then
            return nil
        end
        return {
            name = k,
            path = pathOf(_G, k),
            text = function(self)
                return readText(_G, k)
            end,
            json = function(self)
                return readJson(_G, k)
            end,
            reload = function(self)
                return reload(_G, k)
            end
        }
    end
    local api = {
        list = list,
        get = get,
        pathOf = pathOf,
        readText = readText,
        readJson = readJson,
        reload = reload,
        reloadAll = reloadAll
    }
    local keys = Tables:keys(cfg)
    do
        local i = 0
        while i < #keys do
            local k = keys[i + 1]
            api[k] = get(_G, k)
            i = i + 1
        end
    end
    return api
end
local BootstrapDataConfigApi = Classes:create()
BootstrapDataConfigApi.name = "BootstrapDataConfigApi"
BootstrapDataConfigApi.prototype.buildDataConfigApi = buildDataConfigApi
return Classes:construct(BootstrapDataConfigApi)
