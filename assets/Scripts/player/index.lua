local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local Error = luaRuntime.Error
local LuaConstruct = luaRuntime.LuaConstruct
local lua_require_result_0 = require("./PlayerController.lua")
PlayerController = lua_require_result_0.PlayerController
local lua_require_result_1 = require("./PlayerControllers.lua")
createPlayerRegistry = lua_require_result_1.createPlayerRegistry
_player = nil
_registered = false
function getHotReloadDomain(self, ctx)
    if not ctx then
        return nil
    end
    do
        local lua_try, lua_hasReturned, lua_returnValue = pcall(function()
            if KTypeOf(ctx.hotReload) == "function" then
                return true, ctx:hotReload()
            end
        end)
        if lua_try and lua_hasReturned then
            return lua_returnValue
        end
    end
    do
        local lua_try, lua_hasReturned, lua_returnValue = pcall(function()
            if ctx.hotReload and KTypeOf(ctx.hotReload.register) == "function" then
                return true, ctx.hotReload
            end
        end)
        if lua_try and lua_hasReturned then
            return lua_returnValue
        end
    end
    return nil
end
function getStateDomain(self, ctx)
    if not ctx then
        return nil
    end
    do
        local lua_try, lua_hasReturned, lua_returnValue = pcall(function()
            if ctx.stateDomain and KTypeOf(ctx.stateDomain.get) == "function" then
                return true, ctx.stateDomain
            end
        end)
        if lua_try and lua_hasReturned then
            return lua_returnValue
        end
    end
    do
        local lua_try, lua_hasReturned, lua_returnValue = pcall(function()
            if KTypeOf(ctx.state) == "function" then
                return true, ctx:state()
            end
        end)
        if lua_try and lua_hasReturned then
            return lua_returnValue
        end
    end
    do
        local lua_try, lua_hasReturned, lua_returnValue = pcall(function()
            if ctx.state and KTypeOf(ctx.state.get) == "function" then
                return true, ctx.state
            end
        end)
        if lua_try and lua_hasReturned then
            return lua_returnValue
        end
    end
    return nil
end
function ensureHotReloadHook(self, ctx)
    local hr = getHotReloadDomain(_G, ctx)
    if not hr or KTypeOf(hr.register) ~= "function" then
        return false
    end
    local sd = getStateDomain(_G, ctx)
    local FLAG = "__player_hot_reload_hook__"
    if sd and KTypeOf(sd.get) == "function" and sd:get(FLAG) == true then
        return true
    end
    hr:register(function(lua_, reason)
        do
            pcall(function()
                M:destroy(ctx)
            end)
        end
    end)
    if sd and KTypeOf(sd.set) == "function" then
        sd:set(FLAG, true)
    end
    return true
end
function ensureRegistered(self, ctx)
    if _registered then
        return
    end
    local ENGINE = _G.ENGINE
    if not ENGINE or not ENGINE.controllers then
        error(
            LuaConstruct(Error, "[player] ENGINE.controllers required"),
            0
        )
    end
    if KTypeOf(ENGINE.controllers.registerRegistry) ~= "function" then
        error(
            LuaConstruct(Error, "[player] ENGINE.controllers.registerRegistry(registry) required"),
            0
        )
    end
    ensureHotReloadHook(_G, ctx)
    ENGINE.controllers:registerRegistry(createPlayerRegistry(_G))
    _registered = true
end
M.create = function(self, ctx, cfg)
    ensureRegistered(_G, ctx)
    return LuaConstruct(PlayerController, ctx, cfg or nil)
end
M.init = function(self, ctx, cfg)
    ensureRegistered(_G, ctx)
    if _player then
        return _player
    end
    _player = LuaConstruct(PlayerController, ctx, cfg or nil)
    return _player
end
M.update = function(self, ctx, tpf)
    if _player then
        _player:update(tpf)
    end
end
M.destroy = function(self, ctx)
    do
        pcall(function()
            if _player then
                _player:dispose()
            end
        end)
    end
    _player = nil
    _registered = false
end
M.PlayerController = PlayerController

return M
