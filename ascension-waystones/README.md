# Ascension Rutas 1.0.0

Complemento de cliente NeoForge 1.21.1 para Waystones 21.1.46. Sustituye la selección normal por un mapa interactivo y una lista con buscador. Filtra por dimensión, permite zoom, arrastre, selección y confirmación. Usa el paquete oficial SelectWaystoneMessage: el servidor sigue comprobando destinos y requisitos. El botón Menú clásico permite acceder a las funciones de gestión originales.

El selector dibuja terreno de los chunks actualmente cargados en la dimensión del jugador; en las demás zonas muestra la cuadrícula y los marcadores. Xaero conserva independientemente su mapa explorado y la integración nativa de puntos Waystones. La búsqueda ignora mayúsculas y acentos.

Compilar con JDK 21 mediante build.ps1. Acepta -Libraries (bibliotecas de Modrinth) y -ModsDirectory (carpeta con Waystones y Balm). No instalar en el servidor.

Configuración distribuida: públicas por defecto, sin generación natural. Receta y era de cobre se distribuyen en kubejs. No contiene viajes gratuitos ni comandos de operador.

Verificación: compilado; cliente real iniciado con Waystones, Minimap y World Map; captura de pantalla real y búsqueda por nombre comprobadas. Configuración y receta verificadas mediante GameTests con KubeJS. Pendiente viaje real multijugador y validación del conjunto completo del servidor.
