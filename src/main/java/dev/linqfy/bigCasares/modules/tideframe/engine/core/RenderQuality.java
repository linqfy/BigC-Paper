package dev.linqfy.bigCasares.modules.tideframe.engine.core;

/** Bounded source raster size. It does not specify the client's physical screen resolution. */
public record RenderQuality(String preset,int width,int height) {
    public RenderQuality {
        if (preset==null) throw new IllegalArgumentException("Missing render quality");
        boolean valid=switch(preset) {
            case "legacy" -> width==1024 && height==576;
            case "low" -> width==512 && height==288;
            case "medium" -> width==1920 && height==1080;
            case "high" -> width==2560 && height==1440;
            case "custom" -> width>=256 && width<=3840 && width%32==0 && height>=144 && height<=2160
                    && (long)width*9==(long)height*16;
            default -> false;
        };
        if(!valid) throw new IllegalArgumentException("Invalid render quality dimensions or preset");
    }
    public static RenderQuality legacy() { return new RenderQuality("legacy",1024,576); }
    public static RenderQuality low() { return new RenderQuality("low",512,288); }
    public static RenderQuality medium() { return new RenderQuality("medium",1920,1080); }
    public static RenderQuality high() { return new RenderQuality("high",2560,1440); }
    public static RenderQuality custom(int width,int height) { return new RenderQuality("custom",width,height); }
}
