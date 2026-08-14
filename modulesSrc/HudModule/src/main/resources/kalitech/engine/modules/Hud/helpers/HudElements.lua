local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local Strings = luaRuntime.string
local Numbers = luaRuntime.number
local Tables = luaRuntime.table
local Classes = luaRuntime.class
local Error = luaRuntime.Error
local lua_require_result_0 = require("./HudUtil.lua")
num = lua_require_result_0.num
bool = lua_require_result_0.bool
idOf = lua_require_result_0.idOf
function isFn(self, v)
    return KTypeOf(v) == "function"
end
function readPath(self, root, path)
    if root == nil then
        return nil
    end
    local p = Strings:trim(tostring(path or ""))
    if not p then
        return nil
    end
    local v = root
    local parts = Strings:split(p, ".")
    do
        local i = 0
        while i < #parts do
            do
                local k = parts[i + 1]
                if not k then
                    goto lua_continue7
                end
                if v == nil then
                    return nil
                end
                v = v[k]
            end
            ::lua_continue7::
            i = i + 1
        end
    end
    return v
end
Element = Classes:create()
Element.name = "Element"
function Element.prototype.lua_constructor(self, hud, handle, layer, parent)
    self._hud = hud
    self._api = hud._api
    self.handle = handle
    self.id = idOf(_G, handle)
    self.layer = layer
    self.parent = parent or nil
    self.kind = "element"
    self.key = nil
    self._place = nil
    self._w = 0
    self._h = 0
    self._bindPrefix = nil
    self._bindFmt = nil
    self._bindModel = nil
    self._bindPath = nil
    self._bindRead = nil
end
function Element.prototype.text(self, v)
    local lua_self_3 = self._api
    local lua_self_3_setText_4 = lua_self_3.setText
    local lua_self_handle_2 = self.handle
    local lua_v_1 = v
    if lua_v_1 == nil then
        lua_v_1 = ""
    end
    lua_self_3_setText_4(
        lua_self_3,
        lua_self_handle_2,
        tostring(lua_v_1)
    )
    return self
end
function Element.prototype.getText(self)
    if isFn(_G, self._api.getText) then
        local lua_temp_5 = self._api:getText(self.handle)
        if lua_temp_5 == nil then
            lua_temp_5 = ""
        end
        return tostring(lua_temp_5)
    end
    return ""
end
function Element.prototype.visible(self, v)
    self._api:setVisible(self.handle, not not v)
    return self
end
function Element.prototype.pos(self, x, y)
    self._api:setPosition(
        self.handle,
        num(_G, x, 0),
        num(_G, y, 0)
    )
    return self
end
function Element.prototype.size(self, w, h)
    self._w = num(_G, w, 0)
    self._h = num(_G, h, 0)
    self._api:setSize(self.handle, self._w, self._h)
    if self.layer and isFn(_G, self.layer._markDirty) then
        self.layer:_markDirty()
    end
    return self
end
function Element.prototype.bg(self, r, g, b, a)
    self._api:setBgColor(
        self.handle,
        num(_G, r, 0),
        num(_G, g, 0),
        num(_G, b, 0),
        num(_G, a, 1)
    )
    return self
end
function Element.prototype.color(self, r, g, b, a)
    self._api:setTextColor(
        self.handle,
        num(_G, r, 1),
        num(_G, g, 1),
        num(_G, b, 1),
        num(_G, a, 1)
    )
    return self
end
function Element.prototype.fontSize(self, px)
    if isFn(_G, self._api.setFontSize) then
        self._api:setFontSize(
            self.handle,
            math.max(
                6,
                num(_G, px, 14)
            )
        )
        if self.layer and isFn(_G, self.layer._markDirty) then
            self.layer:_markDirty()
        end
    end
    return self
end
function Element.prototype.remove(self)
    if self.layer then
        local lua_self_7 = self.layer
        local lua_self_7_drop_8 = lua_self_7.drop
        local lua_self_key_6 = self.key
        if lua_self_key_6 == nil then
            lua_self_key_6 = self.id
        end
        lua_self_7_drop_8(lua_self_7, lua_self_key_6, true)
    else
        self._api:remove(self.handle)
    end
    return nil
end
function Element.prototype.bindPrefix(self, prefix)
    local lua_temp_9
    if prefix == nil then
        lua_temp_9 = nil
    else
        lua_temp_9 = tostring(prefix)
    end
    self._bindPrefix = lua_temp_9
    return self
end
function Element.prototype.bindFormat(self, fn)
    local lua_temp_10
    if KTypeOf(fn) == "function" then
        lua_temp_10 = fn
    else
        lua_temp_10 = nil
    end
    self._bindFmt = lua_temp_10
    return self
end
function Element.prototype.bind(self, model, path, fmtOrNull)
    self._bindModel = model or nil
    local lua_temp_11
    if path == nil then
        lua_temp_11 = nil
    else
        lua_temp_11 = tostring(path)
    end
    self._bindPath = lua_temp_11
    self._bindRead = nil
    if KTypeOf(fmtOrNull) == "function" then
        self._bindFmt = fmtOrNull
    end
    return self
end
function Element.prototype.bindRead(self, model, fn, fmtOrNull)
    self._bindModel = model or nil
    self._bindPath = nil
    local lua_temp_12
    if KTypeOf(fn) == "function" then
        lua_temp_12 = fn
    else
        lua_temp_12 = nil
    end
    self._bindRead = lua_temp_12
    if KTypeOf(fmtOrNull) == "function" then
        self._bindFmt = fmtOrNull
    end
    return self
end
function Element.prototype.pull(self)
    if not self._bindModel then
        return self
    end
    local v
    if self._bindRead then
        v = self:_bindRead(self._bindModel)
    elseif self._bindPath then
        v = readPath(_G, self._bindModel, self._bindPath)
    else
        return self
    end
    self:value(v)
    return self
end
function Element.prototype._formatValue(self, v)
    local s
    if self._bindFmt then
        s = self:_bindFmt(v)
    else
        s = v == nil and "" or tostring(v)
    end
    if self._bindPrefix ~= nil then
        local lua_self__bindPrefix_14 = self._bindPrefix
        local lua_s_13 = s
        if lua_s_13 == nil then
            lua_s_13 = ""
        end
        s = lua_self__bindPrefix_14 .. tostring(lua_s_13)
    end
    local lua_s_15 = s
    if lua_s_15 == nil then
        lua_s_15 = ""
    end
    return tostring(lua_s_15)
end
function Element.prototype.value(self, v)
    if self.kind == "slider" then
        if v == nil then
            if isFn(_G, self._api.getSliderValue) then
                return Numbers:coerce(self._api:getSliderValue(self.handle)) or 0
            end
            return 0
        end
        if isFn(_G, self._api.setSliderValue) then
            self._api:setSliderValue(
                self.handle,
                num(_G, v, 0)
            )
        end
        return self
    end
    if v == nil then
        return self:getText()
    end
    local s = self:_formatValue(v)
    self._api:setText(self.handle, s)
    return self
end
function Element.prototype.checked(self, v)
    if not isFn(_G, self._api.setChecked) or not isFn(_G, self._api.isChecked) then
        local lua_temp_16
        if v == nil then
            lua_temp_16 = false
        else
            lua_temp_16 = self
        end
        return lua_temp_16
    end
    if v == nil then
        return not not self._api:isChecked(self.handle)
    end
    self._api:setChecked(self.handle, not not v)
    return self
end
function Element.prototype._setPlace(self, place)
    self._place = place or nil
    return self
end
Panel = Classes:create()
Panel.name = "Panel"
Classes:extend(Panel, Element)
function Panel.prototype.lua_constructor(self, hud, handle, layer, parent)
    Element.prototype.lua_constructor(
        self,
        hud,
        handle,
        layer,
        parent
    )
    self.kind = "panel"
    self._flow = {padX = 0, padY = 0, gap = 0, fontSize = nil}
    self._stack = {}
end
function Panel.prototype.flow(self, cfg)
    if cfg == nil then
        cfg = {}
    end
    local c = cfg or ({})
    self._flow.padX = num(_G, c.padX, self._flow.padX)
    self._flow.padY = num(_G, c.padY, self._flow.padY)
    self._flow.gap = num(_G, c.gap, self._flow.gap)
    local lua_self__flow_18 = self._flow
    local lua_temp_17
    if c.fontSize ~= nil then
        lua_temp_17 = num(_G, c.fontSize, 14)
    else
        lua_temp_17 = self._flow.fontSize
    end
    lua_self__flow_18.fontSize = lua_temp_17
    if self.layer and isFn(_G, self.layer._markDirty) then
        self.layer:_markDirty()
    end
    return self
end
function Panel.prototype.stack(self, id, text, cfg)
    if cfg == nil then
        cfg = {}
    end
    if not self.layer or KTypeOf(self.layer.stackText) ~= "function" then
        error(
            Classes:construct(Error, "[HUD] Panel.stack requires Layer.stackText"),
            0
        )
    end
    return self.layer:stackText(
        self,
        Tables:merge({}, cfg or ({}), {id = id, text = text})
    )
end
M = {Element = Element, Panel = Panel}

return M
