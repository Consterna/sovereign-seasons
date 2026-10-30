# 🤖 Pruebas end-to-end (servidor Paper local + bot)

Pruebas de juego reales sin Docker: un bot [Mineflayer](https://github.com/PrismarineJS/mineflayer) entra en un servidor Paper local en modo offline, y la consola se controla por RCON. Cada suite devuelve código de salida `0` solo si **todas** sus comprobaciones pasan.

| Suite | Archivo | Duración | Qué cubre |
|---|---|---|---|
| Principal | `e2e.js` | ~4 min | 33 comprobaciones de juego de core y pantry (+ PlaceholderAPI si está instalado) |
| Logistics | `e2e-logistics.js` | ~3 min | 25 comprobaciones de nieve, barro, caminos, monturas, ventiscas, lagos helados, peso y cajas (+ frescura con Pantry) |
| Commerce | `e2e-commerce.js` | ~2 min | 24 comprobaciones de precios estacionales (cliente y servidor), comida podrida rechazada, venta a granel de cajas, límite diario y ferias del vendedor ambulante |
| Resource pack | `e2e-resourcepack.js` | ~1 min | 16 comprobaciones: el bot hace de cliente (recibe los packs, los descarga y comprueba su SHA-1, responde y sigue los cambios de estación) |
| Naturaleza | `e2e-nature.js` | ~5 min | Lluvia natural por estación y aparición natural de Strays/Husks, acelerando el servidor con `/tick sprint` |

## Compatibilidad verificada

| Versión de Paper | Java | Resultado (suites principal + logistics + commerce + resource pack + naturaleza) |
|---|---|---|
| 1.21.11 (build 132) | Temurin 21 | 33/33 + 25/25 + 24/24 + 16/16 + 5/5, con PlaceholderAPI 2.12.3 |
| 26.1.2 (build 74) | Temurin 25 | 33/33 + 25/25 + 24/24 + 16/16 + 5/5, con PlaceholderAPI 2.12.3 |
| 26.3 (build 227) | Temurin 25 | Prueba de humo por consola: carga de los cuatro plugins, comandos (incluido `/commerce`), placeholders y recargas sin errores. El bot no se usa porque Mineflayer aún no soporta el protocolo 26.3. |

## Requisitos
- Java 21 para Paper 1.21.x y **Java 25 para Paper 26.x**. Sirve un JDK portable en ZIP de [Adoptium](https://adoptium.net/); no hace falta instalarlo.
- Node.js 18+.
- El JAR de Paper descargado desde la [API oficial de PaperMC](https://fill.papermc.io/v3/projects/paper). Verifica su SHA-256.
- Aceptar el [EULA de Minecraft](https://aka.ms/MinecraftEULA) (`eula.txt`).
- Opcional: [PlaceholderAPI](https://github.com/PlaceholderAPI/PlaceholderAPI/releases) para probar los placeholders.

Las gamerules usan los nombres nuevos de 1.21.11+ (`advance_time`, `advance_weather`, `spawn_mobs`, `random_tick_speed`).

## Preparación (una vez por versión)
1. Crea una carpeta de servidor en la raíz del repositorio (`testserver*/` está en `.gitignore`) y coloca ahí `paper.jar`.
2. Crea `eula.txt` con `eula=true`, solo si aceptas el EULA.
3. Crea `server.properties` con al menos:
   ```properties
   server-ip=127.0.0.1
   server-port=25566
   online-mode=false
   level-type=minecraft\:flat
   enable-rcon=true
   rcon.port=25576
   rcon.password=<contraseña aleatoria local>
   ```
4. Instala el bot: `cd tools/e2e && npm install`.

## Ejecución
```bash
./gradlew clean build
tools/e2e/run-local.sh testserver /ruta/a/jdk-21/bin/java 1.21.11 --nature --extra-plugin PlaceholderAPI-2.12.3.jar
```

El script `run-local.sh` hace todo el ciclo en cada pasada:
1. Comprueba que ningún JAR de plugin sea más antiguo que el código de su módulo y, si lo es, se para. `./gradlew assemble` solo regenera los JAR normales; el del plugin sale de `shadowJar` (o de `build`).
2. Copia los JAR recién compilados.
3. Arranca el servidor una vez para que se generen las configs.
4. Aplica la configuración determinista de pruebas con `apply-test-config.js` (gemelos 100 %, compost 100 % y revisión del inventario cada 100 ticks).
5. Reinicia el servidor sobre un **mundo limpio**.
6. Ejecuta las suites y lista cualquier línea de error o aviso de los plugins.

El mundo se borra en cada pasada porque el bot reaparece donde se desconectó y arrastraría el estado de la pasada anterior. Para Paper 26.1.x usa Java 25 y la versión de protocolo `26.1`.

## Qué se comprueba
- **Core:**
  - Ambos plugins habilitados. `/season info` muestra la temperatura y `/season hud` enseña la BossBar.
  - El reloj muestra la fecha y aparece un título al cambiar de estación.
  - **Frío:** las llanuras nevadas en invierno dan Lentitud y aviso, sin daño; una pieza de cuero protege.
  - **Calor:** el desierto en verano da el aviso de calor.
  - **Cultivos:** las patatas crecen ~3 veces más en su clima ideal que en un desierto caluroso.
  - **Fauna:** gemelos al criar en primavera.
  - **PlaceholderAPI:** los 9 placeholders resuelven.
- **Naturaleza:**
  - Con probabilidad 1.0 la lluvia natural empieza; con 0.0 se bloquea **cada** inicio. El log del servidor (`weather.debug`) es la fuente de verdad, porque el bot también recibe el desvanecimiento de la lluvia anterior.
  - El servidor sigue sano: no hay bucle de eventos.
  - Los esqueletos pasan a Strays en llanuras en invierno y los zombis a Husks en sabana en verano. Se usan biomas donde vanilla **nunca** genera esas variantes.
- **Logistics:**
  - Nieve fina y profunda, botas de cuero, barro con lluvia en otoño frente a sendero de tierra.
  - Caballo en grava (Velocidad) y en nieve (Lentitud, salvo con armadura de cuero).
  - Ventisca a cielo abierto frente a bajo techo.
  - Un estanque en taiga se congela en invierno y se derrite en primavera sin tocar el hielo colocado a mano. Se usa taiga porque vanilla no lo congela, así que todo el hielo es del plugin.
  - El peso sobrecarga con 15 stacks de piedra.
  - Las cajas se empaquetan, se apilan, se desempaquetan, no se colocan y conservan la frescura de Pantry.
  - Con una caja en la mano, un cofre se abre normalmente, y la comida que caduca dentro de la caja sale podrida.
  - Una tormenta en una sabana (donde vanilla nunca llueve) no crea barro.
- **Commerce:**
  - En invierno el granjero pide 16 zanahorias por esmeralda en vez de 22 (visto por el cliente y en el NBT del servidor), 28 en otoño y 22 en verano. Las zanahorias doradas suben de 3 a 4 esmeraldas y un trato de 1 esmeralda no baja de 1.
  - Al cerrar el comercio no queda ningún ajuste guardado en el aldeano.
  - Un trato real a precio de invierno con zanahorias de Pantry funciona, y las zanahorias que se pudren se rechazan sin perder nada.
  - Agachado y con cajas en la mano, el aldeano las compra a precio de temporada sin abrir el comercio. Respeta el límite diario, rechaza cajas de comida podrida y un aldeano que no compra zanahorias tampoco compra sus cajas.
  - El vendedor ambulante añade la feria de verano, la sustituye por la de invierno al cambiar de estación y sus precios se borran al cerrar sin acumularse. Al desactivar las ferias, devuelve las ofertas añadidas y conserva las suyas.
  - Mineflayer siempre declara «no agachado» al usar una entidad, así que la prueba envía ella misma el paquete `use_entity` con `sneaking: true`, como un cliente real.
- **Resource pack:**
  - Al entrar, el cliente recibe el pack base y el de la estación, opcionales. Cada zip se descarga de su URL con el SHA-1 anunciado, y el servidor de packs no sirve nada más (404) ni acepta otros métodos (405).
  - Con el pack cargado, el HUD muestra el icono de la estación; un segundo bot que nunca carga el pack no recibe icono.
  - En otoño se quita el pack de primavera y se pone el de otoño; en verano se quita sin poner otro. Tras rechazar un pack no se insiste, y `/seasonpack resend` lo vuelve a enviar.
  - Las cajas de Logistics llevan `custom_model_data` 7301001 (zanahorias).
  - Mineflayer ya usa `bot.bossBars`, así que la suite guarda los títulos con otro nombre.
- **Pantry:**
  - `/pantry inspect`, `/pantry stats` y `/pantry setfresh`. Comer comida rancia da Hambre.
  - El ahumador no se atasca y marca "Ahumado".
  - La carne podrida llena un compostador.
  - La carne cruda de un cofre se pudre tras cinco días de verano.
  - La carne del inventario se pudre y el jugador recibe el aviso.

## Lo que no cubre
- **Lo visual (escarcha en pantalla, colores, tooltips):** sigue la guía [MANUAL_CHECKS.md](MANUAL_CHECKS.md) con tu propio cliente.
- **El bot en Paper 26.3:** espera a que Mineflayer soporte ese protocolo. Mientras tanto, solo hay prueba de humo por consola.
