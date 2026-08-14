local unpackValues = table.unpack or unpack

local function LuaArrayIsArray(value)
    return type(value) == "table" and (value[1] ~= nil or next(value) == nil)
end

local function LuaArrayConcat(self, ...)
    local result = {}
    for i = 1, #self do
        result[#result + 1] = self[i]
    end
    for _, item in ipairs({...}) do
        if LuaArrayIsArray(item) then
            for i = 1, #item do
                result[#result + 1] = item[i]
            end
        else
            result[#result + 1] = item
        end
    end
    return result
end

local function LuaArrayEvery(self, callback, thisArg)
    for i = 1, #self do
        if not callback(thisArg, self[i], i - 1, self) then
            return false
        end
    end
    return true
end

local function LuaArrayJoin(self, separator)
    local parts = {}
    for i = 1, #self do
        parts[i] = tostring(self[i])
    end
    return table.concat(parts, separator == nil and "," or separator)
end

local function LuaArrayMap(self, callback, thisArg)
    local result = {}
    for i = 1, #self do
        result[i] = callback(thisArg, self[i], i - 1, self)
    end
    return result
end

local function LuaArrayReduce(self, callback, ...)
    local length = #self
    local index = 1
    local accumulator
    if select("#", ...) > 0 then
        accumulator = select(1, ...)
    elseif length > 0 then
        accumulator = self[1]
        index = 2
    else
        error("Cannot reduce an empty Lua array without an initial value", 0)
    end
    while index <= length do
        accumulator = callback(nil, accumulator, self[index], index - 1, self)
        index = index + 1
    end
    return accumulator
end

local function LuaArraySetLength(self, length)
    if type(length) ~= "number" or length < 0 or length ~= length or length == math.huge or math.floor(length) ~= length then
        error("invalid Lua array length: " .. tostring(length), 0)
    end
    for i = length + 1, #self do
        self[i] = nil
    end
    return length
end

local function LuaArraySlice(self, first, last)
    local length = #self
    first = first or 0
    if first < 0 then first = math.max(0, length + first) else first = math.min(length, first) end
    last = last or length
    if last < 0 then last = math.max(0, length + last) else last = math.min(length, last) end
    local result = {}
    for i = first + 1, last do
        result[#result + 1] = self[i]
    end
    return result
end

local function LuaArraySort(self, compare)
    if compare then
        table.sort(self, function(a, b) return compare(nil, a, b) < 0 end)
    else
        table.sort(self)
    end
    return self
end

local function LuaStringAccess(self, index)
    if index >= 0 and index < #self then
        return string.sub(self, index + 1, index + 1)
    end
    return nil
end

local function LuaStringSlice(self, startIndex, endIndex)
    startIndex = startIndex or 0
    if startIndex >= 0 then startIndex = startIndex + 1 end
    if endIndex ~= nil and endIndex < 0 then endIndex = endIndex - 1 end
    return string.sub(self, startIndex, endIndex)
end

local function LuaStringSplit(source, separator, limit)
    limit = limit or 4294967295
    if limit <= 0 then return {} end
    local result = {}
    if separator == nil or separator == "" then
        for i = 1, math.min(#source, limit) do
            result[#result + 1] = string.sub(source, i, i)
        end
        return result
    end
    local position = 1
    while #result < limit do
        local first, last = string.find(source, separator, position, true)
        if not first then break end
        result[#result + 1] = string.sub(source, position, first - 1)
        position = last + 1
    end
    if #result < limit then result[#result + 1] = string.sub(source, position) end
    return result
end

local function LuaStringTrim(value)
    return (string.gsub(tostring(value), "^%s*(.-)%s*$", "%1"))
end

local function LuaNumberIsNaN(value)
    return type(value) == "number" and value ~= value
end

local function LuaNumberIsFinite(value)
    return type(value) == "number" and value == value and value ~= math.huge and value ~= -math.huge
end

local function LuaNumber(value)
    local valueType = type(value)
    if valueType == "number" then return value end
    if valueType == "boolean" then return value and 1 or 0 end
    if valueType == "string" then
        local parsed = tonumber(value)
        if parsed ~= nil then return parsed end
    end
    return 0 / 0
end

local function LuaParseInt(value, radix)
    local parsed = tonumber(tostring(value), radix or 10)
    if parsed == nil then return 0 / 0 end
    return parsed >= 0 and math.floor(parsed) or math.ceil(parsed)
end

local function LuaNumberToFixed(value, digits)
    local precision = math.floor(digits or 0)
    if precision < 0 or precision > 99 then
        error("fixed-point precision must be between 0 and 99", 0)
    end
    return string.format("%." .. tostring(precision) .. "f", value)
end

local function LuaTableMerge(target, ...)
    for _, source in ipairs({...}) do
        if type(source) == "table" then
            for key, value in pairs(source) do target[key] = value end
        end
    end
    return target
end

local function LuaTableKeys(source)
    local result = {}
    for key in pairs(source) do result[#result + 1] = key end
    return result
end

local function LuaClass()
    local class = {prototype = {}}
    class.prototype.__index = class.prototype
    class.prototype.constructor = class
    return class
end

local function LuaConstruct(target, ...)
    if type(target) ~= "table" or type(target.prototype) ~= "table" then
        error("Lua constructor target must be a class table", 0)
    end
    local instance = setmetatable({}, target.prototype)
    if type(instance.lua_constructor) == "function" then
        instance:lua_constructor(...)
    end
    return instance
end

local function LuaClassExtends(target, base)
    target.lua_super = base
    setmetatable(target, {__index = base})
    setmetatable(target.prototype, base.prototype)
    if type(base.prototype.__index) == "function" then target.prototype.__index = base.prototype.__index end
    if type(base.prototype.__newindex) == "function" then target.prototype.__newindex = base.prototype.__newindex end
    if type(base.prototype.__tostring) == "function" then target.prototype.__tostring = base.prototype.__tostring end
end

local function LuaInstanceOf(value, class)
    if type(value) ~= "table" or type(class) ~= "table" then return false end
    local current = value.constructor
    while current ~= nil do
        if current == class then return true end
        current = current.lua_super
    end
    return false
end

local function descriptorGet(self, prototype, key)
    while prototype do
        local direct = rawget(prototype, key)
        if direct ~= nil then return direct end
        local descriptors = rawget(prototype, "_luaProperties")
        local descriptor = descriptors and descriptors[key]
        if descriptor then
            if descriptor.get then return descriptor.get(self) end
            return descriptor.value
        end
        prototype = getmetatable(prototype)
    end
    return nil
end

local function descriptorSet(self, prototype, key, value)
    while prototype do
        local descriptors = rawget(prototype, "_luaProperties")
        local descriptor = descriptors and descriptors[key]
        if descriptor then
            if descriptor.set then
                descriptor.set(self, value)
            elseif descriptor.writable == false then
                error("Lua property is read-only: " .. tostring(key), 0)
            else
                descriptor.value = value
            end
            return
        end
        prototype = getmetatable(prototype)
    end
    rawset(self, key, value)
end

local function LuaDefineProperty(target, key, descriptor, isPrototype)
    local prototype = isPrototype and target or getmetatable(target)
    if prototype == nil then
        prototype = {}
        setmetatable(target, prototype)
    end
    if rawget(prototype, "_luaProperties") == nil then prototype._luaProperties = {} end
    prototype._luaProperties[key] = {
        get = descriptor.get,
        set = descriptor.set,
        value = descriptor.value,
        writable = descriptor.writable ~= false
    }
    prototype.__index = function(self, name) return descriptorGet(self, getmetatable(self), name) end
    prototype.__newindex = function(self, name, value) return descriptorSet(self, getmetatable(self), name, value) end
end

local function LuaTableRemove(target, key)
    target[key] = nil
    return true
end

local Error = LuaClass()
Error.name = "Error"
function Error.prototype.lua_constructor(self, message)
    self.name = "Error"
    self.message = tostring(message or "")
    self.stack = debug and debug.traceback and debug.traceback("", 3) or ""
end
function Error.prototype.__tostring(self)
    return self.message ~= "" and (self.name .. ": " .. self.message) or self.name
end
setmetatable(Error, {__call = function(_, _, message) return LuaConstruct(Error, message) end})

local function makeIterator(values)
    local index = 0
    return {
        next = function()
            index = index + 1
            if index > #values then return {done = true} end
            return {done = false, value = values[index]}
        end
    }
end

local function iteratorStep(iterator)
    local result = iterator:next()
    if result == nil or result.done then return nil end
    return true, result.value
end

local function stringStep(value, index)
    index = index + 1
    if index > #value then return nil end
    return index, string.sub(value, index, index)
end

local function LuaIterator(iterable)
    if type(iterable) == "string" then return stringStep, iterable, 0 end
    if type(iterable) ~= "table" then return function() return nil end end
    if type(iterable.iterator) == "function" then return iteratorStep, iterable:iterator() end
    if type(iterable.next) == "function" then return iteratorStep, iterable end
    return ipairs(iterable)
end

local LuaMap = LuaClass()
LuaMap.name = "LuaMap"
function LuaMap.prototype.lua_constructor(self, entries)
    self._values = {}
    self._present = {}
    self._keys = {}
    self.size = 0
    if entries then
        for _, pair in LuaIterator(entries) do self:set(pair[1], pair[2]) end
    end
end
function LuaMap.prototype.clear(self)
    self._values, self._present, self._keys, self.size = {}, {}, {}, 0
end
function LuaMap.prototype.has(self, key) return self._present[key] == true end
function LuaMap.prototype.get(self, key) return self._values[key] end
function LuaMap.prototype.set(self, key, value)
    if key == nil then error("LuaMap keys cannot be nil", 0) end
    if not self:has(key) then
        self._keys[#self._keys + 1] = key
        self._present[key] = true
        self.size = self.size + 1
    end
    self._values[key] = value
    return self
end
function LuaMap.prototype.delete(self, key)
    if not self:has(key) then return false end
    self._present[key], self._values[key] = nil, nil
    for i = 1, #self._keys do
        if self._keys[i] == key then table.remove(self._keys, i); break end
    end
    self.size = self.size - 1
    return true
end
function LuaMap.prototype.keys(self)
    local values = {}
    for i = 1, #self._keys do values[i] = self._keys[i] end
    return makeIterator(values)
end
function LuaMap.prototype.values(self)
    local values = {}
    for i, key in ipairs(self._keys) do values[i] = self._values[key] end
    return makeIterator(values)
end
function LuaMap.prototype.entries(self)
    local values = {}
    for i, key in ipairs(self._keys) do values[i] = {key, self._values[key]} end
    return makeIterator(values)
end
function LuaMap.prototype.iterator(self) return self:entries() end
function LuaMap.prototype.forEach(self, callback)
    for _, key in ipairs(self._keys) do callback(nil, self._values[key], key, self) end
end

local LuaSet = LuaClass()
LuaSet.name = "LuaSet"
function LuaSet.prototype.lua_constructor(self, values)
    self._present = {}
    self._values = {}
    self.size = 0
    if values then for _, value in LuaIterator(values) do self:add(value) end end
end
function LuaSet.prototype.has(self, value) return self._present[value] == true end
function LuaSet.prototype.add(self, value)
    if value == nil then error("LuaSet values cannot be nil", 0) end
    if not self:has(value) then
        self._present[value] = true
        self._values[#self._values + 1] = value
        self.size = self.size + 1
    end
    return self
end
function LuaSet.prototype.delete(self, value)
    if not self:has(value) then return false end
    self._present[value] = nil
    for i = 1, #self._values do
        if self._values[i] == value then table.remove(self._values, i); break end
    end
    self.size = self.size - 1
    return true
end
function LuaSet.prototype.clear(self)
    self._present, self._values, self.size = {}, {}, 0
end
function LuaSet.prototype.values(self)
    local values = {}
    for i = 1, #self._values do values[i] = self._values[i] end
    return makeIterator(values)
end
function LuaSet.prototype.keys(self) return self:values() end
function LuaSet.prototype.entries(self)
    local values = {}
    for i, value in ipairs(self._values) do values[i] = {value, value} end
    return makeIterator(values)
end
function LuaSet.prototype.iterator(self) return self:values() end
function LuaSet.prototype.forEach(self, callback)
    for _, value in ipairs(self._values) do callback(nil, value, value, self) end
end

local function LuaSparseArrayNew(...)
    local result = {...}
    result.sparseLength = select("#", ...)
    return result
end

local function LuaSparseArrayPush(target, ...)
    local count = select("#", ...)
    local base = target.sparseLength or #target
    for i = 1, count do target[base + i] = select(i, ...) end
    target.sparseLength = base + count
end

local function LuaSparseArraySpread(target)
    return unpackValues(target, 1, target.sparseLength or #target)
end

local function LuaTypeOf(value)
    local valueType = type(value)
    if valueType == "table" then return "object" end
    return valueType
end

return {
    Error = Error,
    LuaArrayConcat = LuaArrayConcat,
    LuaArrayEvery = LuaArrayEvery,
    LuaArrayIsArray = LuaArrayIsArray,
    LuaArrayJoin = LuaArrayJoin,
    LuaArrayMap = LuaArrayMap,
    LuaArrayReduce = LuaArrayReduce,
    LuaArraySetLength = LuaArraySetLength,
    LuaArraySlice = LuaArraySlice,
    LuaArraySort = LuaArraySort,
    LuaClass = LuaClass,
    LuaClassExtends = LuaClassExtends,
    LuaConstruct = LuaConstruct,
    LuaDefineProperty = LuaDefineProperty,
    LuaInstanceOf = LuaInstanceOf,
    LuaIterator = LuaIterator,
    LuaMap = LuaMap,
    LuaNumber = LuaNumber,
    LuaNumberIsFinite = LuaNumberIsFinite,
    LuaNumberIsNaN = LuaNumberIsNaN,
    LuaNumberToFixed = LuaNumberToFixed,
    LuaParseInt = LuaParseInt,
    LuaSet = LuaSet,
    LuaSparseArrayNew = LuaSparseArrayNew,
    LuaSparseArrayPush = LuaSparseArrayPush,
    LuaSparseArraySpread = LuaSparseArraySpread,
    LuaStringAccess = LuaStringAccess,
    LuaStringSlice = LuaStringSlice,
    LuaStringSplit = LuaStringSplit,
    LuaStringTrim = LuaStringTrim,
    LuaTableKeys = LuaTableKeys,
    LuaTableMerge = LuaTableMerge,
    LuaTableRemove = LuaTableRemove,
    LuaTypeOf = LuaTypeOf
}
