local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local Arrays = luaRuntime.array
local Strings = luaRuntime.string
local Classes = luaRuntime.class
local Error = luaRuntime.Error
local lua_require_result_0 = require("./HudUtil.lua")
isObj = lua_require_result_0.isObj
num = lua_require_result_0.num
bool = lua_require_result_0.bool
function isFn(self, v)
    return KTypeOf(v) == "function"
end
KindByType = KObject:freeze({
    Container = "container",
    Panel = "panel",
    Rect = "panel",
    Label = "text",
    Text = "text",
    Input = "input",
    Checkbox = "checkbox",
    Slider = "slider",
    Radio = "radio"
})
function normalizeType(self, lua_type)
    local t = Strings:trim(tostring(lua_type or ""))
    if not t then
        return ""
    end
    return string.upper(Strings:access(t, 0)) .. string.sub(t, 2)
end
function expectedKind(self, lua_type)
    return KindByType[lua_type] or "element"
end
function normalizeId(self, v)
    if v == nil or v == nil then
        return nil
    end
    local s = Strings:trim(tostring(v))
    local lua_s_1
    if s then
        lua_s_1 = s
    else
        lua_s_1 = nil
    end
    return lua_s_1
end
function shouldPrefix(self, prefix, id)
    if not prefix or not id then
        return false
    end
    return KArrayOps.indexOf(id, tostring(prefix) .. ".") ~= 0
end
function prefixedId(self, prefix, id)
    if not id then
        return nil
    end
    local lua_shouldPrefix_result_2
    if shouldPrefix(_G, prefix, id) then
        lua_shouldPrefix_result_2 = (tostring(prefix) .. ".") .. tostring(id)
    else
        lua_shouldPrefix_result_2 = id
    end
    return lua_shouldPrefix_result_2
end
function resolveParent(self, layer, parent, created, prefix)
    if not parent then
        return nil
    end
    if KTypeOf(parent) == "string" or KTypeOf(parent) == "number" then
        local raw = tostring(parent)
        local k = prefixedId(_G, prefix, raw)
        local c = created[k]
        if c then
            return c
        end
        local r = layer:get(k)
        if r then
            return r
        end
        return nil
    end
    if isObj(_G, parent) and parent.kind then
        return parent
    end
    return nil
end
function applyCommon(self, el, spec)
    if not el or not spec then
        return
    end
    if spec.visible ~= nil and isFn(_G, el.visible) then
        el:visible(not not spec.visible)
    end
    if spec.text ~= nil and isFn(_G, el.text) then
        local lua_el_4 = el
        local lua_el_text_5 = el.text
        local lua_spec_text_3 = spec.text
        if lua_spec_text_3 == nil then
            lua_spec_text_3 = ""
        end
        lua_el_text_5(
            lua_el_4,
            tostring(lua_spec_text_3)
        )
    end
    if spec.value ~= nil and isFn(_G, el.value) then
        el:value(spec.value)
    end
    if spec.fontSize ~= nil and isFn(_G, el.fontSize) then
        el:fontSize(num(_G, spec.fontSize, 14))
    end
    if spec.color and isFn(_G, el.color) then
        local c = spec.color
        if isObj(_G, c) then
            el:color(
                num(_G, c.r, 1),
                num(_G, c.g, 1),
                num(_G, c.b, 1),
                num(_G, c.a, 1)
            )
        end
    end
    if spec.bg and isFn(_G, el.bg) then
        local b = spec.bg
        if isObj(_G, b) then
            el:bg(
                num(_G, b.r, 0),
                num(_G, b.g, 0),
                num(_G, b.b, 0),
                num(_G, b.a, 1)
            )
        end
    end
    if (spec.w ~= nil or spec.h ~= nil) and isFn(_G, el.size) then
        local lua_temp_6
        if spec.w ~= nil then
            lua_temp_6 = num(_G, spec.w, el._w or 0)
        else
            lua_temp_6 = el._w or 0
        end
        local w = lua_temp_6
        local lua_temp_7
        if spec.h ~= nil then
            lua_temp_7 = num(_G, spec.h, el._h or 0)
        else
            lua_temp_7 = el._h or 0
        end
        local h = lua_temp_7
        el:size(w, h)
    end
    if (spec.x ~= nil or spec.y ~= nil) and isFn(_G, el.pos) then
        local lua_temp_8
        if spec.x ~= nil then
            lua_temp_8 = num(_G, spec.x, 0)
        else
            lua_temp_8 = 0
        end
        local x = lua_temp_8
        local lua_temp_9
        if spec.y ~= nil then
            lua_temp_9 = num(_G, spec.y, 0)
        else
            lua_temp_9 = 0
        end
        local y = lua_temp_9
        el:pos(x, y)
    end
    if spec.place ~= nil and isFn(_G, el._setPlace) then
        el:_setPlace(spec.place or nil)
        if el.layer and isFn(_G, el.layer._markDirty) then
            el.layer:_markDirty()
        end
    end
    if el.kind == "panel" and spec.flow and isFn(_G, el.flow) then
        el:flow(spec.flow)
    end
end
function shouldStackIntoPanel(self, panel, childSpec)
    if not panel or panel.kind ~= "panel" then
        return false
    end
    if not childSpec or normalizeType(_G, childSpec.type) ~= "Text" then
        return false
    end
    if childSpec.x ~= nil or childSpec.y ~= nil then
        return false
    end
    if childSpec.place ~= nil then
        return false
    end
    if childSpec.stack == true then
        return true
    end
    if childSpec.stack == false then
        return false
    end
    return panel._flow ~= nil
end
function buildOne(self, layer, spec, created, used, opts, parentEl)
    if not isObj(_G, spec) then
        return nil
    end
    local lua_type = normalizeType(_G, spec.type)
    if not lua_type then
        return nil
    end
    local prefix = opts.prefix or ""
    local rawId = normalizeId(_G, spec.id)
    local id = prefixedId(_G, prefix, rawId)
    local reuse = bool(_G, opts.reuse, true)
    local resolvedParent = resolveParent(
        _G,
        layer,
        spec.parent or parentEl,
        created,
        prefix
    )
    local cfg = KObject:create(nil)
    if id then
        cfg.id = id
    end
    if resolvedParent then
        cfg.parent = resolvedParent
    end
    if spec.place ~= nil then
        cfg.place = spec.place
    end
    if spec.x ~= nil then
        cfg.x = spec.x
    end
    if spec.y ~= nil then
        cfg.y = spec.y
    end
    if spec.w ~= nil then
        cfg.w = spec.w
    end
    if spec.h ~= nil then
        cfg.h = spec.h
    end
    if spec.autoHeight ~= nil then
        cfg.autoHeight = spec.autoHeight
    end
    if spec.minH ~= nil then
        cfg.minH = spec.minH
    end
    if spec.padX ~= nil then
        cfg.padX = spec.padX
    end
    if spec.padY ~= nil then
        cfg.padY = spec.padY
    end
    if spec.gap ~= nil then
        cfg.gap = spec.gap
    end
    if spec.fontSize ~= nil then
        cfg.fontSize = spec.fontSize
    end
    if spec.flow ~= nil then
        cfg.flow = spec.flow
    end
    if spec.text ~= nil then
        cfg.text = spec.text
    end
    if spec.value ~= nil then
        cfg.value = spec.value
    end
    if spec.min ~= nil then
        cfg.min = spec.min
    end
    if spec.max ~= nil then
        cfg.max = spec.max
    end
    if spec.checked ~= nil then
        cfg.checked = spec.checked
    end
    if spec.group ~= nil then
        cfg.group = spec.group
    end
    if spec.color ~= nil then
        cfg.color = spec.color
    end
    if spec.bg ~= nil then
        cfg.bg = spec.bg
    end
    if spec.style ~= nil then
        cfg.style = spec.style
    end
    local el = nil
    if reuse and id then
        local existing = layer:get(id)
        if existing then
            local needKind = expectedKind(_G, lua_type)
            if existing.kind ~= needKind and not (needKind == "panel" and existing.kind == "panel") then
                layer:drop(id, true)
            else
                el = existing
            end
        end
    end
    if not el then
        el = layer._hud.components:create(lua_type, layer, cfg)
        if id then
            created[id] = el
        end
    else
        if resolvedParent and el.parent and el.parent ~= resolvedParent then
            layer:drop(id, true)
            el = layer._hud.components:create(lua_type, layer, cfg)
            if id then
                created[id] = el
            end
        else
            applyCommon(_G, el, spec)
        end
    end
    if not el then
        return nil
    end
    if id then
        used[id] = true
        el.__specEpoch = bit32.bor(opts.__epoch, 0)
    end
    local kids = spec.children
    if not kids then
        return el
    end
    if Arrays:isArray(kids) then
        if #kids == 0 then
            return el
        end
        if el.kind == "panel" then
            do
                local i = 0
                while i < #kids do
                    do
                        local ch = kids[i + 1]
                        if not isObj(_G, ch) then
                            goto lua_continue83
                        end
                        if shouldStackIntoPanel(_G, el, ch) then
                            local rawCid = normalizeId(_G, ch.id) or "__stack_" .. tostring(bit32.bor(i, 0))
                            local cid = prefixedId(_G, prefix, rawCid)
                            local lua_temp_11
                            if ch.text ~= nil then
                                local lua_ch_text_10 = ch.text
                                if lua_ch_text_10 == nil then
                                    lua_ch_text_10 = ""
                                end
                                lua_temp_11 = tostring(lua_ch_text_10)
                            else
                                lua_temp_11 = ""
                            end
                            local text = lua_temp_11
                            local stackCfg
                            if ch.fontSize ~= nil or ch.color ~= nil then
                                stackCfg = KObject:create(nil)
                                if ch.fontSize ~= nil then
                                    stackCfg.fontSize = ch.fontSize
                                end
                                if ch.color ~= nil then
                                    stackCfg.color = ch.color
                                end
                            end
                            local childEl = el:stack(cid, text, stackCfg)
                            if cid then
                                created[cid] = childEl
                                used[cid] = true
                                if childEl then
                                    childEl.__specEpoch = bit32.bor(opts.__epoch, 0)
                                end
                            end
                            if childEl then
                                applyCommon(_G, childEl, ch)
                            end
                            goto lua_continue83
                        end
                        buildOne(
                            _G,
                            layer,
                            ch,
                            created,
                            used,
                            opts,
                            el
                        )
                    end
                    ::lua_continue83::
                    i = i + 1
                end
            end
        elseif el.kind == "container" then
            do
                local i = 0
                while i < #kids do
                    buildOne(
                        _G,
                        layer,
                        kids[i + 1],
                        created,
                        used,
                        opts,
                        el
                    )
                    i = i + 1
                end
            end
        else
            do
                local i = 0
                while i < #kids do
                    buildOne(
                        _G,
                        layer,
                        kids[i + 1],
                        created,
                        used,
                        opts,
                        nil
                    )
                    i = i + 1
                end
            end
        end
        return el
    end
    if isObj(_G, kids) then
        if el.kind == "panel" then
            if shouldStackIntoPanel(_G, el, kids) then
                local rawCid = normalizeId(_G, kids.id) or "__stack_0"
                local cid = prefixedId(_G, prefix, rawCid)
                local lua_temp_13
                if kids.text ~= nil then
                    local lua_kids_text_12 = kids.text
                    if lua_kids_text_12 == nil then
                        lua_kids_text_12 = ""
                    end
                    lua_temp_13 = tostring(lua_kids_text_12)
                else
                    lua_temp_13 = ""
                end
                local text = lua_temp_13
                local stackCfg
                if kids.fontSize ~= nil or kids.color ~= nil then
                    stackCfg = KObject:create(nil)
                    if kids.fontSize ~= nil then
                        stackCfg.fontSize = kids.fontSize
                    end
                    if kids.color ~= nil then
                        stackCfg.color = kids.color
                    end
                end
                local childEl = el:stack(cid, text, stackCfg)
                if cid then
                    created[cid] = childEl
                    used[cid] = true
                    if childEl then
                        childEl.__specEpoch = bit32.bor(opts.__epoch, 0)
                    end
                end
                if childEl then
                    applyCommon(_G, childEl, kids)
                end
                return el
            end
            buildOne(
                _G,
                layer,
                kids,
                created,
                used,
                opts,
                el
            )
        elseif el.kind == "container" then
            buildOne(
                _G,
                layer,
                kids,
                created,
                used,
                opts,
                el
            )
        else
            buildOne(
                _G,
                layer,
                kids,
                created,
                used,
                opts,
                nil
            )
        end
    end
    return el
end
function pruneLayer(self, layer, epoch, opts)
    local mode = tostring(opts.pruneMode or "hide")
    local remove = mode == "remove"
    local reg = layer._reg
    if not reg then
        return
    end
    local keys = layer._regKeys
    if Arrays:isArray(keys) then
        do
            local i = 0
            while i < #keys do
                do
                    local k = keys[i + 1]
                    local el = reg[k]
                    if not el then
                        goto lua_continue113
                    end
                    if bit32.bor(el.__specEpoch, 0) == 0 then
                        goto lua_continue113
                    end
                    if bit32.bor(el.__specEpoch, 0) ~= bit32.bor(epoch, 0) then
                        if remove then
                            layer:drop(k, true)
                        elseif isFn(_G, el.visible) then
                            el:visible(false)
                        end
                    end
                end
                ::lua_continue113::
                i = i + 1
            end
        end
        return
    end
    for k in pairs(reg) do
        do
            local el = reg[k]
            if not el then
                goto lua_continue119
            end
            if bit32.bor(el.__specEpoch, 0) == 0 then
                goto lua_continue119
            end
            if bit32.bor(el.__specEpoch, 0) ~= bit32.bor(epoch, 0) then
                if remove then
                    layer:drop(k, true)
                elseif isFn(_G, el.visible) then
                    el:visible(false)
                end
            end
        end
        ::lua_continue119::
    end
end
function buildFromSpec(self, layer, spec, opts)
    if opts == nil then
        opts = {}
    end
    if not layer then
        error(
            Classes:construct(Error, "[HUD][SPEC] layer is required"),
            0
        )
    end
    local lua_isObj_result_14
    if isObj(_G, opts) then
        lua_isObj_result_14 = opts
    else
        lua_isObj_result_14 = {}
    end
    local o = lua_isObj_result_14
    local created = KObject:create(nil)
    local used = KObject:create(nil)
    local epoch = bit32.bor(
        bit32.bor(layer.__specEpochCounter, 0) + 1,
        0
    )
    layer.__specEpochCounter = epoch
    o.__epoch = epoch
    if Arrays:isArray(spec) then
        do
            local i = 0
            while i < #spec do
                buildOne(
                    _G,
                    layer,
                    spec[i + 1],
                    created,
                    used,
                    o,
                    nil
                )
                i = i + 1
            end
        end
    elseif isObj(_G, spec) then
        buildOne(
            _G,
            layer,
            spec,
            created,
            used,
            o,
            nil
        )
    end
    local prune = bool(
        _G,
        o.prune,
        bool(_G, o.reuse, true)
    )
    if prune then
        pruneLayer(_G, layer, epoch, o)
    end
    if bool(_G, o.pull, true) and o.model then
        layer:pullAll()
    end
    if bool(_G, o.relayout, true) then
        local lua_layer_flushLayout_15
        if layer.flushLayout then
            lua_layer_flushLayout_15 = layer:flushLayout()
        else
            lua_layer_flushLayout_15 = layer:relayout()
        end
    end
    return {created = created, used = used}
end
M = {buildFromSpec = buildFromSpec}

return M
