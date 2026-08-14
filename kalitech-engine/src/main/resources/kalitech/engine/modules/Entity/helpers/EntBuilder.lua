local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local Classes = luaRuntime.class
local Error = luaRuntime.Error
local lua_require_result_0 = require("./EntUtil.lua")
deepMerge = lua_require_result_0.deepMerge
EntBuilder = Classes:create()
EntBuilder.name = "EntBuilder"
function EntBuilder.prototype.lua_constructor(self, entApi, presetName)
    self._ent = entApi
    self._presetName = presetName or ""
    self._cfg = {}
end
function EntBuilder.prototype.merge(self, cfg)
    self._cfg = deepMerge(_G, self._cfg, cfg or ({}))
    return self
end
function EntBuilder.prototype.name(self, v)
    self._cfg.name = tostring(v or "entity")
    return self
end
function EntBuilder.prototype.debug(self, v)
    if v == nil then
        v = true
    end
    self._cfg.debug = not not v
    return self
end
function EntBuilder.prototype.surface(self, v)
    self._cfg.surface = deepMerge(_G, self._cfg.surface or ({}), v or ({}))
    return self
end
function EntBuilder.prototype.body(self, v)
    self._cfg.body = deepMerge(_G, self._cfg.body or ({}), v or ({}))
    return self
end
function EntBuilder.prototype.attachSurface(self, v)
    if v == nil then
        v = true
    end
    self._cfg.attachSurface = not not v
    return self
end
function EntBuilder.prototype.component(self, name, dataOrFn)
    local n = tostring(name or "")
    if not n then
        error(
            Classes:construct(Error, "[ENT] builder.component(name,data): name required"),
            0
        )
    end
    if not self._cfg.components then
        self._cfg.components = KObject:create(nil)
    end
    self._cfg.components[n] = dataOrFn
    return self
end
function EntBuilder.prototype.create(self)
    local base = {}
    if self._presetName then
        local p = self._ent._presets[self._presetName]
        if p then
            base = deepMerge(_G, {}, p)
        end
    end
    local finalCfg = deepMerge(_G, base, self._cfg)
    return self._ent:create(finalCfg)
end
M = {EntBuilder = EntBuilder}

return M
