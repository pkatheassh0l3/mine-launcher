# Hearthbound 1.6.3 — Resurrección de habitantes

En la pestaña Habitantes de la aldea, un residente fallecido muestra Revivir. La segunda pulsación confirma el pago de **10 monedas de cobre y 2 de plata**. Hearthbound acepta monedas equivalentes y devuelve cambio.

La resurrección conserva el registro del residente: identidad, nombre, oficio, apariencia, hogar, historia y relaciones. Crea una nueva entidad viva con la rutina habitual del mod, sin copiar inventarios de cadáveres ni sus objetos caídos. También sirve para fallecidos registrados en partidas existentes. La reaparición automática sigue dependiendo de respawnDays y no se desactiva.

El servidor valida la cercanía, la aldea, la era de comercio, el estado del residente y el saldo. Solo cobra si puede crear la entidad. Repetir la petición no duplica habitantes ni cobra otra vez.

Configuración de servidor, sección [settlers] de hearthbound-server.toml:

- paidRevival = true
- revivalCopper = 10
- revivalSilver = 2

Instalar Hearthbound 1.6.3 tanto en clientes como en el servidor, sustituyendo 1.6.2 y conservando los demás mods. Para actualizar, cerrar Minecraft o detener el servidor y guardar una copia del JAR anterior. No dejar las dos versiones juntas en mods.

Compilar con Java 21: gradlew.bat build. Pruebas de integración: gradlew.bat runGameTestServer. Superadas las seis pruebas, incluidas pago/cambio, persistencia de identidad e historia, petición repetida, saldo insuficiente, identidad inexistente, distancia y cancelación de la aparición.

El servidor remoto del usuario no se ha modificado: todavía no se ha facilitado su ubicación.
