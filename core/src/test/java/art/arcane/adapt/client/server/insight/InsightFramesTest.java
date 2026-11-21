package art.arcane.adapt.client.server.insight;

import art.arcane.adapt.client.protocol.ClientProtocol;
import art.arcane.adapt.client.protocol.ServerFrame;
import art.arcane.adapt.client.server.hud.RecordingHudLink;
import art.arcane.adapt.client.view.InsightBond;
import art.arcane.adapt.client.view.InsightEffect;
import art.arcane.adapt.client.view.InsightKind;
import art.arcane.adapt.client.view.InsightMark;
import art.arcane.adapt.client.view.InsightView;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

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

    assertThat(link.frames()).containsExactly(new ServerFrame.InsightTarget(view(1, 20D, 600)),
        new ServerFrame.InsightVitals(1, 15D, 0D), new ServerFrame.InsightTarget(view(2, 15D, 580)));
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

    frames.show(VIEWER, view(1, 20D, 600), List.of(new InsightMark(5, InsightKind.HOSTILE, 0.51D)));
    frames.show(VIEWER, view(1, 20D, 600), List.of(new InsightMark(5, InsightKind.HOSTILE, 0.49D)));
    frames.show(VIEWER, view(1, 20D, 600), List.of());

    assertThat(link.frames()).containsExactly(new ServerFrame.InsightTarget(view(1, 20D, 600)),
        new ServerFrame.InsightMarks(List.of(new InsightMark(5, InsightKind.HOSTILE, 0.5D))),
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

    assertThat(link.frames()).containsExactly(new ServerFrame.InsightTarget(view(1, 20D, 600)),
        new ServerFrame.InsightClear(), new ServerFrame.InsightTarget(view(1, 20D, 600)));
  }

  @Test
  void aWithdrawnClientIsSentTheWholeTargetAgainWhenItReturns() {
    link.grant(VIEWER, ClientProtocol.INSIGHT);
    frames.show(VIEWER, view(1, 20D, 600), List.of());
    link.withdraw(VIEWER);
    assertThat(frames.show(VIEWER, view(1, 20D, 600), List.of())).isFalse();
    link.grant(VIEWER, ClientProtocol.INSIGHT);
    frames.show(VIEWER, view(1, 20D, 600), List.of());

    assertThat(link.frames()).containsExactly(new ServerFrame.InsightTarget(view(1, 20D, 600)),
        new ServerFrame.InsightTarget(view(1, 20D, 600)));
  }

  private static InsightView view(int entityId, double health, int effectTicks) {
    return new InsightView(entityId, "minecraft:zombie", "", InsightKind.HOSTILE, health, 20D, 0D, 2D,
        InsightBond.NONE, "", false, List.of(), List.of(new InsightEffect("minecraft:speed", 0, effectTicks, false)));
  }
}
