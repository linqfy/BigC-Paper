# BigCasares

Plugin modular para Paper: misiones, economía, equipos y Nexus, bosses PvE,
eventos ambientales, ítems custom y administración del servidor. Incluye packs
Java/Bedrock e integraciones con BetterModel, Geyser y Discord.

## Plataforma y requisitos

- Minecraft/Paper **26.2**, Java **25**, Gradle Wrapper **9.2.1**.
- API de compilación fijada a `io.papermc.paper:paper-api:26.2.build.56-alpha`.
  Esto no fija el build del servidor que descarga `runServer`.
- **BetterModel es obligatorio** (`plugin.yml`); API usada: **3.2.0**.
- Con la configuración por defecto, **Vault y un proveedor de economía** son
  necesarios para Mission, Bounty y Entity Shop. Un fallo de activación revierte
  los módulos ya habilitados y hace fallar el arranque del plugin.
- PlaceholderAPI, Geyser y floodgate son dependencias opcionales. Discord requiere
  IDs configurados y credenciales locales; sin ellos, la integración no inicia.

## Compilar

Con un JDK 25 seleccionado en `JAVA_HOME` y `PATH`, desde la raíz:

```bash
bash gradlew --no-daemon clean build
```

En Windows: `.\gradlew.bat --no-daemon clean build`.
El wrapper no tiene permiso de ejecución en Git; CI también usa `bash gradlew`.

El build ejecuta los tests, valida los packs y genera:

- `build/libs/BigCasares-0.0.1.jar`
- `build/generated-resourcepacks/bigcasares-java.zip`
- `build/generated-resourcepacks/bigcasares-bedrock.mcpack`

El JAR incluye ambos packs y las fuentes que inicializan el contenido editable
del servidor. Los generadores Python son pasos manuales, separados del build.

## Documentación

- [Índice](docs/README.md): guías actuales y documentos históricos.
- [Desarrollo](docs/development.md): arquitectura, dependencias, tests y CI.
- [Operación](docs/operations.md): instalación, configuración, packs y recargas.
- [Dimensiones custom](docs/dimensions/README.md) y [The Sift](docs/dimensions/the-sift.md)
  (port del mod de Mielon; requiere reiniciar una vez tras instalar).
- [Contribución](GUIDELINES.MD) y [guía de agentes](AGENTS.md).
- [Modelado](docs/blockbench/00-bettermodel-setup.md),
  [Celular](docs/celular/README.md) y
  [cliente de prueba](tools/minecraft-test-client/README.md).

Los planes fechados describen decisiones históricas; para el comportamiento
actual, verificá el código y la configuración versionada.
