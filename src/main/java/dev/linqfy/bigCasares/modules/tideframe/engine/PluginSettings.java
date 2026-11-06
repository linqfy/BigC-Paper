package dev.linqfy.bigCasares.modules.tideframe.engine;

import java.net.URI;
import org.bukkit.configuration.ConfigurationSection;

public record PluginSettings(String bind, int port, String publicUrl, boolean required,
                             double distance, int predictionMs, double snapRadius, int elements,
                             java.util.List<Integer> displaySizes, int defaultSize) {
    /** 2x fills a 1920x1080 window at GUI scale Auto edge to edge (a few pixels of the frame fall off); quarters (4 = 1x). */
    public static final int DEFAULT_SIZE = 8;
    public PluginSettings(String bind, int port, String publicUrl, boolean required,
                          double distance, int predictionMs, double snapRadius, int elements) {
        this(bind,port,publicUrl,required,distance,predictionMs,snapRadius,elements,dev.linqfy.bigCasares.modules.tideframe.engine.pack.HudAssets.DEFAULT_SIZES,DEFAULT_SIZE);
    }
    public PluginSettings {
        var sizes=new java.util.TreeSet<Integer>(displaySizes); sizes.add(4);
        if(sizes.first()<4||sizes.last()>16||sizes.size()>8||defaultSize<4||defaultSize>16)
            throw new IllegalArgumentException("display.sizes must hold up to 8 sizes from 1 to 4 (steps of 0.25)");
        displaySizes=java.util.List.copyOf(sizes);
        URI url = URI.create(publicUrl);
        if (bind == null || bind.isBlank() || port < 1024 || port > 65535
                || !("http".equals(url.getScheme()) || "https".equals(url.getScheme()))
                || url.getHost() == null || url.getUserInfo() != null || !publicUrl.endsWith("/")
                || !Double.isFinite(distance) || distance < 1 || distance > 5
                || predictionMs < 0 || predictionMs > 75 || !Double.isFinite(snapRadius)
                || snapRadius < 0 || snapRadius > .3 || elements < 1 || elements > 128)
            throw new IllegalArgumentException("Invalid Tideframe configuration");
    }
    public static PluginSettings defaults() { return new PluginSettings("127.0.0.1",8136,"http://127.0.0.1:8136/packs/",false,2.5,35,.08,128); }
    /** {@code y}: the engine's settings ({@link EngineConfig#read()}). */
    public static PluginSettings load(ConfigurationSection y) {
        var d = defaults();
        var sizes=new java.util.ArrayList<Integer>();
        if(y.isList("display.sizes")) for(Object value:y.getList("display.sizes"))
            sizes.add(dev.linqfy.bigCasares.modules.tideframe.engine.core.ViewportFit.parseSize(String.valueOf(value)));
        else sizes.addAll(d.displaySizes);
        int defaultSize=y.isSet("display.default-size")?dev.linqfy.bigCasares.modules.tideframe.engine.core.ViewportFit.parseSize(String.valueOf(y.get("display.default-size"))):d.defaultSize;
        return new PluginSettings(y.getString("pack.bind",d.bind),y.getInt("pack.port",d.port),
                y.getString("pack.public-url",d.publicUrl),y.getBoolean("pack.required",d.required),
                y.getDouble("cursor.distance",d.distance),y.getInt("cursor.prediction-ms",d.predictionMs),
                y.getDouble("cursor.snap-radius",d.snapRadius),y.getInt("limits.elements",d.elements),sizes,defaultSize);
    }
}
