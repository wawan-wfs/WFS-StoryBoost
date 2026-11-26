#!/usr/bin/env python3
"""Offline host reference for overlap composition; NOT the Android renderer."""
import argparse
import array
import hashlib
import json
import math
import subprocess
from pathlib import Path


def ff(*args):
    return subprocess.check_output(['ffmpeg', '-hide_banner', '-loglevel', 'error', '-nostdin', *map(str, args)])


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def fixture(path, color, tone):
    ff('-y', '-f', 'lavfi', '-i', f'color=c={color}:size=160x90:rate=24',
       '-f', 'lavfi', '-i', f'sine=frequency={tone}:sample_rate=48000', '-t', '2',
       '-c:v', 'libx264', '-pix_fmt', 'yuv420p', '-preset', 'ultrafast', '-c:a', 'aac', '-b:a', '96k', path)


def pixel(path, time):
    data = ff('-ss', time, '-i', path, '-frames:v', 1, '-vf', 'crop=2:2:80:44,format=rgb24', '-f', 'rawvideo', '-')
    if len(data) != 12:
        raise ValueError('Missing decoded frame')
    return [sum(data[k::3])/4 for k in range(3)]


def amplitude(samples, frequency, start, end):
    a, b = int(start*48000), int(end*48000)
    if b > len(samples) or b <= a:
        raise ValueError('Missing PCM window')
    re = sum(samples[i]*math.cos(2*math.pi*frequency*i/48000) for i in range(a, b))
    im = sum(samples[i]*math.sin(2*math.pi*frequency*i/48000) for i in range(a, b))
    return 2*math.hypot(re, im)/(b-a)


def require(condition, message):
    if not condition:
        raise ValueError(message)


def run(out):
    out.mkdir(parents=True, exist_ok=True)
    a, b, result = out/'red-440.mp4', out/'blue-780.mp4', out/'crossfade.mp4'
    fixture(a, 'red', 440)
    fixture(b, 'blue', 780)
    before = [digest(a), digest(b)]
    ff('-y', '-filter_complex_threads', 1, '-i', a, '-i', b, '-filter_complex',
       '[0:v][1:v]xfade=transition=fade:duration=0.5:offset=1.5[v];[0:a][1:a]acrossfade=d=0.5:c1=tri:c2=tri[a]',
       '-map', '[v]', '-map', '[a]', '-c:v', 'libx264', '-pix_fmt', 'yuv420p', '-preset', 'ultrafast',
       '-c:a', 'aac', '-b:a', '96k', '-movflags', '+faststart', result)
    probe = json.loads(subprocess.check_output(['ffprobe', '-v', 'error', '-show_streams', '-show_format', '-of', 'json', str(result)]))
    streams = {s['codec_type']: s for s in probe['streams']}
    duration = float(probe['format']['duration'])
    require(set(streams) == {'video', 'audio'}, 'Expected both streams')
    require(streams['video']['codec_name'] == 'h264' and streams['audio']['codec_name'] == 'aac', 'Wrong codecs')
    require(abs(duration-3.5) <= .12, 'Overlap duration mismatch')
    require(abs(float(streams['video']['duration'])-float(streams['audio']['duration'])) <= .12, 'A/V drift')
    first, middle, last = [pixel(result, t) for t in (.5, 1.75, 2.75)]
    require(first[0]>200 and first[2]<25, 'Red initial frame missing')
    require(last[2]>200 and last[0]<25, 'Blue final frame missing')
    require(75<middle[0]<180 and 75<middle[2]<180, 'No true video blend')
    samples = array.array('f')
    samples.frombytes(ff('-i', result, '-map', '0:a:0', '-ac', 1, '-ar', 48000, '-f', 'f32le', '-'))
    early = [amplitude(samples, f, .3, .9) for f in (440, 780)]
    overlap = [amplitude(samples, f, 1.68, 1.82) for f in (440, 780)]
    late = [amplitude(samples, f, 2.4, 3.0) for f in (440, 780)]
    require(early[0]>.05 and early[1]<early[0]*.1, 'Early audio isolation failed')
    require(late[1]>.05 and late[0]<late[1]*.1, 'Late audio isolation failed')
    require(early[0]*.25<overlap[0]<early[0]*.75 and late[1]*.25<overlap[1]<late[1]*.75, 'Audio crossfade missing')
    require(before == [digest(a), digest(b)], 'Source bytes changed')
    report = {'scope':'host FFmpeg reference, not Android', 'duration_s':duration, 'overlap_s':.5,
              'video_rgb':{'early':first,'overlap':middle,'late':last},
              'tone_amplitudes_440_780':{'early':early,'overlap':overlap,'late':late},
              'source_sha256':before,'output_sha256':digest(result),'status':'passed'}
    (out/'report.json').write_text(json.dumps(report, indent=2)+'\n', encoding='utf-8')
    return report


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    print(json.dumps(run(parser.parse_args().out), indent=2))
