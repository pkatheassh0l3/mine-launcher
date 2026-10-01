Actualización local 2026.9.30: añadida cuarta gama Intermedia 8 GB (4096 MB, 8 chunks, sin shaders). Instalador recompilado y cuatro gamas comprobadas. No publicada en GitHub.

# Estado actualizado — 29/09/2026

La edición vigente es Ascension, con los tres paquetes probados de 4/6/8 GB, nueve eras y misiones con bloqueo. El flujo actual utiliza publicar/paquetes-base y Preparar-Versiones.ps1. Consulta README.md y PUBLICAR.md.

El instalador se ha recompilado y la versión 2026.9.29 está preparada localmente. No se ha publicado en GitHub. Los perfiles antiguos TFC se mantienen separados para no migrar sus mundos a un conjunto distinto de mods.

Los apartados siguientes son el estado histórico del 27/09/2026, no tareas pendientes ni instrucciones del flujo actual. intro-mod y los generadores antiguos se conservan sin modificarlos.

---
# Estado del proyecto TFC Create (para retomar en otra conversación)

> Pega o enlaza este archivo al empezar una conversación nueva. Resume qué hay hecho, dónde está cada cosa y qué falta.

## 1. Qué es

Modpack **TFC Create** para Minecraft **1.21.1 / NeoForge 21.1.250**. Incluye:

- TerraFirmaCraft 4.2.10
- Create 6.0.10, con Aeronautics/Sable y Steam 'n' Rails
- Millénaire 9.0.2
- Farmer's Delight y sus Delights (Veggies, Ramadan, More)
- Naturalist, Spawn, Critters and Companions, Unusual Fish
- FTB Chunks, JEI, KubeJS 2101.7.2 y Almost Unified 1.4.2

Se distribuye en **3 gamas** con un instalador visual (.exe) que lo instala en **Modrinth App** o **Migurinth**. También se actualiza solo desde GitHub.

| Ruta | Qué es |
|---|---|
| Repo local | `C:\Users\pKa\Documents\GitHub\mine-launcher` (GitHub: `pkatheassh0l3/mine-launcher`, público, rama `main`) |
| Perfil origen | `C:\Users\pKa\AppData\Roaming\ModrinthApp\profiles\NeoForge 1.21.1` (todo sale de aquí) |
| Perfil de prueba instalado | `...\profiles\TFC-Create_alta` |
| Helper del auto-update | `C:\Users\pKa\AppData\Local\tfc-create-pack\TFC-Create.exe` |
| Token de GitHub | cifrado con DPAPI en `%APPDATA%\tfc-create-pack\github-token.dat` (fuera del repo) |

## 2. Las 3 gamas (qué PC es cada una)

| Gama | Para quién | Qué incluye |
|---|---|---|
| **Baja**: "que funcione" | Portátil u oficina con gráficos integrados, ~8 GB de RAM. Va justo: tirones al explorar y en aldeas o fábricas grandes | 4 GB de RAM, render 6 / simulación 5, gráficos rápidos, 60 FPS, shaders MakeUp incluidos pero apagados; configs de Create, Flywheel, TFC, Sable y FTB rebajadas |
| **Media**: **recomendada** | PC gaming de hace unos años (GTX 1050–1660 / RX 570–580), 16 GB | 8 GB, render 10 / simulación 6, gráficos detallados, 120 FPS + VSync, 3D Skin Layers, Complementary Reimagined opcional |
| **Alta**: experiencia completa | RTX 3060 o superior, 32 GB | 10 GB, render 16 / simulación 8, **Distant Horizons**, shaders **activados** (Complementary Reimagined; también Unbound, BSL, Photon y MakeUp), Particle Rain |

Todas llevan: Sodium (+Extra, Reese's), Lithium, FerriteCore, ModernFix, ImmediatelyFast, EntityCulling, MoreCulling, BadOptimizations, Clumps, Dynamic FPS e Iris 1.8.14-beta.1. Además: idioma es_es y `syncChunkWrites=false`. El instalador pone la RAM (máximo el 60 % de la del PC, mínimo 3 GB) y los argumentos G1GC.

Al actualizar se conservan los ajustes de cada jugador: `options.txt`, iris, sodium, Distant Horizons y JEI. El resto de configs se renueva.

## 3. Estructura del repo

- `publicar/Publicar-Actualizacion.bat`: genera las 3 gamas (.mrpack), crea la release en GitHub y sube los 4 archivos solo (3 packs + `Instalar-TFC-Create.exe`).
- `publicar/tiers.json`: catálogo de mods extra y, por gama, los extras, las opciones de `options.txt`, los reemplazos en configs y los archivos completos. `includeFolders` indica qué carpetas del perfil se copian: `config`, `defaultconfigs`, `kubejs`, `millenaire-custom`.
- `publicar/Sincronizar-Perfil.bat`: vuelca `mods-perfil.json` y `unificacion.zip` en el perfil origen.
- `publicar/unificacion.zip`: todo lo de unificación, listo para el perfil.
- `publicar/repo.txt` y `publicar/perfil-origen.txt`.
- `instalador/`: `app.ps1` (interfaz WPF en PowerShell) + `main.go` (lanzador Go que incrusta `app.ps1`, `logo.png` e `instance_icon.png`) + `build.sh`.
- `unificacion/`: generadores Python. Hay que ejecutarlos en este orden: `kjs_gen.py` → `millenaire_gen.py` → `guia_gen.py`. Necesitan los jars extraídos.
- `README.md` (jugadores), `PUBLICAR.md` (autor), `GUIA-MILLENAIRE.md` (guía web) y este archivo.
- `intro-mod/`: mod propio **a medio hacer** (ver sección 7).

## 4. El instalador (.exe): cómo funciona

1. Detecta Modrinth App o Migurinth. Si no hay ninguno, abre la página de descarga.
2. Detecta el hardware y recomienda una gama; el jugador la confirma.
3. Descarga el .mrpack de la última release e importa el perfil. Si ya existe uno de la misma gama, **lo actualiza** en vez de duplicarlo.
4. Escribe en la base de datos del launcher (`app.db`, SQLite) la RAM, los argumentos de Java, el hook de auto-update y el icono. Soporta el esquema viejo (`profiles`) y el nuevo (`instances` + `instance_launch_overrides` + `instance_icon_configs`).
5. Hook pre-launch `TFC-Create.exe -auto`: cada vez que se pulsa Jugar, comprueba si hay versión nueva.

## 5. Unificación (objetos no repetidos)

Prioridad: **TFC > Farmer's Delight > Delights > Millénaire > Naturalist > Critters > Spawn > More Delight > Create > Minecraft**.

- **Almost Unified**: 61 grupos de objetos equivalentes (tags `unificado:*`). Metales: lingotes y pepitas a TFC, y `c:ingots/iron` incluye el hierro forjado de TFC. Las planchas **no** se unifican porque generaban recetas duplicadas. Oculta los duplicados en JEI y unifica el botín.
- **KubeJS**:
  - 5 recetas puente (aceite y masa de Millénaire, algodón a hilo, pieles).
  - Quita los cuchillos duplicados de FD y More Delight y los oculta en JEI.
  - 238 definiciones de comida TFC (caducidad y nutrición) para otros mods. **Spawn está desactivado** porque causaba el crash del inventario creativo ("same item stack twice [Trozo de Atún]").
- **Millénaire** (`millenaire-custom/tfc_unificado/`):
  - tiendas, objetos de comercio, aldeanos y trabajos usan objetos de TFC y FD;
  - **115 aldeas** con etiquetas de bioma TFC (`kubejs/data/unificado/tags/worldgen/biome/tfc_*.json`). Antes no aparecía ninguna aldea en TFC.

## 6. Guía de Millénaire (estado actual)

- `kubejs/server_scripts/guia_millenaire.js`: libro escrito de 15 páginas que se da al entrar por primera vez, y comando `/guia`. **Al usuario no le gusta** y debe sustituirse (ver sección 7).
- `GUIA-MILLENAIRE.md`: guía web en GitHub, enlazada desde el README. Esta se mantiene.

## 7. Pendiente (por orden)

1. **Intro cinemática + Códice** (mod propio `tfccreate_intro`, solo cliente, NeoForge 1.21.1).
   - **Hecho:** `TfcIntroMod.java`. Registra la tecla **Y** ("Abrir el Códice", categoría "TFC Create"; Y está libre en los controles). Detecta la primera entrada a cada mundo (lista en `config/tfccreate_intro-vistos.txt`) y abre la intro 2 s después.
   - **Falta `IntroScreen`**, estilo intro de juego de rol:
     - fondo del mundo desenfocado y oscurecido, franjas negras de cine, partículas flotando;
     - un objeto grande flotando por diapositiva, título grande dorado, subtítulo y texto que aparece línea a línea;
     - puntos de progreso; clic / Espacio / → para avanzar, ← para volver, Esc para saltar.
     - Diapositivas: *TFC Create — Crónica de una tierra indómita* · *La Tierra Firme* (clima, estaciones, comida que caduca) · *Los Pueblos Antiguos* (7 culturas de Millénaire) · *La Era de las Máquinas* (Create, trenes, naves) · *Tu leyenda empieza aquí* ("pulsa [Y] para abrir el Códice").
   - **Falta `CodexScreen`**:
     - barra lateral de cuero con capítulos (icono + nombre) y página de pergamino a la derecha, con paginación automática;
     - flechas y rueda del ratón para pasar página, sonido de página y botón "Ver introducción".
     - Capítulos: Primeros pasos (piedras, tallado, fuego, cerámica, JEI R/U, Ponder W) · Clima y comida · Metalurgia · Create · Naves y vuelo · Aldeas (Millénaire: V, M, G, comercio, reputación, jefe, misiones, aldea propia) · Fauna · Teclas.
   - **Textos** en `assets/tfccreate_intro/lang/es_es.json`, con claves `tfcintro.intro.N.*` y `tfcintro.ch.<id>.sN.h/.b/.icon`, para poder editarlos sin recompilar. Texturas de pergamino y cuero generadas con Python.
   - **Cómo compilar:** la nube no tiene acceso a Maven. Se compila con `javac --release 21` contra los jars de `%APPDATA%\ModrinthApp\meta\libraries`, en este orden: `neoforge-21.1.250-client.jar` primero, luego `client-1.21.1-...-srg.jar`, `neoforge-...-universal.jar`, bus, loader, `mergetool-api` (clase `Dist`), brigadier, DFU, authlib, logging, joml, slf4j y guava. Hace falta `META-INF/neoforge.mods.toml`. El jar va a `mods/` del perfil origen y el publicador lo mete en el pack.
   - Al terminar: **quitar el libro** (vaciar `guia_millenaire.js` y quitar `guia_gen.py` del flujo) y actualizar el README con la tecla Y.
2. **README e instalador**: sustituir la tabla de RAM por la descripción de gamas de la sección 2 (quién es cada una; la media es la recomendada).
3. **Probar en el juego** (mundo nuevo):
   - el inventario creativo no crashea;
   - salen aldeas (V);
   - los aldeanos comercian con objetos de TFC;
   - la intro sale la primera vez y Y abre el Códice.
4. **Publicar**: Commit + Push en GitHub Desktop, luego `Publicar-Actualizacion.bat` con una versión nueva. Así se actualizan TFC-Create_alta y los jugadores.

## 8. Notas técnicas útiles

- Desde la nube, la API de Modrinth y GitHub están bloqueadas. Se usó el navegador integrado del PC (fetch en JS) para consultar Modrinth.
- Si el comando de shell en el PC no está disponible, los archivos se escriben con commit de archivos. Los archivos a más de 7 carpetas de profundidad no se pueden subir desde el PC.
- No hay permiso para borrar archivos en el PC: para "quitar" algo se sobrescribe.
- Las teclas ocupadas en el perfil incluyen V, M, G (Millénaire), R/U (JEI) y W (Ponder de Create). Libres: **Y**, entre otras.
