# Ascension — Minecraft 1.21.1 / NeoForge

Cinco versiones con los mismos mods de contenido, nueve eras, 126 misiones personalizadas y 16 hitos obligatorios. Incluyen las mejoras QoL, la retirada de las flechas de inventario y del antiguo libro de Millénaire.

| Versión | RAM para el juego | Visión | Shaders |
|---|---:|---:|---|
| PC patata | 4096 MB | 4 chunks | Sin shaders; texturas F8thful 8x8 |
| Baja | 4096 MB | 6 chunks | Desactivados; sin Iris |
| Intermedia 8 GB | 4096 MB | 8 chunks | Sin shaders; detalle intermedio |
| Media | 6144 MB | 10 chunks | Complementary Reimagined r5.5.1 LOW |
| Alta / RTX 3060 | 8192 MB | 14 chunks | Complementary Reimagined r5.5.1 HIGH |

## Instalar

Abre `Instalar-TFC-Create.exe` desde la carpeta de la versión preparada en `publicar/salida`, junto a `versiones.json` y los cinco `.mrpack`. Selecciona una gama y confirma la importación en Modrinth App o Migurinth. El instalador intenta aplicar la memoria automáticamente; si el launcher no lo permite, te indicará cómo ajustarla.

También puedes importar un `.mrpack` directamente y asignar la RAM de la tabla manualmente. Se necesita Java 21. La versión ligera conserva todos los mods de contenido y no garantiza fluidez en cualquier ordenador.

El instalador usa los paquetes locales si encuentra `versiones.json` junto al ejecutable; si no, consulta la última release de GitHub. Los nombres `TFC-Create_baja/media/alta.mrpack` y el nombre del ejecutable se mantienen por compatibilidad con la distribución.

## Partidas y actualizaciones

Esta edición se instala en perfiles nuevos **Ascension**. El antiguo TFC Create usa otro conjunto de mods: sus perfiles y mundos no se actualizan automáticamente a Ascension. No copies un mundo de TerraFirmaCraft a esta edición sin una comprobación específica de compatibilidad.

Las posteriores actualizaciones de los perfiles Ascension conservan mundos y ajustes personales. Las cinco gamas pueden usar el mismo servidor preparado para Ascension.

## Estado

Paquetes probados a 1080p en Ryzen 5 7600, RTX 3060 y 32 GB de RAM, con 4/6/8 GB asignados. No se incluyen partidas ni datos personales. La actualización está preparada localmente; debe publicarse una release para distribuirla desde GitHub.

Para preparar o publicar versiones: [PUBLICAR.md](PUBLICAR.md).

## Actualización 2026.10.1 — instalador para amigos

La distribución actual tiene cinco opciones: PC patata, Baja, Intermedia, Media y Alta. Todas incluyen Absolute Order ascension.12. PC patata conserva los mods de contenido y añade F8thful 8x8, visión de 4 chunks, simulación de 4, sin shaders y 4 GB asignados. Descarga las texturas directamente desde Modrinth y las activa en perfiles nuevos.

La carpeta lista para compartir está en `publicar/salida/v2026.10.1`: conserva el ejecutable, versiones.json y los cinco .mrpack juntos. Se ha probado la instalación y actualización con perfiles temporales, conservando mundos y ajustes; no es una prueba de FPS en hardware de gama baja. La release remota no se ha publicado.

