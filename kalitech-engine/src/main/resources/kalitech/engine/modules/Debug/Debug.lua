local M = {}
local json = require("@builtin/json")
local luaRuntime = require("@builtin/lua_runtime")
local LuaNumber = luaRuntime.LuaNumber
local LuaArrayIsArray = luaRuntime.LuaArrayIsArray
local LuaSparseArrayNew = luaRuntime.LuaSparseArrayNew
local LuaSparseArrayPush = luaRuntime.LuaSparseArrayPush
local LuaSparseArraySpread = luaRuntime.LuaSparseArraySpread
local LuaStringTrim = luaRuntime.LuaStringTrim
local LuaTableRemove = luaRuntime.LuaTableRemove
local Error = luaRuntime.Error
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
function isObj(self, v)
    return not not v and KTypeOf(v) == "table"
end
function clamp01(self, x)
    x = LuaNumber(x)
    if not (x == x) then
        return 0
    end
    if x < 0 then
        return 0
    end
    if x > 1 then
        return 1
    end
    return x
end
function v3(self, x, y, z, dx, dy, dz)
    if x == nil then
        return {dx or 0, dy or 0, dz or 0}
    end
    if LuaArrayIsArray(x) then
        local lua_x__1_0 = x[1]
        if lua_x__1_0 == nil then
            lua_x__1_0 = dx or 0
        end
        local lua_temp_3 = LuaNumber(lua_x__1_0) or 0
        local lua_x__2_1 = x[2]
        if lua_x__2_1 == nil then
            lua_x__2_1 = dy or 0
        end
        local lua_temp_4 = LuaNumber(lua_x__2_1) or 0
        local lua_x__3_2 = x[3]
        if lua_x__3_2 == nil then
            lua_x__3_2 = dz or 0
        end
        return {
            lua_temp_3,
            lua_temp_4,
            LuaNumber(lua_x__3_2) or 0
        }
    end
    if isObj(_G, x) and KTypeOf(x.x) == "number" then
        return {
            LuaNumber(x.x) or 0,
            LuaNumber(x.y) or 0,
            LuaNumber(x.z) or 0
        }
    end
    local lua_x_5 = x
    if lua_x_5 == nil then
        lua_x_5 = dx or 0
    end
    local lua_temp_8 = LuaNumber(lua_x_5) or 0
    local lua_y_6 = y
    if lua_y_6 == nil then
        lua_y_6 = dy or 0
    end
    local lua_temp_9 = LuaNumber(lua_y_6) or 0
    local lua_z_7 = z
    if lua_z_7 == nil then
        lua_z_7 = dz or 0
    end
    return {
        lua_temp_8,
        lua_temp_9,
        LuaNumber(lua_z_7) or 0
    }
end
function rgba(self, r, g, b, a, dr, dg, db, da)
    if r == nil then
        local lua_dr_10 = dr
        if lua_dr_10 == nil then
            lua_dr_10 = 1
        end
        local lua_dg_11 = dg
        if lua_dg_11 == nil then
            lua_dg_11 = 1
        end
        local lua_db_12 = db
        if lua_db_12 == nil then
            lua_db_12 = 1
        end
        local lua_da_13 = da
        if lua_da_13 == nil then
            lua_da_13 = 1
        end
        return {lua_dr_10, lua_dg_11, lua_db_12, lua_da_13}
    end
    if LuaArrayIsArray(r) then
        local lua_clamp01_17 = clamp01
        local lua_G_16 = _G
        local lua_r__1_15 = r[1]
        if lua_r__1_15 == nil then
            local lua_dr_14 = dr
            if lua_dr_14 == nil then
                lua_dr_14 = 1
            end
            lua_r__1_15 = lua_dr_14
        end
        local lua_array_30 = LuaSparseArrayNew(lua_clamp01_17(lua_G_16, lua_r__1_15))
        local lua_clamp01_21 = clamp01
        local lua_G_20 = _G
        local lua_r__2_19 = r[2]
        if lua_r__2_19 == nil then
            local lua_dg_18 = dg
            if lua_dg_18 == nil then
                lua_dg_18 = 1
            end
            lua_r__2_19 = lua_dg_18
        end
        LuaSparseArrayPush(
            lua_array_30,
            lua_clamp01_21(lua_G_20, lua_r__2_19)
        )
        local lua_clamp01_25 = clamp01
        local lua_G_24 = _G
        local lua_r__3_23 = r[3]
        if lua_r__3_23 == nil then
            local lua_db_22 = db
            if lua_db_22 == nil then
                lua_db_22 = 1
            end
            lua_r__3_23 = lua_db_22
        end
        LuaSparseArrayPush(
            lua_array_30,
            lua_clamp01_25(lua_G_24, lua_r__3_23)
        )
        local lua_clamp01_29 = clamp01
        local lua_G_28 = _G
        local lua_r__4_27 = r[4]
        if lua_r__4_27 == nil then
            local lua_da_26 = da
            if lua_da_26 == nil then
                lua_da_26 = 1
            end
            lua_r__4_27 = lua_da_26
        end
        LuaSparseArrayPush(
            lua_array_30,
            lua_clamp01_29(lua_G_28, lua_r__4_27)
        )
        return {LuaSparseArraySpread(lua_array_30)}
    end
    if isObj(_G, r) and (KTypeOf(r.r) == "number" or KTypeOf(r.g) == "number" or KTypeOf(r.b) == "number") then
        local lua_clamp01_34 = clamp01
        local lua_G_33 = _G
        local lua_r_r_32 = r.r
        if lua_r_r_32 == nil then
            local lua_dr_31 = dr
            if lua_dr_31 == nil then
                lua_dr_31 = 1
            end
            lua_r_r_32 = lua_dr_31
        end
        local lua_array_47 = LuaSparseArrayNew(lua_clamp01_34(lua_G_33, lua_r_r_32))
        local lua_clamp01_38 = clamp01
        local lua_G_37 = _G
        local lua_r_g_36 = r.g
        if lua_r_g_36 == nil then
            local lua_dg_35 = dg
            if lua_dg_35 == nil then
                lua_dg_35 = 1
            end
            lua_r_g_36 = lua_dg_35
        end
        LuaSparseArrayPush(
            lua_array_47,
            lua_clamp01_38(lua_G_37, lua_r_g_36)
        )
        local lua_clamp01_42 = clamp01
        local lua_G_41 = _G
        local lua_r_b_40 = r.b
        if lua_r_b_40 == nil then
            local lua_db_39 = db
            if lua_db_39 == nil then
                lua_db_39 = 1
            end
            lua_r_b_40 = lua_db_39
        end
        LuaSparseArrayPush(
            lua_array_47,
            lua_clamp01_42(lua_G_41, lua_r_b_40)
        )
        local lua_clamp01_46 = clamp01
        local lua_G_45 = _G
        local lua_r_a_44 = r.a
        if lua_r_a_44 == nil then
            local lua_da_43 = da
            if lua_da_43 == nil then
                lua_da_43 = 1
            end
            lua_r_a_44 = lua_da_43
        end
        LuaSparseArrayPush(
            lua_array_47,
            lua_clamp01_46(lua_G_45, lua_r_a_44)
        )
        return {LuaSparseArraySpread(lua_array_47)}
    end
    local lua_clamp01_51 = clamp01
    local lua_G_50 = _G
    local lua_r_49 = r
    if lua_r_49 == nil then
        local lua_dr_48 = dr
        if lua_dr_48 == nil then
            lua_dr_48 = 1
        end
        lua_r_49 = lua_dr_48
    end
    local lua_array_64 = LuaSparseArrayNew(lua_clamp01_51(lua_G_50, lua_r_49))
    local lua_clamp01_55 = clamp01
    local lua_G_54 = _G
    local lua_g_53 = g
    if lua_g_53 == nil then
        local lua_dg_52 = dg
        if lua_dg_52 == nil then
            lua_dg_52 = 1
        end
        lua_g_53 = lua_dg_52
    end
    LuaSparseArrayPush(
        lua_array_64,
        lua_clamp01_55(lua_G_54, lua_g_53)
    )
    local lua_clamp01_59 = clamp01
    local lua_G_58 = _G
    local lua_b_57 = b
    if lua_b_57 == nil then
        local lua_db_56 = db
        if lua_db_56 == nil then
            lua_db_56 = 1
        end
        lua_b_57 = lua_db_56
    end
    LuaSparseArrayPush(
        lua_array_64,
        lua_clamp01_59(lua_G_58, lua_b_57)
    )
    local lua_clamp01_63 = clamp01
    local lua_G_62 = _G
    local lua_a_61 = a
    if lua_a_61 == nil then
        local lua_da_60 = da
        if lua_da_60 == nil then
            lua_da_60 = 1
        end
        lua_a_61 = lua_da_60
    end
    LuaSparseArrayPush(
        lua_array_64,
        lua_clamp01_63(lua_G_62, lua_a_61)
    )
    return {LuaSparseArraySpread(lua_array_64)}
end
function makePrefix(self, scope)
    local s = LuaStringTrim(tostring(scope or ""))
    local lua_s_65
    if s then
        lua_s_65 = ("[" .. s) .. "] "
    else
        lua_s_65 = ""
    end
    return lua_s_65
end
function makeApi(self, engine)
    local lua_temp_66
    if engine and engine.debug and KTypeOf(engine.debug) == "function" then
        lua_temp_66 = engine:debug()
    else
        lua_temp_66 = nil
    end
    local dbg = lua_temp_66
    local function has(self, fn)
        return not not (dbg and KTypeOf(dbg[fn]) == "function")
    end
    local function call(self, fn, cfgOrArg)
        if not dbg then
            return nil
        end
        do
            local function lua_catch(_)
                return true, nil
            end
            local lua_try, lua_hasReturned, lua_returnValue = pcall(function()
                if not has(_G, fn) then
                    return true, nil
                end
                return true, dbg[fn](dbg, cfgOrArg)
            end)
            if not lua_try then
                lua_hasReturned, lua_returnValue = lua_catch(lua_hasReturned)
            end
            if lua_hasReturned then
                return lua_returnValue
            end
        end
    end
    local function call0(self, fn)
        if not dbg then
            return nil
        end
        do
            local function lua_catch(_)
                return true, nil
            end
            local lua_try, lua_hasReturned, lua_returnValue = pcall(function()
                if not has(_G, fn) then
                    return true, nil
                end
                return true, dbg[fn](dbg)
            end)
            if not lua_try then
                lua_hasReturned, lua_returnValue = lua_catch(lua_hasReturned)
            end
            if lua_hasReturned then
                return lua_returnValue
            end
        end
    end
    local function callN(self, fn, ...)
        local args = {...}
        if not dbg then
            return nil
        end
        do
            local function lua_catch(_)
                return true, nil
            end
            local lua_try, lua_hasReturned, lua_returnValue = pcall(function()
                if not has(_G, fn) then
                    return true, nil
                end
                return true, KFunction:apply(dbg[fn], dbg, args)
            end)
            if not lua_try then
                lua_hasReturned, lua_returnValue = lua_catch(lua_hasReturned)
            end
            if lua_hasReturned then
                return lua_returnValue
            end
        end
    end
    local function makeState(self, scopeName, parentState)
        local api
        local scope = LuaStringTrim(tostring(scopeName or ""))
        local prefix = makePrefix(_G, scope)
        local lua_temp_67
        if parentState and parentState.ttl ~= nil then
            lua_temp_67 = parentState.ttl
        else
            lua_temp_67 = 0.15
        end
        local lua_temp_68
        if parentState and parentState.depthTest ~= nil then
            lua_temp_68 = parentState.depthTest
        else
            lua_temp_68 = true
        end
        local lua_temp_69
        if parentState and parentState.alpha ~= nil then
            lua_temp_69 = parentState.alpha
        else
            lua_temp_69 = 1
        end
        local lua_temp_70
        if parentState and parentState.depthWrite ~= nil then
            lua_temp_70 = parentState.depthWrite
        else
            lua_temp_70 = nil
        end
        local S = {ttl = lua_temp_67, depthTest = lua_temp_68, alpha = lua_temp_69, depthWrite = lua_temp_70}
        local function withCommon(self, cfg, ttl, depthTest, alpha, depthWrite)
            local out = cfg or ({})
            local lua_temp_71
            if ttl ~= nil then
                lua_temp_71 = LuaNumber(ttl)
            else
                lua_temp_71 = LuaNumber(S.ttl)
            end
            local t = lua_temp_71
            local lua_temp_72
            if t > 0 then
                lua_temp_72 = t
            else
                lua_temp_72 = 0
            end
            out.ttl = lua_temp_72
            local lua_temp_73
            if depthTest ~= nil then
                lua_temp_73 = not not depthTest
            else
                lua_temp_73 = not not S.depthTest
            end
            out.depthTest = lua_temp_73
            local lua_temp_74
            if alpha ~= nil then
                lua_temp_74 = LuaNumber(alpha)
            else
                lua_temp_74 = LuaNumber(S.alpha)
            end
            local a = lua_temp_74
            if a == a then
                out._alpha = a
            end
            if depthWrite ~= nil then
                out.depthWrite = not not depthWrite
            elseif S.depthWrite ~= nil then
                out.depthWrite = not not S.depthWrite
            end
            return out
        end
        local function enabled(self, v)
            if v == nil then
                return not not call0(_G, "enabled")
            end
            callN(_G, "enabled", not not v)
            return not not v
        end
        local function clear(self)
            call0(_G, "clear")
        end
        local function tick(self, tpf)
            callN(
                _G,
                "tick",
                LuaNumber(tpf) or 0
            )
        end
        local function setTTL(self, sec)
            S.ttl = LuaNumber(sec)
            if not (S.ttl == S.ttl) then
                S.ttl = 0
            end
            return api
        end
        local function setDepth(self, v)
            S.depthTest = not not v
            return api
        end
        local function setAlpha(self, a)
            S.alpha = LuaNumber(a)
            if not (S.alpha == S.alpha) then
                S.alpha = 1
            end
            return api
        end
        local function setDepthWrite(self, v)
            local lua_temp_75
            if v == nil then
                lua_temp_75 = nil
            else
                lua_temp_75 = not not v
            end
            S.depthWrite = lua_temp_75
            return api
        end
        local function line(self, a, b, color, ttl, depthTest, alpha)
            local cfg = withCommon(
                _G,
                {
                    a = v3(
                        _G,
                        a,
                        nil,
                        nil,
                        nil,
                        0,
                        0,
                        0
                    ),
                    b = v3(
                        _G,
                        b,
                        nil,
                        nil,
                        nil,
                        0,
                        1,
                        0
                    )
                },
                ttl,
                depthTest,
                alpha
            )
            local c = rgba(
                _G,
                color,
                nil,
                nil,
                nil,
                1,
                1,
                0,
                1
            )
            local lua_temp_76
            if cfg._alpha ~= nil then
                lua_temp_76 = cfg._alpha
            else
                lua_temp_76 = c[4]
            end
            local aa = lua_temp_76
            cfg.color = {
                c[1],
                c[2],
                c[3],
                clamp01(_G, aa)
            }
            if KTypeOf(color) == "string" then
                local lua_clamp01_79 = clamp01
                local lua_G_78 = _G
                local lua_cfg__alpha_77 = cfg._alpha
                if lua_cfg__alpha_77 == nil then
                    lua_cfg__alpha_77 = 1
                end
                cfg.color = {
                    1,
                    1,
                    0,
                    lua_clamp01_79(lua_G_78, lua_cfg__alpha_77)
                }
            end
            LuaTableRemove(cfg, "_alpha")
            call(_G, "line", cfg)
            return api
        end
        local function ray(self, origin, dir, len, color, ttl, depthTest, alpha, arrow, arrowSize)
            local lua_withCommon_85 = withCommon
            local lua_G_84 = _G
            local lua_v3_result_82 = v3(
                _G,
                origin,
                nil,
                nil,
                nil,
                0,
                0,
                0
            )
            local lua_v3_result_83 = v3(
                _G,
                dir,
                nil,
                nil,
                nil,
                0,
                1,
                0
            )
            local lua_temp_80
            if len ~= nil then
                lua_temp_80 = LuaNumber(len)
            else
                lua_temp_80 = 1
            end
            local lua_temp_81
            if arrow ~= nil then
                lua_temp_81 = not not arrow
            else
                lua_temp_81 = true
            end
            local cfg = lua_withCommon_85(
                lua_G_84,
                {origin = lua_v3_result_82, dir = lua_v3_result_83, len = lua_temp_80, arrow = lua_temp_81},
                ttl,
                depthTest,
                alpha
            )
            if arrowSize ~= nil then
                cfg.arrowSize = LuaNumber(arrowSize)
            end
            local c = rgba(
                _G,
                color,
                nil,
                nil,
                nil,
                1,
                1,
                0,
                1
            )
            local lua_temp_86
            if cfg._alpha ~= nil then
                lua_temp_86 = cfg._alpha
            else
                lua_temp_86 = c[4]
            end
            local aa = lua_temp_86
            cfg.color = {
                c[1],
                c[2],
                c[3],
                clamp01(_G, aa)
            }
            LuaTableRemove(cfg, "_alpha")
            call(_G, "ray", cfg)
            return api
        end
        local function axes(self, pos, size, ttl, depthTest)
            local lua_withCommon_90 = withCommon
            local lua_G_89 = _G
            local lua_v3_result_88 = v3(
                _G,
                pos,
                nil,
                nil,
                nil,
                0,
                0,
                0
            )
            local lua_temp_87
            if size ~= nil then
                lua_temp_87 = LuaNumber(size)
            else
                lua_temp_87 = 1
            end
            local cfg = lua_withCommon_90(
                lua_G_89,
                {pos = lua_v3_result_88, size = lua_temp_87},
                ttl,
                depthTest,
                nil
            )
            LuaTableRemove(cfg, "_alpha")
            call(_G, "axes", cfg)
            return api
        end
        local function box(self, center, sizeOrHalf, color, ttl, depthTest, alpha, rotOrEulerDeg)
            local cfg = withCommon(
                _G,
                {center = v3(
                    _G,
                    center,
                    nil,
                    nil,
                    nil,
                    0,
                    0,
                    0
                )},
                ttl,
                depthTest,
                alpha
            )
            cfg.size = v3(
                _G,
                sizeOrHalf,
                nil,
                nil,
                nil,
                1,
                1,
                1
            )
            if rotOrEulerDeg ~= nil then
                if LuaArrayIsArray(rotOrEulerDeg) and #rotOrEulerDeg >= 4 then
                    cfg.rot = rotOrEulerDeg
                else
                    cfg.eulerDeg = v3(
                        _G,
                        rotOrEulerDeg,
                        nil,
                        nil,
                        nil,
                        0,
                        0,
                        0
                    )
                end
            end
            local c = rgba(
                _G,
                color,
                nil,
                nil,
                nil,
                0.95,
                0.95,
                0.95,
                1
            )
            local lua_temp_91
            if cfg._alpha ~= nil then
                lua_temp_91 = cfg._alpha
            else
                lua_temp_91 = c[4]
            end
            local aa = lua_temp_91
            cfg.color = {
                c[1],
                c[2],
                c[3],
                clamp01(_G, aa)
            }
            LuaTableRemove(cfg, "_alpha")
            call(_G, "box", cfg)
            return api
        end
        local function sphere(self, center, radius, color, ttl, depthTest, alpha, segments)
            local lua_withCommon_96 = withCommon
            local lua_G_95 = _G
            local lua_v3_result_94 = v3(
                _G,
                center,
                nil,
                nil,
                nil,
                0,
                0,
                0
            )
            local lua_temp_92
            if radius ~= nil then
                lua_temp_92 = LuaNumber(radius)
            else
                lua_temp_92 = 1
            end
            local lua_temp_93
            if segments ~= nil then
                lua_temp_93 = bit32.bor(
                    LuaNumber(segments),
                    0
                )
            else
                lua_temp_93 = 24
            end
            local cfg = lua_withCommon_96(
                lua_G_95,
                {center = lua_v3_result_94, radius = lua_temp_92, segments = lua_temp_93},
                ttl,
                depthTest,
                alpha
            )
            local c = rgba(
                _G,
                color,
                nil,
                nil,
                nil,
                0.9,
                0.9,
                0.9,
                1
            )
            local lua_temp_97
            if cfg._alpha ~= nil then
                lua_temp_97 = cfg._alpha
            else
                lua_temp_97 = c[4]
            end
            local aa = lua_temp_97
            cfg.color = {
                c[1],
                c[2],
                c[3],
                clamp01(_G, aa)
            }
            LuaTableRemove(cfg, "_alpha")
            call(_G, "sphere", cfg)
            return api
        end
        local function circle(self, center, normal, radius, color, ttl, depthTest, alpha, segments)
            local lua_withCommon_103 = withCommon
            local lua_G_102 = _G
            local lua_v3_result_100 = v3(
                _G,
                center,
                nil,
                nil,
                nil,
                0,
                0,
                0
            )
            local lua_v3_result_101 = v3(
                _G,
                normal,
                nil,
                nil,
                nil,
                0,
                1,
                0
            )
            local lua_temp_98
            if radius ~= nil then
                lua_temp_98 = LuaNumber(radius)
            else
                lua_temp_98 = 1
            end
            local lua_temp_99
            if segments ~= nil then
                lua_temp_99 = bit32.bor(
                    LuaNumber(segments),
                    0
                )
            else
                lua_temp_99 = 24
            end
            local cfg = lua_withCommon_103(
                lua_G_102,
                {center = lua_v3_result_100, normal = lua_v3_result_101, radius = lua_temp_98, segments = lua_temp_99},
                ttl,
                depthTest,
                alpha
            )
            local c = rgba(
                _G,
                color,
                nil,
                nil,
                nil,
                0.9,
                0.9,
                0.9,
                1
            )
            local lua_temp_104
            if cfg._alpha ~= nil then
                lua_temp_104 = cfg._alpha
            else
                lua_temp_104 = c[4]
            end
            local aa = lua_temp_104
            cfg.color = {
                c[1],
                c[2],
                c[3],
                clamp01(_G, aa)
            }
            LuaTableRemove(cfg, "_alpha")
            call(_G, "circle", cfg)
            return api
        end
        local function polyline(self, points, closed, color, ttl, depthTest, alpha)
            local cfg = withCommon(
                _G,
                {points = points or ({}), closed = not not closed},
                ttl,
                depthTest,
                alpha
            )
            local c = rgba(
                _G,
                color,
                nil,
                nil,
                nil,
                0.9,
                0.9,
                0.9,
                1
            )
            local lua_temp_105
            if cfg._alpha ~= nil then
                lua_temp_105 = cfg._alpha
            else
                lua_temp_105 = c[4]
            end
            local aa = lua_temp_105
            cfg.color = {
                c[1],
                c[2],
                c[3],
                clamp01(_G, aa)
            }
            LuaTableRemove(cfg, "_alpha")
            call(_G, "polyline", cfg)
            return api
        end
        local function grid(self, center, halfSize, step, ttl, depthTest, colorMajor, colorMinor, majorEvery)
            local lua_withCommon_111 = withCommon
            local lua_G_110 = _G
            local lua_v3_result_109 = v3(
                _G,
                center,
                nil,
                nil,
                nil,
                0,
                0.01,
                0
            )
            local lua_temp_106
            if halfSize ~= nil then
                lua_temp_106 = LuaNumber(halfSize)
            else
                lua_temp_106 = 10
            end
            local lua_temp_107
            if step ~= nil then
                lua_temp_107 = LuaNumber(step)
            else
                lua_temp_107 = 1
            end
            local lua_temp_108
            if majorEvery ~= nil then
                lua_temp_108 = bit32.bor(
                    LuaNumber(majorEvery),
                    0
                )
            else
                lua_temp_108 = 5
            end
            local cfg = lua_withCommon_111(
                lua_G_110,
                {center = lua_v3_result_109, halfSize = lua_temp_106, step = lua_temp_107, majorEvery = lua_temp_108},
                ttl,
                depthTest,
                nil
            )
            if colorMinor ~= nil then
                cfg.colorMinor = rgba(
                    _G,
                    colorMinor,
                    nil,
                    nil,
                    nil,
                    0.25,
                    0.35,
                    0.45,
                    0.55
                )
            end
            if colorMajor ~= nil then
                cfg.colorMajor = rgba(
                    _G,
                    colorMajor,
                    nil,
                    nil,
                    nil,
                    0.55,
                    0.65,
                    0.75,
                    0.85
                )
            end
            LuaTableRemove(cfg, "_alpha")
            call(_G, "grid", cfg)
            return api
        end
        local function child(self, childScope)
            return makeState(
                _G,
                prefix .. LuaStringTrim(tostring(childScope or "")),
                S
            ).api
        end
        local function supported(self)
            if not dbg then
                return KObject:freeze({})
            end
            local out = {}
            local fns = {
                "enabled",
                "clear",
                "tick",
                "line",
                "ray",
                "axes",
                "box",
                "sphere",
                "circle",
                "polyline",
                "grid"
            }
            do
                local i = 0
                while i < #fns do
                    out[fns[i + 1]] = has(_G, fns[i + 1])
                    i = i + 1
                end
            end
            return KObject:freeze(out)
        end
        api = KObject:freeze({
            enabled = enabled,
            clear = clear,
            tick = tick,
            setTTL = setTTL,
            setDepth = setDepth,
            setAlpha = setAlpha,
            setDepthWrite = setDepthWrite,
            line = line,
            ray = ray,
            axes = axes,
            box = box,
            sphere = sphere,
            circle = circle,
            polyline = polyline,
            grid = grid,
            child = child,
            scope = child,
            supported = supported,
            scopeName = scope,
            prefix = prefix,
            safeJson = safeJson
        })
        return {api = api, state = S}
    end
    local root = makeState(_G, "", nil)
    local function enabled(self)
        return not not dbg
    end
    return KObject:freeze({
        enabled = enabled,
        enabledDraw = root.api.enabled,
        clear = root.api.clear,
        tick = root.api.tick,
        setTTL = root.api.setTTL,
        setDepth = root.api.setDepth,
        setAlpha = root.api.setAlpha,
        setDepthWrite = root.api.setDepthWrite,
        line = root.api.line,
        ray = root.api.ray,
        axes = root.api.axes,
        box = root.api.box,
        sphere = root.api.sphere,
        circle = root.api.circle,
        polyline = root.api.polyline,
        grid = root.api.grid,
        child = root.api.child,
        scope = root.api.child,
        supported = root.api.supported,
        safeJson = safeJson
    })
end
create = setmetatable(
    {},
    {__call = function(lua_, self, engine, K)
        if not engine then
            error(
                LuaConstruct(Error, "[DEBUG] engine is required"),
                0
            )
        end
        return makeApi(_G, engine, K)
    end}
)
create.META = {
    moduleId = "debug",
    version = "1.0.0",
    description = "Rootkit wrapper for engine.debug() with safe configs + scoped child drawers + ergonomic helpers",
    engineMin = "0.1.0"
}
M = create

return M
