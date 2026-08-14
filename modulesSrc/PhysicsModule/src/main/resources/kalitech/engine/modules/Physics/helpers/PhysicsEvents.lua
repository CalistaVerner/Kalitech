local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local Tables = luaRuntime.table
local Classes = luaRuntime.class
local Error = luaRuntime.Error
local lua_require_result_0 = require("./PhysicsIds.lua")
bodyIdOf = lua_require_result_0.bodyIdOf
surfaceIdOf = lua_require_result_0.surfaceIdOf
function normalizeFilter(self, filter)
    if not filter then
        return nil
    end
    if KTypeOf(filter) ~= "table" then
        error(
            Classes:construct(Error, "[ENGINE.physics.events] filter must be an object"),
            0
        )
    end
    local f = Tables:merge({}, filter)
    if f.a ~= nil then
        f.a = bodyIdOf(_G, f.a)
    end
    if f.b ~= nil then
        f.b = bodyIdOf(_G, f.b)
    end
    if f.bodyId ~= nil then
        f.bodyId = bodyIdOf(_G, f.bodyId)
    end
    if f.surfaceId ~= nil then
        f.surfaceId = surfaceIdOf(_G, f.surfaceId)
    end
    return f
end
function match(self, filter, evt)
    if not filter then
        return true
    end
    if not evt then
        return false
    end
    local a = evt.a or ({})
    local b = evt.b or ({})
    if filter.a and a.bodyId ~= filter.a and b.bodyId ~= filter.a then
        return false
    end
    if filter.b and a.bodyId ~= filter.b and b.bodyId ~= filter.b then
        return false
    end
    if filter.bodyId and a.bodyId ~= filter.bodyId and b.bodyId ~= filter.bodyId then
        return false
    end
    if filter.surfaceId and a.surfaceId ~= filter.surfaceId and b.surfaceId ~= filter.surfaceId then
        return false
    end
    return true
end
function createPhysicsEvents(self, engine, physics)
    if not engine then
        error(
            Classes:construct(Error, "[ENGINE.physics.events] engine is required"),
            0
        )
    end
    if not physics then
        error(
            Classes:construct(Error, "[ENGINE.physics.events] physics is required"),
            0
        )
    end
    if KTypeOf(physics.on) ~= "function" then
        error(
            Classes:construct(Error, "[ENGINE.physics.events] ENGINE.physics.on(topic,fn) missing"),
            0
        )
    end
    local function onTopic(self, topic, filter, fn)
        if KTypeOf(filter) == "function" then
            fn = filter
            filter = nil
        end
        if KTypeOf(fn) ~= "function" then
            error(
                Classes:construct(Error, "[ENGINE.physics.events] handler must be a function"),
                0
            )
        end
        local f = normalizeFilter(_G, filter)
        return physics:on(
            topic,
            function(lua_, e)
                if match(_G, f, e) then
                    return fn(_G, e)
                end
            end
        )
    end
    return KObject:freeze({
        onCollisionBegin = function(lua_, f, fn) return onTopic(_G, "engine.physics.collision.begin", f, fn) end,
        onCollisionStay = function(lua_, f, fn) return onTopic(_G, "engine.physics.collision.stay", f, fn) end,
        onCollisionEnd = function(lua_, f, fn) return onTopic(_G, "engine.physics.collision.end", f, fn) end,
        onImpact = function(lua_, f, fn) return onTopic(_G, "engine.physics.impact", f, fn) end,
        onPostStep = function(self, fn)
            if KTypeOf(fn) ~= "function" then
                error(
                    Classes:construct(Error, "[ENGINE.physics.events] handler must be a function"),
                    0
                )
            end
            return physics:on("engine.physics.postStep", fn)
        end
    })
end
M = {createPhysicsEvents = createPhysicsEvents}

return M
