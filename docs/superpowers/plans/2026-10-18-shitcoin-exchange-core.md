# Shitcoin Exchange Core Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Implementar el núcleo persistente del exchange: cantidades exactas,
AMM, libro contable, lanzamientos y retiros de liquidez verificables.

**Architecture:** El dominio vive en `modules.finance`, sin dependencias de Bukkit
ni llamadas a Vault. `ExchangeService` aplica reglas dentro de transacciones de
`ExchangeLedger`; SQLite conserva partidas, saldos, mercados y resultados
idempotentes. Las cotizaciones puras usan `BigInteger`.

**Tech Stack:** Java 25, Gradle 9.2.1, JUnit 5,
`org.xerial:sqlite-jdbc:3.50.3.0`.

**Spec:** [Especificación aprobada](../specs/2026-10-18-shitcoin-exchange-core-design.md).

## Global Constraints

- Paper 26.2, Java 25, Gradle 9.2.1; no actualizar estas versiones.
- Paquete `dev.linqfy.bigCasares.modules.finance`, cuatro espacios y UTF-8.
- BC$: dos decimales; tokens: seis decimales; persistencia en unidades `long`.
- Lanzamiento: 500 BC$; liquidez mínima: 1.000 BC$; comisión: 150 puntos básicos.
- Un mercado activo por creador y seis mercados activos en total.
- LP inicial: 1.000.000 participaciones; bloqueo opcional entre 1 y 48 horas.
- `minimumOutput` estrictamente positivo; ninguna cuenta gastable negativa.
- WAL, `synchronous=FULL`, `foreign_keys=ON`, `busy_timeout=5000`.
- Ninguna llamada a Vault, comando, GUI, NPC ni registro de módulo en esta etapa.

## Review Focus

1. UUID repetido después de un reinicio: devolver el recibo original sin escribir
   otras partidas, incluso si las reservas o los límites cambiaron. Tarea 2.
2. Dos conexiones al mismo archivo: comprobar saldo y reservas después de adquirir
   el bloqueo de escritura, evitando sobregiro y cotizaciones obsoletas. Tarea 4.
3. Cantidades cercanas a `Long.MAX_VALUE`: no permitir overflow en productos,
   saldos, suministro ni contrapartidas de control. Tareas 1 y 2.
4. Vencimiento exacto del bloqueo y retiro del último LP: permitir retirar en el
   vencimiento y devolver todo el polvo sin tocar tenencias de terceros. Tarea 3.
5. Fallo después de modificar un saldo pero antes de guardar el mercado: revertir
   partidas, cuentas nuevas, saldos y registro de operación conjuntamente. Tarea 2.

---

## File Structure

Todas las clases siguientes se crearán bajo
`src/main/java/dev/linqfy/bigCasares/modules/finance/`; las pruebas usan el paquete
equivalente bajo `src/test/java/`.

| Archivo | Responsabilidad |
| --- | --- |
| `FixedAmount.java` | Conversión de cantidades decimales a unidades y formato |
| `AmmMath.java`, `TradeQuote.java`, `LiquidityQuote.java` | Cálculo puro del AMM |
| `ExchangeException.java` | Rechazos de dominio y fallos de persistencia identificables |
| `ExchangeSettings.java` | Parámetros y límites validados |
| `LedgerAccount.java`, `AccountKind.java`, `LedgerEntry.java` | Identidad de cuentas y partidas |
| `OperationKind.java`, `OperationReceipt.java` | Resultado durable de una operación |
| `MarketSnapshot.java`, `LiquidityPosition.java` | Instantáneas inmutables del estado |
| `ExchangeLedger.java`, `ExchangeTransaction.java` | Contrato transaccional del almacenamiento |
| `SqliteExchangeLedger.java`, `ExchangeSchema.java` | JDBC, esquema y transacciones |
| `LaunchRequest.java`, `TradeRequest.java`, `LiquidityRequest.java` | Entradas inmutables del servicio |
| `ExchangeService.java` | Lanzamientos, swaps, retiros y crédito externo confirmado |

Modificar `build.gradle` y `src/main/resources/plugin.yml` únicamente para alinear
la dependencia SQLite. Añadir `docs/finance.md` y su enlace desde `docs/README.md`.
No modificar `BigCasares.initializeRuntime()` ni `config.yml`.

### Task 1: Cantidades exactas y cotizaciones puras

**Files:** `FixedAmount.java`, `AmmMath.java`, `TradeQuote.java`,
`LiquidityQuote.java`, `ExchangeException.java` y sus pruebas.

**Interfaces:**

- `FixedAmount.parsePositive(String value, int decimals): long` y
  `FixedAmount.format(long units, int decimals): String`; constantes
  `BC_DECIMALS = 2`, `TOKEN_DECIMALS = 6`.
- `AmmMath.buy(long bcReserve, long tokenReserve, long input, int feeBps,
  long minimumOutput): TradeQuote`; `sell` tiene la misma firma.
- `AmmMath.withdraw(long bcReserve, long tokenReserve, long shares,
  long totalShares): LiquidityQuote`.
- `TradeQuote(long input, long feeBc, long output, long bcReserveAfter,
  long tokenReserveAfter)` y `LiquidityQuote(long bcOut, long tokensOut,
  long sharesAfter)`.

- [x] Escribir `FixedAmountTest` y `AmmMathTest` antes del código: rechazar cero,
  signos, exponente, precisión excesiva y overflow al parsear; formatear cero y
  límites sin pérdida. Usar punto decimal independiente del locale.
- [x] Verificar el ejemplo: `buy(100_000, 1_000_000_000_000L, 50_000, 0, 1)`
  entrega `333_333_333_333L` tokens, con reservas `150_000` y
  `666_666_666_667L`.
- [x] Probar compra con 150 bps: de `50_000` unidades BC$, comisión `750`;
  comprobar venta con comisión en BC$, slippage, salida cero, reserva cero,
  comisión fuera de `[0, 9999]` y desbordamiento de reserva posterior.
- [x] Probar compras y ventas repetidas con semilla fija y comprobar el producto
  de reservas con `BigInteger`; probar productos mayores que `Long.MAX_VALUE`.
- [x] Ejecutar `bash gradlew test --tests '*finance.FixedAmountTest' --tests
  '*finance.AmmMathTest'` y registrar el fallo por clases ausentes.
- [x] Implementar las firmas usando división entera: techo para comisión, piso
  para salida y retiro; rechazar entrada/salida consumida por comisiones.
- [x] Repetir las pruebas anteriores y exigir cero fallos.
- [x] Commit: `feat(finance): add exact amounts and constant-product quotes`.

### Task 2: Ledger SQLite atómico y resultados idempotentes

**Files:** cuentas, partidas, recibos, instantáneas, contrato de ledger,
`ExchangeSchema.java`, `SqliteExchangeLedger.java`, `SqliteExchangeLedgerTest.java`,
`build.gradle`, `src/main/resources/plugin.yml`.

**Interfaces:**

- `LedgerAccount(String id, AccountKind kind)` con fábricas `wallet(UUID)`,
  `pool(UUID)`, `fees()`, `external()` e `issuance(UUID)`; las dos últimas son
  contrapartidas no gastables. Activo BC$: `"BC"`; activo token: UUID del mercado.
- `LedgerEntry(UUID operationId, LedgerAccount account, String asset, long delta)`.
- `OperationReceipt(UUID operationId, OperationKind kind, UUID marketId,
  long input, long output, long feeBc, long secondaryOutput)`; `marketId` es nulo
  únicamente para un crédito externo confirmado. `secondaryOutput` contiene
  tokens devueltos al retirar LP; las demás operaciones usan cero.
- `MarketSnapshot(UUID marketId, UUID creatorId, String ticker, String name,
  long totalSupply, long bcReserve, long tokenReserve, long lpShares,
  Instant unlockAt, boolean closed)`; `LiquidityPosition(UUID marketId,
  UUID ownerId, long shares, Instant unlockAt)`.
- `ExchangeLedger extends AutoCloseable`: `balance(LedgerAccount, String): long`,
  `market(UUID): Optional<MarketSnapshot>`, `markets(): List<MarketSnapshot>`,
  `position(UUID): Optional<LiquidityPosition>`,
  `operation(UUID): Optional<OperationReceipt>`, `entries(UUID): List<LedgerEntry>`.
- Escritura interna al paquete: `transact(UUID operationId, String fingerprint,
  Instant now, Function<ExchangeTransaction, OperationReceipt> work)`.
  Primero consultar recibo y huella, luego ejecutar `work` una sola vez.
- `ExchangeTransaction`: consultar saldo/mercado/posición y cantidad de mercados
  activos, mover fondos entre cuentas, registrar emisión inicial, y guardar
  mercado/posición. No exponer `Connection` ni permitir gastar contrapartidas.
- `SqliteExchangeLedger(Path database)` abre o crea el archivo y aplica esquema
  versión 1; rechaza otras versiones. `close()` es idempotente y espera el cierre
  de una transacción activa antes de liberar la conexión.

- [ ] Añadir SQLite JDBC 3.50.3.0 a Gradle y `plugin.yml`, sin actualizar otras
  dependencias. Crear pruebas sobre archivos temporales reales.
- [ ] Escribir pruebas de partidas balanceadas por activo, débito sin saldo,
  control no gastable, overflow de cuenta destino y contrapartida, y emisión única.
- [ ] Probar commit de un crédito y replay con el mismo UUID; otra huella debe
  rechazarse. Cerrar/reabrir y exigir el mismo recibo y las mismas partidas.
- [ ] Forzar una excepción dentro del callback después de una transferencia:
  saldo, partidas, cuentas nuevas y operación deben quedar como antes.
- [ ] Probar `user_version` futuro sin borrar tablas, configuración PRAGMA,
  claves foráneas y rechazo de operaciones después de `close()`.
- [ ] Ejecutar `bash gradlew test --tests '*finance.SqliteExchangeLedgerTest'`
  y registrar los fallos iniciales.
- [ ] Implementar esquema con claves únicas, `CHECK` de rango/tipo de saldo,
  prepared statements y `BEGIN IMMEDIATE`. Acumular partidas con overflow exacto,
  exigir suma cero por activo antes del commit y persistir el recibo con la huella.
  Una reserva del pool siempre se corresponde con su cuenta contable.
- [ ] Repetir la prueba enfocada y exigir cero fallos.
- [ ] Commit: `feat(finance): persist balanced and idempotent exchange transactions`.

### Task 3: Lanzamientos, trading y retiro de LP

**Files:** `ExchangeSettings.java`, solicitudes, `ExchangeService.java`,
`ExchangeServiceTest.java`.

**Interfaces:**

- `ExchangeSettings(long launchFeeBc, long minimumLiquidityBc,
  int marketsPerCreator, int maximumActiveMarkets, int tradeFeeBps)` y
  `ExchangeSettings.defaults()` con los valores de Global Constraints.
- `LaunchRequest(UUID operationId, UUID creatorId, String ticker, String name,
  long bcLiquidity, long poolTokens, long creatorTokens, Duration lockDuration)`.
  `Duration.ZERO` significa desbloqueada; de otro modo, entre 1 y 48 horas.
- `TradeRequest(UUID operationId, UUID playerId, UUID marketId, long input,
  long minimumOutput)` y `LiquidityRequest(UUID operationId, UUID playerId,
  UUID marketId, long shares)`.
- `ExchangeService(ExchangeLedger ledger, ExchangeSettings settings, Clock clock)`.
  Métodos: `recordConfirmedDeposit(UUID operationId, UUID playerId, long amount)`,
  `launch(LaunchRequest)`, `buy(TradeRequest)`, `sell(TradeRequest)` y
  `withdrawLiquidity(LiquidityRequest)` devuelven `OperationReceipt`.
- El UUID de lanzamiento es también el UUID estable del mercado. La huella SHA-256
  incorpora tipo y campos de la solicitud normalizada, serializados con longitud
  explícita; nunca incorpora la hora ni la configuración del reintento. Para un
  UUID existente, el servicio verifica la solicitud original antes de aplicar
  límites cambiantes. El recibo conserva las cantidades y comisiones originales;
  un cambio posterior de configuración no invalida un replay idéntico.

- [ ] Escribir pruebas de crédito externo idempotente y lanzamiento: creador
  financiado con `150_000` unidades BC$ paga `50_000` de comisión y aporta
  `100_000` al pool. Verificar emisión total, tokens retenidos y LP inicial.
- [ ] Probar nombres/tickers inválidos, normalización con `Locale.ROOT`, BC/ORO
  reservados, duplicados, límite global y por creador, supply overflow, saldo
  insuficiente y duración de bloqueo fuera de rango; ningún rechazo debe mover fondos.
- [ ] Probar compra/venta con comisión segregada, dev dump limitado al saldo,
  ventas ajenas rechazadas, reservas actualizadas y mínimo de salida incumplido.
- [ ] Probar retiro parcial y total por el dueño: ambas reservas vuelven a su
  billetera; el retiro total cierra el mercado y conserva tokens de terceros.
  Rechazar tercero, exceso de shares, salida cero y operaciones en un pool cerrado.
- [ ] Probar bloqueo un instante antes del vencimiento y en el instante exacto
  mediante `Clock.fixed`; probar replay posterior al vencimiento y al cierre.
- [ ] Ejecutar `bash gradlew test --tests '*finance.ExchangeServiceTest'` y
  registrar los fallos iniciales.
- [ ] Implementar las reglas dentro de `transact`; recalcular swaps sobre las
  reservas leídas después del bloqueo SQLite y persistir movimientos y snapshots
  conjuntamente. El servicio no consulta proveedores externos.
- [ ] Repetir las pruebas enfocadas y exigir cero fallos.
- [ ] Commit: `feat(finance): add token launches trading and liquidity rugs`.

### Task 4: Concurrencia, recuperación y documentación de integración

**Files:** `ExchangeConcurrencyTest.java`, `ExchangeRecoveryTest.java`,
`docs/finance.md`, `docs/README.md` y nota final de este plan.

**Interfaces:** consume las APIs públicas de las tareas anteriores. No añade
acceso a saldos ni módulos en el runtime.

- [ ] Escribir una prueba con dos ledgers sobre el mismo archivo y una barrera de
  inicio: dos compras que juntas exceden el saldo deben producir un solo éxito,
  sin sobregiro ni partidas de la compra rechazada.
- [ ] Probar dos compradores financiados: ambas operaciones pueden confirmar y
  sus recibos deben corresponder a dos swaps secuenciales, nunca al mismo estado
  inicial. Probar también el mismo UUID ejecutado concurrentemente.
- [ ] Ejecutar un proceso hijo que abra SQLite, escriba dentro de una transacción
  y termine sin commit; reabrir desde el proceso padre y verificar fondos,
  reservas y ausencia de la operación interrumpida. Usar límites de tiempo y
  `ProcessBuilder`, sin añadir herramientas de producción para simular fallos.
- [ ] Probar que un error en una transacción no inutiliza la conexión para la
  siguiente operación válida, y que `close()` concurrente no deja un commit parcial.
- [ ] Ejecutar `bash gradlew test --tests '*finance.*'`; exigir cero fallos y
  corregir cualquier problema identificado por las nuevas pruebas.
- [ ] Documentar unidades, comisiones, APIs, idempotencia, ruta futura de datos y
  restricciones del contrato `recordConfirmedDeposit`. Explicar que un recibo
  interno no verifica cobro en Vault y que `/bolsa` todavía no existe.
- [ ] Ejecutar `bash gradlew --no-daemon clean build` con Java 25, revisar el
  resultado completo y comprobar que el JAR declara la dependencia SQLite.
- [ ] Revisar el diff contra la especificación, registrar evidencia y límites
  reales de verificación en este plan, y ejecutar `git diff --check`.
- [ ] Commit: `test(finance): verify concurrency recovery and core contracts`.

## Execution and Completion

Recomendación: ejecución nativa en esta sesión, porque servicio y ledger comparten
contratos estrechos y una sola implementación facilita mantenerlos coherentes.
La revisión de este plan precede a la escritura de código del subsistema.

Estado al redactar: especificación aprobada por el usuario; plan preparado para
revisión. No se han implementado las clases ni modificado dependencias.
