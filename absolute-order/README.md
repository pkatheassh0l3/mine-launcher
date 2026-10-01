# Absolute Order — Ascension 2.0.0-ascension.12

Minecraft 1.21.1 / NeoForge. La misma versión debe instalarse en el servidor y en todos los clientes. Reinicia Minecraft tras sustituir el JAR; reinicia el servidor después de actualizarlo.

## Pestañas

- **Por defecto**: catálogo automático de supervivencia por funciones y materiales, mixto entre mods. Conserva las categorías de madera, piedra y drops, filtros por filas, opciones de cofre completo y orden de disponibilidad estimada. Estos presets no se pueden sobrescribir, renombrar ni borrar desde la pestaña.
- **Comunidad**: biblioteca del mundo/servidor. Al editar manualmente un filtro, aparece una solicitud de nombre: al publicarlo, se crea una entrada vinculada a ese cofre. Las ediciones posteriores se publican automáticamente, sin duplicados. También sigue disponible el botón **Publicar cofre actual**. Cada entrada muestra su autor. Todos pueden aplicarla a un almacenamiento con el mismo número de ranuras y columnas. El botón de actualización vuelve a pedir el catálogo.

Solo el autor o un administrador con nivel de permisos 2 puede borrar un preset de comunidad. La papelera requiere dos pulsaciones para confirmar. Una publicación queda vinculada a su cofre de origen; sus posteriores cambios actualizan esa misma entrada y conservan al creador. Editar una copia en otro cofre no modifica el preset original. La identidad del autor la obtiene el servidor, no el cliente. Límite: 40 publicaciones por jugador y 200 por mundo. Los filtros compartidos admiten paquetes de hasta 29.000 bytes; un preset demasiado grande produce un aviso.

Los presets de comunidad se almacenan en `data/absoluteorder/community.json` dentro del mundo del servidor. Las divisiones de cada almacenamiento se guardan en `data/absoluteorder/chests`. El guardado usa sustitución de archivos para evitar escrituras parciales. Incluye esa carpeta en las copias del mundo.

Aplicar o editar una configuración sincroniza las divisiones y filtros de ese cofre con sus usuarios. Publicar un preset lo añade a la biblioteca; no sobrescribe los demás cofres. La biblioteca está separada por mundo/servidor y funciona también en partidas locales. Los antiguos archivos personales permanecen en disco y no se publican automáticamente.

## Compatibilidad

Inventario del jugador desactivado. Almacenamientos estándar identificables por bloque o entidad y adaptador Sophisticated Core/Backpacks. Se excluyen ranuras de mejoras. Se exige coincidencia de geometría al aplicar una publicación para evitar recortar filas. Los contenedores sin identidad compatible muestran un aviso; no se garantiza soporte universal para máquinas, menús virtuales o redes de almacenamiento. Los cofres de Ender no se convierten en almacenamiento compartido entre jugadores.

La clasificación de supervivencia usa tipos, nombres y etiquetas, además de drops obtenidos de los mods del perfil. La prioridad de objetos es una estimación de acceso y dificultad, no una probabilidad medida ni una simulación de todas las recetas. La familia de una fila toma la prioridad de su objeto más accesible; sus variantes se ordenan de comunes a avanzadas.

## Verificación

- Compilación final correcta y **27 pruebas automatizadas** superadas.
- Servidor aislado con Sophisticated: catálogo sin duplicados, filas y filtros comprobados; 76 presets para 1.290 objetos admitidos.
- Prueba real del código de servidor con dos jugadores simulados: publicación, atribución al autor, aplicación por otro jugador, rechazo de borrado ajeno, borrado por autor y conservación de divisiones en disco.
- Pruebas de persistencia y aislamiento entre mundos, permisos y límites de paquetes.
- Se ha revisado una vista de componentes renderizada en Minecraft; no todas las pantallas ni una sesión con dos clientes humanos. El perfil completo no se ha validado en el entorno de desarrollo debido al error de transformación de KubeJS documentado en revisiones anteriores.

## Instalación y código

El perfil local recibe el JAR ascension.12 y una copia de seguridad del anterior en `absolute-order-backups`. El JAR de `outputs` sirve también para actualizar el servidor: debe reemplazar al Absolute Order anterior, sin dejar dos versiones activas. La instalación local no actualiza automáticamente un servidor remoto ni las distribuciones del launcher.

El código adaptado está en el ZIP entregado y en `mine-launcher/absolute-order`. Compila con Java 21 y `Compilar.ps1 -Perfil "ruta del perfil"`. Conserva la licencia original. Fuente inicial: https://github.com/marcsanz-dev/Absolute-Order-Mod, módulo multiloader-1.21.1.
## Publicación automática y filtro por jugador

La primera modificación de los filtros de un cofre sin publicación asociada solicita un nombre en una ventana dentro del inventario. Introduce el nombre y pulsa Publicar o Enter. Más tarde/Escape pospone la publicación: el cofre conserva sus cambios y vuelve a pedir nombre al reabrirlo o seguir editando. La solicitud pendiente se conserva incluso tras reiniciar el servidor.

Una vez nombrado, el preset mantiene su identificador, nombre y autor; cualquier usuario que edite ese cofre actualiza la misma publicación. Los cambios de filtros, opciones, orden de objetos y aspecto del cofre ya vinculado se guardan en ella. Aplicar otro preset a ese cofre también actualiza su publicación existente. Cargar un preset en un cofre sin publicación no crea entradas ni le vincula al autor del preset utilizado: al modificar esa copia manualmente se pide un nuevo nombre.

En Comunidad, el selector Usuario permite elegir Todos, cualquiera de los jugadores conectados o un autor con presets publicados aunque esté desconectado. Usa el botón de actualización para renovar la lista. Las opciones se identifican por UUID, y los resultados siguen respetando el tamaño y las columnas del almacenamiento. Si hay muchos usuarios, desplaza la lista del selector con la rueda del ratón.

Los presets anteriores que no tenían una asociación con el cofre se conservan. La primera edición de esos cofres requerirá dar nombre a su publicación vinculada. Se requiere ascension.8 tanto en servidor como en clientes por el nuevo protocolo del catálogo.

Validación de esta revisión: 25 pruebas automatizadas, incluida selección por autor y transporte de la lista de usuarios; servidor aislado con jugadores simulados verificando solicitud de nombre, publicación, actualización con el mismo identificador y protección del preset de origen. La interfaz no se ha revisado visualmente dentro de una sesión de juego.
## Tema Ascension / Hearthbound y JEI (ascension.8)

Los paneles, tarjetas, botones, pestañas, listas y ventanas compartidos utilizan fondos azul oscuro/pizarra, bordes discretos, texto claro y acentos dorados. La paleta se ha tomado de los valores de Theme de Ascension 1.0.0-votaciones y Ui de Hearthbound 1.6.2 instalados en el perfil, sin añadir una dependencia obligatoria de esos mods. Se mantiene el diseño funcional de los menús de Absolute Order.

JEI se oculta temporalmente mientras está abierto el editor de cofres (incluida selección/edición de filtros, dibujo, presets y solicitud de nombre). También se bloquea la captura de teclado/ratón y los atajos de JEI durante ese tiempo. Al cerrar el editor o cambiar de pantalla vuelve a aplicarse el estado habitual de JEI: no se escriben ni se alternan sus preferencias, y no se fuerza a mostrarlo si antes estaba oculto.

Integración opcional para NeoForge y JEI 19.51.0.418, comprobada con el JAR instalado. Las clases de integración se omiten cuando JEI no está presente. No se desinstala JEI ni se desactiva el cálculo de recetas.

Verificación: 27 pruebas automatizadas y cliente aislado con JEI real; comprobados aplicación de las integraciones, ocultación al editar, conservación de las preferencias y restauración al cambiar de pantalla. La apariencia de todas las pantallas dentro del modpack completo no se ha revisado visualmente. El código del cliente de validación no está incluido en el JAR de entrega.
## Rediseño visual (ascension.9)

Paneles con esquinas suaves, relieve y acento dorado; pestañas con indicador de selección, tarjetas para cada preset y estados de interacción más claros. Conserva el funcionamiento de presets y comunidad y la integración con JEI.

Verificación: 27 pruebas automatizadas y vista de componentes renderizada e inspeccionada en un cliente real de Minecraft. Esta vista utiliza ejemplos ficticios y no representa una biblioteca real del servidor. No se ha comprobado cada pantalla en el modpack completo.


## Apariencia de Ascension (ascension.10)

El tema actual reproduce los valores y la geometría de Ascension 1.0.0-votaciones instalado: panel D0121826, tarjetas D81A2233, hover E0222C40, bordes blancos translúcidos y dorado F5C451. Paneles con radio 5 y la misma sombra desplazada; tarjetas con radio 6 y marca lateral; pestañas redondeadas con relleno y borde de selección. Controles sin encogimiento al seleccionarlos y etiquetas sin sombra, con la fuente de Minecraft que utiliza Ascension. Los campos de búsqueda y publicación también usan este tema.

Se sustituyen los adornos y colores mixtos de Hearthbound de las revisiones anteriores. Se conserva la distribución necesaria para editar filtros junto al cofre; las funciones y contenidos de Absolute Order siguen siendo propios. No añade una dependencia obligatoria de Ascension. La revisión visual usa datos ficticios en un cliente aislado, no una partida completa del servidor.

## Editor de zonas unificado (ascension.11)

La barra del cofre tiene una entrada común para filtros y colores. Selecciona una zona y pulsa **Color de la zona** para colorear sus ranuras. Dentro de la edición del filtro, la paleta junto a Guardar permite elegir el color sin cambiar de editor. En ese contexto, el color es un borrador: se guarda junto al filtro al pulsar Guardar; Cancelar descarta ese color. Desde la vista de zonas, cerrar el selector aplica directamente el color a la selección. Los bordes y diseños previamente guardados se conservan.

Los iconos se dibujan con un conjunto uniforme de símbolos lineales. Los textos de botones, listas y paneles usan tamaño fijo; las etiquetas largas se recortan en lugar de encogerse, y los botones muestran el texto completo al pasar el ratón.

Verificado en cliente aislado: el color queda pendiente hasta guardar, afecta solo a las ranuras elegidas, conserva el filtro y se deshace junto a él. Las 27 pruebas existentes se mantienen. Vista de componentes revisada; no se ha realizado una sesión completa en el servidor remoto.

## Color obligatorio al terminar la zona (ascension.12)

El flujo actual sustituye al de la revisión anterior: selecciona la zona, define sus filtros, pulsa **Elegir color** y termina con **Confirmar** en el selector. Solo entonces se guardan el filtro y el color juntos. Cerrar el selector o pulsar Escape devuelve al filtro pendiente sin crear la zona. También pasan por este paso las opciones de guardar al salir y confirmar la expulsión de objetos. La publicación en comunidad ocurre después del guardado, conservando su solicitud de nombre habitual.
