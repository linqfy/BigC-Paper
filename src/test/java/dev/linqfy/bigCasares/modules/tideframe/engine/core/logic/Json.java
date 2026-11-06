package dev.linqfy.bigCasares.modules.tideframe.engine.core.logic;

/** Test helper: the codec's own bounded JSON reader. */
final class Json {
    private Json() { }
    static Object parse(String json) { return dev.linqfy.bigCasares.modules.tideframe.engine.edit.ScreenCodec.parse(json); }
}
