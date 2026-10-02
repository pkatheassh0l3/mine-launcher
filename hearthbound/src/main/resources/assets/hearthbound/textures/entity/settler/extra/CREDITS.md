# Créditos y licencias de terceros

El código de Hearthbound, los edificios procedurales, los textos y las texturas generadas por `tools/generate_textures.py` son originales y están bajo licencia MIT (ver `LICENSE`).

Algunos recursos proceden de proyectos abiertos. Cada uno conserva su licencia original:

## Edificios (schematics)

`src/main/resources/data/hearthbound/hearthbound/structures/rs/`: 212 plantillas de edificios de aldea de **Repurposed Structures**, de TelepathicGrunt.

- Fuente: https://github.com/TelepathicGrunt/RepurposedStructures (rama `1.21-Arch`, Minecraft 1.21.1).
- Licencia: **GNU LGPL v3.0**. La copia de la licencia está en `LICENSE-LGPL-3.0.txt`, en esa misma carpeta.
- Los archivos se incluyen **sin modificar**. Hearthbound solo los lee, los gira y los coloca en tiempo de ejecución.
- Asignación por cultura:

  | Cultura | Estilos de aldea |
  |---|---|
  | Valdor | roble |
  | Sylvaran | abedul y cerezo |
  | Durnhal | montaña |
  | Solarys | badlands |
  | Brumaverde | pantano y bosque oscuro |
  | Hrimfell | taiga gigante |

## Aspectos de aldeanos

`src/main/resources/assets/hearthbound/textures/entity/settler/extra/` contiene aspectos del mod **mobs_npc** para Luanti (https://github.com/minetest-mirrors/mobs_npc). Los de 64×32 se ampliaron a 64×64.

| Archivo | Original | Autor | Licencia |
|---|---|---|---|
| farmer_sdzen_2.png | mobs_npc.png "Farmer 2" | Sdzen | CC BY-SA 3.0 |
| farmer_sdzen_7.png | mobs_npc2.png "Farmer 7" | Sdzen | CC BY-SA 3.0 |
| farmer_sdzen_5.png | mobs_npc3.png "Farmer 5" | Sdzen | CC BY-SA 3.0 |
| bartender_sdzen.png | mobs_npc4.png "Bartender" | Sdzen | CC BY-SA 3.0 |
| dwarf_fishywet.png | mobs_trader.png "dwarf" | fishyWET | CC BY-SA 3.0 |
| dwarf_catninja.png | mobs_trader2.png "dwarf1" | CatNinja | CC BY-SA 3.0 |
| dwarf_gabo.png | mobs_trader3.png "dwarf2" | Gabo | CC BY-SA 3.0 |
| wanderer_astrobe_1.png | mobs_npc5.png | Astrobe | CC0 |
| wanderer_astrobe_2.png | mobs_npc6.png | Astrobe | CC0 |
| trader_astrobe.png | mobs_trader4.png | Astrobe | CC0 |

Los archivos CC BY-SA 3.0 se redistribuyen bajo esa misma licencia: https://creativecommons.org/licenses/by-sa/3.0/

## Inspiración

La idea general está inspirada en Millénaire, de Kinniken. Hearthbound no contiene código, texturas ni edificios de Millénaire.
