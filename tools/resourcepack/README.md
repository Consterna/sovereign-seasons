# 🎨 Herramientas del resource pack

## Generar el pack
Todas las texturas y los JSON de `sovereign-seasons-resourcepack/src/pack` salen de un único script, solo con la librería estándar de Python. Así la tabla de modelos de las cajas está en un único sitio, y una prueba Java comprueba que coincide con `CrateModels` de Logistics.

```bash
python tools/resourcepack/generate_pack.py sovereign-seasons-resourcepack/src/pack --preview preview.png
```

- **Arte original:** todo es procedural y no se copia ningún recurso de Minecraft.
- **`--preview`:** dibuja muestras de hierba y hojas por bioma (verano vanilla, primavera, otoño e invierno) para revisar los colores antes de entrar al juego.
- **Gradle:** empaqueta cada carpeta en un zip reproducible, con fechas y orden fijos. El SHA-1 solo cambia si cambia el contenido, así que los jugadores reutilizan su copia en caché.

## Validar con el código del propio juego
Los dos programas Java de esta carpeta pasan los archivos del pack por los lectores **reales** de Minecraft, no por una imitación.

| Programa | Qué valida | Necesita |
|---|---|---|
| `PackMetaCheck.java` | `pack.mcmeta`, con el lector de metadatos de packs | El JAR de Paper de cada versión y sus `libraries/` (los del servidor de pruebas) |
| `ClientPackCheck.java` | Definiciones de ítem (`items/`), modelos (`models/`) y fuentes (`font/`) | El `client.jar` oficial de 26.x, que Mojang publica sin ofuscar, y las librerías del servidor de esa versión |

```bash
# Compilar contra el JAR de Paper (o el client.jar) y sus librerías; ejecutar con las rutas de los packs
javac -proc:none -cp "<paper.jar>;<libraries...>" -d out tools/resourcepack/PackMetaCheck.java
java -cp "out;<paper.jar>;<libraries...>" PackMetaCheck sovereign-seasons-resourcepack/src/pack/base/pack.mcmeta
```

`ClientPackCheck` hace lo mismo que `net.minecraft.client.ClientBootstrap` al registrar los tipos de modelo, salvo el selector `select`, que necesita ICU4J (una librería solo del cliente) y que el pack no usa. Para comprobar que el arnés de verdad detecta errores, se le pasaron a propósito archivos rotos: un tipo de propiedad desconocido, un umbral no numérico, `ascent` mayor que `height` y texturas mal formadas. Los cuatro fueron rechazados con el mismo mensaje que da el juego.

## Datos verificados en el código del juego
| Dato | Fuente | Resultado |
|---|---|---|
| Formato de resource pack | `version.json` de los JAR oficiales | 1.21.11 = 75.0, 26.1.2 = 84.0, 26.3 = 97.1 |
| Reglas de `pack.mcmeta` | `PackFormat` (1.21.11, 26.1.2 y 26.3) | Hasta el 64, `pack_format` y `supported_formats`; desde el 65, `min_format` y `max_format`. Nuestro pack se resuelve como **[22.0, 97.*]**, de 1.20.4 a 26.3. El antiguo `pack_format: 46` solo cargaba en 1.21.4. |
| Modelo de las cajas | `CraftMetaItem#setCustomModelData` (Paper 1.21.11 y 26.3) | Guarda el número como `floats[0]` del componente `custom_model_data`, así que el barril usa `range_dispatch` con índice 0. Hasta 1.21.3 se usan los `overrides` del modelo. |
| Lectura de colormaps | `ColorMapColorUtil` | `humedad ×= temperatura`; `x = (1 − temperatura) × 255`; `y = (1 − humedad) × 255`; textura de 256 × 256 |
| Bloques teñidos | `BlockColors` del cliente 26.3 | Hojas de roble, jungla, acacia, roble oscuro y mangle, y enredaderas: colormap de hojas. Abeto y abedul: color **constante**. Hierba, helechos, arbustos y pétalos: colormap de hierba. Hojarasca: `dry_foliage`. |
| Biomas con color propio | Datos de biomas de 1.21.11 | Badlands (los tres), bosque de cerezos y jardín pálido: color fijo. Pantano y manglar: hojas fijas y modificador propio en la hierba. El bosque oscuro sí sigue al colormap. |
| Fuentes de varios packs | `FontManager` del cliente 26.3 | Lee el mismo archivo de fuente de todos los packs y los combina (`listMatchingResourceStacks`). El pack usa su propia fuente, `sovereignseasons:icons`, para no tocar la de nadie. |
