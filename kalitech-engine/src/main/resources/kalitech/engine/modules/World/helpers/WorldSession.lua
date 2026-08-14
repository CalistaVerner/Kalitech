local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local Arrays = luaRuntime.array
local Numbers = luaRuntime.number
local Tables = luaRuntime.table
local Classes = luaRuntime.class
local Error = luaRuntime.Error
local lua_require_result_0 = require("./WorldUtil.lua")
deepMerge = lua_require_result_0.deepMerge
isObj = lua_require_result_0.isObj
str = lua_require_result_0.str
bool = lua_require_result_0.bool
numInt = lua_require_result_0.numInt
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
WorldSession = Classes:create()
WorldSession.name = "WorldSession"
function WorldSession.prototype.lua_constructor(self, worldApi, seed)
    self._api = worldApi
    local lua_isObj_result_1
    if isObj(_G, seed) then
        lua_isObj_result_1 = deepMerge(_G, {}, seed)
    else
        lua_isObj_result_1 = {}
    end
    self._desc = lua_isObj_result_1
    if not Arrays:isArray(self._desc.systems) then
        self._desc.systems = {}
    end
end
function WorldSession.prototype.merge(self, v)
    if v == nil then
        return self
    end
    if not isObj(_G, v) then
        error(
            Classes:construct(Error, "[WORLD] session.merge(v): v must be an object"),
            0
        )
    end
    self._desc = deepMerge(_G, self._desc, v)
    if not Arrays:isArray(self._desc.systems) then
        self._desc.systems = {}
    end
    return self
end
function WorldSession.prototype.name(self, v)
    self._desc.name = str(_G, v, "world")
    return self
end
function WorldSession.prototype.start(self, v)
    if v == nil then
        v = true
    end
    self._desc.start = bool(_G, v, true)
    return self
end
function WorldSession.prototype.runtime(self, v)
    self._desc.runtime = str(_G, v, "world")
    return self
end
function WorldSession.prototype.profile(self, v)
    return self:runtime(v)
end
function WorldSession.prototype.orderStep(self, v)
    self._desc.orderStep = numInt(_G, v, 10)
    return self
end
function WorldSession.prototype.systems(self, list)
    local lua_self__desc_3 = self._desc
    local lua_Array_isArray_result_2
    if Arrays:isArray(list) then
        lua_Array_isArray_result_2 = list
    else
        lua_Array_isArray_result_2 = {}
    end
    lua_self__desc_3.systems = lua_Array_isArray_result_2
    return self
end
function WorldSession.prototype.addSystem(self, v)
    if v == nil then
        error(
            Classes:construct(Error, "[WORLD] addSystem(v): v is required"),
            0
        )
    end
    if not Arrays:isArray(self._desc.systems) then
        self._desc.systems = {}
    end
    KArrayOps.push(self._desc.systems, v)
    return self
end
function WorldSession.prototype.time(self, v)
    local t = normalizeTimeDesc(_G, v)
    if t and #Tables:keys(t) then
        self._desc.time = t
    else
        Tables:remove(self._desc, "time")
    end
    return self
end
function WorldSession.prototype.build(self)
    return self._api:normalize(self._desc)
end
function WorldSession.prototype.create(self)
    return self._api:create(self._desc)
end
function WorldSession.prototype.run(self)
    return self:create()
end
M = {WorldSession = WorldSession}

return M
