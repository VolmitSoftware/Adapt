package art.arcane.adapt.localization;

import art.arcane.adapt.AdaptTestBase;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdaptLanguageDownloadTest extends AdaptTestBase {

  @Test
  void sourceManifestRestrictsDownloadsToKnownLocales() {
    Set<String> locales = AdaptLanguageDownload.availableLocales();

    assertThat(locales).hasSize(17).contains("de_DE", "ja-JP", "zh_TW").doesNotContain("en_US");
    assertThatThrownBy(() -> AdaptLanguageDownload.sourceUri("unknown_LOCALE"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void sourceUriPinsTheSelectedLocaleToTheBuildRevision() {
    URI source = AdaptLanguageDownload.sourceUri("de_DE");

    assertThat(source.getHost()).isEqualTo("raw.githubusercontent.com");
    assertThat(source.getPath()).isEqualTo(
        "/VolmitSoftware/Adapt/"
            + AdaptLanguageDownload.sourceRevision()
            + "/src/main/resources/de_DE.toml"
    );
  }

  @Test
  void downloadAcceptsOnlyThePinnedLocaleBytes() throws Exception {
    byte[] sourceBytes = Files.readAllBytes(Path.of("src/main/resources/de_DE.toml"));
    HttpServer server = startServer(sourceBytes);
    Path target = dataFolder.toPath().resolve("download-result/de_DE.toml");
    try {
      AdaptLanguageDownload.download(serverUri(server), "de_DE", target);
    } finally {
      server.stop(0);
    }

    assertThat(target).hasBinaryContent(sourceBytes);
  }

  @Test
  void checksumMismatchIsNeverPublished() throws Exception {
    HttpServer server = startServer("not the pinned locale".getBytes(StandardCharsets.UTF_8));
    Path target = dataFolder.toPath().resolve("download-result/de_DE.toml");
    try {
      assertThatThrownBy(() -> AdaptLanguageDownload.download(serverUri(server), "de_DE", target))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("checksum");
    } finally {
      server.stop(0);
    }

    assertThat(target).doesNotExist();
  }

  @Test
  void verifiedCacheRejectsModifiedBytes() throws Exception {
    Path target = AdaptLanguageDownload.localeFile("de_DE").toPath();
    Files.createDirectories(target.getParent());
    Files.copy(Path.of("src/main/resources/de_DE.toml"), target);

    assertThat(AdaptLanguageDownload.readVerifiedOverlay("de_DE", false)).isNotNull();
    Files.writeString(target, "modified", StandardCharsets.UTF_8);
    assertThat(AdaptLanguageDownload.readVerifiedOverlay("de_DE", false)).isNull();
  }

  private HttpServer startServer(byte[] body) throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/de_DE.toml", (HttpExchange exchange) -> {
      exchange.sendResponseHeaders(200, body.length);
      exchange.getResponseBody().write(body);
      exchange.close();
    });
    server.start();
    return server;
  }

  private URI serverUri(HttpServer server) {
    return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/de_DE.toml");
  }
}
