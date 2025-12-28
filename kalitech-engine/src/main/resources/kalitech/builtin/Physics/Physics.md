<!-- Author: Calista Verner -->

# Physics (PHYS) — RootKit-обёртка над `engine.physics()`
Автор: Calista Verner

Этот модуль — тонкий и **стабильный** слой над Java-API физики (PhysicsApiImpl), который:

* нормализует *handle/id* (число, Java-handle, `{id}`, `{bodyId}`, Graal `Value`, и т.п.);
* принимает удобные форматы векторов (массивы, plain-объекты, Java Vec3);
* даёт дружелюбные хелперы (коллайдеры, `ref()` и т.д.);
* старается быть «безопасным»: где это уместно — `try/catch` вокруг host-вызовов.

> Важно: модуль **не меняет** Java API. Он лишь вызывает `engine.physics()` и прокидывает туда данные.

---

## Подключение

Обычно модуль регистрируется в глобальных алиасах как **`PHYS`** (см. `META.globalName`). Тогда в скриптах:

```js
// вариант 1: через глобальный алиас (рекомендовано)
PHYS.debug(true);

// вариант 2: прямой require (если у вас так принято)
const PHYS = require("@builtin/Physics")(engine, K);
```

---

## Ключевая идея: «handle или id — всё равно»

Большинство методов принимают **`handleOrId`**:

* `number` (bodyId)
* Java handle с `.id`/`.bodyId` или методами `id()/getId()/bodyId()/getBodyId()/handle()`
* объект вида `{ id: 12 }` или `{ bodyId: 12 }`
* Graal `Value`, который приводит к числу через `valueOf()`

Модуль всегда приводит всё к **целому `bodyId`** через `bodyIdOf()`.

### `PHYS.idOf(handleOrId)`

```js
const id = PHYS.idOf(bodyHandle); // -> 12
```

### `PHYS.surfaceIdOf(surfaceHandleOrId)`

Нужно, когда вы создаёте физическое тело «по поверхности».

```js
const sid = PHYS.surfaceIdOf(surfaceHandle);
```

---

## Векторы: `PHYS.vec3()`

Принимаются:

* `[x, y, z]`
* `{ x, y, z }`
* `{ x:()=>, y:()=>, z:()=> }`
* Java Vec3 с полями `.x/.y/.z` **или** методами `.x()/.y()/.z()`

```js
PHYS.vec3([1,2,3]);            // -> {x:1,y:2,z:3}
PHYS.vec3({x:1,y:2,z:3});      // -> {x:1,y:2,z:3}
PHYS.vec3(null, 0,1,0);        // -> {x:0,y:1,z:0}
```

> Внутри все физические вызовы получают вектор уже в нормализованном виде.

---

## Создание тела: `PHYS.body(cfg)`

Создаёт `RigidBody` на стороне Java.

**Минимум нужно**: `cfg.surface` (surfaceId или surface-handle).

Поддерживаемые поля (типично):

* `surface` — `number | SurfaceHandle`
* `mass` — число
* `friction`, `restitution`
* `damping: { linear, angular }`
* `kinematic` — boolean
* `lockRotation` — boolean
* `collider` — описание коллайдера

Пример:

```js
const surf = MSH.create({
  type: "capsule",
  radius: 0.35,
  height: 1.8,
  pos: [0, 3, 0]
});

const body = PHYS.body({
  surface: surf,              // можно handle
  mass: 80,
  friction: 0.9,
  restitution: 0.0,
  lockRotation: true,
  collider: PHYS.collider.capsule(0.35, 1.8)
});

// body — Java handle (обычно имеет .id/.surfaceId)
LOG.info("bodyId=" + PHYS.idOf(body));
```

### `PHYS.ensureBodyForSurface(surface, cfg)`

Удобно, когда поверхность уже есть, а тело нужно создать (или «получить существующее»):

```js
const body = PHYS.ensureBodyForSurface(surf, { mass: 0, kinematic: true });
```

> Если тело уже привязано к поверхности — Java может вернуть существующий handle.

---

## Управление телом (трансформации)

### `PHYS.position(handleOrId)` → Vec3

### `PHYS.position(handleOrId, vec3)` → warp

```js
const p = PHYS.position(body);
LOG.info("pos=" + JSON.stringify(p));

PHYS.position(body, [10, 2, -5]); // сеттер = warp
```

### `PHYS.warp(handleOrId, pos)`

Жёстко телепортирует тело.

```js
PHYS.warp(body, { x: 0, y: 5, z: 0 });
```

### `PHYS.velocity(handleOrId)` / `PHYS.velocity(handleOrId, v)`

```js
const v = PHYS.velocity(body);
PHYS.velocity(body, [0, 0, 0]);
```

### `PHYS.yaw(handleOrId, yawRad)`

Поворачивает тело по yaw (в радианах):

```js
PHYS.yaw(body, Math.PI * 0.5);
```

---

## Силы и импульсы

### `PHYS.applyImpulse(handleOrId, impulse)`

```js
PHYS.applyImpulse(body, [0, 6.5, 0]);
```

> Импульс — мгновенное изменение скорости.

### Дополнительные методы (если проброшены Java-API)

Внутри модуля есть обёртки на:

* `applyCentralForce(handleOrId, force)`
* `applyTorque(handleOrId, torque)`
* `angularVelocity(handleOrId)` / `angularVelocity(handleOrId, v)`
* `clearForces(handleOrId)`
* `collisionGroups(handleOrId, group, mask)`

Если они экспортированы из `engine.physics()`, используйте так:

```js
PHYS.applyCentralForce(body, [0, 0, 12]);
PHYS.applyTorque(body, [0, 2.0, 0]);
PHYS.angularVelocity(body, [0, 0.4, 0]);
PHYS.clearForces(body);

// группы коллизий (пример)
PHYS.collisionGroups(body, 0x0002, 0xFFFF);
```

---

## Флаги

### `PHYS.lockRotation(handleOrId, bool)`

```js
PHYS.lockRotation(body, true);
```

---

## Запросы мира: Raycast

Модуль даёт «тонкие» прокси на Java-методы:

* `PHYS.raycast(cfg)`
* `PHYS.raycastEx(cfg)`
* `PHYS.raycastAll(cfg)`

### Базовый `raycast({ from, to })`

```js
const hit = PHYS.raycast({
  from: [0, 2, 0],
  to:   [0, -10, 0]
});

if (hit) {
  LOG.info("hit=" + JSON.stringify(hit));
}
```

### Практика: «земля под ногами»

```js
function groundCheck(bodyId) {
  const p = PHYS.position(bodyId);
  const from = [p.x, p.y + 0.2, p.z];
  const to   = [p.x, p.y - 0.8, p.z];

  const hit = PHYS.raycast({ from, to });
  return !!hit;
}
```

### Практика: «камера не пролезает сквозь стены»

Идея: луч от **точки интереса** (например, голова игрока) к желаемой позиции камеры. Если есть хит — приблизить камеру.

```js
function clampCameraByRay(targetPos, desiredCamPos) {
  const hit = PHYS.raycast({ from: targetPos, to: desiredCamPos });
  if (!hit) return desiredCamPos;

  // В зависимости от формата PhysicsRayHit на Java стороне,
  // обычно есть точка столкновения hit.point / hit.hitPos / hit.position.
  // Ниже — псевдо-обработка:
  const hp = hit.point || hit.position || hit.hitPos;
  if (!hp) return desiredCamPos;

  // небольшой отступ от стены
  const pad = 0.12;
  const dir = {
    x: desiredCamPos[0] - targetPos[0],
    y: desiredCamPos[1] - targetPos[1],
    z: desiredCamPos[2] - targetPos[2]
  };
  const len = Math.hypot(dir.x, dir.y, dir.z) || 1;
  dir.x /= len; dir.y /= len; dir.z /= len;

  return [hp.x - dir.x * pad, hp.y - dir.y * pad, hp.z - dir.z * pad];
}
```

> Именно такие проверки вам пригодятся для AAA-камеры: third-person, free, и даже first (чтобы не «влезать» в геометрию).

---

## Быстрые пресеты коллайдеров: `PHYS.collider.*`

Коллайдеры — чистый JSON, который уходит в Java.

```js
const c1 = PHYS.collider.box([0.5, 1.0, 0.5]);
const c2 = PHYS.collider.sphere(0.75);
const c3 = PHYS.collider.capsule(0.35, 1.8);
const c4 = PHYS.collider.cylinder(0.5, 1.0);
const c5 = PHYS.collider.mesh();
const c6 = PHYS.collider.dynamicMesh();
```

Использование:

```js
PHYS.body({
  surface: surf,
  mass: 10,
  collider: PHYS.collider.box([1,1,1])
});
```

---

## Удобная привязка: `PHYS.ref(handleOrId)`

`ref()` возвращает **замороженный** JS-объект, привязанный к конкретному `bodyId`.

```js
const b = PHYS.ref(body);

b.velocity([0,0,0]);
b.applyImpulse([0, 5, 0]);

const p = b.position();
LOG.info("p=" + JSON.stringify(p));

b.lockRotation(true);

// удалить
b.remove();
```

Плюсы `ref()`:

* меньше шума с `handleOrId`;
* удобные короткие вызовы в геймплее;
* безопасно: `id` фиксируется и не «плывёт».

---

## Debug и гравитация

### `PHYS.debug(true/false)`

```js
PHYS.debug(true);
```

Если Java-слой поддерживает debug-рендер/логирование — включится.

### `PHYS.gravity(vec3)`

```js
PHYS.gravity([0, -9.81, 0]);
```

---

## Типичные паттерны использования в Kalitech

### 1) Спавн физического объекта

```js
const box = MSH.create({ type: "box", size: 1.0, pos: [0, 3, 0] });

const b = PHYS.ensureBodyForSurface(box, {
  mass: 25,
  friction: 0.85,
  collider: PHYS.collider.box([0.5,0.5,0.5])
});

PHYS.applyImpulse(b, [1, 0, 0]);
```

### 2) «Кинематик» (ручное перемещение)

```js
const body = PHYS.body({
  surface: surf,
  mass: 0,
  kinematic: true,
  collider: PHYS.collider.capsule(0.35, 1.8)
});

// каждую обнову:
PHYS.warp(body, [x, y, z]);
```

### 3) Защита от ошибок в скриптах

Скрипты должны считать, что:

* любой host-вызов может бросить исключение (например, тело уже удалено);
* `bodyIdOf()` вернёт 0, если handle «не распознан».

Рекомендуемый стиль:

```js
function safeImpulse(h, v) {
  try {
    if (PHYS.idOf(h) > 0) PHYS.applyImpulse(h, v);
  } catch (e) {
    LOG.warn("impulse failed: " + (e && e.message ? e.message : e));
  }
}
```

---

## Справочник API (коротко)

### Core

* `PHYS.body(cfg)`
* `PHYS.ensureBodyForSurface(surface, cfg)`
* `PHYS.remove(handleOrId)`

### Transforms

* `PHYS.position(handleOrId)` / `PHYS.position(handleOrId, pos)`
* `PHYS.warp(handleOrId, pos)`
* `PHYS.velocity(handleOrId)` / `PHYS.velocity(handleOrId, v)`
* `PHYS.yaw(handleOrId, yawRad)`

### Forces / Flags

* `PHYS.applyImpulse(handleOrId, impulse)`
* `PHYS.lockRotation(handleOrId, bool)`
* *(если доступно)* `applyCentralForce`, `applyTorque`, `angularVelocity`, `clearForces`, `collisionGroups`

### World queries

* `PHYS.raycast(cfg)`
* *(если доступно)* `raycastEx(cfg)`, `raycastAll(cfg)`

### Utils

* `PHYS.vec3(v)`
* `PHYS.idOf(handleOrId)`
* `PHYS.surfaceIdOf(surfaceHandleOrId)`
* `PHYS.collider.*`
* `PHYS.ref(handleOrId)`

### Debug

* `PHYS.debug(bool)`
* `PHYS.gravity(vec3)`

---

## Советы для AAA-камеры

1. **Third-person**: луч `target(head)` → `desiredCam` и поджимать дистанцию при препятствии.
2. **First-person**: иногда полезно делать короткий луч вперёд, чтобы не «протыкать» тонкие стены при рывках.
3. **Free-cam**: опционально можно включать коллизии/скольжение (если реализуете) — но базовый режим может оставаться «без физики».
4. Не забывайте про **pad** (отступ от стены), иначе камера будет дрожать на поверхности.

Если хочешь — я допишу готовый модуль `CameraCollisionSolver.js`, который подключается в `CameraOrchestrator` как пост-проход и использует `PHYS.raycast` для стабилизации всех режимов (особенно third/top).
