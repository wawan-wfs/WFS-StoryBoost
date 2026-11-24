package com.wfs.storyboost;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;

final class TimelinePreviewCacheTest {
    private static int checks;
    private static void check(boolean value){
        if(!value)throw new AssertionError("Preview cache check "+(checks+1));
        checks++;
    }
    public static void main(String[] args)throws Exception{
        Path dir=Files.createTempDirectory("wfs-preview-test-");
        try{
            String prefix=TimelinePreviewCache.PREFIX;
            Path stale=Files.write(dir.resolve(prefix+UUID.randomUUID()+".mp4"),new byte[]{1});
            Path partial=Files.createFile(dir.resolve(prefix+UUID.randomUUID()+".mp4"));
            Path export=Files.write(dir.resolve("wfs_join_"+UUID.randomUUID()+".mp4"),new byte[]{2});
            Path unrelated=Files.write(dir.resolve(prefix+"not-a-uuid.mp4"),new byte[]{3});
            Path extension=Files.write(dir.resolve(prefix+UUID.randomUUID()+".jpg"),new byte[]{4});
            Path folder=Files.createDirectory(dir.resolve(prefix+UUID.randomUUID()+".mp4"));
            Path nested=Files.write(folder.resolve(prefix+UUID.randomUUID()+".mp4"),new byte[]{5});
            Path link=Files.createSymbolicLink(dir.resolve(prefix+UUID.randomUUID()+".mp4"),export);
            TimelinePreviewCache.Result result=TimelinePreviewCache.pruneAtProcessStart(dir.toFile());
            check(result.removed==2);check(result.failed==0);
            check(!Files.exists(stale));check(!Files.exists(partial));
            check(Files.readAllBytes(export)[0]==2);check(Files.readAllBytes(unrelated)[0]==3);
            check(Files.readAllBytes(extension)[0]==4);check(Files.isDirectory(folder));
            check(Files.readAllBytes(nested)[0]==5);check(Files.isSymbolicLink(link));
            check(TimelinePreviewCache.pruneAtProcessStart(dir.toFile()).removed==0);
            check(TimelinePreviewCache.pruneAtProcessStart(dir.resolve("missing").toFile()).failed==1);
            System.out.println("PASS "+checks+" preview cache assertions");
        }finally{
            try(java.util.stream.Stream<Path> files=Files.walk(dir)){
                for(Path p:files.sorted(Comparator.reverseOrder()).toArray(Path[]::new))Files.deleteIfExists(p);
            }
        }
    }
}
