package com.wfs.storyboost;

import android.app.Application;
import android.util.Log;

/** Single-process startup recovery; never touches Gallery, source URIs or drafts. */
public final class StoryBoostApplication extends Application {
    @Override public void onCreate(){
        super.onCreate();
        TimelinePreviewCache.Result result=TimelinePreviewCache.pruneAtProcessStart(getCacheDir());
        Log.i("WfsTimelinePreviewQA","startupRemoved="+result.removed+" startupFailures="+result.failed);
    }
}
