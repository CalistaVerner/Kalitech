# HUD

`ENGINE.hud` — Lua API интерфейса поверх Lemur. Модуль управляет слоями, элементами, layout и событиями.

```lua
local hud = ENGINE.hud
local layer = hud:layer("game")

layer:panel({id = "root", x = 20, y = 20, width = 320, height = 140})
layer:text({id = "fps", parent = "root", text = "FPS: 0"})
layer:setText("fps", "FPS: 60")
```

## Layer

Основные методы:

- `clear()`, `destroy()`, `get(id)`, `has(id)`, `drop(id)`;
- `setText(id, text)`, `setValue(id, value)`, `setVisible(id, bool)`;
- `relayout()`, `pullAll()`, `ns(prefix)`;
- `container(cfg)`, `panel(cfg)`, `rect(cfg)`, `text(cfg)`, `label(cfg)`;
- `input(cfg)`, `checkbox(cfg)`, `slider(cfg)`, `radio(cfg)`, `radioGroup(cfg)`;
- `stackText(panel, rows, cfg)`;
- `spec(tree, options)` для декларативной сборки.

```lua
layer:spec({
    type = "panel",
    id = "settings",
    children = {
        {type = "label", id = "title", text = "Settings"},
        {type = "slider", id = "volume", min = 0, max = 1, value = 0.8}
    }
}, {relayout = true})
```

Идентификаторы уникальны внутри слоя. Для подсистем используйте `layer:ns("player")`, чтобы автоматически добавлять префикс.
