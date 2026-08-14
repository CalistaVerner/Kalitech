local M = {}
local json = require("@builtin/json")
local luaRuntime = require("@builtin/lua_runtime")
local LuaTableMerge = luaRuntime.LuaTableMerge
local LuaTableKeys = luaRuntime.LuaTableKeys
local LuaArraySort = luaRuntime.LuaArraySort
local LuaClass = luaRuntime.LuaClass
local Error = luaRuntime.Error
local LuaConstruct = luaRuntime.LuaConstruct
local LuaArraySlice = luaRuntime.LuaArraySlice
local LuaTableRemove = luaRuntime.LuaTableRemove
local LuaArraySetLength = luaRuntime.LuaArraySetLength
function isPlainLuaTable(self, x)
    if not x or KTypeOf(x) ~= "table" then
        return false
    end
    local p = KObject:getPrototypeOf(x)
    return p == KObject.prototype or p == nil
end
function shallowClone(self, obj)
    local lua_obj_0
    if obj then
        lua_obj_0 = LuaTableMerge({}, obj)
    else
        lua_obj_0 = KObject:create(nil)
    end
    return lua_obj_0
end
function stableStringify(self, obj)
    if not obj or KTypeOf(obj) ~= "table" then
        return tostring(obj)
    end
    local keys = LuaArraySort(LuaTableKeys(obj))
    local out = "{"
    do
        local i = 0
        while i < #keys do
            local k = keys[i + 1]
            local v = obj[k]
            out = out .. (((i and "," or "") .. json:encode(k)) .. ":") .. json:encode(v)
            i = i + 1
        end
    end
    out = out .. "}"
    return out
end
MaterialsRegistry = LuaClass()
MaterialsRegistry.name = "MaterialsRegistry"
function MaterialsRegistry.prototype.lua_constructor(self, engine, K)
    self.engineRef = engine
    self.K = K or (_G.__kalitech or KObject:create(nil))
    self.defs = nil
    self.cacheHandle = KObject:create(nil)
    self.cacheHandleOv = KObject:create(nil)
    self._enableOverrideCache = true
    self._overrideCacheMax = 256
    self._overrideCacheOrder = {}
end
function MaterialsRegistry.prototype.engine(self)
    local e = self.engineRef
    if not e then
        error(
            LuaConstruct(Error, "[MAT] engine not attached"),
            0
        )
    end
    return e
end
function MaterialsRegistry.prototype.dbPath(self)
    local c = self.K and self.K.config and self.K.config.materials
    local lua_temp_1
    if c and c.dbPath then
        lua_temp_1 = tostring(c.dbPath)
    else
        lua_temp_1 = "data/materials.json"
    end
    return lua_temp_1
end
function MaterialsRegistry.prototype.loadDefs(self)
    if self.defs then
        return self.defs
    end
    local path = self:dbPath()
    local jsonText
    do
        local function lua_catch(e)
            local lua_Error_4 = Error
            local lua_temp_3 = ("[MAT] failed to read defs at '" .. path) .. "': "
            local lua_temp_2
            if e and e.message then
                lua_temp_2 = e.message
            else
                lua_temp_2 = e
            end
            error(
                LuaConstruct(
                    lua_Error_4,
                    lua_temp_3 .. tostring(lua_temp_2)
                ),
                0
            )
        end
        local lua_try, lua_hasReturned = pcall(function()
            local assets = self:engine().assets and self:engine():assets()
            if not assets or KTypeOf(assets.readText) ~= "function" then
                error(
                    LuaConstruct(Error, "engine.assets().readText missing"),
                    0
                )
            end
            jsonText = assets:readText(path)
        end)
        if not lua_try then
            lua_catch(lua_hasReturned)
        end
    end
    do
        local function lua_catch(e)
            local lua_Error_7 = Error
            local lua_temp_6 = ("[MAT] invalid JSON in '" .. path) .. "': "
            local lua_temp_5
            if e and e.message then
                lua_temp_5 = e.message
            else
                lua_temp_5 = e
            end
            error(
                LuaConstruct(
                    lua_Error_7,
                    lua_temp_6 .. tostring(lua_temp_5)
                ),
                0
            )
        end
        local lua_try, lua_hasReturned = pcall(function()
            self.defs = json:decode(jsonText)
        end)
        if not lua_try then
            lua_catch(lua_hasReturned)
        end
    end
    if not self.defs or KTypeOf(self.defs) ~= "table" then
        error(
            LuaConstruct(Error, ("[MAT] defs must be an object map in '" .. path) .. "'"),
            0
        )
    end
    return self.defs
end
function MaterialsRegistry.prototype.keys(self)
    do
        local function lua_catch(_)
            return true, {}
        end
        local lua_try, lua_hasReturned, lua_returnValue = pcall(function()
            return true, LuaTableKeys(self:loadDefs())
        end)
        if not lua_try then
            lua_hasReturned, lua_returnValue = lua_catch(lua_hasReturned)
        end
        if lua_hasReturned then
            return lua_returnValue
        end
    end
end
function MaterialsRegistry.prototype.base(self, name)
    local n = tostring(name or "")
    local all = self:loadDefs()
    local b = all[n]
    if not b then
        local sample = table.concat(
            LuaArraySlice(
                self:keys(),
                0,
                12
            ),
            ", "
        )
        error(
            LuaConstruct(
                Error,
                (((((("[MAT] unknown material: " .. n) .. " (db=") .. self:dbPath()) .. ", known=") .. sample) .. (#self:keys() > 12 and ", ..." or "")) .. ")"
            ),
            0
        )
    end
    return b
end
function MaterialsRegistry.prototype.cloneCfg(self, base)
    local lua_base_def_9 = base.def
    local lua_shallowClone_result_10 = shallowClone(_G, base.params or nil)
    local lua_base_scales_8
    if base.scales then
        lua_base_scales_8 = shallowClone(_G, base.scales)
    else
        lua_base_scales_8 = nil
    end
    return {def = lua_base_def_9, params = lua_shallowClone_result_10, scales = lua_base_scales_8}
end
function MaterialsRegistry.prototype.normalizeOverrides(self, overrides)
    if not overrides then
        return nil
    end
    if overrides.params or overrides.scales then
        local lua_overrides_params_11
        if overrides.params then
            lua_overrides_params_11 = shallowClone(_G, overrides.params)
        else
            lua_overrides_params_11 = nil
        end
        local lua_overrides_scales_12
        if overrides.scales then
            lua_overrides_scales_12 = shallowClone(_G, overrides.scales)
        else
            lua_overrides_scales_12 = nil
        end
        return {params = lua_overrides_params_11, scales = lua_overrides_scales_12}
    end
    if isPlainLuaTable(_G, overrides) then
        return {
            params = shallowClone(_G, overrides),
            scales = nil
        }
    end
    return nil
end
function MaterialsRegistry.prototype.applyOverrides(self, cfg, normalized)
    if not normalized then
        return
    end
    if normalized.params then
        LuaTableMerge(cfg.params, normalized.params)
    end
    if normalized.scales then
        cfg.scales = LuaTableMerge(cfg.scales or ({}), normalized.scales)
    end
end
function MaterialsRegistry.prototype._ovKey(self, name, normalized)
    if not normalized then
        return nil
    end
    local lua_normalized_params_13
    if normalized.params then
        lua_normalized_params_13 = stableStringify(_G, normalized.params)
    else
        lua_normalized_params_13 = ""
    end
    local p = lua_normalized_params_13
    local lua_normalized_scales_14
    if normalized.scales then
        lua_normalized_scales_14 = stableStringify(_G, normalized.scales)
    else
        lua_normalized_scales_14 = ""
    end
    local s = lua_normalized_scales_14
    return (((tostring(name) .. "|p=") .. p) .. "|s=") .. s
end
function MaterialsRegistry.prototype._ovCachePut(self, map, key, value)
    if not self._enableOverrideCache then
        return
    end
    map[key] = value
    local lua_self__overrideCacheOrder_15 = self._overrideCacheOrder
    lua_self__overrideCacheOrder_15[#lua_self__overrideCacheOrder_15 + 1] = key
    local max = bit32.bor(self._overrideCacheMax, 0)
    while #self._overrideCacheOrder > max do
        local old = table.remove(self._overrideCacheOrder, 1)
        if old and map[old] then
            LuaTableRemove(map, old)
        end
        if old and self.cacheMatOv[old] then
            LuaTableRemove(self.cacheMatOv, old)
        end
        if old and self.cacheHandleOv[old] then
            LuaTableRemove(self.cacheHandleOv, old)
        end
    end
end
function MaterialsRegistry.prototype.getHandle(self, name, overrides)
    local n = tostring(name or "")
    local ov = self:normalizeOverrides(overrides)
    if not ov and self.cacheHandle[n] then
        return self.cacheHandle[n]
    end
    local ovKey = self:_ovKey(n, ov)
    if ovKey and self.cacheHandleOv[ovKey] then
        return self.cacheHandleOv[ovKey]
    end
    local cfg = self:cloneCfg(self:base(n))
    self:applyOverrides(cfg, ov)
    local matApi = self:engine().material and self:engine():material()
    if not matApi or KTypeOf(matApi.createId) ~= "function" then
        error(
            LuaConstruct(Error, "[MAT] engine.material().createId(cfg) is required"),
            0
        )
    end
    local h = matApi:createId(cfg)
    if not ov then
        self.cacheHandle[n] = h
    else
        self:_ovCachePut(self.cacheHandleOv, ovKey, h)
    end
    return h
end
function MaterialsRegistry.prototype.getMaterial(self, name, overrides)
    return self:getHandle(name, overrides)
end
function MaterialsRegistry.prototype.get(self, name, overrides)
    return self:getMaterial(name, overrides)
end
function MaterialsRegistry.prototype.handle(self, name, overrides)
    return self:getHandle(name, overrides)
end
function MaterialsRegistry.prototype.preset(self, name, overrides)
    local lua_self = self
    local n = tostring(name or "")
    local ov = self:normalizeOverrides(overrides)
    local fn = setmetatable(
        {},
        {__call = function(lua_, self, moreOverrides)
            if moreOverrides then
                local a = lua_self:normalizeOverrides(ov) or ({params = nil, scales = nil})
                local b = lua_self:normalizeOverrides(moreOverrides) or ({params = nil, scales = nil})
                local merged = {params = nil, scales = nil}
                if a.params or b.params then
                    merged.params = LuaTableMerge({}, a.params or nil, b.params or nil)
                end
                if a.scales or b.scales then
                    merged.scales = LuaTableMerge({}, a.scales or nil, b.scales or nil)
                end
                return lua_self:getMaterial(n, merged)
            end
            return lua_self:getMaterial(n, ov)
        end}
    )
    fn.handle = function(self, moreOverrides)
        if moreOverrides then
            local a = lua_self:normalizeOverrides(ov) or ({params = nil, scales = nil})
            local b = lua_self:normalizeOverrides(moreOverrides) or ({params = nil, scales = nil})
            local merged = {params = nil, scales = nil}
            if a.params or b.params then
                merged.params = LuaTableMerge({}, a.params or nil, b.params or nil)
            end
            if a.scales or b.scales then
                merged.scales = LuaTableMerge({}, a.scales or nil, b.scales or nil)
            end
            return lua_self:getHandle(n, merged)
        end
        return lua_self:getHandle(n, ov)
    end
    fn.presetName = n
    fn.overrides = ov or nil
    return fn
end
function MaterialsRegistry.prototype.params(self, name, paramsObj)
    return self:getMaterial(name, {params = paramsObj or nil})
end
function MaterialsRegistry.prototype.configure(self, cfg)
    local lua_temp_16
    if cfg and KTypeOf(cfg) == "table" then
        lua_temp_16 = cfg
    else
        lua_temp_16 = {}
    end
    cfg = lua_temp_16
    if cfg.overrideCache ~= nil then
        self._enableOverrideCache = not not cfg.overrideCache
    end
    if cfg.overrideCacheMax ~= nil then
        self._overrideCacheMax = math.max(
            0,
            bit32.bor(cfg.overrideCacheMax, 0)
        )
    end
    return self
end
function MaterialsRegistry.prototype.reload(self)
    self.defs = nil
    for k in pairs(self.cacheHandle) do
        LuaTableRemove(self.cacheHandle, k)
    end
    for k in pairs(self.cacheHandleOv) do
        LuaTableRemove(self.cacheHandleOv, k)
    end
    LuaArraySetLength(self._overrideCacheOrder, 0)
    return true
end
create = setmetatable(
    {},
    {__call = function(lua_, self, engine, K)
        if not engine then
            error(
                LuaConstruct(Error, "[MAT] engine is required"),
                0
            )
        end
        return LuaConstruct(MaterialsRegistry, engine, K)
    end}
)
create.META = {
    moduleId = "material",
    id = "material",
    version = "2.0.0",
    description = "Materials registry (MaterialId-only) with JSON DB, caching, overrides and presets",
    engineMin = "0.1.0",
    changelog = {"2.0.0: switched Lua API to return MaterialId only (no host Material objects)."},
}
M = create

return M
