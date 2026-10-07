# Don Pollos

Módulo `don-pollos` de BigCasares. Agrega cinco variantes de Don Pollo con menús propios, ciudades que aparecen al
generar el mundo y el Don Pollo Boss, una pelea en cuatro fases con premio al final. Funciona en Java y en Bedrock
(Geyser + Floodgate).

- Código: `src/main/java/dev/linqfy/bigCasares/modules/donpollos/`
- Configuración: `src/main/resources/don-pollos.yml`, que en el servidor queda en `plugins/BigCasares/don-pollos.yml`
- Modelos: `src/main/resources/bettermodel/models/bigcasares_*.bbmodel` (BetterModel)
- Items, texturas y sonidos de Java: `resourcepack/java/assets/donpollos/` (van dentro del pack de BigCasares)
- Generadores de modelos y assets: `tools/donpollos/` (ver su `README.md`)
- Bedrock: [compatibilidad-bedrock.md](compatibilidad-bedrock.md)
- Diseño y plan: `docs/superpowers/specs/2026-10-04-don-pollos-design.md` y
  `docs/superpowers/plans/2026-10-04-don-pollos.md`

## Activarlo

El módulo viene prendido. Para apagarlo, en `config.yml`:

```yaml
modules:
  don-pollos:
    enabled: false
```

Necesita **BetterModel** en el servidor; se recomienda la 3.5.0. Sin BetterModel, los Don Pollos se ven como aldeanos.

## Probarlo en local

```powershell
.\gradlew.bat runServer
```

`runServer` descarga Paper 26.2 y los plugins del servidor: BetterModel 3.5.0, Geyser, Floodgate, packetevents,
GeyserUtils y GeyserModelEngine. También pone las extensiones de Geyser en `build/run-server/plugins/Geyser-Spigot/`. La lista está
en `serverPlugins` y `geyserExtensions` de `build.gradle`. La primera vez hay que aceptar la EULA en
`build/run-server/eula.txt`.

Cuando Minecraft pregunte si aceptás el resource pack del servidor, decile que sí.

## Comandos

Todos piden el permiso `bigcasares.donpollos.admin`, que viene en `bigcasares.*` y es de OP por defecto. El comando es
`/donpollo` y también acepta el alias `/donpollos`.

| Comando | Qué hace |
|---|---|
| `/donpollo spawn <comun\|gordito\|salsero\|aura-67\|fino>` | Pone un Don Pollo donde estás |
| `/donpollo remove` | Saca el más cercano, a 5 bloques como máximo |
| `/donpollo list` | Lista los Don Pollos cargados |
| `/donpollo give <pollo-frito\|salsa\|picante\|balde\|secreto> [cantidad]` | Da los items del módulo |
| `/donpollo bailar <1-5\|parar> [todos]` | Hace bailar al Salsero más cercano (hasta 10 bloques) o a todos |
| `/donpollo boss` / `/donpollo boss quitar` | Pone el boss delante tuyo, o lo saca |
| `/donpollo ciudad cerca` | Dice dónde queda la ciudad más cercana |
| `/donpollo ciudad aqui` | Construye una ciudad delante tuyo |

Desde la consola, el spawn es `donpollo spawn <variante> <mundo> <x> <y> <z>` y el boss es
`donpollo boss <mundo> <x> <y> <z>`.

## Las variantes

Todas usan pan como moneda.

- **Común**: camina por ahí. Cuando alguien se le acerca a menos de 15 bloques, dice un audio.
- **Gordito**: tiene intercambios de pan y además guarda un secreto (ver abajo).
- **Salsero**: sigue a los jugadores. Si le ganás la batalla de baile, festeja con un audio.
- **Aura 67**: tiene una pollería.
  - 4 pollos fritos por 5 panes.
  - Una salsa por 2 panes; al tomarla da Fuerza I por 10 s.
  - Un picante por 3 panes; al tomarlo da Velocidad III por 10 s.
  - El botón verde abre los intercambios de pan comunes.
  - Precios y cantidades: `variants.aura-67.offers`.
- **Fino**: tiene un casino de ruleta.
  - Ponés un objeto, elegís rojo, negro o verde y tocás GIRAR.
  - La ruleta tiene 26 casillas. Las chances son rojo 24/50, negro 24/50 y verde 2/50.
  - Si sale tu color te devuelve el doble; si no, perdés el objeto. Si te desconectás mientras gira, te devuelve la
    apuesta.
  - Chances y multiplicadores: `fino.roulette`.

Al alejarte, cada uno se despide con su frase, salvo el Común. La primera vez que entrás a una ciudad aparece
"En este ciudad pasaron cosas...".

## El secreto del Gordito

1. Hacé click derecho al Gordito. Dice "Yo se cosas...", "Queres saberlas..." y "Dame Salsa y Picante".
2. Con otro click abre su menú. Poné una Salsa y un Picante, que se sacan de tu inventario.
3. Tocá la estrella **Secreto**. Te da el **Secreto del Don Pollo**, que es un beacon.
4. Ponelo en el piso. El bloque cuenta su historia con las fotos del bloque: "Era una vez...", "En el auto...",
   y sigue hasta "Desde ese dia algo cambio en Don Pollo...".
5. Salen partículas rojas y blancas, explota una esfera y del medio aparece el Don Pollo Boss.

El boss necesita la dificultad en fácil o más alta.

## Ciudades

La ciudad se construye a partir de `src/main/resources/donpollos/structures/ciudad.json.gz`. Ese archivo se genera
desde `tools/donpollos/fuentes/schema/ciudad.schematic` con:

```powershell
python tools/donpollos/convertir_schematic.py
```

Para cambiar el diseño de la ciudad, se reemplaza el `.schematic`, se corre ese comando y se recompila.

Así se reparten las ciudades:

- El mundo se divide en celdas de 1400 bloques, con una ciudad por celda en un lugar fijo según la semilla y con un
  giro al azar.
- Se construye por tandas cuando se genera el chunk del centro de la ciudad.
- Al construirla, rellena los cimientos, limpia el terreno y la puebla con 4 Don Pollos comunes y 2 de cada una de las
  otras variantes.
- Nunca aparece sobre agua ni a menos de 500 bloques del spawn.

Las opciones están en la sección `ciudades` (mundos, separación, probabilidad, velocidad de construcción y cuántos Don
Pollos pone). Las ciudades ya construidas quedan guardadas en `plugins/BigCasares/data/don-pollos/ciudades.yml`.

## Don Pollo Boss

Es una estatua de Don Pollo saliendo de un auto, montada sobre un Ravager agrandado. Tiene barra de boss y no persigue
a los jugadores en creativo. Pelea en cuatro fases y cada una arranca con un mensaje en pantalla:

1. **Fase 1** (desde el 100% de vida): "Un video más mi gente?". Te persigue y cada tanto frena para tirarte dos láseres
   por los ojos.
2. **Fase 2** (desde el 75%): "No hay salsa para ti...".
   - Se queda a unos 10 bloques y tira meteoritos con el logo de WhatsApp; los meteoritos no te siguen.
   - Cada 7.5 s aparecen 2 gallinas WhatsApp que pegan como un zombie.
   - Los jugadores que están en el rango de la barra quedan encerrados en el área de pelea, donde no se pueden poner
     bloques.
3. **Fase 3** (desde el 50%): "No hay picante para ti...".
   - El boss se eleva 20 bloques en 7 s y se queda flotando, invulnerable, dentro de una esfera de partículas azules.
   - El chunk de cada jugador se vacía y queda como un cubo de 6 bloques de profundidad, con paredes de piedra. Adentro
     del cubo no se pueden poner ni romper bloques.
   - Empiezan las **Batallas del Cubo**: 3 rondas de 10, 30 y 50 Labubus, con un Coronel por ronda que es cada vez más
     rápido y fuerte. Al ganar las tres, el jugador sube y el cubo se vuelve a rellenar.
4. **Fase final** (desde el 25%): "Me haz hecho enojar, Papi Don Pollo esta enojado".
   - Se agranda y te persigue a los saltos con el auto.
   - Se infla dos veces y tira un rayo de warden al lugar donde estabas.
   - Da mega saltos que caen a unos 8 bloques de vos, con bloques volando y una onda expansiva.
   - Cada 10 s invoca 2 Sanders.

Los números de cada fase están en `boss`, `boss.fase-2`, `boss.fase-3` y `boss.fase-final`.

### Cuando le ganás

Don Pollo no muere: el golpe final lo deja con 1 de vida. Te mira, llora y dice "Ya no hay video mi gente...",
"Ni salsa...", "Ni picante...", "Mi gente triste..." y "Elegi lo que quieras...".

Después se abre el menú del premio, con 5 lugares:

- El primero es siempre el **Balde de KFC**. Es un casco: mientras lo tenés puesto te da Velocidad II y Agilidad
  acuática II. En Bedrock se pone con click derecho.
- Los otros 4 los elegís vos por categoría, en un menú con páginas. Te llevás un stack de cada uno.

Si cerrás el menú, hacé click derecho a Don Pollo para volver a abrirlo. Cuando reclamás el premio, se va llorando.

## Sonidos

Cada Don Pollo dice un audio al azar en estos momentos:

- Común y Gordito: cuando un jugador se acerca a menos de 15 bloques.
- Salsero: cuando le ganás el baile.
- Aura 67: cuando le comprás algo.
- Fino: cuando abrís la ruleta.

Un mismo Don Pollo no empieza otro audio hasta que termina el que está diciendo.

Los originales están en `tools/donpollos/fuentes/sonidos/`. Se convierten al pack de Java con:

```powershell
python tools/donpollos/convertir_sonidos.py
```

## Bedrock

Con Geyser + Floodgate, los jugadores de Bedrock tienen tres opciones:

- Ven los modelos 3D si el servidor tiene GeyserModelEngine; se configura con `bedrock.modelos-3d`.
- Si no, ven el mob de base: cada Don Pollo es un aldeano de otra región y los bichos llevan armadura de su color.
- Los menús de cofre funcionan igual que en Java.

El módulo instala solo, en la carpeta de Geyser, el pack de Bedrock y el mapeo de items. Hay que reiniciar el servidor
una vez.

Ver [compatibilidad-bedrock.md](compatibilidad-bedrock.md) para el detalle completo y la lista de plugins.

## Modelos y assets

Los modelos y texturas se generan con los scripts de `tools/donpollos/`, que necesitan Python con Pillow:

| Script | Genera |
|---|---|
| `generar_modelos.py` | Las 5 variantes |
| `generar_boss.py` | El boss |
| `generar_wsp.py` | La gallina y el logo de WhatsApp |
| `generar_labubus.py` / `generar_coronel.py` | Los Labubus y el Coronel |
| `generar_balde_casco.py` | El balde de KFC |
| `generar_bloque_secreto.py` | El bloque del secreto |
| `generar_bedrock.py` / `generar_geysermodelengine.py` | Los assets de Bedrock |

Para ver cómo quedó un modelo:

```powershell
python tools/donpollos/render_modelo.py <modelo.bbmodel> <salida.png>
```

Los `.bbmodel` también se pueden abrir y editar en Blockbench.

## Tests

```powershell
.\gradlew.bat test --tests "dev.linqfy.bigCasares.modules.donpollos.*"
```

En Windows, si Gradle falla con "Unable to establish loopback connection", corré esto antes:

```powershell
$env:JAVA_TOOL_OPTIONS='-Djdk.net.unixdomain.tmpdir=C:/gtmp'
```
