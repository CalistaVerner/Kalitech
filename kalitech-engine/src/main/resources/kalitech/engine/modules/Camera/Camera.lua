local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local Classes = luaRuntime.class
local Error = luaRuntime.Error
function req(self, v, msg)
    if v == nil then
        error(
            Classes:construct(Error, msg),
            0
        )
    end
    return v
end
function makeApi(self, engine)
    req(_G, engine, "[camera] engine is required")
    local lua_temp_0
    if engine.log and KTypeOf(engine.log) == "function" then
        lua_temp_0 = engine:log()
    else
        lua_temp_0 = nil
    end
    local log = lua_temp_0
    local function info(self, msg)
        if log and KTypeOf(log.info) == "function" then
            log:info(tostring(msg))
        end
    end
    local function warn(self, msg)
        if log and KTypeOf(log.warn) == "function" then
            log:warn(tostring(msg))
        end
    end
    local ORCH_MODULE_ID = "./CameraOrchestrator.lua"
    local function requireOrchestrator(self)
        local Orchestrator = require(ORCH_MODULE_ID)
        if KTypeOf(Orchestrator) ~= "function" then
            error(
                Classes:construct(Error, "[camera] Orchestrator export must be a function/class: " .. ORCH_MODULE_ID),
                0
            )
        end
        return Orchestrator
    end
    local function createOrchestrator(self, player)
        req(_G, player, "[camera] createOrchestrator(player): player is required")
        local Orchestrator = requireOrchestrator(_G)
        return Classes:construct(Orchestrator, player)
    end
    local function hasOrchestrator(self)
        do
            local function lua_catch(e)
                warn(_G, "[camera] Orchestrator missing: " .. ORCH_MODULE_ID)
                return true, false
            end
            local lua_try, lua_hasReturned, lua_returnValue = pcall(function()
                return true, KTypeOf(require(ORCH_MODULE_ID)) == "function"
            end)
            if not lua_try then
                lua_hasReturned, lua_returnValue = lua_catch(lua_hasReturned)
            end
            if lua_hasReturned then
                return lua_returnValue
            end
        end
    end
    info(_G, "[camera] module ready orchestrator=" .. ORCH_MODULE_ID)
    return KObject:freeze({version = "1.0.0", orchestrator = ORCH_MODULE_ID, hasOrchestrator = hasOrchestrator, createOrchestrator = createOrchestrator})
end
create = setmetatable(
    {},
    {__call = function(lua_, self, engine, K)
        return makeApi(_G, engine, K)
    end}
)
create.META = {
    moduleId = "camera",
    version = "1.0.0",
    description = "Engine camera module. Owns player camera orchestrator factory.",
    engineMin = "0.1.0"
}
M = create

return M
