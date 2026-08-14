# Sound

`ENGINE.sound` — Lua-фасад звука с bank, событиями и управляемыми экземплярами.

```lua
local sound = ENGINE.sound
sound:play("ui.click")
```

## API

- `play(idOrCfg, cfg)`, `playAt(idOrCfg, pos, cfg)`, `play2D(idOrCfg, cfg)`;
- `playSound(cfg)`;
- `stop(handle)`, `pause(handle)`, `resume(handle)`;
- `volume(handle, value)`, `pitch(handle, value)`, `position(handle, pos)`;
- `fadeTo(handle, volume, duration)`;
- `loadBank(bank)`, `reloadBank(path)`, `setBankPath(path)`;
- `setSeed(seed)`, `setDeterministic(bool)`;
- `update(dt)`, `stats()`, `debug(bool)`.

```lua
local handle = sound:playSound({
    event = "debris.hit",
    pos = {x = 4, y = 1, z = -2},
    is3D = true,
    volume = 0.8,
    random = true
})

sound:fadeTo(handle, 0, 0.4)
```

Банк по умолчанию читается из `data/sounds.json`. Конфигурация события может задавать варианты источников, громкость, pitch, дистанцию и параметры повторного использования.
