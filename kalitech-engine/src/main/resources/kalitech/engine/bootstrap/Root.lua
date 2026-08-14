local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local Classes = luaRuntime.class
ROOT_KEY = "__kalitech"
function getRoot(self)
    if not _G[ROOT_KEY] then
        _G[ROOT_KEY] = KObject:create(nil)
    end
    return _G[ROOT_KEY]
end
function ensureRootState(self, K)
    if not K.modules then
        K.modules = KObject:create(nil)
    end
    if not K.instances then
        K.instances = KObject:create(nil)
    end
    if not K.meta then
        K.meta = KObject:create(nil)
    end
    if not K.instancesMeta then
        K.instancesMeta = KObject:create(nil)
    end
    if not K.moduleIds then
        K.moduleIds = KObject:create(nil)
    end
    if not K._engine then
        K._engine = nil
    end
    if not K._engineAttached then
        K._engineAttached = false
    end
    if not K._deferred then
        K._deferred = {}
    end
    if not K._once then
        K._once = KObject:create(nil)
    end
    if not K.config then
        K.config = KObject:create(nil)
    end
    if not K.dataConfig then
        K.dataConfig = KObject:create(nil)
    end
    if not K.dataConfigApi then
        K.dataConfigApi = nil
    end
    if not K.controllers then
        K.controllers = KObject:create(nil)
    end
    if not K.controllersApi then
        K.controllersApi = nil
    end
    if not K.controllersRegs then
        K.controllersRegs = {}
    end
    return K
end
local BootstrapRootApi = Classes:create()
BootstrapRootApi.name = "BootstrapRootApi"
BootstrapRootApi.prototype.getRoot = getRoot
BootstrapRootApi.prototype.ensureRootState = ensureRootState
return Classes:construct(BootstrapRootApi)
