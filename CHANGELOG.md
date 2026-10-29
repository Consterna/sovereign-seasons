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
- **`sovereign-seasons-core` 1.1.0 — Naturaleza estacional (filosofía *vanilla+*)**:
  - Perfil climático por cultivo (`crops.seasonal-growth.profiles`): estaciones fértiles y bandas de temperatura ideales.
    - Fuera de temporada o de su clima, cada intento de crecer tiene menos probabilidad de prosperar, pero el cultivo nunca muere por ello.
    - El invernadero ignora estación y clima; bajo tierra se ignora la estación. La verruga del Nether está exenta.
  - Lluvia estacional (`weather.rain-chance`): veranos más secos. Solo se filtra la lluvia natural (causas `NATURAL` y `UNKNOWN`); `/weather`, dormir y otros plugins no se tocan. Con `weather.debug` se registra cada decisión en consola.
  - Fauna estacional (`fauna`): gemelos al criar en primavera, *Strays* en inviernos fríos y *Husks* en veranos calurosos.
- **`sovereign-seasons-core` 1.1.0 — Frío y calor suaves (`comfort`)**:
  - **Frío:** en zona Helada a la intemperie, escarcha visual y Lentitud, sin daño por defecto. Protegen una pieza de cuero (como con la nieve polvo) o tener fuego o luz cerca.
  - **Calor:** en zona Calurosa, de día y al sol, un poco más de cansancio. Protegen la sombra, el agua o la Resistencia al fuego.
  - Aviso en la barra de acción con enfriamiento por tipo de exposición. Todo es configurable y desactivable.
- **`sovereign-seasons-core` 1.1.0 — Interfaz**:
  - Título y sonido al cambiar de estación.
  - El reloj muestra fecha y temperatura.
  - `/season hud` muestra una BossBar persistente por jugador (permiso `sovereignseasons.command.hud`).
  - Integración opcional con PlaceholderAPI (`softdepend`).
- Pruebas: 173 en core (+31 respecto a 1.0.0).
- **Módulo `sovereign-seasons-pantry` v1.0.0 (Conservación de alimentos)**:
  - Descomposición de alimentos perecederos según vida útil en días de juego (configurable por material) y ritmo por banda de temperatura.
  - Etapas `Fresco`, `Rancio` y `Pudriéndose` en el lore. Al pudrirse por completo, la pila se convierte en carne podrida conservando la cantidad.
  - Bodegas subterráneas frías (sin luz del cielo y con techo sólido o profundidad) y neveras de hielo, con caché espacial LRU e invalidación reactiva.
  - Ahumado: los alimentos que salen de un ahumador quedan conservados (ritmo ×0,25), sin bloquear la salida del horno.
  - Penalización de *Hambre* al comer alimentos rancios o en descomposición.
  - Evaluación diferida al abrir contenedores y periódica en el inventario de los jugadores, compatible con Folia. La caducidad se redondea para preservar el apilamiento.
  - Comando `/pantry` (`inspect`, `cellar`, `stats`, `reload`, `setfresh`) con permisos `sovereignseasons.pantry.*`.
  - Multiplicador por tipo de contenedor: los barriles conservan mejor (×0,75).
  - La carne podrida se puede echar al compostador.
  - Aviso al jugador cuando la comida de su inventario se pudre o está a punto.
  - Suite de 41 pruebas unitarias.
- **Módulo `sovereign-seasons-logistics` v1.0.0 (Transporte y movilidad)**:
  - **Nieve:** las capas ralentizan (Lentitud I) y la nieve profunda más (II). Las botas de cuero y la armadura de caballo de cuero protegen, como con la nieve polvo vanilla.
  - **Barro:** cuando llueve en primavera u otoño, la hierba y la tierra ralentizan; el bloque de barro, siempre.
  - **Caminos** (senderos, grava, adoquines, ladrillos, tablones, hormigón…): anulan la nieve fina y el barro y dan Velocidad a las monturas. La nieve profunda hay que despejarla.
  - **Ventiscas:** en invierno, en zona Helada, nevando y a cielo abierto, un nivel extra de Lentitud. Resguardarse la anula.
  - **Lagos helados:** el agua quieta superficial se congela en inviernos helados (por muestreo cerca de los jugadores) y en primavera se derrite solo el hielo registrado por el plugin en el PDC de cada chunk.
  - **Cajas a granel:** se empaqueta con agacharse + clic derecho al aire (9 unidades) y se desempaqueta con clic derecho. Las cajas son apilables y no se pueden colocar ni quemar. Con Pantry, la comida dentro se estropea a ×0,5.
  - **Peso de carga** (desactivado por defecto): reglas por patrón de material, contenido de shulkers y bundles al 50 % y cajas al 60 %. Las mulas, llamas, vagonetas y el cofre de Ender no cuentan.
  - Comando `/logistics` (`status`, `weight`, `stats`, `reload`) con permisos `sovereignseasons.logistics.*`.
  - Suite de 23 pruebas unitarias y 22 pruebas de juego (`tools/e2e/e2e-logistics.js`).
- **`sovereign-seasons-pantry`**: nueva API pública `PantryApi` en el `ServicesManager` (caducidad, reloj de descomposición, perecederos), usada por las cajas de Logistics. 43 pruebas.
- Imagen Docker, `compose.yml` y workflows de CI/Release incluyen los JAR de Pantry y Logistics. La versión de Paper por defecto pasa a 1.21.11.
- Pruebas de juego end-to-end (`tools/e2e`): servidor Paper local + bot Mineflayer, sin Docker.
  - Suite principal de 33 comprobaciones y suite de naturaleza de 5 (lluvia natural, Strays y Husks), con `/tick sprint`.
  - `run-local.sh` automatiza el ciclo completo sobre un mundo limpio.
  - Guía de revisión visual con el cliente propio (`MANUAL_CHECKS.md`).
- Compatibilidad verificada en servidor real: Paper 1.21.11 (Java 21) y 26.1.2 (Java 25) con todas las pruebas, Paper 26.3 con prueba de humo y PlaceholderAPI 2.12.3.
- Guía de instalación para servidores y sección de filosofía de diseño en el README.

### Corregido
- El JAR normal (`jar`) y el JAR del plugin (`shadowJar`) de cada módulo escribían en el mismo archivo. Un módulo dependiente podía regenerar el normal y dejar un plugin sin FoliaLib (`ClassNotFoundException` al arrancar). El JAR normal pasa a llevar el clasificador `-plain`, y la publicación Maven del core publica el JAR del plugin (`components["shadow"]`).
- `SovereignSeasonsApi.Holder` publica la instancia de forma segura entre hilos (`volatile`), algo necesario en Folia.
- La Lentitud por frío se resuelve por clave de registro (`minecraft:slowness`), porque el campo Java cambió de nombre en 1.20.5.

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
