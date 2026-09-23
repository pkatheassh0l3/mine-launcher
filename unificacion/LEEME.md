# Unificación de objetos

Todo lo que hace que los mods "parezcan uno solo" sale de aquí y se empaqueta en `publicar/unificacion.zip`
(Sincronizar-Perfil.bat lo copia a tu perfil).

| Qué | Dónde (dentro del perfil) | Cómo |
|---|---|---|
| Objetos duplicados → uno solo (TFC primero) | `config/almostunified/` | Almost Unified: cambia recetas, botín y oculta los duplicados en JEI |
| Metales de Create/vanilla → TFC | `config/almostunified/unification/materials.json` | lingotes y pepitas |
| Recetas nuevas que conectan mods | `kubejs/data/unificado/recipe/` | JSON normales |
| Recetas quitadas (cuchillos duplicados) | `kubejs/data/<mod>/recipe/` | condición `neoforge:false` |
| Comida de otros mods con caducidad y nutrición TFC | `kubejs/data/unificado/tfc/food/` | 238 definiciones |
| Aldeanos de Millénaire usan comida y metal de TFC | `millenaire-custom/tfc_unificado/` | sub-mod de Millénaire |

Los `.py` son los generadores (necesitan los jars extraídos; los usa Claude para regenerarlo).
Para un cambio pequeño puedes editar directamente los archivos del perfil.
