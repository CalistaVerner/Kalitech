local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local LuaNumber = luaRuntime.LuaNumber
local Error = luaRuntime.Error
local LuaConstruct = luaRuntime.LuaConstruct
function pickFn(self, obj, names)
    if not obj then
        return nil
    end
    do
        local i = 0
        while i < KLength(names) do
            local k = KIndex(names, i)
            local fn = obj[k]
            if KTypeOf(fn) == "function" then
                return KFunction:bind(fn, obj)
            end
            i = i + 1
        end
    end
    return nil
end
function constFn(self, v)
    return function(self)
        return v
    end
end
function toXYZ(self, a, b, c)
    if a ~= nil and KTypeOf(a) == "table" then
        local x = a.x
        local y = a.y
        local z = a.z
        local lua_temp_0
        if KTypeOf(x) == "function" then
            lua_temp_0 = LuaNumber(x(_G))
        else
            lua_temp_0 = LuaNumber(x)
        end
        local lua_temp_1
        if KTypeOf(y) == "function" then
            lua_temp_1 = LuaNumber(y(_G))
        else
            lua_temp_1 = LuaNumber(y)
        end
        local lua_temp_2
        if KTypeOf(z) == "function" then
            lua_temp_2 = LuaNumber(z(_G))
        else
            lua_temp_2 = LuaNumber(z)
        end
        return {x = lua_temp_0, y = lua_temp_1, z = lua_temp_2}
    end
    return {
        x = LuaNumber(a) or 0,
        y = LuaNumber(b) or 0,
        z = LuaNumber(c) or 0
    }
end
function makeAdapter(self, raw, physicsApi, bodyId)
    if not raw or KTypeOf(raw) ~= "table" then
        return nil
    end
    local pos = pickFn(_G, raw, {
        "position",
        "getPosition",
        "pos",
        "getPos",
        "worldPosition",
        "getWorldPosition"
    })
    local vel = pickFn(_G, raw, {
        "getVel",
        "vel",
        "velocity",
        "getVelocity",
        "linearVelocity",
        "getLinearVelocity",
        "getLinearVel",
        "linearVel",
        "getLinVel"
    })
    local rot = pickFn(_G, raw, {
        "rotation",
        "getRotation",
        "getRot",
        "quat",
        "getQuat",
        "getQuaternion",
        "orientation",
        "getOrientation",
        "worldRotation",
        "getWorldRotation"
    })
    local ang = pickFn(_G, raw, {
        "getAngVel",
        "angVel",
        "angularVelocity",
        "getAngularVelocity",
        "omega",
        "getOmega"
    })
    local tr = pickFn(_G, raw, {"transform", "getTransform", "worldTransform", "getWorldTransform"})
    local rawApplyImpulse = pickFn(_G, raw, {
        "applyImpulse",
        "applyCentralImpulse",
        "impulse",
        "addImpulse",
        "applyLinearImpulse",
        "applyImpulseWorld",
        "applyWorldImpulse"
    })
    local rawSetVel = pickFn(_G, raw, {
        "setVel",
        "setVelocity",
        "setLinearVelocity",
        "setLinearVel",
        "velocitySet",
        "linearVelocitySet"
    })
    local rawSetPos = pickFn(_G, raw, {
        "setPos",
        "setPosition",
        "teleport",
        "warp",
        "setWorldPosition"
    })
    local pid = bit32.bor(bodyId, 0)
    local function apiApplyImpulse(self, x, y, z)
        if not physicsApi or pid <= 0 then
            return false
        end
        local f = physicsApi
        if KTypeOf(f.applyImpulse) == "function" then
            do
                local lua_try, lua_hasReturned, lua_returnValue = pcall(function()
                    if y == nil and z == nil and x and KTypeOf(x) == "table" then
                        f:applyImpulse(pid, x)
                    else
                        f:applyImpulse(pid, x, y, z)
                    end
                    return true, true
                end)
                if lua_try and lua_hasReturned then
                    return lua_returnValue
                end
            end
        end
        if KTypeOf(f.impulse) == "function" then
            do
                local lua_try, lua_hasReturned, lua_returnValue = pcall(function()
                    if y == nil and z == nil and x and KTypeOf(x) == "table" then
                        f:impulse(pid, x)
                    else
                        f:impulse(pid, x, y, z)
                    end
                    return true, true
                end)
                if lua_try and lua_hasReturned then
                    return lua_returnValue
                end
            end
        end
        if KTypeOf(f.addImpulse) == "function" then
            do
                local lua_try, lua_hasReturned, lua_returnValue = pcall(function()
                    if y == nil and z == nil and x and KTypeOf(x) == "table" then
                        f:addImpulse(pid, x)
                    else
                        f:addImpulse(pid, x, y, z)
                    end
                    return true, true
                end)
                if lua_try and lua_hasReturned then
                    return lua_returnValue
                end
            end
        end
        return false
    end
    local function apiSetVel(self, x, y, z)
        if not physicsApi or pid <= 0 then
            return false
        end
        local f = physicsApi
        if KTypeOf(f.setVel) == "function" then
            do
                local lua_try, lua_hasReturned, lua_returnValue = pcall(function()
                    if y == nil and z == nil and x and KTypeOf(x) == "table" then
                        f:setVel(pid, x)
                    else
                        f:setVel(pid, x, y, z)
                    end
                    return true, true
                end)
                if lua_try and lua_hasReturned then
                    return lua_returnValue
                end
            end
        end
        if KTypeOf(f.setVelocity) == "function" then
            do
                local lua_try, lua_hasReturned, lua_returnValue = pcall(function()
                    if y == nil and z == nil and x and KTypeOf(x) == "table" then
                        f:setVelocity(pid, x)
                    else
                        f:setVelocity(pid, x, y, z)
                    end
                    return true, true
                end)
                if lua_try and lua_hasReturned then
                    return lua_returnValue
                end
            end
        end
        if KTypeOf(f.setLinearVelocity) == "function" then
            do
                local lua_try, lua_hasReturned, lua_returnValue = pcall(function()
                    if y == nil and z == nil and x and KTypeOf(x) == "table" then
                        f:setLinearVelocity(pid, x)
                    else
                        f:setLinearVelocity(pid, x, y, z)
                    end
                    return true, true
                end)
                if lua_try and lua_hasReturned then
                    return lua_returnValue
                end
            end
        end
        return false
    end
    local function apiSetPos(self, x, y, z)
        if not physicsApi or pid <= 0 then
            return false
        end
        local f = physicsApi
        if KTypeOf(f.setPos) == "function" then
            do
                local lua_try, lua_hasReturned, lua_returnValue = pcall(function()
                    if y == nil and z == nil and x and KTypeOf(x) == "table" then
                        f:setPos(pid, x)
                    else
                        f:setPos(pid, x, y, z)
                    end
                    return true, true
                end)
                if lua_try and lua_hasReturned then
                    return lua_returnValue
                end
            end
        end
        if KTypeOf(f.setPosition) == "function" then
            do
                local lua_try, lua_hasReturned, lua_returnValue = pcall(function()
                    if y == nil and z == nil and x and KTypeOf(x) == "table" then
                        f:setPosition(pid, x)
                    else
                        f:setPosition(pid, x, y, z)
                    end
                    return true, true
                end)
                if lua_try and lua_hasReturned then
                    return lua_returnValue
                end
            end
        end
        if KTypeOf(f.teleport) == "function" then
            do
                local lua_try, lua_hasReturned, lua_returnValue = pcall(function()
                    if y == nil and z == nil and x and KTypeOf(x) == "table" then
                        f:teleport(pid, x)
                    else
                        f:teleport(pid, x, y, z)
                    end
                    return true, true
                end)
                if lua_try and lua_hasReturned then
                    return lua_returnValue
                end
            end
        end
        return false
    end
    local zeroV = {x = 0, y = 0, z = 0}
    local identQ = {x = 0, y = 0, z = 0, w = 1}
    return KObject:freeze({
        raw = raw,
        position = pos or constFn(_G, zeroV),
        getVel = vel or constFn(_G, zeroV),
        rotation = rot or constFn(_G, identQ),
        getAngVel = ang or constFn(_G, zeroV),
        transform = tr or nil,
        applyImpulse = function(self, a, b, c)
            local v = toXYZ(_G, a, b, c)
            if rawApplyImpulse then
                do
                    local lua_try, lua_hasReturned, lua_returnValue = pcall(function()
                        rawApplyImpulse(_G, v)
                        return true
                    end)
                    if lua_try and lua_hasReturned then
                        return lua_returnValue
                    end
                end
                do
                    local lua_try, lua_hasReturned, lua_returnValue = pcall(function()
                        rawApplyImpulse(_G, v.x, v.y, v.z)
                        return true
                    end)
                    if lua_try and lua_hasReturned then
                        return lua_returnValue
                    end
                end
            end
            if apiApplyImpulse(_G, v, v.y, v.z) then
                return
            end
            if apiApplyImpulse(_G, v.x, v.y, v.z) then
                return
            end
            error(
                LuaConstruct(
                    Error,
                    ("[BodyAccess] applyImpulse not supported by raw body nor physics api (bodyId=" .. tostring(pid)) .. ")"
                ),
                0
            )
        end,
        setVel = function(self, a, b, c)
            local v = toXYZ(_G, a, b, c)
            if rawSetVel then
                do
                    local lua_try, lua_hasReturned, lua_returnValue = pcall(function()
                        rawSetVel(_G, v)
                        return true
                    end)
                    if lua_try and lua_hasReturned then
                        return lua_returnValue
                    end
                end
                do
                    local lua_try, lua_hasReturned, lua_returnValue = pcall(function()
                        rawSetVel(_G, v.x, v.y, v.z)
                        return true
                    end)
                    if lua_try and lua_hasReturned then
                        return lua_returnValue
                    end
                end
            end
            if apiSetVel(_G, v, v.y, v.z) then
                return
            end
            if apiSetVel(_G, v.x, v.y, v.z) then
                return
            end
            error(
                LuaConstruct(
                    Error,
                    ("[BodyAccess] setVel not supported by raw body nor physics api (bodyId=" .. tostring(pid)) .. ")"
                ),
                0
            )
        end,
        setPos = function(self, a, b, c)
            local v = toXYZ(_G, a, b, c)
            if rawSetPos then
                do
                    local lua_try, lua_hasReturned, lua_returnValue = pcall(function()
                        rawSetPos(_G, v)
                        return true
                    end)
                    if lua_try and lua_hasReturned then
                        return lua_returnValue
                    end
                end
                do
                    local lua_try, lua_hasReturned, lua_returnValue = pcall(function()
                        rawSetPos(_G, v.x, v.y, v.z)
                        return true
                    end)
                    if lua_try and lua_hasReturned then
                        return lua_returnValue
                    end
                end
            end
            if apiSetPos(_G, v, v.y, v.z) then
                return
            end
            if apiSetPos(_G, v.x, v.y, v.z) then
                return
            end
            error(
                LuaConstruct(
                    Error,
                    ("[BodyAccess] setPos not supported by raw body nor physics api (bodyId=" .. tostring(pid)) .. ")"
                ),
                0
            )
        end
    })
end
function resolveBodyAccess(self, physicsApi, bodyObj, bodyId)
    if not physicsApi then
        error(
            LuaConstruct(Error, "[BodyAccessResolver] physics api is required"),
            0
        )
    end
    if bodyObj and KTypeOf(bodyObj) == "table" then
        local adapted = makeAdapter(_G, bodyObj, physicsApi, bodyId)
        if not adapted then
            error(
                LuaConstruct(Error, "[BodyAccessResolver] failed to adapt body object"),
                0
            )
        end
        return adapted
    end
    local id = bit32.bor(bodyId, 0)
    if id <= 0 then
        return nil
    end
    local raw = nil
    if KTypeOf(physicsApi.body) == "function" then
        raw = physicsApi:body(id)
    elseif KTypeOf(physicsApi.getBody) == "function" then
        raw = physicsApi:getBody(id)
    elseif KTypeOf(physicsApi.bodyRef) == "function" then
        raw = physicsApi:bodyRef(id)
    end
    if not raw then
        error(
            LuaConstruct(
                Error,
                "[BodyAccessResolver] physics body accessor missing or returned null for id=" .. tostring(id)
            ),
            0
        )
    end
    local adapted = makeAdapter(_G, raw, physicsApi, id)
    if not adapted then
        error(
            LuaConstruct(
                Error,
                "[BodyAccessResolver] failed to adapt body for id=" .. tostring(id)
            ),
            0
        )
    end
    return adapted
end
M = {resolveBodyAccess = resolveBodyAccess}

return M
