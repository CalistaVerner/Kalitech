# Hud (UI) — документация

Hud — это модуль построения UI поверх Java-бриджа `HudApi` (Lemur). Вся «логика мозга» (реестр элементов, placement,
relayout, radio-группы, builder) живёт в JS.

Основа:

* `Hud.js` — точка входа, создаёт `HUD` и регистрирует стандартные компоненты.
* `Layer.js` — слой UI: создание элементов, реестр, relayout, radio-группы.
* `HudPlacement.js` — якоря/позиционирование + переключение систем координат.
* `HudElements.js` — базовые методы элемента (text/visible/pos/size/bg/color/value/checked/remove).
* `UIBuilder.js` — builder для быстрого сборочного «стека» текста внутри панелей.

---

## 1) Быстрый старт

### 1.1 Подключение

В рантайме у тебя уже есть `engine.hud()` (Java API), а в JS — модуль `Hud.js`, который создаёт объект `HUD`.

Пример (псевдо-boot):

```js
"use strict";

// допустим, ctx.engine.api() уже возвращает Engine API
exports.start = function start(ctx) {
    const engine = ctx.engine.api();

    // HUD module обычно создаётся как builtin и пробрасывается в env.
    // Если создаёшь вручную:
    const HudModule = require("@builtin/Hud");
    const HUD = HudModule(engine, {coord: "topLeft"});

    // слой
    const layer = HUD.layer("debug-ui");

    // элемент
    layer.text({
        id: "hello",
        text: "HELLO HUD",
        place: {anchor: "tl", x: 12, y: 12},
        fontSize: 16,
        color: {r: 1, g: 1, b: 1, a: 1}
    });

    layer.relayout();
};
```

Ключевые принципы:

* **Всегда работай через `Layer`** — он держит реестр и умеет relayout/радио-группы.
* Если используешь `place`, обязательно вызывай `layer.relayout()` при создании и при изменении viewport/размеров.

---

## 2) Координаты и размещение

### 2.1 Системы координат

HUD работает в координатах экрана. По умолчанию — **topLeft** (0,0 сверху слева).

Настраивается через `HudModule(engine, { coord: "topLeft" | "bottomLeft" })`.

`bottomLeft` — режим совместимости, при котором Y инвертируется.

### 2.2 Placement: якоря (anchor)

Опция `place` задаёт якорь и смещение.

Формат:

```js
place: {
    anchor: "tl|tr|bl|br|c|tc|bc|lc|rc", x
:
    0, y
:
    0
}
```

* `tl` — top-left
* `tr` — top-right
* `bl` — bottom-left
* `br` — bottom-right
* `c` — center
* `tc` — top-center
* `bc` — bottom-center
* `lc` — left-center
* `rc` — right-center

Поведение:

* Для прямоугольных элементов (panel/container/input/slider) позиция рассчитывается с учётом размеров (`placeRect`).
* Для точечных (label/text/checkbox/radio) — как точка (`placePoint`).

### 2.3 Relayout

`Layer` хранит список элементов, у которых есть `_place`, и при `layer.relayout()` пересчитывает позицию под текущий
viewport.

Типичный паттерн:

* Создал элементы с `place` → `layer.relayout()`.
* Изменил размеры панели/слайдера/инпута → при необходимости снова `layer.relayout()`.
* При resize окна → вызывай relayout (обычно в системном UI-system).

---

## 3) Слои

### 3.1 Создание слоя

```js
const layer = HUD.layer("debug-ui");
```

Создаёт Java-layer через `api.createLayer(name)` и оборачивает его в JS `Layer`.

### 3.2 Очистка и уничтожение

```js
layer.clear();    // удалить все элементы слоя
layer.destroy();  // уничтожить слой полностью
```

`clear()` также сбрасывает реестр элементов и radio-группы детерминированно.

---

## 4) Элементы: создание и базовые операции

Все элементы имеют общий контракт методов (см. `Element`).

### 4.1 Label/Text

```js
layer.text({
    id: "fps",
    text: "FPS: --",
    place: {anchor: "tr", x: -12, y: 12},
    fontSize: 14,
    color: {r: 1, g: 1, b: 1, a: 1}
});
```

`label()` — alias на `text()`.

### 4.2 Panel / Rect

Panel — прямоугольник, может быть родителем.

```js
const panel = layer.panel({
    id: "debug.panel",
    x: 10, y: 10,
    w: 280, h: 120,
    bg: {r: 0.04, g: 0.06, b: 0.08, a: 0.65},
    place: {anchor: "tl", x: 10, y: 10}
});
```

`rect()` — alias на `panel()`.

### 4.3 Container

Контейнер — лёгкий родитель без размеров.

```js
const root = layer.container({
    id: "root",
    place: {anchor: "c", x: 0, y: 0}
});

layer.text({
    parent: root,
    text: "CENTER",
    x: 0, y: 0
});
```

### 4.4 Input

```js
const inp = layer.input({
    id: "name",
    text: "Player",
    w: 240, h: 26,
    place: {anchor: "bl", x: 12, y: -12}
});

// чтение/запись
const v = inp.value();
inp.value("NewName");
```

`value()` по умолчанию работает как текст (для input).

### 4.5 Checkbox

```js
const cb = layer.checkbox({
    id: "god",
    text: "GODMODE",
    x: 12, y: 180
});

cb.checked(true);
const on = cb.checked();
```

`checked()` доступен, если Java API реализует `setChecked/isChecked`.

### 4.6 Slider

```js
const s = layer.slider({
    id: "vol",
    min: 0,
    max: 1,
    value: 0.5,
    w: 240,
    place: {anchor: "bc", x: 0, y: -20}
});

const cur = s.value();
s.value(0.75);
```

Для slider `value()` читает/пишет `getSliderValue/setSliderValue`.

---

## 5) Базовый API элемента

Любой элемент (`Element`) умеет:

* `text(v)` — установить текст
* `getText()` — получить текст (если Java API предоставляет)
* `visible(bool)` — показать/скрыть
* `pos(x,y)` — позиция
* `size(w,h)` — размер (и кэш `_w/_h` для relayout)
* `bg(r,g,b,a)` — фон
* `color(r,g,b,a)` — цвет текста
* `fontSize(px)` — размер шрифта (если `api.setFontSize` реализован)
* `remove()` — удалить элемент (через слой, чтобы удалить из реестра)

Дополнительно:

* `value(v)` — умное чтение/запись: slider → число, иначе текст.
* `checked(v)` — только для checkbox/radio (если Java API умеет).
* `bindPrefix(prefix)` и `bindFormat(fn)` — форматирование при `value(v)`.

Пример форматного биндинга:

```js
layer.text({id: "hp", text: "HP:"})
    .bindPrefix("HP: ")
    .bindFormat(v => (+v).toFixed(0));

layer.get("hp").value(125.8); // => "HP: 126"
```

---

## 6) Реестр элементов слоя

`Layer` хранит элементы по `id` в `_reg`.

### 6.1 get/has/drop

```js
const el = layer.get("fps");
const ok = layer.has("fps");
layer.drop("fps", true); // true => физически удалить из Java
```

### 6.2 Утилиты обновления

```js
layer.setText("fps", "FPS: 120");
layer.setValue("vol", 0.8);
layer.setVisible("debug.panel", false);
```

Это предпочтительнее, чем вручную держать ссылки везде.

---

## 7) UIBuilder: быстрый стек текста

`layer.ui()` возвращает builder.

Пример:

```js
const ui = layer.ui();

ui.panel("debug.panel", {
    w: 280, h: 60,
    place: {anchor: "tl", x: 10, y: 10}
});

ui.stack("debug.title", "DEBUG");
ui.stack("debug.fps", "FPS: --");
ui.stack("debug.pos", "POS: --");

ui.done();
layer.relayout();
```

`stack()` вызывает `panel.stack(...)`, а реализация `Panel.stack` делегирует в `Layer.stackText(...)`.

---

## 8) Radio-группы (JS-only)

Radio реализован как checkbox с эксклюзивностью по `group`.

```js
layer.radio({id: "q.low", text: "Low", group: "quality", x: 12, y: 12, checked: true});
layer.radio({id: "q.med", text: "Med", group: "quality", x: 12, y: 32});
layer.radio({id: "q.high", text: "High", group: "quality", x: 12, y: 52});

const g = layer.radioGroup("quality");
const selected = g.selected();
if (selected) {
    // selected.key / selected.id
}

g.select("q.high");
```

У radio-элемента есть sugar:

* `el.select()` — выбрать себя
* `el.group()` — имя группы

---

## 9) Пример: как сделан PlayerUI (debug-панель)

Твой `PlayerUI.js` — эталон того, как делать UI без магии:

* слой создаётся один раз (`create()`)
* элементы получают стабильные `id`
* обновление идёт через `layer.setText(id, ...)`
* placement делается через `{place:{anchor,x,y}}`
* после сборки — `layer.relayout()`

Мини-шаблон:

```js
"use strict";

class DebugUi {
    constructor(HUD) {
        this.HUD = HUD;
        this.layer = null;
    }

    create() {
        if (this.layer) return;
        const layer = this.HUD.layer("debug-ui");
        this.layer = layer;

        const panel = layer.panel({
            id: "dbg.panel",
            w: 280,
            h: 60,
            place: {anchor: "tl", x: 10, y: 10},
            bg: {r: 0.04, g: 0.06, b: 0.08, a: 0.65}
        });

        panel.flow({padX: 12, padY: 8, gap: 4, fontSize: 14});
        panel.stack("dbg.title", "DEBUG", {fontSize: 18});
        panel.stack("dbg.fps", "FPS: --");

        layer.relayout();
    }

    update(fps) {
        if (!this.layer) return;
        this.layer.setText("dbg.fps", "FPS: " + (fps | 0));
    }

    destroy() {
        if (!this.layer) return;
        this.layer.destroy();
        this.layer = null;
    }
}

module.exports = DebugUi;
```

---

## 10) Рекомендованный «CDPR-подход» к UI

Чтобы UI был детерминированным и без хардкода по всей игре:

1. **Один класс = один экран/виджет** (DebugUi, InventoryUi, PauseMenuUi).
2. `create()` — создаёт слой и все элементы.
3. `update(model)` — только обновляет значения через `layer.setText/setValue/visible`.
4. `destroy()` — уничтожает слой.
5. Все ids стабильные и неймспейсные: `pause.title`, `pause.btn.resume`, `inv.slot.12`.

---

## 11) Java HudApi: минимальный контракт

JS HUD ожидает, что Java-бридж умеет создавать layer и элементы, а также базовые set/get/remove и viewport. Полный
список контрактных методов описан в шапке `Hud.js`.

Если ты расширяешь Java-часть (например, события клика/hover), добавляй новые методы в HudApi и прокидывай их в
`Element` как fluent-методы.
