# Lua-модули Kalitech

Каждый модуль движка — Lua-файл, который возвращает фабрику:

```lua
local function create(_, engine, state)
    local native = engine:camera()
    return {
        position = function() return native:location() end
    }
end

create.META = {
    moduleId = "camera",
    version = "1.0.0",
    engineMin = "0.3.8"
}

return create
```

Bootstrap загружает `@module/manifest`, создаёт модули и регистрирует их под каноническими ключами. Игровой код обращается к ним только через `ENGINE`:

```lua
local world = ENGINE.world
local material = ENGINE.material
local physics = ENGINE.physics
```

Неявных глобальных псевдонимов модулей нет.

## Контракт ресурсов

Манифест JAR содержит только обязательные поля:

- `id`, `name`, `version`;
- `mainClass` для Java-части;
- `depends` для зависимостей;
- `lua` для Lua-точки входа;
- `docs` для документации.

Неизвестные поля считаются ошибкой. Lua-точка входа обязана существовать внутри JAR и вернуть публичное значение.

## Разрешение имён

- `@builtin/...` — ресурсы движка;
- `@module/...` — ресурсы модулей;
- `./...` и `../...` — путь относительно текущего Lua-файла;
- `Scripts/...` — ресурсы приложения.

Каждый модуль исполняется один раз и кешируется. Hot reload удаляет изменённый модуль и все зависящие от него записи.
