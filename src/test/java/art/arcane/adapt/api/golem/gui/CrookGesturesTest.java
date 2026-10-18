package art.arcane.adapt.api.golem.gui;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CrookGesturesTest {
  private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
  private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-0000000000a2");

  @Test
  void ownGolemsOpenTheGolemGuiWithOrWithoutSneakAndOthersGetNothing() {
    assertThat(CrookGestures.onEntity(true)).isEqualTo(CrookGestures.Action.GOLEM_GUI);
    assertThat(CrookGestures.onEntity(false)).isEqualTo(CrookGestures.Action.NONE);
  }

  @Test
  void sneakRightClickingAirOpensTheRosterUnlessItEchoesAnEntityClick() {
    assertThat(CrookGestures.onAir(true, false)).isEqualTo(CrookGestures.Action.ROSTER_GUI);
    assertThat(CrookGestures.onAir(false, false)).isEqualTo(CrookGestures.Action.NONE);
    assertThat(CrookGestures.onAir(true, true)).isEqualTo(CrookGestures.Action.NONE);
  }

  @Test
  void leftClickingAirSendsTheSignalSneakingCyclesItAndEntityClickEchoesDoNothing() {
    assertThat(CrookGestures.onLeftAir(false, false)).isEqualTo(CrookGestures.Action.SEND_SIGNAL);
    assertThat(CrookGestures.onLeftAir(true, false)).isEqualTo(CrookGestures.Action.CYCLE_SIGNAL);
    assertThat(CrookGestures.onLeftAir(false, true)).isEqualTo(CrookGestures.Action.NONE);
    assertThat(CrookGestures.onLeftAir(true, true)).isEqualTo(CrookGestures.Action.NONE);
  }

  @Test
  void containersLinkOnlyWhileLinkModeIsOn() {
    assertThat(CrookGestures.onBlock(true, true)).isEqualTo(CrookGestures.Action.LINK_CHEST);
    assertThat(CrookGestures.onBlock(true, false)).isEqualTo(CrookGestures.Action.NONE);
    assertThat(CrookGestures.onBlock(false, true)).isEqualTo(CrookGestures.Action.NONE);
  }

  @Test
  void theAirClickThatFollowsAnEntityClickIsSwallowedBriefly() {
    CrookGestures<String> gestures = new CrookGestures<>();
    gestures.entityClicked(PLAYER, 1_000L);

    assertThat(gestures.isEntityClickEcho(PLAYER, 1_000L)).isTrue();
    assertThat(gestures.isEntityClickEcho(PLAYER, 1_000L + CrookGestures.ENTITY_CLICK_ECHO_MILLIS)).isTrue();
    assertThat(gestures.isEntityClickEcho(PLAYER, 1_001L + CrookGestures.ENTITY_CLICK_ECHO_MILLIS)).isFalse();
    assertThat(gestures.isEntityClickEcho(OTHER, 1_000L)).isFalse();
  }

  @Test
  void linkModeTargetsTheArmedGolemUntilItExpires() {
    CrookGestures<String> gestures = new CrookGestures<>();
    gestures.armLink(PLAYER, "golem", 10_000L);

    assertThat(gestures.linkTarget(PLAYER, 10_000L + CrookGestures.LINK_WINDOW_MILLIS)).contains("golem");
    assertThat(gestures.linkTarget(OTHER, 10_000L)).isEmpty();
    assertThat(gestures.linkTarget(PLAYER, 10_001L + CrookGestures.LINK_WINDOW_MILLIS)).isEmpty();
    assertThat(gestures.linkTarget(PLAYER, 10_000L)).isEmpty();
  }

  @Test
  void rearmingReplacesTheTargetAndClearingEndsLinkMode() {
    CrookGestures<String> gestures = new CrookGestures<>();
    gestures.armLink(PLAYER, "first", 0L);
    gestures.armLink(PLAYER, "second", 5L);

    assertThat(gestures.linkTarget(PLAYER, 10L)).contains("second");

    gestures.clearLink(PLAYER);

    assertThat(gestures.linkTarget(PLAYER, 10L)).isEmpty();
  }

  @Test
  void forgettingAPlayerDropsLinkModeAndEchoState() {
    CrookGestures<String> gestures = new CrookGestures<>();
    gestures.armLink(PLAYER, "golem", 0L);
    gestures.entityClicked(PLAYER, 0L);

    gestures.forget(PLAYER);

    assertThat(gestures.linkTarget(PLAYER, 0L)).isEmpty();
    assertThat(gestures.isEntityClickEcho(PLAYER, 0L)).isFalse();
  }
}
