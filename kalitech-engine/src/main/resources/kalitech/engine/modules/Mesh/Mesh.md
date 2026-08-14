# Mesh

`ENGINE.mesh` создаёт surface и возвращает Lua-объект с цепочечным API.

```lua
local mesh = ENGINE.mesh

local box = mesh["box$"](mesh)
    :name("crate")
    :size(1.2)
    :pos(0, 4, 0)
    :material(ENGINE.material:getMaterial("box"))
    :physics(5, {lockRotation = false})
    :create()
```

Доступны builder-фабрики для box, sphere, capsule, plane и загрузки модели. Созданный объект предоставляет:

- `pos(x, y, z)`, `move(x, y, z)`, `scale(...)`, `rotate(...)`;
- `visible(bool)`, `cull(mode)`;
- `material(value)`;
- `physics(mass, cfg)`, `velocity(value)`, `impulse(value)`;
- `destroy()`;
- идентификаторы surface, body и связанной entity.

`create(cfg)` остаётся низкоуровневой формой для готовой Lua-таблицы конфигурации.
