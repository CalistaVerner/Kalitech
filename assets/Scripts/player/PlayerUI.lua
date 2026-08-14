local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local LuaNumber = luaRuntime.LuaNumber
local LuaNumberIsFinite = luaRuntime.LuaNumberIsFinite
local LuaArrayIsArray = luaRuntime.LuaArrayIsArray
local LuaClass = luaRuntime.LuaClass
function num(self, v, fb)
    v = LuaNumber(v)
    local lua_Number_isFinite_result_0
    if LuaNumberIsFinite(v) then
        lua_Number_isFinite_result_0 = v
    else
        lua_Number_isFinite_result_0 = fb
    end
    return lua_Number_isFinite_result_0
end
function isObj(self, v)
    return not not v and KTypeOf(v) == "table" and not LuaArrayIsArray(v)
end
function fmt2FromQ(self, q)
    local neg = q < 0
    if neg then
        q = LuaNumber(-q)
    end
    local i = bit32.bor(q / 100, 0)
    local f = q - i * 100
    return ((((neg and "-" or "") .. tostring(i)) .. ".") .. (f < 10 and "0" or "")) .. tostring(f)
end
PlayerUI = LuaClass()
PlayerUI.name = "PlayerUI"
function PlayerUI.prototype.lua_constructor(self, player)
    self.player = player
    local lua_temp_1
    if player and player.cfg and player.cfg.ui then
        lua_temp_1 = player.cfg.ui
    else
        lua_temp_1 = KObject:create(nil)
    end
    local c = lua_temp_1
    self.layerName = tostring(c.layerName or "debug-ui")
    self.anchor = tostring(c.anchor or "tl")
    self.mx = num(_G, c.marginLeft, 10)
    self.my = num(_G, c.marginTop, 10)
    self.w = num(_G, c.w, 280)
    self.padX = num(_G, c.padX, 12)
    self.padY = num(_G, c.padY, 8)
    self.fontTitle = num(_G, c.fontTitle, 16)
    self.fontLine = num(_G, c.fontLine, 14)
    self.gap = num(_G, c.lineGap, 4)
    self.idPrefix = tostring(c.idPrefix or "player.debug")
    local lua_isObj_result_2
    if isObj(_G, c.style) then
        lua_isObj_result_2 = c.style
    else
        lua_isObj_result_2 = {bg = {color = "#0b0f14", alpha = 0.65}, border = {size = 1, color = "#8AA0B6", alpha = 0.45, radius = 8}}
    end
    self.style = lua_isObj_result_2
    self.layer = nil
    self._id = KObject:freeze({
        panel = self.idPrefix .. ".panel",
        title = self.idPrefix .. ".title",
        fps = self.idPrefix .. ".fps",
        pos = self.idPrefix .. ".pos",
        camType = self.idPrefix .. ".camType",
        camYaw = self.idPrefix .. ".camYaw",
        camPitch = self.idPrefix .. ".camPitch",
        worldTime = self.idPrefix .. ".worldTime",
        timeRate = self.idPrefix .. ".timeRate"
    })
    self._cache = {
        fps = -1,
        posXq = 2147483647,
        posYq = 2147483647,
        posZq = 2147483647,
        posStr = "",
        camType = "",
        camYaw = 0 / 0,
        camPitch = 0 / 0,
        worldTime = 0 / 0,
        timeRate = 0 / 0
    }
end
function PlayerUI.prototype.create(self)
    if self.layer then
        return self
    end
    local HUD = self.player and self.player.d and self.player.d.hud
    if not HUD then
        return self
    end
    local layer = HUD:layer(self.layerName)
    self.layer = layer
    layer:spec(
        self:_buildSpec(),
        {relayout = true}
    )
    self:_invalidateCache()
    self:update()
    return self
end
function PlayerUI.prototype._buildSpec(self)
    local id = self._id
    return {
        type = "Panel",
        id = id.panel,
        w = self.w,
        h = 1,
        autoHeight = true,
        padX = self.padX,
        padY = self.padY,
        flow = {fontSize = self.fontLine, gap = self.gap},
        place = {anchor = self.anchor, x = self.mx, y = self.my},
        style = self.style,
        children = {
            {
                type = "Text",
                id = id.title,
                text = "DEBUG",
                fontSize = self.fontTitle,
                color = "#FFFFFF"
            },
            {type = "Text", id = id.fps, text = "FPS: --"},
            {type = "Text", id = id.pos, text = "POS: --"},
            {type = "Text", id = id.camType, text = "CAM(type): --"},
            {type = "Text", id = id.camYaw, text = "CAM(yaw): --"},
            {type = "Text", id = id.camPitch, text = "CAM(pitch): --"},
            {type = "Text", id = id.worldTime, text = "WorldTime: --"},
            {type = "Text", id = id.timeRate, text = "timeRate: --"}
        }
    }
end
function PlayerUI.prototype.refresh(self)
    self:update()
end
function PlayerUI.prototype.update(self, model)
    local layer = self.layer
    if not layer then
        return
    end
    local player = self.player
    local frame = model or player and player.frame
    if not frame then
        return
    end
    local pose = frame.pose
    local view = frame.view
    local eng = player and player.d and player.d.engine
    local lua_temp_3
    if eng and KTypeOf(eng.fps) == "function" then
        lua_temp_3 = bit32.bor(
            eng:fps(),
            0
        )
    else
        lua_temp_3 = 0
    end
    local fps = lua_temp_3
    if fps ~= self._cache.fps then
        self._cache.fps = fps
        layer:setText(
            self._id.fps,
            "FPS: " .. tostring(fps)
        )
    end
    if pose then
        local xq = math.floor(pose.x * 100 + 0.5)
        local yq = math.floor(pose.y * 100 + 0.5)
        local zq = math.floor(pose.z * 100 + 0.5)
        if xq ~= self._cache.posXq or yq ~= self._cache.posYq or zq ~= self._cache.posZq then
            self._cache.posXq = xq
            self._cache.posYq = yq
            self._cache.posZq = zq
            local s = (((("POS: " .. fmt2FromQ(_G, xq)) .. " | ") .. fmt2FromQ(_G, yq)) .. " | ") .. fmt2FromQ(_G, zq)
            self._cache.posStr = s
            layer:setText(self._id.pos, s)
        end
    end
    if view then
        local camType = tostring(view.type)
        if camType ~= self._cache.camType then
            self._cache.camType = camType
            layer:setText(self._id.camType, "CAM(type): " .. camType)
        end
        local yaw = LuaNumber(view.yaw)
        if LuaNumberIsFinite(yaw) and yaw ~= self._cache.camYaw then
            self._cache.camYaw = yaw
            layer:setText(
                self._id.camYaw,
                "CAM(yaw): " .. tostring(yaw)
            )
        end
        local pitch = LuaNumber(view.pitch)
        if LuaNumberIsFinite(pitch) and pitch ~= self._cache.camPitch then
            self._cache.camPitch = pitch
            layer:setText(
                self._id.camPitch,
                "CAM(pitch): " .. tostring(pitch)
            )
        end
    end
    if KTypeOf(ENGINE.world) ~= "nil" and ENGINE.world and KTypeOf(ENGINE.world.getWorldTime) == "function" then
        local wt = ENGINE.world:getWorldTime()
        if wt then
            local worldTime = LuaNumber(wt.worldTime)
            local timeRate = LuaNumber(wt.timeRate)
            if LuaNumberIsFinite(worldTime) and worldTime ~= self._cache.worldTime then
                self._cache.worldTime = worldTime
                layer:setText(
                    self._id.worldTime,
                    "WorldTime: " .. tostring(worldTime)
                )
            end
            if LuaNumberIsFinite(timeRate) and timeRate ~= self._cache.timeRate then
                self._cache.timeRate = timeRate
                layer:setText(
                    self._id.timeRate,
                    "timeRate: " .. tostring(timeRate)
                )
            end
        end
    end
end
function PlayerUI.prototype.destroy(self)
    if not self.layer then
        return
    end
    self.layer:destroy()
    self.layer = nil
    self:_invalidateCache()
end
function PlayerUI.prototype._invalidateCache(self)
    local c = self._cache
    c.fps = -1
    c.posXq = 2147483647
    c.posYq = 2147483647
    c.posZq = 2147483647
    c.posStr = ""
    c.camType = ""
    c.camYaw = 0 / 0
    c.camPitch = 0 / 0
    c.worldTime = 0 / 0
    c.timeRate = 0 / 0
end
M = PlayerUI

return M
