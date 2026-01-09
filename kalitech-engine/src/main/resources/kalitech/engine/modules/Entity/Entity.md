# Entity JS API (v1.1) — Kalitech

Этот гайд описывает **декларативный JS API** для создания сущностей (ENT):

* `entity` + опционально `surface` + опционально `body` + `components`
* пресеты и builder-стиль для «игровых» кейсов
* строгая работа с id/handle для надёжного Java interop
* объектная модель: `EntApi → EntBuilder → EntityHandle`

> Модуль обычно доступен как глобальный алиас **`ENT`** (см. `META.globalName`).

---

## Подключение

```js
// вариант 1: глобальный алиас (рекомендовано)
const ent = ENT;

// вариант 2: require (если ваша система модулей так устроена)
// const ENT = require("@builtin/Entity")(engine, K);
```

---

## Быстрый старт

### 1) Простая сущность с мешем (без физики)

```js
const e = ENT.create({
    name: "crate",
    surface: {
        type: "box",
        name: "crate.mesh",
        size: 1,
        pos: [0, 2, 0],
        attach: true
    },
    attachSurface: true
});

LOG.info("entityId=" + e.id() + " surfaceId=" + e.surfaceHandleId());
```

### 2) Сущность с физикой (body создаётся ровно один раз)

```js
const e = ENT.create({
    name: "player",
    surface: {
        type: "capsule",
        radius: 0.35,
        height: 1.8,
        pos: [0, 3, 0],
        attach: true
    },
    body: {
        mass: 80,
        friction: 0.9,
        lockRotation: true,
        // collider можно не указывать: ENT попробует вывести его из surface.type
        // collider: { type: "capsule", radius: 0.35, height: 1.8 }
    },
    components: {
        tag: {kind: "player"},
        stats: () => ({hp: 100, stamina: 100})
    },
    debug: true
});

// Быстрые физические операции через EntityHandle
e.velocity([0, 0, 0]);
e.applyImpulse([0, 6, 0]);
```

---

## Что возвращает `ENT.create()`

`ENT.create(cfg)` возвращает **`EntityHandle`** — объект-обёртку с:

* `entityId`, `surfaceId`, `bodyId` (примитивы)
* ссылками `surface` и `body` (если были созданы)
* удобными методами (transform/forces/components/destroy)

> Если `body` был создан на Java стороне автоматически (через `surface.physics`), то `EntityHandle.body` может быть
`null`, но `bodyId` будет доступен, и можно использовать `bodyRef()`.

---

## Декларативный API: `ENT.create(cfg)`

### Общие поля

| Поле            |         Тип | По умолчанию | Описание                                                                  |
|-----------------|------------:|-------------:|---------------------------------------------------------------------------|
| `name`          |      string |   `"entity"` | имя сущности (уходит в Java `engine.entity().create(name)`)               |
| `surface`       | object/null |            — | конфиг для `engine.mesh().create(surfaceCfg)`                             |
| `attachSurface` |     boolean |       `true` | прикрепить surface к entity: `engine.surface().attach(surface, entityId)` |
| `body`          | object/null |            — | конфиг для `engine.physics().body(bodyCfg)`                               |
| `components`    | object/null |            — | карта компонентов: `{ name: dataOrFn }`                                   |
| `debug`         |     boolean |      `false` | вывести лог создания (`engine.log().info`)                                |

### Surface config (типично)

`cfg.surface` — это то, что понимает `engine.mesh().create()`.

Примерные поля:

* `type`: `"capsule" | "box" | "sphere" | ...`
* `radius`, `height`, `size`
* `pos`: `[x,y,z]` или `{x,y,z}`
* `attach`: boolean
* `physics`: object (но см. важное правило ниже)

### Body config (типично)

`cfg.body` — то, что понимает `engine.physics().body()`.

* `surface`: surface-handle или surfaceId (если не указан — ENT подставит созданный surface)
* `mass`, `friction`, `restitution`
* `damping: { linear, angular }`
* `kinematic`, `lockRotation`
* `collider: { type, ... }`

---

## Важно: «физика создаётся один раз»

ENT строго держит контракт:

1. Если ты передал **`cfg.body`**, то тело создаётся **только** через `engine.physics().body(cfg.body)`.
2. Если ты **не** передал `cfg.body`, но передал `cfg.surface.physics`, то Java-слой может создать тело внутри
   `mesh.create()`.

> Если переданы оба (`cfg.body` и `cfg.surface.physics`) — ENT удалит `surface.physics`, чтобы не было дублей.

---

## Компоненты: `components` и удобные методы

### `components: { name: dataOrFn }`

```js
const e = ENT.create({
    name: "npc",
    components: {
        tag: {kind: "npc"},
        ai: (ctx) => ({
            state: "idle",
            entityId: ctx.entityId,
            bodyId: ctx.bodyId
        })
    }
});
```

Контекст функции-компонента:

```js
({
    entityId,
    surface,
    body,
    surfaceId,
    bodyId,
    cfg
})
```

### `EntityHandle.component(name, data)`

```js
e.component("tag", {kind: "loot"});
```

### `EntityHandle.components(mapOrFn)`

```js
e.components({
    stats: {hp: 50},
    tag: {kind: "barrel"}
});
```

---

## Builder API: `ENT.$(preset)`

ENT содержит пресеты и builder-стиль, чтобы создавать сущности без простыней конфигов.

### Список пресетов

```js
const names = ENT.presets();
LOG.info(JSON.stringify(names));
```

Типично доступны:

* `player`
* `capsule`
* `box`
* `sphere`

### Пример: capsule preset

```js
const e = ENT.$("capsule")
    .name("capsule_1")
    .surface({pos: [0, 3, 0]})
    .create();
```

### Shortcut методы

```js
const p = ENT.player$({
    body: {mass: 90},
    surface: {pos: [0, 4, 0]}
}).create();

const b = ENT.box$({
    name: "crate",
    surface: {size: 1.2, pos: [2, 2, 0]},
    body: {mass: 15}
}).create();
```

### Переопределение пресетов

```js
ENT.preset("player", {
    body: {friction: 1.0},
    surface: {radius: 0.4}
});
```

### Глобальные body defaults

```js
ENT.bodyDefaults({
    friction: 0.85,
    damping: {linear: 0.1, angular: 0.9}
});
```

---

## EntityHandle: Surface утилиты

### `setVisible(bool)`

```js
e.setVisible(false);
```

### `setCull(hint)`

```js
e.setCull("always");
```

> Эти методы требуют `surfaceId > 0`.

---

## EntityHandle: Physics API

> Все методы ниже требуют `bodyId > 0`. Если тела нет — будет выброшена ошибка.

### `hasBody()`

```js
if (e.hasBody()) {
    // ...
}
```

### `bodyRef()`

Возвращает удобный объект-обёртку, привязанный к конкретному `bodyId`.

```js
const b = e.bodyRef();

b.velocity([0, 0, 0]);
b.applyImpulse([0, 5, 0]);

const p = b.position();
LOG.info("pos=" + JSON.stringify(p));
```

Если доступен глобальный `PHYS.ref(id)` — будет использован он.

### Transform

```js
e.position();                 // get

e.position([0, 2, 0]);        // set (warp)

e.warp({x: 1, y: 3, z: 0});  // warp

e.velocity([0, 0, 0]);

e.yaw(Math.PI * 0.5);
```

### Forces

```js
e.applyImpulse([0, 6.5, 0]);

e.applyCentralForce([0, 0, 12]);

e.applyTorque([0, 2.0, 0]);

e.angularVelocity([0, 0.4, 0]);

e.clearForces();
```

### Flags / groups

```js
e.lockRotation(true);

e.collisionGroups(0x0002, 0xFFFF);
```

### Raycast helpers

```js
const hit = e.raycast({
    from: [0, 2, 0],
    to: [0, -10, 0]
});

const down = e.raycastDown(2.0, 0.15);
```

---

## Жизненный цикл: `destroy()`

```js
e.destroy();
```

Что делает `destroy()`:

1. выполняет кастомные destroyers (если были добавлены на стороне сборки)
2. удаляет физическое тело, если `bodyId > 0`: `engine.physics().remove(bodyId)`
3. зануляет ссылки и ids внутри handle

> Ошибки destroyers не скрываются — если упало, значит упало.

---

## Утилиты: `ENT.idOf(handle, kind)`

Извлекает числовой id из разных форм handle.

```js
const sid = ENT.idOf(surfaceHandle, "surface");
const bid = ENT.idOf(bodyHandle, "body");
const eid = ENT.idOf(entityHandle, "entity");
```

`kind`:

* `"surface"`
* `"body"`
* `"entity"`

---

## Рекомендуемые паттерны

### 1) NPC без физики

```js
const npc = ENT.create({
    name: "npc",
    surface: {type: "box", size: 0.8, pos: [5, 0, 5], attach: true},
    components: {
        tag: {kind: "npc"},
        ai: {mode: "idle"}
    }
});
```

### 2) Динамический объект (ящик)

```js
const crate = ENT.box$({
    name: "crate",
    surface: {pos: [0, 4, 0], size: 1.0},
    body: {mass: 20}
}).create();

crate.applyImpulse([1, 0, 0]);
```

### 3) Игрок-подобный объект

```js
const player = ENT.player$({
    name: "player",
    surface: {pos: [0, 4, 0]},
    body: {mass: 85}
}).create();

player.lockRotation(true);
```

---

## Версия

* ENT JS: **v1.1.0**