# Mesh (builtin) — гайд по использованию

> **Mesh** — встроенный JS‑оркестратор, который **декорирует `ENGINE.mesh()`** и сохраняет **объектную модель**.
>
> Главная идея: `ENGINE.mesh.create(cfg)` возвращает **обёрнутый SurfaceHandle** (Proxy), на который можно вызывать
> методы физики **напрямую**:
>
> ```js
> const m = ENGINE.mesh.create({ type: "box", size: 1, pos: [0,2,0], physics: { mass: 5 } });
> m.warp(10, 5, 76);
> m.applyImpulse([0, 6, 0]);
> ```
>
> Важно: обёртка **не создаёт физическое тело сама**. Тело создаётся на Java‑стороне при
`mesh.create({ physics: ... })`.
> Обёртка работает через **bodyId**, который берётся из `ENGINE.surface().attachedBody(surfaceId)`.

---

## Доступ

В новой схеме модуль живёт в **ENGINE**:

```js
// декорированный API
const Mesh = ENGINE.mesh;
```

Если у вас включён `exposeGlobals`, могут быть алиасы, но каноничный путь — **только через ENGINE**.

---

## Быстрый старт

### Куб с физикой

```js
const box = ENGINE.mesh.create({
    type: "box",
    name: "box01",
    size: 2,
    pos: [0, 5, 0],
    material: MAT.getMaterial("box"),
    physics: {mass: 10, lockRotation: false}
});

box.applyImpulse({ x: 0, y: 6, z: 0 });
```

### Статичный объект

```js
const ground = ENGINE.mesh.create({
    type: "box",
    name: "ground",
    hx: 50, hy: 1, hz: 50,
    pos: [0, -1, 0],
    physics: {mass: 0}
});
```

---

## Создание объектов

### `ENGINE.mesh.create(cfg)`

Создаёт объект указанного типа через Java:

```js
engine.mesh().create(cfg)
```

и возвращает **обёртку** (Proxy) над SurfaceHandle.

Пример:

```js
const s = ENGINE.mesh.create({
    type: "sphere",
    radius: 0.5,
    pos: [1, 3, 0],
    physics: {mass: 1}
});
```

Ожидаемые `type` (ориентир):

* `box`
* `sphere`
* `cylinder`
* `capsule`
* `model`

> Конкретный набор `type` определяется вашей Java‑реализацией `MeshApiImpl`.

---

## Пакетное создание

Если ваша Java‑реализация поддерживает `many(list)`, он будет проксирован и вернёт массив обёрток:

```js
const arr = ENGINE.mesh.many([
    {type: "box", size: 1, pos: [0, 2, 0], physics: {mass: 1}},
    {type: "sphere", radius: 0.5, pos: [2, 2, 0], physics: {mass: 2}}
]);

arr[0].applyImpulse([1, 0, 0]);
```

---

## Builder API (цепочки)

Mesh предоставляет builder прямо на `ENGINE.mesh`:

* `box$()` / `cube$()`
* `sphere$()`
* `cylinder$()`
* `capsule$()`
* `model$()`

### Примитив

```js
const b = ENGINE.mesh
    .box$()
    .size(1)
    .name("box")
    .pos(0, 5, 0)
    .material(MAT.getMaterial("box"))
    .physics(3, {lockRotation: false})
    .create();

b.applyImpulse([0, 6, 0]);
```

### Модель

```js
const npc = ENGINE.mesh
    .model$()
    .name("npc")
    .path("Models/npc.fbx")
    .pos([0, 0, 0])
    .physics(60, {lockRotation: true})
    .create();
```

Builder пишет конфиг в поля `cfg` (в итоге уходит в `ENGINE.mesh.create(cfg)`):

* `.name(string)`
* `.pos(x,y,z)` или `.pos([x,y,z])` или `.pos({x,y,z})`
* `.size(number)`
* `.radius(number)` / `.height(number)` (если вы добавляете в cfg напрямую через `.cfg()`)
* `.material(any)`
* `.path(string)` / `.model(string)`
* `.physics(mass, opts)` → записывает `cfg.physics = { mass, ... }`

---

## Физика

### Ключевой контракт

* Физика создаётся **только** на Java‑стороне (через `cfg.physics`).
* JS‑обёртка не создаёт второе тело.
* Доступ к физике идёт через **`ENGINE.physics()`**.
* `bodyId` резолвится так:

```js
const sid = surface.id();
const bid = ENGINE.surface().attachedBody(sid);
```

### Быстрый формат

```js
const s = ENGINE.mesh.create({
    type: "sphere",
    radius: 0.3,
    pos: [0, 2, 0],
    physics: 1 // => mass=1
});
```

### Полный формат

```js
const obj = ENGINE.mesh.create({
    type: "box",
    size: 1,
    pos: [0, 5, 0],
    physics: {
        mass: 5,
        friction: 0.8,
        restitution: 0.05,
        damping: {linear: 0.1, angular: 0.2},
        lockRotation: false,
        kinematic: false,
        collider: {type: "box", halfExtents: [0.5, 0.5, 0.5]}
    }
});
```

> Поля `physics` должны соответствовать вашему Java контракту `PhysicsApiImpl`.

---

## Методы обёртки (object model)

Обёрнутый mesh‑объект — это SurfaceHandle + «sugar» поверх **id‑based** физики:

### Трансформации

* `warp(x,y,z)` или `warp([x,y,z])` или `warp({x,y,z})`
* `position()` / `position(vec3)`  *(setter использует warp)*
* `velocity()` / `velocity(vec3)`
* `yaw(yawRad)`

### Силы

* `applyImpulse(vec3)`
* *(если поддерживается Java API)* `applyCentralForce(vec3)`
* *(если поддерживается Java API)* `applyTorque(vec3)`
* *(если поддерживается Java API)* `angularVelocity()` / `angularVelocity(vec3)`
* *(если поддерживается Java API)* `clearForces()`

### Флаги

* `lockRotation(bool)`
* *(если поддерживается Java API)* `setKinematic(bool)`
* *(если поддерживается Java API)* `collisionGroups(group, mask)`

### Идентификаторы

* `surfaceId()`
* `bodyId()`

---

## Материалы

`cfg.material` может быть:

* handle материала (как возвращает ваш MAT API)
* объект‑конфиг материала `{ def, params }` (если ваш движок это поддерживает)

Пример:

```js
const b = ENGINE.mesh.create({
    type: "box",
    size: 1,
    pos: [0, 2, 0],
    material: MAT.getMaterial("box"),
    physics: {mass: 2}
});
```

---

## Частые ошибки

### 1) `surface has no physics body (bodyId=0)`

Значит тело не создано на Java‑стороне:

* вы не передали `cfg.physics`
* или `physics.enabled=false`
* или Java `MeshApiImpl` не создаёт body для этого типа/конфига

Решение: убедиться, что `cfg.physics` реально обрабатывается вашей Java реализацией.

### 2) Модель не грузится (FBX/OBJ)

Проверьте:

* подключены ли loader’ы на Java стороне
* путь `cfg.path` корректен

---

## Рекомендованные шаблоны

### Дешёвый коллайдер для динамических моделей

```js
const npc = ENGINE.mesh.create({
    type: "model",
    path: "Models/npc.fbx",
    scale: 0.01,
    physics: {
        mass: 80,
        collider: {type: "capsule", radius: 0.35, height: 1.2},
        lockRotation: true
    }
});
```

### Статичная сцена

```js
ENGINE.mesh.create({
    type: "model",
    path: "Models/level.obj",
    physics: {mass: 0, collider: {type: "mesh"}}
});
```

---

## Версия

* Mesh JS: **v1.x** (ENGINE.mesh RootKit)