# Sound (SND) — Universal Sound Facade (JS)

Автор: **Calista Verner**

Этот модуль — продуктовый, стабильный JS‑фасад над `ENGINE.sound()`.

Он объединяет **event‑банк** и **произвольные локальные звуки** (`src`) в один декларативный формат:

* быстрый one‑shot: `ENGINE.sound().playSound({ event: "player.action.throw" })`
* локальный звук без банка: `ENGINE.sound().playSound({ src: "Sounds/throw.ogg" })`
* объектный режим (пер‑объект конфиг): `ENGINE.sound().getSound("any.event").setDeterministic(true).play()`

Цель: **детерминированность уровня АРСАКИ**, предсказуемое поведение, минимум «магии».

---

## Подключение

Каноничный путь:

```js
const snd = ENGINE.sound();
```

Если включён `exposeGlobals`, может быть алиас `SND`, но источник истины — `ENGINE.sound()`.

---

## Быстрый старт

### 1) Event‑звук из банка

```js
ENGINE.sound().playSound({
    event: "player.action.throw"
});
```

### 2) Event + полная настройка

```js
ENGINE.sound().playSound({
    event: "player.action.throw",
    deterministic: false,
    is3D: true,
    x: 10, y: 2, z: -5,
    volume: [0.85, 1.0],
    pitch: [0.95, 1.05]
});
```

### 3) Локальный звук без банка (`src`)

```js
ENGINE.sound().playSound({
    src: "Sounds/throw.ogg",
    type: "buffer", // buffer | stream
    is3D: true,
    pos: {x: 10, y: 2, z: -5},
    volume: 0.9,
    pitch: 1.0
});
```

---

## Универсальный вход: `playSound(cfg)`

`playSound(cfg)` — единый декларативный вход для **event** и **src**.

### Правило выбора режима

* если задано `cfg.event` → используется event‑банк (`playEventCfg` на Java‑стороне)
* иначе если задано `cfg.src` → создаётся `AudioNode` через `create(cfg)` и проигрывается
* если не задано ни `event`, ни `src` → ошибка

### Поддерживаемые поля `cfg`

#### Общие

* `is3D: boolean` — позиционность
* `looping: boolean`
* `volume: number | [min,max]`
* `pitch: number | [min,max]`
* позиция: `pos` / `position` / `x,y,z`
* `type: "buffer" | "stream"` — для `src` (и для некоторых конфигов на Java)

#### Только для event

* `event: string` — ключ события
* `deterministic: boolean` — режим выбора варианта
* `seed: number` — seed (пер‑вызов)
* `context: { entityId, surfaceId, seq, tick, slot }` — контекст выбора варианта
* `overrides: object` — overrides, применяются поверх дефолтной `SoundDef`

---

## Объектный режим (пер‑объект конфигурация)

Иногда нужен «звуковой объект», который хранит настройки и умеет `play()`.

### Event‑объект

```js
const snd = ENGINE.sound();

const sound = snd.getSound("any.event");

sound
    .setDeterministic(true)
    .setPositional(true)
    .setEntityId(42)
    .setSurfaceId(7)
    .play();
```

### File‑объект (`src`)

```js
const snd = ENGINE.sound();

const sound2 = snd.getSoundFile("Sounds/super.ogg");

sound2
    .setPositional(true)
    .setOverrides({volume: 0.8, pitch: 1.0})
    .play();
```

### Почему объектный режим стабилен

* объект хранит **чётко описанный конфиг**
* `play()` использует **универсальный** `playSound(cfg)`
* минимум имплицитных зависимостей

---

## Детерминизм и «рандом»

### Когда deterministic=true даёт одинаковый звук

Если одновременно:

* `deterministic: true`
* `seed` один и тот же
* `context` неизменен (или отсутствует)

…то выбор варианта события будет одинаковый.

### Как получить «рандом», но управляемый

1. Самое простое: отключить детерминизм для вызова/объекта

```js
ENGINE.sound().playSound({event: "player.action.throw", deterministic: false});
```

2. Если нужен детерминизм (реплеи/сеть), но вариативность — двигайте `context.seq`

```js
let seq = 0;
ENGINE.sound().playSound({
    event: "player.action.throw",
    deterministic: true,
    seed: 1337,
    context: {entityId: 42, surfaceId: 7, seq: ++seq, tick: ENGINE.tick(), slot: 0}
});
```

### AutoSeq в объектном режиме

Event‑объект имеет авто‑счётчик `seq`, который по умолчанию увеличивается при каждом `play()`.

* `enableAutoSeq(false)` — отключить
* `setSeqMode("keep")` — не увеличивать `seq`, если он уже задан

---

## Банк событий

Если Java‑слой поддерживает `loadBank`, можно загрузить bank из JSON.

```js
const snd = ENGINE.sound();

snd.loadBank({
    "player.action.throw": [
        {src: "Sounds/throw1.ogg", volume: [0.85, 1.0], pitch: [0.95, 1.05], is3D: true},
        {src: "Sounds/throw2.ogg", volume: [0.85, 1.0], pitch: [0.95, 1.05], is3D: true}
    ]
});
```

Типичный пайплайн:

* `loadBank(bankObj)`
* `listEvents()`
* `playSound({event: ...})` / `getSound(event)`

---

## Java‑контракт (минимум)

Чтобы SND работал полноценно:

### Для event

* `playEventCfg(Value cfg)` (желательно)
* `createEventCfg(Value cfg)` (опционально, но полезно)

### Для src

* `create(Value cfg)`
* `play(AudioNode node)`

Дополнительно (желательно):

* `setSeed(long)`
* `setDeterministic(boolean)`

---

## Версия

* Sound JS: **v1.3.0**

---

## Примеры паттернов (AAA)

### Footsteps: детерминированно + контекст

```js
const snd = ENGINE.sound();
const step = snd.getSound("player.footstep")
    .setDeterministic(true)
    .setSeed(1337)
    .setEntityId(PLAYER.id)
    .setSlot(0);

let seq = 0;

function playFootstep(surfaceId) {
    step.setSurfaceId(surfaceId);
    step.setContext({seq: ++seq, tick: ENGINE.tick()});
    step.play();
}
```

### Impacts: недетерминированно

```js
ENGINE.sound().playSound({
    event: "weapon.hit",
    deterministic: false,
    is3D: true,
    pos: hitPos,
    volume: [0.75, 1.0],
    pitch: [0.9, 1.1]
});
```
