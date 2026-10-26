# 🤝 Guía de Contribución a Sovereign Seasons

¡Gracias por tu interés en contribuir a **Sovereign Seasons**! Este documento describe los principios arquitectónicos, estándares de código y el flujo de trabajo para colaborar en el proyecto.

---

## 📋 Tabla de Contenidos

1. [Principios del Proyecto](#-principios-del-proyecto)
2. [Estructura del Repositorio](#-estructura-del-repositorio)
3. [Requisitos del Entorno](#-requisitos-del-entorno)
4. [Flujo de Trabajo Git](#-flujo-de-trabajo-git)
5. [Estándares de Código](#-estándares-de-código)
6. [Pruebas Automatizadas](#-pruebas-automatizadas)
7. [Proceso de Pull Request](#-proceso-de-pull-request)

---

## 🎯 Principios del Proyecto

- **Tiempo de Juego Orgánico:** El calendario estacional está estrictamente vinculado a los ticks mundiales de Minecraft (`24.000` ticks = 1 día in-game), desacoplado del reloj real.
- **Rendimiento Extremo (Paper & Folia):** Todos los sistemas deben ser thread-safe y estar optimizados para evitar lag spikes mediante cachés LRU espaciales acotadas y computación reactiva.
- **Desacoplamiento Modular:** Cada subsistema (`core`, `pantry`, `logistics`, `commerce`, `resourcepack`) tiene una responsabilidad única y se comunica mediante la API pública (`SovereignSeasonsApi`).
- **Resiliencia ante Fallos:** Manejo defensivo ante cancelaciones de eventos, desbordamientos numéricos, comandos mal formados y caídas abruptas del servidor.

---

## 🏛️ Estructura del Repositorio

```
sovereign-seasons/
├── sovereign-seasons-core/        # Motor central: calendario, estaciones, invernaderos y API
├── sovereign-seasons-pantry/      # Descomposición de alimentos y bodegas frías
├── sovereign-seasons-logistics/   # Logística de transporte, fardos y silos
├── sovereign-seasons-commerce/    # Economía estacional y contratos de suministro
├── sovereign-seasons-resourcepack/# Texturas, glifos y recursos visuales
└── docker/                        # Infraestructura y scripts de entorno de pruebas
```

---

## 💻 Requisitos del Entorno

- **JDK 21** (Eclipse Temurin recomendado).
- **Git 2.40+**.
- **Docker & Docker Compose** (opcional, para ejecutar servidores de prueba locales).
- Gradle Wrapper incluido (`./gradlew` en Linux/macOS, `gradlew.bat` en Windows).

---

## 🌿 Flujo de Trabajo Git

1. Haz un fork del repositorio o crea una rama descriptiva desde `master`:
   ```bash
   git checkout -b feat/nombre-caracteristica
   # o
   git checkout -b fix/descripcion-del-bug
   ```
2. Sigue la convención de **Conventional Commits**:
   - `feat(modulo): descripción de nueva funcionalidad`
   - `fix(modulo): corrección de error`
   - `docs(modulo): mejoras o adición de documentación`
   - `test(modulo): adición o refactorización de pruebas unitarias`
   - `perf(modulo): mejoras de rendimiento o consumo de memoria`
   - `refactor(modulo): cambios internos sin alteración de comportamiento`
   - `chore(modulo): tareas de mantenimiento, dependencias o CI`
3. Asegúrate de que todos los commits utilicen tu identidad de Git real. No agregues firmas de coautoría automatizada ni menciones de herramientas de IA.

---

## 📐 Estándares de Código

### Java 21 y Arquitectura
- Aprovecha características modernas de Java 21: `record` para DTOs y claves de caché inmutables, pattern matching en `instanceof`, expresiones `switch` exhaustivas.
- **Gestión de Memoria:**
  - **NUNCA** retengas instancias de `org.bukkit.World` ni `org.bukkit.block.Block` en colecciones estáticas o cachés de larga duración para prevenir fugas de memoria (memory leaks).
  - Almacena únicamente `UUID` de mundos o coordenadas desacopladas (`int x, y, z`).
  - Utiliza `CacheBuilder` de Guava con expiración por tiempo de vida (`expireAfterWrite`) y tamaño máximo (`maximumSize`).
- **Compatibilidad con Folia:**
  - No asumas la existencia de un hilo principal global para la lógica de bloques o mundos.
  - Utiliza `FoliaLib` para planificar tareas asíncronas o atadas a regiones espaciales.
- **Validación Defensiva:**
  - Valida entradas de usuario y configuraciones con límites seguros (p. ej. días por estación > 0, límites de mundos de -64 a 320).

---

## 🧪 Pruebas Automatizadas

Antes de enviar cualquier cambio, ejecuta la suite de pruebas automatizadas:

```bash
# Ejecutar todas las pruebas unitarias
./gradlew test

# Compilar artefactos sombreados
./gradlew shadowJar
```

- Cada nueva característica o corrección de error **DEBE** incluir pruebas unitarias o de integración con MockBukkit / JUnit 5.
- La suite de pruebas debe mantener el 100% de éxito.

---

## 🚀 Proceso de Pull Request

1. Actualiza tu rama con respecto a `master` (`git rebase origin/master`).
2. Verifica que el build pase limpiamente sin errores de compilación ni fallos en pruebas.
3. Abre un Pull Request utilizando la plantilla predeterminada ([`PULL_REQUEST_TEMPLATE.md`](.github/PULL_REQUEST_TEMPLATE.md)).
4. Asegúrate de describir claramente la motivación, los módulos afectados y las pruebas realizadas.
5. El pipeline de CI validará automáticamente la compilación y la suite de pruebas en Ubuntu con Java 21 Temurin.
