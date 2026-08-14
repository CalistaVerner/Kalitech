local M = {}
DEFAULT_CONFIG = {
    dataConfig = {
        materials = {path = "data/materials.json"},
        camera = {path = "data/camera/camera.config.json"},
        movement = {path = "data/player/movement.config.json"},
        player = {path = "data/player.json"},
        sounds = {path = "data/sounds.json"}
    }
}
M = {DEFAULT_CONFIG = DEFAULT_CONFIG}

return M
