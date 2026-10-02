# Actualizaciones para los instaladores existentes

Los amigos no necesitan reemplazar su instalador para recibir cambios del contenido del pack. El acceso directo **Ascension** consulta la última release pública de `pkatheassh0l3/mine-launcher`. Los perfiles instalados con el asistente también disponen de una comprobación antes de arrancar Minecraft, siempre que el jugador no haya sustituido ese comando de inicio.

**La carpeta del ZIP funciona como una copia local:** si hay un `versiones.json` al lado del ejecutable, ese instalador usa los paquetes de esa carpeta. Para buscar novedades, abrir el acceso directo Ascension que creó la instalación. También puede ejecutarse el mismo EXE desde una carpeta sin ese manifiesto. No hace falta enviar otro instalador.

## Publicar una actualización

1. Preparar y comprobar los cinco paquetes base. Actualizar sus rutas y SHA256 en `publicar/tiers.json`.
2. Actualizar `publicar/notas-version.md`.
3. Ejecutar `publicar/Publicar-Actualizacion.bat -Version 2026.10.2.2` (con un número superior al publicado).
4. Escribir PUBLICAR. Se necesita GitHub CLI con sesión iniciada y permisos sobre el repositorio.

Si `gh` no está en PATH, pasar `-GitHubCli RUTA_COMPLETA_A_GH.EXE`. El script puede reanudar una carpeta preparada o un borrador interrumpido; no reemplaza una release ya pública. Se conservan los nombres de los cinco paquetes para que los instaladores existentes los encuentren.

Para comprobar sin publicar:

```powershell
./publicar/Publicar-Versiones.ps1 -Version 2026.10.2.1 -ValidateOnly
```

Para usar otra carpeta preparada, añadir `-PreparedDirectory RUTA`. `-Publish` omite la pregunta interactiva y debe usarse solamente cuando se quiera publicar.

El publicador verifica huellas, marcadores y dependencias de los cinco paquetes; sube primero un borrador y comprueba tamaño y SHA256 de los archivos recibidos por GitHub. Solo entonces lo publica como la versión más reciente y comprueba el canal público del instalador.

Las modificaciones locales no se distribuyen hasta publicar una versión nueva. Las partidas y las preferencias personales se conservan. Los perfiles antiguos de TFC no se migran a Ascension automáticamente. Cambiar los mods y configuraciones no requiere recompilar el instalador; añadir opciones nuevas a su interfaz sí requiere un ejecutable nuevo.

Desde 2026.10.2.2 las cinco gamas incluyen el componente de cliente server-defaults, que añade rails-needs.tun.ply.gg al iniciar Minecraft conservando servidores personales. La implementación y las instrucciones de compilación están en server-defaults/README.md. No añadir servers.dat como override: sobrescribiría la lista del jugador con los instaladores existentes.
