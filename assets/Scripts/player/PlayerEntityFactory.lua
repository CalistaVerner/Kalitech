local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local LuaNumber = luaRuntime.LuaNumber
local LuaNumberIsFinite = luaRuntime.LuaNumberIsFinite
local LuaClass = luaRuntime.LuaClass
local Error = luaRuntime.Error
local LuaConstruct = luaRuntime.LuaConstruct
function num(self, v, fb)
    v = LuaNumber(v)
    local lua_Number_isFinite_result_0
    if LuaNumberIsFinite(v) then
        lua_Number_isFinite_result_0 = v
    else
        lua_Number_isFinite_result_0 = fb
    end
    return lua_Number_isFinite_result_0
end
function v3(self, pos, fb)
    local p = pos or fb or ({x = 0, y = 3, z = 0})
    return {
        x = num(_G, p.x, 0),
        y = num(_G, p.y, 3),
        z = num(_G, p.z, 0)
    }
end
function bool(self, v, fb)
    local lua_temp_1
    if v ~= nil then
        lua_temp_1 = not not v
    else
        lua_temp_1 = not not fb
    end
    return lua_temp_1
end
PlayerEntityFactory = LuaClass()
PlayerEntityFactory.name = "PlayerEntityFactory"
function PlayerEntityFactory.prototype.lua_constructor(self, player)
    self.player = player or nil
end
function PlayerEntityFactory.prototype.create(self, spawnCfg)
    local player = self.player
    if not player or not player.d then
        error(
            LuaConstruct(Error, "[player] factory requires player with domains"),
            0
        )
    end
    local cfg = spawnCfg or player.cfg and player.cfg.spawn or KObject:create(nil)
    local radius = num(_G, cfg.radius, 0.35)
    local height = num(_G, cfg.height, 1.8)
    local mass = num(_G, cfg.mass, 80)
    local pos = v3(_G, cfg.pos, {x = 0, y = 3, z = 0})
    if not ENGINE.entity or KTypeOf(ENGINE.entity.create) ~= "function" then
        error(
            LuaConstruct(Error, "[player] ENGINE.entity.create(cfg) required (engine Entity module)"),
            0
        )
    end
    local lua_entityModule_8 = ENGINE.entity
    local lua_entityModule_create_9 = ENGINE.entity.create
    local lua_temp_2
    if cfg.name ~= nil then
        lua_temp_2 = tostring(cfg.name)
    else
        lua_temp_2 = "player"
    end
    local lua_temp_7 = {
        type = "capsule",
        name = "player.surface",
        radius = radius,
        height = height,
        pos = pos,
        attach = true
    }
    local lua_mass_6 = mass
    local lua_temp_3
    if cfg.friction ~= nil then
        lua_temp_3 = num(_G, cfg.friction, 0.9)
    else
        lua_temp_3 = 0.9
    end
    local lua_temp_4
    if cfg.restitution ~= nil then
        lua_temp_4 = num(_G, cfg.restitution, 0)
    else
        lua_temp_4 = 0
    end
    local lua_temp_5
    if cfg.damping ~= nil then
        lua_temp_5 = cfg.damping
    else
        lua_temp_5 = {linear = 0.15, angular = 0.95}
    end
    local pack = lua_entityModule_create_9(
        lua_entityModule_8,
        {
            name = lua_temp_2,
            requireCore = true,
            surface = lua_temp_7,
            body = {
                mass = lua_mass_6,
                lockRotation = false,
                friction = lua_temp_3,
                restitution = lua_temp_4,
                damping = lua_temp_5,
                collider = {type = "capsule", radius = radius, height = height}
            },
            components = {Player = function(lua_, ctx) return {
                uuid = ctx.uuid,
                surfaceId = bit32.bor(ctx.surfaceId, 0),
                bodyId = bit32.bor(ctx.bodyId, 0),
                capsule = {radius = radius, height = height, mass = mass}
            } end}
        }
    )
    if not pack or not pack.core then
        error(
            LuaConstruct(Error, "[player] ENGINE.entity.create() must return {core}"),
            0
        )
    end
    local core = pack.core
    if KTypeOf(core.uuid) ~= "string" or not core.uuid then
        error(
            LuaConstruct(Error, "[player] ENGINE.entity.create() must return core.uuid (UUID-only)"),
            0
        )
    end
    if bit32.bor(core.bodyId, 0) <= 0 then
        error(
            LuaConstruct(Error, "[player] ENGINE.entity.create() returned invalid core.bodyId"),
            0
        )
    end
    if not core.bodyAccess then
        error(
            LuaConstruct(Error, "[player] ENGINE.entity.create() must provide core.bodyAccess (engine-filled)"),
            0
        )
    end
    if pack.handle and not pack.handle.core then
        pack.handle.core = core
    end
    return pack
end
M = {PlayerEntityFactory = PlayerEntityFactory}

return M
