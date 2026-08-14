# Entity

`ENGINE.entity` — Lua-фасад создания и управления игровыми сущностями. Модуль объединяет ECS, surface и physics в один результат с устойчивым UUID.

## Создание

```lua
local entity = ENGINE.entity

local result = entity:create({
    name = "player",
    surface = {
        type = "capsule",
        radius = 0.35,
        height = 1.8,
        position = {x = 0, y = 3, z = 0}
    },
    body = {
        mass = 80,
        lockRotation = true
    }
})

local handle = result.handle
local core = result.core
```

Доступны фабрики `entity["$"]`, `player$`, `capsule$`, `box$` и `sphere$`. Builder поддерживает имя, позицию, материал, физику и завершение через `:create()`.

## API

- `create(cfg)` — создаёт сущность;
- `preset(name, cfg)` — регистрирует preset;
- `bodyDefaults(cfg)` — задаёт базовую конфигурацию тела;
- `presets()` — возвращает известные presets;
- `idOf(value)` и `uuidOf(value)` — нормализуют идентификатор.

Handle предоставляет операции видимости, culling, позиции, скорости, импульса, компонентов и удаления. Для межсистемных ссылок используйте UUID; числовые surface/body id являются локальными ресурсами мира.
