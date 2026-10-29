# 🍲 Sovereign Seasons - Pantry (Módulo 2)

Módulo encargado de la conservación de alimentos y de su descomposición según la estación y la temperatura ambiental. Requiere **`sovereign-seasons-core`** (usa su `TemperatureService`).

---

## 🌟 Características

### 1. Descomposición de alimentos
- **Vida útil en días de juego:** Cada alimento perecedero tiene una vida útil base configurable (`shelf-life-days`). Por ejemplo: carne cruda 2–3 días, carne cocinada 4–6, pan 7, manzana 10, patata 14.
- **No perecederos:** Lo que no aparece en la tabla no caduca nunca: manzanas y zanahorias doradas, kelp seco, miel, carne podrida, etc.
- **Ritmo según temperatura:** La temperatura del lugar donde se guarda el alimento (bioma + estación del core) acelera o frena la descomposición:

  | Banda | Rango por defecto | Ritmo |
  |---|---|---|
  | Helado | < 0 °C | ×0,25 |
  | Frío | 0–10 °C | ×0,5 |
  | Templado | 10–20 °C | ×1,0 |
  | Cálido | 20–30 °C | ×1,5 |
  | Caluroso | ≥ 30 °C | ×2,0 |

- **Etapas visibles en el lore:** `Fresco` (> 50 %), `Rancio` (15–50 %) y `Pudriéndose` (< 15 %). Cuando llega al 0 %, la pila entera se convierte en **carne podrida** (`ROTTEN_FLESH`), conservando la cantidad.
- **Consecuencias al comer:** Un alimento rancio da *Hambre* durante 10 s. Uno que se está pudriendo da *Hambre II* durante 30 s.

### 2. Bodegas frías y neveras
- **Bodega (Cellar):** Un contenedor está en una bodega cuando no le llega luz del cielo a ninguno de sus lados y, además, tiene al menos `3` bloques sólidos encima o está por debajo de `Y=40`. Dentro de una bodega la descomposición va a ×0,5. La bodega no sirve en ambientes *Calurosos*, como un desierto en verano o el Nether.
- **Nevera:** Si hay hielo, hielo compacto, hielo azul o bloques de nieve a 2 bloques o menos del contenedor, la descomposición se multiplica además por ×0,5.
- **Caché espacial LRU:** Las evaluaciones se guardan durante 60 s y se invalidan al instante cuando se pone o se rompe un bloque cerca o en la columna superior.

### 3. Conservación: Ahumado
- Los alimentos que se sacan de un **ahumador** (`SMOKER`), a mano o con una tolva, quedan marcados como `Ahumado`. Su frescura vuelve al 100 % y se descomponen a ×0,25.
- Un alimento ya ahumado no puede volver a ahumarse para refrescarlo.
- *Salazón y fermentación están planificadas para la versión 1.1.*

### 4. Contenedores, compost y avisos
- **Tipo de contenedor** (`containers`): los barriles conservan mejor que los cofres (×0,75), y el efecto se suma al de la bodega y la nevera. Se puede configurar para `chest`, `barrel`, `shulker_box`, `dispenser`, `dropper` y `hopper`.
- **Compost** (`compost`): la carne podrida, que en vanilla no se puede compostar, se echa al compostador con un 30 % de probabilidad de subir de nivel.
- **Avisos** (`warnings.spoilage`): cuando la revisión del inventario pudre pilas o deja comida en "Pudriéndose", aparece un aviso en la barra de acción.

### 5. API para otros módulos
- `com.sovereignseasons.pantry.api.PantryApi`, registrada en el `ServicesManager` de Bukkit, permite leer y escribir la caducidad de un alimento, consultar el reloj de descomposición, saber si un material es perecedero y conocer la frescura ya calculada de una pila (`getFreshness`, con la conservación incluida) o si se está pudriendo (`isSpoiling`).
- La usan las cajas de **Logistics** para que la comida empaquetada conserve su frescura, y **Commerce** para que los aldeanos rechacen la comida que se está pudriendo.

### 6. Diseño de rendimiento (evaluación diferida)
- Nunca se recorre el mundo. El estado de cada alimento se actualiza solo cuando alguien lo observa:
  - al abrir un contenedor (cofres, barriles, cajas de shulker, dispensadores, soltadores, tolvas);
  - en el inventario de cada jugador, como máximo cada minuto;
  - al unirse un jugador.
- Cada ítem guarda su *fecha de caducidad a ritmo base*. El tiempo que nadie evalúa (alimentos en el suelo o en tránsito) avanza a ritmo normal sin cálculos.
- La caducidad se redondea a una hora de juego (`1000` ticks). Así se apilan entre sí los alimentos obtenidos casi al mismo tiempo. El lore muestra solo la etapa, no un porcentaje que cambie cada segundo.
- El último instante evaluado de cada contenedor se guarda en el `PersistentDataContainer` del chunk, sin *snapshots* de `BlockState`. El de cada jugador se guarda en el propio jugador.
- Compatible con **Folia**: el inventario de cada jugador se evalúa en el hilo de su región mediante `FoliaLib`.

---

## 🕹️ Comandos y Permisos

| Comando | Descripción | Permiso |
|---|---|---|
| `/pantry` o `/pantry inspect` | Frescura exacta del alimento en la mano, ritmo de descomposición en tu posición y días restantes. | `sovereignseasons.pantry.inspect` |
| `/pantry cellar` | Diagnóstico de bodega del bloque que miras: luz del cielo, techo, profundidad y hielo cercano. | `sovereignseasons.pantry.cellar` |
| `/pantry stats` | Estadísticas de la caché de bodegas. | `sovereignseasons.pantry.admin` |
| `/pantry reload` | Recarga `config.yml` y vacía la caché. | `sovereignseasons.pantry.admin` |
| `/pantry setfresh <0-100>` | Fija la frescura del alimento en la mano (herramienta de administración y pruebas). | `sovereignseasons.pantry.admin` |

Alias: `/despensa`.

---

## ⚙️ Configuración (`config.yml`)

```yaml
decay:
  enabled: true
  ticks-per-day: 24000
  min-evaluation-interval-ticks: 6000   # Evaluación mínima por contenedor (no se pierde tiempo al omitirla)
  expiry-granularity-ticks: 1000        # Redondeo de caducidad para que los alimentos se apilen
  player-check-interval-ticks: 1200
  spoil-to-rotten-flesh: true
  band-rates: { freezing: 0.25, cold: 0.5, mild: 1.0, warm: 1.5, hot: 2.0 }

stages:
  fresh-above: 0.5
  stale-above: 0.15

shelf-life-days:
  BREAD: 7
  COOKED_BEEF: 6
  # ...

cellar:
  enabled: true
  multiplier: 0.5
  min-roof-blocks: 3
  roof-scan-height: 16
  deep-y: 40
  ice-radius: 2
  ice-multiplier: 0.5

preservation:
  smoked-multiplier: 0.25

consumption:
  stale-hunger-seconds: 10
  spoiling-hunger-seconds: 30
```

Todos los valores se validan y se acotan al cargarse. Si el archivo YAML está mal formado, el plugin sigue funcionando con los valores por defecto y lo avisa en la consola.

---

## 🧪 Pruebas

```bash
./gradlew :sovereign-seasons-pantry:test
./gradlew :sovereign-seasons-pantry:shadowJar
```

Artefacto compilado: `sovereign-seasons-pantry/build/libs/sovereign-seasons-pantry-1.0.0.jar` (FoliaLib relocalizado en `com.sovereignseasons.pantry.libs.folialib`).
