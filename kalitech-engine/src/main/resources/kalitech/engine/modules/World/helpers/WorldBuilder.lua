local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local LuaClass = luaRuntime.LuaClass
local Error = luaRuntime.Error
local LuaConstruct = luaRuntime.LuaConstruct
local LuaArrayIsArray = luaRuntime.LuaArrayIsArray
local lua_require_result_0 = require("./WorldUtil.lua")
deepMerge = lua_require_result_0.deepMerge
isObj = lua_require_result_0.isObj
str = lua_require_result_0.str
bool = lua_require_result_0.bool
numInt = lua_require_result_0.numInt
WorldBuilder = LuaClass()
WorldBuilder.name = "WorldBuilder"
function WorldBuilder.prototype.lua_constructor(self, worldApi)
    self._api = worldApi
    self._desc = {
        name = "world",
        start = true,
        runtime = "world",
        orderStep = 10,
        systems = {}
    }
end
function WorldBuilder.prototype.merge(self, desc)
    if desc == nil then
        return self
    end
    if not isObj(_G, desc) then
        error(
            LuaConstruct(Error, "[WORLD] builder.merge(desc): desc must be an object"),
            0
        )
    end
    self._desc = deepMerge(_G, self._desc, desc)
    if not LuaArrayIsArray(self._desc.systems) then
        self._desc.systems = {}
    end
    return self
end
function WorldBuilder.prototype.name(self, v)
    self._desc.name = str(_G, v, "world")
    return self
end
function WorldBuilder.prototype.start(self, v)
    if v == nil then
        v = true
    end
    self._desc.start = bool(_G, v, true)
    return self
end
function WorldBuilder.prototype.runtime(self, v)
    self._desc.runtime = str(_G, v, "world")
    return self
end
function WorldBuilder.prototype.profile(self, v)
    return self:runtime(v)
end
function WorldBuilder.prototype.orderStep(self, v)
    self._desc.orderStep = numInt(_G, v, 10)
    return self
end
function WorldBuilder.prototype.system(self, v)
    if v == nil then
        error(
            LuaConstruct(Error, "[WORLD] builder.system(v): v is required"),
            0
        )
    end
    local lua_self__desc_systems_1 = self._desc.systems
    lua_self__desc_systems_1[#lua_self__desc_systems_1 + 1] = v
    return self
end
function WorldBuilder.prototype.luaSystem(self, module, config, opts)
    local m = str(_G, module, "")
    if not m then
        error(
            LuaConstruct(Error, "[WORLD] luaSystem(module,...): module is required"),
            0
        )
    end
    local lua_temp_2
    if opts and KTypeOf(opts) == "table" then
        lua_temp_2 = opts
    else
        lua_temp_2 = {}
    end
    local o = lua_temp_2
    local lua_str_5 = str
    local lua_G_4 = _G
    local lua_o_runtime_3 = o.runtime
    if lua_o_runtime_3 == nil then
        lua_o_runtime_3 = o.profile
    end
    local rt = lua_str_5(lua_G_4, lua_o_runtime_3, self._desc.runtime)
    local lua_numInt_result_7 = numInt(_G, o.order, 0)
    local lua_temp_6
    if o.stableId ~= nil then
        lua_temp_6 = tostring(o.stableId)
    else
        lua_temp_6 = nil
    end
    local sys = {module = m, runtime = rt, order = lua_numInt_result_7, stableId = lua_temp_6}
    local cfg = deepMerge(_G, {}, config or ({}))
    local lua_self__desc_systems_8 = self._desc.systems
    lua_self__desc_systems_8[#lua_self__desc_systems_8 + 1] = deepMerge(_G, sys, cfg)
    return self
end
function WorldBuilder.prototype.build(self)
    return self._api:normalize(self._desc)
end
function WorldBuilder.prototype.create(self)
    return self._api:create(self._desc)
end
M = {WorldBuilder = WorldBuilder}

return M
