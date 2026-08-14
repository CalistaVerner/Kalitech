local M = {}
local lua_require_result_0 = require("../helpers/ModuleCommon.lua")
requireEngineApi = lua_require_result_0.requireEngineApi
create = setmetatable(
    {},
    {__call = function(lua_, self, engine)
        local api = requireEngineApi(_G, engine, "time", "TIME")
        local lua_temp_1
        if engine and KTypeOf(engine.world) == "function" then
            lua_temp_1 = engine:world()
        else
            lua_temp_1 = nil
        end
        local worldApi = lua_temp_1
        local function snapshot(self)
            local lua_temp_2
            if worldApi and KTypeOf(worldApi.getWorldTime) == "function" then
                lua_temp_2 = worldApi:getWorldTime()
            else
                lua_temp_2 = nil
            end
            local w = lua_temp_2
            if w and KTypeOf(w) == "table" then
                local lua_w_stepDt_3 = w.stepDt
                if lua_w_stepDt_3 == nil then
                    lua_w_stepDt_3 = w.simDt
                end
                local lua_w_stepDt_3_4 = lua_w_stepDt_3
                if lua_w_stepDt_3_4 == nil then
                    lua_w_stepDt_3_4 = 0
                end
                local lua_w_stepDt_5 = w.stepDt
                if lua_w_stepDt_5 == nil then
                    lua_w_stepDt_5 = w.simDt
                end
                local lua_w_stepDt_5_6 = lua_w_stepDt_5
                if lua_w_stepDt_5_6 == nil then
                    lua_w_stepDt_5_6 = 0
                end
                local lua_w_worldTime_7 = w.worldTime
                if lua_w_worldTime_7 == nil then
                    lua_w_worldTime_7 = 0
                end
                local lua_w_frameIndex_8 = w.frameIndex
                if lua_w_frameIndex_8 == nil then
                    lua_w_frameIndex_8 = 0
                end
                local lua_w_tickIndex_9 = w.tickIndex
                if lua_w_tickIndex_9 == nil then
                    lua_w_tickIndex_9 = 0
                end
                local lua_w_realDt_10 = w.realDt
                if lua_w_realDt_10 == nil then
                    lua_w_realDt_10 = 0
                end
                local lua_w_simDt_11 = w.simDt
                if lua_w_simDt_11 == nil then
                    lua_w_simDt_11 = 0
                end
                local lua_w_stepDt_12 = w.stepDt
                if lua_w_stepDt_12 == nil then
                    lua_w_stepDt_12 = 0
                end
                local lua_w_interpAlpha_13 = w.interpAlpha
                if lua_w_interpAlpha_13 == nil then
                    lua_w_interpAlpha_13 = 0
                end
                return {
                    tpf = lua_w_stepDt_3_4,
                    dt = lua_w_stepDt_5_6,
                    now = lua_w_worldTime_7,
                    frame = lua_w_frameIndex_8,
                    tick = lua_w_tickIndex_9,
                    realDt = lua_w_realDt_10,
                    simDt = lua_w_simDt_11,
                    stepDt = lua_w_stepDt_12,
                    interpAlpha = lua_w_interpAlpha_13
                }
            end
            return {
                tpf = api:tpf(),
                dt = api:dt(),
                now = api:now(),
                frame = api:frame(),
                tick = 0,
                realDt = 0,
                simDt = api:dt(),
                stepDt = api:dt(),
                interpAlpha = 1
            }
        end
        return KObject:freeze({
            tpf = function() return snapshot(_G).tpf end,
            dt = function() return snapshot(_G).dt end,
            now = function() return snapshot(_G).now end,
            frame = function() return snapshot(_G).frame end,
            tick = function() return snapshot(_G).tick end,
            realDt = function() return snapshot(_G).realDt end,
            simDt = function() return snapshot(_G).simDt end,
            stepDt = function() return snapshot(_G).stepDt end,
            interpAlpha = function() return snapshot(_G).interpAlpha end,
            snapshot = snapshot,
            api = api
        })
    end}
)
create.META = {
    moduleId = "time",
    id = "time",
    version = "2.0.0",
    description = "Time wrapper for world clock snapshots (fixed tick + render interpolation).",
    engineMin = "0.1.0",
    changelog = {"2.0.0: route time to ENGINE.world.getWorldTime when available; expose frame/tick/realDt/simDt/stepDt/interpAlpha."},
}
M = create

return M
