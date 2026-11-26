#!/usr/bin/env bash
set -euo pipefail
OUT="app/src/androidTest/assets"
mkdir -p "$OUT"
for tool in ffmpeg ffprobe python3; do command -v "$tool" >/dev/null; done
ffmpeg -hide_banner -loglevel error -nostdin -y \
 -f lavfi -i testsrc2=size=320x568:rate=24 -f lavfi -i sine=frequency=880:sample_rate=48000 \
 -t 2 -c:v libx264 -pix_fmt yuv420p -preset ultrafast -crf 26 -c:a aac -b:a 96k \
 -movflags +faststart "$OUT/qa-h264-aac.mp4"
ffmpeg -hide_banner -loglevel error -nostdin -y -i "$OUT/qa-h264-aac.mp4" \
 -map 0:v:0 -c:v copy -an "$OUT/qa-h264-silent.mp4"
ffmpeg -hide_banner -loglevel error -nostdin -y -display_rotation 90 \
 -i "$OUT/qa-h264-aac.mp4" -c copy "$OUT/qa-rotated.mp4"
# Each 2-second clip has a different grayscale middle and excluded dark/white tails.
for variant in a b; do
  if [[ $variant == a ]]; then shade=76; tone=440; else shade=170; tone=780; fi
  ffmpeg -hide_banner -loglevel error -nostdin -y -filter_threads 1 \
    -f lavfi -i "nullsrc=size=320x568:rate=24,format=yuv420p,geq=lum='if(lt(T,0.5),32,if(lt(T,1.5),$shade,220))':cb=128:cr=128" \
    -f lavfi -i "sine=frequency=$tone:sample_rate=48000" -t 2 \
    -c:v libx264 -pix_fmt yuv420p -preset ultrafast -c:a aac -b:a 80k \
    -movflags +faststart "$OUT/qa-timeline-$variant.mp4"
done
# A real long waveform fixture: silence followed by a late 440 Hz tone.
ffmpeg -hide_banner -loglevel error -nostdin -y \
 -f lavfi -i color=c=gray:size=160x160:rate=12 \
 -f lavfi -i 'aevalsrc=if(lt(t\,16)\,0\,0.1*sin(2*PI*440*t)):s=48000' \
 -t 20 -c:v libx264 -pix_fmt yuv420p -preset ultrafast -c:a aac -b:a 80k \
 -movflags +faststart "$OUT/qa-waveform-late.mp4"
# Extended real encoded H.264/AAC is slow enough to exercise non-zero
# asynchronous Media3 progress before cancellation on the API35 emulator.
ffmpeg -hide_banner -loglevel error -nostdin -y \
 -f lavfi -i testsrc2=size=320x568:rate=24 -f lavfi -i sine=frequency=620:sample_rate=48000 \
 -t 12 -c:v libx264 -pix_fmt yuv420p -preset ultrafast -crf 29 -c:a aac -b:a 80k \
 -movflags +faststart "$OUT/qa-h264-extended.mp4"
# Monochrome video isolates colored caption pixels without relying on a blank frame.
ffmpeg -hide_banner -loglevel error -nostdin -y \
 -f lavfi -i 'testsrc2=size=320x568:rate=24,format=gray,format=yuv420p' \
 -f lavfi -i sine=frequency=440:sample_rate=48000 -t 1.2 \
 -c:v libx264 -pix_fmt yuv420p -preset ultrafast -c:a aac -b:a 96k \
 -movflags +faststart "$OUT/qa-caption.mp4"
ffmpeg -hide_banner -loglevel error -nostdin -y -display_rotation 90 \
 -i "$OUT/qa-caption.mp4" -c copy "$OUT/qa-caption-rotated.mp4"
ffmpeg -hide_banner -loglevel error -nostdin -y -i "$OUT/qa-caption.mp4" \
 -map 0:v:0 -c:v copy -an "$OUT/qa-caption-silent.mp4"
# True 10-bit HEVC luma ramps. Neutral chroma makes caption ink measurable.
for transfer in arib-std-b67 smpte2084; do
 params='pools=none:frame-threads=1:bframes=0:log-level=error:colorprim=9:colormatrix=9:range=limited'
 if [[ $transfer == smpte2084 ]]; then
   params+=':transfer=16:hdr10=1:master-display=G(8500,39850)B(6550,2300)R(35400,14600)WP(15635,16450)L(10000000,50):max-cll=1000,400'
 else
   params+=':transfer=18'
 fi
 ffmpeg -hide_banner -loglevel error -nostdin -y -filter_threads 1 \
  -f lavfi -i "nullsrc=size=320x544:rate=24,format=yuv420p10le,geq=lum='64+790*X/W':cb=512:cr=512" \
  -f lavfi -i sine=frequency=330:sample_rate=48000 -t 1.2 \
  -c:v libx265 -preset ultrafast -x265-params "$params" -pix_fmt yuv420p10le \
  -color_range tv -color_primaries bt2020 -color_trc "$transfer" -colorspace bt2020nc \
  -c:a aac -b:a 80k -tag:v hvc1 -movflags +faststart "$OUT/qa-caption-$transfer.mp4"
done
printf 'invalid media\n' > "$OUT/qa-invalid.mp4"
python3 - "$OUT" <<'PY'
import json, pathlib, subprocess, sys
root=pathlib.Path(sys.argv[1])
for name in ('qa-h264-aac.mp4','qa-h264-silent.mp4','qa-rotated.mp4'):
    path=root/name
    data=json.loads(subprocess.check_output(['ffprobe','-v','error','-show_streams','-show_format','-of','json',str(path)]))
    codecs={(s['codec_type'],s['codec_name']) for s in data['streams']}
    assert ('video','h264') in codecs,(name,codecs)
    assert (('audio','aac') in codecs)==(name!='qa-h264-silent.mp4'),(name,codecs)
    assert 1.8<=float(data['format']['duration'])<=2.2
    if name=='qa-rotated.mp4':
        rotation=[x['rotation'] for s in data['streams'] for x in s.get('side_data_list',[]) if 'rotation' in x]
        assert rotation and abs(rotation[0])==90,rotation
    print(name,sorted(codecs),data['format']['duration'],path.stat().st_size)
for name,angle in (('qa-caption.mp4',0),('qa-caption-rotated.mp4',90),('qa-caption-silent.mp4',0)):
    data=json.loads(subprocess.check_output(['ffprobe','-v','error','-show_streams','-show_format','-of','json',str(root/name)]))
    expected={('video','h264')} if 'silent' in name else {('video','h264'),('audio','aac')}
    assert {(s['codec_type'],s['codec_name']) for s in data['streams']}==expected
    rotation=[d['rotation'] for s in data['streams'] for d in s.get('side_data_list',[]) if 'rotation' in d]
    assert (abs(rotation[0]) if rotation else 0)==angle
    assert 1.1<=float(data['format']['duration'])<=1.3
    print(name,'caption-fixture',angle)
for transfer in ('arib-std-b67','smpte2084'):
    path=root/f'qa-caption-{transfer}.mp4'
    data=json.loads(subprocess.check_output(['ffprobe','-v','error','-show_streams','-show_format','-of','json',str(path)]))
    video,audio=data['streams']
    assert (video['codec_name'],video['profile'],video['pix_fmt'])==('hevc','Main 10','yuv420p10le')
    assert (video['color_primaries'],video['color_transfer'],video['color_space'])==('bt2020',transfer,'bt2020nc')
    assert audio['codec_name']=='aac' and 1.1<=float(data['format']['duration'])<=1.3
    if transfer=='smpte2084':
        first=json.loads(subprocess.check_output(['ffprobe','-v','error','-select_streams','v:0',
            '-read_intervals','%+#1','-show_frames','-of','json',str(path)]))['frames'][0]
        types={item['side_data_type'] for item in first['side_data_list']}
        assert {'Mastering display metadata','Content light level metadata'}<=types,types
    print(path.name,video['profile'],video['pix_fmt'],transfer,path.stat().st_size)
for variant in ('a','b'):
    path=root/f'qa-timeline-{variant}.mp4'
    data=json.loads(subprocess.check_output(['ffprobe','-v','error','-show_streams','-show_format','-of','json',str(path)]))
    assert {(s['codec_type'],s['codec_name']) for s in data['streams']}=={('video','h264'),('audio','aac')}
    assert 1.9<=float(data['format']['duration'])<=2.1
    print(path.name,'timeline markers and AAC',path.stat().st_size)
long_path=root/'qa-h264-extended.mp4'
long_data=json.loads(subprocess.check_output(['ffprobe','-v','error','-show_streams','-show_format','-of','json',str(long_path)]))
assert {('video','h264'),('audio','aac')} <= {(s['codec_type'],s['codec_name']) for s in long_data['streams']}
assert 11.8<=float(long_data['format']['duration'])<=12.2
print(long_path.name,'extended-media3-cancel-fixture',long_path.stat().st_size)
PY

mkdir -p qa-provider/src/main/assets
cp "$OUT/qa-h264-extended.mp4" qa-provider/src/main/assets/
