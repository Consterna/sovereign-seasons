# 🌾 Sovereign Seasons

Ecosistema modular de estaciones climáticas para servidores Minecraft Paper/Folia (1.20.4–1.21.x+), diseñado para comunidades de supervivencia inmersiva y geopolítica con economías vivas y agricultura realista.

El ciclo de estaciones opera estrictamente en **días de juego de Minecraft (in-game days)** a partir de los ticks mundiales (`24.000` ticks = 1 día), desacoplado del tiempo real para garantizar una experiencia orgánica donde el paso de los días y el sueño de los jugadores influyen en el calendario del mundo.

---

## 🏛️ Arquitectura Modular (5 Módulos)

El proyecto está organizado en una suite de 5 módulos especializados:

| Módulo | Tipo | Descripción | Estado |
|---|---|---|---|
| **`sovereign-seasons-core`** | Plugin Paper/Folia | Motor central: Calendario de días de juego, congelación/marchitamiento de cultivos en Invierno, detección de invernaderos y API pública. | **Completado (100% test)** |
| **`sovereign-seasons-pantry`** | Plugin Paper/Folia | Descomposición de alimentos según temperatura/estación, bodegas subterráneas frías y métodos de curado/conserva. | Esqueleto inicial |
| **`sovereign-seasons-logistics`** | Plugin Paper/Folia | Cajas/fardos de transporte masivo de cosechas, silos de grano y penalizaciones de movilidad por nieve/barro. | Esqueleto inicial |
| **`sovereign-seasons-commerce`** | Plugin Paper/Folia | Economía viva: fluctuación estacional de precios, contratos de abastecimiento y ferias de cosecha. | Esqueleto inicial |
| **`sovereign-seasons-resourcepack`** | Resource Pack | Iconos de interfaz (glifos), efectos visuales y tintes de follaje estacional. | Esqueleto inicial |

---

## ❄️ Mecánica Central de Invierno e Invernaderos (`core`)

En **Invierno (`WINTER`)**:
1. **Pausa de Cultivos:** Todos los cultivos al aire libre (`BlockGrowEvent`, `BlockSpreadEvent`, `StructureGrowEvent`) pausan su crecimiento por congelación.
2. **Marchitamiento (5–10%):** Al intentar avanzar su etapa de crecimiento sin protección, tienen una probabilidad configurable (por defecto `8%`, rango `5–10%`) de marchitarse y convertirse en arbustos secos (`DEAD_BUSH`).
3. **Invernadero Funcional (Greenhouse):**
   - **Techo de cristal vertical:** Búsqueda directa hacia arriba (hasta `16` bloques) sin obstrucciones sólidas interpuestas, detectando cristal estándar, tintado o paneles.
   - **Fuente de calor encendida:** Búsqueda en radio cúbico (`4` bloques) de fogatas activas, hornos encendidos, antorchas, lava, faroles o bloques de magma.
   - **Caché Espacial LRU de Alto Rendimiento:** Las evaluaciones de posición se almacenan en una caché LRU Guava con tiempo de vida (TTL de `30s`) e invalidación reactiva ante rotura o colocación de bloques de cristal/calor, evitando lag por escaneos redundantes en granjas masivas.

---

## 🕹️ Comandos

- `/season` o `/season info`: Muestra la estación actual, día de la estación, barra de progreso porcentual, año y si el jugador está protegido en un invernadero.
- `/season greenhouse`: Diagnóstico exhaustivo del bloque actual (validez del techo de cristal, distancia, fuente de calor y estado de la caché).
- `/season set <spring|summer|autumn|winter>`: Cambia inmediatamente la estación del mundo (permiso `sovereignseasons.admin`).
- `/season setday <número>`: Modifica el día de la estación actual (permiso `sovereignseasons.admin`).
- `/season stats`: Muestra estadísticas en vivo de la caché espacial (hits, misses, tamaño y tasa de acierto).
- `/season reload`: Recarga la configuración `config.yml` y vacía la caché de invernaderos.

---

## 🛠️ Compilación y Pruebas

El proyecto utiliza Java 21 y Gradle Wrapper autónomo:

```bash
# Ejecutar todas las pruebas unitarias (42 tests automatizados)
./gradlew test

# Compilar el artefacto final con dependencias relocalizadas
./gradlew shadowJar
```

El artefacto compilado se generará en:
`sovereign-seasons-core/build/libs/sovereign-seasons-core-1.0.0.jar`
