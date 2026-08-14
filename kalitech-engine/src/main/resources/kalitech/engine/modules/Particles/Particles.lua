local M = {}
local json = require("@builtin/json")
local luaRuntime = require("@builtin/lua_runtime")
local LuaNumberIsFinite = luaRuntime.LuaNumberIsFinite
local LuaArrayIsArray = luaRuntime.LuaArrayIsArray
local LuaArraySlice = luaRuntime.LuaArraySlice
local LuaTableKeys = luaRuntime.LuaTableKeys
local LuaTableMerge = luaRuntime.LuaTableMerge
local LuaTableRemove = luaRuntime.LuaTableRemove
local LuaMap = luaRuntime.LuaMap
local LuaConstruct = luaRuntime.LuaConstruct
local LuaStringTrim = luaRuntime.LuaStringTrim
local LuaArrayConcat = luaRuntime.LuaArrayConcat
local Error = luaRuntime.Error
local LuaNumber = luaRuntime.LuaNumber
local LuaArrayReduce = luaRuntime.LuaArrayReduce
local lua_require_result_0 = require("@builtin/modules/Entity/helpers/EntUtil.lua")
req = lua_require_result_0.req
function isObj(self, v)
    return not not v and KTypeOf(v) == "table"
end
function hasOwn(self, o, k)
    return not not o and KFunction:call(KObject.prototype.hasOwnProperty, o, k)
end
function safeInt(self, v, fb)
    v = bit32.bor(v, 0)
    local lua_Number_isFinite_result_1
    if LuaNumberIsFinite(v) then
        lua_Number_isFinite_result_1 = v
    else
        lua_Number_isFinite_result_1 = bit32.bor(fb, 0)
    end
    return lua_Number_isFinite_result_1
end
function getLog(self, engine)
    do
        local lua_try, lua_hasReturned, lua_returnValue = pcall(function()
            if engine and KTypeOf(engine.log) == "function" then
                local l = engine:log()
                if l and KTypeOf(l.error) == "function" then
                    return true, l
                end
            end
        end)
        if lua_try and lua_hasReturned then
            return lua_returnValue
        end
    end
    do
        local lua_try, lua_hasReturned, lua_returnValue = pcall(function()
            if _G.ENGINE and KTypeOf(ENGINE.log) == "function" then
                local l = ENGINE:log()
                if l and KTypeOf(l.error) == "function" then
                    return true, l
                end
            end
        end)
        if lua_try and lua_hasReturned then
            return lua_returnValue
        end
    end
    local c = console or KObject:create(nil)
    return {
        error = function(lua_, m, e)
            local lua_c_error_2
            if c.error then
                lua_c_error_2 = print(m, e)
            else
                lua_c_error_2 = nil
            end
            return lua_c_error_2
        end,
        warn = function(lua_, m, e)
            local lua_c_warn_3
            if c.warn then
                lua_c_warn_3 = print(m, e)
            else
                lua_c_warn_3 = nil
            end
            return lua_c_warn_3
        end,
        info = function(lua_, m, e)
            local lua_c_info_4
            if c.info then
                lua_c_info_4 = print(m, e)
            else
                lua_c_info_4 = nil
            end
            return lua_c_info_4
        end
    }
end
function deepMerge(self, base, over)
    if not isObj(_G, over) then
        return base
    end
    if not isObj(_G, base) or LuaArrayIsArray(base) then
        if LuaArrayIsArray(over) then
            return LuaArraySlice(over)
        end
        local out0 = KObject:create(nil)
        local ks0 = LuaTableKeys(over)
        do
            local i = 0
            while i < #ks0 do
                local k = ks0[i + 1]
                local v = over[k]
                local lua_temp_6
                if isObj(_G, v) and not LuaArrayIsArray(v) then
                    lua_temp_6 = deepMerge(_G, nil, v)
                else
                    local lua_Array_isArray_result_5
                    if LuaArrayIsArray(v) then
                        lua_Array_isArray_result_5 = LuaArraySlice(v)
                    else
                        lua_Array_isArray_result_5 = v
                    end
                    lua_temp_6 = lua_Array_isArray_result_5
                end
                out0[k] = lua_temp_6
                i = i + 1
            end
        end
        return out0
    end
    local out = LuaTableMerge({}, base)
    local ks = LuaTableKeys(over)
    do
        local i = 0
        while i < #ks do
            do
                local k = ks[i + 1]
                local ov = over[k]
                local bv = out[k]
                if LuaArrayIsArray(ov) then
                    out[k] = LuaArraySlice(ov)
                    goto lua_continue22
                end
                if isObj(_G, ov) then
                    out[k] = deepMerge(_G, bv, ov)
                    goto lua_continue22
                end
                out[k] = ov
            end
            ::lua_continue22::
            i = i + 1
        end
    end
    return out
end
function stripMax(self, cfg)
    if not cfg or not hasOwn(_G, cfg, "max") then
        return cfg
    end
    local out = LuaTableMerge({}, cfg)
    LuaTableRemove(out, "max")
    return out
end
function isPlainObject(self, v)
    return not not v and KTypeOf(v) == "table" and not LuaArrayIsArray(v)
end
function isSpawnOptsLike(self, v)
    if not isPlainObject(_G, v) then
        return false
    end
    return hasOwn(_G, v, "pos") or hasOwn(_G, v, "rot") or hasOwn(_G, v, "scale") or hasOwn(_G, v, "dir") or hasOwn(_G, v, "velocity") or hasOwn(_G, v, "burst") or hasOwn(_G, v, "ttlMs") or hasOwn(_G, v, "seed") or hasOwn(_G, v, "override") or hasOwn(_G, v, "deepOverride")
end
function normalizeSpawnArgs(self, overCfg, opts)
    local over = overCfg
    local o = opts
    if opts == nil and isSpawnOptsLike(_G, overCfg) then
        o = overCfg
        local lua_isPlainObject_result_8
        if isPlainObject(_G, o.override) then
            lua_isPlainObject_result_8 = o.override
        else
            local lua_isPlainObject_result_7
            if isPlainObject(_G, o.deepOverride) then
                lua_isPlainObject_result_7 = o.deepOverride
            else
                lua_isPlainObject_result_7 = nil
            end
            lua_isPlainObject_result_8 = lua_isPlainObject_result_7
        end
        over = lua_isPlainObject_result_8
    end
    if not isPlainObject(_G, over) then
        over = nil
    end
    if not isPlainObject(_G, o) then
        o = nil
    end
    return {over = over, opts = o}
end
create = setmetatable(
    {},
    {__call = function(lua_, self, engine, K)
        local loadBank, define, templates, pools, bank
        function loadBank(self, bankObj)
            if not isObj(_G, bankObj) then
                error(
                    LuaConstruct(Error, "[PARTICLES] loadBank(bankObj): object is required"),
                    0
                )
            end
            local lua_temp_11
            if bankObj.templates ~= nil then
                lua_temp_11 = bankObj.templates
            else
                lua_temp_11 = bankObj
            end
            local src = lua_temp_11
            if LuaArrayIsArray(src) then
                do
                    local i = 0
                    while i < #src do
                        local e = src[i + 1]
                        if not isObj(_G, e) then
                            error(
                                LuaConstruct(Error, "[PARTICLES] templates[] entry must be object"),
                                0
                            )
                        end
                        local name = LuaStringTrim(tostring(e.name or ""))
                        if not name then
                            error(
                                LuaConstruct(Error, "[PARTICLES] templates[] entry must have name"),
                                0
                            )
                        end
                        define(_G, name, e)
                        i = i + 1
                    end
                end
            elseif isObj(_G, src) then
                local ks = LuaTableKeys(src)
                do
                    local i = 0
                    while i < #ks do
                        local name = ks[i + 1]
                        define(_G, name, src[name])
                        i = i + 1
                    end
                end
            else
                error(
                    LuaConstruct(Error, "[PARTICLES] bank.templates must be object-map or array"),
                    0
                )
            end
            bank.loaded = true
            return true
        end
        function define(self, name, cfg)
            name = LuaStringTrim(tostring(name or ""))
            if not name then
                error(
                    LuaConstruct(Error, "[PARTICLES] define(name,cfg): name is required"),
                    0
                )
            end
            if not isObj(_G, cfg) then
                error(
                    LuaConstruct(Error, "[PARTICLES] define(name,cfg): cfg object is required"),
                    0
                )
            end
            templates[name] = KObject:freeze(LuaTableMerge({}, cfg))
            if not pools[name] then
                pools[name] = {}
            end
            return true
        end
        req(_G, engine, "[PARTICLES] engine is required")
        req(
            _G,
            KTypeOf(engine.particles) == "function",
            "[PARTICLES] engine.particles() is required"
        )
        local api = engine:particles()
        local log = getLog(_G, engine)
        local assets = engine:assets()
        local timeApi = engine:time()
        templates = KObject:create(nil)
        pools = KObject:create(nil)
        local inUse = LuaConstruct(LuaMap)
        local leaseGen = LuaConstruct(LuaMap)
        local leaseSeq = 1
        local stats = {created = 0, reused = 0, destroyed = 0, released = 0}
        bank = {
            loaded = false,
            path = "data/particles.json",
            candidates = {"data/particles.json", "particles.json", "config/particles.json"},
            lastError = "",
            lastLogAtMs = 0,
            logThrottleMs = 2000
        }
        local function setBankPath(self, path)
            bank.path = LuaStringTrim(tostring(path or "")) or bank.path
            bank.loaded = false
            bank.lastError = ""
            return bank.path
        end
        local function _logAutoloadFailOnce(self, msg, err)
            local now = timeApi:now() * 1000
            if now and now - bank.lastLogAtMs < bank.logThrottleMs then
                return
            end
            bank.lastLogAtMs = now
            log:error(msg, err)
        end
        local function tryAutoLoadBank(self)
            if bank.loaded then
                return true
            end
            local cands = LuaArrayConcat({bank.path}, bank.candidates)
            local uniq = {}
            local seen = KObject:create(nil)
            do
                local i = 0
                while i < #cands do
                    do
                        local p = LuaStringTrim(tostring(cands[i + 1] or ""))
                        if not p then
                            goto lua_continue41
                        end
                        if seen[p] then
                            goto lua_continue41
                        end
                        seen[p] = 1
                        uniq[#uniq + 1] = p
                    end
                    ::lua_continue41::
                    i = i + 1
                end
            end
            do
                local i = 0
                while i < #uniq do
                    local p = uniq[i + 1]
                    do
                        local function lua_catch(e)
                            local lua_temp_10
                            if e and e.message then
                                lua_temp_10 = e.message
                            else
                                lua_temp_10 = e
                            end
                            bank.lastError = tostring(lua_temp_10)
                            _logAutoloadFailOnce(_G, "[PARTICLES] bank autoload failed: " .. p, e)
                        end
                        local lua_try, lua_hasReturned, lua_returnValue = pcall(function()
                            local txt = assets:readText(p)
                            local obj = json:decode(txt)
                            loadBank(_G, obj)
                            bank.path = p
                            bank.loaded = true
                            bank.lastError = ""
                            return true, true
                        end)
                        if not lua_try then
                            lua_hasReturned, lua_returnValue = lua_catch(lua_hasReturned)
                        end
                        if lua_hasReturned then
                            return lua_returnValue
                        end
                    end
                    i = i + 1
                end
            end
            return false
        end
        local function ensureBankLoaded(self)
            if bank.loaded then
                return true
            end
            tryAutoLoadBank(_G)
            return bank.loaded
        end
        local function poolArr(self, name)
            local a = pools[name]
            if not a then
                a = {}
                pools[name] = a
            end
            return a
        end
        local function safeDestroy(self, h)
            if not h then
                return
            end
            do
                pcall(function()
                    api:destroy(h)
                end)
            end
            stats.destroyed = stats.destroyed + 1
        end
        local function poolMaxFor(self, name)
            local t = templates[name]
            local lua_temp_12
            if t and isObj(_G, t.pool) then
                lua_temp_12 = t.pool
            else
                lua_temp_12 = nil
            end
            local pool = lua_temp_12
            local lua_temp_13
            if pool and pool.max ~= nil then
                lua_temp_13 = safeInt(_G, pool.max, 32)
            else
                lua_temp_13 = 32
            end
            local max = lua_temp_13
            return math.max(
                0,
                math.min(
                    2048,
                    bit32.bor(max, 0)
                )
            )
        end
        local function acquire(self, name, cfg)
            local pool = poolArr(_G, name)
            while KLength(pool) > 0 do
                do
                    local h = KArrayOps.pop(pool)
                    if not h then
                        goto lua_continue68
                    end
                    inUse:set(h.id, name)
                    local lua_leaseSeq_14 = leaseSeq
                    leaseSeq = lua_leaseSeq_14 + 1
                    local gen = bit32.bor(lua_leaseSeq_14, 0)
                    leaseGen:set(h.id, gen)
                    stats.reused = stats.reused + 1
                    return {h = h, fresh = false, gen = gen}
                end
                ::lua_continue68::
            end
            local h = api:create(cfg)
            if not h then
                return nil
            end
            inUse:set(h.id, name)
            local lua_leaseSeq_15 = leaseSeq
            leaseSeq = lua_leaseSeq_15 + 1
            local gen = bit32.bor(lua_leaseSeq_15, 0)
            leaseGen:set(h.id, gen)
            stats.created = stats.created + 1
            return {h = h, fresh = true, gen = gen}
        end
        local function release(self, h)
            if not h then
                return false
            end
            local name = inUse:get(h.id)
            if not name then
                safeDestroy(_G, h)
                return false
            end
            inUse:delete(h.id)
            leaseGen:delete(h.id)
            do
                pcall(function()
                    if KTypeOf(api.stop) == "function" then
                        api:stop(h)
                    end
                    if KTypeOf(api.clear) == "function" then
                        api:clear(h)
                    end
                    if KTypeOf(api.setEnabled) == "function" then
                        api:setEnabled(h, true)
                    end
                end)
            end
            local pool = poolArr(_G, name)
            local cap = poolMaxFor(_G, name)
            if cap > 0 and KLength(pool) < cap then
                KArrayOps.push(pool, h)
            else
                safeDestroy(_G, h)
            end
            stats.released = stats.released + 1
            return true
        end
        local function ttlRelease(self, h, ttlMs, gen)
            ttlMs = bit32.bor(ttlMs, 0) or 0
            local lua_temp_16
            if ttlMs > 0 then
                lua_temp_16 = ttlMs
            else
                lua_temp_16 = 900
            end
            local ms = math.max(25, lua_temp_16)
            if KTypeOf(setTimeout) ~= "function" then
                return
            end
            setTimeout(
                _G,
                function()
                    do
                        local lua_try, lua_hasReturned, lua_returnValue = pcall(function()
                            if not inUse:has(h.id) then
                                return true
                            end
                            if leaseGen:get(h.id) ~= gen then
                                return true
                            end
                            release(_G, h)
                        end)
                        if lua_try and lua_hasReturned then
                            return lua_returnValue
                        end
                    end
                end,
                ms
            )
        end
        local function getTemplate(self, name)
            ensureBankLoaded(_G)
            name = LuaStringTrim(tostring(name or ""))
            local t = templates[name]
            if not t then
                error(
                    LuaConstruct(
                        Error,
                        ("[PARTICLES] unknown template '" .. tostring(name)) .. "'"
                    ),
                    0
                )
            end
            return t
        end
        local function spawn(self, name, overCfg, opts)
            local baseCfg = getTemplate(_G, name)
            local n = normalizeSpawnArgs(_G, overCfg, opts)
            local over = n.over
            local o = n.opts
            local hasOver = isObj(_G, over) and #LuaTableKeys(over) > 0
            local hasOpts = isObj(_G, o)
            local cfg = baseCfg
            if hasOver then
                cfg = deepMerge(_G, baseCfg, over)
            end
            if hasOpts then
                if o.pos ~= nil then
                    if cfg == baseCfg then
                        cfg = LuaTableMerge({}, cfg)
                    end
                    cfg.pos = o.pos
                end
                if o.rot ~= nil then
                    if cfg == baseCfg then
                        cfg = LuaTableMerge({}, cfg)
                    end
                    cfg.rot = o.rot
                end
                if o.scale ~= nil then
                    if cfg == baseCfg then
                        cfg = LuaTableMerge({}, cfg)
                    end
                    cfg.scale = LuaNumber(o.scale)
                end
                if o.dir ~= nil or o.velocity ~= nil then
                    local lua_isObj_result_17
                    if isObj(_G, o.velocity) then
                        lua_isObj_result_17 = o.velocity
                    else
                        lua_isObj_result_17 = nil
                    end
                    local vOver = lua_isObj_result_17
                    local lua_isObj_result_18
                    if isObj(_G, o.dir) then
                        lua_isObj_result_18 = {dir = o.dir}
                    else
                        lua_isObj_result_18 = nil
                    end
                    local vDir = lua_isObj_result_18
                    local lua_isObj_result_19
                    if isObj(_G, cfg.velocity) then
                        lua_isObj_result_19 = cfg.velocity
                    else
                        lua_isObj_result_19 = nil
                    end
                    local baseV = lua_isObj_result_19
                    local mergedV = deepMerge(
                        _G,
                        baseV or KObject:create(nil),
                        vOver or KObject:create(nil)
                    )
                    if vDir then
                        mergedV.dir = vDir.dir
                    end
                    if cfg == baseCfg then
                        cfg = LuaTableMerge({}, cfg)
                    end
                    cfg.velocity = mergedV
                end
                if o.seed ~= nil then
                    if cfg == baseCfg then
                        cfg = LuaTableMerge({}, cfg)
                    end
                    cfg.seed = bit32.bor(o.seed, 0)
                end
            end
            cfg = stripMax(_G, cfg)
            local acq = acquire(_G, name, cfg)
            if not acq or not acq.h then
                return nil
            end
            local h = acq.h
            do
                local function lua_catch(e)
                    log:error("[PARTICLES] spawn: set transform failed", e)
                end
                local lua_try, lua_hasReturned = pcall(function()
                    if cfg.pos and KTypeOf(api.setPosition) == "function" then
                        api:setPosition(h, cfg.pos)
                    end
                    if cfg.rot and KTypeOf(api.setRotation) == "function" then
                        api:setRotation(h, cfg.rot)
                    end
                    if cfg.scale ~= nil and KTypeOf(api.setScale) == "function" then
                        api:setScale(h, cfg.scale)
                    end
                end)
                if not lua_try then
                    lua_catch(lua_hasReturned)
                end
            end
            do
                local function lua_catch(e)
                    log:error("[PARTICLES] spawn: configure failed", e)
                end
                local lua_try, lua_hasReturned = pcall(function()
                    if KTypeOf(api.configure) == "function" then
                        api:configure(h, cfg)
                    end
                end)
                if not lua_try then
                    lua_catch(lua_hasReturned)
                end
            end
            do
                local function lua_catch(e)
                    log:error("[PARTICLES] spawn: emit failed", e)
                end
                local lua_try, lua_hasReturned = pcall(function()
                    if KTypeOf(api.clear) == "function" then
                        api:clear(h)
                    end
                    local lua_hasOpts_20
                    if hasOpts then
                        lua_hasOpts_20 = bit32.bor(o.burst, 0)
                    else
                        lua_hasOpts_20 = 0
                    end
                    local burst = lua_hasOpts_20
                    if burst > 0 then
                        if KTypeOf(api.emit) == "function" then
                            api:emit(h, burst)
                        elseif KTypeOf(api.emitAll) == "function" then
                            api:emitAll(h)
                        end
                    else
                        if KTypeOf(api.emitAll) == "function" then
                            api:emitAll(h)
                        end
                    end
                end)
                if not lua_try then
                    lua_catch(lua_hasReturned)
                end
            end
            local lua_hasOpts_21
            if hasOpts then
                lua_hasOpts_21 = bit32.bor(o.ttlMs, 0)
            else
                lua_hasOpts_21 = 0
            end
            local ttlMs = lua_hasOpts_21
            local lua_temp_22
            if ttlMs > 0 then
                lua_temp_22 = ttlMs
            else
                lua_temp_22 = bit32.bor(cfg.ttlMs, 0) or 900
            end
            local t0 = lua_temp_22
            ttlRelease(_G, h, t0, acq.gen)
            return h
        end
        local function flush(self, name)
            if name == nil then
                local keys = LuaTableKeys(pools)
                do
                    local i = 0
                    while i < #keys do
                        flush(_G, keys[i + 1])
                        i = i + 1
                    end
                end
                return true
            end
            name = LuaStringTrim(tostring(name or ""))
            local pool = pools[name]
            if not pool then
                return false
            end
            while KLength(pool) > 0 do
                safeDestroy(
                    _G,
                    KArrayOps.pop(pool)
                )
            end
            return true
        end
        local function info(self)
            local lua_KObject_26 = KObject
            local lua_KObject_freeze_27 = KObject.freeze
            local lua_temp_23
            if KTypeOf(api.alive) == "function" then
                lua_temp_23 = api:alive()
            else
                lua_temp_23 = -1
            end
            return lua_KObject_freeze_27(
                lua_KObject_26,
                {
                    alive = lua_temp_23,
                    templates = #LuaTableKeys(templates),
                    pooled = LuaArrayReduce(
                        LuaTableKeys(pools),
                        function(lua_, acc, k)
                            local lua_acc_25 = acc
                            local lua_pools_k_24
                            if pools[k] then
                                lua_pools_k_24 = KLength(pools[k])
                            else
                                lua_pools_k_24 = 0
                            end
                            return lua_acc_25 + lua_pools_k_24
                        end,
                        0
                    ),
                    inUse = inUse.size,
                    stats = LuaTableMerge({}, stats),
                    bank = KObject:freeze({loaded = bank.loaded, path = bank.path, lastError = bank.lastError})
                }
            )
        end
        tryAutoLoadBank(_G)
        return KObject:freeze({
            create = function(lua_, cfg) return api:create(cfg) end,
            destroy = function(lua_, h) return api:destroy(h) end,
            configure = function(lua_, h, cfg) return api:configure(h, cfg) end,
            setEnabled = function(lua_, h, on) return api:setEnabled(h, on) end,
            play = function(lua_, h) return api:play(h) end,
            stop = function(lua_, h) return api:stop(h) end,
            clear = function(lua_, h)
                local lua_temp_28
                if KTypeOf(api.clear) == "function" then
                    lua_temp_28 = api:clear(h)
                else
                    lua_temp_28 = nil
                end
                return lua_temp_28
            end,
            setPosition = function(lua_, h, v) return api:setPosition(h, v) end,
            setRotation = function(lua_, h, q) return api:setRotation(h, q) end,
            setScale = function(lua_, h, s) return api:setScale(h, s) end,
            emitAll = function(lua_, h) return api:emitAll(h) end,
            emit = function(lua_, h, n)
                local lua_temp_29
                if KTypeOf(api.emit) == "function" then
                    lua_temp_29 = api:emit(
                        h,
                        bit32.bor(n, 0)
                    )
                else
                    lua_temp_29 = api:emitAll(h)
                end
                return lua_temp_29
            end,
            alive = function() return api:alive() end,
            setBankPath = setBankPath,
            define = define,
            loadBank = loadBank,
            getTemplate = getTemplate,
            clearBank = function()
                for lua_, k in ipairs(LuaTableKeys(pools)) do
                    flush(_G, k)
                end
                for lua_, k in ipairs(LuaTableKeys(templates)) do
                    LuaTableRemove(templates, k)
                end
                bank.loaded = false
                bank.lastError = ""
                return true
            end,
            spawn = spawn,
            release = release,
            flush = flush,
            info = info
        })
    end}
)
create.META = {
    moduleId = "particles",
    version = "3.2.1",
    description = "AAA particles: templates + pooling + TTL + bank autoload with retry + fallback paths.",
    engineMin = "0.2.0"
}
M = create
M.META = create.META

return M
