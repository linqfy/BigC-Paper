package dev.linqfy.bigCasares.modules.auth.premium;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Cached "is this name a premium account?" lookup used to offer premium auto-login to new players. */
public final class MojangProfiles {

    private static final long CACHE_MILLIS = 3_600_000L;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final String baseUrl;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    public MojangProfiles(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /** Blocking lookup; any network failure counts as "not premium" so the login falls back safely. */
    public boolean exists(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        long now = System.currentTimeMillis();
        Cached cached = cache.get(key);
        if (cached != null && now - cached.atMillis() < CACHE_MILLIS) {
            return cached.exists();
        }
        boolean exists;
        try {
            HttpResponse<Void> response = client.send(HttpRequest.newBuilder(URI.create(
                    baseUrl + "/users/profiles/minecraft/" + URLEncoder.encode(name, StandardCharsets.UTF_8)))
                .timeout(Duration.ofSeconds(3)).GET().build(), HttpResponse.BodyHandlers.discarding());
            exists = response.statusCode() == 200;
        } catch (java.io.IOException ex) {
            return false;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
        if (cache.size() > 10_000) {
            cache.clear();
        }
        cache.put(key, new Cached(exists, now));
        return exists;
    }

    private record Cached(boolean exists, long atMillis) {
    }
}
