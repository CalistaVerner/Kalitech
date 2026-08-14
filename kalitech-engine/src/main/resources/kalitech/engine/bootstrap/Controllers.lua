local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local Error = luaRuntime.Error
local LuaConstruct = luaRuntime.LuaConstruct
local LuaNumberIsFinite = luaRuntime.LuaNumberIsFinite
local LuaArrayIsArray = luaRuntime.LuaArrayIsArray
local LuaTableKeys = luaRuntime.LuaTableKeys
local LuaArrayMap = luaRuntime.LuaArrayMap
local LuaArraySort = luaRuntime.LuaArraySort
local LuaArrayJoin = luaRuntime.LuaArrayJoin
local lua_require_result_0 = require("./Util.lua")
isPlainObj = lua_require_result_0.isPlainObj
isObj = lua_require_result_0.isObj
function createControllersApi(self, K)
    local function ensureRegistry(self, name)
        local k = tostring(name or "")
        if not k then
            error(
                LuaConstruct(Error, "[CONTROLLERS] registry name is required"),
                0
            )
        end
        local r = K.controllers[k]
        if not r then
            r = {
                name = k,
                defs = KObject:create(nil)
            }
            K.controllers[k] = r
        end
        return r
    end
    local function has(self, registryName, id)
        local R = ensureRegistry(_G, registryName)
        id = tostring(id or "")
        return not not (id and R.defs[id])
    end
    local function register(self, registryName, id, spec)
        local R = ensureRegistry(_G, registryName)
        id = tostring(id or "")
        if not id then
            error(
                LuaConstruct(Error, "[CONTROLLERS] id is required"),
                0
            )
        end
        if R.defs[id] then
            error(
                LuaConstruct(
                    Error,
                    (("[CONTROLLERS] duplicate id: " .. tostring(registryName)) .. "::") .. tostring(id)
                ),
                0
            )
        end
        local lua_isObj_result_1
        if isObj(_G, spec) then
            lua_isObj_result_1 = spec
        else
            lua_isObj_result_1 = KObject:create(nil)
        end
        spec = lua_isObj_result_1
        local lua_id_9 = id
        local lua_Number_isFinite_result_2
        if LuaNumberIsFinite(spec.order) then
            lua_Number_isFinite_result_2 = spec.order
        else
            lua_Number_isFinite_result_2 = 0
        end
        local lua_Array_isArray_result_3
        if LuaArrayIsArray(spec.deps) then
            lua_Array_isArray_result_3 = KArrayOps.slice(spec.deps)
        else
            lua_Array_isArray_result_3 = {}
        end
        local lua_temp_4
        if spec.enabled == false then
            lua_temp_4 = false
        else
            lua_temp_4 = true
        end
        local lua_temp_5
        if KTypeOf(spec.when) == "function" then
            lua_temp_5 = spec.when
        else
            lua_temp_5 = nil
        end
        local lua_spec_moduleId_6
        if spec.moduleId then
            lua_spec_moduleId_6 = tostring(spec.moduleId)
        else
            lua_spec_moduleId_6 = ""
        end
        local lua_spec_exportName_7
        if spec.exportName then
            lua_spec_exportName_7 = tostring(spec.exportName)
        else
            lua_spec_exportName_7 = ""
        end
        local lua_temp_8
        if KTypeOf(spec.Ctor) == "function" then
            lua_temp_8 = spec.Ctor
        else
            lua_temp_8 = nil
        end
        local def = {
            id = lua_id_9,
            order = lua_Number_isFinite_result_2,
            deps = lua_Array_isArray_result_3,
            enabled = lua_temp_4,
            when = lua_temp_5,
            moduleId = lua_spec_moduleId_6,
            exportName = lua_spec_exportName_7,
            Ctor = lua_temp_8
        }
        R.defs[id] = def
        return true
    end
    local function _resolveCtor(self, def)
        if def.Ctor then
            return def.Ctor
        end
        if not def.moduleId then
            error(
                LuaConstruct(
                    Error,
                    "[CONTROLLERS] no Ctor/moduleId for: " .. tostring(def.id)
                ),
                0
            )
        end
        local exp = require(def.moduleId)
        local lua_def_exportName_10
        if def.exportName then
            lua_def_exportName_10 = exp[def.exportName]
        else
            lua_def_exportName_10 = exp
        end
        local ctor = lua_def_exportName_10
        if KTypeOf(ctor) ~= "function" then
            error(
                LuaConstruct(
                    Error,
                    (((("[CONTROLLERS] resolved ctor is not a function for: " .. tostring(def.id)) .. " moduleId=") .. tostring(def.moduleId)) .. " export=") .. tostring(def.exportName)
                ),
                0
            )
        end
        def.Ctor = ctor
        return ctor
    end
    local function build(self, registryName, ctx, entity, cfg)
        local R = ensureRegistry(_G, registryName)
        local defsArr = LuaArrayMap(
            LuaTableKeys(R.defs),
            function(lua_, k) return R.defs[k] end
        )
        local active = {}
        local activeSet = KObject:create(nil)
        do
            local i = 0
            while i < #defsArr do
                do
                    local d = defsArr[i + 1]
                    if not d.enabled then
                        goto lua_continue17
                    end
                    if d.when and d:when(ctx, entity, cfg) ~= true then
                        goto lua_continue17
                    end
                    active[#active + 1] = d
                    activeSet[d.id] = d
                end
                ::lua_continue17::
                i = i + 1
            end
        end
        do
            local i = 0
            while i < #active do
                local d = active[i + 1]
                do
                    local j = 0
                    while j < KLength(d.deps) do
                        local dep = KIndex(d.deps, j)
                        if not R.defs[dep] then
                            error(
                                LuaConstruct(
                                    Error,
                                    (("[CONTROLLERS] unknown dep: " .. tostring(d.id)) .. " -> ") .. tostring(dep)
                                ),
                                0
                            )
                        end
                        if not activeSet[dep] then
                            error(
                                LuaConstruct(
                                    Error,
                                    (("[CONTROLLERS] dep disabled: " .. tostring(d.id)) .. " -> ") .. tostring(dep)
                                ),
                                0
                            )
                        end
                        j = j + 1
                    end
                end
                i = i + 1
            end
        end
        local indeg = KObject:create(nil)
        local edges = KObject:create(nil)
        do
            local i = 0
            while i < #active do
                local d = active[i + 1]
                indeg[d.id] = 0
                edges[d.id] = {}
                i = i + 1
            end
        end
        do
            local i = 0
            while i < #active do
                local d = active[i + 1]
                do
                    local j = 0
                    while j < KLength(d.deps) do
                        local dep = KIndex(d.deps, j)
                        KArrayOps.push(edges[dep], d.id)
                        indeg[d.id] = bit32.bor(indeg[d.id], 0) + 1
                        j = j + 1
                    end
                end
                i = i + 1
            end
        end
        local q = {}
        do
            local i = 0
            while i < #active do
                if indeg[active[i + 1].id] == 0 then
                    q[#q + 1] = active[i + 1]
                end
                i = i + 1
            end
        end
        LuaArraySort(
            q,
            function(lua_, a, b) return a.order - b.order or (a.id < b.id and -1 or (a.id > b.id and 1 or 0)) end
        )
        local ordered = {}
        while #q > 0 do
            local d = table.remove(q, 1)
            ordered[#ordered + 1] = d
            local out = edges[d.id]
            do
                local i = 0
                while i < KLength(out) do
                    local to = KIndex(out, i)
                    indeg[to] = indeg[to] - 1
                    if indeg[to] == 0 then
                        q[#q + 1] = activeSet[to]
                        LuaArraySort(
                            q,
                            function(lua_, a, b) return a.order - b.order or (a.id < b.id and -1 or (a.id > b.id and 1 or 0)) end
                        )
                    end
                    i = i + 1
                end
            end
        end
        if #ordered ~= #active then
            local stuck = {}
            do
                local i = 0
                while i < #active do
                    if indeg[active[i + 1].id] > 0 then
                        stuck[#stuck + 1] = active[i + 1].id
                    end
                    i = i + 1
                end
            end
            error(
                LuaConstruct(
                    Error,
                    "[CONTROLLERS] dependency cycle: " .. LuaArrayJoin(stuck, " -> ")
                ),
                0
            )
        end
        local list = KArray(_G, #ordered)
        local ids = KArray(_G, #ordered)
        do
            local i = 0
            while i < #ordered do
                local d = ordered[i + 1]
                local Ctor = _resolveCtor(_G, d)
                KSetIndex(ids, i, d.id)
                KSetIndex(list, i, LuaConstruct(Ctor, cfg or nil))
                i = i + 1
            end
        end
        return {list = list, ids = ids}
    end
    return {ensureRegistry = ensureRegistry, has = has, register = register, build = build}
end
function loadRegistrators(self, engine, K, controllersCfg, CONTROLLERS)
    local lua_isPlainObj_result_11
    if isPlainObj(_G, controllersCfg) then
        lua_isPlainObj_result_11 = controllersCfg
    else
        lua_isPlainObj_result_11 = KObject:create(nil)
    end
    local cfg = lua_isPlainObj_result_11
    local lua_Array_isArray_result_12
    if LuaArrayIsArray(cfg.registrators) then
        lua_Array_isArray_result_12 = cfg.registrators
    else
        lua_Array_isArray_result_12 = {}
    end
    local regs = lua_Array_isArray_result_12
    do
        local i = 0
        while i < KLength(regs) do
            do
                local mid = tostring(KIndex(regs, i) or "")
                if not mid then
                    goto lua_continue49
                end
                if KArrayOps.indexOf(K.controllersRegs, mid) >= 0 then
                    goto lua_continue49
                end
                local regExp = require(mid)
                if KTypeOf(regExp) ~= "function" then
                    error(
                        LuaConstruct(Error, "[CONTROLLERS] registrator must export function(engine,K,CONTROLLERS,cfg): " .. mid),
                        0
                    )
                end
                regExp(
                    _G,
                    engine,
                    K,
                    CONTROLLERS,
                    K.config
                )
                KArrayOps.push(K.controllersRegs, mid)
            end
            ::lua_continue49::
            i = i + 1
        end
    end
end
M = {createControllersApi = createControllersApi, loadRegistrators = loadRegistrators}

return M
