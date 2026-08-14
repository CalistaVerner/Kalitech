local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local Error = luaRuntime.Error
local LuaConstruct = luaRuntime.LuaConstruct
local LuaNumberIsFinite = luaRuntime.LuaNumberIsFinite
local LuaStringTrim = luaRuntime.LuaStringTrim
function fail(self, msg)
    error(
        LuaConstruct(Error, msg),
        0
    )
end
function isFn(self, x)
    return KTypeOf(x) == "function"
end
function isObj(self, x)
    return x and KTypeOf(x) == "table"
end
function req(self, v, msg)
    if v == nil then
        fail(_G, msg)
    end
    return v
end
function asBool(self, v, name)
    if KTypeOf(v) ~= "boolean" then
        fail(
            _G,
            ("[camera] " .. tostring(name)) .. " must be boolean"
        )
    end
    return v
end
function asInt(self, v, name)
    if not LuaNumberIsFinite(v) or bit32.bor(v, 0) ~= v then
        fail(
            _G,
            ("[camera] " .. tostring(name)) .. " must be int"
        )
    end
    return bit32.bor(v, 0)
end
function asStr(self, v, name)
    if KTypeOf(v) ~= "string" or not LuaStringTrim(v) then
        fail(
            _G,
            ("[camera] " .. tostring(name)) .. " must be non-empty string"
        )
    end
    return KString.trim(v)
end
function validatePlayer(self, player)
    req(_G, player, "[camera] player is required")
    if not isFn(_G, player.getBodyId) then
        fail(_G, "[camera] player.getBodyId() required")
    end
    local d = req(_G, player.d, "[camera] player.d is required")
    local cam = req(_G, d.camera, "[camera] player.d.camera is required")
    if not isFn(_G, cam.setYawPitch) then
        fail(_G, "[camera] d.camera.setYawPitch(yaw,pitch) required")
    end
    if not isFn(_G, cam.setLocation) then
        fail(_G, "[camera] d.camera.setLocation(x,y,z) required")
    end
    if not isFn(_G, cam.location) then
        fail(_G, "[camera] d.camera.location() required")
    end
    local ph = req(_G, d.physics, "[camera] player.d.physics is required")
    if not isFn(_G, ph.position) then
        fail(_G, "[camera] d.physics.position(bodyId) required")
    end
    local inp = req(_G, d.input, "[camera] player.d.input is required")
    if not isFn(_G, inp.keyCode) then
        fail(_G, "[camera] d.input.keyCode(key) required")
    end
    return true
end
function validateMeta(self, meta)
    if not isObj(_G, meta) then
        fail(_G, "[camera] mode.meta required")
    end
    local allowed = {supportsZoom = 1, hasCollision = 1, numRays = 1, playerModelVisible = 1}
    for k in pairs(meta) do
        if not allowed[k] then
            fail(_G, "[camera] mode.meta has unknown key: " .. k)
        end
    end
    return {
        supportsZoom = asBool(_G, meta.supportsZoom, "meta.supportsZoom"),
        hasCollision = asBool(_G, meta.hasCollision, "meta.hasCollision"),
        numRays = asInt(_G, meta.numRays, "meta.numRays"),
        playerModelVisible = asBool(_G, meta.playerModelVisible, "meta.playerModelVisible")
    }
end
function validateMode(self, mode)
    if not isObj(_G, mode) then
        fail(_G, "[camera] mode is null")
    end
    mode.id = asStr(_G, mode.id, "mode.id")
    mode.meta = validateMeta(_G, mode.meta)
    if not isFn(_G, mode.update) then
        fail(_G, "[camera] mode.update(ctx) required")
    end
    return mode
end
M = {validatePlayer = validatePlayer, validateMode = validateMode}

return M
