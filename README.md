# 🌾 Sovereign Seasons

[![CI Pipeline](https://github.com/Consterna/sovereign-seasons/actions/workflows/ci.yml/badge.svg)](https://github.com/Consterna/sovereign-seasons/actions/workflows/ci.yml)
[![CodeQL Analysis](https://github.com/Consterna/sovereign-seasons/actions/workflows/codeql.yml/badge.svg)](https://github.com/Consterna/sovereign-seasons/actions/workflows/codeql.yml)
[![Secret Scanning](https://github.com/Consterna/sovereign-seasons/actions/workflows/secret-scanning.yml/badge.svg)](https://github.com/Consterna/sovereign-seasons/actions/workflows/secret-scanning.yml)
[![GitHub Release](https://img.shields.io/github/v/release/Consterna/sovereign-seasons?label=Release&color=blue)](https://github.com/Consterna/sovereign-seasons/releases)
[![Packages](https://img.shields.io/badge/Packages-GHCR%20%7C%20Maven-blue.svg)](https://github.com/Consterna/sovereign-seasons/pkgs/container/sovereign-seasons)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Security Policy](https://img.shields.io/badge/Security-Policy-brightgreen.svg)](SECURITY.md)
[![Java](https://img.shields.io/badge/Java-21%20Temurin-orange.svg)](https://adoptium.net/)
[![Platform](https://img.shields.io/badge/Minecraft-Paper%20%7C%20Folia%201.21.x-brightgreen.svg)](https://papermc.io)

Ecosistema modular de estaciones climáticas para servidores Minecraft Paper/Folia (1.20.4–1.21.x+), diseñado para comunidades de supervivencia inmersiva y geopolítica con economías vivas y agricultura realista.

El ciclo de estaciones opera estrictamente en **días de juego de Minecraft (in-game days)** a partir de los ticks mundiales (`24.000` ticks = 1 día), desacoplado del tiempo real para garantizar una experiencia orgánica donde el paso de los días y el sueño de los jugadores influyen en el calendario del mundo.

Para un desglose detallado de versiones y cambios, consulta el [CHANGELOG.md](CHANGELOG.md).

### 🎯 Filosofía de diseño: *vanilla+*
Sovereign Seasons añade **dificultad y planificación** sin convertir Minecraft en otro juego. No pretende ser un modpack *hardcore* como TerraFirmaCraft ni un simulador como Vintage Story. La idea es que el jugador tenga que pensar en las estaciones: qué plantar y dónde, cómo guardar la comida y cómo abrigarse. Todo ello con soluciones que ya existen en vanilla, como el cristal y el fuego para los invernaderos, el subsuelo y el hielo para las bodegas, el cuero contra el frío, y la sombra o el agua contra el calor.
- Nada te mata por sorpresa: el frío no hace daño por defecto, y los cultivos fuera de temporada crecen más lento, pero no mueren.
- Cada mecánica es configurable y se puede desactivar por separado.

---

## 🏛️ Arquitectura Modular (5 Módulos)

El proyecto está organizado en una suite de 5 módulos especializados:

| Módulo | Tipo | Descripción | Estado |
|---|---|---|---|
| [**`sovereign-seasons-core`**](sovereign-seasons-core/README.md) | Plugin Paper/Folia | Motor central: calendario de días de juego, invierno e invernaderos, temperatura estacional, cultivos con clima ideal, lluvia y fauna estacional, frío/calor suave, HUD y API pública. | **v1.1.0 (173 tests / 100%)** |
| [**`sovereign-seasons-pantry`**](sovereign-seasons-pantry/README.md) | Plugin Paper/Folia | Descomposición de alimentos según temperatura/estación, bodegas frías, neveras, barriles, ahumado, compost de comida podrida y avisos. | **v1.0.0 (44 tests / 100%)** |
| [**`sovereign-seasons-logistics`**](sovereign-seasons-logistics/README.md) | Plugin Paper/Folia | Nieve y barro estacionales, caminos, ventiscas, lagos helados, cajas a granel y peso de carga. | **v1.0.0 (29 tests / 100%)** |
| [**`sovereign-seasons-commerce`**](sovereign-seasons-commerce/README.md) | Plugin Paper/Folia | Economía estacional con esmeraldas y aldeanos: precios por estación, ferias del vendedor ambulante, comida podrida rechazada y venta a granel de cajas. | **v1.0.0 (20 tests / 100%)** |
| [**`sovereign-seasons-resourcepack`**](sovereign-seasons-resourcepack/README.md) | Resource Pack | Iconos de interfaz (glifos), efectos visuales y tintes de follaje estacional. | Esqueleto inicial |

---

## ❄️ Mecánica Central de Invierno e Invernaderos (`core`)

En **Invierno (`WINTER`)**:
1. **Pausa de Cultivos:** Todos los cultivos al aire libre (`BlockGrowEvent`, `BlockSpreadEvent`, `StructureGrowEvent`) pausan su crecimiento por congelación.
2. **Marchitamiento (5–10%):** Al intentar avanzar su etapa de crecimiento sin protección, tienen una probabilidad configurable (por defecto `8%`, rango `5–10%`) de marchitarse y convertirse en arbustos secos (`DEAD_BUSH`).
3. **Bloqueo de Harina de Hueso:** Cancelación de fertilización manual con polvo de hueso en cultivos y brotes de árbol (incluyendo azaleas) al aire libre en invierno.
4. **Invernadero Funcional (Greenhouse):**
   - **Techo de cristal vertical:** Búsqueda directa hacia arriba (hasta `16` bloques) sin obstrucciones sólidas interpuestas (incluyendo losas, escaleras o muros), detectando cristal estándar, tintado o paneles.
   - **Fuente de calor encendida:** Búsqueda en radio cúbico (`4` bloques) de fogatas activas (no anegadas por agua), hornos encendidos, antorchas, lava, faroles o bloques de magma.
   - **Protección contra hielo y nieve:** El agua dentro de un invernadero funcional no se congela en hielo ni acumula capas de nieve durante el invierno (`prevent-freeze-and-snow: true`).
   - **Caché Espacial LRU de Alto Rendimiento:** Las evaluaciones de posición se almacenan en una caché LRU Guava con tiempo de vida (TTL de `30s`) e invalidación reactiva ante rotura o colocación de bloques de cristal/calor, explosiones, pistones, cubos, calderos de lava, combustión en hornos, y descarte al descargar mundos (`WorldUnloadEvent`), evitando fugas de memoria y lag por escaneos redundantes.

---

## 🌱 Naturaleza Estacional (`core`)

- **Clima ideal por cultivo:** Cada cultivo tiene estaciones fértiles y bandas de temperatura ideales. La temperatura combina bioma y estación.
  - Las patatas, zanahorias y remolachas prefieren el frío templado.
  - El trigo prefiere un clima templado.
  - Los melones, el cacao, la caña y el cactus prefieren el calor.
  - Fuera de su temporada o de su clima crecen más lento, pero nunca mueren por ello.
  - El invernadero ignora estación y clima, y las granjas subterráneas ignoran la estación.
- **Lluvia estacional:** Los veranos son más secos y los cambios de clima por comando nunca se bloquean.
- **Fauna:**
  - En primavera, criar animales puede dar gemelos.
  - En invierno, en zonas frías, aparecen *Strays* en lugar de esqueletos.
  - En verano, en zonas calurosas, aparecen *Husks* en lugar de zombis.

## 🌡️ Frío y Calor Suaves (`core`)

- **Frío** (zona Helada a la intemperie): escarcha visual y Lentitud, **sin daño** por defecto. Te protege llevar *una pieza de cuero*, igual que con la nieve polvo, o tener cerca fuego o antorchas.
- **Calor** (zona Calurosa, de día y al sol): un poco más de hambre. Te proteges con sombra, agua o Resistencia al fuego.

## 🍲 Conservación de Alimentos (`pantry`)

- **Descomposición estacional:** Los alimentos perecederos tienen una vida útil en días de juego que se acelera con el calor (Verano, desiertos, Nether) y se frena con el frío.
- **Bodegas y neveras:** Los contenedores subterráneos sin luz del cielo y los rodeados de hielo conservan mucho mejor la comida.
- **Ahumado:** Lo que sale de un ahumador queda `Ahumado` y dura cuatro veces más.
- **Barriles:** Conservan mejor que los cofres (×0,75).
- **Compost:** La carne podrida se puede echar al compostador.
- **Avisos:** Se avisa cuando la comida de tu inventario se echa a perder.
- **Podredumbre:** La comida en mal estado da *Hambre* y, al pudrirse por completo, se convierte en carne podrida.
- Consulta [sovereign-seasons-pantry/README.md](sovereign-seasons-pantry/README.md) para conocer las mecánicas, los comandos (`/pantry`) y la configuración.

---

## 📦 Transporte y Movilidad (`logistics`)

- **Nieve y barro:** la nieve y el barro (cuando llueve en primavera u otoño) ralentizan a jugadores y monturas. Las botas de cuero y la armadura de caballo de cuero protegen en la nieve.
- **Caminos:** sendero, grava, adoquines, ladrillos, tablones u hormigón anulan la nieve fina y el barro, y aceleran a las monturas. En invierno hay que despejar la nieve profunda.
- **Ventiscas:** nevando en zona Helada y a cielo abierto se va más lento, sin daño. Resguardarse la anula.
- **Lagos helados:** en invierno el agua quieta se congela y crea rutas de hielo. En primavera se derrite solo el hielo que puso el plugin.
- **Cajas a granel:** agáchate y haz clic derecho al aire con 9 cosechas para empaquetarlas. Con Pantry, dentro de la caja la comida dura el doble.
- **Peso de carga** (opcional): llevar demasiado ralentiza. Las mulas, llamas y vagonetas no cuentan.
- Consulta [sovereign-seasons-logistics/README.md](sovereign-seasons-logistics/README.md).

---

## 💰 Economía Estacional (`commerce`)

- **Sin plugins de economía:** se comercia como en vanilla, con esmeraldas y aldeanos.
- **Precios por estación:** los cultivos abundan en su temporada y escasean en invierno. Un granjero que pide 22 zanahorias por esmeralda pide 28 en la cosecha y 16 en invierno, con el precio original tachado. La lana, el carbón y el pescado valen más en invierno y la carne abunda en otoño. El ajuste desaparece al cerrar el comercio.
- **Ferias del vendedor ambulante:** semillas en primavera, hielo para las bodegas en verano, cuero contra la nieve en otoño, y fogatas y cristal para los invernaderos en invierno.
- **Con Pantry:** los aldeanos rechazan la comida que se está pudriendo.
- **Con Logistics:** agachado y con cajas en la mano, el aldeano te las compra al por mayor (+15 %) hasta un límite diario.
- Consulta [sovereign-seasons-commerce/README.md](sovereign-seasons-commerce/README.md).

---

## 🕹️ Comandos

- `/season` o `/season info [mundo]`: Muestra la estación actual, día de la estación, barra de progreso porcentual, año y si el jugador está protegido en un invernadero (permiso `sovereignseasons.command.info`).
- `/season hud`: Activa o desactiva una BossBar con la estación, el día, el año y la temperatura (permiso `sovereignseasons.command.hud`). Al usar un **reloj** (clic derecho), la fecha aparece en la barra de acción.
- `/season greenhouse`: Diagnóstico exhaustivo del bloque actual (validez del techo de cristal, distancia, fuente de calor y estado de la caché; permiso `sovereignseasons.command.greenhouse`).
- `/season set <spring|summer|autumn|winter> [mundo]`: Cambia inmediatamente la estación del mundo (permiso `sovereignseasons.admin`).
- `/season setday <día> [mundo]`: Modifica el día de la estación actual validando el rango `1..daysPerSeason` (permiso `sovereignseasons.admin`).
- `/season stats`: Muestra estadísticas en vivo de la caché espacial (hits, misses, tamaño y tasa de acierto).
- `/season reload`: Recarga la configuración `config.yml` y vacía la caché de invernaderos.
- `/pantry`, `/logistics` (alias `/carga`) y `/commerce` (alias `/mercado`): consulta el README de cada módulo.

---

## 🖥️ Instalación en un Servidor

| Requisito | Detalle |
|---|---|
| Software | **Paper 1.20.4 – 26.3** o **Folia**. Verificado en servidor real: Paper **1.21.11** y **26.1.2** (todas las pruebas de juego) y **26.3** (prueba de humo). |
| Java | 21 o superior para Paper 1.21.x; **25** para Paper 26.x (exigencia de Paper, no del plugin). |
| Plugins | `sovereign-seasons-core-1.1.0.jar` (obligatorio). `sovereign-seasons-pantry-1.0.0.jar`, `sovereign-seasons-logistics-1.0.0.jar` y `sovereign-seasons-commerce-1.0.0.jar` (opcionales, independientes entre sí; requieren Core 1.1.0+). Juntos se complementan: las cajas de Logistics conservan la frescura de Pantry, y Commerce rechaza la comida podrida y compra las cajas al por mayor. |
| Opcional | [PlaceholderAPI](https://github.com/PlaceholderAPI/PlaceholderAPI) 2.10+ (verificado con 2.12.3) para `%sovereignseasons_season%`, `_subseason`, `_day`, `_days_per_season`, `_year`, `_progress`, `_temperature` y `_band`. |

1. Copia los JAR en la carpeta `plugins/` del servidor y reinícialo (no uses `/reload`).
2. Edita `plugins/SovereignSeasons-Core/config.yml`: añade tus mundos en `calendar.enabled-worlds` y ajusta la duración de las estaciones.
3. Edita `plugins/SovereignSeasons-Pantry/config.yml` si quieres cambiar la vida útil de los alimentos.
4. Aplica los cambios con `/season reload` y `/pantry reload`. Los intervalos de las tareas periódicas necesitan reiniciar el servidor.

No hacen falta otras dependencias: FoliaLib va incluido y relocalizado dentro de cada JAR.

---

## 🛠️ Compilación y Pruebas

El proyecto utiliza Java 21 y Gradle Wrapper autónomo:

```bash
# Ejecutar todas las pruebas unitarias (266 tests automatizados: 173 core + 44 pantry + 29 logistics + 20 commerce)
./gradlew test

# Compilar el artefacto final con dependencias relocalizadas
./gradlew shadowJar
```

Además hay **pruebas de juego reales** con un servidor Paper local y un bot (Mineflayer), sin necesidad de Docker: 87 comprobaciones (core, pantry, logistics, commerce, lluvia natural y aparición de mobs) verificadas en Paper 1.21.11 y 26.1.2. Consulta [tools/e2e/README.md](tools/e2e/README.md) y, para revisar lo visual con tu cliente, [tools/e2e/MANUAL_CHECKS.md](tools/e2e/MANUAL_CHECKS.md).

Los artefactos compilados se generarán en:
- `sovereign-seasons-core/build/libs/sovereign-seasons-core-1.1.0.jar`
- `sovereign-seasons-pantry/build/libs/sovereign-seasons-pantry-1.0.0.jar`
- `sovereign-seasons-logistics/build/libs/sovereign-seasons-logistics-1.0.0.jar`
- `sovereign-seasons-commerce/build/libs/sovereign-seasons-commerce-1.0.0.jar`

---

## 🐳 Despliegue y Pruebas con Docker (Paper 1.21.x)

El repositorio incluye soporte completo para desplegar un servidor Paper 1.21.x containerizado con los plugins `sovereign-seasons-core` y `sovereign-seasons-pantry` preinstalados y configurado con las banderas de JVM optimizadas de Aikar:

### Inicio Rápido (Multi-Stage Dockerfile)

```bash
# Construir la imagen y levantar el servidor Paper en segundo plano
docker compose up -d

# Seguir los registros de la consola del servidor
docker compose logs -f

# Acceder a la consola interactiva del servidor Minecraft
docker attach sovereign-seasons-paper
# (Para salir de la consola sin detener el servidor: presionar Ctrl + P, luego Ctrl + Q)

# Detener el servidor de forma segura
docker compose down
```

### Imagen Precompilada desde GitHub Container Registry (GHCR)

También puedes ejecutar el servidor directamente con la imagen oficial publicada en GitHub Packages sin compilar localmente:

```bash
# Descargar y ejecutar la imagen pública del contenedor
docker run -d --name sovereign-seasons-paper \
  -p 25565:25565 \
  -e EULA=true \
  -v ./docker/data:/server/data \
  ghcr.io/consterna/sovereign-seasons:1.0.0
```

### Perfil Alternativo con Imagen Comunitaria (`itzg/minecraft-server`)

Si prefieres utilizar la imagen comunitaria `itzg/minecraft-server:java21` montando el binario JAR local previamente compilado:

```bash
# Compilar previamente el JAR sombreado
./gradlew shadowJar

# Levantar el servidor con el perfil itzg
docker compose --profile itzg up -d
```

### Variables de Entorno Configurables

| Variable | Por Defecto | Descripción |
|---|---|---|
| `EULA` | `true` (en compose) | Aceptación del Contrato de Licencia de Usuario Final (EULA) de Mojang. |
| `MEMORY` | `2G` | Memoria RAM asignada a la JVM (`-Xms` y `-Xmx`). |
| `MINECRAFT_VERSION` | `1.21.11` | Versión de Paper a descargar y ejecutar automáticamente. |
| `SERVER_PORT` | `25565` | Puerto expuesto del servidor Minecraft. |

Los datos de mundos, configuraciones del plugin y logs persisten localmente en el directorio `./docker/data`.

---

## 📜 Gobernanza, Propiedad Intelectual y Seguridad

- **Licencia:** Distribuido bajo la **GNU General Public License v3.0 (GPL-3.0)**. Consulta [LICENSE](LICENSE) para más detalles.
- **Aviso Legal y Derechos de Autor:** Consulta [NOTICE](NOTICE) y [COPYRIGHT](COPYRIGHT) para detalles sobre titularidad de autor, política de marcas, requerimientos anti-plagio y descargo de marcas de Mojang AB.
- **Guía de Contribución:** Consulta [CONTRIBUTING.md](CONTRIBUTING.md) para detalles sobre estándares de código, pruebas, DCO (Developer Certificate of Origin) y política de forks/re-branding.
- **Seguridad y Análisis SAST:** Consulta [SECURITY.md](SECURITY.md) para conocer la política de divulgación coordinada de vulnerabilidades y la integración de análisis continuo con CodeQL.
- **Código de Conducta:** Consulta [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) para conocer las normas de convivencia de la comunidad.
