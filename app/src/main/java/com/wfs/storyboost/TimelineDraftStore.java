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
    private static final int VERSION=1,MAX_CHARS=70000;
    private TimelineDraftStore(){}
    private static SharedPreferences prefs(Context c){return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);}

    static boolean exists(Context c){return prefs(c).contains(KEY);}
    static TimelineProject load(Context c){
        try{
            String raw=prefs(c).getString(KEY,null);
            if(raw==null||raw.length()>MAX_CHARS)return null;
            JSONObject j=new JSONObject(raw);
            if(j.getInt("version")!=VERSION)return null;
            JSONArray entries=j.getJSONArray("clips");
            if(entries.length()>TimelineProject.MAX_CLIPS)return null;
            ArrayList<TimelineProject.Clip> clips=new ArrayList<>();
            for(int i=0;i<entries.length();i++){
                JSONObject e=entries.getJSONObject(i);
                clips.add(new TimelineProject.Clip(e.getString("id"),e.getString("uri"),e.getLong("duration"),
                        e.getLong("inMs"),e.getLong("outMs")));
            }
            TimelineProject.Caption caption=null;
            JSONObject cap=j.optJSONObject("caption");
            if(cap!=null)caption=new TimelineProject.Caption(cap.getString("text"),cap.getLong("inMs"),
                    cap.getLong("outMs"),cap.getInt("color"));
            return new TimelineProject(clips,caption,j.getInt("quality"));
        }catch(JSONException|IllegalArgumentException|ClassCastException corrupt){return null;}
    }

    static boolean save(Context c,TimelineProject project){
        try{
            JSONArray entries=new JSONArray();
            for(TimelineProject.Clip clip:project.clips)
                entries.put(new JSONObject().put("id",clip.id).put("uri",clip.uri)
                        .put("duration",clip.duration).put("inMs",clip.in).put("outMs",clip.out));
            JSONObject doc=new JSONObject().put("version",VERSION).put("clips",entries)
                    .put("quality",project.quality);
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
