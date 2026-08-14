# Восстановление исходников модулей

Исходники всех внешних модулей находятся в `modulesSrc/`:

- CameraModule;
- HudModule;
- InputModule;
- MaterialModule;
- ParticlesModule;
- PhysicsModule;
- RenderModule;
- SoundModule;
- TerrainModule.

Каждый проект содержит Gradle-конфигурацию, Java-часть, Lua-ресурсы, descriptor и документацию. Корневой build подключает проекты и задачей `syncModuleJars` переносит собранные JAR в каталог runtime-модулей.

## Проверка

```text
gradlew clean test assemble
```

Smoke-тесты проверяют:

- наличие Lua-точки входа из descriptor;
- загрузку каждой точки входа в Lua-runtime;
- отсутствие посторонних script-ресурсов в JAR;
- регистрацию модулей через `ENGINE`;
- загрузку и вызов `Scripts/main.lua`.

Исходники в `modulesSrc/` являются единственным местом редактирования внешних модулей. Скомпилированные JAR считаются результатом сборки.
