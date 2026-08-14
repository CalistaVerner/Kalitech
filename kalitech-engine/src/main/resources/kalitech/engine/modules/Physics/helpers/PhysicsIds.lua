local M = {}
function bodyIdOf(self, handleOrId)
    if KTypeOf(handleOrId) == "number" then
        return bit32.bor(handleOrId, 0)
    end
    if not handleOrId then
        return 0
    end
    if KTypeOf(handleOrId.id) == "function" then
        return bit32.bor(
            handleOrId:id(),
            0
        )
    end
    if KTypeOf(handleOrId.id) == "number" then
        return bit32.bor(handleOrId.id, 0)
    end
    if KTypeOf(handleOrId.bodyId) == "number" then
        return bit32.bor(handleOrId.bodyId, 0)
    end
    return 0
end
function surfaceIdOf(self, handleOrId)
    if KTypeOf(handleOrId) == "number" then
        return bit32.bor(handleOrId, 0)
    end
    if not handleOrId then
        return 0
    end
    if KTypeOf(handleOrId.id) == "function" then
        return bit32.bor(
            handleOrId:id(),
            0
        )
    end
    if KTypeOf(handleOrId.id) == "number" then
        return bit32.bor(handleOrId.id, 0)
    end
    if KTypeOf(handleOrId.surfaceId) == "number" then
        return bit32.bor(handleOrId.surfaceId, 0)
    end
    return 0
end
M = KObject:freeze({bodyIdOf = bodyIdOf, surfaceIdOf = surfaceIdOf})

return M
