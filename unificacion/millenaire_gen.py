import os, json, copy, collections
SRC = '/tmp/claude-0/mill/full/millenaire'
OUT = '/tmp/claude-0/unify/out/millenaire-custom/tfc_unificado'
IDS = set(json.load(open('/tmp/claude-0/jars/all_item_ids.json')))

M = {
 # --- vanilla -> TFC (comida y metales que no existen en un mundo TFC)
 'minecraft:wheat':'tfc:food/wheat', 'minecraft:wheat_seeds':'tfc:seeds/wheat',
 'minecraft:bread':'tfc:food/wheat_bread',
 'minecraft:potato':'tfc:food/potato', 'minecraft:baked_potato':'tfc:food/baked_potato',
 'minecraft:carrot':'tfc:food/carrot', 'minecraft:beetroot':'tfc:food/beet',
 'minecraft:apple':'tfc:food/red_apple', 'minecraft:sugar_cane':'tfc:food/sugarcane',
 'minecraft:melon_slice':'tfc:food/melon_slice', 'minecraft:dried_kelp':'tfc:food/dried_kelp',
 'minecraft:beef':'tfc:food/beef', 'minecraft:cooked_beef':'tfc:food/cooked_beef',
 'minecraft:porkchop':'tfc:food/pork', 'minecraft:cooked_porkchop':'tfc:food/cooked_pork',
 'minecraft:chicken':'tfc:food/chicken', 'minecraft:cooked_chicken':'tfc:food/cooked_chicken',
 'minecraft:mutton':'tfc:food/mutton', 'minecraft:cooked_mutton':'tfc:food/cooked_mutton',
 'minecraft:rabbit':'tfc:food/rabbit', 'minecraft:cooked_rabbit':'tfc:food/cooked_rabbit',
 'minecraft:cod':'tfc:food/cod', 'minecraft:cooked_cod':'tfc:food/cooked_cod',
 'minecraft:salmon':'tfc:food/salmon', 'minecraft:cooked_salmon':'tfc:food/cooked_salmon',
 'minecraft:tropical_fish':'tfc:food/tropical_fish',
 'minecraft:iron_ingot':'tfc:metal/ingot/wrought_iron', 'minecraft:gold_ingot':'tfc:metal/ingot/gold',
 'minecraft:copper_ingot':'tfc:metal/ingot/copper',
 # --- duplicados de Millénaire -> TFC
 'millenaire:rice':'tfc:food/rice_grain', 'millenaire:maize':'tfc:food/maize',
 'millenaire:olives':'tfc:food/olive', 'millenaire:cider_apple':'tfc:food/red_apple',
 'millenaire:bearmeat_raw':'tfc:food/bear', 'millenaire:bearmeat_cooked':'tfc:food/cooked_bear',
 'millenaire:wolfmeat_raw':'tfc:food/wolf', 'millenaire:wolfmeat_cooked':'tfc:food/cooked_wolf',
 'millenaire:seafood_raw':'tfc:food/shellfish', 'millenaire:seafood_cooked':'tfc:food/cooked_shellfish',
}
for k, v in M.items():
    assert v in IDS, v

# claves cuyo valor es un BLOQUE (no tocar)
BLOCK_KEYS = {'targetBlock','cropBlock','soilBlock','sapling','flowers','animalType','targetState','block','blocks'}
ITEM_LIST_KEYS = {'heldItems','deliver_to','bring_back_home_goods','collect_goods','items_needed',
                  'priorityInvPenaltyItems','heldItemsDestination','foodItems','sells','buys'}
ITEM_STR_KEYS = {'item','irrigationBonusCrop','seedItem','collectGood','icon','travelbook_held_item',
                 'travelbook_held_item_off_hand','default_weapon'}
DICT_KEY_KEYS = {'villageLimit','buildingLimit','townhallLimit','food_growth','food_conception'}

changes = collections.Counter()
def tr(o, key=None):
    if isinstance(o, dict):
        out = {}
        for k, v in o.items():
            if k in BLOCK_KEYS:
                out[k] = v
            elif k in DICT_KEY_KEYS and isinstance(v, dict):
                nd = {}
                for kk, vv in v.items():
                    nk = M.get(kk, kk)
                    if nk != kk: changes[kk] += 1
                    nd[nk] = max(nd.get(nk, 0), vv) if isinstance(vv, (int, float)) and nk in nd else vv
                out[k] = nd
            else:
                out[k] = tr(v, k)
        return out
    if isinstance(o, list):
        return [tr(x, key) for x in o]
    if isinstance(o, str) and (key in ITEM_STR_KEYS or key in ITEM_LIST_KEYS) and o in M:
        changes[o] += 1
        return M[o]
    return o

def load(p):
    return json.load(open(p, encoding='utf-8'))
def dump(p, d):
    os.makedirs(os.path.dirname(p), exist_ok=True)
    json.dump(d, open(p, 'w', encoding='utf-8'), indent=2, ensure_ascii=False)

files_written = 0
for root, _, fs in os.walk(SRC):
    rel_root = os.path.relpath(root, SRC)
    parts = rel_root.split(os.sep)
    for f in fs:
        if not f.endswith('.json'): continue
        rel = os.path.join(rel_root, f)
        top = parts[0]
        if top not in ('gathering_type', 'cultures', 'quests', 'visit_goal', 'villager_config'): continue
        if top == 'cultures' and len(parts) >= 3 and parts[2] == 'buildings': continue
        try: d = load(os.path.join(root, f))
        except Exception as e: print('skip', rel, e); continue
        if f == 'traded_goods.json':
            overlay = []; seen = {}
            for g in d.get('goods', []):
                it = g.get('item')
                if it in M:
                    ng = dict(g); ng['item'] = M[it]; overlay.append(ng); changes[it] += 1
            # detectar duplicados de item dentro de la cultura tras el cambio
            final = {}
            for g in d.get('goods', []):
                it = M.get(g.get('item'), g.get('item'))
                final.setdefault(it, []).append(g['id'])
            for it, ids in final.items():
                if len(ids) > 1 and any(M.get(x['item']) for x in d['goods'] if x['id'] in ids):
                    print('  aviso duplicado', parts[1], it, ids)
            if overlay:
                dump(os.path.join(OUT, rel), {'goods': overlay}); files_written += 1
            continue
        nd = tr(d)
        if nd != d:
            dump(os.path.join(OUT, rel), nd); files_written += 1
print('ficheros:', files_written)
for k, v in changes.most_common(): print(v, k)
