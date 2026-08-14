local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local LuaNumber = luaRuntime.LuaNumber
local LuaNumberIsFinite = luaRuntime.LuaNumberIsFinite
local LuaClass = luaRuntime.LuaClass
local LuaArrayIsArray = luaRuntime.LuaArrayIsArray
function clamp(self, v, lo, hi)
    local lua_temp_1
    if v < lo then
        lua_temp_1 = lo
    else
        local lua_temp_0
        if v > hi then
            lua_temp_0 = hi
        else
            lua_temp_0 = v
        end
        lua_temp_1 = lua_temp_0
    end
    return lua_temp_1
end
function num(self, v, fb)
    local n = LuaNumber(v)
    local lua_Number_isFinite_result_2
    if LuaNumberIsFinite(n) then
        lua_Number_isFinite_result_2 = n
    else
        lua_Number_isFinite_result_2 = fb
    end
    return lua_Number_isFinite_result_2
end
function expSmooth(self, cur, target, smooth, dt)
    local lua_temp_3
    if smooth > 0 then
        lua_temp_3 = smooth
    else
        lua_temp_3 = 0
    end
    local s = lua_temp_3
    if s == 0 then
        return target
    end
    local a = 1 - math.exp(LuaNumber(-s) * dt)
    return cur + (target - cur) * a
end
CameraZoomController = LuaClass()
CameraZoomController.name = "CameraZoomController"
function CameraZoomController.prototype.lua_constructor(self, cfg)
    cfg = cfg or KObject:create(nil)
    local lua_temp_4
    if LuaArrayIsArray(cfg.steps) and KLength(cfg.steps) > 0 then
        lua_temp_4 = cfg.steps
    else
        lua_temp_4 = {
            2,
            4,
            8,
            16,
            32
        }
    end
    local steps = lua_temp_4
    self.steps = KArrayOps.slice(steps)
    self.minIndex = bit32.bor(
        clamp(
            _G,
            bit32.bor(num(_G, cfg.minIndex, 0), 0),
            0,
            KLength(self.steps) - 1
        ),
        0
    )
    local lua_temp_5
    if cfg.maxIndex ~= nil then
        lua_temp_5 = bit32.bor(num(_G, cfg.maxIndex, KLength(self.steps) - 1), 0)
    else
        lua_temp_5 = KLength(self.steps) - 1
    end
    self.maxIndex = lua_temp_5
    self.maxIndex = bit32.bor(
        clamp(_G, self.maxIndex, self.minIndex, KLength(self.steps) - 1),
        0
    )
    local mid = bit32.bor(KLength(self.steps) / 2, 0)
    local lua_temp_6
    if cfg.index ~= nil then
        lua_temp_6 = bit32.bor(num(_G, cfg.index, mid), 0)
    else
        lua_temp_6 = mid
    end
    self.index = lua_temp_6
    self.index = bit32.bor(
        clamp(_G, self.index, self.minIndex, self.maxIndex),
        0
    )
    self.min = num(_G, cfg.min, 1.2)
    self.max = math.max(
        self.min,
        num(_G, cfg.max, 120)
    )
    self.target = clamp(
        _G,
        num(
            _G,
            cfg.target,
            num(_G, KIndex(self.steps, self.index), 8)
        ),
        self.min,
        self.max
    )
    self.current = clamp(
        _G,
        num(_G, cfg.current, self.target),
        self.min,
        self.max
    )
    self.smooth = num(_G, cfg.smooth, 18)
    self.cooldown = math.max(
        0,
        num(_G, cfg.cooldown, 0.08)
    )
    self._cd = 0
    self.invertWheel = not not cfg.invertWheel
    self.stepStride = math.max(
        1,
        bit32.bor(num(_G, cfg.stepStride, 1), 0)
    )
end
function CameraZoomController.prototype.configure(self, cfg)
    if not cfg then
        return self
    end
    if LuaArrayIsArray(cfg.steps) and KLength(cfg.steps) > 0 then
        self.steps = KArrayOps.slice(cfg.steps)
        self.minIndex = bit32.bor(
            clamp(_G, self.minIndex, 0, KLength(self.steps) - 1),
            0
        )
        self.maxIndex = bit32.bor(
            clamp(_G, self.maxIndex, self.minIndex, KLength(self.steps) - 1),
            0
        )
        self.index = bit32.bor(
            clamp(_G, self.index, self.minIndex, self.maxIndex),
            0
        )
    end
    if cfg.minIndex ~= nil then
        self.minIndex = bit32.bor(
            clamp(
                _G,
                bit32.bor(num(_G, cfg.minIndex, self.minIndex), 0),
                0,
                KLength(self.steps) - 1
            ),
            0
        )
    end
    if cfg.maxIndex ~= nil then
        self.maxIndex = bit32.bor(
            clamp(
                _G,
                bit32.bor(num(_G, cfg.maxIndex, self.maxIndex), 0),
                self.minIndex,
                KLength(self.steps) - 1
            ),
            0
        )
    end
    if cfg.index ~= nil then
        self.index = bit32.bor(
            clamp(
                _G,
                bit32.bor(num(_G, cfg.index, self.index), 0),
                self.minIndex,
                self.maxIndex
            ),
            0
        )
    end
    if cfg.smooth ~= nil then
        self.smooth = num(_G, cfg.smooth, self.smooth)
    end
    if cfg.cooldown ~= nil then
        self.cooldown = math.max(
            0,
            num(_G, cfg.cooldown, self.cooldown)
        )
    end
    if cfg.invertWheel ~= nil then
        self.invertWheel = not not cfg.invertWheel
    end
    if cfg.min ~= nil then
        self.min = num(_G, cfg.min, self.min)
    end
    if cfg.max ~= nil then
        self.max = math.max(
            self.min,
            num(_G, cfg.max, self.max)
        )
    end
    if cfg.stepStride ~= nil then
        self.stepStride = math.max(
            1,
            bit32.bor(num(_G, cfg.stepStride, self.stepStride), 0)
        )
    end
    self.target = clamp(
        _G,
        num(_G, KIndex(self.steps, self.index), self.target),
        self.min,
        self.max
    )
    self.current = clamp(_G, self.current, self.min, self.max)
    return self
end
function CameraZoomController.prototype.reset(self, value)
    if value ~= nil then
        local v = LuaNumber(value)
        if LuaNumberIsFinite(v) then
            self.current = clamp(_G, v, self.min, self.max)
            self.target = self.current
        end
    end
    self._cd = 0
    return self
end
function CameraZoomController.prototype.value(self)
    return self.current
end
function CameraZoomController.prototype.targetValue(self)
    return self.target
end
function CameraZoomController.prototype.stepIndex(self)
    return self.index
end
function CameraZoomController.prototype.setIndex(self, idx, snap)
    self.index = bit32.bor(
        clamp(
            _G,
            bit32.bor(idx, 0),
            self.minIndex,
            self.maxIndex
        ),
        0
    )
    self.target = clamp(
        _G,
        num(_G, KIndex(self.steps, self.index), self.target),
        self.min,
        self.max
    )
    if snap then
        self.current = self.target
    end
    return self
end
function CameraZoomController.prototype.update(self, dt, ctx)
    dt = clamp(
        _G,
        num(_G, dt, 1 / 60),
        0,
        0.05
    )
    self._cd = math.max(0, self._cd - dt)
    local want = 0
    local inp = ctx and ctx.input
    if inp then
        local w = num(_G, inp.wheel, 0)
        if w ~= 0 then
            local lua_table_invertWheel_7
            if self.invertWheel then
                lua_table_invertWheel_7 = LuaNumber(-w)
            else
                lua_table_invertWheel_7 = w
            end
            want = want + (lua_table_invertWheel_7 > 0 and 1 or -1)
        end
        if inp.zoomIn then
            want = want + 1
        end
        if inp.zoomOut then
            want = want - 1
        end
    end
    if want ~= 0 and self._cd == 0 then
        local dir = want > 0 and -1 or 1
        self.index = bit32.bor(
            clamp(_G, self.index + dir * self.stepStride, self.minIndex, self.maxIndex),
            0
        )
        self.target = clamp(
            _G,
            num(_G, KIndex(self.steps, self.index), self.target),
            self.min,
            self.max
        )
        self._cd = self.cooldown
    end
    self.current = expSmooth(
        _G,
        self.current,
        self.target,
        self.smooth,
        dt
    )
    return self
end
M = CameraZoomController

return M
