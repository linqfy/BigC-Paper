package dev.linqfy.bigCasares.modules.auth.premium;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Asks Mojang's session server whether a client really joined with the given server hash. */
@FunctionalInterface
public interface MojangSessionClient {

    String DEFAULT_BASE_URL = "https://sessionserver.mojang.com";

    Optional<PremiumProfile> hasJoined(String username, String serverHash) throws IOException, InterruptedException;

    static MojangSessionClient http(String baseUrl) {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return (username, serverHash) -> {
            URI uri = URI.create(base + "/session/minecraft/hasJoined?username="
                + URLEncoder.encode(username, StandardCharsets.UTF_8)
                + "&serverId=" + URLEncoder.encode(serverHash, StandardCharsets.UTF_8));
            HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(8)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 || response.body().isBlank()) {
                return Optional.empty();
            }
            return Optional.of(parse(response.body()));
        };
    }

    static PremiumProfile parse(String json) throws IOException {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            String id = root.get("id").getAsString();
            UUID uuid = UUID.fromString(id.replaceFirst(
                "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)", "$1-$2-$3-$4-$5"));
            List<PremiumProfile.Property> properties = new ArrayList<>();
            JsonArray array = root.has("properties") ? root.getAsJsonArray("properties") : new JsonArray();
            for (JsonElement element : array) {
                JsonObject property = element.getAsJsonObject();
                properties.add(new PremiumProfile.Property(
                    property.get("name").getAsString(),
                    property.get("value").getAsString(),
                    property.has("signature") ? property.get("signature").getAsString() : null));
            }
            return new PremiumProfile(uuid, root.get("name").getAsString(), List.copyOf(properties));
        } catch (RuntimeException ex) {
            throw new IOException("Unexpected session server response", ex);
        }
    }
}
