# Mesh (builtin) — гайд по использованию

> **Mesh** — встроенный JS‑оркестратор для создания примитивов и загрузки моделей через `engine.mesh().create(...)`.
>
> Возвращает **SurfaceHandle‑обёртку** (Proxy), которая добавляет удобные методы физики:
>
> * `applyImpulse(vec3)`
> * `applyCentralForce(vec3)`
> * `velocity()` / `velocity(vec3)`
> * `position()` / `position(vec3)`
> * `teleport(vec3)`
> * `lockRotation(true|false)`
>
> При этом **физическое тело создаётся на Java‑стороне** (через `mesh.create({ physics: ... })`).

---

## Подключение

### Вариант 1: через require

```js
const Mesh = require("@builtin/Mesh")(engine, K);
```

### Вариант 2: через глобальный алиас

Если включено `exposeGlobals`, модуль может быть доступен как `globalThis.MSH` (или как вы его переименовали).

---

## Быстрый старт

### Куб с физикой

```js
const box = Mesh.cube({
  name: "box01",
  size: 2,
  pos: [0, 5, 0],
  physics: { mass: 10, lockRotation: false }
});

box.applyImpulse({ x: 0, y: 6, z: 0 });
```

### Статичный объект (без движения)

```js
const ground = Mesh.box({
  name: "ground",
  hx: 50, hy: 1, hz: 50,
  pos: [0, -1, 0],
  physics: { mass: 0 }
});
```

---

## Создание примитивов

Все фабрики внутри вызывают `engine.mesh().create(cfg)` и возвращают обёртку.

### `Mesh.create(cfg)`

Создаёт объект указанного типа.

```js
const g = Mesh.create({
  type: "sphere",
  radius: 0.5,
  pos: [1, 3, 0],
  physics: { mass: 1 }
});
```

Поддерживаемые `type`:

* `box` (или `cube` как алиас)
* `sphere`
* `cylinder`
* `capsule`
* `model` (загрузка моделей)

### `Mesh.box(cfg)` / `Mesh.cube(cfg)`

Параметры:

* `size` — размер куба (если задан, задаёт `hx=hy=hz=size/2`)
* или `hx, hy, hz` — half‑extents
* `pos` — позиция
* `material` — материал (см. ниже)
* `physics` — физика

```js
const b = Mesh.box({ hx: 1, hy: 0.5, hz: 2, pos: [0, 2, 0], physics: { mass: 3 } });
```

### `Mesh.sphere(cfg)`

Параметры:

* `radius`
* (опционально) `zSamples`, `radialSamples`

```js
const s = Mesh.sphere({ radius: 0.35, pos: [2, 4, 0], physics: { mass: 0.5 } });
```

### `Mesh.cylinder(cfg)`

Параметры:

* `radius`
* `height`
* (опционально) `axisSamples`, `radialSamples`

```js
const c = Mesh.cylinder({ radius: 0.3, height: 1.2, pos: [0, 3, 2], physics: { mass: 2 } });
```

### `Mesh.capsule(cfg)`

Параметры:

* `radius`
* `height`

```js
const cap = Mesh.capsule({ radius: 0.35, height: 1.8, pos: [0, 1, 0], physics: { mass: 80 } });
```

---

## Загрузка моделей

Mesh добавляет удобный метод `loadModel`, который внутри вызывает:

```js
engine.mesh().create({ type: "model", path: "...", ... })
```

### `Mesh.loadModel(path, cfg?)`

```js
const house = Mesh.loadModel("Models/house.obj", {
  pos: [10, 0, -5],
  scale: 1,
  physics: { mass: 0 }
});
```

### `Mesh.loadModel(cfg)`

```js
const npc = Mesh.loadModel({
  path: "Models/npc.fbx",
  pos: [0, 0, 0],
  scale: 0.01,
  physics: { mass: 60 }
});
```

#### Нормализация пути

`cfg.path` можно задавать синонимами:

* `path`
* `model`
* `asset`
* `url`

В итоге будет использовано `cfg.path`.

---

## Позиция/поворот/масштаб

### Позиция `pos`

Поддерживаемые форматы:

```js
pos: [x, y, z]
pos: { x, y, z }
```

Допустимые алиасы, которые будут приведены к `pos`:

* `position`
* `loc`
* `location`

### Вращение `rot` и масштаб `scale`

Обычно обрабатываются Java‑стороной (`mesh.create`). Если ваш `MeshApiImpl` поддерживает их:

```js
const m = Mesh.loadModel("Models/thing.obj", {
  pos: [0,0,0],
  rot: [0, 90, 0],
  scale: 1.5
});
```

Если в вашей Java‑реализации `rot/scale` ещё не поддерживаются — добавьте их в `MeshApiImpl` (вы уже это делали в патче).

---

## Физика

### Быстрый вариант: `physics: number`

```js
const s = Mesh.sphere({ radius: 0.3, pos: [0, 2, 0], physics: 1 }); // mass=1
```

### Полный вариант: `physics: { ... }`

Поддерживаемые поля (ориентир):

* `mass` (0 = static)
* `enabled`
* `lockRotation`
* `kinematic`
* `friction`
* `restitution`
* `damping: { linear, angular }`
* `collider: { ... }`

```js
const body = Mesh.box({
  size: 1,
  pos: [0, 5, 0],
  physics: {
    mass: 5,
    friction: 0.8,
    restitution: 0.05,
    damping: { linear: 0.1, angular: 0.2 },
    lockRotation: false
  }
});
```

### Легаси‑поля (совместимость)

Mesh нормализует физику из верхнего уровня:

* `mass`
* `enabled` / `physicsEnabled`
* `lockRotation`
* `kinematic`
* `friction`
* `restitution`
* `damping`
* `collider`

То есть так тоже можно:

```js
const b = Mesh.box({ size: 1, pos: [0, 4, 0], mass: 2, friction: 0.9 });
```

---

## Коллайдеры (важно для моделей)

Если вы используете Java‑реализацию `mesh.create({ type:"model", ... })` с авто‑коллайдером:

* `mass <= 0` → `collider.type = "mesh"` (статический треугольный)
* `mass > 0` → `collider.type = "dynamicMesh"` (движущийся треугольный)

Можно переопределить вручную:

```js
const car = Mesh.loadModel({
  path: "Models/car.fbx",
  scale: 0.01,
  physics: {
    mass: 1200,
    collider: { type: "box", halfExtents: [1.2, 0.6, 2.4] }
  }
});
```

---

## Материалы

### Быстрый Unshaded

```js
const red = Mesh.unshadedColor([1, 0, 0, 1]);
const b = Mesh.box({ size: 1, pos: [0, 2, 0], material: red });
```

`material` может быть:

* конфиг материала `{ def, params }`
* или handle материала (если ваш движок так возвращает)

---

## Пакетное создание: `Mesh.many(list)`

```js
const arr = Mesh.many([
  { type: "box", size: 1, pos: [0,2,0], physics: 1 },
  { type: "sphere", radius: 0.5, pos: [2,2,0], physics: 2 },
  { type: "model", path: "Models/house.obj", pos: [10,0,-5], physics: 0 }
]);

arr[0].applyImpulse({ x: 1, y: 0, z: 0 });
```

---

## Builder API

Builder удобен для цепочек.

### Примитив

```js
const b = Mesh.box$()
  .name("box")
  .size(1)
  .pos(0, 5, 0)
  .physics(3, { lockRotation: false })
  .create();
```

### Модель

```js
const npc = Mesh.model$()
  .name("npc")
  .path("Models/npc.fbx")
  .pos([0,0,0])
  .physics(60, { lockRotation: true })
  .create();
```

---

## Методы обёртки (physics sugar)

### `applyImpulse(vec3)`

```js
obj.applyImpulse({ x: 0, y: 6, z: 0 });
```

### `applyCentralForce(vec3)`

```js
obj.applyCentralForce({ x: 10, y: 0, z: 0 });
```

### `velocity()` / `velocity(vec3)`

```js
const v = obj.velocity();
obj.velocity({ x: 0, y: 0, z: 0 });
```

### `position()` / `position(vec3)` / `teleport(vec3)`

```js
const p = obj.position();
obj.position([0, 10, 0]);
obj.teleport({ x: 0, y: 2, z: 0 });
```

### `lockRotation(true|false)`

```js
obj.lockRotation(true);
```

---

## Частые ошибки

### 1) `loadModel: path is required`

Вы вызвали `Mesh.loadModel({ ... })` без `path/model/asset/url`.

### 2) Модель не грузится (FBX/OBJ)

Проверьте что на Java‑стороне подключены loader’ы (например `jme3-plugins` для FBX) и путь верный.

### 3) Нет реакции на physics‑методы

Значит тело не создано:

* нет `physics` в конфиге
* или `physics.enabled=false`
* или Java‑сторона не создала body

---

## Рекомендованные шаблоны

### Дешёвый коллайдер для динамических моделей

```js
const npc = Mesh.loadModel({
  path: "Models/npc.fbx",
  scale: 0.01,
  physics: {
    mass: 80,
    collider: { type: "capsule", radius: 0.35, height: 1.2 },
    lockRotation: true
  }
});
```

### Статичная сцена

```js
Mesh.loadModel({
  path: "Models/level.obj",
  physics: { mass: 0 } // static mesh collider
});
```
