# 🤖 Pruebas end-to-end (servidor Paper local + bot)

Pruebas de juego reales sin Docker: un bot [Mineflayer](https://github.com/PrismarineJS/mineflayer) entra en un servidor Paper local en modo offline, y la consola se controla por RCON. El script devuelve código de salida `0` solo si **todas** las comprobaciones pasan.

## Requisitos
- Java 21+ (por ejemplo, Temurin 21).
- Node.js 18+.
- Paper 1.21.11 (las gamerules usan los nombres nuevos `advance_time`, `spawn_mobs`, `random_tick_speed`) descargado desde la [API oficial de PaperMC](https://fill.papermc.io/v3/projects/paper/versions/1.21.11/builds/latest). Antes de usarlo, verifica su SHA-256.
- Aceptar el [EULA de Minecraft](https://aka.ms/MinecraftEULA) (`eula.txt`).

## Preparación (una vez)
1. Crea `testserver/` en la raíz del repositorio (está en `.gitignore`) y coloca ahí `paper.jar`.
2. Crea `testserver/eula.txt` con `eula=true`, solo si aceptas el EULA.
3. Crea `testserver/server.properties` con al menos:
   ```properties
   server-ip=127.0.0.1
   server-port=25566
   online-mode=false
   level-type=minecraft\:flat
   spawn-monsters=false
   enable-rcon=true
   rcon.port=25576
   rcon.password=<contraseña aleatoria local>
   ```
4. Instala el bot: `cd tools/e2e && npm install`.

## Ejecución
```bash
./gradlew clean build
cp sovereign-seasons-core/build/libs/*.jar sovereign-seasons-pantry/build/libs/*.jar testserver/plugins/
cd testserver && java -Xmx2G -jar paper.jar --nogui
```

En otra terminal, cuando el servidor muestre `Done` por primera vez (así se generan las configs):

```bash
# Hace deterministas las mecánicas aleatorias SOLO en el servidor de pruebas
# (gemelos 100 %, compost 100 %, revisión de inventario cada 100 ticks)
node tools/e2e/apply-test-config.js testserver
node tools/e2e/rcon-cli.js testserver/server.properties stop
```

Antes de **cada** pasada, borra `testserver/world*` y `plugins/SovereignSeasons-Core/calendar_data.yml`: el bot reaparece donde se desconectó y el mundo arrastraría biomas, bloques y animales de la pasada anterior. Después vuelve a arrancar el servidor y ejecuta:

```bash
node tools/e2e/e2e.js
node tools/e2e/rcon-cli.js testserver/server.properties stop
```

## Qué se comprueba (32 comprobaciones)
- Ambos plugins habilitados. `/season info` muestra la temperatura y `/season hud` enseña la BossBar.
- El reloj muestra la fecha y aparece un título al cambiar de estación.
- **Frío:** las llanuras nevadas en invierno dan Lentitud y aviso, sin daño; una pieza de cuero protege.
- **Calor:** el desierto en verano da el aviso de calor.
- **Cultivos:** las patatas crecen más rápido en su clima ideal que en un desierto caluroso.
- **Fauna:** gemelos al criar en primavera.
- **Despensa:** `/pantry inspect`, `/pantry stats` y `/pantry setfresh`. Comer comida rancia da Hambre.
- **Despensa:** el ahumador no se atasca y marca "Ahumado".
- **Despensa:** la carne podrida llena el compostador, y la carne cruda de un cofre se pudre tras cinco días de verano.
- **Despensa:** la carne del inventario se pudre y el jugador recibe el aviso.

## Lo que no cubre
- **Lluvia estacional:** vanilla no permite provocar a voluntad un inicio de lluvia *natural*, así que solo la cubren los tests unitarios.
- **Strays y Husks:** el servidor de pruebas desactiva la aparición natural de mobs para que no maten al bot, así que solo los cubren los tests unitarios.
- **PlaceholderAPI:** no está instalado en el servidor de pruebas.
- **Lo visual (escarcha en pantalla, colores):** requiere un cliente real.
