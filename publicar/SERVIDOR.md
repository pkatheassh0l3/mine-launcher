# Entregas del servidor Ascension

Por indicación expresa del usuario del 2 de octubre de 2026, incluir siempre YetAnotherConfigLib (modId yet_another_config_lib_v3) en los paquetes de servidor. El usuario ya lo instaló manualmente en su servidor; no hace falta volver a hacerlo allí.

Versión usada con Minecraft 1.21.1 y NeoForge: yet_another_config_lib_v3-3.8.2+1.21.1-neoforge.jar. Debe mantenerse compatible con la versión del cliente. No excluir YACL como biblioteca exclusiva de cliente aunque sus metadatos indiquen que su instalación en servidor es opcional.

Antes de entregar una carpeta o ZIP de servidor, ejecutar Test-Servidor.ps1 -ModsDirectory <carpeta mods>. La comprobación rechaza una entrega sin YACL. Con el contenido de Ascension 2026.10.2.8 más esta corrección, son 52 JAR.

Esta corrección se aplica a las próximas entregas. No se han sustituido los archivos ya publicados de 2026.10.2.8.

## SkinRestorer

Por indicación expresa del usuario del 2 de octubre de 2026, añadir también SkinRestorer en futuras entregas del servidor. Antes de empaquetarlo, verificar el proyecto exacto, una versión compatible con Minecraft 1.21.1 / NeoForge y sus dependencias. No sustituirlo por un plugin Bukkit/Spigot incompatible con el servidor NeoForge. Esta indicación no implica que ya esté instalado o incluido en la versión publicada.


Actualización 2026.10.3: la base de servidor ahora incluye Hearthbound 1.6.5, YACL y SkinRestorer 2.11.0+1.21-neoforge (Modrinth QFkb2iq2; sin dependencias externas nuevas). Son 53 JAR. Test-Servidor exige ambos mods para evitar omisiones futuras.

Entrega final 2026.10.3: 57 JAR. Incluye el JAR oficial intacto de Pale Garden Remastered 1.3.4, Another Pale Garden Backport con corrección de tags/dependencia, TerraBlender 4.1.0.8 y compatibilidad de sintaxis/registros para 1.21.1. FallingTree reconoce troncos/hojas y árboles altos. La versión anterior de 53 JAR era preparación intermedia, no la entrega final.
