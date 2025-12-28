<!-- Author: Calista Verner -->

# Kalitech: Materials Registry API RootKit-обёртка над `engine.material()`
Автор: Calista Verner

`MaterialsRegistry` — встроенный реестр материалов Kalitech. Он уже включён в RootKit: ничего дополнительно подключать, устанавливать или регистрировать не нужно.

Реестр даёт единый способ:
- получать материалы по имени из materials JSON;
- применять переопределения параметров (overrides) поверх базового материала;
- кэшировать результаты (включая варианты с overrides) для производительности;
- быстро делать «пресеты» (переиспользуемые фабрики материалов).

---

## Доступ к реестру

Реестр доступен тремя способами (в зависимости от настроек bootstrap):

### 1) Через require
```js
const M = require("@builtin/MaterialsRegistry")(K);
````

### 2) Через globalThis.materials (если `exposeGlobals=true`)

```js
materials.get("box");
```

### 3) Через алиас globalThis.MAT (если `exposeGlobals=true`)

```js
MAT.get("box");
```

Обычно в игровых скриптах используют `M`.

---

## Термины и типы

### MaterialCfg

Описание материала (то, что обычно создаётся на стороне движка через `engine.material().create(cfg)`).

```ts
interface MaterialCfg {
  def: string;                    // путь к .j3md
  params?: Record<string, unknown>; // параметры материала
  scales?: Record<string, unknown>; // дополнительные масштабы/настройки (если применимо)
}
```

### HostMaterial

Объект материала движка (jME `Material`). В JS его следует воспринимать как opaque-объект: вы передаёте его в `setMaterial`, но не обязаны знать внутреннюю структуру.

### MaterialHandle

Хэндл, возвращаемый хостом. Форма намеренно «мягкая» для совместимости:

* может иметь `id` (число или функция),
* может иметь `__material()` и другие поля.

---

## Переопределения (MaterialOverrides)

Overrides применяются поверх базового определения из реестра.

Поддерживаются формы:

### 1) Без overrides

* `null` / `undefined`

### 2) Полная форма

```js
{
  params: { /* ... */ },
  scales: { /* ... */ }
}
```

`params` и `scales` могут быть `null` — это явный сброс/обнуление соответствующей части (поведение зависит от реализации реестра/движка).

### 3) Короткая форма

Любой объект трактуется как `params`:

```js
MAT.get("box", { Color: [1,0,0,1], Roughness: 0.5 })
```

---

## Методы API

### getMaterial(name, overrides?)

Возвращает `HostMaterial` по имени. Результат кэшируется.

Пример без overrides:

```js
const mat = MAT.getMaterial("box");
geom.setMaterial(mat);
```

Короткая форма overrides:

```js
geom.setMaterial(MAT.getMaterial("box", { Color: [1, 0, 0, 1] }));
```

Полная форма overrides:

```js
geom.setMaterial(MAT.getMaterial("box", {
  params: { Color: [0.2, 0.7, 1.0, 1.0], Roughness: 0.35 },
  scales: { NormalMap: 1.5 }
}));
```

---

### get(name, overrides?)

Сахар: алиас `getMaterial(name, overrides)`.

```js
geom.setMaterial(MAT.get("box"));
geom.setMaterial(MAT.get("box", { Color: [0, 1, 0, 1] }));
```

---

### getHandle(name, overrides?)

Возвращает `MaterialHandle` по имени. Полезно, когда требуется идентификатор/хэндл.

```js
const h = MAT.getHandle("box");
```

Если нужно безопасно извлечь id:

```js
function matId(h) {
  if (!h) return 0;
  const id = h.id;
  if (typeof id === "function") return (id.call(h) | 0);
  if (typeof id === "number") return (id | 0);
  return 0;
}

const hid = matId(MAT.getHandle("box", { Color: [1,0,0,1] }));
LOG.info("material id=" + hid);
```

---

### handle(name, overrides?)

Сахар: алиас `getHandle(name, overrides)`.

```js
const h = MAT.handle("box");
```

---

### preset(name, overrides?)

Создаёт пресет — вызываемую функцию-фабрику:

* вызов `presetFn(overrides?)` возвращает `HostMaterial`;
* `presetFn.handle(overrides?)` возвращает `MaterialHandle`.

Пример:

```js
const RedBox = MAT.preset("box", { Color: [1, 0, 0, 1] });

geomA.setMaterial(RedBox());
geomB.setMaterial(RedBox());

const h = RedBox.handle();
```

Если нужно доопределить params при вызове:

```js
const Metal = MAT.preset("box", { Metallic: 1.0, Roughness: 0.2 });
geom.setMaterial(Metal({ Color: [0.9, 0.9, 1.0, 1.0] }));
```

---

### params(name, params?)

Удобный метод для частого случая: переопределить только `params`.

```js
geom.setMaterial(MAT.params("box", { Color: [0, 0.6, 1, 1] }));
```

Сброс params:

```js
geom.setMaterial(MAT.params("box", null));
```

---

### configure(cfg)

Настройка поведения реестра.

Параметры:

* `overrideCache` (по умолчанию `true`) — кэшировать материалы, созданные с overrides;
* `overrideCacheMax` (по умолчанию `256`) — максимум записей кэша overrides (простая FIFO).

Примеры:

```js
MAT.configure({ overrideCache: false });
```

```js
MAT.configure({ overrideCache: true, overrideCacheMax: 1024 });
```

Практика:

* Если overrides повторяются (несколько типовых вариантов) — кэш должен быть включён.
* Если вы генерируете тысячи уникальных overrides (например, уникальные цвета на каждый объект) — кэш может разрастаться; ограничьте `overrideCacheMax` или отключите `overrideCache`.

---

### reload()

Очищает кэши и заставляет перечитать materials JSON при следующем доступе.

```js
const ok = MAT.reload();
LOG.info("materials reload=" + (ok ? "ok" : "fail"));
```

---

### keys()

Возвращает список имён материалов, известных реестру.

```js
const names = MAT.keys();
for (let i = 0; i < names.length; i++) {
  LOG.info("material: " + names[i]);
}
```

---

## Практические паттерны

### 1) Базовый материал + редкие варианты

```js
const Rock = MAT.get("rock");
const RockWet = MAT.get("rock", { Roughness: 0.02 });

geom1.setMaterial(Rock);
geom2.setMaterial(RockWet);
```

### 2) Семейства через presets

```js
const BoxBase = MAT.preset("box");
const BoxRed  = MAT.preset("box", { Color: [1, 0, 0, 1] });

geomA.setMaterial(BoxBase());
geomB.setMaterial(BoxRed());
```

---

## Ограничения и договорённости

* Материалы адресуются по имени, которое определено в materials JSON. Полный список доступных имён можно получить через `MAT.keys()`.
* `HostMaterial` и `MaterialHandle` являются объектами хоста. Не следует полагаться на их внутренние поля, кроме заявленных в типах, и даже их использовать с проверками.
* При активном использовании overrides контролируйте размер кэша через `configure()`.

```
```
