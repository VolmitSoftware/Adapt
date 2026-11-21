package art.arcane.adapt.client.protocol;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the wire bytes of every frame. A failure here means the protocol changed: bump
 * {@link ClientProtocol#VERSION} and rewrite the vectors under {@code client-protocol/} on purpose.
 */
class FrameGoldenTest {
  @Test
  void serverFramesMatchTheirGoldenBytes() throws Exception {
    for (Map.Entry<String, ServerFrame> sample : FrameSamples.server().entrySet()) {
      byte[] golden = golden(sample.getKey());
      assertThat(hex(FrameCodec.encodeServer(sample.getValue()))).as(sample.getKey()).isEqualTo(hex(golden));
      assertThat(FrameCodec.decodeServer(golden)).as(sample.getKey()).isEqualTo(sample.getValue());
    }
  }

  @Test
  void clientFramesMatchTheirGoldenBytes() throws Exception {
    for (Map.Entry<String, ClientFrame> sample : FrameSamples.client().entrySet()) {
      byte[] golden = golden(sample.getKey());
      assertThat(hex(FrameCodec.encodeClient(sample.getValue()))).as(sample.getKey()).isEqualTo(hex(golden));
      assertThat(FrameCodec.decodeClient(golden)).as(sample.getKey()).isEqualTo(sample.getValue());
    }
  }

  private static byte[] golden(String name) throws IOException {
    try (InputStream in = FrameGoldenTest.class.getResourceAsStream("/client-protocol/" + name + ".hex")) {
      assertThat(in).as("golden vector " + name).isNotNull();
      String text = new String(in.readAllBytes(), StandardCharsets.US_ASCII).replaceAll("\\s+", "");
      return HexFormat.of().parseHex(text);
    }
  }

  private static String hex(byte[] bytes) {
    return HexFormat.of().formatHex(bytes);
  }
}
