# Servidor predeterminado de Ascension

Componente de cliente para NeoForge 1.21.1. Durante la preparación del cliente añade `rails-needs.tun.ply.gg` a la lista de Multijugador como Ascension. Conserva entradas existentes, nombres e iconos personalizados; reconoce la dirección sin distinguir mayúsculas y con el puerto estándar explícito. Si ya está en la lista oculta, la hace visible. Guarda una copia antes de modificar la lista; un archivo malformado se deja intacto.

Compilar con Java 21 y `./build.ps1`, usando las bibliotecas de la instalación local de Minecraft. El JAR se genera en build. Las pruebas de src/test/java usan las clases NBT reales de Minecraft para comprobar instalaciones nuevas, actualizaciones, duplicados, entradas ocultas y archivos dañados.

`publicar/Integrar-Servidor.ps1` copia los cinco paquetes a una carpeta nueva, añade el JAR y actualiza sus rutas y huellas en tiers.json. Ejecutarlo con PowerShell 7. No incluye servers.dat en los paquetes: así, los instaladores existentes nunca sobrescriben las listas personales.
