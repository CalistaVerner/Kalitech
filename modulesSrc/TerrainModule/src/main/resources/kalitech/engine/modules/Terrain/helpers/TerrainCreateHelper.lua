local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local LuaClass = luaRuntime.LuaClass
local Error = luaRuntime.Error
local LuaConstruct = luaRuntime.LuaConstruct
local LuaNumberIsFinite = luaRuntime.LuaNumberIsFinite
local LuaTableMerge = luaRuntime.LuaTableMerge
local lua_require_result_0 = require("./TerrainTypes.lua")
isObj = lua_require_result_0.isObj
num = lua_require_result_0.num
i32 = lua_require_result_0.i32
local lua_require_result_1 = require("./TerrainHeights.lua")
TerrainHeights = lua_require_result_1.TerrainHeights
local lua_require_result_2 = require("./TerrainInstance.lua")
TerrainInstance = lua_require_result_2.TerrainInstance
TerrainCreateHelper = LuaClass()
TerrainCreateHelper.name = "TerrainCreateHelper"
function TerrainCreateHelper.prototype.lua_constructor(self, engine, terrNative, heightsApi, physicsApi, proxies)
    self.engine = engine
    self.terr = terrNative
    self.heights = heightsApi
    self.phys = physicsApi
    self.p = proxies
end
function TerrainCreateHelper.prototype.create(self, cfg)
    local lua_isObj_result_3
    if isObj(_G, cfg) then
        lua_isObj_result_3 = cfg
    else
        lua_isObj_result_3 = {}
    end
    local c = lua_isObj_result_3
    local kind = tostring(c.kind or "terrain")
    local lua_temp_4
    if c.attach == nil then
        lua_temp_4 = true
    else
        lua_temp_4 = not not c.attach
    end
    local attachFlag = lua_temp_4
    local materialH = c.material
    local uvCfg = c.uv
    local lodCfg = c.lod
    local physCfg = c.physics
    local lua_isObj_result_5
    if isObj(_G, c.scale) then
        lua_isObj_result_5 = c.scale
    else
        lua_isObj_result_5 = nil
    end
    local scaleCfg = lua_isObj_result_5
    local lua_scaleCfg_6
    if scaleCfg then
        lua_scaleCfg_6 = num(
            _G,
            scaleCfg.xz,
            num(_G, c.xzScale, 1)
        )
    else
        lua_scaleCfg_6 = num(_G, c.xzScale, 1)
    end
    local xz = lua_scaleCfg_6
    local lua_scaleCfg_7
    if scaleCfg then
        lua_scaleCfg_7 = num(
            _G,
            scaleCfg.y,
            num(
                _G,
                c.yScale,
                num(_G, c.heightScale, 1)
            )
        )
    else
        lua_scaleCfg_7 = num(
            _G,
            c.yScale,
            num(_G, c.heightScale, 1)
        )
    end
    local y = lua_scaleCfg_7
    local function post(lua_, surfaceOrBundle)
        local lua_temp_8
        if surfaceOrBundle and surfaceOrBundle.surface then
            lua_temp_8 = surfaceOrBundle.surface
        else
            lua_temp_8 = surfaceOrBundle
        end
        local surface = lua_temp_8
        if not surface then
            error(
                LuaConstruct(Error, "[TERR] create(): native returned null surface"),
                0
            )
        end
        if materialH ~= nil then
            self.p:material(surface, materialH)
        end
        if uvCfg ~= nil then
            self.p:uv(surface, uvCfg)
        end
        if lodCfg ~= nil then
            self.p:lod(surface, lodCfg)
        end
        if kind ~= "plane" and kind ~= "quad" then
            if LuaNumberIsFinite(xz) and xz ~= 1 then
                self.p:scale(surface, xz, {yScale = y})
            elseif LuaNumberIsFinite(y) and y ~= 1 then
                self.p:scale(surface, 1, {yScale = y})
            end
        end
        return LuaConstruct(TerrainInstance, self.p, surface)
    end
    if kind == "plane" then
        local lua_temp_10 = {}
        local lua_isObj_result_9
        if isObj(_G, c.plane) then
            lua_isObj_result_9 = c.plane
        else
            lua_isObj_result_9 = {}
        end
        local planeCfg = LuaTableMerge(lua_temp_10, lua_isObj_result_9, {name = c.name, attach = attachFlag, physics = physCfg})
        return post(
            _G,
            self.p:plane(planeCfg)
        )
    end
    if kind == "quad" then
        local lua_temp_12 = {}
        local lua_isObj_result_11
        if isObj(_G, c.quad) then
            lua_isObj_result_11 = c.quad
        else
            lua_isObj_result_11 = {}
        end
        local quadCfg = LuaTableMerge(lua_temp_12, lua_isObj_result_11, {name = c.name, attach = attachFlag, physics = physCfg})
        return post(
            _G,
            self.p:quad(quadCfg)
        )
    end
    if kind == "heightmap" then
        local lua_temp_14 = {}
        local lua_isObj_result_13
        if isObj(_G, c.terrain) then
            lua_isObj_result_13 = c.terrain
        else
            lua_isObj_result_13 = {}
        end
        local tcfg = LuaTableMerge(lua_temp_14, lua_isObj_result_13, {name = c.name, attach = attachFlag, physics = physCfg})
        if c.heightmap and not tcfg.heightmap then
            tcfg.heightmap = c.heightmap
        end
        if tcfg.heightScale == nil and LuaNumberIsFinite(y) then
            tcfg.heightScale = y
        end
        if tcfg.xzScale == nil and LuaNumberIsFinite(xz) then
            tcfg.xzScale = xz
        end
        return post(
            _G,
            self.p:terrain(tcfg)
        )
    end
    if kind == "noise" then
        local lua_isObj_result_15
        if isObj(_G, c.noise) then
            lua_isObj_result_15 = c.noise
        else
            lua_isObj_result_15 = {}
        end
        local noise = lua_isObj_result_15
        local lua_type = tostring(noise.type or "perlin")
        local lua_i32_18 = i32
        local lua_G_17 = _G
        local lua_isObj_result_16
        if isObj(_G, c.terrain) then
            lua_isObj_result_16 = c.terrain.size
        else
            lua_isObj_result_16 = c.size
        end
        local size = lua_i32_18(
            lua_G_17,
            lua_isObj_result_16,
            i32(_G, noise.size, 513)
        ) or 513
        local lua_i32_21 = i32
        local lua_G_20 = _G
        local lua_isObj_result_19
        if isObj(_G, c.terrain) then
            lua_isObj_result_19 = c.terrain.patchSize
        else
            lua_isObj_result_19 = c.patchSize
        end
        local patchSize = lua_i32_21(lua_G_20, lua_isObj_result_19, 65) or 65
        TerrainHeights:validateTerrainDims(size, patchSize)
        local lua_temp_22
        if lua_type == "ridged" then
            lua_temp_22 = self.heights:ridged(LuaTableMerge({}, noise, {size = size}))
        else
            lua_temp_22 = self.heights:perlin(LuaTableMerge({}, noise, {size = size}))
        end
        local raw = lua_temp_22
        local lua_temp_23
        if noise.normalize == nil then
            lua_temp_23 = true
        else
            lua_temp_23 = not not noise.normalize
        end
        local normalize = lua_temp_23
        local out = {}
        if normalize then
            do
                local i = 0
                while i < KLength(raw) do
                    KSetIndex(out, i, (KIndex(raw, i) * 2 - 1) * y)
                    i = i + 1
                end
            end
        else
            do
                local i = 0
                while i < KLength(raw) do
                    KSetIndex(out, i, KIndex(raw, i) * y)
                    i = i + 1
                end
            end
        end
        local lua_isObj_result_24
        if isObj(_G, c.terrain) then
            lua_isObj_result_24 = c.terrain
        else
            lua_isObj_result_24 = {}
        end
        local tcfg0 = lua_isObj_result_24
        local tcfgNoPhys = LuaTableMerge({}, tcfg0, {
            name = c.name,
            size = size,
            patchSize = patchSize,
            heights = out,
            attach = attachFlag
        })
        local surface = self.terr:terrainHeights(tcfgNoPhys)
        local inst = post(_G, surface)
        if physCfg ~= nil then
            self.phys:withBody(self.terr, inst.surface, physCfg, "dynamicMesh")
        end
        return inst
    end
    if kind == "heights" then
        local heightsIn = c.heights
        if not heightsIn then
            error(
                LuaConstruct(Error, "[TERR] create(kind='heights'): cfg.heights is required"),
                0
            )
        end
        local heights = TerrainHeights:toFloatArray(heightsIn)
        local lua_isObj_result_25
        if isObj(_G, c.terrain) then
            lua_isObj_result_25 = c.terrain
        else
            lua_isObj_result_25 = {}
        end
        local tcfg0 = lua_isObj_result_25
        local size = i32(
            _G,
            tcfg0.size,
            i32(_G, c.size, 0)
        ) or TerrainHeights:inferSizeFromHeights(heights)
        local patchSize = i32(
            _G,
            tcfg0.patchSize,
            i32(_G, c.patchSize, 0)
        )
        if size > 0 then
            TerrainHeights:validateTerrainDims(size, patchSize or 0)
            TerrainHeights:assertHeightsMatchSize(heights, size, "create(kind='heights')")
        end
        local tcfgNoPhys = LuaTableMerge({}, tcfg0, {
            name = c.name,
            heights = heights,
            size = size or nil,
            patchSize = patchSize or nil,
            attach = attachFlag
        })
        local surface = self.terr:terrainHeights(tcfgNoPhys)
        local inst = post(_G, surface)
        if physCfg ~= nil then
            self.phys:withBody(self.terr, inst.surface, physCfg, "dynamicMesh")
        end
        return inst
    end
    local lua_G_30 = _G
    local lua_self_28 = self.p
    local lua_self_28_terrain_29 = lua_self_28.terrain
    local lua_temp_27 = {}
    local lua_isObj_result_26
    if isObj(_G, c.terrain) then
        lua_isObj_result_26 = c.terrain
    else
        lua_isObj_result_26 = c
    end
    return post(
        lua_G_30,
        lua_self_28_terrain_29(
            lua_self_28,
            LuaTableMerge(lua_temp_27, lua_isObj_result_26)
        )
    )
end
M = {TerrainCreateHelper = TerrainCreateHelper}

return M
