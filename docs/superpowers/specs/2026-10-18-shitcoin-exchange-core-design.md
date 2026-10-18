# Shitcoin Exchange: núcleo contable y AMM

Fecha: 2026-10-18. Estado: especificación aprobada por el usuario para la primera entrega.
Base inspeccionada: `d47cd07`, rama `work`.

## Intención y alcance

La propuesta aportada por el usuario describe un mercado ficticio de Minecraft:
BC$ conserva su función de moneda del servidor y los jugadores pueden lanzar,
comprar, vender y retirar liquidez de tokens de alto riesgo. Los precios deben
resultar de operaciones reales, y una pérdida en el exchange nunca debe debitar
automáticamente la cuenta externa de un jugador.

Se conserva ese objetivo. La primera entrega desarrolla el núcleo contable y el
AMM, siguiendo el orden de implementación recomendado al final de la propuesta.
Debe producir una biblioteca interna persistente, con pruebas que permitan
integrarla después sin exponer todavía dinero de los jugadores.

**Alcance aprobado:** la implementación empieza por esta primera entrega. El mercado jugable completo
requiere las etapas posteriores descritas al final; esta entrega no se presenta
como un exchange listo para habilitar en producción.

## Evidencia del repositorio

- [Bootstrap](../../../src/main/java/dev/linqfy/bigCasares/BigCasares.java):
  `initializeRuntime()` crea los módulos y establece su orden.
- [PluginModule](../../../src/main/java/dev/linqfy/bigCasares/module/PluginModule.java)
  y [registro de recursos](../../../src/main/java/dev/linqfy/bigCasares/module/runtime/BukkitRuntimeRegistrations.java):
  el módulo futuro debe participar de la limpieza por generación.
- [PaymentVoucherService](../../../src/main/java/dev/linqfy/bigCasares/modules/bounties/PaymentVoucherService.java):
  un fallo de Vault puede ocurrir después de aplicar un pago; el servicio conserva
  el vale bloqueado para no repetirlo automáticamente.
- [ShopModule](../../../src/main/java/dev/linqfy/bigCasares/modules/shop/ShopModule.java):
  existen inventarios Java y formularios Bedrock reutilizables como patrón.
- [Build](../../../build.gradle) y [descriptor](../../../src/main/resources/plugin.yml):
  Paper 26.2, Java 25, Gradle 9.2.1; dependencias de ejecución mediante `libraries`.
- La búsqueda en código y configuración no encontró un adaptador de autenticación
  ni una capa SQLite existente. No se debe asumir que estar conectado equivale a
  estar autenticado en un servidor que acepta identidades offline.

Estas referencias describen código actual. Los nombres de clases de las secciones
siguientes son archivos propuestos, no componentes que ya existan.

## Alternativas y elección

1. **Núcleo transaccional primero, recomendado.** Se prueban contabilidad,
   persistencia y cálculo de operaciones sin Bukkit ni Vault. Reduce el alcance de
   los fallos antes de conectar saldos externos y deja una base útil por sí sola.
2. **Corte vertical con `/bolsa` y Vault desde el inicio.** Permite jugar antes,
   pero exige resolver en la misma entrega autenticación, conciliación, hilos,
   lifecycle y experiencia Java/Bedrock.
3. **Prototipo en memoria.** Facilita probar precios; no cumple la recuperación
   tras reinicios y habría que sustituir el almacenamiento antes de aceptar fondos.

Se elige la primera alternativa. La segunda es la integración siguiente. YAML no
es el almacén financiero y un precio aleatorio no sustituye al AMM.

## Límites de la primera entrega

Incluye:

- Cantidades de precisión fija, validación de límites y redondeo conservador.
- Libro contable por activo, partidas balanceadas y saldos nunca negativos en
  cuentas gastables.
- SQLite, transacciones atómicas, operaciones idempotentes y recuperación al reabrir.
- Emisión inicial explícita de tokens, creación de pools, compra y venta con
  comisión y salida mínima.
- Una posición LP inicial por pool, retiro proporcional parcial o total y bloqueo
  temporal voluntario de esa posición.
- Consulta de reservas, saldos, comisiones y operaciones confirmadas para las capas
  siguientes.
- Pruebas de rechazo sin cambios parciales, concurrencia y reapertura de la base.

No se conectan todavía Vault, comandos, GUIs, broker físico, staking, recompensas,
ORO ni NPC. Tampoco se registra un `FinanceModule` incompleto en el runtime.
No hay créditos administrativos de BC$ expuestos a jugadores.

## Estructura propuesta

Paquete: `dev.linqfy.bigCasares.modules.finance`.

| Unidad propuesta | Responsabilidad |
| --- | --- |
| `FixedAmount` | Parsear/formatear unidades; rechazar precisión excesiva y desbordamiento |
| `AmmMath` | Cotizar compras, ventas y retiro proporcional; sin I/O ni Bukkit |
| `TradeQuote` | Resultado inmutable: entrada, comisión, salida y reservas posteriores |
| `ExchangeLedger` | Contrato de lectura y ejecución atómica de operaciones |
| `SqliteExchangeLedger` | Esquema, transacciones, partidas y deduplicación persistente |
| `ExchangeService` | Validación de negocio, propiedad del LP, bloqueos y aplicación de cotizaciones |
| `ExchangeSettings` | Límites y parámetros inmutables, independientes de Bukkit |

Los registros de solicitudes/resultados y las excepciones de dominio se colocan
junto a estas unidades. El servicio recibe un `Clock`; no usa la hora de pared
directamente en los tests. El ledger implementa `AutoCloseable`.

## Cantidades y reglas del AMM

BC$ usa dos decimales en el núcleo: `1 BC$ = 100` unidades. Los tokens usan seis:
`1 token = 1_000_000` unidades. Entradas, reservas y saldos se guardan como `long`
no negativo. Los productos, sumas intermedias y divisiones del AMM se calculan con
`BigInteger`; los resultados se convierten mediante comprobación exacta.

No se aceptan `double`, notación científica, `NaN`, infinito, cantidades negativas,
cero como entrada operativa ni dígitos adicionales que deban truncarse.

Con reservas BC$ `x`, reserva de tokens `y` y comisión `b` en puntos básicos:

- Compra con entrada bruta `a`: `fee = ceil(a * b / 10000)`,
  `effective = a - fee`, `out = floor(y * effective / (x + effective))`.
- Venta de `a` unidades de token: `gross = floor(x * a / (y + a))`,
  `fee = ceil(gross * b / 10000)`, `out = gross - fee`.
- La comisión se paga en BC$ y se transfiere a una cuenta segregada de comisiones.
  En compra ingresan al pool `effective` BC$; en venta salen del pool `gross` BC$.
- Se rechazan comisión que consuma toda la entrada/salida, salida redondeada a
  cero, reserva inicial cero, desbordamiento y `out < minimumOutput`.
- La cotización mostrada es informativa. La ejecución vuelve a calcular sobre las
  reservas actuales dentro de la misma transacción que mueve los fondos.
- El redondeo no favorece al operador. Para cada swap válido,
  `xAfter * yAfter >= xBefore * yBefore` con las reservas del pool.

`minimumOutput` debe ser estrictamente positivo: la capa de interfaz calculará su
valor a partir del slippage elegido y entregará esa protección al motor.

Caso de referencia sin comisión: un pool con 1.000 BC$ y 1.000.000 tokens recibe
una compra de 500 BC$. Entrega 333.333,333333 tokens, sujeto al redondeo de unidades;
el precio marginal posterior es aproximadamente 2,25 veces el inicial. Ese precio
no es una promesa de valor realizable al vender todos los tokens.

## Contabilidad e idempotencia

Cada operación tiene un UUID y una huella canónica que incluye tipo, actor,
mercado, cantidades y condiciones. La transacción persiste simultáneamente la
operación, todas sus partidas, los saldos afectados y las reservas resultantes.

- Repetir un UUID con la misma huella devuelve el resultado original, incluso
  después de reiniciar; no ejecuta nuevamente la operación.
- Repetir el UUID con otros parámetros se rechaza.
- Un error de validación o persistencia revierte el conjunto completo.
- Una cuenta gastable no puede quedar negativa ni exceder `Long.MAX_VALUE`.
- Las partidas suman cero por activo. BC$ y cada token se verifican por separado.

Tipos de cuenta: billetera de jugador, reserva de pool, comisiones, contrapartida
externa de BC$ y contrapartida de emisión de cada token. Solo las contrapartidas
de control admiten saldo firmado negativo; nunca se pueden gastar. Una emisión
de tokens crea una partida igual y opuesta en su contrapartida, y registra su
suministro máximo, que no vuelve a aumentar.

El contrato de entrada de BC$ registra únicamente una transferencia externa ya
confirmada, identificada de forma idempotente. Es un contrato interno reservado
al futuro puente de Vault; no demuestra que un proveedor externo haya cobrado ni
autoriza a una interfaz a otorgar saldo. Los tests lo usan con confirmaciones
simuladas. No se afirma atomicidad entre SQLite y Vault.

## Persistencia y concurrencia

Archivo previsto para la integración: `plugins/BigCasares/data/finance/exchange.db`.
El núcleo recibe un `Path`, por lo que sus pruebas usan archivos temporales.

SQLite usa `journal_mode=WAL`, `synchronous=FULL`, `foreign_keys=ON` y
`busy_timeout=5000`. Se versiona el esquema con `user_version`: un esquema futuro
desconocido impide abrir la base; no se borra ni se recrea silenciosamente.

Tablas: `accounts`, `balances`, `operations`, `entries`, `markets` y
`liquidity_positions`. Se usan consultas parametrizadas, claves únicas para UUID
de operación y ticker, claves foráneas, y restricciones coherentes con los tipos
de cuentas. El resultado de una operación se conserva para responder reintentos.

Las escrituras comienzan con `BEGIN IMMEDIATE`, leen saldos y reservas dentro de
esa transacción y terminan con `COMMIT` o `ROLLBACK`. Dos instancias sobre el mismo
archivo no pueden gastar el mismo saldo o confirmar sobre reservas obsoletas.
Las lecturas devuelven instantáneas inmutables.

La futura capa Paper usará un ejecutor serial dedicado para I/O. No ejecutará
Vault desde ese hilo, ya que los proveedores pueden requerir el hilo principal.
El cierre espera la finalización controlada de operaciones y rechaza trabajo nuevo;
si se pierde el proceso antes del commit, SQLite conserva el estado anterior.

Se propone añadir SQLite JDBC `org.xerial:sqlite-jdbc:3.50.3.0` como dependencia del
nuevo subsistema, alineada entre Gradle y `plugin.yml`. No se actualizan Java,
Paper, Gradle ni las integraciones existentes.

## Lanzamiento y liquidez

El servicio crea mercado, emisión, asignación al creador, comisión de lanzamiento,
reservas y posición LP en una sola transacción. El creador debe disponer de la
liquidez inicial más la comisión en su billetera interna. Los tokens destinados
al pool y los retenidos por el creador se especifican por separado y su suma es
el suministro emitido. No se permiten acuñaciones posteriores.

Ticker: de 2 a 12 caracteres ASCII `[A-Z0-9]`, normalizado con `Locale.ROOT`, único.
`BC` y `ORO` quedan reservados. El nombre tiene entre 1 y 32 caracteres visibles,
sin controles ni códigos de formato. No se aceptan cantidades ni límites inválidos.

Parámetros iniciales: lanzamiento 500 BC$, liquidez mínima 1.000 BC$, un mercado
activo por creador, seis mercados activos y comisión 150 puntos básicos. Las
pruebas pueden configurar cero comisión para verificar los ejemplos económicos.

El creador recibe 1.000.000 de participaciones LP iniciales; el pool registra su
total. En esta entrega no hay aportes posteriores ni transferencia de LP. Para
retirar `s` participaciones de un total `S` se devuelve
`floor(reserve * s / S)` de cada activo y se queman esas participaciones.
Retirar el total devuelve exactamente las reservas restantes, cierra el mercado
y deja los saldos de tokens de terceros intactos. Los retiros parciales que
redondean cualquiera de sus dos salidas a cero se rechazan.

La posición puede estar desbloqueada o bloqueada por una duración entre 1 y 48
horas fijada al crearla. Antes del instante de vencimiento no puede retirarse;
en el instante exacto puede retirarse. En esta entrega el bloqueo comprende toda
la posición inicial: no se promete configurar una fracción bloqueada todavía.

El retiro proporcional es el liquidity rug permitido por las reglas. Un dev
dump es una venta ordinaria del saldo retenido del creador, con las mismas reglas
de saldo, comisión y slippage de cualquier otro jugador. Ninguno altera la
billetera externa ni borra tenencias ajenas.

## Verificación requerida para implementar

1. Pruebas del AMM: ejemplo 1.000/1.000.000, compras y ventas, comisión exacta,
   redondeo, salida mínima, reservas extremas y no disminución del producto.
2. Ledger: suma cero por activo, insuficiencia de saldo, overflow, emisión única,
   repetición de UUID idéntico y conflictivo, y ausencia de partidas parciales.
3. Persistencia real: ejecutar sobre SQLite temporal, cerrar/reabrir y verificar
   saldos, reservas, posiciones y resultado deduplicado; rechazar esquema futuro.
4. Concurrencia: dos compradores con saldo limitado y dos instancias sobre el
   mismo archivo; no confirmar sobregiros ni cálculos con reservas obsoletas.
5. Lanzamientos: coste y límites, ticker duplicado, reservas y tenencias iniciales,
   retiro por tercero, bloqueo antes/en/después del vencimiento y cierre por rug.
6. Fallos: forzar un rechazo durante una transacción y comprobar que no cambia
   ninguna tabla; cerrar el servicio y comprobar que no admite operaciones nuevas.
7. Ejecutar las pruebas enfocadas y `bash gradlew --no-daemon clean build` con
   JDK 25, tal como exige [AGENTS.md](../../../AGENTS.md).

Una compilación correcta no acredita funcionamiento en Paper/Bedrock. La primera
entrega tampoco acredita conciliación de Vault, que todavía no implementa.

## Etapas siguientes hacia el producto completo

1. **Puente y mercado jugable:** `FinanceModule` con ID `shitcoin-exchange`, switch
   explícito inicialmente deshabilitado, autenticación verificable, `/bolsa`,
   wallet, depósitos/retiros y conciliación durable de Vault. Preparar una
   transferencia, registrar el intento antes de llamar al proveedor y bloquear
   resultados ambiguos; nunca reintentar pagos externos ambiguos automáticamente.
   La precisión admitida debe respetar la del proveedor y rechazar importes que
   no pueda representar. Esta etapa tiene su propia especificación de fallos.
2. **Experiencia de lanzamiento y cartera:** fichas de riesgo, confirmación de
   cotización, liquidación estimada, inventarios Java, formularios Bedrock y broker
   de spawn; sesiones/cotizaciones de un solo uso y callbacks de generación vigente.
3. **Ahorro y recompensas:** ORO con respaldo BC$ segregado y canje fijo, staking
   con bloqueo, acumulación de tenencias ponderada por tiempo y repartos cubiertos
   por comisiones ya cobradas. Sin rentabilidad fija ni dinero fabricado.
4. **Eventos:** operadores simulados identificados, financiados por transferencias
   registradas y con inventario/budget persistentes. Operan mediante el mismo
   servicio. Hype y anuncios no escriben precios ni reservas directamente.

Las cuatro etapas forman parte del objetivo original. Se enumeran para preservar
su alcance y dependencias; no se consideran implementadas por entregar el núcleo.
