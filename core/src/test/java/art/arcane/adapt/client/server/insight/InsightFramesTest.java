package art.arcane.adapt.client.server.insight;

import art.arcane.adapt.client.protocol.ClientProtocol;
import art.arcane.adapt.client.protocol.ServerFrame;
import art.arcane.adapt.client.server.hud.RecordingHudLink;
import art.arcane.adapt.api.insight.InsightBond;
import art.arcane.adapt.api.insight.InsightEffect;
import art.arcane.adapt.api.insight.InsightFeed;
import art.arcane.adapt.api.insight.InsightKind;
import art.arcane.adapt.api.insight.InsightMark;
import art.arcane.adapt.api.insight.InsightStat;
import art.arcane.adapt.api.insight.InsightView;
import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InsightFramesTest {
  private static final UUID VIEWER = new UUID(0L, 1L);

  private final RecordingHudLink link = new RecordingHudLink();
  private final AtomicLong clock = new AtomicLong(1_000L);
  private final InsightFrames frames = new InsightFrames(link, clock::get);

  @Test
  void viewersWithoutTheCapabilityGetNothing() {
    link.grant(VIEWER, ClientProtocol.HUD);

    assertThat(frames.show(VIEWER, view(1, 20D, 600), List.of())).isFalse();
    assertThat(link.frames()).isEmpty();
  }

  @Test
  void aNewTargetIsSentWholeThenOnlyItsVitals() {
    link.grant(VIEWER, ClientProtocol.INSIGHT);

    frames.show(VIEWER, view(1, 20D, 600), List.of());
    frames.show(VIEWER, view(1, 20D, 590), List.of());
    frames.show(VIEWER, view(1, 15D, 580), List.of());
    frames.show(VIEWER, view(2, 15D, 580), List.of());

    assertThat(link.frames()).containsExactly(InsightFrames.target(view(1, 20D, 600)),
        new ServerFrame.InsightVitals(1, 15D, 0D), InsightFrames.target(view(2, 15D, 580)));
  }

  @Test
  void anUnchangedTargetIsKeptAliveOncePerSecond() {
    link.grant(VIEWER, ClientProtocol.INSIGHT);

    frames.show(VIEWER, view(1, 20D, 600), List.of());
    clock.addAndGet(999L);
    frames.show(VIEWER, view(1, 20D, 600), List.of());
    clock.addAndGet(1L);
    frames.show(VIEWER, view(1, 20D, 600), List.of());

    assertThat(link.frames()).hasSize(2).last().isEqualTo(new ServerFrame.InsightVitals(1, 20D, 0D));
  }

  @Test
  void marksAreSentRoundedAndOnlyWhenTheyChange() {
    link.grant(VIEWER, ClientProtocol.INSIGHT);

    frames.show(VIEWER, view(1, 20D, 600), List.of(InsightMark.of(5, InsightKind.HOSTILE, 0.51D)));
    frames.show(VIEWER, view(1, 20D, 600), List.of(InsightMark.of(5, InsightKind.HOSTILE, 0.49D)));
    frames.show(VIEWER, view(1, 20D, 600), List.of());

    assertThat(link.frames()).containsExactly(InsightFrames.target(view(1, 20D, 600)),
        new ServerFrame.InsightMarks(List.of(new ServerFrame.InsightMarks.Mark(5, InsightKind.HOSTILE, 0.5D))),
        new ServerFrame.InsightMarks(List.of()));
  }

  @Test
  void clearIsSentOnceAndOnlyAfterSomethingWasShown() {
    link.grant(VIEWER, ClientProtocol.INSIGHT);
    frames.clear(VIEWER);
    frames.show(VIEWER, view(1, 20D, 600), List.of());
    frames.clear(VIEWER);
    frames.clear(VIEWER);
    frames.show(VIEWER, view(1, 20D, 600), List.of());

    assertThat(link.frames()).containsExactly(InsightFrames.target(view(1, 20D, 600)),
        new ServerFrame.InsightClear(), InsightFrames.target(view(1, 20D, 600)));
  }

  @Test
  void aWithdrawnClientIsSentTheWholeTargetAgainWhenItReturns() {
    link.grant(VIEWER, ClientProtocol.INSIGHT);
    frames.show(VIEWER, view(1, 20D, 600), List.of());
    link.withdraw(VIEWER);
    assertThat(frames.show(VIEWER, view(1, 20D, 600), List.of())).isFalse();
    link.grant(VIEWER, ClientProtocol.INSIGHT);
    frames.show(VIEWER, view(1, 20D, 600), List.of());

    assertThat(link.frames()).containsExactly(InsightFrames.target(view(1, 20D, 600)),
        InsightFrames.target(view(1, 20D, 600)));
  }

  @Test
  void theWireTargetCarriesIdsAsTextAndTagsInOrder() {
    InsightView view = InsightView.builder(7, Id.minecraft("horse"), InsightKind.PASSIVE).health(10D, 20D)
        .bond(InsightBond.WILD, "").tag(Id.adapt("stable_hand"))
        .stat(InsightStat.of(Id.minecraft("movement_speed"), 0.25D))
        .effect(InsightEffect.harmful(Id.minecraft("poison"), 1, InsightEffect.INFINITE)).build();

    assertThat(InsightFrames.target(view)).isEqualTo(new ServerFrame.InsightTarget(7, "minecraft:horse", "",
        InsightKind.PASSIVE, 10D, 20D, 0D, 0D, InsightBond.WILD, "", List.of("adapt:stable_hand"),
        List.of(new ServerFrame.InsightTarget.Stat("minecraft:movement_speed", 0.25D)),
        List.of(new ServerFrame.InsightTarget.Effect("minecraft:poison", 1, -1, true))));
  }

  @Test
  void moreMarksThanTheLimitAreRefusedNamingThem() {
    link.grant(VIEWER, ClientProtocol.INSIGHT);
    List<InsightMark> marks = new ArrayList<>();
    for (int index = 0; index <= InsightFeed.MAX_MARKS; index++) {
      marks.add(InsightMark.of(index, InsightKind.HOSTILE, 1D));
    }

    assertThatThrownBy(() -> frames.show(VIEWER, view(1, 20D, 600), marks))
        .isInstanceOf(IllegalArgumentException.class).hasMessageStartingWith("marks holds 17 entries");
  }

  private static InsightView view(int entityId, double health, int effectTicks) {
    return InsightView.builder(entityId, Id.minecraft("zombie"), InsightKind.HOSTILE).health(health, 20D).armor(2D)
        .effect(InsightEffect.beneficial(Id.minecraft("speed"), 0, effectTicks)).build();
  }
}
