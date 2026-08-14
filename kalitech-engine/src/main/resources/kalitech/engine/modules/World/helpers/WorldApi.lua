local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local Collections = luaRuntime.collection
local Arrays = luaRuntime.array
local Strings = luaRuntime.string
local Numbers = luaRuntime.number
local Tables = luaRuntime.table
local Classes = luaRuntime.class
local Error = luaRuntime.Error
local lua_require_result_0 = require("./WorldUtil.lua")
req = lua_require_result_0.req
deepMerge = lua_require_result_0.deepMerge
isObj = lua_require_result_0.isObj
subsystem = lua_require_result_0.subsystem
numInt = lua_require_result_0.numInt
str = lua_require_result_0.str
bool = lua_require_result_0.bool
local lua_require_result_1 = require("./WorldBuilder.lua")
WorldBuilder = lua_require_result_1.WorldBuilder
local lua_require_result_2 = require("./WorldSession.lua")
WorldSession = lua_require_result_2.WorldSession
WORLD_SCHEMA_VERSION = 1
function stableIdFromModule(self, modulePath)
    local m = Strings:trim(tostring(modulePath or ""))
    if not m then
        return nil
    end
    local x = KString:slashes(m)
    x = KString:beforeQuery(x)
    x = KString:stripModuleExtension(x)
    x = KString:safeModuleChars(x)
    x = KString:collapseSlashes(x)
    x = KString.lower(x)
    return "sys." .. tostring(x)
end
function ensureUniqueStableIds(self, systems)
    local seen = Collections:newSet()
    do
        local i = 0
        while i < KLength(systems) do
            do
                local s = KIndex(systems, i)
                local lua_temp_3
                if s and s.stableId ~= nil then
                    lua_temp_3 = tostring(s.stableId)
                else
                    lua_temp_3 = ""
                end
                local id = lua_temp_3
                if not id then
                    goto lua_continue6
                end
                if seen:has(id) then
                    error(
                        Classes:construct(Error, "[WORLD] duplicate stableId: " .. id),
                        0
                    )
                end
                seen:add(id)
            end
            ::lua_continue6::
            i = i + 1
        end
    end
end
function normalizeTimeDesc(self, time)
    if not isObj(_G, time) then
        return nil
    end
    local out = {}
    if time.worldTime ~= nil then
        out.worldTime = Numbers:coerce(time.worldTime)
    end
    if time.timeRate ~= nil then
        out.timeRate = Numbers:coerce(time.timeRate)
    end
    if time.paused ~= nil then
        out.paused = not not time.paused
    end
    if time.fixedStep ~= nil then
        out.fixedStep = Numbers:coerce(time.fixedStep)
    end
    if time.maxDelta ~= nil then
        out.maxDelta = Numbers:coerce(time.maxDelta)
    end
    return out
end
function normalizeMode(self, mode)
    local m = mode == nil and "game" or tostring(mode)
    local t = Strings:trim(m)
    local lua_t_4
    if t then
        lua_t_4 = t
    else
        lua_t_4 = "game"
    end
    return lua_t_4
end
WorldApi = Classes:create()
WorldApi.name = "WorldApi"
function WorldApi.prototype.lua_constructor(self, engine, K)
    self.engine = engine
    self.K = K or (_G.__kalitech or KObject:create(nil))
    req(_G, engine, "[WORLD] engine is required")
    subsystem(_G, engine, "world")
    self._defaults = {name = "world", start = true, runtime = "world", orderStep = 10}
end
function WorldApi.prototype.getWorldTime(self)
    local w = subsystem(_G, self.engine, "world")
    if not w or KTypeOf(w.getWorldTime) ~= "function" then
        return nil
    end
    local t = w:getWorldTime()
    if not t or KTypeOf(t) ~= "table" then
        return nil
    end
    local out = {}
    if t.worldTime ~= nil then
        out.worldTime = Numbers:coerce(t.worldTime)
    end
    if t.timeRate ~= nil then
        out.timeRate = Numbers:coerce(t.timeRate)
    end
    if t.paused ~= nil then
        out.paused = not not t.paused
    end
    if t.frameIndex ~= nil then
        out.frameIndex = Numbers:coerce(t.frameIndex)
    end
    if t.tickIndex ~= nil then
        out.tickIndex = Numbers:coerce(t.tickIndex)
    end
    if t.realDt ~= nil then
        out.realDt = Numbers:coerce(t.realDt)
    end
    if t.simDt ~= nil then
        out.simDt = Numbers:coerce(t.simDt)
    end
    if t.stepDt ~= nil then
        out.stepDt = Numbers:coerce(t.stepDt)
    end
    if t.interpAlpha ~= nil then
        out.interpAlpha = Numbers:coerce(t.interpAlpha)
    end
    if t.fixedStep ~= nil then
        out.fixedStep = Numbers:coerce(t.fixedStep)
    end
    if t.maxDelta ~= nil then
        out.maxDelta = Numbers:coerce(t.maxDelta)
    end
    return out
end
function WorldApi.prototype.env(self, opts)
    local lua_temp_5
    if opts and KTypeOf(opts) == "table" then
        lua_temp_5 = opts
    else
        lua_temp_5 = {}
    end
    opts = lua_temp_5
    local name = str(_G, opts.name, "main")
    local start = bool(_G, opts.start, true)
    local lua_str_8 = str
    local lua_G_7 = _G
    local lua_opts_runtime_6 = opts.runtime
    if lua_opts_runtime_6 == nil then
        lua_opts_runtime_6 = opts.profile
    end
    local runtime = lua_str_8(lua_G_7, lua_opts_runtime_6, self._defaults.runtime)
    local orderStep = numInt(_G, opts.orderStep, self._defaults.orderStep)
    local out = {
        name = name,
        start = start,
        mode = normalizeMode(_G, opts.mode),
        schemaVersion = WORLD_SCHEMA_VERSION,
        runtime = runtime,
        orderStep = orderStep,
        systems = {},
        entities = {}
    }
    local time = normalizeTimeDesc(_G, opts.time)
    if time and #Tables:keys(time) then
        out.time = time
    end
    return out
end
WorldApi.prototype["$"] = function(self, seed)
    return Classes:construct(WorldSession, self, seed or ({}))
end
function WorldApi.prototype.boot(self, desc, systems, overrides)
    local lua_temp_9
    if desc and KTypeOf(desc) == "table" then
        lua_temp_9 = desc
    else
        lua_temp_9 = {}
    end
    local d = lua_temp_9
    local lua_Array_isArray_result_10
    if Arrays:isArray(systems) then
        lua_Array_isArray_result_10 = systems
    else
        lua_Array_isArray_result_10 = {}
    end
    local sys = lua_Array_isArray_result_10
    local finalDesc = deepMerge(
        _G,
        deepMerge(_G, {}, d),
        overrides or ({})
    )
    finalDesc.systems = sys
    Tables:remove(finalDesc, "entities")
    return self:create(finalDesc)
end
function WorldApi.prototype.normalize(self, desc)
    local lua_temp_11
    if desc and KTypeOf(desc) == "table" then
        lua_temp_11 = desc
    else
        lua_temp_11 = {}
    end
    desc = lua_temp_11
    local name = str(_G, desc.name, self._defaults.name)
    local start = bool(_G, desc.start, self._defaults.start)
    local lua_str_14 = str
    local lua_G_13 = _G
    local lua_desc_runtime_12 = desc.runtime
    if lua_desc_runtime_12 == nil then
        lua_desc_runtime_12 = desc.profile
    end
    local runtime = lua_str_14(lua_G_13, lua_desc_runtime_12, self._defaults.runtime)
    local orderStep = numInt(_G, desc.orderStep, self._defaults.orderStep)
    local lua_Array_isArray_result_15
    if Arrays:isArray(desc.systems) then
        lua_Array_isArray_result_15 = desc.systems
    else
        lua_Array_isArray_result_15 = {}
    end
    local systemsIn = lua_Array_isArray_result_15
    local systems = {}
    do
        local i = 0
        while i < KLength(systemsIn) do
            do
                local it = KIndex(systemsIn, i)
                if KTypeOf(it) == "string" then
                    local module = Strings:trim(it)
                    if not module then
                        error(
                            Classes:construct(
                                Error,
                                ("[WORLD] systems[" .. tostring(i)) .. "]: empty module string"
                            ),
                            0
                        )
                    end
                    systems[#systems + 1] = self:_mkLuaSystem({
                        module = module,
                        runtime = runtime,
                        order = i * orderStep,
                        stableId = stableIdFromModule(_G, module),
                        config = {}
                    })
                    goto lua_continue38
                end
                if not isObj(_G, it) then
                    error(
                        Classes:construct(
                            Error,
                            ("[WORLD] systems[" .. tostring(i)) .. "]: must be string or object"
                        ),
                        0
                    )
                end
                if it.id == "luaSystem" and isObj(_G, it.config) then
                    local cfg = deepMerge(_G, {}, it.config)
                    local module = str(_G, cfg.module, "")
                    if not module then
                        error(
                            Classes:construct(
                                Error,
                                ("[WORLD] systems[" .. tostring(i)) .. "].config.module is required"
                            ),
                            0
                        )
                    end
                    local lua_str_18 = str
                    local lua_G_17 = _G
                    local lua_cfg_runtime_16 = cfg.runtime
                    if lua_cfg_runtime_16 == nil then
                        lua_cfg_runtime_16 = cfg.profile
                    end
                    local rt = lua_str_18(lua_G_17, lua_cfg_runtime_16, runtime)
                    cfg.module = module
                    cfg.runtime = rt
                    local order = numInt(_G, it.order, i * orderStep)
                    local lua_temp_19
                    if it.stableId ~= nil then
                        lua_temp_19 = tostring(it.stableId)
                    else
                        lua_temp_19 = stableIdFromModule(_G, module)
                    end
                    local stableId = lua_temp_19
                    systems[#systems + 1] = {id = "luaSystem", order = order, stableId = stableId, config = cfg}
                    goto lua_continue38
                end
                if it.config and isObj(_G, it.config) then
                    local cfg = deepMerge(_G, {}, it.config)
                    local module = str(_G, cfg.module, "")
                    if not module then
                        error(
                            Classes:construct(
                                Error,
                                ("[WORLD] systems[" .. tostring(i)) .. "].config.module is required"
                            ),
                            0
                        )
                    end
                    local lua_str_22 = str
                    local lua_G_21 = _G
                    local lua_cfg_runtime_20 = cfg.runtime
                    if lua_cfg_runtime_20 == nil then
                        lua_cfg_runtime_20 = cfg.profile
                    end
                    local rt = lua_str_22(lua_G_21, lua_cfg_runtime_20, runtime)
                    cfg.module = module
                    cfg.runtime = rt
                    local order = numInt(_G, it.order, i * orderStep)
                    local lua_temp_23
                    if it.stableId ~= nil then
                        lua_temp_23 = tostring(it.stableId)
                    else
                        lua_temp_23 = stableIdFromModule(_G, module)
                    end
                    local stableId = lua_temp_23
                    systems[#systems + 1] = {id = "luaSystem", order = order, stableId = stableId, config = cfg}
                    goto lua_continue38
                end
                if it.module ~= nil then
                    local module = str(_G, it.module, "")
                    if not module then
                        error(
                            Classes:construct(
                                Error,
                                ("[WORLD] systems[" .. tostring(i)) .. "].module is required"
                            ),
                            0
                        )
                    end
                    local lua_str_26 = str
                    local lua_G_25 = _G
                    local lua_it_runtime_24 = it.runtime
                    if lua_it_runtime_24 == nil then
                        lua_it_runtime_24 = it.profile
                    end
                    local rt = lua_str_26(lua_G_25, lua_it_runtime_24, runtime)
                    local order = numInt(_G, it.order, i * orderStep)
                    local lua_temp_27
                    if it.stableId ~= nil then
                        lua_temp_27 = tostring(it.stableId)
                    else
                        lua_temp_27 = stableIdFromModule(_G, module)
                    end
                    local stableId = lua_temp_27
                    local cfg = deepMerge(_G, {}, it)
                    Tables:remove(cfg, "id")
                    Tables:remove(cfg, "order")
                    Tables:remove(cfg, "stableId")
                    Tables:remove(cfg, "module")
                    Tables:remove(cfg, "runtime")
                    Tables:remove(cfg, "profile")
                    Tables:remove(cfg, "config")
                    cfg.module = module
                    cfg.runtime = rt
                    systems[#systems + 1] = {id = "luaSystem", order = order, stableId = stableId, config = cfg}
                    goto lua_continue38
                end
                error(
                    Classes:construct(
                        Error,
                        ("[WORLD] systems[" .. tostring(i)) .. "]: cannot infer luaSystem (missing module/config.module)"
                    ),
                    0
                )
            end
            ::lua_continue38::
            i = i + 1
        end
    end
    local time = normalizeTimeDesc(_G, desc.time)
    ensureUniqueStableIds(_G, systems)
    local out = {name = name, start = start, systems = systems}
    if time and #Tables:keys(time) then
        out.time = time
    end
    return out
end
function WorldApi.prototype._mkLuaSystem(self, lua_bindingPattern0)
    local config
    local stableId
    local order
    local runtime
    local module
    module = lua_bindingPattern0.module
    runtime = lua_bindingPattern0.runtime
    order = lua_bindingPattern0.order
    stableId = lua_bindingPattern0.stableId
    config = lua_bindingPattern0.config
    local cfg = deepMerge(_G, {}, config or ({}))
    cfg.module = str(_G, module, "")
    if not cfg.module then
        error(
            Classes:construct(Error, "[WORLD] luaSystem: module is required"),
            0
        )
    end
    cfg.runtime = str(_G, runtime, self._defaults.runtime)
    local lua_numInt_result_29 = numInt(_G, order, 0)
    local lua_temp_28
    if stableId ~= nil then
        lua_temp_28 = tostring(stableId)
    else
        lua_temp_28 = stableIdFromModule(_G, cfg.module)
    end
    return {id = "luaSystem", order = lua_numInt_result_29, stableId = lua_temp_28, config = cfg}
end
function WorldApi.prototype.create(self, desc)
    local w = subsystem(_G, self.engine, "world")
    req(
        _G,
        w and KTypeOf(w.create) == "function",
        "[WORLD] engine.world().create(desc) missing"
    )
    local normalized = self:normalize(desc)
    w:create(normalized)
    return normalized
end
function WorldApi.prototype.builder(self, seed)
    local b = Classes:construct(WorldBuilder, self)
    if seed ~= nil then
        b:merge(seed)
    end
    return b
end
M = {WorldApi = WorldApi}

return M
