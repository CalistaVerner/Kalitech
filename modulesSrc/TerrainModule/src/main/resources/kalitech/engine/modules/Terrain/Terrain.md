# Terrain

`ENGINE.terrain` создаёт terrain surface, генерирует карты высот и связывает поверхность с физикой.

```lua
local terrain = ENGINE.terrain
local heights = terrain.heights:perlin({
    size = 513,
    seed = 1337,
    scale = 480,
    octaves = 8
})

local instance = terrain:create({
    name = "island",
    kind = "heights",
    heights = heights,
    terrain = {size = 513, patchSize = 65},
    scale = {x = 2, y = 180, z = 2},
    material = {preset = "terrain"}
})
```

## API

- `create(cfg)`, `destroy(value)`;
- `physics(surface, cfg)`;
- `heightmap(surface)`, `heightAt(surface, x, z)`;
- `normalAt(surface, x, z)`;
- `setLod(surface, cfg)`, `setMaterial(surface, value)`;
- `idOf(value)`.

`terrain.heights` предоставляет `flat`, `perlin`, `ridged`, проверку размера и преобразование через `toArray`. Карта высот — обычный Lua-массив чисел длиной `size * size`.

Для physics используется канонический `ENGINE.physics`; дополнительные глобальные имена не создаются.
