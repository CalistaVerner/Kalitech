local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local LuaNumberIsFinite = luaRuntime.LuaNumberIsFinite
local LuaStringTrim = luaRuntime.LuaStringTrim
local lua_require_result_0 = require("./HudUtil.lua")
isObj = lua_require_result_0.isObj
num = lua_require_result_0.num
Anchor = KObject:freeze({
    TL = 0,
    TR = 1,
    BL = 2,
    BR = 3,
    C = 4,
    TC = 5,
    BC = 6,
    LC = 7,
    RC = 8
})
AnchorMap = KObject:freeze({
    tl = Anchor.TL,
    tr = Anchor.TR,
    bl = Anchor.BL,
    br = Anchor.BR,
    c = Anchor.C,
    tc = Anchor.TC,
    bc = Anchor.BC,
    lc = Anchor.LC,
    rc = Anchor.RC
})
function toAnchor(self, v)
    if KTypeOf(v) == "number" and LuaNumberIsFinite(v) then
        return bit32.bor(v, 0)
    end
    local s = string.lower(LuaStringTrim(tostring(v or "tl")))
    local a = AnchorMap[s]
    local lua_temp_1
    if a == nil then
        lua_temp_1 = Anchor.TL
    else
        lua_temp_1 = a
    end
    return lua_temp_1
end
function parsePlace(self, p)
    local lua_isObj_result_2
    if isObj(_G, p) then
        lua_isObj_result_2 = p
    else
        lua_isObj_result_2 = {}
    end
    local c = lua_isObj_result_2
    local anchor = toAnchor(_G, c.anchor)
    local x = num(_G, c.x, 0)
    local y = num(_G, c.y, 0)
    return {anchor = anchor, x = x, y = y}
end
function placeRect(self, vw, vh, w, h, place)
    local p = place or ({anchor = Anchor.TL, x = 0, y = 0})
    local a = toAnchor(_G, p.anchor)
    local x = p.x
    local y = p.y
    repeat
        local lua_switch6 = a
        local lua_cond6 = lua_switch6 == Anchor.TR
        if lua_cond6 then
            x = vw - w + p.x
            break
        end
        lua_cond6 = lua_cond6 or lua_switch6 == Anchor.BL
        if lua_cond6 then
            y = vh - h + p.y
            break
        end
        lua_cond6 = lua_cond6 or lua_switch6 == Anchor.BR
        if lua_cond6 then
            x = vw - w + p.x
            y = vh - h + p.y
            break
        end
        lua_cond6 = lua_cond6 or lua_switch6 == Anchor.C
        if lua_cond6 then
            x = (vw - w) * 0.5 + p.x
            y = (vh - h) * 0.5 + p.y
            break
        end
        lua_cond6 = lua_cond6 or lua_switch6 == Anchor.TC
        if lua_cond6 then
            x = (vw - w) * 0.5 + p.x
            break
        end
        lua_cond6 = lua_cond6 or lua_switch6 == Anchor.BC
        if lua_cond6 then
            x = (vw - w) * 0.5 + p.x
            y = vh - h + p.y
            break
        end
        lua_cond6 = lua_cond6 or lua_switch6 == Anchor.LC
        if lua_cond6 then
            y = (vh - h) * 0.5 + p.y
            break
        end
        lua_cond6 = lua_cond6 or lua_switch6 == Anchor.RC
        if lua_cond6 then
            x = vw - w + p.x
            y = (vh - h) * 0.5 + p.y
            break
        end
        do
            break
        end
    until true
    return {x = x, y = y}
end
function placePoint(self, vw, vh, place)
    local p = place or ({anchor = Anchor.TL, x = 0, y = 0})
    local a = toAnchor(_G, p.anchor)
    local x = p.x
    local y = p.y
    repeat
        local lua_switch8 = a
        local lua_cond8 = lua_switch8 == Anchor.TR
        if lua_cond8 then
            x = vw + p.x
            break
        end
        lua_cond8 = lua_cond8 or lua_switch8 == Anchor.BL
        if lua_cond8 then
            y = vh + p.y
            break
        end
        lua_cond8 = lua_cond8 or lua_switch8 == Anchor.BR
        if lua_cond8 then
            x = vw + p.x
            y = vh + p.y
            break
        end
        lua_cond8 = lua_cond8 or lua_switch8 == Anchor.C
        if lua_cond8 then
            x = vw * 0.5 + p.x
            y = vh * 0.5 + p.y
            break
        end
        lua_cond8 = lua_cond8 or lua_switch8 == Anchor.TC
        if lua_cond8 then
            x = vw * 0.5 + p.x
            break
        end
        lua_cond8 = lua_cond8 or lua_switch8 == Anchor.BC
        if lua_cond8 then
            x = vw * 0.5 + p.x
            y = vh + p.y
            break
        end
        lua_cond8 = lua_cond8 or lua_switch8 == Anchor.LC
        if lua_cond8 then
            y = vh * 0.5 + p.y
            break
        end
        lua_cond8 = lua_cond8 or lua_switch8 == Anchor.RC
        if lua_cond8 then
            x = vw + p.x
            y = vh * 0.5 + p.y
            break
        end
        do
            break
        end
    until true
    return {x = x, y = y}
end
function applyCoordY(self, coord, vh, y)
    if coord == "bottomLeft" then
        return vh - y
    end
    return y
end
M = {
    Anchor = Anchor,
    parsePlace = parsePlace,
    placeRect = placeRect,
    placePoint = placePoint,
    applyCoordY = applyCoordY
}

return M
