local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local Arrays = luaRuntime.array
local Tables = luaRuntime.table
local Classes = luaRuntime.class
local Error = luaRuntime.Error
function req(self, v, msg)
    if v == nil then
        error(
            Classes:construct(Error, msg),
            0
        )
    end
    return v
end
function reqStr(self, s, msg)
    if KTypeOf(s) ~= "string" or not s then
        error(
            Classes:construct(Error, msg),
            0
        )
    end
    return s
end
function loadCore(self)
    local lua_require_result_0 = require("./helpers/ControllerStack")
    local ControllerStack = lua_require_result_0.ControllerStack
    local lua_require_result_1 = require("./helpers/ControllerRegistry")
    local ControllerRegistry = lua_require_result_1.ControllerRegistry
    local lua_require_result_2 = require("./helpers/EngineControllers")
    local ensureControllersHub = lua_require_result_2.ensureControllersHub
    local lua_require_result_3 = require("./helpers/EntityController")
    local EntityController = lua_require_result_3.EntityController
    return {ControllerStack = ControllerStack, ControllerRegistry = ControllerRegistry, ensureControllersHub = ensureControllersHub, EntityController = EntityController}
end
create = setmetatable(
    {},
    {__call = function(lua_, self, engine, K)
        req(_G, engine, "[Controllers] engine is required")
        req(_G, K, "[Controllers] root state is required")
        local lua_loadCore_result_4 = loadCore(_G)
        local ControllerStack = lua_loadCore_result_4.ControllerStack
        local ControllerRegistry = lua_loadCore_result_4.ControllerRegistry
        local ensureControllersHub = lua_loadCore_result_4.ensureControllersHub
        local EntityController = lua_loadCore_result_4.EntityController
        local hub = ensureControllersHub(_G, engine, K)
        local function resolveRegistry(self, nameOrRegistry)
            if KTypeOf(nameOrRegistry) == "string" then
                local name = reqStr(_G, nameOrRegistry, "[Controllers] registry name is required")
                local lua_temp_5
                if hub and KTypeOf(hub.get) == "function" then
                    lua_temp_5 = hub:get(name)
                else
                    lua_temp_5 = nil
                end
                local r = lua_temp_5
                if not r then
                    r = Classes:construct(ControllerRegistry, name)
                    if hub and KTypeOf(hub.set) == "function" then
                        hub:set(r)
                    end
                end
                return r
            end
            return req(_G, nameOrRegistry, "[Controllers] registry is required")
        end
        local function buildStack(self, registryOrName, ctx, entity, cfg)
            local registry = resolveRegistry(_G, registryOrName)
            ctx = req(_G, ctx, "[Controllers] ctx is required")
            entity = req(_G, entity, "[Controllers] entity is required")
            if KTypeOf(registry.build) ~= "function" then
                error(
                    Classes:construct(Error, "[Controllers] registry.build(ctx,entity,cfg) is required"),
                    0
                )
            end
            local built = registry:build(ctx, entity, cfg or nil)
            local stack = Classes:construct(ControllerStack, built.list, built.ids)
            if KTypeOf(stack.bind) == "function" then
                stack:bind(ctx, entity)
            end
            return stack
        end
        local function absorbRegistratorExport(self, exp, id)
            if KTypeOf(exp) == "function" then
                exp(_G, hub, engine, K)
                return
            end
            if exp and Arrays:isArray(exp.registries) then
                do
                    local i = 0
                    while i < KLength(exp.registries) do
                        hub:set(KIndex(exp.registries, i))
                        i = i + 1
                    end
                end
                return
            end
            local ok = false
            if exp and KTypeOf(exp) == "table" then
                for lua_, k in ipairs(Tables:keys(exp)) do
                    do
                        if (string.find(k, "create", nil, true) or 0) - 1 ~= 0 or KString:lastIndexOf(k, "Registry") ~= #k - #"Registry" then
                            goto lua_continue21
                        end
                        local fn = exp[k]
                        if KTypeOf(fn) ~= "function" then
                            goto lua_continue21
                        end
                        local r = fn(_G)
                        hub:set(r)
                        ok = true
                    end
                    ::lua_continue21::
                end
            end
            if not ok then
                error(
                    Classes:construct(
                        Error,
                        "[Controllers] invalid registrator export: " .. reqStr(_G, id, "id")
                    ),
                    0
                )
            end
        end
        local function tryInstallHotReload(self, apiObj)
            do
                local function lua_catch(_)
                    return true, false
                end
                local lua_try, lua_hasReturned, lua_returnValue = pcall(function()
                    local ctx = K and (K.ctx or K.context) or engine and (engine.ctx or engine.context) or nil
                    if not ctx then
                        return true, false
                    end
                    local hr = nil
                    do
                        local function lua_catch(_)
                            hr = nil
                        end
                        local lua_try, lua_hasReturned = pcall(function()
                            if KTypeOf(ctx.hotReload) == "function" then
                                hr = ctx:hotReload()
                            else
                                hr = ctx.hotReload
                            end
                        end)
                        if not lua_try then
                            lua_catch(lua_hasReturned)
                        end
                    end
                    if not hr or KTypeOf(hr.register) ~= "function" then
                        return true, false
                    end
                    local FLAG = "__CTRL_HR_INSTALLED__"
                    local function getStateDomain(self)
                        do
                            local lua_try, lua_hasReturned, lua_returnValue = pcall(function()
                                if ctx.stateDomain and KTypeOf(ctx.stateDomain.get) == "function" then
                                    return true, ctx.stateDomain
                                end
                            end)
                            if lua_try and lua_hasReturned then
                                return lua_returnValue
                            end
                        end
                        do
                            local lua_try, lua_hasReturned, lua_returnValue = pcall(function()
                                if KTypeOf(ctx.state) == "function" then
                                    return true, ctx:state()
                                end
                            end)
                            if lua_try and lua_hasReturned then
                                return lua_returnValue
                            end
                        end
                        do
                            local lua_try, lua_hasReturned, lua_returnValue = pcall(function()
                                if ctx.state and KTypeOf(ctx.state.get) == "function" then
                                    return true, ctx.state
                                end
                            end)
                            if lua_try and lua_hasReturned then
                                return lua_returnValue
                            end
                        end
                        return nil
                    end
                    local sd = getStateDomain(_G)
                    if sd and KTypeOf(sd.get) == "function" and sd:get(FLAG) == true then
                        return true, true
                    end
                    hr:register(function()
                        do
                            pcall(function()
                                apiObj:resetAll()
                            end)
                        end
                    end)
                    if sd and KTypeOf(sd.set) == "function" then
                        sd:set(FLAG, true)
                    end
                    return true, true
                end)
                if not lua_try then
                    lua_hasReturned, lua_returnValue = lua_catch(lua_hasReturned)
                end
                if lua_hasReturned then
                    return lua_returnValue
                end
            end
        end
        local api = KObject:freeze({
            Registry = ControllerRegistry,
            loadRegistrators = function(self, list)
                if not Arrays:isArray(list) then
                    return false
                end
                do
                    local i = 0
                    while i < #list do
                        do
                            local id = tostring(list[i + 1] or "")
                            if not id then
                                goto lua_continue49
                            end
                            local exp = require(id)
                            absorbRegistratorExport(_G, exp, id)
                        end
                        ::lua_continue49::
                        i = i + 1
                    end
                end
                return true
            end,
            registry = function(self, name)
                return resolveRegistry(
                    _G,
                    reqStr(_G, name, "[Controllers] registry name is required")
                )
            end,
            registerRegistry = function(self, registry)
                registry = req(_G, registry, "[Controllers] registry is required")
                if not hub or KTypeOf(hub.set) ~= "function" then
                    error(
                        Classes:construct(Error, "[Controllers] controllers hub has no set(registry)"),
                        0
                    )
                end
                hub:set(registry)
                return registry
            end,
            register = function(self, name, fn)
                name = reqStr(_G, name, "[Controllers] name is required")
                fn = req(_G, fn, "[Controllers] fn is required")
                if KTypeOf(fn) ~= "function" then
                    error(
                        Classes:construct(Error, "[Controllers] register(name, fn): fn must be function"),
                        0
                    )
                end
                local r = resolveRegistry(_G, name)
                fn(_G, r)
                if hub and KTypeOf(hub.set) == "function" then
                    hub:set(r)
                end
                return r
            end,
            reset = function(self, name)
                name = reqStr(_G, name, "[Controllers] name is required")
                if hub and KTypeOf(hub.reset) == "function" then
                    return hub:reset(name)
                end
                local r = resolveRegistry(_G, name)
                if r and KTypeOf(r.clear) == "function" then
                    r:clear()
                end
                return r
            end,
            resetAll = function(self)
                if hub and KTypeOf(hub.resetAll) == "function" then
                    hub:resetAll()
                end
                return true
            end,
            stack = function(self, registryOrName, ctx, entity, cfg)
                return buildStack(
                    _G,
                    registryOrName,
                    ctx,
                    entity,
                    cfg
                )
            end,
            entity = function(self, registryOrName, ctx, entity, cfg)
                local stack = buildStack(
                    _G,
                    registryOrName,
                    ctx,
                    entity,
                    cfg
                )
                return Classes:construct(EntityController, ctx, entity, stack)
            end
        })
        tryInstallHotReload(_G, api)
        return api
    end}
)
create.META = {
    moduleId = "controllers",
    version = "1.0.5",
    description = "ENGINE.controllers hub space + game-side registration + reset/resetAll + self-installed hot reload hook.",
    engineMin = "0.1.0"
}
M = create

return M
