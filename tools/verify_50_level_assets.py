#!/usr/bin/env python3
"""Validate the offline JSON/manifest contract for every authored level (L01-L50)."""
from __future__ import annotations
import hashlib,json,re,sys
from pathlib import Path
from typing import Any
ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'app/src/main/assets'; LEVELS=ASSETS/'levels'
LEVEL_KEYS={'id','index','act','title','world_width','story_cue','new_mechanic','checkpoints','beats','boss','asset_manifest'}
CHECKPOINT_KEYS={'id','x'}
BEAT_KEYS={'id','x','purpose','enemy_kinds','mechanic_params','story_cue','asset_refs'}
failed:list[str]=[]
def check(label:str, ok:bool):
 if ok: print('PASS',label)
 else: failed.append(label); print('FAIL',label,file=sys.stderr)
def read(path:Path):
 try:return json.loads(path.read_text(encoding='utf-8'))
 except Exception as e: check(f'{path.name} parses ({e})',False); return None
def local(raw:Any)->Path|None:
 if not isinstance(raw,str) or re.search(r'(?:https?|ftp)://|\b(?:cdn|remote)\b',raw,re.I) or raw.startswith(('/', '\\')) or '..' in Path(raw).parts:return None
 p=(ASSETS/raw).resolve()
 try:p.relative_to(ASSETS.resolve())
 except ValueError:return None
 return p
def metadata(item:Any, hashes:list[bool])->bool:
 if not isinstance(item,dict):return False
 required={'id','path','source','license','size_bytes','sha256','generator','fallback'}
 if not required.issubset(item):return False
 p=local(item.get('path'))
 if p is None or not p.is_file():return False
 digest=item.get('sha256'); size=item.get('size_bytes')
 ok=(all(isinstance(item.get(k),str) and item[k].strip() for k in ('id','source','license','generator','fallback')) and isinstance(digest,str) and re.fullmatch(r'[0-9a-fA-F]{64}',digest) is not None and isinstance(size,int) and size>0)
 if ok: hashes.append(hashlib.sha256(p.read_bytes()).hexdigest()==digest.lower() and p.stat().st_size==size)
 return ok
levels=[]
for n in range(1,51):
 path=LEVELS/f'level_{n:02d}.json'; data=read(path)
 check(f'L{n:02d} JSON exists',path.is_file() and data is not None)
 if not isinstance(data,dict): continue
 levels.append(data)
 check(f'L{n:02d} top-level schema',set(data)==LEVEL_KEYS)
 check(f'L{n:02d} identity',data.get('index')==n and isinstance(data.get('id'),str) and data['id'].startswith(f'level_{n:02d}_'))
 check(f'L{n:02d} title/story',isinstance(data.get('title'),str) and bool(data['title'].strip()) and isinstance(data.get('story_cue'),str) and bool(data['story_cue'].strip()) and not re.search(r'https?://',data.get('story_cue',''),re.I))
 mech=data.get('new_mechanic')
 mechanic_ok=isinstance(mech,dict) and isinstance(mech.get('id'),str) and bool(mech['id']) and mech.get('version')==1
 if n >= 3 and mechanic_ok:
  params=mech.get('params')
  mechanic_ok=isinstance(params,dict) and params.get('cycle_ticks','').isdigit() and 36 <= int(params['cycle_ticks']) <= 240 and params.get('severity','') in {'1','2','3'} and params.get('visual_variant','') in {'0','1','2','3','4','5'}
 if mechanic_ok and mech.get('id')=='magnetic_cover':
  mechanic_ok=set(mech)=={'id','version','lock_cooldown_ticks','boxes'} and isinstance(mech.get('boxes'),list) and bool(mech['boxes'])
 if mechanic_ok and mech.get('id')=='bridge_collapse':
  mechanic_ok=set(mech)=={'id','version','warning_ticks','collapse_ticks','segments'} and isinstance(mech.get('segments'),list) and bool(mech['segments'])
 check(f'L{n:02d} mechanic',mechanic_ok)
 cps=data.get('checkpoints'); beats=data.get('beats')
 check(f'L{n:02d} checkpoints',isinstance(cps,list) and len(cps)>=2 and all(isinstance(x,dict) and set(x)==CHECKPOINT_KEYS and isinstance(x['id'],str) and isinstance(x['x'],int) for x in cps) and all(a['x']<b['x'] for a,b in zip(cps,cps[1:])))
 check(f'L{n:02d} beats',isinstance(beats,list) and len(beats)>=4 and all(isinstance(x,dict) and set(x)==BEAT_KEYS and isinstance(x['id'],str) and isinstance(x['x'],int) and isinstance(x['purpose'],str) and isinstance(x['enemy_kinds'],list) and bool(x['enemy_kinds']) and isinstance(x['mechanic_params'],dict) and x['mechanic_params'].get('id')==mech.get('id') and isinstance(x['story_cue'],str) and isinstance(x['asset_refs'],list) and bool(x['asset_refs']) and all(isinstance(r,str) and not re.search(r'https?://|cdn|remote',r,re.I) for r in x['asset_refs']) for x in beats) and all(a['x']<b['x'] for a,b in zip(beats,beats[1:])))
 if n >= 3:
  route_ok=isinstance(cps,list) and bool(cps) and isinstance(beats,list) and bool(beats) and data.get('world_width',0) >= 10000 and cps[-1].get('x',0) >= 7000 and beats[-1].get('x',0) >= 8000
  check(f'L{n:02d} full route beats',route_ok)
 boss=data.get('boss')
 if n%5==0:
  phases=boss.get('phases') if isinstance(boss,dict) else None
  check(f'L{n:02d} boss contract',isinstance(boss,dict) and isinstance(boss.get('id'),str) and isinstance(boss.get('name'),str) and isinstance(phases,list) and len(phases)>=3 and all(isinstance(p,dict) and isinstance(p.get('weakpoint'),str) and isinstance(p.get('warning_ticks'),int) and p['warning_ticks']>0 for p in phases))
 else: check(f'L{n:02d} no boss',boss is None)
 ref=data.get('asset_manifest'); mp=ASSETS/ref if isinstance(ref,str) else Path('missing')
 manifest=read(mp) if mp.is_file() else None
 check(f'L{n:02d} manifest exists',mp.is_file() and isinstance(manifest,dict))
 if not isinstance(manifest,dict): continue
 check(f'L{n:02d} manifest identity',manifest.get('schema_version')==1 and manifest.get('level_id')==data.get('id') and manifest.get('offline_only') is True)
 assets=manifest.get('assets'); audio=manifest.get('audio'); proc=manifest.get('procedural_assets')
 check(f'L{n:02d} manifest arrays',isinstance(assets,list) and bool(assets) and isinstance(audio,list) and isinstance(proc,list) and bool(proc))
 hs=[]; items=(assets if isinstance(assets,list) else[])+(audio if isinstance(audio,list) else[])
 check(f'L{n:02d} manifest local metadata',all(metadata(i,hs) for i in items) and len([i.get('id') for i in items if isinstance(i,dict)])==len(set(i.get('id') for i in items if isinstance(i,dict))))
 check(f'L{n:02d} manifest hashes',bool(hs) and all(hs))
 proc_ids={i.get('id') for i in proc if isinstance(i,dict)}
 check(f'L{n:02d} procedural metadata',all(isinstance(i,dict) and all(isinstance(i.get(k),str) and i[k].strip() and not re.search(r'https?://|cdn|remote',i[k],re.I) for k in ('id','kind','generator','license')) for i in proc))
 # Every authored reference is either a checked local path or a declared procedural asset.
 refs=[r for b in (beats if isinstance(beats,list) else[]) for r in (b.get('asset_refs',[]) if isinstance(b,dict) else[])] + ([r for r in boss.get('asset_refs',[]) if isinstance(r,str)] if isinstance(boss,dict) else [])
 resolved=True
 for r in refs:
  if r.startswith('procedural:'):
   resolved &= r.removeprefix(f'procedural:levels/level_{n:02d}/').split('/')[0] in proc_ids
  else: resolved &= local(r) is not None and local(r).is_file()
 check(f'L{n:02d} asset refs resolve',resolved)
# Cross-level uniqueness and boss layout.
check('50 level JSON files',len(levels)==50)
check('mechanic ids unique',len({x['new_mechanic']['id'] for x in levels if isinstance(x.get('new_mechanic'),dict)})==50)
check('level ids unique',len({x['id'] for x in levels})==50)
if failed:
 print(f'Level asset contract failed ({len(failed)} checks)',file=sys.stderr); raise SystemExit(1)
print('PASS 50 level offline asset contracts')
