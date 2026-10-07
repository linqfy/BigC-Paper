# Herramientas de Don Pollos

Scripts de Python (necesitan Pillow) que generan los recursos del modulo `don-pollos`. Se corren desde la raiz de
BigC-Paper, por ejemplo `python tools/donpollos/generar_modelos.py`.

| Script | Genera |
|---|---|
| `generar_modelos.py` | Los 5 Don Pollos (`src/main/resources/bettermodel/models/`) |
| `generar_boss.py` | El Don Pollo Boss |
| `generar_wsp.py` | Gallina WhatsApp, logo de WhatsApp y el item del meteorito |
| `generar_labubus.py`, `generar_coronel.py` | Los 4 Labubus y el Coronel |
| `bailes_salsero.py` (+ `bailes/`) | Las 5 coreografias del Salsero (dentro del modelo del Salsero) |
| `generar_balde_casco.py` | Balde de KFC dado vuelta (`resourcepack/java/assets/donpollos/`) |
| `generar_bloque_secreto.py` | Bloque del secreto (caras de `fuentes/fotosbloque/`) |
| `convertir_sonidos.py` | Audios de Don Pollo (de `fuentes/sonidos/`, necesita ffmpeg) |
| `convertir_schematic.py` | La ciudad (`fuentes/schema/ciudad.schematic` -> `src/main/resources/donpollos/structures/`) |
| `generar_bedrock.py` | Pack de Bedrock y mapeo de Geyser (`src/main/resources/donpollos/bedrock/`) |
| `generar_geysermodelengine.py` | Modelos para GeyserModelEngine (`src/main/resources/donpollos/bedrock/`) |
| `render_modelo.py`, `render_animacion.py` | Vistas previas de los modelos |

`retarget.py`, `extraer_pose.py`, `comparar_baile.py` y `hoja_baile.py` sirvieron para sacar los bailes de videos de
referencia (no estan en el repo: pesan ~47 MB).
