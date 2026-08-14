# Physics

`ENGINE.physics` — строгий Lua-фасад физического backend.

## Тела

```lua
local physics = ENGINE.physics

local body = physics:body({
    surface = surfaceId,
    mass = 10,
    collider = {type = "mesh"},
    lockRotation = false
})
```

Основные методы:

- `body(cfg)`, `remove(value)`, `removeById(id)`;
- `bodyOfSurface(surfaceId)`, `handle(id)`, `exists(id)`;
- `position(value)`, `teleport(value, pos)`, `warp(value, pos)`;
- `velocity(value[, vec])`, `angularVelocity(value[, vec])`, `yaw(value, radians)`;
- `applyImpulse(value, vec)`, `applyCentralForce(value, vec)`, `applyTorque(value, vec)`;
- `clearForces(value)`, `lockRotation(value, bool)`, `setKinematic(value, bool)`;
- `collisionGroups(value, group, mask)`, `gravity(vec)`, `debug(bool)`;
- `idOf(value)`, `surfaceIdOf(value)`, `ref(id)`.

## Запросы пространства

Канонический точный raycast — `raycastEx`:

```lua
local hit = physics:raycastEx({
    from = {0, 10, 0},
    to = {0, -10, 0},
    ignoreBodyId = playerBodyId,
    staticOnly = true
})

if hit.hit then
    print(hit.point.x, hit.point.y, hit.point.z)
end
```

Также доступны `raycast`, `raycastAll`, `sweepSphere` и `sweepCapsule`. Векторы задаются Lua-массивом `{x, y, z}` или таблицей `{x=..., y=..., z=...}`.
