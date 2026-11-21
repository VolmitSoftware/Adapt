package art.arcane.adapt.bukkit.port;

import art.arcane.adapt.client.protocol.FrameFragments;
import art.arcane.adapt.client.protocol.FrameLimits;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BukkitClientLinkTest {
  @Test
  void aFullSizeFrameFitsTheFragmentCapUnderTheSmallestLimit() throws Exception {
    assertThat(FrameFragments.split(new byte[FrameLimits.MAX_S2C_BYTES], FrameLimits.MAX_C2S_PAYLOAD_BYTES))
        .hasSizeLessThanOrEqualTo(FrameLimits.MAX_FRAGMENTS);
  }
}
