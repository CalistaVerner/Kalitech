local M = {}
local json = require("@builtin/json")
local luaRuntime = require("@builtin/lua_runtime")
local Error = luaRuntime.Error
local LuaInstanceOf = luaRuntime.LuaInstanceOf
local LuaTypeOf = luaRuntime.LuaTypeOf
local LuaStringTrim = luaRuntime.LuaStringTrim
local LuaConstruct = luaRuntime.LuaConstruct
function safeJson(self, v)
    do
        local lua_try, lua_hasReturned, lua_returnValue = pcall(function()
            return true, json:encode(v)
        end)
        if lua_try and lua_hasReturned then
            return lua_returnValue
        end
    end
    do
        local lua_try, lua_hasReturned, lua_returnValue = pcall(function()
            return true, tostring(v)
        end)
        if lua_try and lua_hasReturned then
            return lua_returnValue
        end
    end
    return "[unserializable]"
end
function isThrowableLike(self, v)
    if not v then
        return false
    end
    do
        local lua_try, lua_hasReturned, lua_returnValue = pcall(function()
            if LuaInstanceOf(v, Error) then
                return true, true
            end
        end)
        if lua_try and lua_hasReturned then
            return lua_returnValue
        end
    end
    local t = LuaTypeOf(v)
    if t ~= "object" and t ~= "function" then
        return false
    end
    do
        local lua_try, lua_hasReturned, lua_returnValue = pcall(function()
            local stack = v.stack
            if KTypeOf(stack) == "string" and #stack > 0 then
                return true, true
            end
        end)
        if lua_try and lua_hasReturned then
            return lua_returnValue
        end
    end
    do
        local lua_try, lua_hasReturned, lua_returnValue = pcall(function()
            local name = v.name
            local msg = v.message
            if KTypeOf(name) == "string" and #name > 0 and KTypeOf(msg) == "string" then
                return true, true
            end
        end)
        if lua_try and lua_hasReturned then
            return lua_returnValue
        end
    end
    do
        local lua_try, lua_hasReturned, lua_returnValue = pcall(function()
            if KTypeOf(v.getClass) == "function" then
                return true, true
            end
        end)
        if lua_try and lua_hasReturned then
            return lua_returnValue
        end
    end
    return false
end
function throwableToText(self, e)
    if not e then
        return "null"
    end
    do
        local lua_try, lua_hasReturned, lua_returnValue = pcall(function()
            if LuaInstanceOf(e, Error) then
                return true, e.stack or e.message or tostring(e)
            end
        end)
        if lua_try and lua_hasReturned then
            return lua_returnValue
        end
    end
    do
        local lua_try, lua_hasReturned, lua_returnValue = pcall(function()
            if KTypeOf(e.stack) == "string" and e.stack then
                return true, e.stack
            end
        end)
        if lua_try and lua_hasReturned then
            return lua_returnValue
        end
    end
    do
        local lua_try, lua_hasReturned, lua_returnValue = pcall(function()
            if KTypeOf(e.message) == "string" and e.message then
                return true, e.message
            end
        end)
        if lua_try and lua_hasReturned then
            return lua_returnValue
        end
    end
    do
        local lua_try, lua_hasReturned, lua_returnValue = pcall(function()
            return true, tostring(e)
        end)
        if lua_try and lua_hasReturned then
            return lua_returnValue
        end
    end
    return "[unserializable-exception]"
end
function valueToText(self, v)
    if v == nil then
        return "null"
    end
    local t = LuaTypeOf(v)
    if t == "string" then
        return v
    end
    if t == "number" or t == "boolean" or t == "bigint" then
        return tostring(v)
    end
    if isThrowableLike(_G, v) then
        return throwableToText(_G, v)
    end
    if t == "object" then
        return safeJson(_G, v)
    end
    do
        local lua_try, lua_hasReturned, lua_returnValue = pcall(function()
            return true, tostring(v)
        end)
        if lua_try and lua_hasReturned then
            return lua_returnValue
        end
    end
    return "[unserializable]"
end
function joinArgs(self, args, from, toExclusive)
    local out = ""
    do
        local i = from
        while i < toExclusive do
            out = out .. (i > from and " " or "") .. tostring(valueToText(_G, KIndex(args, i)))
            i = i + 1
        end
    end
    return out
end
function makePrefix(self, scope)
    local s = LuaStringTrim(tostring(scope or ""))
    local lua_s_0
    if s then
        lua_s_0 = ("[" .. s) .. "] "
    else
        lua_s_0 = ""
    end
    return lua_s_0
end
function makeApi(self, engine)
    local lua_temp_1
    if engine and engine.log and KTypeOf(engine.log) == "function" then
        lua_temp_1 = engine:log()
    else
        lua_temp_1 = nil
    end
    local log = lua_temp_1
    local function has(self, fn)
        return not not (log and KTypeOf(log[fn]) == "function")
    end
    local function call1(self, levelFn, msg)
        if not log then
            return
        end
        if has(_G, levelFn) then
            log[levelFn](log, msg)
        elseif has(_G, "info") then
            log:info(msg)
        end
    end
    local function write(self, levelFn, scope, args)
        local prefix = makePrefix(_G, scope)
        if not args or KLength(args) == 0 then
            local msg0 = prefix
            do
                pcall(function()
                    call1(_G, levelFn, msg0)
                end)
            end
            return msg0
        end
        if KLength(args) >= 2 and isThrowableLike(_G, KIndex(args, KLength(args) - 1)) then
            local head = prefix .. joinArgs(_G, args, 0, KLength(args) - 1)
            local err = KIndex(args, KLength(args) - 1)
            local msg = (head .. "\n") .. tostring(throwableToText(_G, err))
            do
                pcall(function()
                    call1(_G, levelFn, msg)
                end)
            end
            return msg
        end
        local msg = prefix .. joinArgs(_G, args, 0, KLength(args))
        do
            pcall(function()
                call1(_G, levelFn, msg)
            end)
        end
        return msg
    end
    local function trace(self, ...)
        local args = {...}
        return write(_G, "trace", "", args)
    end
    local function lua_debug(self, ...)
        local args = {...}
        return write(_G, "debug", "", args)
    end
    local function info(self, ...)
        local args = {...}
        return write(_G, "info", "", args)
    end
    local function warn(self, ...)
        local args = {...}
        return write(_G, "warn", "", args)
    end
    local function lua_error(self, ...)
        local args = {...}
        return write(_G, "error", "", args)
    end
    local function fatal(self, ...)
        local args = {...}
        return write(_G, "fatal", "", args)
    end
    local function scoped(self, scopeName)
        local scope = LuaStringTrim(tostring(scopeName or ""))
        return KObject:freeze({
            trace = function(self, ...)
                local args = {...}
                return write(_G, "trace", scope, args)
            end,
            debug = function(self, ...)
                local args = {...}
                return write(_G, "debug", scope, args)
            end,
            info = function(self, ...)
                local args = {...}
                return write(_G, "info", scope, args)
            end,
            warn = function(self, ...)
                local args = {...}
                return write(_G, "warn", scope, args)
            end,
            error = function(self, ...)
                local args = {...}
                return write(_G, "error", scope, args)
            end,
            fatal = function(self, ...)
                local args = {...}
                return write(_G, "fatal", scope, args)
            end,
            scope = scope
        })
    end
    local function enabled(self)
        return not not log
    end
    return KObject:freeze({
        enabled = enabled,
        trace = trace,
        debug = lua_debug,
        info = info,
        warn = warn,
        error = lua_error,
        fatal = fatal,
        child = scoped,
        scope = scoped,
        safeJson = safeJson
    })
end
create = setmetatable(
    {},
    {__call = function(lua_, self, engine, K)
        if not engine then
            error(
                LuaConstruct(Error, "[LOG] engine is required"),
                0
            )
        end
        return makeApi(_G, engine, K)
    end}
)
create.META = {
    moduleId = "log",
    version = "1.2.0",
    description = "Rootkit wrapper for engine.log() with safe formatting + scoped child loggers",
    engineMin = "0.1.0"
}
M = create

return M
