local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local LuaClass = luaRuntime.LuaClass
local Error = luaRuntime.Error
local LuaConstruct = luaRuntime.LuaConstruct
TerrainInstance = LuaClass()
TerrainInstance.name = "TerrainInstance"
function TerrainInstance.prototype.lua_constructor(self, api, surface)
    if not api then
        error(
            LuaConstruct(Error, "[TERR] api is required"),
            0
        )
    end
    if not surface then
        error(
            LuaConstruct(Error, "[TERR] surface handle is required"),
            0
        )
    end
    self._api = api
    self.surface = surface
    KObject:freeze(self)
end
function TerrainInstance.prototype.heightAt(self, x, z, world)
    if world == nil then
        world = true
    end
    return self._api:heightAt(self.surface, x, z, world)
end
function TerrainInstance.prototype.normalAt(self, x, z, world)
    if world == nil then
        world = true
    end
    return self._api:normalAt(self.surface, x, z, world)
end
function TerrainInstance.prototype.setHeight(self, x, z, h, world)
    if world == nil then
        world = true
    end
    return self._api:setHeight(
        self.surface,
        x,
        z,
        h,
        world
    )
end
function TerrainInstance.prototype.adjustHeight(self, x, z, delta, world)
    if world == nil then
        world = true
    end
    return self._api:adjustHeight(
        self.surface,
        x,
        z,
        delta,
        world
    )
end
function TerrainInstance.prototype.setHeightmap(self, heights, size, rebuild)
    if rebuild == nil then
        rebuild = true
    end
    return self._api:setHeightmap(self.surface, heights, size, rebuild)
end
function TerrainInstance.prototype.heightmap(self)
    return self._api:heightmap(self.surface)
end
function TerrainInstance.prototype.rebuild(self)
    return self._api:rebuild(self.surface)
end
function TerrainInstance.prototype.material(self, mat)
    return self._api:material(self.surface, mat)
end
function TerrainInstance.prototype.uv(self, cfg)
    return self._api:uv(self.surface, cfg)
end
function TerrainInstance.prototype.lod(self, cfg)
    return self._api:lod(self.surface, cfg)
end
function TerrainInstance.prototype.scale(self, xz, cfg)
    return self._api:scale(self.surface, xz, cfg)
end
function TerrainInstance.prototype.attachEntity(self, entityUuid)
    return self._api:attachEntity(self.surface, entityUuid)
end
function TerrainInstance.prototype.attach(self, entityUuid)
    return self:attachEntity(entityUuid)
end
function TerrainInstance.prototype.detach(self)
    return self._api:detach(self.surface)
end
M = {TerrainInstance = TerrainInstance}

return M
