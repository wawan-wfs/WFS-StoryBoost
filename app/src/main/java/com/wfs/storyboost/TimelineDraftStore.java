package com.wfs.storyboost;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;

/** One private, versioned timeline slot. Legacy single-source drafts are independent. */
final class TimelineDraftStore {
    private static final String PREFS="one-track-timeline",KEY="draft";
    private static final int VERSION=9,MAX_CHARS=90000;
    private TimelineDraftStore(){}
    private static SharedPreferences prefs(Context c){return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);}

    static boolean exists(Context c){return prefs(c).contains(KEY);}
    static TimelineProject load(Context c){
        try{
            String raw=prefs(c).getString(KEY,null);
            if(raw==null||raw.length()>MAX_CHARS)return null;
            JSONObject j=new JSONObject(raw);
            int version=strictInt(j,"version");
            if(version<1||version>VERSION)return null;
            JSONArray entries=j.getJSONArray("clips");
            if(entries.length()>TimelineProject.MAX_CLIPS)return null;
            ArrayList<TimelineProject.Clip> clips=new ArrayList<>();
            for(int i=0;i<entries.length();i++){
                JSONObject e=entries.getJSONObject(i);
                ClipAdjustments edits=ClipAdjustments.NEUTRAL;
                if(version>=2){
                    JSONObject a=e.getJSONObject("adjustments");
                    Object mute=a.get("mute");
                    if(!(mute instanceof Boolean))throw new JSONException("Invalid mute");
                    edits=new ClipAdjustments(strictInt(a,"look"),strictInt(a,"brightness"),
                            strictInt(a,"contrast"),strictInt(a,"saturation"),(Boolean)mute);
                }
                clips.add(new TimelineProject.Clip(e.getString("id"),e.getString("uri"),e.getLong("duration"),
                        e.getLong("inMs"),e.getLong("outMs"),edits,version>=4?strictInt(e,"gainStart"):100,version>=4?strictInt(e,"gainEnd"):100,version>=5?strictInt(e,"zoomStart"):100,version>=5?strictInt(e,"zoomEnd"):100));
            }
            TimelineProject.Caption caption=null;
            JSONObject cap=j.optJSONObject("caption");
            if(cap!=null)caption=new TimelineProject.Caption(cap.getString("text"),cap.getLong("inMs"),
                    cap.getLong("outMs"),cap.getInt("color"));
            TimelineProject.Audio audio=null,voice=null;int fade=0;
            if(version>=3){
                fade=strictInt(j,"fadeMs");
                if(j.has("audio")){
                    JSONObject a=j.getJSONObject("audio");
                    audio=new TimelineProject.Audio(a.getString("uri"),strictLong(a,"duration"),
                            strictLong(a,"inMs"),strictLong(a,"outMs"),strictLong(a,"startMs"),strictInt(a,"volume"));
                }
            }
            if(version>=4&&j.has("voice")){
                JSONObject a=j.getJSONObject("voice");
                voice=new TimelineProject.Audio(a.getString("uri"),strictLong(a,"duration"),strictLong(a,"inMs"),
                        strictLong(a,"outMs"),strictLong(a,"startMs"),strictInt(a,"volume"));
            }
            PictureLayer picture=null;
            if(version==7||(version>=8&&j.has("picture"))){
                JSONObject a=j.getJSONObject("picture");
                picture=new PictureLayer(a.getString("uri"),strictLong(a,"duration"),strictLong(a,"inMs"),strictLong(a,"outMs"),
                        strictLong(a,"startMs"),strictInt(a,"scale"),strictInt(a,"opacity"),strictInt(a,"xStart"),strictInt(a,"yStart"),strictInt(a,"xEnd"),strictInt(a,"yEnd"),version>=8?strictInt(a,"curve"):PictureLayer.LINEAR,version>=8?strictLong(a,"motionDuration"):strictLong(a,"outMs")-strictLong(a,"inMs"));
            }
            PictureLayer picture2=version>=9&&j.has("picture2")?readPicture(j.getJSONObject("picture2"),version):null;
            ArrayList<CutTransition> cuts=new ArrayList<>();
            if(version>=8){
                JSONArray list=j.getJSONArray("cuts");
                if(list.length()>Math.max(0,clips.size()-1)||(version==8&&list.length()==0&&(picture==null||picture.curve==PictureLayer.LINEAR)))throw new JSONException("Invalid cut count");
                for(int i=0;i<list.length();i++){
                    JSONObject entry=list.getJSONObject(i);
                    cuts.add(new CutTransition(entry.getString("left"),entry.getString("right"),strictInt(entry,"ms"),version>=9?strictInt(entry,"curve"):0));
                }
            }
            if(version==9&&picture2==null&&cuts.stream().noneMatch(cut->cut.curve!=0))throw new JSONException("Invalid v9 draft");
            return new TimelineProject(clips,caption,strictInt(j,"quality"),audio,voice,fade,version>=6?strictInt(j,"crossfadeMs"):0,picture,cuts,picture2);
        }catch(JSONException|IllegalArgumentException|ClassCastException corrupt){return null;}
    }

    private static PictureLayer readPicture(JSONObject a,int version)throws JSONException{
        return new PictureLayer(a.getString("uri"),strictLong(a,"duration"),strictLong(a,"inMs"),strictLong(a,"outMs"),
            strictLong(a,"startMs"),strictInt(a,"scale"),strictInt(a,"opacity"),strictInt(a,"xStart"),strictInt(a,"yStart"),
            strictInt(a,"xEnd"),strictInt(a,"yEnd"),version>=8?strictInt(a,"curve"):PictureLayer.LINEAR,
            version>=8?strictLong(a,"motionDuration"):strictLong(a,"outMs")-strictLong(a,"inMs"));
    }
    private static JSONObject writePicture(PictureLayer a,int schema)throws JSONException{
        JSONObject picture=new JSONObject().put("uri",a.uri).put("duration",a.duration).put("inMs",a.in)
            .put("outMs",a.out).put("startMs",a.start).put("scale",a.scale).put("opacity",a.opacity)
            .put("xStart",a.xStart).put("yStart",a.yStart).put("xEnd",a.xEnd).put("yEnd",a.yEnd);
        if(schema>=8)picture.put("curve",a.curve).put("motionDuration",a.motionDuration);
        return picture;
    }
    private static long strictLong(JSONObject j,String key)throws JSONException{
        Object value=j.get(key);
        if(!(value instanceof Integer)&&!(value instanceof Long))throw new JSONException("Invalid long "+key);
        return ((Number)value).longValue();
    }
    private static int strictInt(JSONObject j,String key)throws JSONException{
        Object value=j.get(key);
        if(!(value instanceof Number))throw new JSONException("Invalid integer "+key);
        double number=((Number)value).doubleValue();
        if(!Double.isFinite(number)||number!=Math.rint(number)||number<Integer.MIN_VALUE||number>Integer.MAX_VALUE)
            throw new JSONException("Invalid integer "+key);
        return (int)number;
    }

    static boolean save(Context c,TimelineProject project){
        try{
            int schema=project.picture2!=null||project.hasCurvedCuts()?VERSION:!project.cuts.isEmpty()||(project.picture!=null&&project.picture.curve!=PictureLayer.LINEAR)?8:project.picture!=null?7:project.crossfadeMs>0?6:project.hasZoom()?5:project.voice!=null||project.hasKeyframes()?4:project.audio!=null||project.fadeMs!=0?3:2;
            JSONArray entries=new JSONArray();
            for(TimelineProject.Clip clip:project.clips){
                JSONObject entry=new JSONObject().put("id",clip.id).put("uri",clip.uri)
                        .put("duration",clip.duration).put("inMs",clip.in).put("outMs",clip.out)
                        .put("adjustments",new JSONObject().put("look",clip.adjustments.look)
                                .put("brightness",clip.adjustments.brightness).put("contrast",clip.adjustments.contrast)
                                .put("saturation",clip.adjustments.saturation).put("mute",clip.adjustments.mute));
                if(schema>=4)entry.put("gainStart",clip.gainStart).put("gainEnd",clip.gainEnd);
                if(schema>=5)entry.put("zoomStart",clip.zoomStart).put("zoomEnd",clip.zoomEnd);
                entries.put(entry);
            }
            // Preserve v2 compatibility for projects without new soundtrack/fade data.
            JSONObject doc=new JSONObject().put("version",schema).put("clips",entries)
                    .put("quality",project.quality);
            if(schema>=6)doc.put("crossfadeMs",project.crossfadeMs);
            if(schema>=8){
                JSONArray cuts=new JSONArray();for(CutTransition cut:project.cuts){JSONObject item=new JSONObject().put("left",cut.left).put("right",cut.right).put("ms",cut.ms);if(schema>=9)item.put("curve",cut.curve);cuts.put(item);}doc.put("cuts",cuts);
            }
            if(project.picture!=null)doc.put("picture",writePicture(project.picture,schema));
            if(project.picture2!=null)doc.put("picture2",writePicture(project.picture2,schema));
            if(schema>=3){
                doc.put("fadeMs",project.fadeMs);
                if(project.audio!=null){
                    TimelineProject.Audio a=project.audio;
                    doc.put("audio",new JSONObject().put("uri",a.uri).put("duration",a.duration)
                            .put("inMs",a.in).put("outMs",a.out).put("startMs",a.start).put("volume",a.volume));
                }
            }
            if(project.voice!=null){
                TimelineProject.Audio a=project.voice;
                doc.put("voice",new JSONObject().put("uri",a.uri).put("duration",a.duration).put("inMs",a.in)
                        .put("outMs",a.out).put("startMs",a.start).put("volume",a.volume));
            }
            if(project.caption!=null){
                TimelineProject.Caption cap=project.caption;
                doc.put("caption",new JSONObject().put("text",cap.text).put("inMs",cap.in)
                        .put("outMs",cap.out).put("color",cap.color));
            }
            String raw=doc.toString();
            return raw.length()<=MAX_CHARS&&prefs(c).edit().putString(KEY,raw).commit();
        }catch(JSONException e){return false;}
    }

    /** Explicit v1 single-source -> timeline conversion; never rewrites the original draft. */
    static TimelineProject fromSingleSource(Uri source,ExportOptions settings,long duration){
        long end=settings.trimEndMs==0?duration:Math.min(duration,settings.trimEndMs);
        TimelineProject.Clip clip=TimelineProject.Clip.create(source.toString(),duration,settings.trimStartMs,end);
        TimelineProject.Caption caption=settings.hasText()
                ?new TimelineProject.Caption(settings.caption,0,clip.length(),settings.captionColor):null;
        return new TimelineProject(Collections.singletonList(clip),caption,settings.qualityMode);
    }

    static boolean hasReadGrant(Context c,Uri uri){
        for(android.content.UriPermission p:c.getContentResolver().getPersistedUriPermissions())
            if(p.isReadPermission()&&uri.equals(p.getUri()))return true;
        return false;
    }
    static void clear(Context c){prefs(c).edit().remove(KEY).commit();}
}
