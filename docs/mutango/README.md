# Mutango Boss (Don Pollos, la secuela)

Modulo `mutango` de BigCasares (paquete `dev.linqfy.bigCasares.modules.mutango`): el boss en tres fases con Lef, Lihuen
y Lican, Neguechen con el Sol y la Luna, el santuario en ruinas del mundo, los premios (Botas de Neguechen y Cruz Andina)
y soporte completo para Bedrock. Nacio como un plugin aparte y se integro a BigC como modulo.

## Como se prende y donde esta cada cosa

- **Modulo:** `modules.mutango.enabled` en `config.yml` (id `mutango`). Comando `/mutango` (permiso
  `bigcasares.mutango.admin`, hijo de `bigcasares.*`).
- **Configuracion:** `plugins/BigCasares/mutango.yml` (se copia la primera vez; solo tiene la seccion `bedrock`).
- **Datos que persisten:** `plugins/BigCasares/data/mutango/` (`pendientes.yml`: victorias y premios por entregar,
  `bloques-por-devolver.yml`: el diario de bloques, `ruinas.yml`: que ruinas se hicieron, con version).
- **Modelos 3D:** `src/main/resources/bettermodel/models/` (28 `.bbmodel`); `MutangoModelInstaller` los copia a BetterModel
  y lo recarga. Los `.bbmodel` se editan en Blockbench.
- **Pack de recursos (Java):** el de BigC ya manda el pack de BetterModel mezclado con el suyo, asi que los modelos llegan
  por ahi. Las texturas de las botas y la cruz estan en `resourcepack/java/assets/mutango/`. **No hay servidor de packs
  propio.**
- **Bedrock:** los modelos convertidos para GeyserModelEngine en `src/main/resources/mutango/bedrock/` y el pack con los
  iconos de las botas y la cruz en `src/main/resources/mutango/relics-bedrock/` (ver mas abajo).
- **Moderacion:** los intentos de cheat con las botas van directo a `ModerationModule.acceptSignal` (ver mas abajo).
- **Datos de las entidades:** las marcas (PDC) usan el espacio de nombres `bigcasares` (como todo BigC).

## Probarlo

`gradlew.bat runServer` levanta un Paper 26.2 de prueba (hace falta BetterModel en sus `plugins/`). Entra como OP y usa
`/mutango` (spawnea los tres), `/mutango santuario` (arma el santuario delante tuyo) o `/mutango ruinas aqui`.

## Comandos (OP)

- `/mutango`: spawnea los tres en fila delante tuyo: **Lef** (1 ojo), **Lihuen** (2 ojos) y **Lican** (3 ojos).
  Cada vez es una pelea nueva e independiente (con la dificultad de quien la usa): las que ya habia siguen como
  estaban. En cada pelea hay un solo Lef, un solo Lihuen y un solo Lican (al arrancar el servidor se borran los
  repetidos y los viejos que eran aldeanos).
- `/mutango atacar`: todos hacen la animacion `attack` una vez.
- `/mutango quitar`: saca todas las peleas (los Mutangos, Neguechen, el Sol y la Luna de todas).
- `/mutango fase <1|2>`: pasa la pelea de Mutangos mas cercana a esa fase (para probar sin tener que pegarles).
- `/mutango fase 3`: saca a los Mutangos y aparece Neguechen a 12 bloques delante tuyo.
- `/mutango ruinas cerca`: donde quedan las ruinas mas cercanas (y si ya estan hechas).
- `/mutango ruinas aqui`: construye unas ruinas delante tuyo (para probar).
- `/mutango santuario`: arma solo el santuario (estatuas y cubo) delante tuyo, para probar el despertar.
- `/mutango premios`: te da las Botas de Neguechen y la Cruz Andina (para probarlas).

Por dentro son vacas silenciosas sin ninguna IA propia (solo las mueve el plugin; asi no salen corriendo de panico
cuando les pegas) que BetterModel esconde; `idle` y `walk` las pone BetterModel solo. Tienen rango de seguimiento 64 (el de una vaca es
10 y no llegarian a lugares lejos).

Solo atacan a jugadores en **supervivencia o aventura**: en creativo te ignoran.

## Vidas

Cada uno tiene su propia vida (50, `MAX_HEALTH` en `MutangoFights.java`) y su barra de boss: Lef roja, Lihuen
amarilla y Lican violeta, visibles a menos de 64 bloques. Solo los lastiman los jugadores (golpes o flechas); los
golpes suenan a piedra. Al morir cada uno avisa en el chat, y cuando caen los tres: "¡Derrotaste a Lef, Lihuen y Lican!".

Arrancan en la fase 1. **En la fase 1 no se mueren:** cuando a uno se le acaba la vida queda caido (desplomado, barra
vacia, no recibe dano). **Cuando caen los tres** se levantan con la vida llena y arranca la fase 2. En la fase 2 si se
mueren de verdad. Cada cambio de fase sale en pantalla.

## Fase 1: cabras

Cada Mutango por su cuenta te persigue y:

- **De lejos (3 a 10 bloques, si te ve): embestida.** Frena, se inclina para atras despacio mirandote (animacion
  `embestida`, 1.2 s) y se tira de cabeza en linea recta hacia donde estabas (unos 9 bloques). Pega 5 corazones y te
  tira por el aire. Despues se queda quieto 1 s y espera 2 a 4.5 s para volver a embestir. Se esquiva corriendose de
  costado mientras se inclina.
- **De cerca (menos de 3 bloques): cabezazo.** Se carga 0.35 s y te mete un cabezazo con un saltito para adelante
  (animacion `cabezazo`): 4 corazones y te levanta. Lo repite cada 1.5 s como mucho.

El golpe se mide con la caja que ocupa el cuerpo en la pose de cabeza tirada para adelante (sale del modelo: 1 bloque
hacia adelante, medio bloque a cada lado, de 0.33 a 1.71 de alto) contra toda tu hitbox, no con un radio desde el centro.
Para pegarle a el, el modelo tiene el hueso `b_cuerpo` (invisible): BetterModel lo vuelve una hitbox que se mueve con
el cuerpo cuando se inclina o se tira de cabeza.

Para que te ataquen hay que estar en supervivencia (`/gamemode survival`).
Todos los numeros estan arriba de todo en `MutangoPhaseOne.java`.

## Fase 2: a las pinas y apilados

1. **Pinas de bebe zombie:** te persiguen rapido y te pegan con los brazos (1.5 corazones, como un zombie), cada uno
   como mucho una vez por segundo.
2. Cada vez que les sacas el **10% de la vida maxima de los tres juntos** (15 de 150) se van corriendo y se arman
   **a 20 bloques fijos de donde estabas** en ese momento, para el otro lado (el lugar no te sigue). Llegan
   corriendo; solo si se traban (3 s sin acercarse) se los pone en su lugar. Desde ese momento **no reciben dano** hasta que los lanzan (suena a escudo).
3. **Se apilan** en un orden al azar: el del medio salta arriba del de abajo y el de arriba salta arriba del del
   medio. El de abajo agarra al del medio de los pies y el del medio al de arriba. Mientras se apilan los rodea una
   esfera de pocas particulas grises que gira.
4. El del medio **tira para atras** al de arriba y **lo lanza de cabeza** contra vos; despues **el de abajo hace lo
   mismo con el del medio**. Vuelan muy rapido (2.6 bloques por tick: 20 bloques en menos de medio segundo; el golpe
   se chequea en pasos chicos para no atravesarte). Cada golpe es dano de golem de hierro (3.75 a 10.75 corazones) y salis volando. Apuntan
   al piso debajo tuyo (si seguis en el aire por el primer golpe, adonde vas a caer). Rebotan un poco y, cuando caen,
   vuelven a pegarte.
5. Vuelven a las pinas y la cuenta del 10% arranca de nuevo: cada 10% que les sacas se vuelven a apilar.

Con dos vivos se apilan los dos y el de abajo lanza al otro; con uno solo ya no se apila. Cuando mueren los tres,
2 segundos despues aparece Neguechen donde cayo el ultimo: la fase 3.
Los numeros estan arriba de todo en `MutangoPhaseTwo.java`. Video de la coreografia: `mutango_apilar.mp4`.

## Fase 3: Neguechen

Neguechen (agrandado 1.6 veces: unos 7 bloques de alto) flota mirandote siempre (150 de vida, barra blanca) y cada 2.5 s pega un **saltito** en arco a otro lugar
cerca (nunca a mas de 6 bloques de donde aparecio), con el **Sol y la Luna** dando vueltas arriba de su cabeza.
Mientras tiene al Sol y a la Luna lo rodea una espiral de particulas doradas y blancas y **no le podes pegar** (suena
a escudo); **cuando los manda a atacar se le van las particulas y ahi si** (el anillo del piso se pone gris y le saltan chispas electricas). Al Sol y a la Luna no se les puede pegar.

**Primer ataque:** empuja las manos para adelante y manda al Sol a estrellarse contra vos, y la Luna sale 0.8 s
despues (asincronicos). Cada uno calcula la trayectoria hacia vos (a la altura del pecho) en el momento en que sale y
la sigue sin corregir, bastante rapido (1.3 bloques por tick). Si te toca: 5 corazones y te empuja. Te toque o no,
sigue hasta el piso y **queda clavado** (con una explosion chiquita). Las particulas de Neguechen se le van cuando sale
el Sol (ahi le podes pegar) y **vuelven 6 segundos despues de que salio la Luna**: ahi el Sol y la Luna se desclavan
y vuelven a girar (y ya no le podes pegar), y unos 5 segundos despues los vuelve a mandar. El primero sale a los 4 segundos de aparecer.

**Segundo ataque (las manos):** levanta las manos arriba de la cabeza, se le sueltan y te las tira en parabola a
**5 bloques de cada lado tuyo** (su izquierda a tu izquierda; la derecha cae un poquito despues). En el vuelo se
agrandan (hasta 2.6 veces) y se dan vuelta para caer con la palma para abajo. Cuando caen, cada una larga una **onda
expansiva** por el piso: un anillo de polvo que se agranda hasta 9 bloques y **arranca los bloques del piso** por
donde pasa (salen volando y dejan el hueco; a los 3 segundos vuelven a estar como antes, asi no se rompe el mapa; no
toca cofres ni bloques irrompibles). Las manos caen acostadas, con la palma para abajo. **Si te agarra parado en el piso te levanta muy alto** (1.5 corazones y el golpe para arriba):
para esquivarla hay que **saltarla** justo cuando pasa. Despues de 1.5 s vuelven achicandose y se le pegan de nuevo.
Mientras tanto el Sol y la Luna siguen girando, asi que no le podes pegar.

**Tercer ataque (los animales, el menos probable):** empuja las manos e invoca exactamente **3 vacas, 2 gallinas,
1 caballo, 1 burro y 5 chanchos** en un anillo a su alrededor. Te persiguen corriendo cada uno a su velocidad de
Minecraft (caballo 0.3, gallina y chancho 0.25, vaca 0.2, burro 0.175) y te pegan al tocarte, como mucho una vez por
segundo cada uno: gallina medio corazon, chancho 1.5, vaca 2, burro 2.5 y caballo 3. Dejan una estelita dorada. Se
los puede matar (no sueltan nada, y no se los puede montar ni alimentar) y a los 15 segundos los que quedan
desaparecen. No vuelve a invocar mientras queden animales de la vez anterior.

**Cual ataque usa es al azar** cada vez (puede salir 1, 1, 2, 1, 3, 2...): el Sol y la Luna el 30% de las veces, las
manos el 40% y los animales el 30% (`SUN_AND_MOON_CHANCE` y `HANDS_CHANCE`). Entre un ataque y el siguiente pasan unos
5 segundos.

Los numeros estan arriba de todo en `MutangoPhaseThree.java` (primer ataque), `MutangoHandsAttack.java` (segundo) y
`MutangoAnimalsAttack.java` (tercero).
**Si se reinicia el servidor (o se descarga y vuelve el chunk) la pelea se retoma donde estaba:**

- Cada Mutango guarda en su ficha la fase en la que estaban (1 o 2), si esta caido, con que dificultad salio y quienes
  pelearon. La vida la guarda Minecraft. Retoman la fase sin curarse ni avisar en pantalla, y los caidos siguen caidos.
- Neguechen peleando se retoma donde estaba, con la vida que tenia y el Sol y la Luna girando de nuevo (los ataques en
  curso, como las manos, los animales o el Sol y la Luna en vuelo, se pierden). Si la estaban venciendo (diciendo sus
  ultimas palabras) no se retoma: la victoria y los premios ya estan guardados (ver "El final y los premios").
- Lo que no se guarda: cuanto daño llevan para la proxima pila de la fase 2.

## Dano y mensajes de muerte

Todo lo que te pega (embestidas, cabezazos, pinas, lanzados, Sol y Luna, manos, animales y espinas) sale con la entidad
que pega como causa directa: el escudo lo frena, el empuje respeta tu resistencia al retroceso (armadura de netherite) y
el mensaje de muerte sabe quien fue. Si te mata Lef, Lihuen o Lican sale uno de estos (al azar):

- "(Mutango) te ha matado"
- "Moriste a pies de (Mutango)"
- "El poder mapuche de (Mutango) te mató"

Si te mata Neguechen (o su Sol, su Luna, sus manos o sus animales):

- "Neguechen te mató"
- "Neguechen te destruyó"
- "Se nota que sos tan malo que una diosa te ganó"
- "Sos malísimo, te ganó una mapuche"

A quien muere le sale el mensaje tal cual en la pantalla de muerte; en el chat sale con su nombre ("Juan: Lef te ha matado").
Los textos estan en `MutangoDeathMessages.java`.

## Cada vez mas dificil

Como el Don Pollo Boss en BigC. Cada jugador lleva la cuenta de cuantas veces le gano a Neguechen: cuando la vencen
le suma una a cada uno que peleo (el que empezo la pelea y los que le pegaron a alguno; los que miraban, no). Quien empieza la pelea (corriendo el
cubo del santuario, con `/mutango` o con `/mutango fase 3`) la hace salir mas fuerte segun cuantas victorias lleva, y se lo dice. **El aumento es logaritmico**: la primera victoria
suma un 50% y despues cada vez que se duplican las victorias mas uno (1, 3, 7, 15, 31...) suma otro 50%. Sube rapido al
principio y despues muy despacio:

| Pelea | Victorias previas | Fuerza (1 = la primera vez) |
|---|---|---|
| 1 | 0 | 1.00 |
| 2 | 1 | 1.50 |
| 3 | 2 | 1.79 |
| 4 | 3 | 2.00 |
| 5 | 4 | 2.16 |
| 6 | 5 | 2.29 |
| 7 | 6 | 2.40 |
| 8 | 7 | 2.50 |
| 9 | 8 | 2.58 |
| 16 | 15 | 3.00 |
| 32 | 31 | 3.50 |

De la pelea 8 en adelante ya casi no cambia (de la 8 a la 9 sube menos del 4%). La formula es
`1 + 0,5 * log2(1 + victorias)` (`MutangoDifficulty.factor()`). Vale para la vida, el dano, la frecuencia de los ataques,
la cantidad y velocidad de los animales. Las armaduras siguen su propia escalera, por victoria (ver mas abajo). Se aplica
a lo siguiente:

- Tienen mas vida: Lef, Lihuen y Lican (50 la primera vez), Neguechen (150) y los animales (hasta 1024, lo maximo
  de Minecraft). En esto va mas alla que Don Pollos, que no le sube la vida al jefe.
- Pegan mas: embestidas, cabezazos, pinas, lanzamientos, el Sol y la Luna, las ondas de las manos y los animales.
- Atacan mas seguido (nunca mas de una vez por segundo).
- Neguechen invoca mas animales (la primera vez exactamente 3 vacas, 2 gallinas, 1 caballo, 1 burro y 5 chanchos),
  mas rapidos (nunca mas del doble) y que pegan mas.

**Armaduras.** La primera vez salen como siempre; despues Lef, Lihuen, Lican, Neguechen y los animales salen con
armadura de **cobre**, **hierro**, **diamante** y **netherite**; y con netherite, cada vez con mejores encantamientos
(**Proteccion** hasta V y despues **Espinas** hasta III, que te devuelven el golpe **solo si pegas cuerpo a cuerpo**: una
flecha no, como las espinas de Minecraft).

- Se ve en el modelo: los Mutangos con casco (sin tapar los ojos), brazales, grebas y botas; Neguechen con hombreras
  y pechera. Son 16 modelos aparte (`*_cobre`, `*_hierro`, `*_diamante`, `*_netherite`), en la grilla de pixeles de cada uno.
- Protege de verdad: puntos de armadura y dureza como un juego completo de ese material (netherite tambien resiste el
  empuje), y la Proteccion frena como en Minecraft (hasta 80%).
- El caballo invocado lleva su armadura de caballo; los demas animales la llevan sin que se vea.

El nivel queda guardado en cada Mutango y en Neguechen, asi no cambia si se reinicia el servidor. Desde la consola
(o si nadie corrio el cubo) sale con la fuerza normal.

## El poema en pantalla

En cuatro momentos sale en el medio de la pantalla, como en una pelicula, un fragmento de un poema: cada verso
aparece letra por letra (italica, las palabras en mapudungun en dorado), se queda unos segundos y se desvanece, y
despues va el siguiente. Los carteles de "FASE 2", "FASE 3" y "¡Derrotaste a Neguechen!" esperan a que termine.

1. **Al correr el cubo del santuario:** "Ahora sé, que el kultrún, nuestro instrumento sagrado, / representa al
   planeta, / a wenu Mapu que es el espacio de la atmósfera,"
2. **Al terminar la fase 1:** "a trufken Mapu que es la superficie / y a minche Mapu que es el subsuelo. [...]"
3. **Al terminar la fase 2:** "Ahora que las fuerzas de la naturaleza / están cortadas por alambrados, cables y caños."
4. **Al terminar la fase 3:** "Minche Mapu entubada. / Trufken Mapu habitada por la prisa..."
5. **Enseguida, las ultimas palabras de Neguechen** (enteras en dorado): "Elueyu tami elun. / Minche Mapu mew küpan. /
   Weichayal wedake che mew. / Pepilan ñi newen mew."

Los versos estan en `MutangoPoem.java` y los tiempos en `MutangoCinematic.java`.

## El final y los premios

**Como aparece y como se va:** por una grieta. Se abre una raja en el piso de verdad (del centro hacia las puntas,
13 bloques de largo, 5 de ancho y mas honda en el medio) con luces verdes en el fondo (froglights) y luz verde que sube; ella
sale despacio desde abajo hasta donde flota, la grieta se cierra y recien ahi aparecen el Sol y la Luna y empieza a
pelear (mientras sale no se le puede pegar). La grieta queda de costado para quien mira, asi se ve entera. Al cerrarse
los bloques vuelven a estar como antes (si alguien quedo adentro lo saca para arriba); no toca cofres, bedrock ni agua.

Cuando le sacas la ultima vida a Neguechen **no muere**: frena (se cortan los ataques y se van el Sol y la Luna), sube
unos 2.5 bloques despacio y se queda flotando y mirandote, con luz que le cae, mientras salen los versos 4 y 5. Ya no
se le puede pegar y se va su barra. Cuando terminan se abre otra grieta debajo suyo, se hunde por ahi y, cuando la
grieta se cierra, **cada uno que peleo recibe los dos premios en el inventario** (el que la empezo y los que le pegaron a
alguno; lo que no entra cae al piso a sus pies, brilla, no desaparece y es solo suyo):

- **Aviso global.** En el momento en que le sacas la ultima vida, todo el servidor ve en el chat "(Jugador) le ha
  ganado a la diosa mapuche Neguechen" (si ganaron varios: "A, B y C le han ganado..."; si son mas de 4, nombra a 4 y
  cuenta el resto). Es el unico aviso de la pelea que ve todo el mundo; el resto lo ven los que estan cerca.
- **La victoria y los premios no se pierden.** La victoria se guarda en el momento en que le sacas la ultima vida y los
  premios quedan anotados en `plugins/BigCasares/data/mutango/pendientes.yml`. Si sacan la pelea con `/mutango quitar` antes de que
  se entreguen, se dan en el momento. Si se apaga el servidor, o estabas desconectado, o muerto, los recibis al entrar,
  al revivir o cuando el servidor vuelve a arrancar.

- **Botas de Neguechen** (botas de cuero, Proteccion IV, Caida de pluma IV, irrompibles). Puestas, en supervivencia o
  aventura: apreta saltar en el aire y saltas otra vez (una vez por salto, se recarga al tocar el piso). El doble salto
  pone la caida en cero (y las botas tienen Caida de pluma IV). No flotan: se saco porque dependia de la velocidad
  vertical del jugador, que no es confiable.
- **Cruz Andina** (un totem con brillo). Te salva como un totem, pero te deja con la mitad de la vida (el totem, con
  medio corazon) y con mas efectos que duran mas: Regeneracion III 60 s, Absorcion IV 60 s, Resistencia al fuego
  120 s, Resistencia II 30 s, Velocidad II 30 s, Fuerza I 30 s y Respiracion acuatica 60 s.

## Las botas: modelo 3D y modelo puesto

Las Botas de Neguechen no son un icono plano: el item es un modelo 3D (`resourcepack/java/assets/mutango/models/item/
botas_neguechen.json`) de **botas de vaquero de piedra con un poco de musgo** (cana alta con borde oscuro y una pestana
arriba, taco). Atras tienen una **espuela roja** y su rueda, una estrella de ocho puntas que es **el Sol (amarillo) en
una bota y la Luna (blanca) en la otra**. Puestas se ven con un modelo propio (`equipment/neguechen_boots.json` + una
textura de 64x32: piedra y musgo, y atras la espuela roja con el Sol y la Luna; la espuela no sobresale puesta porque ese
modelo es solo una textura). En Bedrock
siguen con el icono plano (Geyser no tiene este modelo puesto).

## Intentos de cheat: se los pasamos a BigC

Mutango **no resuelve** los intentos de cheat que ve: se los manda al sistema de moderacion de BigC (el modulo
`moderation-system`, por `acceptSignal`), que suma puntos y avisa al staff cuando corresponde. Es una llamada
directa al modulo (`MutangoModeration`), que lo pide al momento de avisar: si el modulo de moderacion esta apagado,
el aviso no hace nada.

- **Intentos de volar con las Botas de Neguechen:** las botas usan el permiso de volar para el doble salto, asi que un
  cliente tramposo podria intentar volar de verdad. Cada intento se cancela, y si hay 8 o mas seguidos en 2 segundos
  (los que no dieron un doble salto), o si alguien aparece volando con las botas puestas, se manda la senal
  `mutango-boots-flight` (30 puntos, con lo que se vio como evidencia; despues espera 30 s antes de volver a avisar del
  mismo jugador). Lo ve el sistema de BigC con el resto de sus senales.
- **Lo que BigC no marca por error:** su deteccion de movimiento ya ignora a quien tiene permiso de volar (las botas
  puestas) y a quien recibe un empuje, asi que el doble salto no suma "upward-movement".
- Las reglas nuevas no hace falta cargarlas en BigC: si una regla no esta en su configuracion usa los puntos que manda
  Mutango. Si querés cambiar los puntos, agregala en la configuracion de BigC
  (`moderation-system.signals.mutango-boots-flight.points`) — OJO: hoy BigC solo lee los puntos de sus reglas conocidas,
  asi que eso hay que agregarlo del lado de BigC.

## Zonas protegidas

Antes de sacar o cambiar un bloque, la grieta y las ondas de las manos le preguntan a los plugins de proteccion
(WorldGuard, claims, etc.) disparando el evento de cambio de bloque de una entidad (`EntityChangeBlockEvent`, el que se
usa contra los mobs que rompen bloques) con Neguechen como autora. Si alguien lo cancela, ese bloque no se toca.

- **La grieta:** si en el lugar donde Neguechen iba a aparecer la grieta tocaria algun bloque protegido, aparece en el
  lugar permitido **mas cercano** (hasta 12 bloques de distancia). Lo mismo al irse: antes de hundirse se corre al lugar
  permitido mas cercano para abrir la grieta. Si todo alrededor esta protegido, se abre igual donde estaba pero sin
  tocar lo protegido.
- **Las manos:** si la mano iba a caer en una zona protegida, cae en el lugar permitido mas cercano (hasta 10 bloques).
  Las ondas nunca arrancan bloques protegidos.
- **Quien se mete en una zona protegida para que no le tire las manos:** si Neguechen va a tirar las manos y el jugador al
  que apunta esta parado en una zona protegida, no se las tira: le aparece una **cuenta regresiva de 5 segundos** en la
  pantalla (el numero grande en el medio, "Salí de la zona protegida", y "Tenés N segundos para salir de la zona
  protegida" en la barra de abajo, mas un aviso en el chat solo para el). Si sale de la zona antes de que se acabe, se
  cancela ("Saliste a tiempo"). Si no sale, le cae un **mega rayo de Warden**: una columna de luz azul del cielo, trueno y
  estallido sonico, que le hace **dano solo a el** (20 de base, mas con la dificultad; no lo frena la armadura; no toca
  bloques ni a nadie alrededor). Si sigue ahi en el proximo ataque de manos, vuelve a empezar la cuenta. Si lo mata, la
  pantalla de muerte dice "Moriste por cagón, las zonas protegidas como que no son para pelear, eh" y despues sale en el
  chat para todo el servidor: "Hola Gente, (Jugador) es un cagón, no entiende que la idea de las peleas es pelear y no
  es meterse en zonas protegidas para evitar ataques, no sean como (Jugador)".

## Si el servidor se cae con la grieta o las ondas abiertas

Cada bloque que la grieta o las ondas de las manos sacan se anota antes en `plugins/BigCasares/data/mutango/bloques-por-devolver.yml`
(como era) y se tacha cuando se devuelve. Si el servidor se cae o se cuelga en medio, **al arrancar devuelve los bloques
que faltaban** (los que siguen siendo aire o la luz verde de la grieta; si alguien puso algo ahi, se respeta) y avisa en
la consola. Con un apagado normal ya se cierra todo antes.

Si saliste del servidor estando dentro de la grieta o de un hueco de las ondas y los bloques se devolvieron mientras no
estabas, **al volver a entrar te sube a la superficie** (igual que a los que estan conectados cuando se cierra): si al
entrar tenes los pies y la cabeza dentro de bloques, te lleva a la superficie en ese mismo lugar.

## Ruinas en el mundo

Las ruinas de `ruinas modelo/Ruined World Spawn.litematic` (42 x 26 x 52: una plataforma redonda en ruinas con una
torre y un beacon) aparecen solas en el terreno nuevo de los mundos normales, como las ciudades de Don Pollos: el
mundo se divide en celdas de 800 bloques y en el 70% de las celdas hay unas, en un lugar y con un giro al azar fijos
segun la semilla (nunca a menos de 250 bloques del spawn). Se construyen por tandas cuando se genera su chunk del
medio.

- **Nunca sobre agua:** si en el lugar hay aunque sea un poco de agua (o hielo, algas, bloques inundados) no se ponen.
- **Relleno:** la capa de abajo de las ruinas (pasto y flores) va apoyada en el piso, y para abajo se rellena con
  **piedra y piedra musgosa** (cobblestone musgosa) hasta tocar suelo firme, asi nunca quedan colgando. Abajo de las
  plantas va un bloque de pasto para que no se rompan.
- Las que ya se hicieron (o se saltearon por agua) quedan en `plugins/BigCasares/data/mutango/ruinas.yml`. Si el servidor se cae a
  mitad de construir unas (tambien las de `/mutango ruinas aqui`, que guardan donde estaban), se terminan al arrancar; un
  registro que no se entiende se descarta con un aviso y no impide que el plugin arranque.

**El santuario:** en el medio de la plataforma (donde estaba el beacon) estan **Lef, Lihuen y Lican como estatuas
con los ojos cerrados** y, delante de ellos, un **cubo de piedra** (textura `referencias visuales/TEXTURA.png`) tapando
un agujero. Si le haces click al cubo (derecho o izquierdo):

1. se corre un bloque hacia la izquierda (mirando a las estatuas), frenando, y deja ver el agujero;
2. del agujero sube una esferita de particulas doradas y blancas, se queda flotando y se divide en tres;
3. cada una va hacia una estatua; cuando las tocan brillan y el cubo se vuelve a cerrar;
4. las estatuas cobran vida (los mismos tres, con los ojos abiertos) y arranca la pelea en la fase 1.

Cada despertar es una pelea aparte (`MutangoFight`): tiene su propia dificultad (la de quien corrio el cubo), sus
Mutangos, quienes pelearon, su fase, su Neguechen y sus versos en pantalla. Puede haber varias a la vez (una por
santuario) sin pisarse, aunque haya Mutangos sueltos de otras peleas. Cada entidad guarda el id de su pelea, asi se
vuelve a armar sola si se reinicia el servidor.
Si el despertar se corta antes de que las estatuas cobren vida (se descarga el chunk, se cae o se apaga el servidor, o
sacan la pelea con `/mutango quitar`), el cubo vuelve solo a su lugar cerrado (al cargarlo o al clickearlo) y las
estatuas siguen ahi: se puede volver a abrir.
Las estatuas y el cubo no se pueden lastimar. Despues de despertarlos, el cubo ya no se mueve.

La estructura esta en `src/main/resources/mutango/structures/ruinas.json.gz`. Los numeros (separacion, probabilidad, mezcla de piedra) estan arriba
de todo en `MutangoRuins.java`.

## Tests

```
gradlew.bat test --tests "dev.linqfy.bigCasares.modules.mutango.*"
```

JUnit 5 contra la API de Spigot (como el resto de BigC), sin servidor: solo se testean las partes puras.

- `MutangoRuinsLayoutTest`: donde caen las ruinas (misma semilla = mismos lugares, probabilidad, adentro de su celda,
  lejos del spawn, chunk ancla, la mas cercana).
- `MutangoStructureTest`: las ruinas cargan con las medidas del litematic, un solo beacon en la plataforma (ahi va el
  santuario), pasto abajo, y una estructura rota falla.
- `MutangoRotationTest`: los cuatro giros (cada columna adentro, ninguna repetida, ida y vuelta).
- `MutangoDirectionsTest`: frente e izquierda segun el yaw, como en Minecraft.
- `MutangoPoemTest`: el poema es el que va y la maquina de escribir no pierde letras ni corta colores.
- `MutangoModelsTest`: los modelos estan en el jar con las animaciones y huesos que el codigo pide por nombre.
- `MutangoBedrockTest`: quien es de Bedrock y cuando ve los modelos; que los modelos convertidos para
  GeyserModelEngine esten todos y al dia con los de Java (huesos, cubos, animaciones, textura, X espejada); que se
  instalen en Geyser sin reescribir lo que no cambio ni escribir afuera de la carpeta.
- `MutangoBootsJumpTest`: el doble salto (uno por vez, se recarga al aterrizar) y el aviso de intentos de volar (`MutangoFlightWatchTest`).
- `MutangoDifficultyTest` y `MutangoArmorTest`: la dificultad logaritmica (+50% la primera victoria y cada vez menos), nunca mas de un ataque por segundo,
  los materiales, la Proteccion y las Espinas, y que modelo con armadura le toca a cada uno.
- `MutangoRiftShapeTest`: la forma de la grieta (angosta, mas honda en el medio, una luz por fila, se abre del
  centro a las puntas).
- `MutangoCrossEffectsTest`: la Cruz Andina da todo lo del totem pero mas fuerte y mas largo, y mas efectos.

## Bedrock (Geyser + Floodgate)

Igual que Don Pollos en BigC (`docs/don-pollos/compatibilidad-bedrock.md`):

- Bedrock no dibuja los modelos de BetterModel. **Sin GeyserModelEngine** a los de Bedrock no se les manda el modelo,
  asi ven las vacas de abajo (si no, no verian nada). La pelea, las barras, el poema y las particulas andan igual.
- **Con GeyserModelEngine** el plugin copia los modelos convertidos a
  `plugins/Geyser-Spigot/extensions/geysermodelengineextension/input/` y avisa que hay que reiniciar una vez.
  Si Geyser esta en un proxy, el zip queda en `plugins/BigCasares/mutango/bedrock/` para copiarlo a mano.
- `plugins/BigCasares/mutango.yml`: `bedrock.modelos-3d: auto` (se prende solo con GeyserModelEngine), `true` o `false`.
- Para probarlo: `gradlew.bat runServer` baja Geyser, Floodgate, packetevents, GeyserUtils y
  GeyserModelEngine con la tarea `downloadBedrockPlugins`. Las versiones estan **fijadas** y se **verifican con su
  checksum** (sha256, o sha512 para los de Modrinth), como en BigC: se baja a un archivo temporal, se verifica y recien
  ahi se mueve, asi una descarga cortada no queda como valida y lo que ya esta y coincide no se vuelve a bajar. Son los
  mismos archivos que BigC, salvo GeyserModelEngine y su extension: BigC fijo unos que ya dan 404 (GeyserExtensionists
  republico el tag `latest` el 2026-10-15), asi que aca estan los vigentes de la misma version 1.0.9. Desde Bedrock: `localhost`, puerto `19132`.
- Si cambias un modelo hay que volver a convertir el zip de GeyserModelEngine (`src/main/resources/mutango/bedrock/geysermodelengine.zip`); el test avisa si quedo viejo.
- El aviso "reinicia el servidor para que Geyser cargue los modelos" solo sale cuando de verdad cambio algo. La extension
  de GeyserModelEngine modifica el `config.json` de cada modelo al cargarlo (llena `binding_bones` y agrega campos): si
  el que ya esta tiene todo lo que Mutango quiere poner, no se lo pisa (antes se lo pisaba en cada arranque y avisaba siempre).
- **Premios con textura:** las Botas de Neguechen y la Cruz Andina tienen textura propia. En Java van en el pack
  de BigCasares (`resourcepack/java/assets/mutango`) y en Bedrock por items personalizados de Geyser (`MutangoGeyserBridge`,
  pack `plugins/BigCasares/mutango/bedrock/premios-bedrock.mcpack`; hay que reiniciar si cambian). `bedrock.items-personalizados:
  false` los apaga.
- Sin probar con un cliente de Bedrock: no se sabe si GeyserModelEngine respeta la escala (Neguechen 1.6, las manos
  que crecen), el brillo `glow_` ni las animaciones de una sola vez.

## Cambiar los modelos

Los `.bbmodel` estan en `src/main/resources/bettermodel/models/`. Al reiniciar el servidor el modulo los copia a
BetterModel y lo recarga. Si cambias uno, el zip de Bedrock tiene que volver a convertirse (los tests `MutangoModelsTest` y
`MutangoBedrockTest` avisan si quedo viejo). Los scripts de Python que generaron los modelos no van en este repo.
