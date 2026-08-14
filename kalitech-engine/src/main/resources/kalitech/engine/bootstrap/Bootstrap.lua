local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local Arrays = luaRuntime.array
local Strings = luaRuntime.string
local Classes = luaRuntime.class
local Error = luaRuntime.Error
local lua_require_result_0 = require("./Config.lua")
DEFAULT_CONFIG = lua_require_result_0.DEFAULT_CONFIG
local lua_require_result_1 = require("./Root.lua")
getRoot = lua_require_result_1.getRoot
ensureRootState = lua_require_result_1.ensureRootState
U = require("./Util.lua")
local lua_require_result_2 = require("./Deferred.lua")
createDeferredProxy = lua_require_result_2.createDeferredProxy
local lua_require_result_3 = require("./DataConfig.lua")
buildDataConfigApi = lua_require_result_3.buildDataConfigApi
local lua_require_result_4 = require("./Meta.lua")
normalizeMeta = lua_require_result_4.normalizeMeta
local lua_require_result_5 = require("./EngineProxy.lua")
createEngineProxy = lua_require_result_5.createEngineProxy
K = ensureRootState(
    _G,
    getRoot(_G)
)
function req(self, v, msg)
    if v == nil then
        error(
            Classes:construct(Error, msg),
            0
        )
    end
    return v
end
function str(self, v)
    return tostring(v == nil and "" or v)
end
function sOrUnknown(self, v)
    local s = Strings:trim(str(_G, v))
    local lua_s_6
    if s then
        lua_s_6 = s
    else
        lua_s_6 = "unknown"
    end
    return lua_s_6
end
function requireModule(self, moduleId)
    do
        local function lua_catch(e)
            error(
                Classes:construct(
                    Error,
                    (("[bootstrap] require failed: " .. sOrUnknown(_G, moduleId)) .. " :: ") .. sOrUnknown(_G, KTypeOf(e) == "table" and e.message or e)
                ),
                0
            )
        end
        local lua_try, lua_hasReturned, lua_returnValue = pcall(function()
            return true, require(moduleId)
        end)
        if not lua_try then
            lua_hasReturned, lua_returnValue = lua_catch(lua_hasReturned)
        end
        if lua_hasReturned then
            return lua_returnValue
        end
    end
end
function instantiateModule(self, exp, engine, meta)
    local name = sOrUnknown(_G, meta and meta.name)
    if KTypeOf(exp) ~= "function" then
        error(
            Classes:construct(Error, "[bootstrap] Module export must be a function (engine,K)=>api for: " .. name),
            0
        )
    end
    local api = exp(_G, engine, K)
    if not api or KTypeOf(api) ~= "table" then
        error(
            Classes:construct(Error, "[bootstrap] Module factory returned invalid api for: " .. name),
            0
        )
    end
    return api
end
function ensureENGINE(self)
    if not K.ENGINE then
        K.ENGINE = KObject:create(nil)
    end
    return K.ENGINE
end
function pickEngineKey(self, meta, fallbackId)
    local lua_temp_7
    if meta and meta.key then
        lua_temp_7 = Strings:trim(str(_G, meta.key))
    else
        lua_temp_7 = ""
    end
    local k = lua_temp_7
    if k ~= "" then
        return k
    end
    local lua_temp_8
    if meta and meta.moduleId then
        lua_temp_8 = Strings:trim(str(_G, meta.moduleId))
    else
        lua_temp_8 = ""
    end
    local mid = lua_temp_8
    if mid ~= "" then
        return mid
    end
    local lua_temp_9
    if meta and meta.name then
        lua_temp_9 = Strings:trim(str(_G, meta.name))
    else
        lua_temp_9 = ""
    end
    local n = lua_temp_9
    if n ~= "" then
        if (string.find(n, "@module/", nil, true) or 0) - 1 == 0 or (string.find(n, "@builtin/", nil, true) or 0) - 1 == 0 then
            local tail = table.remove(Strings:split(n, "/")) or "module"
            return string.lower(tail)
        end
        return string.lower(n)
    end
    local fb = str(_G, fallbackId)
    local tail = table.remove(Strings:split(fb, "/")) or "module"
    return KString.lower(KString:stripModuleExtension(tail))
end
function logEngineModule(self, ENGINE, key, meta, moduleId)
    do
        local lua_try, lua_hasReturned, lua_returnValue = pcall(function()
            local msg = ((((((((((("[ENGINE] module registered: " .. "key='") .. sOrUnknown(_G, key)) .. "' ") .. "name='") .. sOrUnknown(_G, meta and meta.name)) .. "' ") .. "ver='") .. sOrUnknown(_G, meta and meta.version)) .. "'") .. " from='") .. sOrUnknown(_G, moduleId)) .. "'"
            local L = ENGINE and ENGINE.log
            if L and KTypeOf(L.info) == "function" then
                L:info(msg)
                return true
            end
            if KTypeOf(print) == "function" then
                print(_G, msg)
                return true
            end
            if KTypeOf(console) ~= "nil" and console.log then
                print(msg)
            end
        end)
        if lua_try and lua_hasReturned then
            return lua_returnValue
        end
    end
end
function loadEngineModulesFromManifest(self)
    local man = require("@module/manifest")
    local list = man and man.modules
    if not Arrays:isArray(list) or not #list then
        error(
            Classes:construct(Error, "[bootstrap] Missing or invalid engine modules manifest: @module/manifest"),
            0
        )
    end
    local out = {}
    do
        local i = 0
        while i < #list do
            do
                local id = Strings:trim(str(_G, list[i + 1]))
                if not id then
                    goto lua_continue28
                end
                out[#out + 1] = id
            end
            ::lua_continue28::
            i = i + 1
        end
    end
    if not #out then
        error(
            Classes:construct(Error, "[bootstrap] Engine modules manifest is empty: @module/manifest"),
            0
        )
    end
    return out
end
KalitechBootstrap = Classes:create()
KalitechBootstrap.name = "KalitechBootstrap"
function KalitechBootstrap.prototype.lua_constructor(self, defaults)
    self.defaults = defaults
    self.config = U:deepMergePlain({}, defaults)
    K.config = self.config
    local lua_temp_10
    if self.config and self.config.dataConfig then
        lua_temp_10 = self.config.dataConfig
    else
        lua_temp_10 = KObject:create(nil)
    end
    K.dataConfig = lua_temp_10
    ensureENGINE(_G)
end
function KalitechBootstrap.createDefault(self)
    return Classes:construct(KalitechBootstrap, DEFAULT_CONFIG)
end
function KalitechBootstrap.prototype.init(self)
    _G.ENGINE = createDeferredProxy(
        _G,
        function() return K.ENGINE or nil end,
        "ENGINE"
    )
    return self
end
function KalitechBootstrap.prototype.attachEngine(self, engine)
    engine = req(_G, engine, "[bootstrap] engine is required")
    if K._engineAttached and K._engine == engine then
        return true
    end
    K._engine = engine
    K._engineAttached = true
    local engVer = U:readEngineVersion(engine)
    local ENGINE = createEngineProxy(_G, engine)
    _G.ENGINE = ENGINE
    do
        pcall(function()
            local lua_temp_11
            if self.config and self.config.dataConfig then
                lua_temp_11 = self.config.dataConfig
            else
                lua_temp_11 = KObject:create(nil)
            end
            K.dataConfig = lua_temp_11
            K.dataConfigApi = buildDataConfigApi(_G, engine, K.dataConfig)
        end)
    end
    local moduleIds = loadEngineModulesFromManifest(_G)
    do
        local i = 0
        while i < #moduleIds do
            local moduleId = moduleIds[i + 1]
            local exp = requireModule(_G, moduleId)
            local meta = normalizeMeta(
                _G,
                exp,
                moduleId,
                moduleId,
                engine
            )
            local mid = sOrUnknown(_G, meta and meta.moduleId)
            if meta and meta.engineMin and engVer and not U:semverGte(engVer, meta.engineMin) then
                error(
                    Classes:construct(
                        Error,
                        (((("[bootstrap] Engine version " .. sOrUnknown(_G, engVer)) .. " is below minimum ") .. sOrUnknown(_G, meta.engineMin)) .. " for module ") .. mid
                    ),
                    0
                )
            end
            local api = instantiateModule(_G, exp, engine, meta)
            local lua_temp_13
            if meta and meta.moduleId and Strings:trim(str(_G, meta.moduleId)) then
                lua_temp_13 = Strings:trim(str(_G, meta.moduleId))
            else
                lua_temp_13 = pickEngineKey(_G, meta, moduleId)
            end
            local key = lua_temp_13
            if ENGINE:hasModule(key) then
                error(
                    Classes:construct(
                        Error,
                        (("[ENGINE] duplicate module key '" .. tostring(key)) .. "' while registering: ") .. moduleId
                    ),
                    0
                )
            end
            ENGINE:setModule(key, api)
            K.instances = K.instances or KObject:create(nil)
            K.instancesMeta = K.instancesMeta or KObject:create(nil)
            K.moduleIds = K.moduleIds or KObject:create(nil)
            K.instances[key] = api
            K.instancesMeta[key] = meta
            local lua_temp_14
            if meta and meta.moduleId and Strings:trim(str(_G, meta.moduleId)) then
                lua_temp_14 = Strings:trim(str(_G, meta.moduleId))
            else
                lua_temp_14 = sOrUnknown(_G, meta and meta.name)
            end
            local idKey = lua_temp_14
            K.moduleIds[idKey] = moduleId
            logEngineModule(
                _G,
                ENGINE,
                key,
                meta,
                moduleId
            )
            i = i + 1
        end
    end
    do
        local function lua_catch(e)
            do
                pcall(function()
                    local msg = "[bootstrap] controllers.registrators failed: " .. sOrUnknown(_G, KTypeOf(e) == "table" and e.message or e)
                    if ENGINE.log and KTypeOf(ENGINE.log.error) == "function" then
                        ENGINE.log:error(msg)
                    end
                end)
            end
        end
        local lua_try, lua_hasReturned = pcall(function()
            local lua_temp_15
            if self.config and self.config.controllers then
                lua_temp_15 = self.config.controllers
            else
                lua_temp_15 = KObject:create(nil)
            end
            local ccfg = lua_temp_15
            local lua_Array_isArray_result_16
            if Arrays:isArray(ccfg.registrators) then
                lua_Array_isArray_result_16 = ccfg.registrators
            else
                lua_Array_isArray_result_16 = {}
            end
            local regs = lua_Array_isArray_result_16
            if ENGINE.controllers and KTypeOf(ENGINE.controllers.loadRegistrators) == "function" then
                ENGINE.controllers:loadRegistrators(regs)
            end
        end)
        if not lua_try then
            lua_catch(lua_hasReturned)
        end
    end
    local q = K._deferred
    K._deferred = {}
    do
        local i = 0
        while i < KLength(q) do
            do
                pcall(function()
                    KIndex(q, i)(q, engine)
                end)
            end
            i = i + 1
        end
    end
    return true
end
function KalitechBootstrap.prototype.whenEngine(self, fn)
    if K._engineAttached and K._engine then
        do
            pcall(function()
                fn(_G, K._engine)
            end)
        end
        return true
    end
    KArrayOps.push(K._deferred, fn)
    return false
end
function KalitechBootstrap.prototype.whenEngineOnce(self, key, fn)
    local k = str(_G, key)
    if not k then
        return self:whenEngine(fn)
    end
    if K._once[k] then
        return false
    end
    K._once[k] = true
    return self:whenEngine(fn)
end
KalitechBootstrap.prototype.safeJson = U.safeJson
M = KalitechBootstrap
M.createDefault = KalitechBootstrap.createDefault

return M
