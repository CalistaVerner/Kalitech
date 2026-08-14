local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local Error = luaRuntime.Error
local LuaConstruct = luaRuntime.LuaConstruct
local LuaClass = luaRuntime.LuaClass
local LuaNumberIsFinite = luaRuntime.LuaNumberIsFinite
local LuaNumber = luaRuntime.LuaNumber
local LuaDefineProperty = luaRuntime.LuaDefineProperty
U = require("./util.lua")
FrameContext = require("./FrameContext.lua")
CharacterConfig = require("./CharacterConfig.lua")
local lua_require_result_0 = require("./PlayerEntityFactory.lua")
PlayerEntityFactory = lua_require_result_0.PlayerEntityFactory
InputRouter = require("./systems/InputRouter.lua")
function req(self, v, msg)
    if v == nil then
        error(
            LuaConstruct(Error, msg),
            0
        )
    end
    return v
end
function apiFrom(self, ctx)
    if ctx and KTypeOf(ctx.api) == "function" then
        return ctx:api()
    end
    if ctx and ctx.engine and KTypeOf(ctx.engine.api) == "function" then
        return ctx.engine:api()
    end
    if ctx and KTypeOf(ctx.engineApi) == "function" then
        return ctx:engineApi()
    end
    error(
        LuaConstruct(Error, "[player] ctx must provide api()"),
        0
    )
end
function buildDomains(self, ctx)
    local E = apiFrom(_G, ctx)
    local ENGINE = req(_G, _G.ENGINE, "[player] _G.ENGINE is required")
    local physics = req(_G, ENGINE.physics, "[player] ENGINE.physics is required")
    local input = req(
        _G,
        E.input and E:input(),
        "[player] engine.input() required"
    )
    local camera = req(
        _G,
        E.camera and E:camera(),
        "[player] engine.camera() required"
    )
    local assets = req(
        _G,
        E.assets and E:assets(),
        "[player] engine.assets() required"
    )
    local entity = req(
        _G,
        E.entity and E:entity(),
        "[player] engine.entity() required"
    )
    local mesh = req(
        _G,
        E.mesh and E:mesh(),
        "[player] engine.mesh() required"
    )
    local surface = req(
        _G,
        E.surface and E:surface(),
        "[player] engine.surface() required"
    )
    local hud = req(_G, ENGINE.hud, "[player] ENGINE.hud module required")
    local lua_temp_4
    if KTypeOf(E.hud) == "function" then
        lua_temp_4 = E:hud()
    else
        lua_temp_4 = nil
    end
    local hudNative = lua_temp_4
    local lua_temp_5
    if KTypeOf(E.bus) == "function" then
        lua_temp_5 = E:bus()
    else
        lua_temp_5 = nil
    end
    local bus = lua_temp_5
    return KObject:freeze({
        ctx = ctx,
        engine = E,
        physics = physics,
        input = input,
        camera = camera,
        assets = assets,
        entity = entity,
        mesh = mesh,
        surface = surface,
        bus = bus,
        hud = hud,
        hudNative = hudNative
    })
end
function readUuidFromHandle(self, h)
    if not h then
        return ""
    end
    if KTypeOf(h.uuidString) == "function" then
        return tostring(h:uuidString() or "")
    end
    if KTypeOf(h.uuid) == "function" then
        return tostring(h:uuid() or "")
    end
    if KTypeOf(h.uuid) == "string" then
        return tostring(h.uuid or "")
    end
    return ""
end
PlayerPawn = LuaClass()
PlayerPawn.name = "PlayerPawn"
function PlayerPawn.prototype.lua_constructor(self, ctx, cfg)
    self.ctx = req(_G, ctx, "[PlayerPawn] ctx required")
    self.cfg = cfg or KObject:create(nil)
    self.d = nil
    self.characterCfg = LuaConstruct(CharacterConfig)
    self.frame = LuaConstruct(FrameContext)
    self.inputRouter = nil
    self.handle = nil
    self.core = nil
    self.alive = false
    ENGINE.input:grabMouse(true)
end
function PlayerPawn.prototype.init(self)
    if self.alive then
        return self
    end
    self.cfg = U:deepMerge({
        character = {radius = 0.35, height = 1.8, mass = 80, eyeHeight = 1.65},
        spawn = {pos = {x = 135, y = -10, z = -334}, radius = 0.35, height = 1.8, mass = 80},
        camera = {type = "third"},
        ui = {},
        events = {enabled = true},
        input = {},
        movement = {},
        shoot = {}
    }, self.cfg)
    self.d = buildDomains(_G, self.ctx)
    self.inputRouter = LuaConstruct(InputRouter, self.d.input, self.cfg.input)
    local factory = LuaConstruct(PlayerEntityFactory, self)
    self.handle = factory:create(self.cfg.spawn)
    local uuid = self.uuid
    if not uuid then
        error(
            LuaConstruct(Error, "[PlayerPawn] player uuid missing (UUID-only)"),
            0
        )
    end
    self.core = self.handle.core
    if not self.core then
        error(
            LuaConstruct(Error, "[PlayerPawn] ENGINE.entity.create() must return {core}"),
            0
        )
    end
    if not self.core.bodyAccess then
        error(
            LuaConstruct(Error, "[PlayerPawn] core.bodyAccess missing (engine must fill EntityCore)"),
            0
        )
    end
    if bit32.bor(self.core.bodyId, 0) <= 0 then
        error(
            LuaConstruct(Error, "[PlayerPawn] invalid core.bodyId"),
            0
        )
    end
    self.core.uuid = uuid
    if KTypeOf(self.frame.probeGroundCapsule) ~= "function" then
        error(
            LuaConstruct(Error, "[PlayerPawn] FrameContext.probeGroundCapsule required"),
            0
        )
    end
    self.characterCfg:loadFrom(self.cfg, self.cfg.movement)
    self.core:setGroundProbe(function(lua_, core)
        local probe = self.frame.probeGroundCapsule
        return KFunction:call(
            probe,
            self.frame,
            core.bodyAccess,
            self.characterCfg,
            bit32.bor(core.bodyId, 0)
        )
    end)
    self.alive = true
    return self
end
function PlayerPawn.prototype.beginFrame(self, tpf)
    if not self.alive then
        error(
            LuaConstruct(Error, "[PlayerPawn] beginFrame on dead pawn"),
            0
        )
    end
    if not LuaNumberIsFinite(tpf) then
        error(
            LuaConstruct(Error, "[PlayerPawn] tpf must be finite"),
            0
        )
    end
    local snap = self.d.input:consumeSnapshot()
    self.frame:begin(self, tpf, snap)
    self.inputRouter:read(self.frame)
    self.frame.bodyAccess = self.core.bodyAccess
    self.frame.bodyId = bit32.bor(self.core.bodyId, 0)
end
function PlayerPawn.prototype.syncPose(self)
    if not self.alive then
        error(
            LuaConstruct(Error, "[PlayerPawn] syncPose on dead pawn"),
            0
        )
    end
    local s = self.core:syncPhysics()
    local pose = self.frame.pose
    pose.x = s.x
    pose.y = s.y
    pose.z = s.z
    pose.vx = s.vx
    pose.vy = s.vy
    pose.vz = s.vz
    pose.speed = s.speed
    local lua_temp_6
    if s.vy < 0 then
        lua_temp_6 = LuaNumber(-s.vy)
    else
        lua_temp_6 = 0
    end
    pose.fallSpeed = lua_temp_6
    pose.rx = s.rx
    pose.ry = s.ry
    pose.rz = s.rz
    pose.rw = s.rw
    pose.avx = s.avx
    pose.avy = s.avy
    pose.avz = s.avz
    pose.grounded = s.grounded
end
function PlayerPawn.prototype.endFrame(self)
    if not self.alive then
        error(
            LuaConstruct(Error, "[PlayerPawn] endFrame on dead pawn"),
            0
        )
    end
    self.d.input:endFrame()
end
function PlayerPawn.prototype.destroy(self)
end
function PlayerPawn.prototype.getModel(self)
    return self.core:model()
end
function PlayerPawn.prototype.getBodyId(self)
    return bit32.bor(self.core.bodyId, 0)
end
function PlayerPawn.prototype.getSurfaceId(self)
    return bit32.bor(self.core.surfaceId, 0)
end
function PlayerPawn.prototype.getUuid(self)
    return self.uuid
end
LuaDefineProperty(
    PlayerPawn.prototype,
    "entity",
    {get = function(self)
        return self.handle
    end},
    true
)
LuaDefineProperty(
    PlayerPawn.prototype,
    "uuid",
    {get = function(self)
        return readUuidFromHandle(_G, self.handle)
    end},
    true
)
LuaDefineProperty(
    PlayerPawn.prototype,
    "bodyAccess",
    {get = function(self)
        return self.core.bodyAccess
    end},
    true
)
LuaDefineProperty(
    PlayerPawn.prototype,
    "bodyId",
    {get = function(self)
        return bit32.bor(self.core.bodyId, 0)
    end},
    true
)
LuaDefineProperty(
    PlayerPawn.prototype,
    "surfaceId",
    {get = function(self)
        return bit32.bor(self.core.surfaceId, 0)
    end},
    true
)
LuaDefineProperty(
    PlayerPawn.prototype,
    "state",
    {get = function(self)
        return self.core.state
    end},
    true
)
M = {PlayerPawn = PlayerPawn}

return M
