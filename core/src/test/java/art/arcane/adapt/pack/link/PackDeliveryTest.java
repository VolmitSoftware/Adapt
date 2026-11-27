package art.arcane.adapt.pack.link;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.util.config.TomlCodec;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class PackDeliveryTest {
  @Test
  void thePackIsUnhostedOnlyWithoutListenerAddressOrMerge() throws IOException {
    assertThat(PackDelivery.unhosted(pack(true, "", false))).isFalse();
    assertThat(PackDelivery.unhosted(pack(false, "https://cdn.example/{sha1}.zip", false))).isFalse();
    assertThat(pack(false, "", true).isMerged()).isTrue();
    assertThat(pack(false, "https://cdn.example/{sha1}.zip", false).getExternalUrl()).isNotEmpty();
    assertThat(PackDelivery.unhosted(pack(false, "", true))).isFalse();

    assertThat(PackDelivery.unhosted(pack(false, "", false))).isTrue();
  }

  @Test
  void mergedIsOffByDefault() {
    assertThat(new AdaptConfig().getPack().isMerged()).isFalse();
  }

  private static AdaptConfig.Pack pack(boolean serve, String externalUrl, boolean merged) throws IOException {
    String canonical = TomlCodec.toToml(new AdaptConfig(), "core-config");
    String edited = canonical.replaceAll("(?m)^serve = true$", "serve = " + serve)
        .replaceAll("(?m)^externalUrl = \"\"$", "externalUrl = \"" + externalUrl + "\"")
        .replaceAll("(?m)^merged = false$", "merged = " + merged);
    return TomlCodec.fromToml(edited, AdaptConfig.class).getPack();
  }
}
