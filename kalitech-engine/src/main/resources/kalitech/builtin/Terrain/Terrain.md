# Terrain (TERR)

JS модуль-обёртка над `engine.terrain()` (Java: `TerrainApiImpl`).

## Быстрый старт

### Heightmap terrain + физика

```js
const t = TERR.terrain({
  name: "world-terrain",
  heightmap: "Textures/heightmaps/hm.png",
  patchSize: 65,
  size: 513,
  heightScale: 2.0,
  xzScale: 2.0,
  material: MAT.getMaterial("unshaded.grass"),
  attach: true,

  // если есть: создаём static body и возвращаем {surface, bodyId, body}
  physics: {
    mass: 0,
    kinematic: true,
    collider: { type: "mesh" },
    friction: 1.0,
    restitution: 0.0,
  }
});

// одинаково работает и если вернулся handle, и если {surface,body}
const surface = t.surface ? t.surface : t;
```

### Procedural terrain from heights

```js
const size = 513;
const heights = new Float32Array(size * size);
// ... fill heights

const t = TERR.terrainHeights({
  name: "proc",
  size,
  patchSize: 65,
  xzScale: 2.0,
  yScale: 1.0,
  heights,
  attach: true,
  physics: { collider: { type: "mesh" } }
});
```

### Plane/Quad ground

```js
const ground = TERR.plane({
  w: 1000, h: 1000,
  material: MAT.getMaterial("unshaded.grass"),
  attach: true,
  physics: { mass: 0, collider: { type: "box" }, friction: 1.0 }
});
```

## Операции

### Материал

```js
TERR.material(surface, MAT.getMaterial("unshaded.sand"));
```

### LOD

```js
TERR.lod(surface, { enabled: true });
// отключить
TERR.lod(surface, { enabled: false });
```

### Scale

```js
TERR.scale(surface, 4.0, { yScale: 1.0 });
```

### Height / Normal queries

```js
const y = TERR.heightAt(surface, worldX, worldZ, true);
const n = TERR.normalAt(surface, worldX, worldZ, true); // {x,y,z}
```

## Физика

`TERR` **не создаёт дубликаты тел**.

- Если ты передал `physics:` в `terrain/terrainHeights/plane/quad`, модуль:
  1) создаст тело через `terrain.physics(surface,cfg)`
  2) **резолвит `bodyId` без создания второго тела** через:
     - `engine.surface().attachedBody(surfaceId)`
     - `engine.physics().bodyOfSurface(surfaceId)`
  3) если доступен `PHYS.ref(bodyId)`, вернёт `body` (ref-объект)

Также можно вызвать напрямую:

```js
const { bodyId, body } = TERR.physics(surface, { mass: 0, collider: { type: "mesh" } });
```

## Интеграция с коллизиями

С новой физикой (begin/stay/end) слушай через `PHYS.onCollision*` или напрямую через `EVENTS`:

```js
PHYS.onCollisionBegin({ surfaceId: surface.id() }, (e) => {
  // e.a / e.b / e.contact
});
```
