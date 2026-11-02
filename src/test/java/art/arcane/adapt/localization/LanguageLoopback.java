package art.arcane.adapt.localization;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

final class LanguageLoopback implements AutoCloseable {
  static final String REVISION_ONE = "1111111111111111111111111111111111111111";
  static final String REVISION_TWO = "2222222222222222222222222222222222222222";

  private final HttpServer server;
  private final AtomicReference<String> revision = new AtomicReference<>(REVISION_ONE);
  private final Map<String, String> files = new ConcurrentHashMap<>();
  private final AtomicInteger requests = new AtomicInteger();
  private volatile int fileStatus = 200;

  LanguageLoopback() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/api/", this::api);
    server.createContext("/raw/", this::raw);
    server.start();
  }

  void connect() {
    URI root = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
    AdaptLanguage.connectUpstream(root.resolve("api/"), root.resolve("raw/"), Duration.ZERO);
  }

  void publish(String sha, String locale, String content) {
    files.put(sha + "/" + locale, content);
  }

  void revision(String sha) {
    revision.set(sha);
  }

  void fileStatus(int status) {
    fileStatus = status;
  }

  int requests() {
    return requests.get();
  }

  @Override
  public void close() {
    server.stop(0);
  }

  private void api(HttpExchange exchange) throws IOException {
    requests.incrementAndGet();
    String etag = "\"" + revision.get() + "\"";
    if (etag.equals(exchange.getRequestHeaders().getFirst("If-None-Match"))) {
      exchange.sendResponseHeaders(304, -1);
      exchange.close();
      return;
    }
    exchange.getResponseHeaders().set("ETag", etag);
    respond(exchange, 200, "[{\"sha\":\"" + revision.get() + "\"}]");
  }

  private void raw(HttpExchange exchange) throws IOException {
    requests.incrementAndGet();
    String[] parts = exchange.getRequestURI().getPath().split("/");
    String content = files.get(parts[4] + "/" + parts[parts.length - 1].replace(".toml", ""));
    if (fileStatus != 200) {
      respond(exchange, fileStatus, "Unavailable");
      return;
    }
    if (content == null) {
      exchange.sendResponseHeaders(404, -1);
      exchange.close();
      return;
    }
    respond(exchange, 200, content);
  }

  private void respond(HttpExchange exchange, int status, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.sendResponseHeaders(status, bytes.length);
    exchange.getResponseBody().write(bytes);
    exchange.close();
  }
}
