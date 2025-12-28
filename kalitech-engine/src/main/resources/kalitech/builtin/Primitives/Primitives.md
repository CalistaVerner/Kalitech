# Kalitech Primitives API

Автор: **Calista Verner**
Подсистема: `@builtin/Primitives`
Назначение: декларативное и fluent-создание мешей + физики из JavaScript
Контекст: Kalitech Engine (GraalVM, Java ↔ JS)

---

## Общая идея

`Primitives` — это **высокоуровневый JS-API** для создания геометрии (surface), который:

* работает **декларативно** (`create({ ... })`)
* поддерживает **fluent builder** (`box$().size(...).physics(...).create()`)
* автоматически связывает **mesh ↔ physics**
* добавляет **физические sugar-методы** прямо на handle:

    * `velocity()`
    * `applyImpulse()`
    * `position()`
    * `teleport()`
* не создаёт дублирующих физических тел

Java-часть остаётся **тонкой и стабильной**, вся эргономика — в JS.

---

## Подключение

### Через require (канонично)

```js
const primitives = require("@builtin/Primitives")(__kalitech);
```

### Глобально (если `exposeGlobals=true`)

```js
const g = primitives.create({ ... });
```

---

## Базовый декларативный API

### `primitives.create(cfg)`

Создаёт примитив на основе конфигурационного объекта.

```js
const box = primitives.create({
    type: "box",
    size: 3,
    name: "my-box",
    pos: [0, 5, 0],

    material: {
        def: "Common/MatDefs/Misc/Unshaded.j3md",
        params: { Color: [1, 0, 0, 1] }
    },

    physics: {
        mass: 80,
        lockRotation: false
    }
});
```

### Поддерживаемые типы (`type`)

* `"box"`
* `"sphere"`
* `"cylinder"`
* `"capsule"`
* любые кастомные строки (если Java-сторона умеет)

---

## Шорткаты для типов

```js
primitives.box(cfg);
primitives.cube(cfg);     // alias для box
primitives.sphere(cfg);
primitives.cylinder(cfg);
primitives.capsule(cfg);
```

Пример:

```js
const s = primitives.sphere({
    radius: 1.5,
    pos: [0, 10, 0],
    physics: 10   // shorthand → { mass: 10 }
});
```

---

## Массовое создание

### `primitives.many(list)`

```js
const boxes = primitives.many([
    { type: "box", size: 1, pos: [0,0,0] },
    { type: "box", size: 2, pos: [3,0,0] },
    { type: "box", size: 3, pos: [6,0,0] }
]);
```

> В будущем может быть оптимизировано Java-батчингом.

---

## Материалы

### `primitives.unshadedColor(rgba)`

```js
const red = primitives.unshadedColor([1, 0, 0, 1]);

const g = primitives.create({
    type: "box",
    size: 2,
    material: red
});
```

Можно также установить материал после создания:

```js
engine.surface().setMaterial(g, M.getMaterial("box"));
```

---

## Физика (декларативно)

### Через `physics` в cfg

```js
const g = primitives.create({
    type: "capsule",
    radius: 0.35,
    height: 1.8,
    physics: {
        mass: 80,
        lockRotation: true,
        damping: { linear: 0.15, angular: 0.95 }
    }
});
```

### Shorthand-варианты

```js
physics: 80                // → { mass: 80 }
mass: 80                   // будет перенесено в physics.mass
lockRotation: true         // будет перенесено в physics.lockRotation
```

---

## Физические методы (sugar на handle)

`Primitives` возвращает **Proxy-объект**, который расширяет `SurfaceHandle`.

### Установка скорости

```js
g.velocity({ x: 0, y: 0, z: 5 });
```

### Получение скорости

```js
const v = g.velocity();
```

### Импульс

```js
g.applyImpulse({ x: 0, y: 5, z: 0 });
```

### Телепорт / позиция

```js
g.teleport([0, 10, 0]);
// или
g.position({ x: 0, y: 10, z: 0 });
```

### Блокировка вращения

```js
g.lockRotation(true);
```

> Если физическое тело ещё не связано (редко, при кастомных пайплайнах), методы могут быть no-op. В нормальном случае всё работает автоматически.

---

## Fluent Builder API (рекомендуется для читабельности)

### Пример: box$

```js
const g = primitives
    .box$()
    .size(2)
    .name("box-1")
    .pos(0, 3, 0)
    .material(primitives.unshadedColor([0, 1, 0, 1]))
    .physics(80, { lockRotation: false })
    .create();
```

### Доступные builder-методы

* `.name(string)`
* `.pos(x, y, z)` или `.pos(Vec3)`
* `.rot(Vec3 | Vec4)`
* `.scale(number | Vec3)`
* `.size(number)`
* `.radius(number)`
* `.height(number)`
* `.material(MaterialCfg)`
* `.physics(mass, opts)`
* `.attach(true|false)`
* `.cfg()` — получить финальный cfg
* `.create()` — создать объект

### Builder для других типов

```js
primitives.sphere$()
primitives.cylinder$()
primitives.capsule$()
```

---

## Архитектурные принципы

* Нет дублирования physics body
* Физика создаётся **в Java при mesh.create**
* JS лишь **находит и использует** существующее тело
* Fluent API = sugar, не ядро
* Декларативный cfg = стабильный контракт

---

## Рекомендуемый стиль (канон)

### Продакшн

```js
primitives.create({
    type: "box",
    size: 2,
    pos: [0, 3, 0],
    material: M.getMaterial("box"),
    physics: { mass: 80 }
});
```

### Геймплей / логика

```js
primitives
  .capsule$()
  .radius(0.35)
  .height(1.8)
  .physics(80, { lockRotation: true })
  .create();
```

---

## Будущие расширения

* `primitives.batch(fn)` → Java-батчинг
* `builder.attachTo(entityId)`
* `prefab(builder => { ... })`
* schema/versioning (`cfg.api = "mesh@1"`)

---

## Итог

`Primitives` — это:

* удобство Unity/UE
* стабильность Java
* мощь JS
* ноль хардкода

Именно так строится Калитех.
