local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local LuaNumber = luaRuntime.LuaNumber
local Error = luaRuntime.Error
local LuaConstruct = luaRuntime.LuaConstruct
local LuaNumberToFixed = luaRuntime.LuaNumberToFixed
function ms(self, nanos)
    return (LuaNumber(nanos) or 0) / 1000000
end
create = setmetatable(
    {},
    {__call = function(lua_, self, engine, K)
        if not engine then
            error(
                LuaConstruct(Error, "[PROFILER] engine is required"),
                0
            )
        end
        if KTypeOf(engine.hud) ~= "function" then
            error(
                LuaConstruct(Error, "[PROFILER] engine.hud() is required"),
                0
            )
        end
        local hud = engine:hud()
        local bus = engine.bus and engine:bus() or nil
        local cfg = K or (_G.__kalitech or KObject:create(nil))
        local function overlay(self, ctx, opts)
            if opts == nil then
                opts = {}
            end
            local layerName = tostring(opts.layer or "perf")
            local lua_opts_x_0 = opts.x
            if lua_opts_x_0 == nil then
                lua_opts_x_0 = 12
            end
            local x = LuaNumber(lua_opts_x_0)
            local lua_opts_y_1 = opts.y
            if lua_opts_y_1 == nil then
                lua_opts_y_1 = 12
            end
            local y = LuaNumber(lua_opts_y_1)
            local lua_opts_font_2 = opts.font
            if lua_opts_font_2 == nil then
                lua_opts_font_2 = 14
            end
            local font = LuaNumber(lua_opts_font_2)
            local layer = hud:createLayer(layerName)
            local root = hud:addContainer(layer, x, y)
            local title = hud:addLabel(
                layer,
                root,
                "FrameProfiler",
                0,
                0
            )
            local frameLine = hud:addLabel(
                layer,
                root,
                "",
                0,
                font + 4
            )
            local eventsLine = hud:addLabel(
                layer,
                root,
                "",
                0,
                (font + 4) * 2
            )
            local workerLine = hud:addLabel(
                layer,
                root,
                "",
                0,
                (font + 4) * 3
            )
            hud:setFontSize(title, font)
            hud:setFontSize(frameLine, font)
            hud:setFontSize(eventsLine, font)
            hud:setFontSize(workerLine, font)
            local function update(self)
                if not ctx or not ctx.perf then
                    return
                end
                local perf = ctx:perf()
                local lua_perf_3
                if perf then
                    lua_perf_3 = perf:frame()
                else
                    lua_perf_3 = nil
                end
                local frame = lua_perf_3
                if frame then
                    local total = ms(_G, frame.frameNanos)
                    local world = ms(_G, frame.worldUpdateNanos)
                    local await = ms(_G, frame.awaitWorkersNanos)
                    hud:setText(
                        frameLine,
                        ((((("frame=" .. LuaNumberToFixed(total, 2)) .. "ms world=") .. LuaNumberToFixed(world, 2)) .. "ms await=") .. LuaNumberToFixed(await, 2)) .. "ms"
                    )
                end
                local lua_temp_4
                if bus and KTypeOf(bus.stats) == "function" then
                    lua_temp_4 = bus:stats()
                else
                    lua_temp_4 = nil
                end
                local evtStats = lua_temp_4
                if evtStats then
                    hud:setText(
                        eventsLine,
                        (("events/sec=" .. tostring(LuaNumberToFixed(evtStats.eventsPerSec, 1))) .. " queued=") .. tostring(evtStats.queued)
                    )
                end
                local lua_perf_5
                if perf then
                    lua_perf_5 = perf:workers()
                else
                    lua_perf_5 = {}
                end
                local workers = lua_perf_5
                if workers and KLength(workers) > 0 then
                    local top = KArrayOps.join(KArrayOps.map(KArrayOps.slice(workers, 0, 3), function(lua_, w) return ((tostring(w.systemName) .. ":") .. LuaNumberToFixed(
                        ms(_G, w.lastTickNanos),
                        2
                    )) .. "ms" end), " | ")
                    hud:setText(
                        workerLine,
                        "systems: " .. tostring(top)
                    )
                end
            end
            local function destroy(self)
                do
                    pcall(function()
                        hud:remove(workerLine)
                    end)
                end
                do
                    pcall(function()
                        hud:remove(eventsLine)
                    end)
                end
                do
                    pcall(function()
                        hud:remove(frameLine)
                    end)
                end
                do
                    pcall(function()
                        hud:remove(title)
                    end)
                end
                do
                    pcall(function()
                        hud:remove(root)
                    end)
                end
                do
                    pcall(function()
                        hud:destroyLayer(layer)
                    end)
                end
            end
            return KObject:freeze({update = update, destroy = destroy})
        end
        return KObject:freeze({overlay = overlay, config = cfg})
    end}
)
create.META = {
    moduleId = "profiler",
    id = "profiler",
    version = "1.0.0",
    description = "FrameProfiler HUD overlay (frame time + events/sec + worker ticks).",
    engineMin = "0.2.0",
    changelog = {"1.0.0: initial profiler overlay helper."},
}
M = create
M.META = create.META

return M
