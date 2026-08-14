local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local Error = luaRuntime.Error
local LuaConstruct = luaRuntime.LuaConstruct
local LuaNumberIsFinite = luaRuntime.LuaNumberIsFinite
local LuaClass = luaRuntime.LuaClass
local LuaMap = luaRuntime.LuaMap
local LuaArrayIsArray = luaRuntime.LuaArrayIsArray
local LuaIterator = luaRuntime.LuaIterator
local LuaArraySort = luaRuntime.LuaArraySort
local LuaArrayJoin = luaRuntime.LuaArrayJoin
function req(self, v, msg)
    if v == nil then
        error(
            LuaConstruct(Error, msg),
            0
        )
    end
    return v
end
function reqStr(self, s, msg)
    if KTypeOf(s) ~= "string" or not s then
        error(
            LuaConstruct(Error, msg),
            0
        )
    end
    return s
end
function reqFn(self, fn, msg)
    if KTypeOf(fn) ~= "function" then
        error(
            LuaConstruct(Error, msg),
            0
        )
    end
    return fn
end
function safeBool(self, v, fb)
    local lua_temp_1
    if v == true then
        lua_temp_1 = true
    else
        local lua_temp_0
        if v == false then
            lua_temp_0 = false
        else
            lua_temp_0 = fb
        end
        lua_temp_1 = lua_temp_0
    end
    return lua_temp_1
end
function safeNum(self, v, fb)
    local lua_Number_isFinite_result_2
    if LuaNumberIsFinite(v) then
        lua_Number_isFinite_result_2 = v
    else
        lua_Number_isFinite_result_2 = fb
    end
    return lua_Number_isFinite_result_2
end
ControllerRegistry = LuaClass()
ControllerRegistry.name = "ControllerRegistry"
function ControllerRegistry.prototype.lua_constructor(self, name)
    self.name = name or "registry"
    self._defs = LuaConstruct(LuaMap)
end
function ControllerRegistry.prototype.clear(self)
    self._defs:clear()
    return self
end
function ControllerRegistry.prototype.register(self, id, Ctor, opts)
    id = reqStr(
        _G,
        id,
        ("[Registry:" .. tostring(self.name)) .. "] id is required"
    )
    Ctor = reqFn(
        _G,
        Ctor,
        ("[Registry:" .. tostring(self.name)) .. "] Ctor is required"
    )
    opts = opts or ({})
    local lua_KObject_9 = KObject
    local lua_KObject_freeze_10 = KObject.freeze
    local lua_id_5 = id
    local lua_Ctor_6 = Ctor
    local lua_safeNum_result_7 = safeNum(_G, opts.order, 0)
    local lua_Array_isArray_result_3
    if LuaArrayIsArray(opts.deps) then
        lua_Array_isArray_result_3 = KArrayOps.slice(opts.deps)
    else
        lua_Array_isArray_result_3 = {}
    end
    local lua_safeBool_result_8 = safeBool(_G, opts.enabled, true)
    local lua_temp_4
    if KTypeOf(opts.when) == "function" then
        lua_temp_4 = opts.when
    else
        lua_temp_4 = nil
    end
    local def = lua_KObject_freeze_10(lua_KObject_9, {
        id = lua_id_5,
        Ctor = lua_Ctor_6,
        order = lua_safeNum_result_7,
        deps = lua_Array_isArray_result_3,
        enabled = lua_safeBool_result_8,
        when = lua_temp_4
    })
    self._defs:set(id, def)
    return self
end
function ControllerRegistry.prototype.registerPack(self, packFn, packCfg)
    packFn = reqFn(
        _G,
        packFn,
        ("[Registry:" .. tostring(self.name)) .. "] packFn(registry, cfg) is required"
    )
    packFn(_G, self, packCfg or nil)
    return self
end
function ControllerRegistry.prototype.build(self, ctx, entity, cfg)
    ctx = req(
        _G,
        ctx,
        ("[Registry:" .. tostring(self.name)) .. "] ctx is required"
    )
    entity = req(
        _G,
        entity,
        ("[Registry:" .. tostring(self.name)) .. "] entity is required"
    )
    local active = {}
    for lua_, def in LuaIterator(self._defs:values()) do
        do
            if not def.enabled then
                goto lua_continue15
            end
            if def.when and def:when(ctx, entity, cfg) ~= true then
                goto lua_continue15
            end
            active[#active + 1] = def
        end
        ::lua_continue15::
    end
    local activeSet = LuaConstruct(LuaMap)
    do
        local i = 0
        while i < #active do
            activeSet:set(active[i + 1].id, active[i + 1])
            i = i + 1
        end
    end
    for lua_, def in ipairs(active) do
        do
            local i = 0
            while i < KLength(def.deps) do
                local dep = KIndex(def.deps, i)
                if not self._defs:has(dep) then
                    error(
                        LuaConstruct(
                            Error,
                            ((((("[Registry:" .. tostring(self.name)) .. "] '") .. tostring(def.id)) .. "' depends on unknown '") .. tostring(dep)) .. "'"
                        ),
                        0
                    )
                end
                if not activeSet:has(dep) then
                    error(
                        LuaConstruct(
                            Error,
                            ((((("[Registry:" .. tostring(self.name)) .. "] '") .. tostring(def.id)) .. "' depends on disabled '") .. tostring(dep)) .. "'"
                        ),
                        0
                    )
                end
                i = i + 1
            end
        end
    end
    local indeg = LuaConstruct(LuaMap)
    local edges = LuaConstruct(LuaMap)
    for lua_, def in ipairs(active) do
        indeg:set(def.id, 0)
        edges:set(def.id, {})
    end
    for lua_, def in ipairs(active) do
        do
            local i = 0
            while i < KLength(def.deps) do
                local dep = KIndex(def.deps, i)
                KArrayOps.push(edges:get(dep), def.id)
                indeg:set(
                    def.id,
                    indeg:get(def.id) + 1
                )
                i = i + 1
            end
        end
    end
    local queue = {}
    for lua_, def in ipairs(active) do
        if indeg:get(def.id) == 0 then
            queue[#queue + 1] = def
        end
    end
    LuaArraySort(
        queue,
        function(lua_, a, b) return a.order - b.order or (a.id < b.id and -1 or (a.id > b.id and 1 or 0)) end
    )
    local orderedDefs = {}
    while #queue > 0 do
        local def = table.remove(queue, 1)
        orderedDefs[#orderedDefs + 1] = def
        local out = edges:get(def.id)
        do
            local i = 0
            while i < KLength(out) do
                local to = KIndex(out, i)
                indeg:set(
                    to,
                    indeg:get(to) - 1
                )
                if indeg:get(to) == 0 then
                    queue[#queue + 1] = activeSet:get(to)
                    LuaArraySort(
                        queue,
                        function(lua_, a, b) return a.order - b.order or (a.id < b.id and -1 or (a.id > b.id and 1 or 0)) end
                    )
                end
                i = i + 1
            end
        end
    end
    if #orderedDefs ~= #active then
        local stuck = {}
        for lua_, def in ipairs(active) do
            if indeg:get(def.id) > 0 then
                stuck[#stuck + 1] = def.id
            end
        end
        error(
            LuaConstruct(
                Error,
                (("[Registry:" .. tostring(self.name)) .. "] dependency cycle: ") .. LuaArrayJoin(stuck, " -> ")
            ),
            0
        )
    end
    local list = KArray(_G, #orderedDefs)
    local ids = KArray(_G, #orderedDefs)
    do
        local i = 0
        while i < #orderedDefs do
            local def = orderedDefs[i + 1]
            KSetIndex(ids, i, def.id)
            KSetIndex(list, i, LuaConstruct(def.Ctor, cfg or nil))
            i = i + 1
        end
    end
    return {list = list, ids = ids}
end
M = {ControllerRegistry = ControllerRegistry}

return M
