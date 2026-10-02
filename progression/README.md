# Progresión de mods de Ascension

Versión del pack: 2026.10.2.4. Conserva las nueve eras y las misiones existentes. Las definiciones de `generated/kubejs/data/ascension/ascension/ages` incluyen 3356 objetos registrados de mods, asignados a una única era efectiva. Las vistas previas (`showcase`) combinan Minecraft y mods dentro del límite de 16 iconos de Ascension.

`baseline-data` contiene las reglas y misiones de partida; `registry.json` es el registro exportado del pack, y `item-eras.csv` permite revisar las asignaciones. `Build-Progression.ps1` expande las reglas existentes y completa equipo menor. Mantiene la era más restrictiva cuando antes coincidían varias reglas. Los objetos creativos no reciben recetas ni se añaden como objetivos.

Para otra composición de mods, actualizar primero el registro exportado y revisar las reglas de cada mod nuevo: asignar una era inicial por defecto no sustituye esa revisión. No regenerar con un registro antiguo.

Validación con `Test-Progression.ps1`: IDs reales, cobertura y unicidad de objetos, vistas previas en las nueve eras, objetivos accesibles y dependencias sin ciclos. El test necesita el JAR actual de Ascension como `ascension.jar` junto al script para leer sus misiones originales; puede extraerse del pack. Ese JAR no se guarda en el repositorio. Se comprobaron también las 3356 reglas con la clase Matcher del JAR real, fuera del juego.

Integrar las nueve definiciones en copias de los paquetes mediante `publicar/Integrar-Progresion.ps1` con PowerShell 7 y publicar una versión nueva. El perfil local se respaldó antes de actualizarlo. No se modifican archivos de progreso de jugadores.

En multijugador, el servidor es la autoridad. Debe recibir el parche de servidor que contiene estos mismos nueve JSON. No se ha aplicado todavía al servidor remoto: falta conocer su ubicación. El parche incluye instrucciones para instalarlo con el servidor detenido y una copia de seguridad previa.
