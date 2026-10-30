# 🎨 Sovereign Seasons - Resource Pack (Módulo 5)

Detalles visuales de las estaciones, enviados y alojados por el propio plugin. Todo es **opcional para el jugador**: quien rechaza el pack juega igual, sin estos detalles, y no se le vuelve a preguntar.

## Qué añade
- **Colores de estación en hierba y hojas:**
  - en **primavera**, verdes más vivos;
  - en **otoño**, hierba dorada y hojas amarillas, naranjas o rojas según el clima;
  - en **invierno**, tonos apagados y oliváceos;
  - el **verano** usa los colores normales del juego.

  Los biomas calurosos (desierto, sabana, jungla) apenas cambian. Al cambiar la estación, o al pasar a otro mundo con otra estación, solo se cambia este pack.
- **Cajas de Logistics con modelo propio:** listones de madera y, en la tapa, el color de cada cultivo (zanahorias, patatas, remolachas, manzanas, bayas, melón, cacao y caña). Sin el pack se siguen viendo como barriles.
- **Iconos de estación** (flor, sol, hoja y copo) junto al nombre de la estación en el título, el reloj y la BossBar del HUD. Solo aparecen a quien tiene el pack cargado; a cualquier otro cliente le saldría un recuadro vacío.

### Qué no cambia (límites del juego)
- **Hojas de abeto y abedul:** el juego les da un color constante, no del colormap.
- **Hojas de cerezo, azalea y roble pálido:** no se tiñen.
- **Biomas con color propio:** badlands, bosque de cerezos y jardín pálido; también las hojas de pantanos y manglares.
- **Colores por estación, no por día:** cambiar los colores obliga al cliente a recargar texturas (un par de segundos), algo aceptable una vez por estación. Por eso no hay un degradado continuo.

## Cómo funciona
| Pack | Contenido | Cuándo se envía |
|---|---|---|
| `base` | Modelo de las cajas e iconos (fuente `sovereignseasons:icons`) | Al entrar |
| `spring`, `autumn`, `winter` | Colormaps de hierba, hojas y hojarasca | Al entrar, al cambiar la estación y al cambiar de mundo; se quita en verano |

- **Envío:** se usa la API de varios packs a la vez de 1.20.3+ (`addResourcePack` / `removeResourcePack`) con identificadores fijos, así que el pack de estación se cambia sin tocar el base.
- **Alojamiento propio, por defecto:** un servidor HTTP mínimo dentro del plugin, en el puerto 8163.
  - Solo sirve los cuatro zip, desde memoria: `GET` y `HEAD` a rutas exactas, sin listados.
  - Está acotado frente a abusos: cuatro hilos con cola limitada (lo que no cabe se cierra), límite de tamaño de la petición, tiempo de espera desde que se acepta la conexión y un máximo de 60 s de vida por conexión, que corta a los clientes lentos al enviar o al leer.
  - No depende de módulos opcionales de Java.
  - La URL usa la dirección con la que el jugador entró al juego, o `hosting.self.public-url` si está detrás de un proxy.
- **Alojamiento externo:** con `hosting.mode: external`, sube a tu web los zip de `plugins/SovereignSeasons-ResourcePack/packs/` y pon su carpeta en `hosting.external.base-url`.
- **Zip reproducibles:** el hash SHA-1 solo cambia si cambia el contenido, así que el jugador no vuelve a descargar el pack en cada reinicio.
- **Versiones:** un único `pack.mcmeta` vale de **1.20.4 a 26.3**, comprobado con el lector real del juego. Para las cajas, el pack trae a la vez el formato nuevo (`items/barrel.json`, 1.21.4+) y el antiguo (`overrides`, hasta 1.21.3).

## Comandos y permisos
| Comando | Permiso | Uso |
|---|---|---|
| `/seasonpack` o `/seasonpack status` (alias `/texturas`) | `sovereignseasons.pack.use` (todos) | Estado de tus packs |
| `/seasonpack resend` | `sovereignseasons.pack.use` | Volver a recibir los packs (por ejemplo, tras haberlos rechazado) |
| `/seasonpack stats` | `sovereignseasons.pack.admin` (op) | Alojamiento, tamaño y SHA-1 de cada pack, envíos, cargas, rechazos y descargas servidas |

## Instalación
1. Requiere **SovereignSeasons-Core 1.1.0+**. Con **Logistics**, las cajas usan su modelo (`crates.custom-model-data: true`, activo por defecto).
2. Abre el puerto **8163** (TCP) para los jugadores, o usa el alojamiento externo.
3. La configuración está comentada en [`config.yml`](src/main/resources/config.yml): packs, estaciones con colores, iconos, `force`, mensaje y alojamiento.

## Desarrollo
- **Fuentes del pack:** están en `src/pack/<pack>/` y se generan con [`tools/resourcepack/generate_pack.py`](../tools/resourcepack/README.md). El arte es original y procedural, sin recursos copiados de Minecraft.
- **Pruebas unitarias (20):**
  - la estructura y las referencias del pack;
  - el tamaño de las texturas;
  - que la tabla del barril coincida con `CrateModels` de Logistics;
  - que los zip incluidos sean idénticos a las fuentes;
  - el servidor HTTP con conexiones reales (incluidos clientes lentos que no terminan su petición y el cierre al parar);
  - las reglas de cambio de pack;
  - las URL;
  - la configuración;
  - el comando.
- **Prueba de juego** (`tools/e2e/e2e-resourcepack.js`): un bot hace de cliente.
  - Recibe los packs, los descarga y comprueba su SHA-1.
  - Responde aceptado, cargado o rechazado.
  - Sigue los cambios de estación.
  - Comprueba que el icono solo llega a quien tiene el pack y que las cajas llevan su número de modelo.
