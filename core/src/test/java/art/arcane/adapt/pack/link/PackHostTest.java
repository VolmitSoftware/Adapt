package art.arcane.adapt.pack.link;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PackHostTest {
  private final HttpClient client = HttpClient.newHttpClient();
  private final AtomicLong clock = new AtomicLong(1_000L);
  private PackHost host;

  @AfterEach
  void stop() {
    if (host != null) {
      host.stop();
    }
  }

  @Test
  void getServesThePackBytesAtItsRoute() throws Exception {
    host = PackHost.serve("127.0.0.1", 0, "", clock::get);
    PackArtifact artifact = TestPacks.pack("a");

    String url = host.publish(artifact);
    HttpResponse<byte[]> response = get(url, "GET");

    assertThat(url).startsWith("http://127.0.0.1:" + host.port() + "/").endsWith("/" + artifact.sha1Hex() + ".zip");
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue("Content-Type")).contains("application/zip");
    assertThat(response.headers().firstValueAsLong("Content-Length")).hasValue(artifact.size());
    assertThat(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(response.body())))
        .isEqualTo(artifact.sha1Hex());
  }

  @Test
  void headAnswersTheLengthWithoutABody() throws Exception {
    host = PackHost.serve("127.0.0.1", 0, "", clock::get);
    PackArtifact artifact = TestPacks.pack("a");

    HttpResponse<byte[]> response = get(host.publish(artifact), "HEAD");

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValueAsLong("Content-Length")).hasValue(artifact.size());
    assertThat(response.body()).isEmpty();
  }

  @Test
  void onlyTheSecretRouteIsServed() throws Exception {
    host = PackHost.serve("127.0.0.1", 0, "", clock::get);
    PackArtifact artifact = TestPacks.pack("a");
    host.publish(artifact);
    String base = "http://127.0.0.1:" + host.port();

    assertThat(get(base + "/", "GET").statusCode()).isEqualTo(404);
    assertThat(get(base + "/" + artifact.sha1Hex() + ".zip", "GET").statusCode()).isEqualTo(404);
    assertThat(get(base + "/00000000000000000000000000000000/" + artifact.sha1Hex() + ".zip", "GET").statusCode())
        .isEqualTo(404);
  }

  @Test
  void routesAreUnguessableAndChangeWithEachArtifact() throws Exception {
    host = PackHost.serve("127.0.0.1", 0, "", clock::get);

    String first = host.publish(TestPacks.pack("a"));
    String second = host.publish(TestPacks.pack("b"));

    String token = URI.create(first).getPath().split("/")[1];
    assertThat(token).hasSize(32).matches("[0-9a-f]+");
    assertThat(URI.create(second).getPath().split("/")[1]).isNotEqualTo(token);
  }

  @Test
  void republishingTheSameArtifactKeepsItsUrl() throws Exception {
    host = PackHost.serve("127.0.0.1", 0, "", clock::get);
    PackArtifact artifact = TestPacks.pack("a");

    assertThat(host.publish(artifact)).isEqualTo(host.publish(PackArtifact.of(readAll(artifact), 84, 97)));
  }

  @Test
  void otherMethodsAreRefused() throws Exception {
    host = PackHost.serve("127.0.0.1", 0, "", clock::get);
    String url = host.publish(TestPacks.pack("a"));

    HttpResponse<byte[]> response = client.send(HttpRequest.newBuilder(URI.create(url))
        .POST(HttpRequest.BodyPublishers.ofString("x")).build(), HttpResponse.BodyHandlers.ofByteArray());

    assertThat(response.statusCode()).isEqualTo(405);
  }

  @Test
  void aReplacedArtifactStaysDownloadableUntilItRetires() throws Exception {
    host = PackHost.serve("127.0.0.1", 0, "", clock::get);
    String old = host.publish(TestPacks.pack("a"));
    String next = host.publish(TestPacks.pack("b"));

    clock.addAndGet(PackHost.RETIRED_MILLIS - 1L);
    assertThat(get(old, "GET").statusCode()).isEqualTo(200);

    clock.addAndGet(1L);
    assertThat(get(old, "GET").statusCode()).isEqualTo(404);
    assertThat(get(next, "GET").statusCode()).isEqualTo(200);
  }

  @Test
  void publicUrlReplacesTheBindAddress() throws Exception {
    host = PackHost.serve("127.0.0.1", 0, "https://packs.example.com/adapt/", clock::get);
    PackArtifact artifact = TestPacks.pack("a");

    assertThat(host.publish(artifact)).startsWith("https://packs.example.com/adapt/")
        .endsWith("/" + artifact.sha1Hex() + ".zip");
  }

  @Test
  void stopFreesThePort() throws Exception {
    host = PackHost.serve("127.0.0.1", 0, "", clock::get);
    String url = host.publish(TestPacks.pack("a"));
    int port = host.port();

    host.stop();

    assertThatThrownBy(() -> get(url, "GET")).isInstanceOf(IOException.class);
    try (ServerSocket socket = new ServerSocket()) {
      socket.bind(new InetSocketAddress("127.0.0.1", port));
      assertThat(socket.isBound()).isTrue();
    }
  }

  @Test
  void externalModeServesNothingAndFillsInTheHash() {
    host = PackHost.external("https://cdn.example.com/adapt-{sha1}.zip");
    PackArtifact artifact = TestPacks.pack("a");

    assertThat(host.port()).isZero();
    assertThat(host.publish(artifact)).isEqualTo("https://cdn.example.com/adapt-" + artifact.sha1Hex() + ".zip");
  }

  private HttpResponse<byte[]> get(String url, String method) throws IOException, InterruptedException {
    HttpRequest request = HttpRequest.newBuilder(URI.create(url)).method(method, HttpRequest.BodyPublishers.noBody())
        .build();
    return client.send(request, HttpResponse.BodyHandlers.ofByteArray());
  }

  private static byte[] readAll(PackArtifact artifact) throws IOException {
    return artifact.open().readAllBytes();
  }
}
