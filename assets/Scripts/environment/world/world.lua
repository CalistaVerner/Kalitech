local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local LuaClass = luaRuntime.LuaClass
local LuaConstruct = luaRuntime.LuaConstruct
Index = LuaClass()
Index.name = "Index"
function Index.prototype.lua_constructor(self)
    self.KEY_GROUND = "scene:ground"
    self.KEY_GROUND_PHYS = "scene:ground:phys"
end
function Index.prototype.init(self, ctx)
    ENGINE.log:info("[scene] init")
    local size = 513
    local xz = 2
    local half = (size - 1) * xz * 0.5
    ENGINE.sceneTerrain = ENGINE.terrain:create({
        name = "proc",
        kind = "heights",
        heights = ENGINE.terrain.heights:perlin({
            size = size,
            seed = 1337,
            scale = 480,
            octaves = 12,
            warp = {amp = 18, scale = 42, octaves = 3}
        }),
        terrain = {size = size, patchSize = 65},
        scale = {xz = xz, y = 60},
        pos = {x = -half, y = 0, z = -half},
        material = ENGINE.material:getMaterial("unshaded.sand"),
        uv = {scale = {50, 50}},
        attach = true,
        physics = {mass = 0, friction = 1}
    })
    ENGINE.events:on(
        "engine.physics.body.added",
        function(lua_, i)
            local x = i.pos.x
            local y = i.pos.y
            local z = i.pos.z
            ENGINE.log:debug(((((("Spawned at (x=" .. tostring(x)) .. ",y=") .. tostring(y)) .. "z=") .. tostring(z)) .. ")")
        end
    )
end
function Index.prototype.destroy(self, ctx)
    local st = ctx:state()
    local phys = st:get(self.KEY_GROUND_PHYS)
    if phys then
        do
            local function lua_catch(e)
                ENGINE.log:error("[scene] failed to remove ground body", e)
            end
            local lua_try, lua_hasReturned = pcall(function()
                ENGINE.physics:remove(phys)
            end)
            if not lua_try then
                lua_catch(lua_hasReturned)
            end
        end
    end
    st:remove(self.KEY_GROUND_PHYS)
    local ground = st:get(self.KEY_GROUND)
    if ground then
        do
            local function lua_catch(e)
                ENGINE.log:error("[scene] failed to destroy ground surface", e)
            end
            local lua_try, lua_hasReturned = pcall(function()
                ctx:engine():surface():destroy(ground)
            end)
            if not lua_try then
                lua_catch(lua_hasReturned)
            end
        end
    end
    st:remove(self.KEY_GROUND)
end
M = LuaConstruct(Index)

return M
