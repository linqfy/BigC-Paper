# Compatibilidad de Don Pollos con Minecraft Bedrock

Fecha: 4 de octubre de 2026. Plugin: Don Pollos 0.1.0 (Paper 26.2 + BetterModel 3.5.0).

## Plugins necesarios para jugar desde Bedrock con todo (modelos 3D incluidos)

Servidor Paper (Geyser en el mismo servidor):

| Plugin / extensión | Dónde va | Para qué |
|---|---|---|
| **BetterModel 3.5.0** (ya está) | `plugins/` | Modelos 3D para Java |
| **BigCasares** (con el módulo `don-pollos`) | `plugins/` | Todo lo de Don Pollo |
| **Geyser-Spigot** | `plugins/` | Deja entrar a los de Bedrock |
| **Floodgate** | `plugins/` | Entran con su cuenta de Bedrock, sin cuenta de Java |
| **packetevents** | `plugins/` | Lo necesita GeyserModelEngine (sin él no carga) |
| **GeyserUtils** (versión Spigot) | `plugins/` | Lo necesita GeyserModelEngine |
| **GeyserModelEngine** | `plugins/` | Muestra los modelos de BetterModel a los de Bedrock |
| **GeyserModelEngineExtension** | `plugins/Geyser-Spigot/extensions/` | Arma el pack de Bedrock con los modelos |
| **geyserutils-geyser** | `plugins/Geyser-Spigot/extensions/` | La otra mitad de GeyserUtils, del lado de Geyser |
| **GeyserDisplayEntity** (opcional) | `plugins/Geyser-Spigot/extensions/` | Meteoritos y cara del bloque del secreto (los *item displays*) |

Para probar en local: `gradlew runServer` ya baja todo esto solo. Los plugins están en la lista `serverPlugins` de `build.gradle`; las extensiones y el pack de GeyserDisplayEntity los baja la tarea `downloadGeyserExtensions` a `build/run-server/plugins/Geyser-Spigot/`.

Configuración:
- **Floodgate:** `send-floodgate-data: true`. Si hay proxy, copiá `key.pem` del proxy a cada servidor.
- **`don-pollos.yml` (en plugins/BigCasares/):** `bedrock.modelos-3d: auto` (ya viene así): se prende solo al detectar GeyserModelEngine.

Si Geyser corre en un **proxy** (Velocity o BungeeCord):
- En el proxy van Geyser, Floodgate y `geyserutils-velocity` o `geyserutils-bungeecord`.
- En el servidor Paper van BetterModel, DonPollos, Floodgate, packetevents, GeyserUtils y GeyserModelEngine.
- Las extensiones van en la carpeta de Geyser del proxy.
- Los archivos de `plugins/BigCasares/don-pollos/bedrock/` se copian a mano:
  - `DonPollos-bedrock.mcpack` a `packs/`;
  - `donpollos-geyser.json` a `custom_mappings/`;
  - el contenido de `geysermodelengine-modelos.zip` a `extensions/geysermodelengineextension/input/`.

Qué hace el plugin solo (Geyser en el mismo servidor):
1. Copia el pack de Bedrock (íconos y audios) a `packs/` y el mapeo de ítems a `custom_mappings/`.
2. Si está GeyserModelEngine, copia los 13 modelos convertidos (geometría, animaciones, textura y config de cada
   uno) a `extensions/geysermodelengineextension/input/`. Los genera `tools/donpollos/generar_geysermodelengine.py`, que
   hace lo mismo que exportar cada modelo a mano desde Blockbench con "Export GeyserModelEngine Model".
3. Avisa en la consola que hay que **reiniciar una vez** para que Geyser cargue todo.
4. Con GeyserModelEngine les manda los modelos a los de Bedrock. Sin GeyserModelEngine les deja ver el mob de abajo.

Riesgos, sin probar con un cliente de Bedrock:
- La conversión de los modelos copia el exportador de Blockbench. Si algo sale espejado o con la textura corrida,
  se corrige en `tools/donpollos/generar_geysermodelengine.py`.
- No sé si GeyserModelEngine copia todo lo especial de BetterModel: el tamaño que sigue a la escala del mob (jefe
  grande, Labubus chicos), las animaciones de una sola vez (láser, golpe) y esconder los láseres de los ojos.
- No confirmé que GeyserModelEngine, GeyserUtils y GeyserDisplayEntity tengan versión para Paper 26.2.
- GeyserDisplayEntity no mueve bien en Bedrock los displays que se mueven (issue #6723 de Geyser). Los
  meteoritos podrían verse quietos o no verse.

## Cambios hechos en el plugin para Bedrock (4 de octubre de 2026)

| Problema | Qué hace ahora el plugin |
|---|---|
| Los modelos 3D eran invisibles en Bedrock | A los jugadores de Bedrock no se les manda el modelo, así que **ven el mob de abajo**: los Don Pollos como aldeanos, el jefe como un Ravager grande y los bichos como zombies. Si instalás GeyserModelEngine, poné `bedrock.modelos-3d: true` en `don-pollos.yml` (en plugins/BigCasares/). |
| Todos los Don Pollos se verían iguales | Cada uno es un aldeano de otra región, con otra ropa: Común (llanura), Gordito (desierto), Salsero (jungla), Aura 67 (sabana), Fino (nieve). En Java no cambia nada, porque lo tapa el modelo. |
| Gallinas y Labubus serían zombies iguales | Llevan casco y pechera de cuero de su color: gallinas verde WhatsApp y cada Labubu de su color. El Coronel y los Sanders ya tenían netherite. En Java no se ve, porque BetterModel esconde la armadura. |
| Ítems como galletita o papel | El plugin trae un **pack de Bedrock** con los íconos (pollo frito, salsa, picante, balde, secreto) y el **mapeo de Geyser** por `item_model`. Los instala solo en `plugins/Geyser-Spigot/packs/` y `custom_mappings/`. |
| Audios de Don Pollo mudos | El mismo pack trae los audios con `sound_definitions.json` (con y sin el prefijo `donpollos:`). |
| No se podía poner el balde | **Click derecho con el balde en la mano** te lo pone en la cabeza, en Java y en Bedrock. En Bedrock el mapeo además lo marca como casco. |
| El bloque del secreto no cambiaba | Al volverse malo, el bloque de abajo pasa a ser un bloque rojo (bloque de verruga del Nether). En Java lo sigue tapando la cara. |

Cómo detecta a los de Bedrock: con la API de Floodgate o de Geyser si están en el servidor; si no, por la UUID
que les inventa Floodgate (empieza con `00000000-0000-0000`).

Lo que **sigue sin verse** en Bedrock: los meteoritos de WhatsApp (se ve la estela de fuego) y la cara de Don Pollo
en el bloque del secreto. Las dos cosas son *item displays*, que Bedrock no tiene.

**Instalación con Geyser:**
- **Geyser en el mismo servidor (Paper):** arrancá una vez, el plugin copia los archivos y avisa en la consola.
  Reiniciá para que Geyser los cargue.
- **Geyser en un proxy** (Velocity o BungeeCord): copiá a mano `plugins/BigCasares/don-pollos/bedrock/DonPollos-bedrock.mcpack`
  a `packs/` y `donpollos-geyser.json` a `custom_mappings/` de Geyser.
- **Si cambiás texturas o audios:** volvé a generar los archivos con `python tools/donpollos/generar_bedrock.py`.

**No probado con un cliente de Bedrock de verdad.** En particular hay que confirmar jugando:
- que se vea el mob de abajo, que es lo más importante;
- que se vean los íconos;
- que suenen los audios.

## Resumen (revisión original, antes de los cambios)

Un jugador de Bedrock (celular, consola, Windows 10/11) solo puede entrar a este servidor con **Geyser + Floodgate**,
que traducen Bedrock a Java. Con eso instalado y nada más:

- **Toda la lógica funciona:** menús, comercio, casino, frases, Batallas del Cubo, fases del jefe, daño,
  invocaciones, premios, balde (salvo ponérselo), mensajes de muerte y ciudades.
- **Lo visual personalizado no se ve.** Los Don Pollos, el jefe, las gallinas, los Labubus, el Coronel, los
  meteoritos y el bloque con la cara quedan **invisibles**, y los ítems personalizados se ven como el ítem común
  de abajo (galletita, papel, beacon).
- **Los audios de Don Pollo no suenan.** Los sonidos de Minecraft sí.

| Estado | Qué |
|---|---|
| Funciona igual | Menús (pollería, casino, Gordito, premios con páginas), títulos, barras de jefe, chat, comandos, fases y ataques del jefe, cubo y paredes, frases, ciudades, daño y mensajes de muerte |
| Funciona distinto | Partículas de colores (pueden cambiar de tono), batalla de baile en pantalla táctil, flechas Unicode en los títulos |
| No se ve / no suena | Todos los modelos 3D de BetterModel, meteoritos, cubo con la cara de Don Pollo, texturas de ítems, audios de Don Pollo |
| No funciona | Ponerse el Balde de KFC en la cabeza |

Hay arreglo para casi todo (ver "Qué hacer"). El más importante es instalar **GeyserModelEngine**, que convierte
los modelos de BetterModel para Bedrock.

## Antes de empezar: versión

El servidor es Paper **26.2**. Geyser suele dar soporte a la versión nueva de Java poco después de que sale, pero
hay que **confirmar en geysermc.org/download que haya un build para 26.2** antes de prometer nada. No lo pude
verificar.

## Detalle por función

### 1. Modelos 3D (BetterModel): no se ven — problema grande

**Qué pasa:** BetterModel dibuja los modelos con *item displays*, un tipo de entidad que **Bedrock no tiene**
(fuente: issue #3810 de Geyser). Además BetterModel esconde el mob de abajo mandándole la marca de *invisible*
(lo confirmé en la API: `EntityHideOption` tiene `visibility` y `equipment`). Un jugador de Bedrock recibe
"invisible" pero no puede dibujar el modelo, así que **no ve nada**.

**A quién afecta:**
- Los 5 Don Pollos (por dentro son aldeanos).
- El Don Pollo Boss (por dentro es un Ravager): ve la barra de vida, las partículas y los rayos, pero no al jefe.
- Las gallinas WhatsApp, los Labubus, el Coronel y los Sanders (por dentro son zombies). Pegan sin que se los vea.

**Lo que sí sigue andando:** la entidad existe, así que se le puede tocar o pegar apuntando donde está.
Deducción mía, sin probar: en Bedrock se puede interactuar con mobs invisibles, pero apuntarles sin verlos es
muy difícil, y en la práctica la pelea queda injugable.

**Arreglo:** instalar **GeyserModelEngine**, que "convierte modelos de ModelEngine/BetterModel para jugadores de
Bedrock". Necesita:
- el plugin GeyserModelEngine;
- packetevents;
- GeyserUtils;
- GeyserModelEngineExtension y geyserutils-geyser en las extensiones de Geyser;
- Floodgate con `send-floodgate-data: true`.

Límites declarados: las texturas múltiples o animadas necesitan exportarse con un plugin de Blockbench. No lo
probé con estos modelos. BetterModel aclara que Bedrock no es una plataforma que soporten oficialmente.

### 2. Meteoritos de WhatsApp y cubo con la cara: no se ven

También son *item displays* (`DonPollosBossMeteors`, `DonPollosSecret.faceCube`).
- **Meteoritos:** en Bedrock se ve la estela de fuego y humo y la explosión, pero no el logo. Igual se pueden
  esquivar por la estela.
- **Bloque del secreto:** se ve un beacon común toda la historia y no cambia a la cara mala. Las frases, las
  partículas, la explosión y el jefe sí salen.

**Arreglo:** la extensión **GeyserDisplayEntity** (no oficial) hace visibles los item displays en Bedrock, pero
necesita los modelos en un pack de Bedrock. Tiene un problema conocido: si se mueve el display, Bedrock no lo
actualiza (issue #6723). Eso afecta justo a los meteoritos, que se mueven todo el tiempo. Para el cubo de la cara,
que está quieto, sí serviría.

### 3. Ítems con textura propia: se ven como el ítem común

El plugin usa el componente `item_model` (Java 1.21.4+). Bedrock no lo entiende sin un mapeo de Geyser. Sin
mapeo, el jugador ve el ítem base:

| Ítem | En Java | En Bedrock (sin mapeo) |
|---|---|---|
| Pollo frito | textura propia | Pollo cocido |
| Salsa, Picante | textura propia | Galletita (las dos iguales) |
| Ícono del balde (menú pollería) | balde rayado | Papel |
| Balde de KFC dado vuelta | ícono / balde 3D en la cabeza | Papel |
| Secreto del Don Pollo | cubo con la cara | Beacon |

**Lo que sigue andando:** los nombres y descripciones se ven, así que se distingue "Salsa" de "Picante" por el
nombre. Comer o tomar funciona: los efectos los da el servidor.

**Arreglo:** Geyser soporta mapear ítems por `item_model` (Custom Items API v2). La herramienta **Rainbow** de
GeyserMC genera los mapeos y el pack de Bedrock detectando los ítems con `item_model`.

### 4. Balde de KFC dado vuelta: no se lo puede poner — hay que arreglarlo en el plugin

Es un **papel** con el componente *equippable* en la cabeza. Bedrock decide del lado del celular o la consola qué
puede ir en el lugar del casco, y un papel no puede.

Deducción mía, sin probar: el jugador de Bedrock no lo va a poder arrastrar al lugar del casco y no va a recibir
Velocidad II ni Agilidad acuática II.

**Arreglo en el plugin (recomendado):** que con **click derecho con el balde en la mano** el servidor te lo ponga
en la cabeza. Es un cambio chico y le sirve también a Java.

### 5. Sonidos: los audios de Don Pollo no suenan

Los audios (`DonPollosSounds`) son sonidos propios del pack de Java (`donpollos:...`). Bedrock usa otro formato de
pack, así que **se quedan mudos**: el Común y el Gordito al acercarte, el Aura al comprar, el Fino al abrir el
casino, el Salsero al ganarle y el jefe. Los sonidos de Minecraft (cuernos, explosiones, rayo sónico, etc.) sí se
escuchan.

**Arreglo:** un pack de Bedrock con los `.ogg` y un `sounds/sound_definitions.json` con los mismos nombres. Geyser
los reproduce si los nombres coinciden con los de Java. Hay issues de Geyser con sonidos de plugins, así que hay
que probarlo.

### 6. Pack de recursos

El plugin manda el pack de Java con `addResourcePack(..., false)`, o sea **no obligatorio**. A un jugador de
Bedrock no le hace nada (Bedrock no usa packs de Java) y no lo echa del servidor. Hay un issue de Geyser (#5741)
de rechazos de pack al cambiar de servidor; como acá no es obligatorio, no debería afectar.

### 7. Lo que funciona igual

Todo esto pasa en el servidor o usa cosas que Bedrock sí tiene:
- **Menús de cofre** (todos son de 27 o 54 lugares y se manejan con click): pollería, casino con ruleta, menú del
  Salsero, menú del secreto del Gordito, menú de premios con categorías y páginas.
- **Títulos, subtítulos, chat y barra de acción:** frases de los Don Pollos, de las fases, de la derrota, de la
  historia del bloque, de las ciudades y de despedida.
- **Barras de jefe:** la del Don Pollo Boss y la de Batallas del Cubo. Las muescas (*NOTCHED_10*) en Bedrock
  pueden verse como barra lisa.
- **Lógica del jefe:** fases, persecución, alejarse, láser, rayo de warden (el daño y el empuje los da el
  servidor), mega salto, onda expansiva (te levanta: lo hace el servidor con la velocidad), invocaciones,
  levitación y esfera (invulnerable), cubo con paredes y piso, sin poner ni romper bloques, relleno al ganar,
  derrota y llanto.
- **Comandos** `/donpollo ...` (con OP).
- **Ciudades:** son bloques normales.
- **Mensajes de muerte.**
- **El Gordito:** frases, menú, Salsa y Picante (se reconocen por datos internos, no por la textura) y el bloque
  del secreto (se pone como un beacon normal).

### 8. Funciona, pero distinto

- **Partículas de colores** (`DUST`): la esfera azul, los láseres violetas, el ritual rojo y blanco y la línea
  celeste de puntería. Geyser las traduce, pero el tono o el tamaño pueden no ser exactos. Las de Minecraft
  (fuego, humo, explosiones, rayo sónico) salen bien.
- **Batalla de baile del Salsero:** usa el evento de "teclas apretadas" (adelante, atrás, izquierda, derecha,
  saltar, agacharse). Geyser manda esas teclas desde Bedrock, así que con mando o teclado debería andar. En
  **pantalla táctil** el joystick es analógico y agacharse es un botón que queda fijo. Puede costar marcar una
  sola dirección limpia y "agachate" puede contar raro. Sin probar.
- **Flechas y símbolos en los títulos** (⬅ ⬆ ✔): la fuente de Bedrock puede no tener algunos y mostrar un
  cuadradito. Conviene probarlo, y si falla, usar texto ("IZQUIERDA") o flechas más comunes.

## Qué hacer (en orden)

1. **Instalar Geyser + Floodgate** (con build para 26.2) y probar con un jugador de Bedrock.
2. **Instalar GeyserModelEngine** con sus dependencias, para que se vean el jefe, los Don Pollos y los bichos.
   Sin esto la pelea no se puede jugar bien desde Bedrock.
3. **Arreglo en el plugin:** equipar el Balde de KFC con click derecho (cambio chico, sirve para todos).
4. **Pack de Bedrock** con Rainbow: texturas de los ítems (`item_model`) y sonidos de Don Pollo
   (`sound_definitions.json`).
5. **Opcional:** GeyserDisplayEntity para el cubo con la cara; los meteoritos no se arreglan bien con eso.
6. **Opcional en el plugin:** cambiar las flechas de los títulos del baile si en Bedrock se ven como cuadraditos.

**Plan B si GeyserModelEngine no anda con estos modelos:** que el plugin detecte con Floodgate a los jugadores de
Bedrock y no les esconda el mob de abajo. Así ven aldeanos, un Ravager y zombies en vez de nada. BetterModel tiene
un evento `PlayerHideTrackerEvent` que podría servir para eso; habría que investigarlo.

## Cómo se revisó

- **Leído en el código:** `DonPollosPackServer` (pack no obligatorio), `DonPollosSounds` (sonidos propios),
  `DonPollosBossMeteors` y `DonPollosSecret` (item displays), `DonPollosItems`, `DonPollosKfcBucket` y
  `DonPollosSecret` (`item_model` y *equippable*), `DonPollosBattleManager` (teclas) y los menús.
- **Leído en BetterModel 3.5.0:** cómo esconde el mob (`EntityHideOption`: visibilidad y equipo) y la
  configuración `follow-mob-invisibility`.
- **No probado:** nada de esto se probó con un cliente de Bedrock de verdad. Lo marcado como "deducción mía" hay
  que confirmarlo jugando.

## Fuentes

- [BetterModel (GitHub)](https://github.com/toxicity188/BetterModel)
- [GeyserModelEngine (GitHub)](https://github.com/GeyserExtensionists/GeyserModelEngine)
- [Geyser: item/block display entities, issue #3810](https://github.com/GeyserMC/Geyser/issues/3810)
- [GeyserDisplayEntity (GitHub)](https://github.com/GeyserExtensionists/GeyserDisplayEntity)
- [Geyser: displays que se mueven no se actualizan en Bedrock, issue #6723](https://github.com/GeyserMC/Geyser/issues/6723)
- [Geyser: Custom Items](https://geysermc.org/wiki/geyser/custom-items/)
- [Rainbow (GeyserMC)](https://geysermc.org/wiki/other/rainbow/)
- [Geyser: sonidos de Java en Bedrock, issue #2493](https://github.com/GeyserMC/Geyser/issues/2493)
- [Geyser: rechazo del pack de recursos, issue #5741](https://github.com/GeyserMC/Geyser/issues/5741)
- [Geyser: descargas](https://geysermc.org/download/)
