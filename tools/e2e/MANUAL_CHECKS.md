# 👀 Revisión visual con tu cliente de Minecraft

El bot no puede *ver* la pantalla. Esta guía sirve para comprobar a ojo lo visual con tu propio Minecraft **1.21.11** (Java Edition), entrando al servidor de pruebas local.

## 1. Conectarte
1. Arranca el servidor de pruebas como se explica en [README.md](README.md). Debe aparecer `Done` en la consola.
2. En Minecraft: **Multijugador → Conexión directa →** `127.0.0.1:25566`.
   El servidor está en modo *offline* y solo escucha en tu propio equipo (`127.0.0.1`), así que nadie de fuera puede entrar.
3. Hazte operador escribiendo en la consola del servidor (o por RCON con `node tools/e2e/rcon-cli.js testserver/server.properties op <tu_nombre>`):
   ```
   op <tu_nombre>
   ```

## 2. Qué mirar

| # | Prueba | Comandos (en el chat del juego) | Qué deberías ver |
|---|---|---|---|
| 1 | Título de estación | `/season set winter world` | Título **Invierno** con degradado y el subtítulo "Comienza una nueva estación · Año N", con un sonido de campanilla. |
| 2 | HUD | `/season hud` | Una BossBar arriba: `Invierno (Temprano) · Día 1/7 · Año N · X°C Banda`, de color blanco en invierno. Vuelve a escribirlo para ocultarla. |
| 3 | Reloj | `/give @s clock` y clic derecho | La fecha y la temperatura en la barra de acción, encima del inventario. |
| 4 | Frío | `/gamemode survival`, `/time set noon`, `/fillbiome ~-10 -64 ~-10 ~10 -50 ~10 minecraft:snowy_plains` y espera 2 s | **Escarcha en los bordes de la pantalla** (como en la nieve polvo), el icono de Lentitud y el aviso "Hace un frío helado…". La vida **no baja**. |
| 5 | Protección con cuero | `/give @s leather_helmet` y póntelo | La escarcha desaparece en unos segundos y deja de aplicarse la Lentitud. |
| 6 | Calor | `/season set summer world`, `/fillbiome ~-10 -64 ~-10 ~10 -50 ~10 minecraft:desert`, de día y al sol | El aviso "El calor es sofocante…" y que el hambre baja algo más rápido de lo normal. |
| 7 | Lore de comida | `/give @s bread 3` y espera ~1 minuto (o abre y cierra un cofre que lo contenga) | En el tooltip del pan: **Frescura: Fresco** en verde. |
| 8 | Comida rancia | Con el pan en la mano: `/pantry setfresh 30` | El tooltip pasa a **Rancio** en amarillo. Al comerlo, tienes Hambre unos segundos. |
| 9 | Ahumado | Cocina carne cruda en un **ahumador** y sácala | Tooltip con **Conservación: Ahumado** en dorado. El ahumador sigue cocinando el resto. |
| 10 | Compost | `/give @s rotten_flesh 16` y clic derecho en un compostador | Las partículas verdes y que el nivel del compostador sube a veces (30 %). |
| 11 | Precios de invierno | `/season set winter world`, `/summon villager ~2 ~ ~ {NoAI:1b,Xp:10,VillagerData:{profession:"minecraft:farmer",level:2,type:"minecraft:plains"},Offers:{Recipes:[{buy:{id:"minecraft:carrot",count:22},sell:{id:"minecraft:emerald",count:1},maxUses:99}]}}` y clic derecho sobre él | En el trato de zanahorias, el **22 tachado** y al lado **16**; en la barra de acción, "Precios de Invierno". Al cerrar y volver en verano (`/season set summer world`), vuelve a 22 sin tachar. |
| 12 | Feria de verano | `/season set summer world`, `/summon wandering_trader ~2 ~ ~ {NoAI:1b}` y clic derecho | Al final de la lista, hielo, hielo compacto y hielo azul; en la barra de acción, "Feria de Verano: ofertas de temporada". |
| 13 | Resource pack | Al entrar, acepta el pack (dos avisos: base y estación); `/season set autumn world` | La hierba y las hojas de roble se vuelven doradas y naranjas tras recargar texturas un par de segundos. Las de abeto y abedul no cambian (el juego las pinta con color fijo). |
| 14 | Caja con modelo | `/give @s carrot 9`, agáchate y haz clic derecho al aire | La caja se ve como una caja de madera con zanahorias en la tapa, no como un barril. |
| 15 | Icono de estación | `/season hud` | En la BossBar, el icono de la estación (una hoja en otoño) delante del nombre. |

Si algo no se ve como dice la tabla, anota el número de la prueba y lo que viste, y lo revisamos.
