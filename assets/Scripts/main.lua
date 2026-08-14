local M = {}
local WorldSystems = require("./World.systems").worldSystems

M.meta = {
    id = "kalitech.app",
    version = "2.4.0",
    apiMin = "0.2.0",
    name = "Kalitech App Entrypoint"
}

M.start = function()
    local world = ENGINE.world
    local base = world:env({mode = "game"})
    local desc = world["$"](world, base)
        :systems(WorldSystems)
        :time({dayLength = 60 * 60, day = 0, timeOfDay = 8 * 3600})
        :build()
    world:create(desc)
end

return M
