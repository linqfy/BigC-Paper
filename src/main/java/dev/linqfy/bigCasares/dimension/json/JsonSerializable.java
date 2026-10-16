package dev.linqfy.bigCasares.dimension.json;

import com.google.gson.JsonElement;

/** A value that knows its vanilla data-driven JSON representation. */
@FunctionalInterface
public interface JsonSerializable {

    JsonElement toJson();
}
