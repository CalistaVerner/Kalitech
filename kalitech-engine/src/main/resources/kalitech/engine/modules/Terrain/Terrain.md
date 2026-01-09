# Terrain JS API (v2) — Kalitech

Этот гайд описывает **удобный декларативный JS API** для террейна и совместимые низкоуровневые вызовы.

> В новой схеме **террейн создаётся через `ENGINE.terrain`** (а не глобальный `TERR`).

* Поддержка: **plane / quad / heightmap / heights / noise(perlin/ridged)**
* Декларативный стиль: один вход `ENGINE.terrain.create({...})`
* Безопасный interop: генераторы высот возвращают **Float32Array**
* Валидация размеров JME: `size` и `patchSize` должны быть **(2^k + 1)**

---

## Быстрый старт

```js
// Получить TERR-API через ENGINE
const TERR = ENGINE.terrain;

const ground = TERR.create({
    name: "ground",
    kind: "plane",
    plane: {w: 1000, h: 1000},
    material: MAT.getMaterial("unshaded.grass"),
    uv: {scale: [50, 50]},
    attach: true,
    physics: {mass: 0, collider: {type: "mesh"}, friction: 1.0}
});
```

`ENGINE.terrain.create()` возвращает:

* **SurfaceHandle** (или обёртку `{ surface, bodyId, body }` если была физика)

---

## Декларативный API: `ENGINE.terrain.create(cfg)`

### Общие поля

| Поле       |         Тип | По умолчанию | Описание                                                                  |
|------------|------------:|-------------:|---------------------------------------------------------------------------|
| `name`     |      string |            — | Имя объекта/узла (если поддерживается на стороне Java)                    |
| `kind`     |      string |  `"terrain"` | Тип создания: `plane`, `quad`, `heightmap`, `heights`, `noise`            |
| `attach`   |     boolean |       `true` | Прикрепить к сцене (если поддерживается вашим Java API)                   |
| `material` |         any |            — | Материал (handle/объект как у вашего MAT API)                             |
| `uv`       |      object |            — | UV настройки (см. ниже)                                                   |
| `lod`      |      object |            — | LOD настройки TerrainQuad                                                 |
| `physics`  | object/null |            — | Физика: `mass`, `collider`, `friction`, ...                               |
| `scale`    |      object |            — | Масштаб: `{ xz, y }` (или старые поля `xzScale`, `yScale`, `heightScale`) |

### Масштаб

```js
scale: { xz: 2.0, y: 40.0 }
```

* `xz` — масштаб по XZ (в ширину)
* `y` — вертикальный масштаб высот

> Для `kind: "noise"` значение `y` обычно используется как **амплитуда** при генерации.

---

## Размеры TerrainQuad (важно)

В JME TerrainQuad размеры должны быть:

* `size = 2^k + 1` (например: `129`, `257`, `513`, `1025`)
* `patchSize = 2^k + 1` и `patchSize <= size`

Если размер невалидный — API бросит ошибку **раньше**, чем вызовет Java.

---

## Kind: plane

Создаёт плоскость (обычно Mesh/Surface), удобна для «земли без рельефа».

```js
const TERR = ENGINE.terrain;

const g = TERR.create({
    kind: "plane",
    plane: {w: 1000, h: 1000},
    material: MAT.getMaterial("unshaded.grass"),
    uv: {scale: [50, 50]},
    physics: {mass: 0, collider: {type: "mesh"}}
});
```

Поля `plane` (типично):

* `w`, `h` — размеры
* `size` — сегментация (если поддерживается)

---

## Kind: quad

Быстрый вариант простой сетки, когда TerrainQuad не нужен.

```js
const TERR = ENGINE.terrain;

const q = TERR.create({
    kind: "quad",
    quad: {w: 200, h: 200},
    material: MAT.getMaterial("unshaded.grass"),
    physics: {mass: 0, collider: {type: "mesh"}}
});
```

---

## Kind: heightmap

Создание TerrainQuad на основе **heightmap ассета**.

```js
const TERR = ENGINE.terrain;

const t = TERR.create({
    kind: "heightmap",
    terrain: {
        heightmap: "Textures/heightmaps/hm.png",
        size: 513,
        patchSize: 65
    },
    scale: {xz: 2.0, y: 40.0},
    material: MAT.getMaterial("unshaded.grass"),
    lod: {enabled: true},
    physics: {mass: 0, collider: {type: "mesh"}}
});
```

`terrain` обычно принимает:

* `heightmap` — путь к ассету
* `size`, `patchSize` — размеры TerrainQuad

---

## Kind: heights

TerrainQuad из **массива высот** (Float32Array или JS array).

```js
const TERR = ENGINE.terrain;

const heights = TERR.heights.perlin({
  size: 513,
  seed: 1337,
  scale: 120,
  octaves: 6,
  persistence: 0.5,
  lacunarity: 2.0,
  normalize: true
});

const t = TERR.create({
  kind: "heights",
  terrain: { size: 513, patchSize: 65 },
  heights,
  scale: { xz: 2.0, y: 40.0 },
  material: MAT.getMaterial("unshaded.grass"),
  physics: { mass: 0, collider: { type: "mesh" } }
});
```

### Авто-вывод `size` из массива

Если `terrain.size` не указан, API попробует вывести `size` из длины `heights`:

* `size = sqrt(length)`
* проверка: `size*size === length`

Если длина не квадрат — нужно явно указать `terrain.size`.

---

## Kind: noise

Супер-удобный режим: **создать террейн и сгенерировать высоты внутри**.

```js
const TERR = ENGINE.terrain;

const t = TERR.create({
  kind: "noise",
  terrain: { size: 513, patchSize: 65 },
  noise: {
    type: "ridged",      // "perlin" | "ridged"
    seed: 42,
    scale: 150,
    octaves: 5,
    normalize: true
  },
  scale: { xz: 2.0, y: 60.0 },
  material: MAT.getMaterial("unshaded.grass"),
  physics: { mass: 0, collider: { type: "mesh" } }
});
```

### Как применяется `normalize`

* если `normalize: true`, высоты из генератора ожидаются в `[0..1]`
* затем они мапятся в `[-1..1]` и умножаются на `scale.y`

---

## Генераторы высот: `ENGINE.terrain.heights.*`

Все генераторы возвращают **Float32Array** — это важно для Graal interop.

### Perlin

```js
const TERR = ENGINE.terrain;

const h = TERR.heights.perlin({
  size: 513,
  seed: 1337,
  scale: 120,
  octaves: 6,
  persistence: 0.5,
  lacunarity: 2.0,
  normalize: true
});
```

### Ridged

```js
const TERR = ENGINE.terrain;

const h = TERR.heights.ridged({
  size: 513,
  seed: 1337,
  scale: 140,
  octaves: 5,
  normalize: true
});
```

### Утилиты

```js
const TERR = ENGINE.terrain;

const f32 = TERR.heights.toF32(anyArrayLike);
const size = TERR.heights.sizeOf(f32); // 0 если не квадрат
```

---

## Низкоуровневые методы (совместимость)

Если тебе нужно напрямую вызывать базовый Java API — он доступен и в v2:

* `ENGINE.terrain.terrain(cfg)` — TerrainQuad из heightmap/параметров
* `ENGINE.terrain.terrainHeights(cfg)` — TerrainQuad из массива высот
* `ENGINE.terrain.plane(cfg)` / `ENGINE.terrain.quad(cfg)`
* `ENGINE.terrain.physics(surface, cfg)` — привязать физику
* `ENGINE.terrain.material(surface, mat)`
* `ENGINE.terrain.uv(surface, cfg)`
* `ENGINE.terrain.lod(surface, cfg)`
* `ENGINE.terrain.scale(surface, xzScale, { yScale })`

Пример:

```js
const TERR = ENGINE.terrain;

const t = TERR.terrainHeights({
    name: "t",
    size: 513,
    patchSize: 65,
    heights,
    heightScale: 40,
    xzScale: 2,
    physics: {mass: 0, collider: {type: "mesh"}}
});
```

---

## Query API (высота и нормаль)

```js
const TERR = ENGINE.terrain;

const y = TERR.heightAt(terrainSurface, x, z, true);   // world=true
const n = TERR.normalAt(terrainSurface, x, z, true);
```

* `world=true` — координаты/результат в мире (если поддерживается)

---

## Edit API (скульпт/правка)

### Поставить новый heightmap (массив высот)

```js
const TERR = ENGINE.terrain;

TERR.setHeightmap(terrainSurface, heights, 513, true);
```

или object-формат:

```js
const TERR = ENGINE.terrain;

TERR.setHeightmap(terrainSurface, {
    heights,
    size: 513,
    rebuild: true
});
```

### Точечная правка

```js
const TERR = ENGINE.terrain;

TERR.setHeight(terrainSurface, x, z, 10.0, true);
TERR.adjustHeight(terrainSurface, x, z, +0.25, true);
```

### Rebuild

```js
const TERR = ENGINE.terrain;

TERR.rebuild(terrainSurface);
```

---

## UV / LOD (типично)

### UV

```js
const TERR = ENGINE.terrain;

TERR.uv(terrainSurface, { scale: [50, 50] });
```

### LOD

```js
const TERR = ENGINE.terrain;

TERR.lod(terrainSurface, {
    enabled: true,
    // любые параметры, которые поддерживает ваша Java реализация
});
```

---

## Physics (типично)

```js
const TERR = ENGINE.terrain;

const t = TERR.create({
  kind: "heights",
  terrain: { size: 513, patchSize: 65 },
  heights,
  physics: {
    mass: 0,
    collider: { type: "mesh" },
    friction: 1.0
  }
});
```

> В v2 предусмотрена защита от дублей: если тело уже привязано к surface, новое не создаётся.

---

## FAQ / Ошибки

### «terrainHeights: heights length must be size*size»

Причина: длина массива `heights` не совпадает с `size*size`.

Решения:

* убедись, что `size` корректен (например `513`)
* если `size` не указываешь — передай массив длиной **квадрат** (`257*257`, `513*513`)

### «Unsupported operation Value.getArraySize() … float[]»

Причина: Java вернула `float[]`, а JS попробовал обращаться к нему как к массиву.

Решение в v2: генераторы высот и `heightmap()` всегда приводятся к **Float32Array**.

---

## Соглашения

* `kind: "heights"` — ты уже подготовил массив высот
* `kind: "noise"` — TERR сам сгенерирует высоты и сразу создаст террейн
* `scale.y` — вертикальная амплитуда/масштаб высот

---

## Версия

* TERR JS: **v2.0.0**