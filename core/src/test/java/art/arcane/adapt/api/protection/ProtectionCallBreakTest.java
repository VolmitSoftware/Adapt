package art.arcane.adapt.api.protection;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProtectionCallBreakTest {
  @TempDir
  Path data;

  private TestPlatform platform;
  private TestAdapt adapt;
  private FakePlayer player;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    player = platform.player("Cutter", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of());
  }

  @AfterEach
  void stop() {
    adapt.close();
  }

  @Test
  void callBreakPostsAnOrdinaryBreakEventThatListenersSeeAndCanCancel() {
    BlockRef block = player.location().world().blockAt(1, 61, 0);
    List<BlockRef> seen = new ArrayList<>();
    boolean[] cancel = {false};
    AdaptCore.get().getBus().register(this, BlockBreakEvent.class, EventPriority.NORMAL, (BlockBreakEvent event) -> {
      seen.add(event.block());
      event.setCancelled(cancel[0]);
    });

    assertThat(ProtectionProbes.callBlockBreak(player, block)).isTrue();
    cancel[0] = true;
    assertThat(ProtectionProbes.callBlockBreak(player, block)).isFalse();

    assertThat(seen).hasSize(2);
  }

  @Test
  void callBreakReportsAProtectionVetoAsCancelled() {
    BlockRef block = player.location().world().blockAt(1, 61, 0);
    platform.protection().deny(true);

    assertThat(ProtectionProbes.callBlockBreak(player, block)).isFalse();
  }
}
