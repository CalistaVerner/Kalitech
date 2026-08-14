package org.foxesworld.kalitech.engine.script;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.api.EngineApi;
import org.foxesworld.kalitech.engine.script.cache.ScriptCaches;
import org.foxesworld.kalitech.engine.script.jobs.ScriptJobQueue;
import org.foxesworld.kalitech.engine.script.lua.LuaHostProxy;
import org.foxesworld.kalitech.engine.script.lua.LuaExecutionLimiter;
import org.foxesworld.kalitech.engine.script.resolve.BuiltinResolver;
import org.foxesworld.kalitech.engine.script.resolve.EngineResolver;
import org.foxesworld.kalitech.engine.script.resolve.NamespaceResolver;
import org.foxesworld.kalitech.engine.script.resolve.PassThroughResolver;
import org.foxesworld.kalitech.engine.script.resolve.PathNorm;
import org.foxesworld.kalitech.engine.script.resolve.RelativeResolver;
import org.foxesworld.kalitech.engine.script.resolve.ResolverChain;
import org.foxesworld.kalitech.engine.script.lua.LuaValueRef;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.jse.JsePlatform;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-confined Lua 5.2 module runtime backed by LuaJ.
 *
 * <p>Java values cross the runtime boundary through the engine-owned
 * {@link LuaValueRef} API. LuaJ is the only script engine.</p>
 */
public final class ScriptRuntime implements Closeable {

    private static final Logger log = LogManager.getLogger(ScriptRuntime.class);
    private static final String BUILTIN_PREFIX = "@builtin/";
    private static final String MODULES_PREFIX = "@modules/";
    private static final String BUILTIN_RES_DIR = "kalitech/engine/";
    private static final String BOOTSTRAP_ID = "@builtin/init";
    private static final String JSON_MODULE_ID = "@builtin/json.lua";
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String LUA_RUNTIME_LIBRARY = """
            local KNativeType = type

            function KTypeOf(value)
                local actual = KNativeType(value)
                if actual ~= "table" then return actual end

                local metatable = getmetatable(value)
                if KNativeType(metatable) == "table"
                        and KNativeType(rawget(metatable, "__call")) == "function" then
                    return "function"
                end

                local prototype = rawget(value, "prototype")
                if KNativeType(prototype) == "table"
                        and rawget(prototype, "constructor") == value then
                    return "function"
                end
                return actual
            end

            function KArray(a, b)
                return {}
            end

            function KArrayFilled(a, b, c)
                local length, value
                if c ~= nil then length, value = b, c else length, value = a, b end
                local out = {}
                local count = math.max(0, math.floor(tonumber(length) or 0))
                for i = 1, count do out[i] = value end
                return out
            end

            function KLength(value)
                if value == nil then return 0 end
                if type(value) == "string" then return #value end
                if type(value) ~= "table" then return 0 end
                local declared = rawget(value, "length")
                if type(declared) == "number" then return declared end
                return #value
            end

            function KIndex(value, key)
                if value == nil then return nil end
                if type(key) == "number" then
                    if type(value) == "string" then
                        return string.sub(value, key + 1, key + 1)
                    end
                    return value[key + 1]
                end
                return value[key]
            end

            function KSetIndex(value, key, item)
                if type(value) ~= "table" then error("indexed value is not writable") end
                if type(key) == "number" then value[key + 1] = item
                else value[key] = item end
                return item
            end

            function KArrayClear(value)
                if type(value) ~= "table" then return end
                for key, _ in pairs(value) do
                    if type(key) == "number" then value[key] = nil end
                end
                rawset(value, "length", nil)
            end

            KArrayOps = {}

            function KArrayOps.push(array, ...)
                local count = select("#", ...)
                for i = 1, count do array[#array + 1] = select(i, ...) end
                return #array
            end

            function KArrayOps.pop(array)
                if type(array) ~= "table" or #array == 0 then return nil end
                return table.remove(array)
            end

            function KArrayOps.shift(array)
                if type(array) ~= "table" or #array == 0 then return nil end
                return table.remove(array, 1)
            end

            function KArrayOps.slice(value, first, last)
                local length = KLength(value)
                local start = math.floor(tonumber(first) or 0)
                local finish = last == nil and length or math.floor(tonumber(last) or 0)
                if start < 0 then start = math.max(length + start, 0)
                else start = math.min(start, length) end
                if finish < 0 then finish = math.max(length + finish, 0)
                else finish = math.min(finish, length) end
                if finish < start then finish = start end

                if type(value) == "string" then
                    return string.sub(value, start + 1, finish)
                end

                local out = {}
                for index = start, finish - 1 do
                    out[#out + 1] = KIndex(value, index)
                end
                return out
            end

            function KArrayOps.concat(array, ...)
                local out = KArrayOps.slice(array)
                local count = select("#", ...)
                for argIndex = 1, count do
                    local item = select(argIndex, ...)
                    if type(item) == "table" then
                        for i = 1, #item do out[#out + 1] = item[i] end
                    else
                        out[#out + 1] = item
                    end
                end
                return out
            end

            function KArrayOps.indexOf(value, needle, from)
                local start = math.max(0, math.floor(tonumber(from) or 0))
                if type(value) == "string" then
                    local found = string.find(value, tostring(needle), start + 1, true)
                    return found and found - 1 or -1
                end
                for index = start, KLength(value) - 1 do
                    if KIndex(value, index) == needle then return index end
                end
                return -1
            end

            function KArrayOps.includes(value, needle, from)
                return KArrayOps.indexOf(value, needle, from) >= 0
            end

            function KArrayOps.map(array, callback)
                local out = {}
                for i = 1, #array do
                    out[i] = callback(nil, array[i], i - 1, array)
                end
                return out
            end

            function KArrayOps.filter(array, callback)
                local out = {}
                for i = 1, #array do
                    if callback(nil, array[i], i - 1, array) then
                        out[#out + 1] = array[i]
                    end
                end
                return out
            end

            function KArrayOps.reduce(array, callback, ...)
                local supplied = select("#", ...) > 0
                local accumulator
                local first
                if supplied then
                    accumulator = select(1, ...)
                    first = 1
                else
                    if #array == 0 then error("reduce of empty array") end
                    accumulator = array[1]
                    first = 2
                end
                for i = first, #array do
                    accumulator = callback(nil, accumulator, array[i], i - 1, array)
                end
                return accumulator
            end

            function KArrayOps.every(array, callback)
                for i = 1, #array do
                    if not callback(nil, array[i], i - 1, array) then return false end
                end
                return true
            end

            function KArrayOps.some(array, callback)
                for i = 1, #array do
                    if callback(nil, array[i], i - 1, array) then return true end
                end
                return false
            end

            function KArrayOps.join(array, separator)
                local out = {}
                for i = 1, #array do
                    local value = array[i]
                    out[i] = value == nil and "" or tostring(value)
                end
                return table.concat(out, separator == nil and "," or tostring(separator))
            end

            KObject = { prototype = {} }

            function KObject.prototype.hasOwnProperty(self, key)
                return type(self) == "table" and self[key] ~= nil
            end

            function KObject.create(a, b)
                local prototype = b
                if a ~= KObject then prototype = a end
                local value = {}
                if type(prototype) == "table" then
                    setmetatable(value, { __index = prototype })
                end
                return value
            end

            function KObject.freeze(a, b)
                if a == KObject then return b end
                return a
            end

            function KObject.getPrototypeOf(a, b)
                local value = (a == KObject) and b or a
                local meta = type(value) == "table" and getmetatable(value) or nil
                return meta and meta.__index or nil
            end

            function KObject.isExtensible(a, b)
                local value = (a == KObject) and b or a
                return type(value) == "table" or type(value) == "function"
            end

            function KObject.getOwnPropertyDescriptor(a, b, c)
                local value, key
                if a == KObject then value, key = b, c else value, key = a, b end
                if type(value) ~= "table" or value[key] == nil then return nil end
                return {
                    value = value[key],
                    configurable = true,
                    enumerable = true,
                    writable = true
                }
            end

            function KObject.keys(a, b)
                local value = (a == KObject) and b or a
                local out = {}
                if type(value) ~= "table" then return out end
                for key, _ in pairs(value) do out[#out + 1] = key end
                return out
            end

            KMath = {}

            function KMath.random(a, b, c)
                local low, high
                if a == KMath then low, high = b, c else low, high = a, b end
                if low == nil then return math.random() end
                if high == nil then return math.random(low) end
                return math.random(low, high)
            end

            function KMath.hypot(...)
                local args = {...}
                local first = args[1] == KMath and 2 or 1
                local sum = 0
                for i = first, #args do
                    local value = tonumber(args[i]) or 0
                    sum = sum + value * value
                end
                return math.sqrt(sum)
            end

            function KMath.imul(a, b, c)
                local left, right
                if a == KMath then left, right = b, c else left, right = a, b end
                local product = ((tonumber(left) or 0) * (tonumber(right) or 0)) % 4294967296
                if product >= 2147483648 then product = product - 4294967296 end
                return product
            end

            KString = {}

            local function KStringArg(a, b)
                if a == KString then return b end
                return a
            end

            function KString.lastIndexOf(a, b, c, d)
                local source, needle, from
                if a == KString then source, needle, from = b, c, d
                else source, needle, from = a, b, c end
                source, needle = tostring(source or ""), tostring(needle or "")
                local limit = from == nil and #source or math.min(#source, math.floor(from) + 1)
                local result, cursor = -1, 1
                while true do
                    local found = string.find(source, needle, cursor, true)
                    if found == nil or found > limit then break end
                    result, cursor = found - 1, found + 1
                end
                return result
            end

            function KString.slashes(a, b)
                local value = KStringArg(a, b)
                return (string.gsub(tostring(value or ""), string.char(92), "/"))
            end

            function KString.stripModuleExtension(a, b)
                local source = tostring(KStringArg(a, b) or "")
                local lower = string.lower(source)
                for _, suffix in ipairs({".lua"}) do
                    if string.sub(lower, -#suffix) == suffix then
                        return string.sub(source, 1, #source - #suffix)
                    end
                end
                return source
            end

            function KString.trimLeadingSlashes(a, b)
                local source = KString.slashes(KStringArg(a, b))
                return (string.gsub(source, "^/+", ""))
            end

            function KString.trimTrailingSlashes(a, b)
                local source = KString.slashes(KStringArg(a, b))
                return (string.gsub(source, "/+$", ""))
            end

            function KString.parseSemver(a, b)
                local value = KStringArg(a, b)
                local major, minor, patch = string.match(
                    tostring(value or ""), "^(%d+)%.(%d+)%.(%d+)")
                if major == nil then return nil end
                return {
                    [0] = tonumber(major),
                    [1] = tonumber(minor),
                    [2] = tonumber(patch)
                }
            end

            function KString.beforeQuery(a, b)
                local source = tostring(KStringArg(a, b) or "")
                local at = string.find(source, "?", 1, true)
                return at and string.sub(source, 1, at - 1) or source
            end

            function KString.safeModuleChars(a, b)
                return (string.gsub(
                    tostring(KStringArg(a, b) or ""), "[^%w/_%.%-]", "_"))
            end

            function KString.collapseSlashes(a, b)
                return (string.gsub(tostring(KStringArg(a, b) or ""), "/+", "/"))
            end

            function KString.trim(a, b)
                local value = tostring(KStringArg(a, b) or "")
                return (string.gsub(value, "^%s*(.-)%s*$", "%1"))
            end

            function KString.lower(a, b)
                return string.lower(tostring(KStringArg(a, b) or ""))
            end

            KFunction = {}

            function KFunction.bind(a, b, c)
                local fn, thisArg
                if a == KFunction then fn, thisArg = b, c else fn, thisArg = a, b end
                if type(fn) ~= "function" then return fn end
                return function(_, ...)
                    return fn(thisArg, ...)
                end
            end

            function KFunction.call(a, b, c, ...)
                local fn, thisArg
                if a == KFunction then fn, thisArg = b, c
                else fn, thisArg = a, b end
                if a == KFunction then return fn(thisArg, ...)
                else return fn(thisArg, c, ...) end
            end

            function KFunction.apply(a, b, c, d)
                local fn, thisArg, args
                if a == KFunction then fn, thisArg, args = b, c, d
                else fn, thisArg, args = a, b, c end
                local values = args or {}
                local unpackValues = table.unpack or unpack
                return fn(thisArg, unpackValues(values))
            end

            function KProxy(a, b, c)
                local target, handler
                if c ~= nil then target, handler = b, c else target, handler = a, b end
                handler = handler or {}
                local proxy = {}
                local meta = {}

                meta.__index = function(_, key)
                    if type(handler.get) == "function" then
                        return handler.get(handler, target, key, proxy)
                    end
                    if type(target) == "table" then return target[key] end
                    return nil
                end

                meta.__newindex = function(_, key, value)
                    if type(handler.set) == "function" then
                        local accepted = handler.set(handler, target, key, value, proxy)
                        if accepted == false then error("Lua proxy rejected assignment: " .. tostring(key)) end
                        return
                    end
                    if type(target) == "table" then
                        target[key] = value
                        return
                    end
                    error("Lua proxy target is not writable")
                end

                if type(handler.apply) == "function" or KTypeOf(target) == "function" then
                    meta.__call = function(_, thisArg, ...)
                        local args = {...}
                        if type(handler.apply) == "function" then
                            return handler.apply(handler, target, thisArg, args)
                        end
                        return target(thisArg, ...)
                    end
                end

                meta.__pairs = function()
                    if type(handler.ownKeys) == "function" then
                        local keys = handler.ownKeys(handler, target) or {}
                        local index = 0
                        return function()
                            index = index + 1
                            local key = keys[index]
                            if key == nil then return nil end
                            return key, proxy[key]
                        end, proxy, nil
                    end
                    if type(target) == "table" then return pairs(target) end
                    return next, {}, nil
                end

                meta.__len = function()
                    return type(target) == "table" and #target or 0
                end

                return setmetatable(proxy, meta)
            end
            """;

    private final Globals globals;
    private final LuaValue jsonModule = LuaHostProxy.wrap(new LuaJsonCodec());
    private final ScriptCaches caches;
    private final ScriptJobQueue jobs = new ScriptJobQueue();
    private final ResolverChain resolver;
    private final ThreadLocal<ArrayDeque<String>> requireStack =
            ThreadLocal.withInitial(ArrayDeque::new);
    private final Map<String, ModuleRecord> moduleCache = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> moduleVersions = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> forwardDeps = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> reverseDeps = new ConcurrentHashMap<>();
    private final Map<String, MethodHandle> hostHandles = new ConcurrentHashMap<>();

    private volatile ModuleStreamProvider streamLoader;
    private volatile EngineApi lastAttachedEngineApi;
    private volatile boolean builtinsInitialized;
    private volatile boolean closing;
    private volatile Thread ownerThread;
    private long invalidateEpoch;

    public ScriptRuntime() {
        this(ScriptCaches.defaults());
    }

    public ScriptRuntime(ScriptCaches caches) {
        this.caches = Objects.requireNonNull(caches, "caches");
        this.resolver = new ResolverChain()
                .add(new BuiltinResolver(BUILTIN_PREFIX))
                .add(new BuiltinResolver(MODULES_PREFIX))
                .add(new EngineResolver("@module", "@builtin/modules"))
                .add(new RelativeResolver())
                .add(new NamespaceResolver("Mods"))
                .add(new PassThroughResolver());

        this.globals = JsePlatform.standardGlobals();
        this.globals.load(new LuaExecutionLimiter());
        restrictGlobals();
        installRuntimeGlobals();
        this.streamLoader = wrapWithBuiltIns(null);
        assertOwnerThread();
    }

    private void restrictGlobals() {
        for (String name : List.of(
                "luajava", "io", "os", "package", "debug", "coroutine",
                "dofile", "loadfile", "load")) {
            globals.set(name, LuaValue.NIL);
        }
        globals.load(LUA_RUNTIME_LIBRARY, "@kalitech/lua-runtime").call();
    }

    private void installRuntimeGlobals() {
        VarArgFunction require = new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                String request = requireArgument(args);
                String parent = requireStack.get().peek();
                return requireFrom(parent == null ? "" : parent, request).asLuaValue();
            }
        };
        globals.set("require", require);
    }

    private static String requireArgument(Varargs args) {
        LuaValue value = args.arg1();
        if (!value.isstring()) throw new LuaError("require expects a module id");
        return value.tojstring();
    }

    public Globals globals() {
        return globals;
    }

    public ModuleStreamProvider moduleStreamProvider() {
        return streamLoader;
    }

    private void assertOwnerThread() {
        Thread current = Thread.currentThread();
        Thread owner = ownerThread;
        if (owner == null) {
            ownerThread = current;
        } else if (owner != current) {
            throw new IllegalStateException(
                    "ScriptRuntime is thread confined. Owner=" + owner.getName()
                            + ", current=" + current.getName());
        }
    }

    public ScriptRuntime attachEngineApi(EngineApi api) {
        assertOwnerThread();
        this.lastAttachedEngineApi = Objects.requireNonNull(api, "api");
        return this;
    }

    public void initBuiltIns(EngineApi api) {
        assertOwnerThread();
        if (builtinsInitialized) return;
        this.lastAttachedEngineApi = Objects.requireNonNull(api, "api");
        builtinsInitialized = true;
        try (LuaExecutionLimiter.Scope ignored =
                     LuaExecutionLimiter.enterModule("builtins")) {
            LuaValueRef bootstrap = require(BOOTSTRAP_ID);
            if (bootstrap != null && bootstrap.hasMember("attachEngine")) {
                bootstrap.invokeMember("attachEngine", api);
            }
            log.info("[lua] builtins initialized from {}", BOOTSTRAP_ID);
        } catch (RuntimeException | Error failure) {
            builtinsInitialized = false;
            throw failure;
        }
    }

    private void ensureBuiltInsBeforeUserScripts(String request) {
        if (builtinsInitialized || request == null || request.startsWith(BUILTIN_PREFIX)) return;
        EngineApi api = lastAttachedEngineApi;
        if (api == null) {
            throw new IllegalStateException(
                    "Lua builtins are not initialized and EngineApi is not attached");
        }
        initBuiltIns(api);
    }

    public ScriptRuntime setModuleStreamProvider(ModuleStreamProvider loader) {
        this.streamLoader = wrapWithBuiltIns(loader);
        return this;
    }

    public ScriptJobQueue jobs() {
        return jobs;
    }

    public ScriptRuntime registerHostHandle(String name, MethodHandle handle) {
        hostHandles.put(Objects.requireNonNull(name, "name"), Objects.requireNonNull(handle, "handle"));
        return this;
    }

    public MethodHandle hostHandle(String name) {
        return hostHandles.get(name);
    }

    public LuaValueRef require(String moduleId) {
        assertOwnerThread();
        return requireFrom("", moduleId);
    }

    public long moduleVersion(String moduleId) {
        String id = PathNorm.normalizeId(moduleId);
        AtomicLong direct = moduleVersions.get(id);
        if (direct != null) return direct.get();
        long result = 0L;
        for (String candidate : PathNorm.expandCandidates(id)) {
            AtomicLong value = moduleVersions.get(candidate);
            if (value != null) result = Math.max(result, value.get());
        }
        return result;
    }

    private LuaValueRef requireFrom(String parentModuleId, String requestRaw) {
        assertOwnerThread();
        String request = requestRaw == null ? "" : requestRaw.trim();
        ensureBuiltInsBeforeUserScripts(request);

        String parent = PathNorm.normalizeId(parentModuleId);
        String baseId = resolveToModuleId(parent, request);
        List<String> candidates = PathNorm.expandCandidates(baseId);
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("Blank Lua module request from '" + parent + "'");
        }

        for (String id : candidates) {
            ModuleRecord existing = moduleCache.get(id);
            if (existing == null) continue;
            recordDependency(parent, id);
            if (existing.state == ModuleState.LOADING || existing.state == ModuleState.LOADED) {
                return LuaValueRef.of(existing.moduleValue);
            }
            if (existing.state == ModuleState.FAILED && existing.failedAtEpoch == invalidateEpoch) {
                throw moduleFailure(existing.id, existing.lastError);
            }
        }

        for (String id : candidates) {
            LuaValueRef nativeValue = loadNativeModule(parent, id);
            if (nativeValue != null) return nativeValue;
        }

        ModuleStreamProvider provider = streamLoader;
        if (provider == null) {
            throw new IllegalStateException("No Lua ModuleStreamProvider is configured");
        }

        Throwable lastFailure = null;
        for (String id : candidates) {
            try {
                String code = caches.moduleText().getIfPresent(id);
                if (code == null) {
                    try (InputStream stream = provider.openStream(id)) {
                        if (stream == null) continue;
                        code = readUtf8(stream);
                        caches.moduleText().put(id, code);
                    }
                }
                recordDependency(parent, id);
                return evaluateModule(id, code);
            } catch (IOException failure) {
                lastFailure = failure;
                break;
            } catch (RuntimeException failure) {
                lastFailure = failure;
                break;
            } catch (Exception failure) {
                lastFailure = failure;
                break;
            }
        }

        String message = "Lua module not found. request='" + request + "', parent='"
                + parent + "', candidates=" + candidates;
        if (lastFailure != null) throw new IllegalStateException(message, lastFailure);
        throw new IllegalStateException(message);
    }

    private LuaValueRef loadNativeModule(String parent, String id) {
        if (!JSON_MODULE_ID.equals(id)) return null;

        ModuleRecord record = new ModuleRecord(id);
        record.moduleValue = jsonModule;
        record.state = ModuleState.LOADED;
        moduleCache.put(id, record);
        recordDependency(parent, id);
        return LuaValueRef.of(jsonModule);
    }

    private LuaValueRef evaluateModule(String id, String code) {
        ModuleRecord record = new ModuleRecord(id);
        moduleCache.put(id, record);
        record.state = ModuleState.LOADING;
        requireStack.get().push(id);

        try {
            LuaValue moduleValue;
            if (id.endsWith(".json")) {
                Object decoded = JSON.readValue(code, Object.class);
                moduleValue = LuaHostProxy.wrap(decoded);
            } else {
                LuaTable environment = moduleEnvironment(record);
                LuaValue chunk = globals.load(code, "@" + id, environment);
                try (LuaExecutionLimiter.Scope ignored =
                             LuaExecutionLimiter.enterModule("module:" + id)) {
                    moduleValue = chunk.call();
                }
                if (moduleValue.isnil()) {
                    throw new IllegalStateException("Lua module must return its public value: " + id);
                }
            }

            record.moduleValue = moduleValue;
            record.state = ModuleState.LOADED;
            return LuaValueRef.of(moduleValue);
        } catch (Throwable failure) {
            record.state = ModuleState.FAILED;
            record.failedAtEpoch = invalidateEpoch;
            record.lastError = failure;
            throw moduleFailure(id, failure);
        } finally {
            ArrayDeque<String> stack = requireStack.get();
            if (!stack.isEmpty()) stack.pop();
        }
    }


    private LuaTable moduleEnvironment(ModuleRecord record) {
        LuaTable environment = new LuaTable();
        LuaTable metatable = new LuaTable();
        metatable.set("__index", globals);
        environment.setmetatable(metatable);
        environment.set("_G", globals);

        VarArgFunction scopedRequire = new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return requireFrom(record.id, requireArgument(args)).asLuaValue();
            }
        };
        environment.set("require", scopedRequire);
        return environment;
    }

    private static RuntimeException moduleFailure(String id, Throwable failure) {
        String detail = failure == null || failure.getMessage() == null
                ? String.valueOf(failure)
                : failure.getMessage();
        return new IllegalStateException("Lua module failed: " + id + ": " + detail, failure);
    }

    private void recordDependency(String parent, String child) {
        if (parent == null || parent.isBlank() || parent.equals(child)) return;
        forwardDeps.computeIfAbsent(parent, ignored -> ConcurrentHashMap.newKeySet()).add(child);
        reverseDeps.computeIfAbsent(child, ignored -> ConcurrentHashMap.newKeySet()).add(parent);
    }

    private String resolveToModuleId(String parent, String request) {
        String normalizedRequest = request == null ? "" : request.trim();

        return resolver.resolveOrThrow(PathNorm.normalizeId(parent), normalizedRequest);
    }

    private ModuleStreamProvider wrapWithBuiltIns(ModuleStreamProvider downstream) {
        ClassLoader loader = ScriptRuntime.class.getClassLoader();
        return id -> {
            String normalized = PathNorm.normalizeId(id);
            if (normalized.startsWith(BUILTIN_PREFIX)) {
                String relative = normalized.substring(BUILTIN_PREFIX.length());
                if (!PathNorm.hasExtension(relative)) relative += ".lua";
                return loader.getResourceAsStream(BUILTIN_RES_DIR + relative);
            }
            return downstream == null ? null : downstream.openStream(normalized);
        };
    }

    private static String readUtf8(InputStream stream) throws IOException {
        return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }

    public boolean invalidate(String moduleId) {
        return invalidateWithReason(moduleId, "invalidate");
    }

    public boolean invalidateWithReason(String moduleId, String reason) {
        assertOwnerThread();
        invalidateEpoch++;
        Set<String> visited = new HashSet<>();
        int removed = 0;
        for (String id : canonicalIds(moduleId)) {
            if (!id.startsWith(BUILTIN_PREFIX)) removed += removeModuleAndDependents(id, visited);
        }
        if (removed > 0) log.debug("[lua] invalidated {} module(s): {}", removed, reason);
        return removed > 0;
    }

    public int invalidateMany(Collection<String> moduleIds) {
        return invalidateManyWithReason(moduleIds, "invalidateMany");
    }

    public int invalidateManyWithReason(Collection<String> moduleIds, String reason) {
        assertOwnerThread();
        invalidateEpoch++;
        if (moduleIds == null || moduleIds.isEmpty()) return 0;
        Set<String> visited = new HashSet<>();
        int removed = 0;
        for (String moduleId : moduleIds) {
            for (String id : canonicalIds(moduleId)) {
                if (!id.startsWith(BUILTIN_PREFIX)) {
                    removed += removeModuleAndDependents(id, visited);
                }
            }
        }
        if (removed > 0) log.debug("[lua] invalidated {} module(s): {}", removed, reason);
        return removed;
    }

    public int invalidateAllWithReason(String reason) {
        assertOwnerThread();
        invalidateEpoch++;
        int removed = 0;
        for (String id : new HashSet<>(moduleCache.keySet())) {
            if (id.startsWith(BUILTIN_PREFIX)) continue;
            if (moduleCache.remove(id) != null) {
                caches.invalidateModule(id);
                bumpVersion(id);
                removed++;
            }
        }
        forwardDeps.clear();
        reverseDeps.clear();
        if (removed > 0) log.info("[lua] invalidated all user modules ({}): {}", removed, reason);
        return removed;
    }

    public int invalidateAll() {
        return invalidateAllWithReason("invalidateAll");
    }

    public int invalidatePrefix(String prefix) {
        return invalidatePrefixWithReason(prefix, "invalidatePrefix");
    }

    public int invalidatePrefixWithReason(String prefix, String reason) {
        assertOwnerThread();
        invalidateEpoch++;
        String canonicalPrefix = PathNorm.normalizeId(prefix);
        if (canonicalPrefix.isBlank() || canonicalPrefix.startsWith(BUILTIN_PREFIX)) return 0;
        Set<String> visited = new HashSet<>();
        int removed = 0;
        Set<String> ids = new HashSet<>(moduleCache.keySet());
        ids.addAll(forwardDeps.keySet());
        ids.addAll(reverseDeps.keySet());
        for (String id : ids) {
            if (id.startsWith(canonicalPrefix)) {
                removed += removeModuleAndDependents(id, visited);
            }
        }
        if (removed > 0) log.debug("[lua] invalidated prefix {} ({}): {}", canonicalPrefix, removed, reason);
        return removed;
    }

    private List<String> canonicalIds(String moduleId) {
        String id = PathNorm.normalizeId(moduleId);
        if (id.isBlank()) return List.of();
        if (PathNorm.hasExtension(id)) return List.of(id);
        return new ArrayList<>(PathNorm.expandCandidates(id));
    }

    private int removeModuleAndDependents(String id, Set<String> visited) {
        if (!visited.add(id)) return 0;
        int removed = 0;
        if (moduleCache.remove(id) != null) {
            caches.invalidateModule(id);
            bumpVersion(id);
            removed++;
        }

        Set<String> dependents = reverseDeps.remove(id);
        if (dependents != null) {
            for (String dependent : new HashSet<>(dependents)) {
                removed += removeModuleAndDependents(dependent, visited);
            }
        }

        Set<String> dependencies = forwardDeps.remove(id);
        if (dependencies != null) {
            for (String dependency : dependencies) {
                Set<String> reverse = reverseDeps.get(dependency);
                if (reverse != null) {
                    reverse.remove(id);
                    if (reverse.isEmpty()) reverseDeps.remove(dependency);
                }
            }
        }
        return removed;
    }

    private void bumpVersion(String id) {
        moduleVersions.computeIfAbsent(id, ignored -> new AtomicLong()).incrementAndGet();
    }

    public void reset() {
        assertOwnerThread();
        invalidateAllWithReason("reset");
        jobs.clear();
        caches.invalidateAll();
        builtinsInitialized = false;
    }

    @Override
    public void close() {
        if (closing) return;
        closing = true;
        reset();
        log.info("Closed Lua ScriptRuntime");
    }

    @FunctionalInterface
    public interface ModuleStreamProvider {
        InputStream openStream(String moduleId) throws Exception;
    }

    private enum ModuleState {
        NEW,
        LOADING,
        LOADED,
        FAILED
    }

    private static final class ModuleRecord {
        final String id;
        LuaValue moduleValue = new LuaTable();
        ModuleState state = ModuleState.NEW;
        long failedAtEpoch = -1L;
        Throwable lastError;

        ModuleRecord(String id) {
            this.id = id;
        }
    }
}
