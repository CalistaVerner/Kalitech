local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local Classes = luaRuntime.class
DEFAULT_CONFIG = {
    dataConfig = {
        materials = {path = "data/materials.json"},
        camera = {path = "data/camera/camera.config.json"},
        movement = {path = "data/player/movement.config.json"},
        player = {path = "data/player.json"},
        sounds = {path = "data/sounds.json"}
    }
}
local BootstrapConfigApi = Classes:create()
BootstrapConfigApi.name = "BootstrapConfigApi"
BootstrapConfigApi.prototype.DEFAULT_CONFIG = DEFAULT_CONFIG
return Classes:construct(BootstrapConfigApi)
