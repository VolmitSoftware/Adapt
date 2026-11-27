package art.arcane.adapt.pack.link;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

/**
 * Hands out the URL clients download the pack from. Serving mode runs a small JDK HTTP listener that answers GET and
 * HEAD only on {@code /<random token>/<sha1>.zip}, one route per published artifact; anything else is a 404. A
 * replaced artifact keeps its route for {@link #RETIRED_MILLIS} so downloads already under way finish. External mode
 * opens nothing and returns the operator's URL with {@code {sha1}} filled in.
 */
public final class PackHost {
  public static final String SHA1_PLACEHOLDER = "{sha1}";
  static final long RETIRED_MILLIS = 120_000L;
  private static final String WILDCARD = "0.0.0.0";
  private static final int THREADS = 2;
  private static final int BACKLOG = 16;

  private final HttpServer server;
  private final ExecutorService executor;
  private final String bind;
  private final String publicUrl;
  private final String externalUrl;
  private final LongSupplier clock;
  private final SecureRandom random = new SecureRandom();
  private final Map<String, Route> routes = new ConcurrentHashMap<>();
  private volatile Route current;

  private PackHost(HttpServer server, ExecutorService executor, String bind, String publicUrl, String externalUrl,
                   LongSupplier clock) {
    this.server = server;
    this.executor = executor;
    this.bind = bind;
    this.publicUrl = publicUrl;
    this.externalUrl = externalUrl;
    this.clock = clock;
  }

  /**
   * Binds the listener. {@code publicUrl} is the base address players reach, such as {@code http://play.example:8164};
   * blank uses the bind address, or this machine's address for a wildcard bind.
   */
  public static PackHost serve(String bind, int port, String publicUrl) throws IOException {
    return serve(bind, port, publicUrl, System::currentTimeMillis);
  }

  static PackHost serve(String bind, int port, String publicUrl, LongSupplier clock) throws IOException {
    String address = bind == null || bind.isBlank() ? WILDCARD : bind.trim();
    HttpServer server = HttpServer.create(new InetSocketAddress(address, port), BACKLOG);
    AtomicInteger threads = new AtomicInteger();
    ExecutorService executor = Executors.newFixedThreadPool(THREADS, (Runnable task) -> {
      Thread thread = new Thread(task, "Adapt-Pack-HTTP-" + threads.incrementAndGet());
      thread.setDaemon(true);
      return thread;
    });
    PackHost host = new PackHost(server, executor, address, trimSlash(publicUrl), "", clock);
    server.setExecutor(executor);
    server.createContext("/", host::handle);
    server.start();
    return host;
  }

  /** A host that serves nothing; clients download from {@code url}. */
  public static PackHost external(String url) {
    String trimmed = url == null ? "" : url.trim();
    if (trimmed.isEmpty()) {
      throw new IllegalArgumentException("external pack url is blank");
    }
    return new PackHost(null, null, "", "", trimmed, System::currentTimeMillis);
  }

  /** Makes {@code artifact} the one new offers point at and returns its URL. */
  public synchronized String publish(PackArtifact artifact) {
    Objects.requireNonNull(artifact, "artifact");
    if (server == null) {
      return externalUrl.replace(SHA1_PLACEHOLDER, artifact.sha1Hex());
    }
    Route previous = current;
    if (previous != null && previous.artifact.equals(artifact)) {
      return url(previous);
    }
    Route route = new Route("/" + token() + "/" + artifact.sha1Hex() + ".zip", artifact);
    routes.put(route.path, route);
    current = route;
    if (previous != null) {
      previous.retiredAt = clock.getAsLong();
    }
    prune();
    return url(route);
  }

  /** Stops pointing at any artifact; routes already handed out retire as usual. */
  public synchronized void withdraw() {
    Route previous = current;
    current = null;
    if (previous != null) {
      previous.retiredAt = clock.getAsLong();
    }
    prune();
  }

  /** The bound port, or 0 in external mode. */
  public int port() {
    return server == null ? 0 : server.getAddress().getPort();
  }

  public synchronized void stop() {
    routes.clear();
    current = null;
    if (server != null) {
      server.stop(0);
      executor.shutdownNow();
    }
  }

  private void handle(HttpExchange exchange) throws IOException {
    try (exchange) {
      String method = exchange.getRequestMethod();
      boolean head = "HEAD".equals(method);
      if (!head && !"GET".equals(method)) {
        exchange.sendResponseHeaders(405, -1);
        return;
      }
      Route route = routes.get(exchange.getRequestURI().getPath());
      if (route == null || route.expired(clock.getAsLong())) {
        exchange.sendResponseHeaders(404, -1);
        return;
      }
      exchange.getResponseHeaders().set("Content-Type", "application/zip");
      exchange.getResponseHeaders().set("Cache-Control", "no-store");
      if (head) {
        exchange.getResponseHeaders().set("Content-Length", Integer.toString(route.artifact.size()));
        exchange.sendResponseHeaders(200, -1);
        return;
      }
      exchange.sendResponseHeaders(200, route.artifact.size());
      try (OutputStream body = exchange.getResponseBody()) {
        route.artifact.writeTo(body);
      }
    }
  }

  private void prune() {
    long now = clock.getAsLong();
    Iterator<Route> iterator = routes.values().iterator();
    while (iterator.hasNext()) {
      if (iterator.next().expired(now)) {
        iterator.remove();
      }
    }
  }

  private String url(Route route) {
    return base() + route.path;
  }

  private String base() {
    if (!publicUrl.isEmpty()) {
      return publicUrl;
    }
    return "http://" + host() + ":" + port();
  }

  private String host() {
    if (!WILDCARD.equals(bind)) {
      return bind;
    }
    try {
      return InetAddress.getLocalHost().getHostAddress();
    } catch (UnknownHostException unknown) {
      return "127.0.0.1";
    }
  }

  private String token() {
    byte[] bytes = new byte[16];
    random.nextBytes(bytes);
    return HexFormat.of().formatHex(bytes);
  }

  private static String trimSlash(String url) {
    String trimmed = url == null ? "" : url.trim();
    while (trimmed.endsWith("/")) {
      trimmed = trimmed.substring(0, trimmed.length() - 1);
    }
    return trimmed;
  }

  private static final class Route {
    private final String path;
    private final PackArtifact artifact;
    private volatile long retiredAt = -1L;

    private Route(String path, PackArtifact artifact) {
      this.path = path;
      this.artifact = artifact;
    }

    private boolean expired(long now) {
      long retired = retiredAt;
      return retired >= 0L && now - retired >= RETIRED_MILLIS;
    }
  }
}
