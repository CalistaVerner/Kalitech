local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local LuaClass = luaRuntime.LuaClass
U = require("./util.lua")
function readPath(self, obj, path)
    local o = obj
    do
        local i = 0
        while i < KLength(path) do
            if not o or KTypeOf(o) ~= "table" then
                return nil
            end
            o = o[KIndex(path, i)]
            i = i + 1
        end
    end
    return o
end
function readNum(self, obj, path, fb)
    local v = readPath(_G, obj, path)
    local lua_temp_0
    if v == nil or v == nil then
        lua_temp_0 = fb
    else
        lua_temp_0 = U:num(v, fb)
    end
    return lua_temp_0
end
function readBool(self, obj, path, fb)
    local v = readPath(_G, obj, path)
    local lua_temp_1
    if v == nil or v == nil then
        lua_temp_1 = fb
    else
        lua_temp_1 = not not v
    end
    return lua_temp_1
end
CharacterConfig = LuaClass()
CharacterConfig.name = "CharacterConfig"
function CharacterConfig.prototype.lua_constructor(self)
    self.radius = 0.35
    self.height = 1.8
    self.eyeHeight = 1.65
    self.groundRay = 1.15
    self.groundStart = 0.2
    self.groundEps = 0.08
    self.maxSlopeDot = 0.55
    self.probeRing = 0.65
    self.stepUp = {
        enabled = true,
        maxHeight = 0.4,
        minHeight = 0.04,
        forwardProbe = 0.35,
        upProbe = 0.6,
        snapUpSpeed = 28,
        minClearNormalY = 0.25,
        warpCooldown = 0.07
    }
    self.stepDown = {enabled = true, max = 0.28, stickVel = 1.6, deadZone = 0.015}
end
function CharacterConfig.prototype.loadFrom(self, playerCfg, movementCfg)
    local lua_temp_2
    if playerCfg and playerCfg.character then
        lua_temp_2 = playerCfg.character
    else
        lua_temp_2 = nil
    end
    local ch = lua_temp_2
    local lua_temp_3
    if playerCfg and playerCfg.spawn then
        lua_temp_3 = playerCfg.spawn
    else
        lua_temp_3 = nil
    end
    local sp = lua_temp_3
    local lua_temp_5
    if ch and ch.height ~= nil then
        lua_temp_5 = U:num(ch.height, 1.8)
    else
        local lua_temp_4
        if sp and sp.height ~= nil then
            lua_temp_4 = U:num(sp.height, 1.8)
        else
            lua_temp_4 = 1.8
        end
        lua_temp_5 = lua_temp_4
    end
    local h = lua_temp_5
    local lua_temp_7
    if ch and ch.radius ~= nil then
        lua_temp_7 = U:num(ch.radius, 0.35)
    else
        local lua_temp_6
        if sp and sp.radius ~= nil then
            lua_temp_6 = U:num(sp.radius, 0.35)
        else
            lua_temp_6 = 0.35
        end
        lua_temp_7 = lua_temp_6
    end
    local r = lua_temp_7
    local ehDefault = math.min(h * 0.92, h - 0.08)
    local lua_temp_8
    if ch and ch.eyeHeight ~= nil then
        lua_temp_8 = U:num(ch.eyeHeight, ehDefault)
    else
        lua_temp_8 = ehDefault
    end
    local ehRaw = lua_temp_8
    self.height = math.max(0.8, h)
    self.radius = U:clamp(r, 0.1, 1.2)
    self.eyeHeight = U:clamp(ehRaw, 1.2, self.height - 0.05)
    local mc = movementCfg or KObject:create(nil)
    self.groundRay = readNum(_G, mc, {"ground", "rayLength"}, self.groundRay)
    self.groundStart = readNum(_G, mc, {"ground", "startUp"}, self.groundStart)
    self.groundEps = readNum(_G, mc, {"ground", "eps"}, self.groundEps)
    self.maxSlopeDot = U:clamp(
        readNum(_G, mc, {"ground", "maxSlopeDot"}, self.maxSlopeDot),
        0,
        1
    )
    self.probeRing = U:clamp(
        readNum(_G, mc, {"ground", "probeRing"}, self.probeRing),
        0.1,
        1.2
    )
    self.stepUp.enabled = readBool(_G, mc, {"stepUp", "enabled"}, self.stepUp.enabled)
    self.stepUp.maxHeight = readNum(_G, mc, {"stepUp", "maxHeight"}, self.stepUp.maxHeight)
    self.stepUp.minHeight = math.max(
        0,
        readNum(_G, mc, {"stepUp", "minHeight"}, self.stepUp.minHeight)
    )
    self.stepUp.forwardProbe = readNum(_G, mc, {"stepUp", "forwardProbe"}, self.stepUp.forwardProbe)
    self.stepUp.upProbe = readNum(_G, mc, {"stepUp", "upProbe"}, self.stepUp.upProbe)
    self.stepUp.snapUpSpeed = readNum(_G, mc, {"stepUp", "snapUpSpeed"}, self.stepUp.snapUpSpeed)
    self.stepUp.minClearNormalY = readNum(_G, mc, {"stepUp", "minClearNormalY"}, self.stepUp.minClearNormalY)
    self.stepUp.warpCooldown = U:clamp(
        readNum(_G, mc, {"stepUp", "warpCooldown"}, self.stepUp.warpCooldown),
        0,
        1
    )
    self.stepDown.enabled = readBool(_G, mc, {"stepDown", "enabled"}, self.stepDown.enabled)
    self.stepDown.max = readNum(_G, mc, {"stepDown", "max"}, self.stepDown.max)
    self.stepDown.stickVel = readNum(_G, mc, {"stepDown", "stickVel"}, self.stepDown.stickVel)
    self.stepDown.deadZone = readNum(_G, mc, {"stepDown", "deadZone"}, self.stepDown.deadZone)
    return self
end
M = CharacterConfig

return M
