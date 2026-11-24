package com.wfs.storyboost;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.util.regex.Pattern;

/** Only private UUID-named preview MP4s belong to this disposable cache. */
final class TimelinePreviewCache {
    static final String PREFIX="wfs_timeline_preview_";
    private static final Pattern OWNED=Pattern.compile(
            PREFIX+"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.mp4");

    static final class Result {
        final int removed,failed;
        Result(int removed,int failed){this.removed=removed;this.failed=failed;}
    }

    /** Call exactly once at application startup, before any render can own a file. */
    static Result pruneAtProcessStart(File cache){
        int removed=0,failed=0;
        File[] files=cache.listFiles();
        if(files==null)return new Result(0,1);
        for(File file:files){
            if(!OWNED.matcher(file.getName()).matches())continue;
            try{
                if(Files.isRegularFile(file.toPath(),LinkOption.NOFOLLOW_LINKS)
                        &&Files.deleteIfExists(file.toPath()))removed++;
            }catch(IOException|SecurityException ex){failed++;}
        }
        return new Result(removed,failed);
    }
}
