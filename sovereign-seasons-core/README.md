# ⚙️ Sovereign Seasons Core (`sovereign-seasons-core`)

Motor central y API pública del ecosistema **Sovereign Seasons**, desarrollado para servidores Minecraft **Paper** y **Folia** (1.20.4–1.21.x+).

Este módulo gestiona el calendario estacional en días de juego, implementa las mecánicas climáticas sobre la agricultura y proporciona detección espacial de alto rendimiento para invernaderos funcionales.

---

## 🌟 Características Principales

### 1. Calendario Estacional In-Game
- **Días de juego reales:** El avance temporal se rige por ticks del mundo (`24.000` ticks = 1 día de Minecraft), respetando el ciclo de día/noche y el sueño de los jugadores en camas.
- **Cuatro Estaciones:** Primavera (`SPRING`), Verano (`SUMMER`), Otoño (`AUTUMN`) e Invierno (`WINTER`).
- **Sub-estaciones:** Cada estación se divide dinámicamente en tres fases: Temprana (`EARLY`), Media (`MID`) y Tardía (`LATE`).
- **Persistencia atómica:** Estado y desfases por mundo almacenados en `calendar_data.yml` con reemplazo seguro de archivos (`AtomicMove`) para prevenir corrupción.
- **Sincronización multi-hilo en Folia:** Totalmente compatible con la arquitectura de hilos regionales de Folia mediante la biblioteca `FoliaLib`.

### 2. Mecánicas de Invierno y Agricultura
- **Pausa de Cultivos al Aire Libre:** Durante el invierno, los cultivos no protegidos detienen su crecimiento (`BlockGrowEvent`, `BlockSpreadEvent`, `StructureGrowEvent`).
- **Marchitamiento (5–10%):** Al intentar avanzar su etapa sin protección, tienen una probabilidad configurable (por defecto `8%`, rango `5–10%`) de secarse y convertirse en arbustos secos (`DEAD_BUSH`) acompañados de efectos sutiles de sonido y partículas.
- **Bloqueo de Harina de Hueso:** Se cancela el uso manual de fertilizante en cultivos y brotes de árbol (incluidas azaleas) al aire libre en invierno (`BlockFertilizeEvent`).
- **Protección contra Hielo y Nieve:** En invernaderos funcionales se previene la congelación de agua en hielo y la formación de nieve sobre los cultivos (`prevent-freeze-and-snow: true`).

### 3. Detección Inteligente de Invernaderos (Greenhouse)
Un bloque se considera protegido dentro de un invernadero si cumple dos condiciones simultáneas:
1. **Techo de cristal vertical:** Búsqueda directa hacia arriba (hasta `16` bloques) de cristal transparente o tintado (`GLASS`, `TINTED_GLASS`, paneles de colores), sin obstrucciones sólidas interpuestas (incluyendo losas, escaleras o muros).
2. **Fuente de calor encendida:** Búsqueda en un radio cúbico de `4` bloques de calor activo: fogatas encendidas (no anegadas por agua), fuegos normales o de almas, hornos/ahumadores, antorchas, faroles, bloques de magma o calderos con lava.
- **Caché Espacial LRU de Alto Rendimiento:**
  - Capacidad máxima de 10.000 posiciones con tiempo de expiración (TTL de 30 segundos).
  - Invalidación reactiva e instantánea ante rotura/colocación de cristal o fuentes térmicas, explosiones, movimiento de pistones, trasvase de cubos, combustión en hornos y vaciado de calderos de lava.
  - Purga automática por mundo en `WorldUnloadEvent` para evitar fugas de memoria.

### 4. Temperatura Estacional (`TemperatureService`)
- **Temperatura en °C por ubicación:** Toma la temperatura vanilla del bioma (ya ajustada por altitud), la convierte a grados Celsius (`bioma × 25 − 5`; Llanuras = 15 °C) y le suma el desfase de la estación (Primavera `0`, Verano `+8`, Otoño `−2`, Invierno `−12`).
- **Transiciones suaves:** En las fases Temprana y Tardía, el desfase se interpola hacia el punto medio con la estación vecina. Así la temperatura nunca da saltos al cambiar de estación.
- **Bandas cualitativas:** `FREEZING`, `COLD`, `MILD`, `WARM` y `HOT`, con umbrales configurables y estrictamente ascendentes.
- `/season info` muestra la temperatura en la posición del jugador.

---

## 🕹️ Comandos y Permisos

| Comando | Descripción | Permiso |
|---|---|---|
| `/season` o `/season info [mundo]` | Muestra la estación actual, día, progreso porcentual, año y protección del jugador. | `sovereignseasons.command.info` |
| `/season greenhouse` | Auditoría en tiempo real del bloque actual (techo de cristal, distancia, fuente de calor y caché). | `sovereignseasons.command.greenhouse` |
| `/season set <spring\|summer\|autumn\|winter> [mundo]` | Modifica de inmediato la estación del mundo especificado. | `sovereignseasons.admin` |
| `/season setday <día> [mundo]` | Ajusta el día de la estación actual (validando rango `1..daysPerSeason`). | `sovereignseasons.admin` |
| `/season stats` | Telemetría en vivo de la caché espacial (hits, misses, tamaño y tasa de acierto). | `sovereignseasons.admin` |
| `/season reload` | Recarga la configuración `config.yml` y vacía la caché LRU. | `sovereignseasons.admin` |

---

## 🔌 API Pública para Desarrolladores

Los módulos de la suite (`sovereign-seasons-pantry`, `sovereign-seasons-commerce`, `sovereign-seasons-logistics`) y plugins de terceros pueden integrarse directamente mediante `SovereignSeasonsApi`:

```java
import com.sovereignseasons.api.SovereignSeasonsApi;
import com.sovereignseasons.api.CalendarDate;
import com.sovereignseasons.api.Season;
import com.sovereignseasons.api.calendar.SeasonService;
import com.sovereignseasons.api.greenhouse.GreenhouseService;
import com.sovereignseasons.api.temperature.TemperatureBand;
import com.sovereignseasons.api.temperature.TemperatureService;

// Obtener la instancia pública
SovereignSeasonsApi api = SovereignSeasonsApi.get();

// Consultar la fecha del calendario en un mundo
SeasonService seasonService = api.getSeasonService();
CalendarDate date = seasonService.getDate(world);
Season currentSeason = date.season();
int day = date.dayOfSeason();
double progress = date.getSeasonProgressPercent();

// Verificar si una ubicación está dentro de un invernadero funcional
GreenhouseService greenhouseService = api.getGreenhouseService();
boolean isProtected = greenhouseService.isGreenhouse(location);

// Consultar la temperatura estacional de una ubicación
TemperatureService temperatureService = api.getTemperatureService();
double celsius = temperatureService.getTemperature(location);
TemperatureBand band = temperatureService.getBand(location);
```

### Eventos Bukkit Personalizados
- **`DayChangeEvent`**: Se dispara al comenzar un nuevo día de juego de Minecraft en un mundo activo.
- **`SeasonChangeEvent`**: Se dispara cuando el calendario transiciona de estación; permite a plugins cancelar o modificar la estación entrante.
- **`CropWinterWitherEvent`**: Evento cancelable que se emite cuando un cultivo al aire libre es seleccionado para marchitarse en invierno.

---

## ⚙️ Configuración (`config.yml`)

```yaml
calendar:
  days-per-season: 7    # Días de juego por estación (7 días = año de 28 días)
  ticks-per-day: 24000  # Ticks por día (24000 vanilla)
  enabled-worlds:
    - world

crops:
  winter-growth-paused: true
  winter-wither-chance: 0.08    # Probabilidad del 8% de marchitamiento al intentar crecer
  wither-material: DEAD_BUSH
  play-wither-effects: true
  affected-crops:
    - WHEAT
    - CARROTS
    - POTATOES
    - BEETROOTS
    # ...

greenhouse:
  enabled: true
  require-glass-ceiling: true
  max-ceiling-height: 16
  require-heat-source: true
  heat-radius: 4
  cache-ttl-seconds: 30
  cache-max-size: 10000
  prevent-freeze-and-snow: true

temperature:
  enabled: true
  biome-scale: 25.0     # celsius = bioma * scale + offset
  biome-offset: -5.0
  season-offsets: { spring: 0.0, summer: 8.0, autumn: -2.0, winter: -12.0 }
  bands: { freezing-below: 0.0, cold-below: 10.0, mild-below: 20.0, warm-below: 30.0 }
```

---

## 🧪 Pruebas Unitarias y Calidad

El módulo cuenta con una suite integral de **152 pruebas unitarias y de estrés automatizadas** en 23 clases de prueba con **100% de aprobación**:

```bash
# Ejecutar todas las pruebas unitarias
./gradlew :sovereign-seasons-core:test

# Compilar el artefacto sombreado final
./gradlew :sovereign-seasons-core:shadowJar
```

Artefacto compilado:
`sovereign-seasons-core/build/libs/sovereign-seasons-core-1.0.0.jar`
