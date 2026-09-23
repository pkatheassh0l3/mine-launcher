# Cómo publicar (solo para el autor)

Todo sale de tu perfil de Modrinth:
`C:\Users\pKa\AppData\Roaming\ModrinthApp\profiles\NeoForge 1.21.1`
(cambia la ruta en `publicar/perfil-origen.txt` si hace falta).

## Primera vez

1. En GitHub Desktop: **File → Add local repository** → elige esta carpeta (`mine-launcher`).
   Si te dice que no es un repositorio, pulsa **create a repository**.
2. Haz **Commit** y luego **Publish repository**, con el nombre `mine-launcher` y **sin** marcar "Keep this code private".
   El repo tiene que ser público para que el instalador pueda descargar las versiones.
3. Doble clic en `publicar/Publicar-Actualizacion.bat` → versión `1.0.0`.
4. Se abren la carpeta `publicar/salida/v1.0.0` y la página de GitHub para crear el release.
   Arrastra **los 4 archivos** a *Attach binaries* y pulsa **Publish release**.

Enlace para repartir:
https://github.com/pkatheassh0l3/mine-launcher/releases/latest/download/Instalar-TFC-Create.bat

## Sacar una actualización

1. Cambia tu perfil (mods, configs…) y comprueba que el juego arranca.
2. Ejecuta `publicar/Publicar-Actualizacion.bat` con un número **mayor** (1.0.1, 1.1.0…).
3. Sube los 4 archivos al release nuevo, igual que la primera vez.
4. En GitHub Desktop, haz Commit + Push (se guarda `ultima-version.txt`).

Truco: con [GitHub CLI](https://cli.github.com) instalado y `gh auth login` hecho, el paso 3 se hace solo.

## Qué hay en `publicar/`

| Archivo | Para qué sirve |
|---|---|
| `Publicar-Actualizacion.bat` | Genera las 3 gamas y el instalador, y crea el release |
| `plantilla-instalador.bat` | Base del instalador (el script de publicación le pone el repo) |
| `tiers.json` | Mods de rendimiento y ajustes de cada gama |
| `repo.txt` / `perfil-origen.txt` | Tu repositorio y la ruta de tu perfil |

## Notas

- Los mods de rendimiento (Sodium, Iris, Distant Horizons…) van en `tiers.json`, no en tu perfil. Si también los tienes en el perfil, se ignoran.
- Los mods que no están en Modrinth (Millénaire, FTB…) van metidos dentro del pack.
- Al actualizar, las configs de la carpeta `config` de los jugadores se sobrescriben, pero su `options.txt` no se toca.
