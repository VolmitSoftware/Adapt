package art.arcane.adapt.pack.link;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PackArtifactTest {
  @Test
  void hashAndIdFollowTheBytes() throws IOException, NoSuchAlgorithmException {
    PackArtifact artifact = TestPacks.pack("a");
    byte[] bytes = artifact.open().readAllBytes();

    PackArtifact copy = PackArtifact.of(bytes, 84, 97);

    assertThat(artifact.sha1Hex()).isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes)));
    assertThat(copy.id()).isEqualTo(artifact.id());
    assertThat(copy).isEqualTo(artifact);
    assertThat(TestPacks.pack("b").id()).isNotEqualTo(artifact.id());
  }

  @Test
  void callersCannotChangeTheBytes() throws IOException {
    byte[] bytes = TestPacks.pack("a").open().readAllBytes();
    PackArtifact artifact = PackArtifact.of(bytes, 84, 97);
    String hash = artifact.sha1Hex();

    bytes[0] ^= 1;
    artifact.sha1()[0] ^= 1;

    assertThat(PackArtifact.of(artifact.open().readAllBytes(), 84, 97).sha1Hex()).isEqualTo(hash);
    assertThat(HexFormat.of().formatHex(artifact.sha1())).isEqualTo(hash);
  }

  @Test
  void formatRangeMustBeOrdered() {
    assertThatThrownBy(() -> PackArtifact.of(new byte[0], 97, 84)).isInstanceOf(IllegalArgumentException.class);
  }
}
