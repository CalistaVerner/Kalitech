# ⚡ Kalitech AEGIS Runtime Architecture

> **AEGIS** — *Adaptive Execution & Guarded Isolation System*
>
> Внутреннее имя архитектуры исполнения Kalitech Engine.
>
> Цель AEGIS — дать **AAA‑уровень производительности**, **жёсткую изоляцию**, **честный профайлинг** и **нулевую магию**, не ломая JS API и не усложняя жизнь разработчику.

---

## 🧠 Философия (почему AEGIS существует)

Большинство движков выбирают один из путей:

* ❌ *«Всё в одном потоке»* — просто, но плохо масштабируется
* ❌ *«Поток на каждую систему»* — красиво на бумаге, убивает CPU
* ❌ *«Невидимая магия»* — FPS падает, но почему — неизвестно

**AEGIS выбирает другой путь:**

> 🔥 *Ограниченная параллельность + жёсткие бюджеты + абсолютная наблюдаемость*

Ты всегда знаешь:

* **кто** тратит время
* **где** именно кадр провалился
* **почему** это произошло
* и **что с этим делать**

---

## 🧩 Основные принципы AEGIS

### 1️⃣ Один runtime на мир — по умолчанию

* `world` runtime — **основной пайплайн**
* общий cache, быстрый старт, минимум аллокаций
* всё, что *может* жить вместе — живёт вместе

```text
World
 └── Runtime: world
```

---

### 2️⃣ Изоляция — по требованию, а не всегда

Отдельные runtime создаются **только если нужно**:

* AI
* pathfinding
* tools
* UI
* hotreload
* sandbox

```text
RuntimePool
 ├── world        (pinned)
 ├── sys.ai.lane0
 ├── sys.ai.lane1
 ├── tools
 └── hotreload
```

> ⚠️ Runtime — дорогой ресурс. AEGIS **не создаёт их без причины**.

---

### 3️⃣ Worker Lanes, а не «поток на систему»

AEGIS использует **striped scheduler**:

* фиксированное число worker‑потоков (≈ CPU cores)
* каждая система **прикрепляется к lane**
* lane = single‑thread executor

```text
CPU
 ├── Lane 0 ── AI System A
 ├── Lane 1 ── AI System B
 ├── Lane 2 ── Pathfinding
 └── Lane 3 ── Background logic
```

✔️ Нет thread explosion
✔️ Отличная cache locality
✔️ Предсказуемое поведение

---

## 🧱 Thread Modes (контракт исполнения)

```java
ThreadMode.MAIN
ThreadMode.WORKER_STRIPED
ThreadMode.WORKER_DEDICATED
```

### MAIN

* исполняется в world thread
* разрешены engine / render / physics
* дорого, но безопасно

### WORKER_STRIPED (рекомендуется)

* исполняется в одном из worker lanes
* runtime изолирован (`sys.*.laneN`)
* **нельзя напрямую трогать engine**

### WORKER_DEDICATED

* отдельный поток + runtime
* для tools / hotreload / sandbox

---

## 🛡️ Sandbox без переписывания JS

В worker‑runtime:

* `ctx` — доступен
* `engine / api / render` — **proxy на main thread**
* все вызовы маршалятся через `ctx.jobs()`

> ❗ Скрипт *физически не может* мутировать мир вне main thread

---

## ⏱️ Frame Budget Guard (60 FPS)

AEGIS считает **каждый кадр**:

```text
Frame Budget @ 60 FPS = 16.66 ms
```

Разбивка кадра:

* jobs
* hotReload
* events
* world.update
* awaitWorkers
* runtimePool maintenance

Если бюджет превышен:

```text
[frame] OVER BUDGET ⚠
  world=73ms
  awaitWorkers=0
```

👉 Ты сразу видишь **настоящего виновника**.

---

## 🏷️ PerfMarks — подсказки виновников

Из JS или Java можно пометить текущий кадр:

```js
ctx.perf().mark("shoot:spawn")
```

И при просадке:

```text
mark: shoot:spawn
```

💡 Это заменяет сложный HUD на ранней стадии разработки.

---

## 🚦 MainThreadBudgetQueue — сердце стабильного FPS

### Проблема

Создание:

* моделей
* physics bodies
* surface registry

в одном кадре = **spайк 100–200ms** ❌

### Решение AEGIS

```js
ctx.perf().main().enqueue(() => {
  spawnBullet();
});
```

Очередь:

* выполняет **N операций за кадр**
* и/или **не более X ms**

```text
maxOpsPerFrame = 8
maxMsPerFrame  = 2
```

✔️ Спавн размазывается
✔️ FPS стабилен
✔️ Лог показывает backlog

---

## 📊 Worker Profiler (встроенный)

Для каждой системы:

* last tick
* EMA tick
* max tick
* queue lag
* skipped ticks

```text
[workers]
  - AiSystem ema=3.2ms max=9.1ms skip=4
```

Это **production‑grade telemetry**, а не debug игрушка.

---

## 🔥 Почему это уровень AAA

AEGIS даёт:

* ❌ Никаких скрытых лагов
* ❌ Никакой магии
* ❌ Никакого guess‑profiling

✔️ Чёткие бюджеты
✔️ Контролируемая параллельность
✔️ Детальный лог
✔️ Безопасный sandbox
✔️ Один и тот же JS API

---

## 🧭 Рекомендованный стиль разработки

* Worker = **считать**, не мутировать
* Main thread = **применять**, не думать
* Всё тяжёлое → `enqueue`
* Если FPS падает — **лог уже знает почему**

---

## 🧬 Заключение

> **AEGIS** — это не просто runtime.
>
> Это контракт честности между движком и разработчиком.
>
> Если что‑то медленно — ты это увидишь.
> Если что‑то опасно — оно будет изолировано.
> Если что‑то тяжёлое — оно будет ограничено бюджетом.

**Kalitech показывает, что Java может AAA.**
