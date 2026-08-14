local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local LuaClass = luaRuntime.LuaClass
local LuaConstruct = luaRuntime.LuaConstruct
local LuaNumber = luaRuntime.LuaNumber
local Error = luaRuntime.Error
local lua_require_result_0 = require("./TerrainTypes.lua")
isObj = lua_require_result_0.isObj
i32 = lua_require_result_0.i32
TerrainHeights = LuaClass()
TerrainHeights.name = "TerrainHeights"
function TerrainHeights.prototype.lua_constructor(self, terrNative)
    self.terr = terrNative
end
function TerrainHeights.toFloatArray(self, raw)
    if raw == nil then
        return nil
    end

    local length = KLength(raw)
    if KTypeOf(length) ~= "number" or length <= 0 then
        return raw
    end

    local out = {}
    local i = 0
    while i < length do
        local ok, value = pcall(function()
            return KIndex(raw, i)
        end)
        if not ok and KTypeOf(raw.get) == "function" then
            ok, value = pcall(function()
                return raw:get(i)
            end)
        end
        if not ok then
            return raw
        end
        KSetIndex(out, i, LuaNumber(value) or 0)
        i = i + 1
    end
    return out
end
function TerrainHeights.inferSizeFromHeights(self, heights)
    if not heights then
        return 0
    end
    local lua_temp_3
    if KTypeOf(KLength(heights)) == "number" then
        lua_temp_3 = bit32.bor(KLength(heights), 0)
    else
        lua_temp_3 = 0
    end
    local len = lua_temp_3
    if len <= 0 then
        return 0
    end
    local s = math.floor(math.sqrt(len) + 0.5)
    local lua_temp_4
    if s > 0 and s * s == len then
        lua_temp_4 = s
    else
        lua_temp_4 = 0
    end
    return lua_temp_4
end
function TerrainHeights.isPow2(self, n)
    return n > 0 and bit32.band(n, n - 1) == 0
end
function TerrainHeights.isJmeTerrainSize(self, n)
    local x = bit32.bor(n, 0) - 1
    return x > 0 and TerrainHeights:isPow2(x)
end
function TerrainHeights.validateTerrainDims(self, size, patchSize)
    local s = bit32.bor(size, 0)
    local p = bit32.bor(patchSize, 0)
    if s > 0 and not TerrainHeights:isJmeTerrainSize(s) then
        error(
            LuaConstruct(
                Error,
                "[TERR] size must be (2^k + 1). Got size=" .. tostring(s)
            ),
            0
        )
    end
    if p > 0 and not TerrainHeights:isJmeTerrainSize(p) then
        error(
            LuaConstruct(
                Error,
                "[TERR] patchSize must be (2^k + 1). Got patchSize=" .. tostring(p)
            ),
            0
        )
    end
    if s > 0 and p > 0 and p > s then
        error(
            LuaConstruct(
                Error,
                (("[TERR] patchSize must be <= size. Got patchSize=" .. tostring(p)) .. " size=") .. tostring(s)
            ),
            0
        )
    end
end
function TerrainHeights.assertHeightsMatchSize(self, heights, size, where)
    local s = i32(_G, size, 0)
    if s <= 0 then
        return
    end
    local need = s * s
    local lua_temp_5
    if heights and KTypeOf(KLength(heights)) == "number" then
        lua_temp_5 = bit32.bor(KLength(heights), 0)
    else
        lua_temp_5 = 0
    end
    local got = lua_temp_5
    if got and got ~= need then
        error(
            LuaConstruct(
                Error,
                ((((((("[TERR] " .. tostring(where)) .. ": heights length must be size*size (") .. tostring(need)) .. "), got ") .. tostring(got)) .. " (size=") .. tostring(s)) .. ")"
            ),
            0
        )
    end
end
function TerrainHeights.prototype.perlin(self, cfg)
    if not self.terr or KTypeOf(self.terr.perlinHeights) ~= "function" then
        error(
            LuaConstruct(Error, "[TERR] perlinHeights: native generator not available in this build"),
            0
        )
    end
    local lua_TerrainHeights_9 = TerrainHeights
    local lua_TerrainHeights_toFloatArray_10 = TerrainHeights.toFloatArray
    local lua_self_7 = self.terr
    local lua_self_7_perlinHeights_8 = lua_self_7.perlinHeights
    local lua_isObj_result_6
    if isObj(_G, cfg) then
        lua_isObj_result_6 = cfg
    else
        lua_isObj_result_6 = {}
    end
    return lua_TerrainHeights_toFloatArray_10(
        lua_TerrainHeights_9,
        lua_self_7_perlinHeights_8(lua_self_7, lua_isObj_result_6)
    )
end
function TerrainHeights.prototype.ridged(self, cfg)
    if not self.terr or KTypeOf(self.terr.ridgedHeights) ~= "function" then
        error(
            LuaConstruct(Error, "[TERR] ridgedHeights: native generator not available in this build"),
            0
        )
    end
    local lua_TerrainHeights_14 = TerrainHeights
    local lua_TerrainHeights_toFloatArray_15 = TerrainHeights.toFloatArray
    local lua_self_12 = self.terr
    local lua_self_12_ridgedHeights_13 = lua_self_12.ridgedHeights
    local lua_isObj_result_11
    if isObj(_G, cfg) then
        lua_isObj_result_11 = cfg
    else
        lua_isObj_result_11 = {}
    end
    return lua_TerrainHeights_toFloatArray_15(
        lua_TerrainHeights_14,
        lua_self_12_ridgedHeights_13(lua_self_12, lua_isObj_result_11)
    )
end
M = {TerrainHeights = TerrainHeights}

return M
