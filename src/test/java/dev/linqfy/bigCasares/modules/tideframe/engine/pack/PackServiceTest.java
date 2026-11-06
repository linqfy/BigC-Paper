package dev.linqfy.bigCasares.modules.tideframe.engine.pack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class PackServiceTest {
    @TempDir Path folder;

    @Test void actualHttpGetAndHeadServeOnlyGeneratedBytesWithCachingAndExplicitLength() throws Exception {
        var artifact = PackGenerator.generate(folder);
        try (var executor = Executors.newFixedThreadPool(2);
             var service = new PackService(new InetSocketAddress("127.0.0.1", 0), executor);
             var client = HttpClient.newHttpClient()) {
            service.start(artifact);
            URI uri = URI.create("http://127.0.0.1:" + service.port() + "/packs/tideframe.zip");
            var get = client.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(3)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(200, get.statusCode());
            assertArrayEquals(Files.readAllBytes(artifact.path()), get.body());
            assertEquals("application/zip", get.headers().firstValue("Content-Type").orElseThrow());
            assertEquals(Integer.toString(get.body().length), get.headers().firstValue("Content-Length").orElseThrow());
            String etag = "\"" + HexFormat.of().formatHex(artifact.sha256()) + "\"";
            assertEquals(etag, get.headers().firstValue("ETag").orElseThrow());
            var head = client.send(HttpRequest.newBuilder(uri).method("HEAD", HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(200, head.statusCode()); assertEquals(0, head.body().length);
            assertEquals(Integer.toString(get.body().length), head.headers().firstValue("Content-Length").orElseThrow());
            var cached = client.send(HttpRequest.newBuilder(uri).header("If-None-Match", etag).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(304, cached.statusCode()); assertEquals(0, cached.body().length);
            for (String path : new String[]{"/", "/packs/../secret", "/packs/%2e%2e/secret", "/packs/tideframe.zip/", "/packs/%74ideframe.zip"}) {
                var response = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + service.port() + path)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
                assertEquals(404, response.statusCode(), path);
            }
            var post = client.send(HttpRequest.newBuilder(uri).POST(HttpRequest.BodyPublishers.ofString("ignored")).build(), HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(405, post.statusCode());
            assertEquals("GET, HEAD", post.headers().firstValue("Allow").orElseThrow());
            assertThrows(IllegalStateException.class, () -> service.start(artifact));
        }
    }

    @Test void alteredFileIsRejectedBeforeHttpStartsAndCloseCanBeRepeated() throws Exception {
        var artifact = PackGenerator.generate(folder);
        Files.writeString(artifact.path(), "changed");
        try (var executor = Executors.newSingleThreadExecutor();
             var service = new PackService(new InetSocketAddress("127.0.0.1", 0), executor)) {
            assertThrows(java.io.IOException.class, () -> service.start(artifact));
            service.close(); service.close();
        }
    }
}
