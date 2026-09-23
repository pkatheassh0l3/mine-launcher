import json, os
IDS = set(json.load(open('/tmp/claude-0/jars/all_item_ids.json')))
OUT = '/tmp/claude-0/unify/out'
def chk(i):
    if not i.startswith('minecraft:'): assert i in IDS, i
    return i

# grupo -> lista de objetos equivalentes (el primero es el que se queda)
GROUPS = {
 # Farmer's Delight / Create / Delights -> TFC
 'tomato': ['tfc:food/tomato','farmersdelight:tomato'],
 'cabbage': ['tfc:food/cabbage','farmersdelight:cabbage'],
 'onion': ['tfc:food/onion','farmersdelight:onion'],
 'rice_grain': ['tfc:food/rice_grain','farmersdelight:rice','millenaire:rice'],
 'rice': ['tfc:food/rice','farmersdelight:rice_panicle'],
 'wheat_dough': ['tfc:food/wheat_dough','farmersdelight:wheat_dough','create:dough'],
 'wheat_flour': ['tfc:food/wheat_flour','create:wheat_flour'],
 'pumpkin_chunks': ['tfc:food/pumpkin_chunks','farmersdelight:pumpkin_slice'],
 'straw': ['tfc:straw','farmersdelight:straw'],
 'cooked_egg': ['tfc:food/cooked_egg','farmersdelight:fried_egg','naturalist:cooked_egg'],
 'garlic': ['tfc:food/garlic','veggiesdelight:garlic'],
 'bell_pepper': ['tfc:food/red_bell_pepper','veggiesdelight:bellpepper'],
 'bell_pepper_seeds': ['tfc:seeds/red_bell_pepper','veggiesdelight:bellpepper_seeds'],
 'tomato_seeds': ['tfc:seeds/tomato','farmersdelight:tomato_seeds'],
 'mashed_potatoes': ['veggiesdelight:mashed_potatoes','moredelight:mashed_potatoes'],
 'date': ['ramadandelight:date','spawn:dates'],
 # Millénaire -> TFC
 'maize': ['tfc:food/maize','millenaire:maize'],
 'olive': ['tfc:food/olive','millenaire:olives'],
 'red_apple': ['tfc:food/red_apple','millenaire:cider_apple','minecraft:apple'],
 'bear': ['tfc:food/bear','millenaire:bearmeat_raw'],
 'cooked_bear': ['tfc:food/cooked_bear','millenaire:bearmeat_cooked'],
 'wolf': ['tfc:food/wolf','millenaire:wolfmeat_raw'],
 'cooked_wolf': ['tfc:food/cooked_wolf','millenaire:wolfmeat_cooked'],
 'shellfish': ['tfc:food/shellfish','millenaire:seafood_raw'],
 'cooked_shellfish': ['tfc:food/cooked_shellfish','millenaire:seafood_cooked'],
 'silk': ['millenaire:silk','crittersandcompanions:silk'],
 # Naturalist -> TFC
 'venison': ['tfc:food/venison','naturalist:venison'],
 'cooked_venison': ['tfc:food/cooked_venison','naturalist:cooked_venison'],
 'largemouth_bass': ['tfc:food/largemouth_bass','naturalist:bass'],
 'cooked_largemouth_bass': ['tfc:food/cooked_largemouth_bass','naturalist:cooked_bass'],
 'red_piranha': ['tfc:food/red_piranha','naturalist:piranha'],
 'cooked_red_piranha': ['tfc:food/cooked_red_piranha','naturalist:cooked_piranha'],
 'snail_shell': ['naturalist:snail_shell','spawn:snail_shell'],
 'duck': ['tfc:food/duck','naturalist:duck'],
 'cooked_duck': ['tfc:food/cooked_duck','naturalist:cooked_duck'],
 # Vanilla (no existe en un mundo TFC) -> TFC
 'carrot': ['tfc:food/carrot','minecraft:carrot'],
 'potato': ['tfc:food/potato','minecraft:potato'],
 'baked_potato': ['tfc:food/baked_potato','minecraft:baked_potato'],
 'beet': ['tfc:food/beet','minecraft:beetroot'],
 'wheat': ['tfc:food/wheat','minecraft:wheat'],
 'wheat_seeds': ['tfc:seeds/wheat','minecraft:wheat_seeds'],
 'wheat_bread': ['tfc:food/wheat_bread','minecraft:bread'],
 'sugarcane': ['tfc:food/sugarcane','minecraft:sugar_cane'],
 'melon_slice': ['tfc:food/melon_slice','minecraft:melon_slice'],
 'dried_kelp': ['tfc:food/dried_kelp','minecraft:dried_kelp'],
 'beef': ['tfc:food/beef','minecraft:beef'], 'cooked_beef': ['tfc:food/cooked_beef','minecraft:cooked_beef'],
 'pork': ['tfc:food/pork','minecraft:porkchop'], 'cooked_pork': ['tfc:food/cooked_pork','minecraft:cooked_porkchop'],
 'chicken': ['tfc:food/chicken','minecraft:chicken'], 'cooked_chicken': ['tfc:food/cooked_chicken','minecraft:cooked_chicken'],
 'mutton': ['tfc:food/mutton','minecraft:mutton'], 'cooked_mutton': ['tfc:food/cooked_mutton','minecraft:cooked_mutton'],
 'rabbit': ['tfc:food/rabbit','minecraft:rabbit'], 'cooked_rabbit': ['tfc:food/cooked_rabbit','minecraft:cooked_rabbit'],
 'cod': ['tfc:food/cod','minecraft:cod'], 'cooked_cod': ['tfc:food/cooked_cod','minecraft:cooked_cod'],
 'salmon': ['tfc:food/salmon','minecraft:salmon'], 'cooked_salmon': ['tfc:food/cooked_salmon','minecraft:cooked_salmon'],
 'tropical_fish': ['tfc:food/tropical_fish','minecraft:tropical_fish'],
}
for g, items in GROUPS.items():
    for i in items: chk(i)

MOD_PRIORITIES = ['tfc','farmersdelight','veggiesdelight','ramadandelight','millenaire','naturalist',
                  'crittersandcompanions','spawn','moredelight','create','minecraft']
# para cada grupo el primero debe ganar: forzamos con priority_overrides cuando haga falta
overrides = {}
for g, items in GROUPS.items():
    win = items[0].split(':')[0]
    others = [i.split(':')[0] for i in items[1:]]
    if any(MOD_PRIORITIES.index(o) < MOD_PRIORITIES.index(win) for o in others):
        overrides['unificado:' + g] = win

au = os.path.join(OUT, 'config/almostunified')
os.makedirs(au + '/unification', exist_ok=True)
json.dump({
  "custom_tags": dict({'unificado:' + g: items for g, items in GROUPS.items()}, **{"c:ingots/iron": ["tfc:metal/ingot/wrought_iron"]}),
  "tag_substitutions": {},
  "item_tag_inheritance_mode": "ALLOW", "item_tag_inheritance": {},
  "block_tag_inheritance_mode": "ALLOW", "block_tag_inheritance": {},
  "emi_strict_hiding": True
}, open(au + '/tags.json', 'w'), indent=2)
json.dump({
  "mod_priorities": MOD_PRIORITIES,
  "priority_overrides": overrides,
  "stone_variants": [],
  "tags": ['unificado:' + g for g in GROUPS],
  "ignored_tags": [], "ignored_items": [],
  "ignored_recipe_types": [], "ignored_recipe_ids": [],
  "recipe_viewer_hiding": True, "loot_unification": True, "ignored_loot_tables": []
}, open(au + '/unification/unificado.json', 'w'), indent=2)
json.dump({
  "mod_priorities": ['tfc','create','minecraft'],
  "priority_overrides": {},
  "stone_variants": ["stone"],
  "tags": ["c:ingots/{material}", "c:nuggets/{material}"],
  "ignored_tags": [], "ignored_items": [],
  "ignored_recipe_types": [], "ignored_recipe_ids": [],
  "recipe_viewer_hiding": True, "loot_unification": True, "ignored_loot_tables": []
}, open(au + '/unification/materials.json', 'w'), indent=2)
json.dump({"server_only": False, "world_gen_unification": False}, open(au + '/startup.json', 'w'), indent=2)
print('AU ok, overrides', overrides)
json.dump(GROUPS, open('/tmp/claude-0/unify/groups.json','w'))
