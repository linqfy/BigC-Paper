package dev.linqfy.bigCasares.modules.tideframe.engine.edit;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.util.*;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.Screen;
public final class ScreenStore implements ScreenStorage {
    private final Path folder;
    public ScreenStore(Path folder) {this.folder=folder.toAbsolutePath().normalize();}
    private Path file(String id) throws IOException {
        if(id==null || !id.matches("[a-z][a-z0-9_-]{0,31}"))throw new IllegalArgumentException("Invalid screen id");
        if(Files.isSymbolicLink(folder))throw new IOException("Screen directory must not be a symbolic link");
        Path file=folder.resolve(id+".json");if(Files.isSymbolicLink(file))throw new IOException("Screen file must not be a symbolic link");return file;
    }
    public void save(Screen screen) throws IOException {
        Files.createDirectories(folder);Path target=file(screen.id());Path temp=Files.createTempFile(folder,".screen-",".tmp");
        try{Files.writeString(temp,ScreenCodec.encode(screen),StandardCharsets.UTF_8);
            try{Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException e){Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(temp);}
    }
    /** Deletes screens/&lt;id&gt;.json; false when there was no such file. */
    public boolean delete(String id) throws IOException { return Files.deleteIfExists(file(id)); }
    /** When screens/&lt;id&gt;.json last changed, in milliseconds (the web editor matches it to the file it published). */
    public long stamp(String id) throws IOException { return Files.getLastModifiedTime(file(id)).toMillis(); }
    public Screen load(String id) throws IOException {
        byte[] data;try(var stream=Files.newInputStream(file(id))){data=stream.readNBytes(ScreenCodec.MAX_JSON_BYTES+1);}
        if(data.length>ScreenCodec.MAX_JSON_BYTES)throw new IllegalArgumentException("Screen file exceeds 128 MiB");
        Screen screen=ScreenCodec.decode(new String(data,StandardCharsets.UTF_8));
        if(!screen.id().equals(id))throw new IllegalArgumentException("File name must match screen id");return screen;
    }
    public List<String> ids() throws IOException {
        if(Files.isSymbolicLink(folder))throw new IOException("Screen directory must not be a symbolic link");
        if(!Files.exists(folder))return List.of();
        var ids=new ArrayList<String>();
        try(var entries=Files.newDirectoryStream(folder,"*.json")){
            for(Path entry:entries){
                String name=entry.getFileName().toString();
                if(!name.matches("[a-z][a-z0-9_-]{0,31}\\.json"))continue;
                if(Files.isSymbolicLink(entry))throw new IOException("Screen file must not be a symbolic link");
                if(!Files.isRegularFile(entry))continue;
                ids.add(name.substring(0,name.length()-5));
                if(ids.size()>96)throw new IllegalArgumentException("Screen library exceeds 96 files");
            }
        }
        Collections.sort(ids);return List.copyOf(ids);
    }
}
