# 🌾 Sovereign Seasons

[![CI Pipeline](https://github.com/Consterna/sovereign-seasons/actions/workflows/ci.yml/badge.svg)](https://github.com/Consterna/sovereign-seasons/actions/workflows/ci.yml)
[![CodeQL Analysis](https://github.com/Consterna/sovereign-seasons/actions/workflows/codeql.yml/badge.svg)](https://github.com/Consterna/sovereign-seasons/actions/workflows/codeql.yml)
[![GitHub Release](https://img.shields.io/github/v/release/Consterna/sovereign-seasons?label=Release&color=blue)](https://github.com/Consterna/sovereign-seasons/releases)
[![Packages](https://img.shields.io/badge/Packages-GHCR%20%7C%20Maven-blue.svg)](https://github.com/Consterna/sovereign-seasons/pkgs/container/sovereign-seasons)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Security Policy](https://img.shields.io/badge/Security-Policy-brightgreen.svg)](SECURITY.md)
[![Java](https://img.shields.io/badge/Java-21%20Temurin-orange.svg)](https://adoptium.net/)
[![Platform](https://img.shields.io/badge/Minecraft-Paper%20%7C%20Folia%201.21.x-brightgreen.svg)](https://papermc.io)

Ecosistema modular de estaciones climáticas para servidores Minecraft Paper/Folia (1.20.4–1.21.x+), diseñado para comunidades de supervivencia inmersiva y geopolítica con economías vivas y agricultura realista.

El ciclo de estaciones opera estrictamente en **días de juego de Minecraft (in-game days)** a partir de los ticks mundiales (`24.000` ticks = 1 día), desacoplado del tiempo real para garantizar una experiencia orgánica donde el paso de los días y el sueño de los jugadores influyen en el calendario del mundo.

Para un desglose detallado de versiones y cambios, consulta el [CHANGELOG.md](CHANGELOG.md).

---

## 🏛️ Arquitectura Modular (5 Módulos)

El proyecto está organizado en una suite de 5 módulos especializados:

| Módulo | Tipo | Descripción | Estado |
|---|---|---|---|
| [**`sovereign-seasons-core`**](sovereign-seasons-core/README.md) | Plugin Paper/Folia | Motor central: Calendario de días de juego, congelación/marchitamiento de cultivos en Invierno, detección de invernaderos y API pública. | **Completado (142 tests / 100%)** |
| [**`sovereign-seasons-pantry`**](sovereign-seasons-pantry/README.md) | Plugin Paper/Folia | Descomposición de alimentos según temperatura/estación, bodegas subterráneas frías y métodos de curado/conserva. | Esqueleto inicial |
| [**`sovereign-seasons-logistics`**](sovereign-seasons-logistics/README.md) | Plugin Paper/Folia | Cajas/fardos de transporte masivo de cosechas, silos de grano y penalizaciones de movilidad por nieve/barro. | Esqueleto inicial |
| [**`sovereign-seasons-commerce`**](sovereign-seasons-commerce/README.md) | Plugin Paper/Folia | Economía viva: fluctuación estacional de precios, contratos de abastecimiento y ferias de cosecha. | Esqueleto inicial |
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

## 🕹️ Comandos

- `/season` o `/season info [mundo]`: Muestra la estación actual, día de la estación, barra de progreso porcentual, año y si el jugador está protegido en un invernadero (permiso `sovereignseasons.command.info`).
- `/season greenhouse`: Diagnóstico exhaustivo del bloque actual (validez del techo de cristal, distancia, fuente de calor y estado de la caché; permiso `sovereignseasons.command.greenhouse`).
- `/season set <spring|summer|autumn|winter> [mundo]`: Cambia inmediatamente la estación del mundo (permiso `sovereignseasons.admin`).
- `/season setday <día> [mundo]`: Modifica el día de la estación actual validando el rango `1..daysPerSeason` (permiso `sovereignseasons.admin`).
- `/season stats`: Muestra estadísticas en vivo de la caché espacial (hits, misses, tamaño y tasa de acierto).
- `/season reload`: Recarga la configuración `config.yml` y vacía la caché de invernaderos.

---

## 🛠️ Compilación y Pruebas

El proyecto utiliza Java 21 y Gradle Wrapper autónomo:

```bash
# Ejecutar todas las pruebas unitarias (142 tests automatizados en 22 suites)
./gradlew test

# Compilar el artefacto final con dependencias relocalizadas
./gradlew shadowJar
```

El artefacto compilado se generará en:
`sovereign-seasons-core/build/libs/sovereign-seasons-core-1.0.0.jar`

---

## 🐳 Despliegue y Pruebas con Docker (Paper 1.21.x)

El repositorio incluye soporte completo para desplegar un servidor Paper 1.21.x containerizado con el plugin `sovereign-seasons-core` preinstalado y configurado con las banderas de JVM optimizadas de Aikar:

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
| `MINECRAFT_VERSION` | `1.21.4` | Versión de Paper a descargar y ejecutar automáticamente. |
| `SERVER_PORT` | `25565` | Puerto expuesto del servidor Minecraft. |

Los datos de mundos, configuraciones del plugin y logs persisten localmente en el directorio `./docker/data`.

---

## 📜 Gobernanza, Propiedad Intelectual y Seguridad

- **Licencia:** Distribuido bajo la **GNU General Public License v3.0 (GPL-3.0)**. Consulta [LICENSE](LICENSE) para más detalles.
- **Aviso Legal y Derechos de Autor:** Consulta [NOTICE](NOTICE) y [COPYRIGHT](COPYRIGHT) para detalles sobre titularidad de autor, política de marcas, requerimientos anti-plagio y descargo de marcas de Mojang AB.
- **Guía de Contribución:** Consulta [CONTRIBUTING.md](CONTRIBUTING.md) para detalles sobre estándares de código, pruebas, DCO (Developer Certificate of Origin) y política de forks/re-branding.
- **Seguridad y Análisis SAST:** Consulta [SECURITY.md](SECURITY.md) para conocer la política de divulgación coordinada de vulnerabilidades y la integración de análisis continuo con CodeQL.
- **Código de Conducta:** Consulta [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) para conocer las normas de convivencia de la comunidad.
