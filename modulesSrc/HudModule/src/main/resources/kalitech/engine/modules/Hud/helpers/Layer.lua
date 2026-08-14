local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local Collections = luaRuntime.collection
local Arrays = luaRuntime.array
local Strings = luaRuntime.string
local Numbers = luaRuntime.number
local Tables = luaRuntime.table
local Classes = luaRuntime.class
local Iterators = luaRuntime.iterator
local Error = luaRuntime.Error
local lua_require_result_0 = require("./HudUtil.lua")
isObj = lua_require_result_0.isObj
num = lua_require_result_0.num
bool = lua_require_result_0.bool
idOf = lua_require_result_0.idOf
local lua_require_result_1 = require("./HudPlacement.lua")
applyCoordY = lua_require_result_1.applyCoordY
parsePlace = lua_require_result_1.parsePlace
placeRect = lua_require_result_1.placeRect
placePoint = lua_require_result_1.placePoint
local lua_require_result_2 = require("./HudElements.lua")
Element = lua_require_result_2.Element
Panel = lua_require_result_2.Panel
local lua_require_result_3 = require("./HudBuilder.lua")
buildFromSpec = lua_require_result_3.buildFromSpec
function isFn(self, v)
    return KTypeOf(v) == "function"
end
function q2(self, v)
    return math.floor(v * 100 + 0.5)
end
function fmtQ2(self, q)
    local neg = q < 0
    if neg then
        q = Numbers:coerce(-q)
    end
    local i = bit32.bor(q / 100, 0)
    local f = q - i * 100
    return ((((neg and "-" or "") .. tostring(i)) .. ".") .. (f < 10 and "0" or "")) .. tostring(f)
end
function prefixSpecInPlace(self, spec, prefix)
    if not spec or not prefix then
        return spec
    end
    local p = tostring(prefix)
    local dot = p .. "."
    local stack = {}
    if Arrays:isArray(spec) then
        do
            local i = #spec - 1
            while i >= 0 do
                stack[#stack + 1] = spec[i + 1]
                i = i - 1
            end
        end
    else
        stack[#stack + 1] = spec
    end
    while #stack > 0 do
        do
            local s = table.remove(stack)
            if not s or KTypeOf(s) ~= "table" then
                goto lua_continue12
            end
            if not KObject:isExtensible(s) then
                error(
                    Classes:construct(Error, "[HUD] ns.spec(): spec object is not extensible (frozen/sealed). Provide mutable spec or use builder-level prefixing."),
                    0
                )
            end
            if s.id ~= nil then
                local id = tostring(s.id)
                if id and (string.find(id, dot, nil, true) or 0) - 1 ~= 0 then
                    s.id = dot .. id
                end
            end
            if s.parent ~= nil and (KTypeOf(s.parent) == "string" or KTypeOf(s.parent) == "number") then
                local pid = tostring(s.parent)
                if pid and (string.find(pid, dot, nil, true) or 0) - 1 ~= 0 then
                    s.parent = dot .. pid
                end
            end
            local kids = s.children
            if not kids then
                goto lua_continue12
            end
            if Arrays:isArray(kids) then
                do
                    local i = #kids - 1
                    while i >= 0 do
                        stack[#stack + 1] = kids[i + 1]
                        i = i - 1
                    end
                end
            elseif KTypeOf(kids) == "table" then
                stack[#stack + 1] = kids
            end
        end
        ::lua_continue12::
    end
    return spec
end
LayerBindings = Classes:create()
LayerBindings.name = "LayerBindings"
function LayerBindings.prototype.lua_constructor(self, ns)
    self._ns = ns
    self._n = 0
    self._kind = {}
    self._id = {}
    self._read = {}
    self._fmt = {}
    self._lastN = {}
    self._lastS = {}
    self._vx = {}
    self._vy = {}
    self._vz = {}
end
function LayerBindings.prototype.text(self, id, read, fmt)
    local lua_self_4, lua_n_5 = self, "_n"
    local lua_self__n_6 = lua_self_4[lua_n_5]
    lua_self_4[lua_n_5] = lua_self__n_6 + 1
    local i = lua_self__n_6
    self._kind[i + 1] = 0
    self._id[i + 1] = tostring(id)
    self._read[i + 1] = read
    self._fmt[i + 1] = fmt or nil
    self._lastS[i + 1] = "\0"
    return self
end
function LayerBindings.prototype.int(self, id, read, prefix)
    local p = tostring(prefix or "")
    return self:text(
        id,
        read,
        function(lua_, v) return p .. tostring(bit32.bor(v, 0)) end
    )
end
function LayerBindings.prototype.vec3q2(self, id, readObj, label)
    local lua_self_7, lua_n_8 = self, "_n"
    local lua_self__n_9 = lua_self_7[lua_n_8]
    lua_self_7[lua_n_8] = lua_self__n_9 + 1
    local i = lua_self__n_9
    self._kind[i + 1] = 1
    self._id[i + 1] = tostring(id)
    self._read[i + 1] = readObj
    self._fmt[i + 1] = tostring(label or "")
    self._vx[i + 1] = 2147483647
    self._vy[i + 1] = 2147483647
    self._vz[i + 1] = 2147483647
    return self
end
function LayerBindings.prototype.run(self, model)
    local ns = self._ns
    do
        local i = 0
        while i < self._n do
            do
                local kind = self._kind[i + 1]
                if kind == 0 then
                    local lua_self_10 = self._read
                    local v = lua_self_10[i + 1](lua_self_10, model)
                    if KTypeOf(v) == "number" then
                        local last = self._lastN[i + 1]
                        if v == last or Numbers:isNaN(v) and Numbers:isNaN(last) then
                            goto lua_continue31
                        end
                        self._lastN[i + 1] = v
                        local lua_table__fmt_i_12
                        if self._fmt[i + 1] then
                            local lua_self_11 = self._fmt
                            lua_table__fmt_i_12 = lua_self_11[i + 1](lua_self_11, v)
                        else
                            lua_table__fmt_i_12 = tostring(v)
                        end
                        local s = lua_table__fmt_i_12
                        if s ~= self._lastS[i + 1] then
                            self._lastS[i + 1] = s
                            ns:setText(self._id[i + 1], s)
                        end
                        goto lua_continue31
                    end
                    local lua_table__fmt_i_15
                    if self._fmt[i + 1] then
                        local lua_self_13 = self._fmt
                        lua_table__fmt_i_15 = lua_self_13[i + 1](lua_self_13, v)
                    else
                        local lua_v_14 = v
                        if lua_v_14 == nil then
                            lua_v_14 = ""
                        end
                        lua_table__fmt_i_15 = tostring(lua_v_14)
                    end
                    local s = lua_table__fmt_i_15
                    if s == self._lastS[i + 1] then
                        goto lua_continue31
                    end
                    self._lastS[i + 1] = s
                    ns:setText(self._id[i + 1], s)
                    goto lua_continue31
                end
                local lua_self_16 = self._read
                local o = lua_self_16[i + 1](lua_self_16, model)
                if not o then
                    goto lua_continue31
                end
                local xq = q2(_G, o.x)
                local yq = q2(_G, o.y)
                local zq = q2(_G, o.z)
                if xq == self._vx[i + 1] and yq == self._vy[i + 1] and zq == self._vz[i + 1] then
                    goto lua_continue31
                end
                self._vx[i + 1] = xq
                self._vy[i + 1] = yq
                self._vz[i + 1] = zq
                local label = self._fmt[i + 1]
                ns:setText(
                    self._id[i + 1],
                    ((((tostring(label) .. fmtQ2(_G, xq)) .. " | ") .. fmtQ2(_G, yq)) .. " | ") .. fmtQ2(_G, zq)
                )
            end
            ::lua_continue31::
            i = i + 1
        end
    end
end
PanelBuilder = Classes:create()
PanelBuilder.name = "PanelBuilder"
function PanelBuilder.prototype.lua_constructor(self, layer, panel)
    self._layer = layer
    self._panel = panel
end
function PanelBuilder.prototype.flow(self, cfg)
    self._panel:flow(cfg or ({}))
    return self
end
function PanelBuilder.prototype.stack(self, id, text, cfg)
    self._panel:stack(id, text, cfg or ({}))
    return self
end
function PanelBuilder.prototype.done(self)
    if self._layer then
        self._layer:flushLayout()
    end
    return self._panel
end
Layer = Classes:create()
Layer.name = "Layer"
function Layer.prototype.lua_constructor(self, hud, handle)
    self._hud = hud
    self._api = hud._api
    self.handle = handle
    self.id = idOf(_G, handle)
    self._reg = KObject:create(nil)
    self._regKeys = {}
    self._placed = {}
    self._lastVp = {w = 0, h = 0}
    self._radioGroups = KObject:create(nil)
    self._dirtyLayout = false
    self._autoLayout = true
    self.__specEpochCounter = 0
end
function Layer.prototype._disposeLocal(self)
    self._reg = KObject:create(nil)
    Arrays:setLength(self._regKeys, 0)
    self._radioGroups = KObject:create(nil)
    Arrays:setLength(self._placed, 0)
    self._dirtyLayout = false
    self._autoLayout = true
    self.__specEpochCounter = 0
    self._lastVp.w = 0
    self._lastVp.h = 0
    self._hud = nil
    self._api = nil
    self.handle = nil
end
function Layer.prototype.destroy(self)
    local api = self._api
    local h = self.handle
    self:_disposeLocal()
    if api and h then
        api:destroyLayer(h)
    end
end
function Layer.prototype.clear(self)
    self._reg = KObject:create(nil)
    Arrays:setLength(self._regKeys, 0)
    self._radioGroups = KObject:create(nil)
    Arrays:setLength(self._placed, 0)
    self._dirtyLayout = false
    self.__specEpochCounter = 0
    self._api:clearLayer(self.handle)
end
function Layer.prototype.buildPanel(self, cfg)
    if cfg == nil then
        cfg = {}
    end
    local p = self:panel(cfg)
    return Classes:construct(PanelBuilder, self, p)
end
function Layer.prototype.spec(self, spec, opts)
    if opts == nil then
        opts = {}
    end
    return buildFromSpec(_G, self, spec, opts or ({}))
end
function Layer.prototype.ns(self, prefix)
    local p = Strings:trim(tostring(prefix or ""))
    if not p then
        error(
            Classes:construct(Error, "[HUD] layer.ns(prefix): prefix is required"),
            0
        )
    end
    local layer = self
    local function pid(self, id)
        return (p .. ".") .. tostring(id)
    end
    return {
        prefix = p,
        get = function(self, id)
            return layer:get(pid(_G, id))
        end,
        has = function(self, id)
            return layer:has(pid(_G, id))
        end,
        drop = function(self, id, remove)
            return layer:drop(
                pid(_G, id),
                not not remove
            )
        end,
        setText = function(self, id, text)
            return layer:setText(
                pid(_G, id),
                text
            )
        end,
        setValue = function(self, id, v)
            return layer:setValue(
                pid(_G, id),
                v
            )
        end,
        setVisible = function(self, id, v)
            return layer:setVisible(
                pid(_G, id),
                v
            )
        end,
        text = function(self, cfg)
            local lua_isObj_result_17
            if isObj(_G, cfg) then
                lua_isObj_result_17 = cfg
            else
                lua_isObj_result_17 = {}
            end
            local c = lua_isObj_result_17
            if c.id == nil then
                error(
                    Classes:construct(Error, "[HUD] ns.text: cfg.id is required"),
                    0
                )
            end
            c.id = pid(_G, c.id)
            return layer:text(c)
        end,
        panel = function(self, cfg)
            local lua_isObj_result_18
            if isObj(_G, cfg) then
                lua_isObj_result_18 = cfg
            else
                lua_isObj_result_18 = {}
            end
            local c = lua_isObj_result_18
            if c.id == nil then
                error(
                    Classes:construct(Error, "[HUD] ns.panel: cfg.id is required"),
                    0
                )
            end
            c.id = pid(_G, c.id)
            return layer:panel(c)
        end,
        spec = function(self, spec0, opts0)
            local lua_temp_19
            if opts0 and KTypeOf(opts0) == "table" then
                lua_temp_19 = opts0
            else
                lua_temp_19 = {}
            end
            local o = lua_temp_19
            prefixSpecInPlace(_G, spec0, p)
            return layer:spec(spec0, o)
        end,
        bind = function(self)
            return Classes:construct(LayerBindings, self)
        end
    }
end
function Layer.prototype._regPut(self, key, el)
    local k = tostring(key)
    el.key = k
    if self._reg[k] == nil then
        local lua_self__regKeys_20 = self._regKeys
        lua_self__regKeys_20[#lua_self__regKeys_20 + 1] = k
    end
    self._reg[k] = el
    return el
end
function Layer.prototype._regRemoveKey(self, k)
    local keys = self._regKeys
    do
        local i = 0
        while i < #keys do
            if keys[i + 1] == k then
                keys[i + 1] = keys[#keys]
                table.remove(keys)
                return
            end
            i = i + 1
        end
    end
end
function Layer.prototype.get(self, key)
    return self._reg[tostring(key)] or nil
end
function Layer.prototype.has(self, key)
    return not not self._reg[tostring(key)]
end
function Layer.prototype.drop(self, key, remove)
    if remove == nil then
        remove = false
    end
    local k = tostring(key)
    local el = self._reg[k]
    if not el then
        return nil
    end
    Tables:remove(self._reg, k)
    self:_regRemoveKey(k)
    if el.kind == "radio" and el._radioGroup then
        local s = self._radioGroups[el._radioGroup]
        if s then
            s:delete(k)
        end
    end
    if remove then
        self._api:remove(el.handle)
    end
    return el
end
function Layer.prototype.setText(self, id, text)
    local el = self:get(id)
    if el then
        el:text(text)
    end
    return el or nil
end
function Layer.prototype.setValue(self, id, v)
    local el = self:get(id)
    if el then
        el:value(v)
    end
    return el or nil
end
function Layer.prototype.setVisible(self, id, v)
    local el = self:get(id)
    if el then
        el:visible(v)
    end
    return el or nil
end
function Layer.prototype.pullAll(self)
    local keys = self._regKeys
    do
        local i = 0
        while i < #keys do
            local el = self._reg[keys[i + 1]]
            if el and isFn(_G, el.pull) then
                el:pull()
            end
            i = i + 1
        end
    end
    return self
end
function Layer.prototype._vp(self)
    local vp = self._hud:viewport()
    self._lastVp.w = bit32.bor(vp.w, 0)
    self._lastVp.h = bit32.bor(vp.h, 0)
    return self._lastVp
end
function Layer.prototype._coord(self)
    local lua_temp_21
    if self._hud and self._hud.META then
        lua_temp_21 = self._hud.META.coord
    else
        lua_temp_21 = "topLeft"
    end
    local c = lua_temp_21
    return tostring(c or "topLeft")
end
function Layer.prototype.autoLayout(self, v)
    if v == nil then
        v = true
    end
    self._autoLayout = not not v
    return self
end
function Layer.prototype._markDirty(self)
    self._dirtyLayout = true
    if self._autoLayout then
        self:flushLayout()
    end
end
function Layer.prototype.flushLayout(self)
    if not self._dirtyLayout then
        return self
    end
    self._dirtyLayout = false
    return self:relayout()
end
function Layer.prototype._trackPlaced(self, el)
    local lua_self__placed_22 = self._placed
    lua_self__placed_22[#lua_self__placed_22 + 1] = el
    return el
end
function Layer.prototype.relayout(self)
    local vp = self:_vp()
    local coord = self:_coord()
    do
        local i = 0
        while i < #self._placed do
            do
                local el = self._placed[i + 1]
                if not el or not el._place then
                    goto lua_continue99
                end
                if el.kind == "panel" or el.kind == "container" or el.kind == "input" or el.kind == "slider" then
                    local p = placeRect(
                        _G,
                        vp.w,
                        vp.h,
                        el._w,
                        el._h,
                        el._place
                    )
                    local y = applyCoordY(_G, coord, vp.h, p.y)
                    self._api:setPosition(el.handle, p.x, y)
                else
                    local p = placePoint(_G, vp.w, vp.h, el._place)
                    local y = applyCoordY(_G, coord, vp.h, p.y)
                    self._api:setPosition(el.handle, p.x, y)
                end
            end
            ::lua_continue99::
            i = i + 1
        end
    end
    return self
end
function Layer.prototype.container(self, cfg)
    if cfg == nil then
        cfg = {}
    end
    local lua_isObj_result_23
    if isObj(_G, cfg) then
        lua_isObj_result_23 = cfg
    else
        lua_isObj_result_23 = {}
    end
    local c = lua_isObj_result_23
    local vp = self:_vp()
    local coord = self:_coord()
    local id = c.id
    local parent = c.parent or nil
    local lua_c_place_24
    if c.place then
        lua_c_place_24 = parsePlace(_G, c.place)
    else
        lua_c_place_24 = nil
    end
    local place = lua_c_place_24
    local x = num(_G, c.x, 0)
    local y = num(_G, c.y, 0)
    if place then
        local p = placeRect(
            _G,
            vp.w,
            vp.h,
            0,
            0,
            place
        )
        x = p.x
        y = p.y
    end
    y = applyCoordY(_G, coord, vp.h, y)
    local lua_parent_25
    if parent then
        lua_parent_25 = parent.handle
    else
        lua_parent_25 = nil
    end
    local ph = lua_parent_25
    local lua_ph_26
    if ph then
        lua_ph_26 = self._api:addContainer(self.handle, ph, x, y)
    else
        lua_ph_26 = self._api:addContainer(self.handle, x, y)
    end
    local h = lua_ph_26
    local el = Classes:construct(
        Element,
        self._hud,
        h,
        self,
        parent
    )
    el.kind = "container"
    el:_setPlace(place)
    if place then
        self:_trackPlaced(el)
        self:_markDirty()
    end
    if id ~= nil then
        self:_regPut(id, el)
    end
    return el
end
function Layer.prototype.panel(self, cfg)
    if cfg == nil then
        cfg = {}
    end
    local lua_isObj_result_27
    if isObj(_G, cfg) then
        lua_isObj_result_27 = cfg
    else
        lua_isObj_result_27 = {}
    end
    local c = lua_isObj_result_27
    local vp = self:_vp()
    local coord = self:_coord()
    local id = c.id
    local parent = c.parent or nil
    local w = num(_G, c.w, 0)
    local h = num(_G, c.h, 0)
    local lua_c_place_28
    if c.place then
        lua_c_place_28 = parsePlace(_G, c.place)
    else
        lua_c_place_28 = nil
    end
    local place = lua_c_place_28
    local x = num(_G, c.x, 0)
    local y = num(_G, c.y, 0)
    if place then
        local p = placeRect(
            _G,
            vp.w,
            vp.h,
            w,
            h,
            place
        )
        x = p.x
        y = p.y
    end
    y = applyCoordY(_G, coord, vp.h, y)
    local lua_parent_29
    if parent then
        lua_parent_29 = parent.handle
    else
        lua_parent_29 = nil
    end
    local ph = lua_parent_29
    local lua_ph_30
    if ph then
        lua_ph_30 = self._api:addPanel(
            self.handle,
            ph,
            x,
            y,
            w,
            h
        )
    else
        lua_ph_30 = self._api:addPanel(
            self.handle,
            x,
            y,
            w,
            h
        )
    end
    local hh = lua_ph_30
    local panel = Classes:construct(
        Panel,
        self._hud,
        hh,
        self,
        parent
    )
    panel._w = w
    panel._h = h
    panel._autoHeight = bool(_G, c.autoHeight, false)
    panel._autoMinH = num(_G, c.minH, 0)
    local lua_isObj_result_31
    if isObj(_G, c.flow) then
        lua_isObj_result_31 = c.flow
    else
        lua_isObj_result_31 = nil
    end
    local flowCfg = lua_isObj_result_31
    if flowCfg then
        panel:flow(flowCfg)
    elseif c.padX ~= nil or c.padY ~= nil or c.gap ~= nil or c.fontSize ~= nil then
        local lua_panel_flow_36 = panel.flow
        local lua_temp_32
        if c.padX ~= nil then
            lua_temp_32 = c.padX
        else
            lua_temp_32 = nil
        end
        local lua_temp_33
        if c.padY ~= nil then
            lua_temp_33 = c.padY
        else
            lua_temp_33 = nil
        end
        local lua_temp_34
        if c.gap ~= nil then
            lua_temp_34 = c.gap
        else
            lua_temp_34 = nil
        end
        local lua_temp_35
        if c.fontSize ~= nil then
            lua_temp_35 = c.fontSize
        else
            lua_temp_35 = nil
        end
        lua_panel_flow_36(panel, {padX = lua_temp_32, padY = lua_temp_33, gap = lua_temp_34, fontSize = lua_temp_35})
    end
    if c.bg then
        panel:bg(
            num(_G, c.bg.r, 0),
            num(_G, c.bg.g, 0),
            num(_G, c.bg.b, 0),
            num(_G, c.bg.a, 1)
        )
    end
    if c.fontSize ~= nil then
        panel:fontSize(c.fontSize)
    end
    if place then
        panel:_setPlace(place)
        self:_trackPlaced(panel)
        self:_markDirty()
    end
    if id ~= nil then
        self:_regPut(id, panel)
    end
    return panel
end
function Layer.prototype.rect(self, cfg)
    if cfg == nil then
        cfg = {}
    end
    return self:panel(cfg)
end
function Layer.prototype.text(self, cfg)
    if cfg == nil then
        cfg = {}
    end
    local lua_isObj_result_37
    if isObj(_G, cfg) then
        lua_isObj_result_37 = cfg
    else
        lua_isObj_result_37 = {}
    end
    local c = lua_isObj_result_37
    local vp = self:_vp()
    local coord = self:_coord()
    local id = c.id
    local parent = c.parent or nil
    local lua_c_text_38 = c.text
    if lua_c_text_38 == nil then
        lua_c_text_38 = ""
    end
    local text = tostring(lua_c_text_38)
    local lua_c_place_39
    if c.place then
        lua_c_place_39 = parsePlace(_G, c.place)
    else
        lua_c_place_39 = nil
    end
    local place = lua_c_place_39
    local x = num(_G, c.x, 0)
    local y = num(_G, c.y, 0)
    if place then
        local p = placePoint(_G, vp.w, vp.h, place)
        x = p.x
        y = p.y
    end
    y = applyCoordY(_G, coord, vp.h, y)
    local lua_parent_40
    if parent then
        lua_parent_40 = parent.handle
    else
        lua_parent_40 = nil
    end
    local ph = lua_parent_40
    local lua_ph_41
    if ph then
        lua_ph_41 = self._api:addLabel(
            self.handle,
            ph,
            text,
            x,
            y
        )
    else
        lua_ph_41 = self._api:addLabel(self.handle, text, x, y)
    end
    local hh = lua_ph_41
    local el = Classes:construct(
        Element,
        self._hud,
        hh,
        self,
        parent
    )
    el.kind = "text"
    if c.fontSize ~= nil then
        el:fontSize(c.fontSize)
    end
    if c.color then
        local col = c.color
        if isObj(_G, col) then
            el:color(
                num(_G, col.r, 1),
                num(_G, col.g, 1),
                num(_G, col.b, 1),
                num(_G, col.a, 1)
            )
        end
    end
    if place then
        el:_setPlace(place)
        self:_trackPlaced(el)
        self:_markDirty()
    end
    if id ~= nil then
        self:_regPut(id, el)
    end
    return el
end
function Layer.prototype.label(self, cfg)
    if cfg == nil then
        cfg = {}
    end
    return self:text(cfg)
end
function Layer.prototype.stackText(self, panel, cfg)
    if cfg == nil then
        cfg = {}
    end
    local p = panel
    if not p or p.kind ~= "panel" then
        error(
            Classes:construct(Error, "[HUD] stackText requires panel"),
            0
        )
    end
    local lua_isObj_result_42
    if isObj(_G, cfg) then
        lua_isObj_result_42 = cfg
    else
        lua_isObj_result_42 = {}
    end
    local c = lua_isObj_result_42
    local id = c.id
    local lua_temp_43
    if c.text ~= nil then
        lua_temp_43 = c.text
    else
        lua_temp_43 = ""
    end
    local text = tostring(lua_temp_43)
    local m = p._flow or ({padX = 0, padY = 0, gap = 0, fontSize = nil})
    local hh = self._api:addLabel(
        self.handle,
        p.handle,
        text,
        0,
        0
    )
    local el = Classes:construct(
        Element,
        self._hud,
        hh,
        self,
        p
    )
    el.kind = "text"
    local lua_temp_45
    if c.fontSize ~= nil then
        lua_temp_45 = bit32.bor(
            num(_G, c.fontSize, 14),
            0
        )
    else
        local lua_temp_44
        if m.fontSize ~= nil then
            lua_temp_44 = bit32.bor(
                num(_G, m.fontSize, 14),
                0
            )
        else
            lua_temp_44 = nil
        end
        lua_temp_45 = lua_temp_44
    end
    local itemFont = lua_temp_45
    if itemFont ~= nil then
        el:fontSize(itemFont)
    end
    KArrayOps.push(p._stack, {
        el = el,
        padX = m.padX,
        padY = m.padY,
        gap = m.gap,
        fontSize = itemFont
    })
    self:_relayoutPanelStack(p)
    if id ~= nil then
        self:_regPut(id, el)
    end
    return el
end
function Layer.prototype._computeAutoPanelHeight(self, panel)
    local p = panel
    local padY = num(_G, p._flow.padY, 0)
    local gap = num(_G, p._flow.gap, 0)
    local lua_temp_46
    if p._flow.fontSize ~= nil then
        lua_temp_46 = num(_G, p._flow.fontSize, 14)
    else
        lua_temp_46 = 14
    end
    local defaultFont = lua_temp_46
    local contentH = padY
    do
        local i = 0
        while i < KLength(p._stack) do
            local it = KIndex(p._stack, i)
            local lua_temp_47
            if it and it.fontSize ~= nil then
                lua_temp_47 = num(_G, it.fontSize, defaultFont)
            else
                lua_temp_47 = defaultFont
            end
            local fs = lua_temp_47
            local lineH = math.max(
                10,
                bit32.bor(fs, 0) + 4
            )
            contentH = contentH + lineH
            if i ~= KLength(p._stack) - 1 then
                contentH = contentH + gap
            end
            i = i + 1
        end
    end
    contentH = contentH + padY
    local minH = num(_G, p._autoMinH, 0)
    if minH > 0 then
        contentH = math.max(contentH, minH)
    end
    return contentH
end
function Layer.prototype._relayoutPanelStack(self, panel)
    local p = panel
    local coord = self:_coord()
    local basePadX = num(_G, p._flow.padX, 0)
    local y = num(_G, p._flow.padY, 0)
    local lua_temp_48
    if p._flow.fontSize ~= nil then
        lua_temp_48 = num(_G, p._flow.fontSize, 14)
    else
        lua_temp_48 = 14
    end
    local defaultFont = lua_temp_48
    local gap = num(_G, p._flow.gap, 0)
    do
        local i = 0
        while i < KLength(p._stack) do
            do
                local it = KIndex(p._stack, i)
                local el = it.el
                if not el then
                    goto lua_continue135
                end
                local lua_temp_49
                if it.fontSize ~= nil then
                    lua_temp_49 = num(_G, it.fontSize, defaultFont)
                else
                    lua_temp_49 = defaultFont
                end
                local fs = lua_temp_49
                local lineH = math.max(
                    10,
                    bit32.bor(fs, 0) + 4
                )
                local x = num(_G, it.padX, basePadX)
                local yy = applyCoordY(_G, coord, 0, y)
                self._api:setPosition(el.handle, x, yy)
                y = y + lineH
                if i ~= KLength(p._stack) - 1 then
                    y = y + gap
                end
            end
            ::lua_continue135::
            i = i + 1
        end
    end
    if p._autoHeight then
        local newH = self:_computeAutoPanelHeight(p)
        if bit32.bor(newH, 0) ~= bit32.bor(p._h, 0) then
            p._h = newH
            self._api:setSize(p.handle, p._w, p._h)
            self:_markDirty()
        end
    else
        self:_markDirty()
    end
end
function Layer.prototype.input(self, cfg)
    if cfg == nil then
        cfg = {}
    end
    local lua_isObj_result_50
    if isObj(_G, cfg) then
        lua_isObj_result_50 = cfg
    else
        lua_isObj_result_50 = {}
    end
    local c = lua_isObj_result_50
    local vp = self:_vp()
    local coord = self:_coord()
    local id = c.id
    local parent = c.parent or nil
    local lua_c_text_51 = c.text
    if lua_c_text_51 == nil then
        lua_c_text_51 = ""
    end
    local text = tostring(lua_c_text_51)
    local w = num(_G, c.w, 220)
    local h = num(_G, c.h, 26)
    local lua_c_place_52
    if c.place then
        lua_c_place_52 = parsePlace(_G, c.place)
    else
        lua_c_place_52 = nil
    end
    local place = lua_c_place_52
    local x = num(_G, c.x, 0)
    local y = num(_G, c.y, 0)
    if place then
        local p = placeRect(
            _G,
            vp.w,
            vp.h,
            w,
            h,
            place
        )
        x = p.x
        y = p.y
    end
    y = applyCoordY(_G, coord, vp.h, y)
    local lua_parent_53
    if parent then
        lua_parent_53 = parent.handle
    else
        lua_parent_53 = nil
    end
    local ph = lua_parent_53
    local lua_ph_54
    if ph then
        lua_ph_54 = self._api:addTextField(
            self.handle,
            ph,
            text,
            x,
            y,
            w,
            h
        )
    else
        lua_ph_54 = self._api:addTextField(
            self.handle,
            text,
            x,
            y,
            w,
            h
        )
    end
    local hh = lua_ph_54
    local el = Classes:construct(
        Element,
        self._hud,
        hh,
        self,
        parent
    )
    el.kind = "input"
    el._w = w
    el._h = h
    if c.fontSize ~= nil then
        el:fontSize(c.fontSize)
    end
    if place then
        el:_setPlace(place)
        self:_trackPlaced(el)
        self:_markDirty()
    end
    if id ~= nil then
        self:_regPut(id, el)
    end
    return el
end
function Layer.prototype.checkbox(self, cfg)
    if cfg == nil then
        cfg = {}
    end
    local lua_isObj_result_55
    if isObj(_G, cfg) then
        lua_isObj_result_55 = cfg
    else
        lua_isObj_result_55 = {}
    end
    local c = lua_isObj_result_55
    local vp = self:_vp()
    local coord = self:_coord()
    local id = c.id
    local parent = c.parent or nil
    local lua_c_text_56 = c.text
    if lua_c_text_56 == nil then
        lua_c_text_56 = ""
    end
    local text = tostring(lua_c_text_56)
    local lua_c_place_57
    if c.place then
        lua_c_place_57 = parsePlace(_G, c.place)
    else
        lua_c_place_57 = nil
    end
    local place = lua_c_place_57
    local x = num(_G, c.x, 0)
    local y = num(_G, c.y, 0)
    if place then
        local p = placeRect(
            _G,
            vp.w,
            vp.h,
            0,
            0,
            place
        )
        x = p.x
        y = p.y
    end
    y = applyCoordY(_G, coord, vp.h, y)
    local lua_parent_58
    if parent then
        lua_parent_58 = parent.handle
    else
        lua_parent_58 = nil
    end
    local ph = lua_parent_58
    local lua_ph_59
    if ph then
        lua_ph_59 = self._api:addCheckbox(
            self.handle,
            ph,
            text,
            x,
            y
        )
    else
        lua_ph_59 = self._api:addCheckbox(self.handle, text, x, y)
    end
    local hh = lua_ph_59
    local el = Classes:construct(
        Element,
        self._hud,
        hh,
        self,
        parent
    )
    el.kind = "checkbox"
    if c.fontSize ~= nil then
        el:fontSize(c.fontSize)
    end
    if c.checked ~= nil and isFn(_G, self._api.setChecked) then
        self._api:setChecked(el.handle, not not c.checked)
    end
    if place then
        el:_setPlace(place)
        self:_trackPlaced(el)
        self:_markDirty()
    end
    if id ~= nil then
        self:_regPut(id, el)
    end
    return el
end
function Layer.prototype.slider(self, cfg)
    if cfg == nil then
        cfg = {}
    end
    local lua_isObj_result_60
    if isObj(_G, cfg) then
        lua_isObj_result_60 = cfg
    else
        lua_isObj_result_60 = {}
    end
    local c = lua_isObj_result_60
    local vp = self:_vp()
    local coord = self:_coord()
    local id = c.id
    local parent = c.parent or nil
    local min = num(_G, c.min, 0)
    local max = num(_G, c.max, 1)
    local value = num(_G, c.value, min)
    local w = num(_G, c.w, 220)
    local h = num(_G, c.h, 26)
    local lua_c_place_61
    if c.place then
        lua_c_place_61 = parsePlace(_G, c.place)
    else
        lua_c_place_61 = nil
    end
    local place = lua_c_place_61
    local x = num(_G, c.x, 0)
    local y = num(_G, c.y, 0)
    if place then
        local p = placeRect(
            _G,
            vp.w,
            vp.h,
            w,
            h,
            place
        )
        x = p.x
        y = p.y
    end
    y = applyCoordY(_G, coord, vp.h, y)
    local lua_parent_62
    if parent then
        lua_parent_62 = parent.handle
    else
        lua_parent_62 = nil
    end
    local ph = lua_parent_62
    local lua_ph_63
    if ph then
        lua_ph_63 = self._api:addSlider(
            self.handle,
            ph,
            min,
            max,
            value,
            x,
            y,
            w,
            h
        )
    else
        lua_ph_63 = self._api:addSlider(
            self.handle,
            min,
            max,
            value,
            x,
            y,
            w,
            h
        )
    end
    local hh = lua_ph_63
    local el = Classes:construct(
        Element,
        self._hud,
        hh,
        self,
        parent
    )
    el.kind = "slider"
    el._w = w
    el._h = h
    if place then
        el:_setPlace(place)
        self:_trackPlaced(el)
        self:_markDirty()
    end
    if id ~= nil then
        self:_regPut(id, el)
    end
    return el
end
function Layer.prototype.radio(self, cfg)
    if cfg == nil then
        cfg = {}
    end
    local lua_isObj_result_64
    if isObj(_G, cfg) then
        lua_isObj_result_64 = cfg
    else
        lua_isObj_result_64 = {}
    end
    local c = lua_isObj_result_64
    if c.id == nil then
        error(
            Classes:construct(Error, "[HUD] radio requires {id}"),
            0
        )
    end
    local group = tostring(c.group or "default")
    local id = tostring(c.id)
    local el = self:checkbox(Tables:merge({}, c, {id = id}))
    el.kind = "radio"
    el._radioGroup = group
    self:_radioRegister(group, id)
    if c.checked then
        self:_radioSelect(group, id)
    elseif isFn(_G, self._api.setChecked) then
        self._api:setChecked(el.handle, false)
    end
    el.select = function()
        self:_radioSelect(group, id)
        return el
    end
    el.group = function() return group end
    return el
end
function Layer.prototype._radioRegister(self, groupName, key)
    local g = tostring(groupName or "default")
    local s = self._radioGroups[g]
    if not s then
        local lua_construct_result_65 = Collections:newSet()
        self._radioGroups[g] = lua_construct_result_65
        s = lua_construct_result_65
    end
    s:add(tostring(key))
end
function Layer.prototype._radioSelect(self, groupName, key)
    local g = tostring(groupName or "default")
    local k = tostring(key)
    local s = self._radioGroups[g]
    if not s then
        return
    end
    for lua_, it in Iterators:iterate(s) do
        do
            local el = self:get(it)
            if not el then
                goto lua_continue166
            end
            if tostring(it) ~= k and isFn(_G, self._api.setChecked) then
                self._api:setChecked(el.handle, false)
            end
        end
        ::lua_continue166::
    end
    local chosen = self:get(k)
    if chosen and isFn(_G, self._api.setChecked) then
        self._api:setChecked(chosen.handle, true)
    end
end
function Layer.prototype.radioGroup(self, name)
    local g = tostring(name or "default")
    local lua_self = self
    return {
        name = g,
        items = function(self)
            local s = lua_self._radioGroups[g]
            if not s then
                return {}
            end
            local out = {}
            for lua_, k in Iterators:iterate(s) do
                local el = lua_self:get(k)
                if el then
                    out[#out + 1] = el
                end
            end
            return out
        end,
        selected = function(self)
            local s = lua_self._radioGroups[g]
            if not s then
                return nil
            end
            for lua_, k in Iterators:iterate(s) do
                local el = lua_self:get(k)
                if el and isFn(_G, lua_self._api.isChecked) and lua_self._api:isChecked(el.handle) then
                    return el
                end
            end
            return nil
        end,
        select = function(self, elOrId)
            local lua_temp_69
            if KTypeOf(elOrId) == "string" or KTypeOf(elOrId) == "number" then
                lua_temp_69 = tostring(elOrId)
            else
                local lua_elOrId_68 = elOrId
                if lua_elOrId_68 then
                    local lua_elOrId_key_66 = elOrId.key
                    if lua_elOrId_key_66 == nil then
                        lua_elOrId_key_66 = elOrId.id
                    end
                    local lua_elOrId_key_66_67 = lua_elOrId_key_66
                    if lua_elOrId_key_66_67 == nil then
                        lua_elOrId_key_66_67 = ""
                    end
                    lua_elOrId_68 = lua_elOrId_key_66_67
                end
                lua_temp_69 = tostring(lua_elOrId_68)
            end
            local id = lua_temp_69
            if not id then
                return nil
            end
            lua_self:_radioSelect(g, id)
            return lua_self:get(id)
        end,
        clear = function(self)
            local s = lua_self._radioGroups[g]
            if not s then
                return
            end
            for lua_, k in Iterators:iterate(s) do
                local el = lua_self:get(k)
                if el and isFn(_G, lua_self._api.setChecked) then
                    lua_self._api:setChecked(el.handle, false)
                end
            end
        end
    }
end
M = {Layer = Layer}

return M
