# 🤖 Pruebas end-to-end (servidor Paper local + bot)

Pruebas de juego reales sin Docker: un bot [Mineflayer](https://github.com/PrismarineJS/mineflayer) entra en un servidor Paper local en modo offline, y la consola se controla por RCON. Cada suite devuelve código de salida `0` solo si **todas** sus comprobaciones pasan.

| Suite | Archivo | Duración | Qué cubre |
|---|---|---|---|
| Principal | `e2e.js` | ~4 min | 33 comprobaciones de juego de core y pantry (+ PlaceholderAPI si está instalado) |
| Logistics | `e2e-logistics.js` | ~3 min | 22 comprobaciones de nieve, barro, caminos, monturas, ventiscas, lagos helados, peso y cajas (+ frescura con Pantry) |
| Naturaleza | `e2e-nature.js` | ~5 min | Lluvia natural por estación y aparición natural de Strays/Husks, acelerando el servidor con `/tick sprint` |

## Compatibilidad verificada

| Versión de Paper | Java | Resultado (suites principal + logistics + naturaleza) |
|---|---|---|
| 1.21.11 (build 132) | Temurin 21 | 33/33 + 22/22 + 5/5, con PlaceholderAPI 2.12.3 |
| 26.1.2 (build 74) | Temurin 25 | 33/33 + 22/22 + 5/5, con PlaceholderAPI 2.12.3 |
| 26.3 (build 227) | Temurin 25 | Prueba de humo por consola: carga, comandos, placeholders, recargas y sprint sin errores. El bot no se usa porque Mineflayer aún no soporta el protocolo 26.3. |

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
1. Copia los JAR recién compilados.
2. Arranca el servidor una vez para que se generen las configs.
3. Aplica la configuración determinista de pruebas con `apply-test-config.js` (gemelos 100 %, compost 100 % y revisión del inventario cada 100 ticks).
4. Reinicia el servidor sobre un **mundo limpio**.
5. Ejecuta las suites y lista cualquier línea de error o aviso de los plugins.

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
- **Pantry:**
  - `/pantry inspect`, `/pantry stats` y `/pantry setfresh`. Comer comida rancia da Hambre.
  - El ahumador no se atasca y marca "Ahumado".
  - La carne podrida llena un compostador.
  - La carne cruda de un cofre se pudre tras cinco días de verano.
  - La carne del inventario se pudre y el jugador recibe el aviso.

## Lo que no cubre
- **Lo visual (escarcha en pantalla, colores, tooltips):** sigue la guía [MANUAL_CHECKS.md](MANUAL_CHECKS.md) con tu propio cliente.
- **El bot en Paper 26.3:** espera a que Mineflayer soporte ese protocolo. Mientras tanto, solo hay prueba de humo por consola.
