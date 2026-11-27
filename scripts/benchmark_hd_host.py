#!/usr/bin/env python3
"""Host-only HD reference encoding, not Android device performance."""
import argparse,hashlib,json,subprocess,time,resource
from pathlib import Path

def run(out):
    out.mkdir(parents=True,exist_ok=True);rows=[]
    for duration in (2,10,30):
        p=out/f'host-hd-{duration}s.mp4'
        start=time.perf_counter()
        subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-nostdin','-y','-threads','2','-f','lavfi','-i','testsrc2=size=1280x720:rate=24','-f','lavfi','-i','sine=frequency=440:sample_rate=48000','-t',str(duration),'-c:v','libx264','-threads','2','-preset','ultrafast','-pix_fmt','yuv420p','-b:v','4M','-c:a','aac','-b:a','128k','-movflags','+faststart',str(p)],check=True)
        elapsed=time.perf_counter()-start
        probe=json.loads(subprocess.check_output(['ffprobe','-v','error','-show_streams','-show_format','-of','json',str(p)]))
        streams={s['codec_type']:s for s in probe['streams']};v,a=streams['video'],streams['audio'];assert v['codec_name']=='h264' and a['codec_name']=='aac' and (v['width'],v['height'])==(1280,720)
        assert abs(float(v['duration'])-duration)<.12 and abs(float(a['duration'])-duration)<.12
        start=time.perf_counter();subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-nostdin','-threads','2','-i',str(p),'-f','null','-'],check=True);decode=time.perf_counter()-start
        rows.append({'duration_s':duration,'encode_s':elapsed,'decode_s':decode,'bytes':p.stat().st_size,'video_bitrate':int(v['bit_rate']),'audio_bitrate':int(a['bit_rate']),'sha256':hashlib.sha256(p.read_bytes()).hexdigest(),'child_peak_rss_kib_cumulative':resource.getrusage(resource.RUSAGE_CHILDREN).ru_maxrss})
    (out/'hd-report.json').write_text(json.dumps({'scope':'Linux host FFmpeg synthetic 720p24 reference; not Android','rows':rows},indent=2)+'\n');print(json.dumps(rows,indent=2))
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--out',type=Path,required=True);run(p.parse_args().out)
