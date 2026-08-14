# Material

`ENGINE.material` — реестр материалов, загружаемый из `data/materials.json`.

```lua
local materials = ENGINE.material
local stone = materials:getMaterial("stone")
```

## API

- `getMaterial(name, overrides)` и `get(name, overrides)`;
- `getHandle(name, overrides)` и `handle(name, overrides)`;
- `preset(name, cfg)`;
- `params(name)`;
- `configure(cfg)`;
- `reload(path)`;
- `keys()`.

Overrides передаются Lua-таблицей:

```lua
local hotRock = materials:getMaterial("rock", {
    Color = {r = 1.0, g = 0.35, b = 0.15, a = 1.0},
    Roughness = 0.7
})
```

Путь базы задаётся в конфигурации bootstrap. Модуль кеширует базовые материалы и варианты с overrides.
