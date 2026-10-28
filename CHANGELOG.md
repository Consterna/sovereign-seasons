# Changelog

Todos los cambios notables en este proyecto serán documentados en este archivo.

El formato se basa en [Keep a Changelog](https://keepachangelog.com/es-ES/1.0.0/)
y este proyecto se adhiere a [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Añadido
- **`sovereign-seasons-core` — Servicio de temperatura estacional**:
  - Nueva API `TemperatureService` y enum `TemperatureBand` (`FREEZING`, `COLD`, `MILD`, `WARM`, `HOT`) en `com.sovereignseasons.api.temperature`, accesible mediante `SovereignSeasonsApi#getTemperatureService()` y el `ServicesManager` de Bukkit.
  - Temperatura en °C a partir de la temperatura del bioma (ajustada por altitud) más un desfase estacional interpolado de forma continua entre estaciones.
  - Sección `temperature` en `config.yml` con escala de bioma, desfases por estación y umbrales de banda validados (finitos, en el rango `[-100, 100]` y ascendentes).
  - `/season info` muestra la temperatura del jugador.
  - 10 pruebas nuevas (152 en total).
- **Módulo `sovereign-seasons-pantry` v1.0.0 (Conservación de alimentos)**:
  - Descomposición de alimentos perecederos según vida útil en días de juego (configurable por material) y ritmo por banda de temperatura.
  - Etapas `Fresco`, `Rancio` y `Pudriéndose` en el lore. Al pudrirse por completo, la pila se convierte en carne podrida conservando la cantidad.
  - Bodegas subterráneas frías (sin luz del cielo y con techo sólido o profundidad) y neveras de hielo, con caché espacial LRU e invalidación reactiva.
  - Ahumado: los alimentos que salen de un ahumador quedan conservados (ritmo ×0,25), sin bloquear la salida del horno.
  - Penalización de *Hambre* al comer alimentos rancios o en descomposición.
  - Evaluación diferida al abrir contenedores y periódica en el inventario de los jugadores, compatible con Folia. La caducidad se redondea para preservar el apilamiento.
  - Comando `/pantry` (`inspect`, `cellar`, `stats`, `reload`, `setfresh`) con permisos `sovereignseasons.pantry.*`.
  - Suite de 39 pruebas unitarias.
- Imagen Docker, `compose.yml` y workflows de CI/Release incluyen el JAR de Pantry.

## [1.0.0] - 2026-10-26

### Añadido
- **Módulo `sovereign-seasons-core` (Motor central v1.0.0)**:
  - Motor de calendario estacional estricto en días de juego de Minecraft (`24.000` ticks de mundo = 1 día), desacoplado del tiempo real para garantizar sincronización orgánica con ciclos de sueño y mecánicas de supervivencia.
  - Cuatro estaciones astronómicas (`SPRING`, `SUMMER`, `AUTUMN`, `WINTER`) divididas en sub-estaciones (`EARLY`, `MID`, `LATE`) con formateo estético mediante MiniMessage (`CalendarDate.toFormattedComponent`).
  - Detección espacial de invernaderos (`GreenhouseDetector`) de alto rendimiento:
    - Verificación vertical de techos de cristal (`GLASS`, `TINTED_GLASS`, paneles tintados) hasta 16 bloques de altura con comprobación de línea de visión directa libre de obstrucciones sólidas (incluyendo losas, escaleras y muros).
    - Detección en radio cúbico 3D (4 bloques) de fuentes térmicas activas: fogatas encendidas (no anegadas por agua), fuegos normales y de almas, hornos, antorchas, faroles, bloques de magma y calderos de lava.
    - Caché LRU de alto rendimiento con tiempo de vida (TTL 30s) y capacidad configurable (10.000 posiciones) para evitar picos de retardo (lag spikes).
    - Invalidación reactiva e inmediata de la caché ante rotura, colocación, combustión, explosiones de TNT/entidades, interacción con cubos de agua/lava, pistones, y eventos de cambio de nivel en calderos de lava.
    - Prevención de congelación de agua en hielo y acumulación de nieve sobre cultivos protegidos bajo invernadero (`prevent-freeze-and-snow: true`).
  - Mecánica de invierno y agricultura adversa:
    - Pausa automática de crecimiento de cultivos al aire libre (`BlockGrowEvent`, `BlockSpreadEvent`, `StructureGrowEvent`) durante la estación de invierno.
    - Probabilidad configurable (5–10%, por defecto 8%) de marchitamiento a arbusto seco (`DEAD_BUSH`) con efectos visuales de partículas de humo y sonido de rotura de cultivo.
    - Cancelación de fertilización manual con harina de hueso (`BlockFertilizeEvent`) en cultivos y brotes de árbol (incluyendo azaleas) al aire libre en invierno.
  - API pública desacoplada y thread-safe (`SovereignSeasonsApi`):
    - `SeasonService`: consulta de fechas, estaciones actuales, avance porcentual de estación y manipulación administrativa.
    - `GreenhouseService`: consulta, evaluación e invalidación manual de invernaderos, con generación de reportes detallados (`GreenhouseReport`).
    - Eventos personalizados Bukkit: `DayChangeEvent`, `SeasonChangeEvent` y `CropWinterWitherEvent` (cancelable).
  - Sistema integral de comandos y permisos (`/season`):
    - `/season` o `/season info [mundo]`: visualización detallada del día, año, estación, porcentaje transcurrido y estado térmico del jugador (`sovereignseasons.command.info`).
    - `/season greenhouse`: diagnóstico exhaustivo en tiempo real del bloque actual (validez del techo de cristal, distancia, fuente de calor y estado de la caché; `sovereignseasons.command.greenhouse`).
    - `/season set <spring|summer|autumn|winter> [mundo]`: cambio forzado de estación para administradores (`sovereignseasons.admin`).
    - `/season setday <día> [mundo]`: ajuste puntual del día de la estación (`sovereignseasons.admin`).
    - `/season stats`: telemetría en vivo con métricas de hits, misses y tasa de acierto de la caché espacial (`sovereignseasons.admin`).
    - `/season reload`: recarga de configuración `config.yml` y vaciado de la caché (`sovereignseasons.admin`).
  - Compatibilidad de arquitectura Paper y Folia multi-hilo mediante integración con `FoliaLib`.
  - Persistencia segura y atómica del estado del calendario por mundo mediante `calendar_data.yml` con reemplazo atómico de archivo (`AtomicMove`).
  - Suite de 142 pruebas unitarias y de estrés automatizadas en 22 clases de prueba (100% aprobadas).
- **Estructura base modular de la suite Sovereign Seasons**:
  - `sovereign-seasons-pantry`: Descomposición de alimentos según temperatura/estación y bodegas subterráneas.
  - `sovereign-seasons-logistics`: Fardos y cajas para transporte a granel de cosechas y silos de grano.
  - `sovereign-seasons-commerce`: Fluctuación estacional de precios y contratos de abastecimiento.
  - `sovereign-seasons-resourcepack`: Iconos de interfaz, efectos visuales y tintes de follaje.

### Corregido
- Bloqueo preventivo de harina de hueso en brotes de azalea y árboles al aire libre durante invierno.
- Normalización defensiva de desfases temporales ante retrocesos en la hora del mundo (`/time set 0`).
- Saneamiento y validación defensiva en la entrada de comandos (`trim()`, prefijos positivos `+4`, detección de desbordamiento numérico).
- Validación de límites verticales (-64 a 320) y verificación segura de chunks cargados en consultas espaciales.
- Invalidación de caché ante flujos de fluidos, vaciado/llenado de calderos de lava y combustión automática en hornos (`FurnaceBurnEvent`).
- Protección contra división por cero en cancelaciones de `SeasonChangeEvent` en `WorldCalendarManager`.
- Prevención de desbordamiento de enteros (overflow) en cálculos del año acotando `days-per-season` a un rango seguro (1 a 100.000).
- Optimización de memoria en el autocompletado de `/season setday` acotando sugerencias a un máximo de 100 días.
- Formateo consistente de dígitos ASCII en `CalendarDate.toFormattedComponent` mediante `Locale.ROOT`.
