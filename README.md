```                                        
                       ▒▒▒▒▒▒▒                      
                 ▒▒▒▒▒▒▒▒▒▒▒░░░                     
                 ░░▒▒▒░░░░░░░▒▒                     
                ░░░░▒░░░░░░░░░▒▒                    
                ░░░░░░░░░░░░░░▒▒▒░░░░░░▒▒▒▒         
                 ░░░░░░░░░░░░░░▒▒░░░░▒▒▒▒▒▒▒        
         ▒▒░░░░░ ░░░░▒▒▒▒▒░▒░░▒▒▒░░▒▒▒▒▒▒▒▒▒▒       
        ▒▒░░░░░░░░░▒▒░▒▒▒▒░▒░▒▒▒▒░▒▒▒▒▒░░░░▒▒▒      
      ▒▒▒▒▒░░░░░░▒░░▓▒▓▓▓▓▓▓▓▒▒▒▒░▒░░░░░░░▒▒▒       
      ▒▒▒▒▒░░░░░░▒▒▒▒▒▒▓▓██▓▓▒▒▒▒░░░░░░▒▒▒▒▒▒       
       ▒▒▒▒░░░░░░░░░░▒▒▓█▓▓█▓▓▒░░░░░▒▒▒▒▒▒▒         
      ▒▒▒▒░░░░░░░▒░░▒▒▒▓▓██▓▓▓▓▓▒▒▒░▒▒▒▒▒▒          
      ▒▒▒▒░░░░░░░░░▒▒▒▒▓▓▓▓▓▓▓▓▒▒▒▒▒▒▒▒▒▒▒          
       ▒▒░░░░░░░░░░▒▒▒▒▒▒▒▓▒▓▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒        
         ░▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒░▒▓▒▒▒▒▒▒▒▒▒▒▒▒▒▒        
                ▒▒▒▒▒▒▒▒▒▒▒░░▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒       
                ▒▒▒▒▒▒▒▒▒▒░░░▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒        
                ░▒▒▒▒▒▒▒░░░░▒▒▒▒▒▓▒▒▒▒▒▒▒▓▓         
                ░░▒▒░░░░░░░▒▒▒  ▓▓▒▒▒▒▒▒            
                 ▒▒░▒░░░▒▒▒▒▒                       
                  ▒▒▒▒▒▒▒▒▒                         
```

## Plataforma soportada

- Minecraft Java 26.2.
- Paper 26.2 build 56 (`26.2.build.56-alpha`).
- Java 25.
- Gradle Wrapper 9.2.1.

Paper todavía publica 26.2 como build alpha. El plugin está fijado al build 56 para que las compilaciones sean reproducibles.

## Build

```powershell
.\gradlew.bat clean build
```

El build valida y genera automáticamente:

- `build/generated-resourcepacks/bigcasares-java.zip`
- `build/generated-resourcepacks/bigcasares-bedrock.mcpack`
- `build/libs/BigCasares-0.0.1.jar`

Vault y un proveedor de economía son necesarios para Mission, Bounty y Entity Shop. PlaceholderAPI y Geyser son integraciones opcionales con fallback seguro.

## Módulos de contenido

### Don Pollos

Agrega cinco Don Pollos: Común, Gordito, Salsero, Aura 67 con su pollería y Fino con su casino. También agrega
ciudades de Don Pollo que aparecen al generar el mundo, y el Don Pollo Boss: una pelea en cuatro fases con premio al
final (el Balde de KFC). Funciona en Java y en Bedrock.

- Comando `/donpollo` (permiso `bigcasares.donpollos.admin`).
- Se configura en `don-pollos.yml` y se apaga con `modules.don-pollos.enabled: false`.
- Necesita BetterModel en el servidor; se recomienda la 3.5.0.

Guía completa: [docs/don-pollos/README.md](docs/don-pollos/README.md). Bedrock:
[docs/don-pollos/compatibilidad-bedrock.md](docs/don-pollos/compatibilidad-bedrock.md).
