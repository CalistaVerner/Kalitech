local luaRuntime = require("@builtin/lua_runtime")
local Collections = luaRuntime.collection
local Strings = luaRuntime.string
local Numbers = luaRuntime.number
local Classes = luaRuntime.class
local Error = luaRuntime.Error
META = {moduleId = "input", version = "1.0.0", engineMin = "0.0.0"}
function _isObj(self, x)
    return x and KTypeOf(x) == "table"
end
function _num(self, x, def)
    if def == nil then
        def = 0
    end
    x = Numbers:coerce(x)
    local lua_Number_isFinite_result_0
    if Numbers:isFinite(x) then
        lua_Number_isFinite_result_0 = x
    else
        lua_Number_isFinite_result_0 = def
    end
    return lua_Number_isFinite_result_0
end
function _bool(self, x)
    return not not x
end
function _keyNameNormalize(self, name)
    if name == nil then
        return ""
    end
    return Strings:trim(tostring(name))
end
function _arrToSet(self, arr)
    do
        local function lua_catch()
            return true, Collections:newSet()
        end
        local lua_try, lua_hasReturned, lua_returnValue = pcall(function()
            local out = Collections:newSet()
            if not arr then
                return true, out
            end
            local n = bit32.rshift(KLength(arr), 0)
            do
                local i = 0
                while i < n do
                    out:add(KIndex(arr, i))
                    i = i + 1
                end
            end
            return true, out
        end)
        if not lua_try then
            lua_hasReturned, lua_returnValue = lua_catch()
        end
        if lua_hasReturned then
            return lua_returnValue
        end
    end
end
local moduleFactory = function(self, engine, K)
    local lua_temp_1
    if engine and KTypeOf(engine.input) == "function" then
        lua_temp_1 = engine:input()
    else
        lua_temp_1 = nil
    end
    local input = lua_temp_1
    if not input then
        error(
            Classes:construct(Error, "Input module: engine.input() is required"),
            0
        )
    end
    local _lastSnap = nil
    local _lastJustPressed = Collections:newSet()
    local _lastJustReleased = Collections:newSet()
    local function _vec2(self, x, y)
        return {
            x = _num(_G, x),
            y = _num(_G, y)
        }
    end
    local function _delta2(self, dx, dy)
        return {
            dx = _num(_G, dx),
            dy = _num(_G, dy)
        }
    end
    local function _readSnapshot(self)
        local snap = input:consumeSnapshot()
        _lastSnap = snap or nil
        do
            local function lua_catch()
                _lastJustPressed = Collections:newSet()
                _lastJustReleased = Collections:newSet()
            end
            local lua_try = pcall(function()
                local jp = snap and (snap.justPressed or snap.justPressedKeyCodes or snap.justPressedCodes)
                local jr = snap and (snap.justReleased or snap.justReleasedKeyCodes or snap.justReleasedCodes)
                _lastJustPressed = _arrToSet(_G, jp)
                _lastJustReleased = _arrToSet(_G, jr)
            end)
            if not lua_try then
                lua_catch()
            end
        end
        return _lastSnap
    end
    local api
    api = {
        META = META,
        consumeSnapshot = function(self)
            return _readSnapshot(_G)
        end,
        keyDown = function(self, key)
            if KTypeOf(key) == "string" then
                return not not input:keyDown(_keyNameNormalize(_G, key))
            end
            return not not input:keyDown(bit32.bor(
                _num(_G, key, -1),
                0
            ))
        end,
        keyCode = function(self, name)
            return bit32.bor(
                input:keyCode(_keyNameNormalize(_G, name)),
                0
            )
        end,
        mouseX = function(self)
            return _num(
                _G,
                input:mouseX()
            )
        end,
        mouseY = function(self)
            return _num(
                _G,
                input:mouseY()
            )
        end,
        cursorPosition = function(self)
            local v = input:cursorPosition()
            if _isObj(_G, v) and v.x ~= nil and v.y ~= nil then
                return _vec2(_G, v.x, v.y)
            end
            return _vec2(
                _G,
                api:mouseX(),
                api:mouseY()
            )
        end,
        mouseDx = function(self)
            return _num(
                _G,
                input:mouseDx()
            )
        end,
        mouseDy = function(self)
            return _num(
                _G,
                input:mouseDy()
            )
        end,
        mouseDX = function(self)
            return _num(
                _G,
                input:mouseDX()
            )
        end,
        mouseDY = function(self)
            return _num(
                _G,
                input:mouseDY()
            )
        end,
        mouseDelta = function(self)
            local d = input:mouseDelta()
            if _isObj(_G, d) and (d.dx ~= nil or d.x ~= nil) then
                local lua_delta2_5 = _delta2
                local lua_G_4 = _G
                local lua_d_dx_2 = d.dx
                if lua_d_dx_2 == nil then
                    lua_d_dx_2 = d.x
                end
                local lua_d_dy_3 = d.dy
                if lua_d_dy_3 == nil then
                    lua_d_dy_3 = d.y
                end
                return lua_delta2_5(lua_G_4, lua_d_dx_2, lua_d_dy_3)
            end
            return _delta2(
                _G,
                api:mouseDx(),
                api:mouseDy()
            )
        end,
        consumeMouseDelta = function(self)
            local d = input:consumeMouseDelta()
            if _isObj(_G, d) and (d.dx ~= nil or d.x ~= nil) then
                local lua_delta2_9 = _delta2
                local lua_G_8 = _G
                local lua_d_dx_6 = d.dx
                if lua_d_dx_6 == nil then
                    lua_d_dx_6 = d.x
                end
                local lua_d_dy_7 = d.dy
                if lua_d_dy_7 == nil then
                    lua_d_dy_7 = d.y
                end
                return lua_delta2_9(lua_G_8, lua_d_dx_6, lua_d_dy_7)
            end
            return _delta2(_G, 0, 0)
        end,
        wheelDelta = function(self)
            return _num(
                _G,
                input:wheelDelta()
            )
        end,
        consumeWheelDelta = function(self)
            return _num(
                _G,
                input:consumeWheelDelta()
            )
        end,
        mouseDown = function(self, button)
            return not not input:mouseDown(bit32.bor(
                _num(_G, button, 0),
                0
            ))
        end,
        cursorVisible = function(self, v)
            if v == nil then
                return not not input:cursorVisible()
            end
            input:cursorVisible(_bool(_G, v))
            return api
        end,
        grabMouse = function(self, grab)
            input:grabMouse(_bool(_G, grab))
            return api
        end,
        grabbed = function(self)
            return not not input:grabbed()
        end,
        endFrame = function(self)
            input:endFrame()
            return api
        end,
        beginFrame = function(self)
            return _readSnapshot(_G)
        end,
        poll = function(self)
            local snap = _readSnapshot(_G)
            input:endFrame()
            return snap
        end,
        lastSnapshot = function(self)
            return _lastSnap
        end,
        pressed = function(self, key)
            local lua_temp_10
            if KTypeOf(key) == "string" then
                lua_temp_10 = api:keyCode(key)
            else
                lua_temp_10 = bit32.bor(
                    _num(_G, key, -1),
                    0
                )
            end
            local code = lua_temp_10
            return _lastJustPressed:has(code)
        end,
        released = function(self, key)
            local lua_temp_11
            if KTypeOf(key) == "string" then
                lua_temp_11 = api:keyCode(key)
            else
                lua_temp_11 = bit32.bor(
                    _num(_G, key, -1),
                    0
                )
            end
            local code = lua_temp_11
            return _lastJustReleased:has(code)
        end,
        mousePos = function(self)
            return api:cursorPosition()
        end,
        delta = function(self)
            return api:mouseDelta()
        end
    }
    return api
end
return setmetatable({META = META}, {
    __call = function(_, ...)
        return moduleFactory(...)
    end
})
