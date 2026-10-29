# 💰 Sovereign Seasons - Commerce (Módulo 4)

Economía estacional *vanilla+*: el valor de las cosas cambia con las estaciones, pero se sigue comerciando como siempre, **con esmeraldas y aldeanos**. No necesita Vault ni ningún plugin de economía.

## Qué hace

### Precios estacionales de aldeanos y vendedores ambulantes
Cada producto tiene un valor por estación:
- **Cultivos:** abundantes en su temporada de cosecha y escasos en invierno, cuando no crecen al aire libre. Las estaciones de cada cultivo coinciden con la fertilidad estacional del core.
- **Otros productos con valor fijo por estación:** la lana y el carbón valen más en invierno; el pescado también (con lagos helados); la carne abunda en otoño.

Con los valores por defecto, el granjero que pide 22 zanahorias por esmeralda pasa a pedir:

| Estación | Zanahorias por esmeralda |
|---|---|
| Primavera y otoño (temporada de cosecha) | 28 |
| Verano (fuera de temporada) | 22 (precio vanilla) |
| Invierno | 16 |

Detalles:
- Al abrir el comercio, el aldeano muestra el precio nuevo **tachando el original**, igual que con los descuentos vanilla, y la barra de acción avisa: «Precios de Invierno».
- El ajuste se aplica al abrir el comercio y **desaparece al cerrarlo**: nunca se guarda en el aldeano ni se acumula.
- Vanilla solo deja cambiar el primer ingrediente de cada trato. Por eso:
  - lo que más cambia es lo que los aldeanos **pagan por tus productos**;
  - un trato de «1 esmeralda → 6 panes» no puede bajar de 1 esmeralda;
  - los tratos de varias esmeraldas sí suben en invierno: las zanahorias doradas pasan de 3 a 4.
- Los descuentos vanilla (reputación, Héroe de la Aldea, curar a un aldeano zombi) se siguen aplicando encima.

### Ferias de temporada (vendedor ambulante)
La primera vez que comercias con un vendedor ambulante en cada estación, añade ofertas pensadas para los otros módulos:

| Estación | Ofertas | Para qué |
|---|---|---|
| Primavera | Semillas de trigo, remolacha, calabaza y melón | La siembra tras el invierno |
| Verano | Hielo, hielo compacto y hielo azul | Enfriar las bodegas de Pantry |
| Otoño | Botas de cuero, armadura de caballo de cuero y fardos de heno | Prepararse para la nieve de Logistics |
| Invierno | Fogatas, cristal y carbón | Los invernaderos del core |

Si la estación cambia mientras el vendedor sigue en el mundo, las ofertas de feria se sustituyen, no se acumulan. Su precio es el de la feria (no se le aplica el valor de la estación), y si se desactivan las ferias, el vendedor devuelve las ofertas que se le añadieron.

### Frescura en el mostrador (con Pantry)
Los aldeanos **rechazan la comida que se está pudriendo** (la etapa «Pudriéndose» de Pantry). El trato se cancela antes de que se pierda nada y la barra de acción lo explica.

### Venta a granel de cajas (con Logistics)
Agáchate y haz **clic derecho sobre un aldeano con cajas de Logistics en la mano**:
- Te las compra al precio de **su propio trato** por ese cultivo, con el valor de la estación y un **plus por venderlas empaquetadas** (+15 % por defecto). Un aldeano que no compra ese cultivo no compra sus cajas.
- Paga esmeraldas enteras y solo se queda con las cajas necesarias para pagarlas.
- **Límite diario por aldeano** (8 cajas por defecto), para que una granja infinita no imprima esmeraldas. Cuenta los días del calendario del core, así que retrasar el reloj no lo reinicia.
- Rechaza las cajas cuya comida se está pudriendo (con Pantry) o ya se pudrió.
- No abre la pantalla de comercio.

## Comandos y permisos
| Comando | Permiso | Uso |
|---|---|---|
| `/commerce` o `/commerce prices [estación]` (alias `/mercado`) | `sovereignseasons.commerce.prices` (todos) | Productos escasos y abundantes en la estación actual o en la indicada |
| `/commerce stats` | `sovereignseasons.commerce.admin` (op) | Ofertas ajustadas, ferias, cajas vendidas y comida rechazada |
| `/commerce reload` | `sovereignseasons.commerce.admin` (op) | Recarga `config.yml` |

## Instalación
- Requiere **SovereignSeasons-Core 1.1.0+**.
- **Pantry** y **Logistics** son opcionales y activan, respectivamente, el rechazo de comida podrida y la venta a granel. Sin ellos, Commerce funciona con los precios estacionales y las ferias.
- Compatible con **Folia**: todo funciona por eventos, que ya se ejecutan en el hilo que controla cada entidad, sin tareas programadas.
- Toda la configuración está comentada en [`config.yml`](src/main/resources/config.yml): valores por estación (con comodines como `*_WOOL`), límites, plus de granel, límite diario y ofertas de feria.

## Cómo funciona por dentro
- **Cuándo se ajusta el precio:** en `InventoryOpenEvent`, que vanilla dispara después de calcular los descuentos del aldeano y antes de enviar las ofertas al cliente. Lo comprobé en el bytecode de Paper 1.21.11: `startTrading` → `updateSpecialPrices` → `openTradingScreen` → `openMenu` (que dispara el evento) → `sendMerchantOffers`.
- **Por qué no se acumula:** al cerrar, el aldeano ejecuta `resetSpecialPrices`. El vendedor ambulante no lo hace, así que con él Commerce fija el ajuste de forma absoluta al abrir y lo borra al cerrar.
- **Compatibilidad entre versiones:**
  - no usa `Villager.Profession`, que pasó de `enum` a interfaz en 1.21: decide por las ofertas de cada aldeano;
  - no usa `InventoryView`, que pasó de clase a interfaz en 1.21.
- La lógica está en modelos puros probados sin servidor (`TradePricing`, `ProductValues`, `BulkSaleModel`).

## Estado
- **v1.0.0.**
- 20 pruebas unitarias.
- Suite de juego `tools/e2e/e2e-commerce.js` (24 comprobaciones), contra un servidor Paper real con un bot: verificada en Paper 1.21.11 y 26.1.2.

**Ideas para v1.1:** contratos de abastecimiento con entrega anticipada y caravanas entre aldeas.
