# Pale Garden Remastered oficial en Ascension

Se usa pale-garden-remastered-v1.3.4.jar oficial, Modrinth o1GhaHBR, sin modificar. SHA512 c207a58175220ed70f1f3c9876d7b8d848d3aee636a0f72f9e8c0f2d2d293f8f43f2a28fdcba826a4030bb91bbf0d0f8fc54e17536796a9ffb610dcd91e13e3e.

El adaptador lee el JAR instalado. Genera una caché local con los mismos datos del autor, cambiando únicamente referencias al backport y sintaxis de componentes/atributos de 1.21.1. Activa las definiciones originales de árboles de su overlay. No hay funciones de ambientación o variantes recreadas en el adaptador.

Backport: Another Pale Garden Backport, YoungEagle12, Modrinth 92Kv38mV/hl0vh044, licencia MIT. Se corrigen dos archivos de tags vacíos, se declara TerraBlender y se registra en servidor el proveedor original de atributos (el backport solo lo registraba en cliente). El archivo corregido es palegardenbackport-1.0.0-ascension.1.jar. TerraBlender 4.1.0.8.

Compilar adaptador: compat/build.ps1. Integrar las cinco gamas con Integrar-Pack.ps1, usando una carpeta de salida nueva. overrides contiene tags, config de FallingTree y asignaciones de eras, no una recreación de Remastered.

Las pruebas aisladas y la antigua recreación descartada no forman parte de esta entrega. No utilizar Build-Compatibility.ps1 del experimento antiguo.
