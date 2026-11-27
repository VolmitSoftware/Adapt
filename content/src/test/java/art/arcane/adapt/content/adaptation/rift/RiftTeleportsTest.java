package art.arcane.adapt.content.adaptation.rift;

import art.arcane.adapt.platform.entity.TeleportCause;
import art.arcane.adapt.platform.event.player.PlayerTeleportEvent;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Phase Step blinks and anchor trips pay no XP, and Phase Step sets off the Rift Trigger at most once per 4 s. */
class RiftTeleportsTest {
  @TempDir
  Path data;

  @Test
  void phaseStepBlinksTriggerOncePerFourSecondsAndPayNoXp() {
    TestPlatform platform = TestPlatform.builder(data).build();
    FakePlayer player = platform.player("Blinker", 0.5D, 61D, 0.5D);
    Loc destination = new Loc(platform.world(), 20.5D, 61D, 0.5D);
    PlayerTeleportEvent blink = new PlayerTeleportEvent(player, player.location(), destination, TeleportCause.PLUGIN);

    RiftTeleports.phasing(player, destination);
    long now = 1_000_000L;

    assertThat(RiftTeleports.byRift(blink)).isTrue();
    assertThat(RiftTeleports.paysNoXp(blink)).isTrue();
    assertThat(RiftTeleports.mayTrigger(blink, now)).isTrue();
    assertThat(RiftTeleports.mayTrigger(blink, now + 3_999L)).isFalse();
    assertThat(RiftTeleports.mayTrigger(blink, now + RiftTeleports.PHASE_TRIGGER_MILLIS)).isTrue();
  }

  @Test
  void otherBlinksAlwaysTriggerAndAnchorTripsAreNoRiftTeleport() {
    TestPlatform platform = TestPlatform.builder(data).build();
    FakePlayer player = platform.player("Blinker", 0.5D, 61D, 0.5D);
    Loc destination = new Loc(platform.world(), 20.5D, 61D, 0.5D);
    PlayerTeleportEvent teleport = new PlayerTeleportEvent(player, player.location(), destination, TeleportCause.PLUGIN);

    RiftTeleports.blinking(player, destination);
    assertThat(RiftTeleports.mayTrigger(teleport, 0L)).isTrue();
    assertThat(RiftTeleports.mayTrigger(teleport, 1L)).isTrue();
    assertThat(RiftTeleports.paysNoXp(teleport)).isFalse();

    RiftTeleports.travelling(player, destination);
    assertThat(RiftTeleports.byRift(teleport)).isFalse();
    assertThat(RiftTeleports.paysNoXp(teleport)).isTrue();
  }
}
