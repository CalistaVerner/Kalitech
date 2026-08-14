local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local LuaClass = luaRuntime.LuaClass
local LuaStringTrim = luaRuntime.LuaStringTrim
U = require("../util.lua")
function arrHas(self, arr, code)
    local n = bit32.bor(KLength(arr), 0)
    do
        local i = 0
        while i < n do
            if bit32.bor(KIndex(arr, i), 0) == code then
                return true
            end
            i = i + 1
        end
    end
    return false
end
DEFAULT_KEYS = KObject:freeze({
    forward = {"W", "UP"},
    back = {"S", "DOWN"},
    left = {"A", "LEFT"},
    right = {"D", "RIGHT"},
    run = {"SHIFT"},
    jump = {"SPACE"}
})
InputRouter = LuaClass()
InputRouter.name = "InputRouter"
function InputRouter.prototype.lua_constructor(self, inputApi, cfg)
    self.inp = inputApi
    local lua_temp_0
    if cfg and cfg.keys and KTypeOf(cfg.keys) == "table" then
        lua_temp_0 = cfg.keys
    else
        lua_temp_0 = DEFAULT_KEYS
    end
    local keys = lua_temp_0
    self._codes = {
        forward = self:_pack(keys.forward or DEFAULT_KEYS.forward),
        back = self:_pack(keys.back or DEFAULT_KEYS.back),
        left = self:_pack(keys.left or DEFAULT_KEYS.left),
        right = self:_pack(keys.right or DEFAULT_KEYS.right),
        run = self:_pack(keys.run or DEFAULT_KEYS.run),
        jump = self:_pack(keys.jump or DEFAULT_KEYS.jump)
    }
    self._prevJumpDown = false
    self._prevLmb = false
    self._state = {
        ax = 0,
        az = 0,
        run = false,
        jump = false,
        lmbDown = false,
        lmbJustPressed = false,
        dx = 0,
        dy = 0,
        wheel = 0
    }
end
function InputRouter.prototype._pack(self, names)
    local out = {}
    local n = bit32.bor(KLength(names), 0)
    do
        local i = 0
        while i < n do
            local c = bit32.bor(
                self.inp:keyCode(string.upper(LuaStringTrim(tostring(KIndex(names, i))))),
                0
            )
            if c > 0 then
                out[#out + 1] = c
            end
            i = i + 1
        end
    end
    return out
end
function InputRouter.prototype._anyDown(self, keysDown, codes)
    do
        local i = 0
        while i < KLength(codes) do
            if arrHas(_G, keysDown, KIndex(codes, i)) then
                return true
            end
            i = i + 1
        end
    end
    return false
end
function InputRouter.prototype.read(self, frame)
    local snap = frame.snap
    if not snap then
        return self._state
    end
    local kd = snap.keysDown
    local c = self._codes
    local s = self._state
    local fwd = self:_anyDown(kd, c.forward) and 1 or 0
    local back = self:_anyDown(kd, c.back) and 1 or 0
    local right = self:_anyDown(kd, c.right) and 1 or 0
    local left = self:_anyDown(kd, c.left) and 1 or 0
    s.ax = left - right
    s.az = fwd - back
    s.run = self:_anyDown(kd, c.run)
    local jumpDown = self:_anyDown(kd, c.jump)
    s.jump = jumpDown and not self._prevJumpDown
    self._prevJumpDown = jumpDown
    s.dx = U:num(snap.dx, 0)
    s.dy = U:num(snap.dy, 0)
    s.wheel = U:num(snap.wheel, 0)
    local lmb = not not self.inp:mouseDown(0)
    s.lmbDown = lmb
    s.lmbJustPressed = lmb and not self._prevLmb
    self._prevLmb = lmb
    local fi = frame.input
    fi.ax = bit32.bor(s.ax, 0)
    fi.az = bit32.bor(s.az, 0)
    fi.run = not not s.run
    fi.jump = not not s.jump
    fi.lmbDown = not not s.lmbDown
    fi.lmbJustPressed = not not s.lmbJustPressed
    fi.dx = s.dx
    fi.dy = s.dy
    fi.wheel = s.wheel
    return s
end
M = InputRouter

return M
