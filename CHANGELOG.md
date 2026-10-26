# Changelog

Todos los cambios notables en este proyecto serán documentados en este archivo.

El formato se basa en [Keep a Changelog](https://keepachangelog.com/es-ES/1.0.0/)
y este proyecto se adhiere a [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
  - Suite de 137 pruebas unitarias y de estrés automatizadas en 22 clases de prueba (100% aprobadas).
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
- Invalidación de caché ante flujos de fluidos y vaciado/llenado de calderos de lava.
