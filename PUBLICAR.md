# Preparar y publicar las versiones actuales

El publicador utiliza **los cinco paquetes preparados y comprobados**, guardados en `publicar/paquetes-base`. No vuelve a mezclar mods del antiguo perfil TFC ni añade el catálogo antiguo de shaders.

## Preparar sin publicar

Ejecuta en PowerShell:

```powershell
./publicar/Preparar-Versiones.ps1 -Version 2026.9.30
```

Verifica las huellas de los paquetes y del instalador, añade los identificadores del actualizador y genera `publicar/salida/v2026.9.30`. Si esa carpeta existe, usa otra versión o `-OutputDirectory` para una prueba; no se sobrescriben versiones anteriores.

La carpeta contiene los cinco `.mrpack`, el instalador, `versiones.json` para instalar desde archivos locales y `SHA256SUMS.txt`. Conserva estos archivos juntos para usar el instalador sin una release. Minecraft y sus bibliotecas pueden necesitar conexión para descargarse.

## Publicar

`publicar/Publicar-Actualizacion.bat` prepara una versión nueva y pide escribir PUBLICAR antes de subirla con GitHub CLI. Valida los cinco paquetes y publica primero un borrador; solo activa la actualización después de comprobar todos los archivos subidos. Permite reutilizar una carpeta ya preparada y reintentar un borrador interrumpido. Consulta ACTUALIZACIONES.md para el canal de actualización de los instaladores existentes. También puedes subir manualmente los archivos preparados a una release del repositorio indicado en `publicar/repo.txt`.

No cambies los nombres `TFC-Create_baja.mrpack`, `TFC-Create_intermedia.mrpack`, `TFC-Create_media.mrpack`, `TFC-Create_alta.mrpack` ni `Instalar-TFC-Create.exe`: el instalador los busca con esos nombres. `ultima-version.txt` solo cambia después de una publicación correcta.

## Cambiar el contenido en futuras versiones

1. Crea y prueba los cinco paquetes de Minecraft.
2. Sustituye los `.mrpack` de `publicar/paquetes-base` y actualiza sus rutas y SHA256 en `tiers.json`.
3. Mantén los cinco perfiles compatibles con el servidor. `contentRevision` identifica la familia compatible de partidas; no lo cambies para una actualización rutinaria.
4. Si cambia la interfaz, recompila `instalador/build.ps1` con Go y actualiza `installerSha256` en `tiers.json` con la huella del nuevo ejecutable. El script usa `github.com/akavel/rsrc` fijado a v0.10.2 para conservar el icono y el manifiesto.
5. Prepara y comprueba la salida antes de publicarla.

Los paquetes base están ignorados por Git debido a su tamaño: guarda una copia externa o consérvalos como archivos de release. Un clon nuevo necesita recuperarlos antes de preparar una versión.

El sincronizador anterior ha quedado desactivado para evitar que restaure recetas, mods y el libro antiguos. Los generadores de `unificacion/`, `mods-perfil.json` y `unificacion.zip` se conservan como material histórico y no forman parte del flujo actual.

## Edición 2026.10.1

Hay cinco paquetes base; el quinto es `TFC-Create_patata.mrpack`. `Preparar-Versiones.ps1` ya utiliza 2026.10.1 por defecto y gestiona también las descargas declaradas en el índice. Los hashes de tiers.json corresponden al instalador y paquetes actualizados con Absolute Order ascension.12.

`Integrar-AbsoluteOrder-Patata.ps1 -BaseDirectory RUTA -AbsoluteOrderJar RUTA` prepara copias nuevas a partir de los paquetes base en un directorio de trabajo vacío. No sobrescribe salidas existentes. La versión fijada de F8thful está descrita en f8thful-version.json y se descarga desde Modrinth; no se incorpora su ZIP como override.


