# 📦 Sovereign Seasons - Logistics (Módulo 3)

Transporte y movilidad según la estación. Requiere **`sovereign-seasons-core` 1.1.0+**. Si **`sovereign-seasons-pantry`** está instalado, las cajas conservan la frescura de la comida.

Sigue la filosofía *vanilla+*: viajar y cargar requiere planificación, y las soluciones son de Minecraft (botas de cuero, caminos, mulas, vagonetas, barcos sobre el hielo). Todo es configurable y desactivable.

---

## 🌟 Características

### 1. Nieve y barro estacionales
- **Nieve:**
  - 1–2 capas dan *Lentitud I*.
  - La nieve profunda (≥ 3 capas, bloque de nieve o nieve polvo) da *Lentitud II*.
  - Unas **botas de cuero** permiten andar sin penalización, como en la nieve polvo vanilla.
- **Barro:** cuando **llueve** (no cuando nieva) en primavera u otoño, la hierba, la tierra, la tierra de cultivo, el podzol y el micelio ralentizan (*Lentitud I*). El bloque de barro ralentiza siempre.
- **Monturas** (caballos, burros, mulas, llamas, camellos): sufren las mismas penalizaciones. Un caballo con **armadura de caballo de cuero** no se hunde en la nieve.

### 2. Caminos que se notan
- Los caminos anulan la nieve fina y el barro: sendero de tierra, grava, adoquines, ladrillos de piedra, ladrillos, losas y escaleras de esos materiales, piedra lisa, ladrillos de barro, **todos los tablones** y **todos los hormigones**.
- Las **monturas** van más rápido sobre un camino (*Velocidad I*). A pie solo cuenta la inmunidad, aunque se puede activar un bonus con `roads.player-speed`.
- La nieve profunda (más de 2 capas) también tapa los caminos: **hay que despejarlos** en invierno.

### 3. Ventiscas
- Si nieva en invierno en una zona **Helada** y estás a cielo abierto, se suma un nivel de *Lentitud*, también en los caminos.
- Resguardarse bajo techo la anula.
- No hay daño, ceguera ni niebla.
- Nota: sin modificar el servidor (NMS) no se puede cambiar lo que dibuja el cliente. En biomas templados que nuestro calendario vuelve Helados (p. ej. taiga en invierno), el cliente puede mostrar lluvia aunque para el plugin sea ventisca.

### 4. Lagos helados
- En invierno, en zonas Heladas, el **agua quieta superficial** cercana a los jugadores se congela poco a poco y crea rutas de hielo para viajar.
- No se congela el agua donde hay alguien dentro ni la que está bajo un invernadero del core.
- Al terminar el invierno se derrite **solo el hielo que puso el plugin**, que se registra en cada chunk. El hielo natural y el colocado por jugadores nunca se toca.
- Se trabaja por muestreo (8 columnas por jugador cada 2 s, en un radio de 48 bloques) para no afectar al rendimiento.

### 5. Cajas a granel
- **Empaquetar:** agáchate y haz **clic derecho mirando al aire** con al menos 9 unidades de un cultivo en la mano.
  - Cultivos: zanahorias, patatas, remolachas, manzanas, bayas dulces y luminosas, rodajas de melón, cacao y caña de azúcar.
  - Se obtiene una *Caja de X* (9 unidades).
  - Al hacer clic derecho sobre un bloque se sigue plantando con normalidad.
  - El trigo ya tiene el fardo de heno vanilla.
- **Desempaquetar:** clic derecho con la caja en la mano.
- Las cajas hechas del mismo stack se **apilan** (hasta 64 cajas = 576 unidades por casilla).
- No se pueden colocar como barril ni quemar como combustible.
- **Con Pantry:** dentro de la caja la comida se estropea a **×0,5**, sin importar dónde esté la caja. Al desempaquetar, recupera su frescura real; si caducó dentro, sale carne podrida.
- Se usa interacción en lugar de recetas de crafteo a propósito: el *crafter* automático no puede fabricar cajas sin estado que "refresquen" comida podrida.

### 6. Peso de carga *(desactivado por defecto)*
- Cada ítem pesa según reglas configurables: bloques metálicos 9; piedra y minerales 1; herramientas y armaduras 2; cosechas y comida 0,25; el resto 0,5.
- El contenido de las cajas de shulker y los bundles cuenta al 50 %, y las cajas de Logistics al 60 % de su contenido suelto.
- Por encima del 60 % de la capacidad, *Lentitud I*; por encima del 85 %, *Lentitud II*. Se avisa en la barra de acción.
- **Animales de carga premiados:** solo cuenta lo que lleva encima el jugador. Mulas, llamas, caballos con cofre, vagonetas, cofres y el cofre de Ender no pesan. Ir montado tampoco ralentiza por peso.

---

## 🕹️ Comandos y Permisos

| Comando | Descripción | Permiso |
|---|---|---|
| `/logistics` o `/logistics status` | Terreno bajo tus pies (o los de tu montura), ventisca, efecto aplicado y carga. | `sovereignseasons.logistics.status` |
| `/logistics weight` | Peso de tu inventario frente a la capacidad. | `sovereignseasons.logistics.status` |
| `/logistics stats` | Funciones activas y contadores de lagos congelados y derretidos. | `sovereignseasons.logistics.admin` |
| `/logistics reload` | Recarga `config.yml`. | `sovereignseasons.logistics.admin` |

Alias: `/carga`.

---

## ⚙️ Configuración (`config.yml`)

```yaml
mobility:
  snow:  { slowness: 1, deep-slowness: 2, deep-layers: 3, leather-boots-protect: true, leather-horse-armor-protect: true }
  mud:   { slowness: 1, seasons: [spring, autumn] }
  roads: { mount-speed: 1, player-speed: 0, max-snow-layers: 2 }
  blizzard: { extra-slowness: 1 }
weight:
  enabled: false
  capacity: 1000
  rules:
    - pattern: ".*_ORE|STONE|COBBLESTONE|..."
      weight: 1.0
crates:  { items-per-crate: 9, packed-decay-rate: 0.5 }
ice:     { samples-per-player: 8, radius: 48, max-thaw-per-chunk: 64 }
```

Todos los valores se validan y acotan al cargarse. Si el YAML está mal formado, el plugin sigue funcionando con los valores por defecto y avisa en la consola. Los intervalos de las tareas periódicas solo cambian al reiniciar el servidor.

---

## 🧪 Pruebas

```bash
./gradlew :sovereign-seasons-logistics:test
tools/e2e/run-local.sh testserver /ruta/a/java 1.21.11
```

- **Pruebas unitarias:** clasificación del terreno, efectos de movimiento, peso, matemática de las cajas, reglas de hielo, registro por chunk, configuración y comandos.
- **Pruebas de juego** (`tools/e2e/e2e-logistics.js`, 22 comprobaciones en un servidor real):
  - nieve, botas de cuero y barro con lluvia frente a sendero de tierra;
  - caballo en grava y en nieve, con y sin armadura de cuero;
  - ventisca a cielo abierto frente a bajo techo;
  - lago que se congela y se derrite sin tocar el hielo puesto a mano;
  - peso y cajas, incluida la frescura conservada con Pantry.

Artefacto compilado: `sovereign-seasons-logistics/build/libs/sovereign-seasons-logistics-1.0.0.jar` (FoliaLib relocalizado en `com.sovereignseasons.logistics.libs.folialib`).
