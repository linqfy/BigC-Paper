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

## Celular

Módulo `celular`: un celular 3D que pasa videos en la pantalla cuando lo tenés en la mano.

- **Receta:** 8 lingotes de hierro alrededor y 1 vidrio en el medio. También aparece en el primer cofre que se abre en cada aldea.
- **Controles (Java):** **F** pasa al siguiente video y **Shift + F** vuelve al anterior.
- **Comando:** `/celular give [jugador]` (alias `/celu`), con el permiso `bigcasares.celular.admin`.
- **Bedrock (Geyser + Floodgate):** se ve el celu en 3D con "Pobre" en la pantalla, sin videos ni controles.
- **Config:** `modules.celular.enabled`, `celular.recipe.enabled` y `celular.village-loot.enabled` en `config.yml`.

Guía completa (uso, Bedrock, cómo agregar videos y regenerar los assets): [`docs/celular/README.md`](docs/celular/README.md).
