local M = {}

local function normalizeMeta(_, exported, fallbackName, moduleId)
    local source = exported and exported.META
    if source ~= nil and KTypeOf(source) ~= "table" then
        error("[bootstrap] module META must be a Lua table: " .. tostring(moduleId), 0)
    end
    source = source or {}

    return {
        moduleId = tostring(source.moduleId or moduleId or fallbackName or ""),
        name = tostring(source.name or fallbackName or moduleId or ""),
        version = tostring(source.version or "0.0.0"),
        description = tostring(source.description or ""),
        engineMin = tostring(source.engineMin or "")
    }
end

M.normalizeMeta = normalizeMeta
return M
