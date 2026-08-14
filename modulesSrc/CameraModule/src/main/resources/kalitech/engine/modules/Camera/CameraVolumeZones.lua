local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local Arrays = luaRuntime.array
local Classes = luaRuntime.class
local Error = luaRuntime.Error
U = require("./camUtil.lua")
ZC = require("./CameraZonesContract.lua")
function clamp01(self, v)
    return v < 0 and 0 or (v > 1 and 1 or v)
end
function resetState(self, st)
    st.id = nil
    st.weight = 0
    st.priority = -2147483648
    st.overrides = nil
    return st
end
function pointInAabb(self, px, py, pz, a)
    return px >= a.min.x and px <= a.max.x and py >= a.min.y and py <= a.max.y and pz >= a.min.z and pz <= a.max.z
end
function aabbWeight(self, px, py, pz, a, blend)
    if not pointInAabb(
        _G,
        px,
        py,
        pz,
        a
    ) then
        return 0
    end
    if not (blend > 0) then
        return 1
    end
    local dx = math.min(px - a.min.x, a.max.x - px)
    local dy = math.min(py - a.min.y, a.max.y - py)
    local dz = math.min(pz - a.min.z, a.max.z - pz)
    return clamp01(
        _G,
        math.min(dx, dy, dz) / blend
    )
end
CameraVolumeZones = Classes:create()
CameraVolumeZones.name = "CameraVolumeZones"
function CameraVolumeZones.prototype.lua_constructor(self, player)
    if not player then
        error(
            Classes:construct(Error, "[camera][zones] player is required"),
            0
        )
    end
    self.player = player
    self.enabled = false
    self._zones = {}
    self.state = resetState(_G, {id = nil, weight = 0, priority = 0, overrides = nil})
end
function CameraVolumeZones.prototype.configureFromPlayerCfg(self)
    local lua_temp_0
    if self.player.cfg and self.player.cfg.camera then
        lua_temp_0 = self.player.cfg.camera
    else
        lua_temp_0 = nil
    end
    local cam = lua_temp_0
    local lua_cam_1
    if cam then
        lua_cam_1 = cam.volumeZones
    else
        lua_cam_1 = nil
    end
    local vz = lua_cam_1
    if vz == nil then
        self.enabled = false
        Arrays:setLength(self._zones, 0)
        resetState(_G, self.state)
        return
    end
    local v = ZC:validateZonesConfig(vz)
    self.enabled = v.enabled
    self._zones = v.zones
    resetState(_G, self.state)
end
function CameraVolumeZones.prototype.update(self, bodyPos)
    if not self.enabled then
        return resetState(_G, self.state)
    end
    local px = U:vx(bodyPos, 0)
    local py = U:vy(bodyPos, 0)
    local pz = U:vz(bodyPos, 0)
    local best = nil
    local bestW = 0
    local bestPr = -2147483648
    do
        local i = 0
        while i < #self._zones do
            do
                local z = self._zones[i + 1]
                local w = aabbWeight(
                    _G,
                    px,
                    py,
                    pz,
                    z.shape.aabb,
                    z.blend
                )
                if not (w > 0) then
                    goto lua_continue15
                end
                if z.priority > bestPr or z.priority == bestPr and w > bestW then
                    best = z
                    bestW = w
                    bestPr = z.priority
                end
            end
            ::lua_continue15::
            i = i + 1
        end
    end
    if not best then
        return resetState(_G, self.state)
    end
    self.state.id = best.id
    self.state.weight = bestW
    self.state.priority = bestPr
    self.state.overrides = best.overrides
    return self.state
end
function CameraVolumeZones.prototype.blendedOverrides(self, base)
    local st = self.state
    if not self.enabled or not st.overrides or not (st.weight > 0) then
        return base or nil
    end
    local w = st.weight
    local over = st.overrides
    local out = KObject:create(nil)
    if base then
        for k in pairs(base) do
            out[k] = base[k]
        end
    end
    for k in pairs(over) do
        do
            local v = over[k]
            if v and KTypeOf(v) == "table" and v.x ~= nil and v.y ~= nil and v.z ~= nil then
                local b = out[k]
                if b and KTypeOf(b) == "table" and b.x ~= nil and b.y ~= nil and b.z ~= nil then
                    out[k] = {x = b.x + (v.x - b.x) * w, y = b.y + (v.y - b.y) * w, z = b.z + (v.z - b.z) * w}
                else
                    out[k] = {x = v.x, y = v.y, z = v.z}
                end
                goto lua_continue24
            end
            if KTypeOf(v) == "number" then
                local b = out[k]
                local lua_temp_2
                if KTypeOf(b) == "number" then
                    lua_temp_2 = b + (v - b) * w
                else
                    lua_temp_2 = v
                end
                out[k] = lua_temp_2
                goto lua_continue24
            end
            if KTypeOf(v) == "boolean" then
                local lua_temp_4
                if w >= 0.5 then
                    lua_temp_4 = v
                else
                    local lua_temp_3
                    if out[k] ~= nil then
                        lua_temp_3 = out[k]
                    else
                        lua_temp_3 = v
                    end
                    lua_temp_4 = lua_temp_3
                end
                out[k] = lua_temp_4
                goto lua_continue24
            end
            out[k] = v
        end
        ::lua_continue24::
    end
    return out
end
M = CameraVolumeZones

return M
