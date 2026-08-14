local M = {}
Boot = require("@builtin/bootstrap/Bootstrap")
boot = Boot:createDefault():init()
M = {
    config = boot.config,
    attachEngine = KFunction:bind(boot.attachEngine, boot),
    whenEngine = KFunction:bind(boot.whenEngine, boot),
    whenEngineOnce = KFunction:bind(boot.whenEngineOnce, boot),
    safeJson = Boot.safeJson
}

return M
