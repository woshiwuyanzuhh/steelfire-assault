from pathlib import Path
import re,json
ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'app/src/main/assets'
LEVELS=ASSETS/'levels'
# Per-level mechanic ids from GAME_DESIGN's authored mechanic contract.
MECHANICS={
3:'tide_valve',4:'stealth_alert',5:'rail_switch',6:'sand_current',7:'mirage_scan',8:'frequency_pair',9:'escort_route',10:'beacon_safe_zone',
11:'tide_cover',12:'breath_window',13:'current_flip',14:'sonar_rhythm',15:'submersible_recoil',16:'thermal_zone',17:'lift_selection',18:'molten_bridge',19:'weapon_overheat',20:'forge_overheat',
21:'zero_g_thrust',22:'gravity_flip',23:'vehicle_jump',24:'gravity_anchor',25:'rotating_gravity',26:'magnetic_freight',27:'thermal_boots',28:'signal_switch',29:'bridge_escort',30:'frostline_cannon',
31:'glass_refraction',32:'spore_purifier',33:'vine_bridge',34:'seed_convoy',35:'spore_core',36:'counterweight_elevator',37:'pressure_valves',38:'rotary_tunnel',39:'deepwell_rescue',40:'drill_emperor',
41:'reflection_gates',42:'mimic_counterplay',43:'memory_sequence',44:'dual_world',45:'mirror_entity',46:'coolant_routing',47:'civilian_convoy',48:'collapse_rhythm',49:'overdrive_weapon',50:'reactor_throne'
}
assert set(MECHANICS)==set(range(3,51))
# Matrix titles are the source of truth; keeping them in the generated JSON prevents drift.
text=(ROOT/'GAME_DESIGN.md').read_text(encoding='utf-8')
titles={int(m.group(1)):m.group(2).strip() for m in re.finditer(r'^\| L(\d{2}) \|\s*([^|]+?)\s*\|',text,re.M)}
missing=set(range(3,51))-set(titles)
if missing: raise SystemExit(f'missing design titles: {sorted(missing)}')
common_assets=[
    {
      'id':'industrial_port','path':'gfx/backgrounds/industrial_port.png','kind':'background','source':'project_original','license':'project-original','size_bytes':2450183,'sha256':'f328a9dd1b6b9d0725d11055369448fb91310ab3d5476d1275441dbba9e2c26c','generator':'art/generated/industrial_port','fallback':'canvas_industrial_harbor'
    },
    {
      'id':'enemy_sheet','path':'gfx/characters/enemy_sheet.png','kind':'enemy','source':'project_original','license':'project-original','size_bytes':1253414,'sha256':'591728869937ca88a80a7269674c9bf71b273d6d165421c5c09553fd1a4bcf23','generator':'art/generated/enemy_sheet','fallback':'canvas_enemy_primitives'
    },
    {
      'id':'operative_sheet','path':'gfx/characters/operative_sheet.png','kind':'player','source':'project_original','license':'project-original','size_bytes':1290639,'sha256':'d5ffc2c4cb3ffbf49a71e540d25a8fb868eebc6b80e66ffee6cc66c39aa41850','generator':'art/generated/operative_sheet','fallback':'canvas_operative_primitives'
    }
]
ui_audio={'id':'ui_cue','kind':'audio','path':'audio/sfx/ui.wav','source':'project_original','license':'project-original','size_bytes':4454,'sha256':'782cf9a6833352624eafc04e1323e17efd2a7a8fa59ff76a4b402de78db4eb93','generator':'tools/audio/synthesize_ui','fallback':'silent_local_cue'}
enemies=[['regular','sentry'],['regular','elite'],['shield_carrier','short_hop_drone'],['elite','sentry']]
for n in range(3,51):
    mech=MECHANICS[n]
    mechanic_params={
      'cycle_ticks': str(72 + ((n * 13) % 90)),
      'severity': str((n % 3) + 1),
      'visual_variant': str(n % 6)
    }
    issue_files=sorted((ROOT/'.scratch/50-level-expansion/issues').glob(f'level-{n:02d}-*.md'))
    if not issue_files: raise SystemExit(f'missing issue slug for L{n:02d}')
    slug=issue_files[0].stem.split('-', 2)[2].replace('-', '_')
    level_id=f'level_{n:02d}_{slug}'
    title=titles[n]
    root={
      'id':level_id,'index':n,'act':((n-1)//5)+1,'title':title,'world_width':11520,
      'story_cue':f'story_l{n:02d}_intro',
      'new_mechanic':{'id':mech,'version':1,'params':mechanic_params},
      'checkpoints':[{'id':f'l{n:02d}_checkpoint','x':3300},{'id':f'l{n:02d}_clear','x':7200}],
      'beats':[
        {'id':f'l{n:02d}_b01','x':360,'purpose':f'teach_{mech}','enemy_kinds':enemies[0],'mechanic_params':{'id':mech,'interaction':'teach'},'story_cue':f'story_l{n:02d}_intro','asset_refs':['gfx/backgrounds/industrial_port.png',f'procedural:levels/level_{n:02d}/{mech}']},
        {'id':f'l{n:02d}_b02','x':3000,'purpose':f'combine_{mech}','enemy_kinds':enemies[1],'mechanic_params':{'id':mech,'interaction':'combine'},'story_cue':f'story_l{n:02d}_mid','asset_refs':['gfx/characters/enemy_sheet.png',f'procedural:levels/level_{n:02d}/{mech}']},
        {'id':f'l{n:02d}_b03','x':6000,'purpose':f'pressure_{mech}','enemy_kinds':enemies[2],'mechanic_params':{'id':mech,'interaction':'pressure'},'story_cue':f'story_l{n:02d}_mid','asset_refs':['gfx/backgrounds/industrial_port.png',f'procedural:levels/level_{n:02d}/{mech}']},
        {'id':f'l{n:02d}_b04','x':8500,'purpose':f'clear_{mech}','enemy_kinds':enemies[3],'mechanic_params':{'id':mech,'interaction':'clear'},'story_cue':f'story_l{n:02d}_end','asset_refs':['gfx/characters/operative_sheet.png',f'procedural:levels/level_{n:02d}/{mech}']}
      ],
      'boss':None,
      'asset_manifest':f'levels/level_{n:02d}/manifest.json'
    }
    if n%5==0:
      root['beats'].append({'id':f'l{n:02d}_b05','x':10200,'purpose':f'boss_arena_{mech}','enemy_kinds':['sentry'],'mechanic_params':{'id':mech,'interaction':'boss'},'story_cue':f'story_l{n:02d}_end','asset_refs':['gfx/backgrounds/industrial_port.png',f'procedural:levels/level_{n:02d}/boss_core']})
      root['boss']={'id':f'boss_l{n:02d}_{slug}','name':title,'phases':[{'id':'phase_1','weakpoint':'weakpoint_1','warning_ticks':30},{'id':'phase_2','weakpoint':'weakpoint_2','warning_ticks':30},{'id':'phase_3','weakpoint':'weakpoint_3','warning_ticks':30}],'asset_refs':[f'procedural:levels/level_{n:02d}/boss_core']}
    level_path=LEVELS/f'level_{n:02d}.json'; level_path.write_text(json.dumps(root,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    proc=[
      {'id':mech,'kind':'mechanic_geometry','generator':'Canvas authored mechanic primitive v1','license':'project-original'},
      {'id':f'l{n:02d}_collision','kind':'collision_layer','generator':'Canvas authored collision plane v1','license':'project-original'},
      {'id':f'l{n:02d}_particles','kind':'particle_profile','generator':'Canvas authored particle profile v1','license':'project-original'},
      {'id':f'story_l{n:02d}_cue','kind':'subtitle','generator':'Canvas story-cue primitive v1','license':'project-original'}
    ]
    if n%5==0: proc.append({'id':'boss_core','kind':'boss_geometry','generator':'Canvas boss-phase primitive v1','license':'project-original'})
    manifest={'schema_version':1,'level_id':level_id,'assets':common_assets,'procedural_assets':proc,'audio':[ui_audio],'offline_only':True}
    mdir=LEVELS/f'level_{n:02d}'; mdir.mkdir(parents=True,exist_ok=True); (mdir/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(f'generated {48} level JSON/manifest pairs')
