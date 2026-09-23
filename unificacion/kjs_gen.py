import json, os, re
IDS = set(json.load(open('/tmp/claude-0/jars/all_item_ids.json')))
OUT = '/tmp/claude-0/unify/out/kubejs'
def chk(i):
    assert i.startswith('minecraft:') or i in IDS, i
    return i
def dump(rel, d):
    p = os.path.join(OUT, rel); os.makedirs(os.path.dirname(p), exist_ok=True)
    json.dump(d, open(p, 'w', encoding='utf-8'), indent=2, ensure_ascii=False)

# ---------------- 1) Recetas que CONECTAN mods ----------------
def shapeless(name, result, count, ings):
    dump(f'data/unificado/recipe/{name}.json', {
        "type": "minecraft:crafting_shapeless", "category": "misc",
        "ingredients": [({"tag": i[1:]} if i.startswith('#') else {"item": chk(i)}) for i in ings],
        "result": {"id": chk(result), "count": count}})
shapeless('aceite_oliva_millenaire', 'millenaire:oliveoil', 4, ['tfc:bucket/olive_oil'] + ['minecraft:glass_bottle']*4)
shapeless('piel_curtida_millenaire', 'millenaire:tannedhide', 1, ['tfc:medium_prepared_hide'])
shapeless('masa_millenaire', 'millenaire:masa', 1, ['tfc:food/maize_dough'])
shapeless('piel_naturalist_a_tfc', 'tfc:medium_raw_hide', 1, ['naturalist:hide'])
shapeless('algodon_a_hilo', 'minecraft:string', 1, ['millenaire:cotton']*3)

# ---------------- 2) Quitar recetas de objetos duplicados ----------------
REMOVE = ['farmersdelight:iron_knife', 'farmersdelight:flint_knife',
          'moredelight:wooden_knife', 'moredelight:stone_knife']
for rid in REMOVE:
    ns, path = rid.split(':')
    dump(f'data/{ns}/recipe/{path}.json', {"neoforge:conditions": [{"type": "neoforge:false"}], "type": "minecraft:crafting_shapeless", "ingredients": [{"item": "minecraft:stick"}], "result": {"id": "minecraft:stick"}})

# ---------------- 3) Datos de comida TFC (caducidad y nutrición) ----------------
P, V, G, F, D = 'protein', 'vegetables', 'grain', 'fruit', 'dairy'
MISSING = []
def food(item, hunger, sat, decay, water=0, **nut):
    if item not in IDS:
        MISSING.append(item); return
    d = {"ingredient": {"item": item}, "hunger": hunger, "saturation": sat, "decay_modifier": decay}
    if water: d["water"] = water
    for k, v in nut.items():
        if v: d[k] = v
    ns, path = item.split(':')
    dump(f'data/unificado/tfc/food/{ns}/{path}.json', d)

RAW = dict(hunger=2, sat=0.5, decay=3.0)
def raw_meat(i, p=1.5): food(i, 2, 0.5, 3.0, protein=p)
def cooked_meat(i, p=2.5): food(i, 4, 1.5, 2.0, protein=p)
def dish(i, h=4, s=2.0, dec=2.25, **n): food(i, h, s, dec, **n)
def drink(i, w=10, **n): food(i, 0, 0.2, 0.1, water=w, **n)

# Millénaire
for i in ['boudin','tripes','souvlaki']: dish('millenaire:'+i, protein=2.5, grain=0.5 if i=='souvlaki' else 0)
dish('millenaire:chickencurry', protein=2.0, vegetables=1.0, grain=1.5)
dish('millenaire:vegcurry', vegetables=2.5, grain=1.5)
dish('millenaire:ikayaki', protein=2.5)
dish('millenaire:inuitbearstew', 5, 2.5, protein=3.0, vegetables=1.0)
dish('millenaire:inuitmeatystew', 5, 2.5, protein=3.0, vegetables=1.0)
dish('millenaire:inuitpotatostew', 4, 2.0, vegetables=2.5)
dish('millenaire:udon', grain=2.5, protein=0.5)
dish('millenaire:wah', grain=2.0, vegetables=1.0, protein=1.0)
dish('millenaire:pide', 4, 2.0, 1.5, grain=2.0, protein=1.0)
food('millenaire:masa', 1, 0.3, 3.0, grain=0.5)
food('millenaire:feta', 3, 1.0, 0.5, dairy=2.0)
food('millenaire:yogurt', 2, 1.0, 1.0, dairy=2.0)
food('millenaire:lokum', 2, 0.5, 0.5, fruit=0.5)
food('millenaire:helva', 3, 1.0, 0.5, grain=1.0)
food('millenaire:rasgulla', 2, 0.8, 1.0, dairy=1.5)
food('millenaire:grapes', 1, 0.3, 2.5, water=5, fruit=0.75)
food('millenaire:pistachios', 1, 0.5, 0.3, protein=0.5)
food('millenaire:turmeric', 1, 0.2, 1.0, vegetables=0.25)
for i in ['cider','calva','sake','winebasic','winefancy','cacauhaa']: drink('millenaire:'+i, 10)
drink('millenaire:ayran', 15, dairy=1.0)

# Veggie's Delight
vd = 'veggiesdelight:'
for i in ['broccoli','cauliflower','sweet_potato','turnip','zucchini']: food(vd+i, 2, 0.5, 3.0, water=5, vegetables=1.0)
for i in ['garlic_clove','cauliflower_floret','zucchini_slice','dandelion_leaf']: food(vd+i, 1, 0.2, 3.5, vegetables=0.5)
for i in ['roasted_garlic_clove','roasted_cauliflower_floret','roasted_zucchini','smoked_bellpepper','baked_sweet_potato']: food(vd+i, 3, 1.0, 2.25, vegetables=1.5)
for i in ['broccoli_salad','cesar_salad','coleslaw','turnip_salad','roasted_vegetables','rice_and_vegetables','garlic_rice_with_cauliflower','garlic_stuffed_mushrooms','stuffed_bellpepper','stuffed_zucchini_boat','cauliflower_kuku','shakshouka']:
    dish(vd+i, vegetables=3.0, grain=1.0 if 'rice' in i else 0, protein=1.0 if i in ('cesar_salad','shakshouka','cauliflower_kuku') else 0)
for i in ['broccoli_soup','cauliflower_soup','turnip_water']: dish(vd+i, 3, 1.5, water=10, vegetables=2.5)
for i in ['carrot_juice','dandelion_juice']: drink(vd+i, 15, vegetables=1.0)
for i in ['cacciatore','garlic_chicken_stew','turnip_beef_stew','turnip_mutton_skewer','steak_and_broccoli','garlic_baked_cod','fish_and_chips','chicken_fajitas_wrap','dandelion_and_eggs']:
    dish(vd+i, 5, 2.5, protein=2.5, vegetables=1.5, grain=1.0 if i in ('fish_and_chips','chicken_fajitas_wrap') else 0)
for i in ['cauliflower_bread','garlic_bread','mhadjeb','uncooked_mhadjeb','potato_noodles','pasta_with_broccoli','lasagna_slice','vegan_pizza_slice','vegetables_wrap','vegetarian_burger','zucchini_sandwich','zucchini_quiche_slice','sweet_potato_pancakes']:
    dish(vd+i, 4, 2.0, 1.5, grain=2.0, vegetables=1.5)
for i in ['raw_vegetarian_patty','cooked_vegetarian_patty']: dish(vd+i, 3, 1.0, vegetables=2.0, protein=0.5)
for i in ['beetroot_brownie','carrot_cake_slice','sweet_potato_cupcake','sweet_potato_pie_slice','turnip_cake']: dish(vd+i, 3, 1.5, 1.5, grain=1.0, vegetables=1.0)
food(vd+'fermented_garlic_honey', 2, 1.0, 0.2, vegetables=0.5)

# More Delight
md = 'moredelight:'
for i in ['toast','bread_slice']: food(md+i, 2, 0.8, 1.0, grain=1.0)
for i in ['toast_with_blueberries','toast_with_glow_berries','toast_with_sweet_berries','toast_with_honey','toast_with_chocolate','toast_with_peanut_butter']: dish(md+i, 3, 1.2, 1.25, grain=1.0, fruit=0.5)
for i in ['toast_with_cheese']: dish(md+i, 3, 1.2, 1.25, grain=1.0, dairy=1.0)
for i in ['toast_with_egg','omelette','spanish_tortilla']: dish(md+i, 4, 1.5, protein=1.5, grain=0.5, vegetables=0.5 if i=='spanish_tortilla' else 0)
for i in ['carrot_soup','potato_salad','diced_potatoes','chicken_salad']: dish(md+i, 3, 1.5, vegetables=2.0, protein=1.5 if 'chicken' in i else 0)
for i in ['diced_potatoes_with_beef','diced_potatoes_with_chicken_cuts','diced_potatoes_with_porkchop','diced_potatoes_with_egg_and_tomato','cooked_rice_with_beef','cooked_rice_with_chicken_cuts','cooked_rice_with_porkchop','creamy_pasta_with_chicken_cuts','creamy_pasta_with_ham']:
    dish(md+i, 5, 2.5, protein=2.0, vegetables=1.0 if 'potato' in i else 0, grain=2.0 if ('rice' in i or 'pasta' in i) else 0, dairy=0.5 if 'creamy' in i else 0)
for i in ['chicken_sandwich_with_egg_and_tomato','egg_with_bacon_sandwich','hamburger_with_cheese','hamburger_with_egg','loaded_hamburger','porkchop_sandwich','simple_hamburger','steak_sandwich','steak_with_egg_sandwich','tomato_sandwich']:
    dish(md+i, 5, 2.5, grain=1.5, protein=2.0 if i!='tomato_sandwich' else 0, vegetables=1.0, dairy=1.0 if 'cheese' in i else 0)
food(md+'chocolate_popsicle', 2, 0.5, 1.0, dairy=0.5)

# Ramadan Delight
rd = 'ramadandelight:'
food(rd+'date', 1, 0.5, 1.0, fruit=1.0)
food(rd+'chickpea', 1, 0.3, 0.5, protein=0.5, vegetables=0.5)
food(rd+'parsley', 1, 0.1, 3.5, vegetables=0.25)
food(rd+'date_syrup', 1, 0.5, 0.2, fruit=0.5)
for i in ['small_dough','bourek_sheet','raw_bourek','raw_kebab','raw_samosa','savory_filling']: food(rd+i, 1, 0.3, 3.0, grain=0.5 if 'kebab' not in i else 0, protein=1.0 if i in ('raw_kebab','savory_filling') else 0)
for i in ['bourek','samosa','flat_bread','quiche_slice','knafeh','luqaimat','zalabiyeh','date_stuffed_cookie']: dish(rd+i, 4, 2.0, 1.5, grain=2.0, fruit=0.5 if 'date' in i else 0)
for i in ['kebab','musakhan','sayadieh','maqluba','haleem','tagine','sweet_tagine']: dish(rd+i, 5, 2.5, protein=2.5, grain=1.0, vegetables=1.0)
for i in ['chorba','harira']: dish(rd+i, 4, 2.0, water=10, protein=1.0, vegetables=1.5, grain=1.0)
for i in ['hummus_tahini','tabbouleh','chickpea_and_rice']: dish(rd+i, 4, 2.0, vegetables=2.0, grain=1.0, protein=1.0)
food(rd+'mahalabia', 3, 1.5, 1.5, dairy=2.0)

# Naturalist
nt = 'naturalist:'
for i in ['anglerfish','blobfish','catfish','bushmeat','clam_meat','crab_meat','drumstick','lizard_tail','mammoth_meat']: raw_meat(nt+i)
for i in ['cooked_anglerfish','cooked_blobfish','cooked_catfish','cooked_bushmeat','cooked_clam_meat','cooked_crab_meat','cooked_drumstick','cooked_lizard_tail','cooked_mammoth_meat','cooked_fish_fillet']: cooked_meat(nt+i)

# Spawn
sp = 'spawn:'
for i in ['bluefish','bluefish_slice','herring','herring_slice','tuna_chunk','tuna_slice','clam','dodo']: raw_meat(sp+i)
for i in ['cooked_bluefish','cooked_bluefish_slice','cooked_herring','cooked_herring_slice','cooked_tuna_chunk','cooked_tuna_slice','cooked_clam','cooked_dodo','escargot','steamed_clams']: cooked_meat(sp+i)
for i in ['clam_chowder','crab_boil']: dish(sp+i, 5, 2.5, water=5, protein=2.5, vegetables=1.0)
for i in ['bluefish_roll','herring_roll','tuna_roll','tuna_sandwich']: dish(sp+i, 4, 2.0, protein=2.0, grain=1.5)
food(sp+'canned_herring', 3, 1.5, 0.2, protein=2.0)
food(sp+'date_cookie', 2, 0.8, 1.0, grain=0.5, fruit=0.5)
food(sp+'sunflower_seeds', 1, 0.2, 0.3, protein=0.25)
food(sp+'roasted_sunflower_seeds', 1, 0.5, 0.3, protein=0.5)

# Unusual Fish
uf = 'unusualfishmod:'
raw_fish = sorted(i.split(':')[1] for i in IDS if i.startswith(uf+'raw_'))
for i in raw_fish: raw_meat(uf+i)
for i in sorted(i.split(':')[1] for i in IDS if i.startswith(uf+'cooked_')): cooked_meat(uf+i)
raw_meat(uf+'unusual_fillet')
for i in ['lobster_roll','unusual_sandwich','odd_fishsticks']: dish(uf+i, 4, 2.0, protein=2.0, grain=1.5)
dish(uf+'strange_broth', 3, 1.5, water=10, protein=1.0)

# Critters & Companions
raw_meat('crittersandcompanions:koi_fish')

n = sum(len(fs) for _, _, fs in os.walk(os.path.join(OUT, 'data/unificado/tfc/food')))
print('food defs', n); print('no existen:', MISSING)

# ---------------- 4) Script de cliente: ocultar lo que ya no se usa ----------------
hide = ['farmersdelight:iron_knife','farmersdelight:flint_knife','moredelight:wooden_knife','moredelight:stone_knife']
os.makedirs(os.path.join(OUT, 'client_scripts'), exist_ok=True)
open(os.path.join(OUT, 'client_scripts/ocultar_duplicados.js'), 'w').write(
'''// Oculta en JEI los objetos que se han sustituido por el de otro mod.
// (Almost Unified ya oculta automáticamente todos los duplicados de comida y metales.)
RecipeViewerEvents.removeEntriesCompletely('item', event => {
''' + ''.join(f"  event.remove('{h}')\n" for h in hide) + '''})
''')
open(os.path.join(OUT, 'README.txt'), 'w').write(
'''Unificación del modpack TFC Create
- config/almostunified/: qué objetos se consideran el mismo y cuál gana (TFC primero).
- kubejs/data/unificado/recipe/: recetas nuevas que conectan mods.
- kubejs/data/unificado/tfc/food/: caducidad y nutrición TFC para la comida de otros mods.
- kubejs/data/<mod>/recipe/: recetas desactivadas (duplicados).
- millenaire-custom/tfc_unificado/: los aldeanos de Millénaire usan comida y metales de TFC.
''')
