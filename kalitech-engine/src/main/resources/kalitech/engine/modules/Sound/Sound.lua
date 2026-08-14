local M = {}
local json = require("@builtin/json")
local luaRuntime = require("@builtin/lua_runtime")
local LuaNumber = luaRuntime.LuaNumber
local LuaNumberIsFinite = luaRuntime.LuaNumberIsFinite
local LuaArrayIsArray = luaRuntime.LuaArrayIsArray
local LuaClass = luaRuntime.LuaClass
local LuaTableMerge = luaRuntime.LuaTableMerge
local Error = luaRuntime.Error
local LuaConstruct = luaRuntime.LuaConstruct
function s(self, v)
    return tostring(v == nil and "" or v)
end
function isObj(self, v)
    return v ~= nil and KTypeOf(v) == "table"
end
function isFn(self, v)
    return KTypeOf(v) == "function"
end
function toBoolOpt(self, v)
    local lua_temp_0
    if v == nil then
        lua_temp_0 = nil
    else
        lua_temp_0 = not not v
    end
    return lua_temp_0
end
function toNumOpt(self, v, defVal)
    if v == nil then
        return defVal
    end
    local n = LuaNumber(v)
    local lua_Number_isFinite_result_1
    if LuaNumberIsFinite(n) then
        lua_Number_isFinite_result_1 = n
    else
        lua_Number_isFinite_result_1 = defVal
    end
    return lua_Number_isFinite_result_1
end
function getLog(self, engine)
    do
        local lua_try, lua_hasReturned, lua_returnValue = pcall(function()
            if engine and isFn(_G, engine.log) then
                local l = engine:log()
                if l and isFn(_G, l.error) then
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
            if _G.ENGINE and isFn(_G, _G.ENGINE.log) then
                local l = _G.ENGINE:log()
                if l and isFn(_G, l.error) then
                    return true, l
                end
            end
        end)
        if lua_try and lua_hasReturned then
            return lua_returnValue
        end
    end
    local lua_temp_2
    if KTypeOf(console) ~= "nil" then
        lua_temp_2 = console
    else
        lua_temp_2 = KObject:create(nil)
    end
    local c = lua_temp_2
    return {
        error = function(lua_, m, e)
            local lua_c_error_3
            if c.error then
                lua_c_error_3 = c:error(m, e)
            else
                lua_c_error_3 = nil
            end
            return lua_c_error_3
        end,
        warn = function(lua_, m, e)
            local lua_c_warn_4
            if c.warn then
                lua_c_warn_4 = c:warn(m, e)
            else
                lua_c_warn_4 = nil
            end
            return lua_c_warn_4
        end,
        info = function(lua_, m, e)
            local lua_c_info_5
            if c.info then
                lua_c_info_5 = c:info(m, e)
            else
                lua_c_info_5 = nil
            end
            return lua_c_info_5
        end
    }
end
function v3(self, v, a, b)
    if LuaArrayIsArray(v) then
        return {
            LuaNumber(v[1]) or 0,
            LuaNumber(v[2]) or 0,
            LuaNumber(v[3]) or 0
        }
    end
    if isObj(_G, v) then
        return {
            LuaNumber(v.x) or 0,
            LuaNumber(v.y) or 0,
            LuaNumber(v.z) or 0
        }
    end
    return {
        LuaNumber(v) or 0,
        LuaNumber(a) or 0,
        LuaNumber(b) or 0
    }
end
function safeExec(self, log, label, fn)
    do
        local function lua_catch(e)
            log:error(
                ("[SND] " .. tostring(label)) .. " failed",
                e
            )
            error(e, 0)
        end
        local lua_try, lua_hasReturned, lua_returnValue = pcall(function()
            return true, fn(_G)
        end)
        if not lua_try then
            lua_hasReturned, lua_returnValue = lua_catch(lua_hasReturned)
        end
        if lua_hasReturned then
            return lua_returnValue
        end
    end
end
function hashString32(self, str)
    local h = bit32.rshift(2166136261, 0)
    do
        local i = 0
        while i < KLength(str) do
            h = bit32.bxor(
                h,
                bit32.band(
                    (string.byte(str, i + 1) or 0),
                    255
                )
            )
            h = bit32.rshift(
                KMath:imul(h, 16777619),
                0
            )
            i = i + 1
        end
    end
    return bit32.rshift(h, 0)
end
function xorshift32(self, state)
    local x = bit32.rshift(state, 0)
    x = bit32.bxor(
        x,
        bit32.rshift(
            bit32.lshift(x, 13),
            0
        )
    )
    x = bit32.bxor(
        x,
        bit32.rshift(
            bit32.rshift(x, 17),
            0
        )
    )
    x = bit32.bxor(
        x,
        bit32.rshift(
            bit32.lshift(x, 5),
            0
        )
    )
    return bit32.rshift(x, 0)
end
function normalizeSrcList(self, cfg)
    if not cfg then
        return {}
    end
    if LuaArrayIsArray(cfg.src) then
        return KArrayOps.filter(KArrayOps.map(cfg.src, s), function(lua_, v) return KLength(v) > 0 end)
    end
    if LuaArrayIsArray(cfg.srcs) then
        return KArrayOps.filter(KArrayOps.map(cfg.srcs, s), function(lua_, v) return KLength(v) > 0 end)
    end
    if KTypeOf(cfg.src) == "string" then
        local one = s(_G, cfg.src)
        local lua_one_length_6
        if #one > 0 then
            lua_one_length_6 = {one}
        else
            lua_one_length_6 = {}
        end
        return lua_one_length_6
    end
    if KTypeOf(cfg.srcs) == "string" then
        local one = s(_G, cfg.srcs)
        local lua_one_length_7
        if #one > 0 then
            lua_one_length_7 = {one}
        else
            lua_one_length_7 = {}
        end
        return lua_one_length_7
    end
    return {}
end
function deriveChoiceSeed(self, cfg)
    local det = not not cfg.deterministic
    if not det then
        return 0
    end
    local lua_temp_8
    if cfg.seed ~= nil then
        lua_temp_8 = LuaNumber(cfg.seed) or 0
    else
        lua_temp_8 = nil
    end
    local seedOpt = lua_temp_8
    if seedOpt ~= nil then
        return bit32.rshift(seedOpt, 0)
    end
    local lua_temp_9
    if cfg.context and KTypeOf(cfg.context) == "table" then
        lua_temp_9 = cfg.context
    else
        lua_temp_9 = KObject:create(nil)
    end
    local c = lua_temp_9
    local packed = table.concat(
        {
            s(_G, c.entityUuid),
            tostring(LuaNumber(c.surfaceId) or 0),
            tostring(LuaNumber(c.seq) or 0),
            tostring(LuaNumber(c.tick) or 0),
            tostring(LuaNumber(c.slot) or 0)
        },
        "|"
    )
    return hashString32(_G, packed)
end
function chooseIndex(self, count, cfg)
    if count <= 1 then
        return 0
    end
    local rnd = not not cfg.random
    if not rnd then
        return 0
    end
    if not not cfg.deterministic then
        local seed = bit32.rshift(
            deriveChoiceSeed(_G, cfg),
            0
        )
        local next = xorshift32(_G, seed or 1)
        return bit32.rshift(next % count, 0)
    end
    return bit32.rshift(
        math.floor(KMath:random() * count) % count,
        0
    )
end
SoundInstance = LuaClass()
SoundInstance.name = "SoundInstance"
function SoundInstance.prototype.lua_constructor(self, engine, id, api, log)
    self._id = LuaNumber(id) or 0
    self._api = api
    self._log = log
end
function SoundInstance.prototype.id(self)
    return self._id
end
function SoundInstance.prototype.play(self)
    safeExec(
        _G,
        self._log,
        "play",
        function() return self._api:playId(self._id) end
    )
    return self
end
function SoundInstance.prototype.stop(self)
    safeExec(
        _G,
        self._log,
        "stop",
        function() return self._api:stopId(self._id) end
    )
    return self
end
function SoundInstance.prototype.pause(self)
    if self._api.pauseId then
        safeExec(
            _G,
            self._log,
            "pause",
            function() return self._api:pauseId(self._id) end
        )
    end
    return self
end
function SoundInstance.prototype.volume(self, v)
    safeExec(
        _G,
        self._log,
        "volume",
        function() return self._api:setVolumeId(
            self._id,
            math.max(
                0,
                LuaNumber(v)
            )
        ) end
    )
    return self
end
function SoundInstance.prototype.pitch(self, v)
    local pv = math.min(
        math.max(
            LuaNumber(v),
            0.5
        ),
        2
    )
    safeExec(
        _G,
        self._log,
        "pitch",
        function() return self._api:setPitchId(self._id, pv) end
    )
    return self
end
function SoundInstance.prototype.loop(self, v)
    if v == nil then
        v = true
    end
    safeExec(
        _G,
        self._log,
        "loop",
        function() return self._api:setLoopingId(self._id, not not v) end
    )
    return self
end
function SoundInstance.prototype.pos(self, x, y, z)
    local p = v3(_G, x, y, z)
    safeExec(
        _G,
        self._log,
        "pos",
        function() return self._api:setPositionId(self._id, p[1], p[2], p[3]) end
    )
    return self
end
function SoundInstance.prototype.positional(self, v)
    if v == nil then
        v = true
    end
    if self._api.setPositionalId then
        safeExec(
            _G,
            self._log,
            "positional",
            function() return self._api:setPositionalId(self._id, not not v) end
        )
    end
    return self
end
function SoundInstance.prototype.maxDistance(self, v)
    if self._api.setMaxDistanceId then
        safeExec(
            _G,
            self._log,
            "maxDistance",
            function() return self._api:setMaxDistanceId(
                self._id,
                LuaNumber(v)
            ) end
        )
    end
    return self
end
function SoundInstance.prototype.refDistance(self, v)
    if self._api.setRefDistanceId then
        safeExec(
            _G,
            self._log,
            "refDistance",
            function() return self._api:setRefDistanceId(
                self._id,
                LuaNumber(v)
            ) end
        )
    end
    return self
end
function SoundInstance.prototype.reverb(self, v)
    if v == nil then
        v = true
    end
    safeExec(
        _G,
        self._log,
        "reverb",
        function() return self._api:setReverbEnabledId(self._id, not not v) end
    )
    return self
end
function SoundInstance.prototype.directional(self, v)
    if v == nil then
        v = true
    end
    safeExec(
        _G,
        self._log,
        "directional",
        function() return self._api:setDirectionalId(self._id, not not v) end
    )
    return self
end
function SoundInstance.prototype.innerAngle(self, v)
    if self._api.setInnerAngleId then
        safeExec(
            _G,
            self._log,
            "innerAngle",
            function() return self._api:setInnerAngleId(
                self._id,
                LuaNumber(v)
            ) end
        )
    end
    return self
end
function SoundInstance.prototype.outerAngle(self, v)
    if self._api.setOuterAngleId then
        safeExec(
            _G,
            self._log,
            "outerAngle",
            function() return self._api:setOuterAngleId(
                self._id,
                LuaNumber(v)
            ) end
        )
    end
    return self
end
function SoundInstance.prototype.direction(self, x, y, z)
    if not self._api.setDirectionId then
        return self
    end
    local d = v3(_G, x, y, z)
    safeExec(
        _G,
        self._log,
        "direction",
        function() return self._api:setDirectionId(self._id, d[1], d[2], d[3]) end
    )
    return self
end
function SoundInstance.prototype.velocity(self, x, y, z)
    if not self._api.setVelocityId then
        return self
    end
    local vv = v3(_G, x, y, z)
    safeExec(
        _G,
        self._log,
        "velocity",
        function() return self._api:setVelocityId(self._id, vv[1], vv[2], vv[3]) end
    )
    return self
end
function SoundInstance.prototype.velocityFromTranslation(self, v)
    if v == nil then
        v = true
    end
    if self._api.setVelocityFromTranslationId then
        safeExec(
            _G,
            self._log,
            "velocityFromTranslation",
            function() return self._api:setVelocityFromTranslationId(self._id, not not v) end
        )
    end
    return self
end
SoundObject = LuaClass()
SoundObject.name = "SoundObject"
function SoundObject.prototype.lua_constructor(self, registry, mode, base)
    self._r = registry
    self._mode = mode
    local lua_temp_10
    if mode == "event" then
        lua_temp_10 = tostring(base or "")
    else
        lua_temp_10 = nil
    end
    self._event = lua_temp_10
    local lua_temp_11
    if mode == "file" then
        lua_temp_11 = base or ({})
    else
        lua_temp_11 = nil
    end
    self._fileCfg = lua_temp_11
    self._deterministic = nil
    self._seed = nil
    self._positional = nil
    self._random = nil
    self._context = {
        entityUuid = "",
        surfaceId = 0,
        seq = 0,
        tick = 0,
        slot = 0
    }
    self._overrides = nil
    self._autoSeq = 0
    self._autoSeqEnabled = true
    self._seqMode = "increment"
end
function SoundObject.prototype.setDeterministic(self, v)
    if v == nil then
        v = true
    end
    self._deterministic = not not v
    return self
end
function SoundObject.prototype.setSeed(self, seed)
    self._seed = LuaNumber(seed) or 0
    return self
end
function SoundObject.prototype.setPositional(self, v)
    if v == nil then
        v = true
    end
    self._positional = not not v
    return self
end
function SoundObject.prototype.setRandom(self, v)
    if v == nil then
        v = true
    end
    self._random = not not v
    return self
end
function SoundObject.prototype.setOverrides(self, overrides)
    self._overrides = overrides or nil
    return self
end
function SoundObject.prototype.setContext(self, ctx)
    if ctx and KTypeOf(ctx) == "table" then
        if ctx.entityUuid ~= nil then
            self._context.entityUuid = s(_G, ctx.entityUuid)
        end
        if ctx.surfaceId ~= nil then
            self._context.surfaceId = LuaNumber(ctx.surfaceId) or 0
        end
        if ctx.seq ~= nil then
            self._context.seq = LuaNumber(ctx.seq) or 0
        end
        if ctx.tick ~= nil then
            self._context.tick = LuaNumber(ctx.tick) or 0
        end
        if ctx.slot ~= nil then
            self._context.slot = LuaNumber(ctx.slot) or 0
        end
    end
    return self
end
function SoundObject.prototype.setEntityUuid(self, uuid)
    self._context.entityUuid = s(_G, uuid)
    return self
end
function SoundObject.prototype.setSurfaceId(self, id)
    self._context.surfaceId = LuaNumber(id) or 0
    return self
end
function SoundObject.prototype.setTick(self, tick)
    self._context.tick = LuaNumber(tick) or 0
    return self
end
function SoundObject.prototype.setSlot(self, slot)
    self._context.slot = LuaNumber(slot) or 0
    return self
end
function SoundObject.prototype.enableAutoSeq(self, v)
    if v == nil then
        v = true
    end
    self._autoSeqEnabled = not not v
    return self
end
function SoundObject.prototype.setSeqMode(self, mode)
    local m = tostring(mode or "")
    self._seqMode = m == "keep" and "keep" or "increment"
    return self
end
function SoundObject.prototype.setSeq(self, seq)
    self._context.seq = LuaNumber(seq) or 0
    return self
end
function SoundObject.prototype.nextSeq(self)
    self._autoSeq = self._autoSeq + 1
    self._context.seq = self._autoSeq
    return self._context.seq
end
function SoundObject.prototype._buildCfgForPlay(self)
    if self._mode == "event" then
        local cfg = {event = self._event}
        if self._deterministic ~= nil then
            cfg.deterministic = not not self._deterministic
        end
        if self._seed ~= nil then
            cfg.seed = LuaNumber(self._seed) or 0
        end
        if self._random ~= nil then
            cfg.random = not not self._random
        end
        cfg.context = {
            entityUuid = self._context.entityUuid,
            surfaceId = self._context.surfaceId,
            seq = self._context.seq,
            tick = self._context.tick,
            slot = self._context.slot
        }
        local ov = nil
        if self._overrides and KTypeOf(self._overrides) == "table" then
            ov = LuaTableMerge({}, self._overrides)
        end
        if self._positional ~= nil then
            ov = ov or ({})
            ov.is3D = not not self._positional
        end
        if ov then
            cfg.overrides = ov
        end
        return cfg
    end
    local cfg = LuaTableMerge({}, self._fileCfg)
    if self._positional ~= nil then
        cfg.is3D = not not self._positional
    end
    if self._random ~= nil then
        cfg.random = not not self._random
    end
    if self._overrides and KTypeOf(self._overrides) == "table" then
        LuaTableMerge(cfg, self._overrides)
    end
    return cfg
end
function SoundObject.prototype.play(self)
    if self._mode == "event" then
        if self._autoSeqEnabled then
            if self._seqMode == "increment" then
                self:nextSeq()
            elseif not self._context.seq then
                self:nextSeq()
            end
        end
    end
    return self._r:playSound(self:_buildCfgForPlay())
end
SoundRegistry = LuaClass()
SoundRegistry.name = "SoundRegistry"
function SoundRegistry.prototype.lua_constructor(self, engine, K)
    self.engine = engine
    self.K = K or (_G.__kalitech or KObject:create(nil))
    self._log = getLog(_G, engine)
    self._bankLoaded = false
    self._bankLoadAttempted = false
    self._bankPath = "data/sounds.json"
    self:_tryAutoLoadBank()
end
function SoundRegistry.prototype.api(self)
    local soundApi = self.engine.sound and self.engine:sound()
    if not soundApi or not isFn(_G, soundApi.createId) then
        error(
            LuaConstruct(Error, "[SND] engine.sound().createId(cfg) is required"),
            0
        )
    end
    return soundApi
end
function SoundRegistry.prototype._tryAutoLoadBank(self)
    if self._bankLoaded then
        return true
    end
    if self._bankLoadAttempted then
        return false
    end
    self._bankLoadAttempted = true
    local soundApi = self:api()
    if not isFn(_G, soundApi.loadBank) then
        return false
    end
    do
        local function lua_catch(e)
            self._log:error("[SND] bank autoload failed: " .. self._bankPath, e)
            return true, false
        end
        local lua_try, lua_hasReturned, lua_returnValue = pcall(function()
            local assets = self.engine.assets and self.engine:assets()
            if not assets or not isFn(_G, assets.readText) then
                error(
                    LuaConstruct(Error, "[SND] engine.assets().readText(path) is required for bank autoload"),
                    0
                )
            end
            local txt = assets:readText(self._bankPath)
            local obj = json:decode(txt)
            soundApi:loadBank(obj)
        end)
        if not lua_try then
            lua_hasReturned, lua_returnValue = lua_catch(lua_hasReturned)
        end
        if lua_hasReturned then
            return lua_returnValue
        end
    end
    self._bankLoaded = true
    return true
end
function SoundRegistry.prototype._ensureBankLoaded(self)
    if self._bankLoaded then
        return true
    end
    self:_tryAutoLoadBank()
    return self._bankLoaded
end
function SoundRegistry.prototype.setSeed(self, seed)
    local api = self:api()
    if not api.setSeed then
        error(
            LuaConstruct(Error, "[SND] engine.sound().setSeed(seed) is required"),
            0
        )
    end
    safeExec(
        _G,
        self._log,
        "setSeed",
        function() return api:setSeed(LuaNumber(seed) or 0) end
    )
    return self
end
function SoundRegistry.prototype.setDeterministic(self, v)
    if v == nil then
        v = true
    end
    local api = self:api()
    if not api.setDeterministic then
        error(
            LuaConstruct(Error, "[SND] engine.sound().setDeterministic(bool) is required"),
            0
        )
    end
    safeExec(
        _G,
        self._log,
        "setDeterministic",
        function() return api:setDeterministic(not not v) end
    )
    return self
end
function SoundRegistry.prototype.create(self, cfg)
    local api = self:api()
    local id = safeExec(
        _G,
        self._log,
        "createId",
        function() return api:createId(cfg) end
    )
    return LuaConstruct(
        SoundInstance,
        self.engine,
        id,
        api,
        self._log
    )
end
function SoundRegistry.prototype.createAndPlay(self, cfg)
    local api = self:api()
    local id = safeExec(
        _G,
        self._log,
        "createId",
        function() return api:createId(cfg) end
    )
    safeExec(
        _G,
        self._log,
        "play",
        function() return api:playId(id) end
    )
    return LuaConstruct(
        SoundInstance,
        self.engine,
        id,
        api,
        self._log
    )
end
function SoundRegistry.prototype.loadBank(self, bankObj)
    local api = self:api()
    if not isFn(_G, api.loadBank) then
        error(
            LuaConstruct(Error, "[SND] engine.sound().loadBank(bankObj) is required for event sound bank"),
            0
        )
    end
    safeExec(
        _G,
        self._log,
        "loadBank",
        function() return api:loadBank(bankObj) end
    )
    self._bankLoaded = true
    self._bankLoadAttempted = true
    return self
end
function SoundRegistry.prototype.clearBank(self)
    local api = self:api()
    if isFn(_G, api.clearBank) then
        safeExec(
            _G,
            self._log,
            "clearBank",
            function() return api:clearBank() end
        )
    end
    self._bankLoaded = false
    self._bankLoadAttempted = false
    return self
end
function SoundRegistry.prototype.listEvents(self)
    self:_ensureBankLoaded()
    local api = self:api()
    if not isFn(_G, api.listEvents) then
        return {}
    end
    return safeExec(
        _G,
        self._log,
        "listEvents",
        function() return api:listEvents() end
    )
end
function SoundRegistry.prototype.getSound(self, eventKey)
    self:_ensureBankLoaded()
    return LuaConstruct(SoundObject, self, "event", eventKey)
end
function SoundRegistry.prototype.getSoundFile(self, srcOrCfg)
    local lua_temp_12
    if KTypeOf(srcOrCfg) == "string" then
        lua_temp_12 = {src = srcOrCfg}
    else
        lua_temp_12 = srcOrCfg or ({})
    end
    local cfg = lua_temp_12
    return LuaConstruct(SoundObject, self, "file", cfg)
end
function SoundRegistry.prototype.playSound(self, cfg)
    local api = self:api()
    if not cfg or KTypeOf(cfg) ~= "table" then
        error(
            LuaConstruct(Error, "[SND] playSound(cfg): cfg object is required"),
            0
        )
    end
    local hasEvent = KTypeOf(cfg.event) == "string" and KLength(cfg.event) > 0
    local srcList = normalizeSrcList(_G, cfg)
    local hasSrc = KLength(srcList) > 0
    if not hasEvent and not hasSrc then
        error(
            LuaConstruct(Error, "[SND] playSound(cfg): 'event' or 'src' (string/array) is required"),
            0
        )
    end
    if hasEvent then
        self:_ensureBankLoaded()
        if not isFn(_G, api.playEventCfgId) then
            error(
                LuaConstruct(Error, "[SND] engine.sound().playEventCfgId(cfg) is required for event sounds"),
                0
            )
        end
        local ecfg = self:_normalizeEventCfg(cfg)
        local id = safeExec(
            _G,
            self._log,
            "playEventCfgId",
            function() return api:playEventCfgId(ecfg) end
        )
        return LuaConstruct(
            SoundInstance,
            self.engine,
            id,
            api,
            self._log
        )
    end
    local scfg = self:_normalizeSrcCfg(cfg, srcList)
    local id = safeExec(
        _G,
        self._log,
        "createId",
        function() return api:createId(scfg) end
    )
    print(id)
    safeExec(
        _G,
        self._log,
        "play",
        function() return api:playId(id) end
    )
    return LuaConstruct(
        SoundInstance,
        self.engine,
        id,
        api,
        self._log
    )
end
function SoundRegistry.prototype._normalizeEventCfg(self, cfg)
    local out = {event = s(_G, cfg.event)}
    if cfg.random ~= nil then
        out.random = not not cfg.random
    end
    if cfg.deterministic ~= nil then
        out.deterministic = not not cfg.deterministic
    end
    if cfg.seed ~= nil then
        out.seed = LuaNumber(cfg.seed) or 0
    end
    if cfg.context and KTypeOf(cfg.context) == "table" then
        local c = cfg.context
        out.context = {
            entityUuid = s(_G, c.entityUuid),
            surfaceId = LuaNumber(c.surfaceId) or 0,
            seq = LuaNumber(c.seq) or 0,
            tick = LuaNumber(c.tick) or 0,
            slot = LuaNumber(c.slot) or 0
        }
    end
    local ov = nil
    if cfg.overrides and KTypeOf(cfg.overrides) == "table" then
        ov = LuaTableMerge({}, cfg.overrides)
    end
    if cfg.is3D ~= nil then
        ov = ov or ({})
        ov.is3D = not not cfg.is3D
    end
    if cfg.volume ~= nil then
        ov = ov or ({})
        ov.volume = cfg.volume
    end
    if cfg.pitch ~= nil then
        ov = ov or ({})
        ov.pitch = cfg.pitch
    end
    if cfg.looping ~= nil then
        ov = ov or ({})
        ov.looping = not not cfg.looping
    end
    if cfg.pos ~= nil or cfg.position ~= nil or cfg.x ~= nil or cfg.y ~= nil or cfg.z ~= nil then
        local lua_temp_14
        if cfg.pos ~= nil then
            lua_temp_14 = cfg.pos
        else
            local lua_temp_13
            if cfg.position ~= nil then
                lua_temp_13 = cfg.position
            else
                lua_temp_13 = {x = cfg.x, y = cfg.y, z = cfg.z}
            end
            lua_temp_14 = lua_temp_13
        end
        local p = lua_temp_14
        local vv = v3(_G, p, cfg.y, cfg.z)
        ov = ov or ({})
        ov.x = vv[1]
        ov.y = vv[2]
        ov.z = vv[3]
    end
    if ov then
        out.overrides = ov
    end
    return out
end
function SoundRegistry.prototype._normalizeSrcCfg(self, cfg, srcList)
    local out = {}
    local lua_temp_15
    if cfg.type ~= nil then
        lua_temp_15 = s(_G, cfg.type)
    else
        lua_temp_15 = nil
    end
    out.type = lua_temp_15
    local det = toBoolOpt(_G, cfg.deterministic)
    local seed = toNumOpt(_G, cfg.seed, nil)
    local rnd = toBoolOpt(_G, cfg.random)
    if det ~= nil then
        out.deterministic = det
    end
    if seed ~= nil then
        out.seed = seed
    end
    if rnd ~= nil then
        out.random = rnd
    end
    if cfg.context and KTypeOf(cfg.context) == "table" then
        local c = cfg.context
        out.context = {
            entityUuid = s(_G, c.entityUuid),
            surfaceId = LuaNumber(c.surfaceId) or 0,
            seq = LuaNumber(c.seq) or 0,
            tick = LuaNumber(c.tick) or 0,
            slot = LuaNumber(c.slot) or 0
        }
    end
    local idx = chooseIndex(_G, KLength(srcList), {deterministic = not not det, seed = seed, random = not not rnd, context = out.context})
    out.src = s(_G, KIndex(srcList, idx))
    if cfg.is3D ~= nil then
        out.is3D = not not cfg.is3D
    end
    if cfg.looping ~= nil then
        out.looping = not not cfg.looping
    end
    if cfg.volume ~= nil then
        out.volume = cfg.volume
    end
    if cfg.pitch ~= nil then
        out.pitch = cfg.pitch
    end
    if cfg.pos ~= nil or cfg.position ~= nil or cfg.x ~= nil or cfg.y ~= nil or cfg.z ~= nil then
        local lua_temp_17
        if cfg.pos ~= nil then
            lua_temp_17 = cfg.pos
        else
            local lua_temp_16
            if cfg.position ~= nil then
                lua_temp_16 = cfg.position
            else
                lua_temp_16 = {x = cfg.x, y = cfg.y, z = cfg.z}
            end
            lua_temp_17 = lua_temp_16
        end
        local p = lua_temp_17
        local vv = v3(_G, p, cfg.y, cfg.z)
        out.x = vv[1]
        out.y = vv[2]
        out.z = vv[3]
    end
    return out
end
create = setmetatable(
    {},
    {__call = function(lua_, self, engine, K)
        if not engine then
            error(
                LuaConstruct(Error, "[SND] engine is required"),
                0
            )
        end
        return LuaConstruct(SoundRegistry, engine, K)
    end}
)
create.META = {
    moduleId = "sound",
    id = "sound",
    version = "2.1.0",
    description = "Universal sound facade (SoundId-only): playSound(cfg), event bank + src sounds, object-mode getSound/getSoundFile",
    engineMin = "0.1.0",
    changelog = {"2.1.0: added random selection support for src arrays; deterministic-safe variant choice; removed stray stdout logging; strengthened validation."},
}
M = create

return M
