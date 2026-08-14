local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local Error = luaRuntime.Error
local LuaConstruct = luaRuntime.LuaConstruct
local LuaNumberIsFinite = luaRuntime.LuaNumberIsFinite
local LuaNumber = luaRuntime.LuaNumber
local LuaArraySort = luaRuntime.LuaArraySort
function isFn(self, f)
    return KTypeOf(f) == "function"
end
function resolveEngineApi(self, ctx)
    if not ctx or not ctx.engine or not isFn(_G, ctx.engine.api) then
        error(
            LuaConstruct(Error, "[towers] ctx.engine.api() is required"),
            0
        )
    end
    local api = ctx.engine:api()
    if not api then
        error(
            LuaConstruct(Error, "[towers] ctx.engine.api() returned null"),
            0
        )
    end
    return api
end
function randNum(self, min, max, opts)
    if not LuaNumberIsFinite(min) or not LuaNumberIsFinite(max) then
        error(
            LuaConstruct(Error, "randNum(min,max): min/max must be finite numbers"),
            0
        )
    end
    if max < min then
        local t = min
        min = max
        max = t
    end
    opts = opts or KObject:create(nil)
    local lua_temp_0
    if KTypeOf(opts.rng) == "function" then
        lua_temp_0 = opts.rng
    else
        lua_temp_0 = KMath.random
    end
    local rng = lua_temp_0
    local isInt = not not opts.int
    local step = LuaNumber(opts.step) or 0
    local v = rng() * (max - min) + min
    if step > 0 then
        v = math.floor(v / step + 0.5) * step
    end
    if isInt then
        v = math.floor(v)
        if v > max then
            v = max
        end
        if v < min then
            v = min
        end
    end
    return v
end
function buildEm(self, engine, state)
    if not state.created then
        state.created = {}
    end
    local TOTAL = 70
    local TOWERS = 6
    local BOXES_PER_TOWER = math.floor(TOTAL / TOWERS)
    local BASE_X = 120
    local BASE_Z = -300
    local TOWER_SPACING = 8
    local density = 4.5
    local weightBias = 0.6
    local boxId = 0
    do
        local t = 0
        while t < TOWERS do
            local x = BASE_X + t * TOWER_SPACING
            local z = BASE_Z
            local sizes = {}
            do
                local i = 0
                while i < BOXES_PER_TOWER do
                    sizes[#sizes + 1] = randNum(_G, 1, 5)
                    i = i + 1
                end
            end
            LuaArraySort(
                sizes,
                function(lua_, a, b) return b - a end
            )
            local y = 0
            do
                local i = 0
                while i < #sizes do
                    local size = sizes[i + 1]
                    y = y + size / 2
                    local mass = density * size ^ 3 * (1 + size * weightBias)
                    local lua_self_3 = ENGINE.mesh
                    local lua_self_2 = lua_self_3["box$"](lua_self_3):size(size)
                    local lua_self_2_name_4 = lua_self_2.name
                    local lua_boxId_1 = boxId
                    boxId = lua_boxId_1 + 1
                    local h = lua_self_2_name_4(
                        lua_self_2,
                        "box-" .. tostring(lua_boxId_1)
                    ):pos(x, y, z):material(ENGINE.material:getMaterial("box")):physics(mass, {lockRotation = false}):create()
                    KArrayOps.push(state.created, h)
                    y = y + size / 2
                    i = i + 1
                end
            end
            t = t + 1
        end
    end
end
M = {
    _state = nil,
    init = function(self, ctx)
        local E = resolveEngineApi(_G, ctx)
        self._state = {created = {}}
        buildEm(_G, E, self._state)
    end,
    update = function(self, ctx, tpf)
    end,
    destroy = function(self)
    end
}

return M
