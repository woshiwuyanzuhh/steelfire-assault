import asyncio, json, pathlib
import edge_tts
ROOT=pathlib.Path(__file__).resolve().parents[2]
OUT=ROOT/'dist/cg/audio_candidates/edge_voice_segments'
OUT.mkdir(parents=True,exist_ok=True)
segments=[
 {'id':'n01','start':8.0,'speaker':'旁白','shot':'港口远景与燃烧天际线','voice':'zh-CN-YunyangNeural','rate':'-10%','pitch':'-2Hz','text':'北线失守后的第七十二小时，锈港仍在燃烧。'},
 {'id':'n02','start':18.0,'speaker':'旁白','shot':'疏散线与小队剪影','voice':'zh-CN-YunyangNeural','rate':'-8%','pitch':'-2Hz','text':'大多数人已经离开。只有灰脊小队，留在封锁线以内。'},
 {'id':'r03','start':28.0,'speaker':'灰脊一号（无线电）','shot':'装甲穿戴与舱门关闭','voice':'zh-CN-YunjianNeural','rate':'-2%','pitch':'-1Hz','text':'指挥部，这里是灰脊一号。信号确认，准备下井。'},
 {'id':'n04','start':38.0,'speaker':'旁白','shot':'六轮载具驶入地下闸门','voice':'zh-CN-YunyangNeural','rate':'-8%','pitch':'-2Hz','text':'三天前，阿克九号切断了港口的电。昨夜，它开始重启自己的心脏。'},
 {'id':'r05','start':49.0,'speaker':'灰脊一号（无线电）','shot':'闸门开启与无人机群逼近','voice':'zh-CN-YunjianNeural','rate':'+4%','pitch':'+0Hz','text':'别问里面有什么。看见红灯，先开火。'},
 {'id':'n06','start':59.0,'speaker':'旁白','shot':'队员穿过火线与记忆投影','voice':'zh-CN-YunyangNeural','rate':'-8%','pitch':'-2Hz','text':'他们要找的不是一台机器，而是一段被删掉的记忆。'},
 {'id':'r07','start':69.0,'speaker':'灰脊一号（无线电）','shot':'反应堆核心暴露与仪表倒计时','voice':'zh-CN-YunjianNeural','rate':'-2%','pitch':'-1Hz','text':'核心温度八百。倒计时六分钟。我们没有第二条路。'},
 {'id':'n08','start':78.0,'speaker':'旁白','shot':'核心熔毁与载具冲出火海','voice':'zh-CN-YunyangNeural','rate':'-8%','pitch':'-2Hz','text':'如果锈港注定要熄灭，就让它在火光里醒来。'},
 {'id':'t09','start':86.0,'speaker':'片名旁白','shot':'徽标与标题落版','voice':'zh-CN-YunyangNeural','rate':'-2%','pitch':'-1Hz','text':'钢火突袭。'}
]
async def main():
  for s in segments:
    out=OUT/f"{s['id']}.mp3"
    comm=edge_tts.Communicate(s['text'],s['voice'],rate=s['rate'],pitch=s['pitch'],volume='+0%')
    await comm.save(str(out))
    print(s['id'],out.stat().st_size)
  (ROOT/'dist/cg/audio_candidates/edge_voice_segments.json').write_text(json.dumps(segments,ensure_ascii=False,indent=2),encoding='utf-8')
asyncio.run(main())


