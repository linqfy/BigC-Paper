package dev.linqfy.bigCasares.modules.tideframe.engine.pack;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;

/** Fixed-resource HTTP delivery. Lifecycle and file reads belong on an async executor. */
public final class PackService implements AutoCloseable {
    private static final int MAX_BYTES = HudAssets.MAX_PACK_BYTES;
    private final InetSocketAddress address;
    private final Executor executor;
    private final Semaphore active = new Semaphore(8);
    private HttpServer server;
    private boolean closed;

    public PackService(InetSocketAddress address, Executor executor) {
        this.address = Objects.requireNonNull(address); this.executor = Objects.requireNonNull(executor);
    }

    public synchronized void start(PackGenerator.Artifact artifact) throws IOException {
        if (closed || server != null) throw new IllegalStateException("Pack service already started or closed");
        byte[] data;
        try (var input = Files.newInputStream(artifact.path())) { data = input.readNBytes(MAX_BYTES + 1); }
        if (data.length > MAX_BYTES || data.length == 0) throw new IOException("Pack size must be 1 byte through 200 MiB");
        if (!MessageDigest.isEqual(PackGenerator.digest("SHA-1", data), artifact.sha1())
                || !MessageDigest.isEqual(PackGenerator.digest("SHA-256", data), artifact.sha256()))
            throw new IOException("Resource pack file does not match its generated digests");
        String etag = "\"" + HexFormat.of().formatHex(artifact.sha256()) + "\"";
        HttpServer candidate = HttpServer.create(address, 16);
        try {
            candidate.setExecutor(executor);
            candidate.createContext("/", exchange -> serve(exchange, data, etag));
            candidate.start(); server = candidate;
        } catch (RuntimeException | Error failure) { candidate.stop(0); throw failure; }
    }

    private void serve(HttpExchange exchange, byte[] data, String etag) throws IOException {
        if (!active.tryAcquire()) { try (exchange) { reply(exchange, 503, "Busy"); } return; }
        try (exchange) {
            if (!"/packs/tideframe.zip".equals(exchange.getRequestURI().getRawPath())
                    || exchange.getRequestURI().getRawQuery() != null) {
                reply(exchange, 404, "Not found"); return;
            }
            String method = exchange.getRequestMethod();
            if (!method.equals("GET") && !method.equals("HEAD")) {
                exchange.getResponseHeaders().set("Allow", "GET, HEAD");
                reply(exchange, 405, "Method not allowed"); return;
            }
            var headers = exchange.getResponseHeaders();
            headers.set("Content-Type", "application/zip"); headers.set("ETag", etag);
            headers.set("Cache-Control", "public, max-age=3600");
            headers.set("X-Content-Type-Options", "nosniff");
            if (etag.equals(exchange.getRequestHeaders().getFirst("If-None-Match"))) {
                exchange.sendResponseHeaders(304, -1); return;
            }
            headers.set("Content-Length", Integer.toString(data.length));
            if (method.equals("HEAD")) exchange.sendResponseHeaders(200, -1);
            else { exchange.sendResponseHeaders(200, data.length); exchange.getResponseBody().write(data); }
        } finally { active.release(); }
    }

    private static void reply(HttpExchange exchange, int status, String message) throws IOException {
        byte[] body = message.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.getResponseHeaders().set("Content-Length", Integer.toString(body.length));
        if (exchange.getRequestMethod().equals("HEAD")) exchange.sendResponseHeaders(status, -1);
        else { exchange.sendResponseHeaders(status, body.length); exchange.getResponseBody().write(body); }
    }

    public synchronized int port() {
        if (server == null) throw new IllegalStateException("Pack service is not running");
        return server.getAddress().getPort();
    }

    /** Does not own or shut down the supplied executor. */
    @Override public synchronized void close() {
        closed = true;
        if (server != null) { server.stop(0); server = null; }
    }
}
