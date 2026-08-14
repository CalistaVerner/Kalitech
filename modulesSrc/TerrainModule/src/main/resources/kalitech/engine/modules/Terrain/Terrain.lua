local luaRuntime = require("@builtin/lua_runtime")
local Error = luaRuntime.Error
local LuaConstruct = luaRuntime.LuaConstruct
local LuaTableMerge = luaRuntime.LuaTableMerge
local LuaTableRemove = luaRuntime.LuaTableRemove
META = KObject:freeze({
    moduleId = "terrain",
    version = "2.0.0",
    engineMin = "0.1.0",
    description = "Declarative terrain builder (plane/quad/heightmap/heights/noise) + physics + edit/query"
})
local lua_require_result_0 = require("./helpers/TerrainTypes.lua")
isObj = lua_require_result_0.isObj
num = lua_require_result_0.num
i32 = lua_require_result_0.i32
local lua_require_result_1 = require("./helpers/TerrainHeights.lua")
TerrainHeights = lua_require_result_1.TerrainHeights
local lua_require_result_2 = require("./helpers/TerrainPhysics.lua")
TerrainPhysics = lua_require_result_2.TerrainPhysics
local lua_require_result_3 = require("./helpers/TerrainCreateHelper.lua")
TerrainCreateHelper = lua_require_result_3.TerrainCreateHelper
function makeApi(self, engine)
    if not engine then
        error(
            LuaConstruct(Error, "[TERR] engine is required"),
            0
        )
    end
    local lua_temp_4
    if engine.terrain and KTypeOf(engine.terrain) == "function" then
        lua_temp_4 = engine:terrain()
    else
        lua_temp_4 = nil
    end
    local terr = lua_temp_4
    if not terr then
        error(
            LuaConstruct(Error, "[TERR] engine.terrain() is not available"),
            0
        )
    end
    local heightsApi = LuaConstruct(TerrainHeights, terr)
    local physicsApi = LuaConstruct(TerrainPhysics, engine)
    local function terrain(self, cfg)
        local lua_isObj_result_5
        if isObj(_G, cfg) then
            lua_isObj_result_5 = LuaTableMerge({}, cfg)
        else
            lua_isObj_result_5 = {}
        end
        local c = lua_isObj_result_5
        local physCfg = c.physics
        if physCfg ~= nil then
            LuaTableRemove(c, "physics")
        end
        if c.size or c.patchSize then
            TerrainHeights:validateTerrainDims(
                i32(_G, c.size, 0),
                i32(_G, c.patchSize, 0)
            )
        end
        local surface = terr:terrain(c)
        return physicsApi:withBody(terr, surface, physCfg, "mesh")
    end
    local function terrainHeights(self, cfg)
        local lua_isObj_result_6
        if isObj(_G, cfg) then
            lua_isObj_result_6 = LuaTableMerge({}, cfg)
        else
            lua_isObj_result_6 = {}
        end
        local c = lua_isObj_result_6
        local physCfg = c.physics
        if physCfg ~= nil then
            LuaTableRemove(c, "physics")
        end
        local heights = c.heights
        if heights ~= nil then
            c.heights = TerrainHeights:toFloatArray(heights)
        end
        local size = i32(_G, c.size, 0) or TerrainHeights:inferSizeFromHeights(c.heights)
        if size > 0 then
            c.size = size
        end
        if c.size or c.patchSize then
            TerrainHeights:validateTerrainDims(
                i32(_G, c.size, 0),
                i32(_G, c.patchSize, 0)
            )
        end
        TerrainHeights:assertHeightsMatchSize(c.heights, c.size, "terrainHeights")
        local surface = terr:terrainHeights(c)
        return physicsApi:withBody(terr, surface, physCfg, "mesh")
    end
    local function quad(self, cfg)
        local lua_isObj_result_7
        if isObj(_G, cfg) then
            lua_isObj_result_7 = LuaTableMerge({}, cfg)
        else
            lua_isObj_result_7 = {}
        end
        local c = lua_isObj_result_7
        local physCfg = c.physics
        if physCfg ~= nil then
            LuaTableRemove(c, "physics")
        end
        local surface = terr:quad(c)
        return physicsApi:withBody(terr, surface, physCfg, "mesh")
    end
    local function plane(self, cfg)
        local lua_isObj_result_8
        if isObj(_G, cfg) then
            lua_isObj_result_8 = LuaTableMerge({}, cfg)
        else
            lua_isObj_result_8 = {}
        end
        local c = lua_isObj_result_8
        local physCfg = c.physics
        if physCfg ~= nil then
            LuaTableRemove(c, "physics")
        end
        local surface = terr:plane(c)
        return physicsApi:withBody(terr, surface, physCfg, "mesh")
    end
    local function physics(self, surfaceHandleOrId, cfg)
        if not surfaceHandleOrId then
            error(
                LuaConstruct(Error, "TERR.physics(surface,cfg): surface handle/id required"),
                0
            )
        end
        return physicsApi:withBody(terr, surfaceHandleOrId, cfg or ({}), "mesh")
    end
    local function material(self, surfaceHandle, materialHandleOrCfg)
        return terr:material(surfaceHandle, materialHandleOrCfg)
    end
    local function uv(self, surfaceHandle, cfg)
        return terr:uv(surfaceHandle, cfg)
    end
    local function lod(self, surfaceHandle, cfg)
        return terr:lod(surfaceHandle, cfg or ({}))
    end
    local function scale(self, surfaceHandle, xzScale, cfg)
        return terr:scale(
            surfaceHandle,
            num(_G, xzScale, 1),
            cfg or nil
        )
    end
    local function heightAt(self, surfaceHandle, x, z, world)
        if world == nil then
            return terr:heightAt(
                surfaceHandle,
                num(_G, x, 0),
                num(_G, z, 0)
            )
        end
        return terr:heightAt(
            surfaceHandle,
            num(_G, x, 0),
            num(_G, z, 0),
            not not world
        )
    end
    local function normalAt(self, surfaceHandle, x, z, world)
        if world == nil then
            return terr:normalAt(
                surfaceHandle,
                num(_G, x, 0),
                num(_G, z, 0)
            )
        end
        return terr:normalAt(
            surfaceHandle,
            num(_G, x, 0),
            num(_G, z, 0),
            not not world
        )
    end
    local function setHeightmap(self, surfaceHandle, heights, size, rebuild)
        if isObj(_G, heights) then
            return terr:setHeightmap(surfaceHandle, heights)
        end
        local h = TerrainHeights:toFloatArray(heights)
        local s = bit32.bor(size, 0) or TerrainHeights:inferSizeFromHeights(h)
        if s > 0 then
            TerrainHeights:assertHeightsMatchSize(h, s, "setHeightmap")
        end
        local lua_terr_setHeightmap_13 = terr.setHeightmap
        local lua_surfaceHandle_12 = surfaceHandle
        local lua_temp_11 = s or nil
        local lua_temp_10
        if rebuild == nil then
            lua_temp_10 = true
        else
            lua_temp_10 = not not rebuild
        end
        return lua_terr_setHeightmap_13(terr, lua_surfaceHandle_12, {heights = h, size = lua_temp_11, rebuild = lua_temp_10})
    end
    local function heightmap(self, surfaceHandle)
        return TerrainHeights:toFloatArray(terr:heightmap(surfaceHandle))
    end
    local function setHeight(self, surfaceHandle, x, z, height, world)
        if world == nil then
            return terr:setHeight(
                surfaceHandle,
                num(_G, x, 0),
                num(_G, z, 0),
                num(_G, height, 0)
            )
        end
        return terr:setHeight(
            surfaceHandle,
            num(_G, x, 0),
            num(_G, z, 0),
            num(_G, height, 0),
            not not world
        )
    end
    local function adjustHeight(self, surfaceHandle, x, z, delta, world)
        if world == nil then
            return terr:adjustHeight(
                surfaceHandle,
                num(_G, x, 0),
                num(_G, z, 0),
                num(_G, delta, 0)
            )
        end
        return terr:adjustHeight(
            surfaceHandle,
            num(_G, x, 0),
            num(_G, z, 0),
            num(_G, delta, 0),
            not not world
        )
    end
    local function rebuild(self, surfaceHandle)
        return terr:rebuild(surfaceHandle)
    end
    local function attachEntity(self, surfaceHandle, entityUuid)
        return terr:attachEntity(surfaceHandle, entityUuid)
    end
    local function attach(self, surfaceHandle, entityUuid)
        return attachEntity(_G, surfaceHandle, entityUuid)
    end
    local function detach(self, surfaceHandle)
        return terr:detach(surfaceHandle)
    end
    local heightsNS = KObject:freeze({
        perlin = function(lua_, cfg) return heightsApi:perlin(cfg or ({})) end,
        ridged = function(lua_, cfg) return heightsApi:ridged(cfg or ({})) end,
        sizeOf = TerrainHeights.inferSizeFromHeights,
        toArray = TerrainHeights.toFloatArray
    })
    local createHelper = LuaConstruct(
        TerrainCreateHelper,
        engine,
        terr,
        heightsApi,
        physicsApi,
        {
            terrain = terrain,
            terrainHeights = terrainHeights,
            quad = quad,
            plane = plane,
            physics = physics,
            material = material,
            uv = uv,
            lod = lod,
            scale = scale,
            heightAt = heightAt,
            normalAt = normalAt,
            setHeightmap = setHeightmap,
            heightmap = heightmap,
            setHeight = setHeight,
            adjustHeight = adjustHeight,
            rebuild = rebuild,
            attach = attach,
            attachEntity = attachEntity,
            detach = detach
        }
    )
    local function create(self, cfg)
        return createHelper:create(cfg)
    end
    return KObject:freeze({
        META = META,
        create = create,
        heights = heightsNS,
        terrain = terrain,
        terrainHeights = terrainHeights,
        quad = quad,
        plane = plane,
        physics = physics,
        material = material,
        uv = uv,
        lod = lod,
        scale = scale,
        heightAt = heightAt,
        normalAt = normalAt,
        setHeightmap = setHeightmap,
        heightmap = heightmap,
        setHeight = setHeight,
        adjustHeight = adjustHeight,
        rebuild = rebuild,
        attach = attach,
        attachEntity = attachEntity,
        detach = detach
    })
end
local moduleFactory = function(self, engine, K)
    return makeApi(_G, engine, K)
end
return setmetatable({META = META}, {
    __call = function(_, ...)
        return moduleFactory(...)
    end
})
