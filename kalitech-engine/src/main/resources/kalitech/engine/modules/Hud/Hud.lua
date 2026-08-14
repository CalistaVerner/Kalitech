local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local Strings = luaRuntime.string
local Classes = luaRuntime.class
local Error = luaRuntime.Error
local lua_require_result_0 = require("./helpers/HudUtil.lua")
isObj = lua_require_result_0.isObj
num = lua_require_result_0.num
local lua_require_result_1 = require("./helpers/Layer.lua")
Layer = lua_require_result_1.Layer
ComponentRegistry = Classes:create()
ComponentRegistry.name = "ComponentRegistry"
function ComponentRegistry.prototype.lua_constructor(self)
    self._reg = KObject:create(nil)
end
function ComponentRegistry.prototype.register(self, lua_type, factory)
    local t = Strings:trim(tostring(lua_type or ""))
    if not t then
        error(
            Classes:construct(Error, "[HUD] components.register: empty type"),
            0
        )
    end
    if KTypeOf(factory) ~= "function" then
        error(
            Classes:construct(Error, "[HUD] components.register: factory must be function"),
            0
        )
    end
    self._reg[t] = factory
    return self
end
function ComponentRegistry.prototype.has(self, lua_type)
    return not not self._reg[Strings:trim(tostring(lua_type or ""))]
end
function ComponentRegistry.prototype.create(self, lua_type, layer, cfg)
    local t = Strings:trim(tostring(lua_type or ""))
    local fn = self._reg[t]
    if not fn then
        error(
            Classes:construct(Error, "[HUD] Unknown component type: " .. t),
            0
        )
    end
    local lua_G_3 = _G
    local lua_layer_4 = layer
    local lua_isObj_result_2
    if isObj(_G, cfg) then
        lua_isObj_result_2 = cfg
    else
        lua_isObj_result_2 = {}
    end
    return fn(lua_G_3, lua_layer_4, lua_isObj_result_2)
end
function HudModule(self, engine, cfg)
    if not engine or KTypeOf(engine.hud) ~= "function" then
        error(
            Classes:construct(Error, "[HUD] ENGINE.hud() missing"),
            0
        )
    end
    local api = engine:hud()
    local lua_isObj_result_5
    if isObj(_G, cfg) then
        lua_isObj_result_5 = cfg
    else
        lua_isObj_result_5 = {}
    end
    local c = lua_isObj_result_5
    local _vpCache = {w = 0, h = 0}
    local hud
    hud = {
        _api = api,
        META = {
            id = "kalitech.hud",
            version = "3.2.0",
            coord = tostring(c.coord or "topLeft")
        },
        components = Classes:construct(ComponentRegistry),
        layer = function(self, name)
            local lua_api_createLayer_7 = api.createLayer
            local lua_name_6 = name
            if lua_name_6 == nil then
                lua_name_6 = "hud"
            end
            local h = lua_api_createLayer_7(
                api,
                tostring(lua_name_6)
            )
            return Classes:construct(Layer, hud, h)
        end,
        spec = function(self, layerName, spec, opts)
            local lua_hud_layer_9 = hud.layer
            local lua_layerName_8 = layerName
            if lua_layerName_8 == nil then
                lua_layerName_8 = "hud"
            end
            local layer = lua_hud_layer_9(
                hud,
                tostring(lua_layerName_8)
            )
            local res = layer:spec(spec, opts or ({}))
            return {layer = layer, created = res.created, used = res.used}
        end,
        viewport = function(self)
            local vp = api:viewport()
            if not vp then
                _vpCache.w = 0
                _vpCache.h = 0
                return _vpCache
            end
            _vpCache.w = bit32.bor(
                num(
                    _G,
                    vp:w(),
                    0
                ),
                0
            )
            _vpCache.h = bit32.bor(
                num(
                    _G,
                    vp:h(),
                    0
                ),
                0
            )
            return _vpCache
        end,
        cursor = function(self, enabled, force)
            if KTypeOf(force) == "boolean" then
                api:setCursorEnabled(not not enabled, force)
            else
                api:setCursorEnabled(not not enabled)
            end
        end,
        clearLayer = function(self, l)
            local lua_api_clearLayer_11 = api.clearLayer
            local lua_temp_10
            if l and l.handle then
                lua_temp_10 = l.handle
            else
                lua_temp_10 = l
            end
            lua_api_clearLayer_11(api, lua_temp_10)
        end,
        destroyLayer = function(self, l)
            local lua_api_destroyLayer_13 = api.destroyLayer
            local lua_temp_12
            if l and l.handle then
                lua_temp_12 = l.handle
            else
                lua_temp_12 = l
            end
            lua_api_destroyLayer_13(api, lua_temp_12)
        end
    }
    hud.components:register(
        "Container",
        function(lua_, layer, cfg0) return layer:container(cfg0) end
    ):register(
        "Panel",
        function(lua_, layer, cfg0) return layer:panel(cfg0) end
    ):register(
        "Rect",
        function(lua_, layer, cfg0) return layer:panel(cfg0) end
    ):register(
        "Label",
        function(lua_, layer, cfg0) return layer:text(cfg0) end
    ):register(
        "Text",
        function(lua_, layer, cfg0) return layer:text(cfg0) end
    ):register(
        "Input",
        function(lua_, layer, cfg0) return layer:input(cfg0) end
    ):register(
        "Checkbox",
        function(lua_, layer, cfg0) return layer:checkbox(cfg0) end
    ):register(
        "Slider",
        function(lua_, layer, cfg0) return layer:slider(cfg0) end
    ):register(
        "Radio",
        function(lua_, layer, cfg0) return layer:radio(cfg0) end
    )
    return hud
end
M = setmetatable({
    META = {moduleId = "hud", version = "3.2.0"}
}, {
    __call = function(_, ...)
        return HudModule(...)
    end
})

return M
