local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local Error = luaRuntime.Error
local LuaConstruct = luaRuntime.LuaConstruct
local lua_require_result_0 = require("./controllers/PlayerEventsController.lua")
PlayerEventsController = lua_require_result_0.PlayerEventsController
local lua_require_result_1 = require("./controllers/PlayerGameplayController.lua")
PlayerGameplayController = lua_require_result_1.PlayerGameplayController
local lua_require_result_2 = require("./controllers/PlayerCameraController.lua")
PlayerCameraController = lua_require_result_2.PlayerCameraController
local lua_require_result_3 = require("./controllers/PlayerUIController.lua")
PlayerUIController = lua_require_result_3.PlayerUIController
function req(self, v, msg)
    if v == nil then
        error(
            LuaConstruct(Error, msg),
            0
        )
    end
    return v
end
function createPlayerRegistry(self)
    local ENGINE = req(_G, _G.ENGINE, "[PlayerControllers] ENGINE is required")
    local C = req(_G, ENGINE.controllers, "[PlayerControllers] ENGINE.controllers is required")
    if KTypeOf(C.registry) ~= "function" then
        error(
            LuaConstruct(Error, "[PlayerControllers] ENGINE.controllers.registry(name) required"),
            0
        )
    end
    local R = C:registry("player")
    R:register("player.events", PlayerEventsController, {order = 10})
    R:register("player.gameplay", PlayerGameplayController, {order = 20, deps = {"player.events"}})
    R:register("player.camera", PlayerCameraController, {order = 30, deps = {"player.gameplay"}})
    R:register("player.ui", PlayerUIController, {order = 40, deps = {"player.events", "player.gameplay"}})
    return R
end
M = {createPlayerRegistry = createPlayerRegistry}

return M
