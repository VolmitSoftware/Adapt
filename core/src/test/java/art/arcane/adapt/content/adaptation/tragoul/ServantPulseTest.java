package art.arcane.adapt.content.adaptation.tragoul;

import art.arcane.adapt.api.minion.ServantOrder;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.entity.EntityTargetEvent;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ServantPulseTest {
  private static final double LEASH = 32D;

  private final WorldRef world = mock(WorldRef.class);
  private final LivingRef target = mock(LivingRef.class);

  @Test
  void aFreeServantKeepsItsUsualHunting() {
    assertThat(ServantPulse.step(ServantOrder.FREE, senses(false, 400D, Double.NaN, false))).isEqualTo(ServantPulse.Step.HUNT);
  }

  @Test
  void aHeldCommandAttacksItsTargetInEveryMode() {
    assertThat(ServantPulse.step(ServantOrder.FREE.command(target, 9L), senses(true, 400D, Double.NaN, false))).isEqualTo(ServantPulse.Step.ATTACK);
    assertThat(ServantPulse.step(ServantOrder.follow().command(target, 9L), senses(true, 2_000D, Double.NaN, false))).isEqualTo(ServantPulse.Step.ATTACK);
    assertThat(ServantPulse.step(hold().command(target, 9L), senses(true, 400D, 100D, false))).isEqualTo(ServantPulse.Step.ATTACK);
  }

  @Test
  void anIdleFollowingServantWalksBackWhenMoreThanFourBlocksAway() {
    assertThat(ServantPulse.step(ServantOrder.follow(), senses(false, 16D, Double.NaN, false))).isEqualTo(ServantPulse.Step.HUNT);
    assertThat(ServantPulse.step(ServantOrder.follow(), senses(false, 16.01D, Double.NaN, false))).isEqualTo(ServantPulse.Step.FOLLOW);
    assertThat(ServantPulse.step(ServantOrder.follow(), senses(false, Double.NaN, Double.NaN, false))).isEqualTo(ServantPulse.Step.HUNT);
  }

  @Test
  void aFollowingServantKeepsFightingUntilItPassesTheLeashRadius() {
    assertThat(ServantPulse.step(ServantOrder.follow(), senses(false, 100D, Double.NaN, true))).isEqualTo(ServantPulse.Step.HUNT);
    assertThat(ServantPulse.step(ServantOrder.follow(), senses(false, LEASH * LEASH, Double.NaN, true))).isEqualTo(ServantPulse.Step.HUNT);
    assertThat(ServantPulse.step(ServantOrder.follow(), senses(false, LEASH * LEASH + 1D, Double.NaN, true))).isEqualTo(ServantPulse.Step.FOLLOW);
  }

  @Test
  void heldServantsStandTheirGroundAndOnlyWalkBackWhenIdle() {
    assertThat(ServantPulse.step(hold(), senses(false, 400D, 0.5D, false))).isEqualTo(ServantPulse.Step.STAND);
    assertThat(ServantPulse.step(hold(), senses(false, 400D, 9D, true))).isEqualTo(ServantPulse.Step.STAND);
    assertThat(ServantPulse.step(hold(), senses(false, 400D, 9D, false))).isEqualTo(ServantPulse.Step.RETURN);
    assertThat(ServantPulse.step(hold(), senses(false, 400D, Double.NaN, false))).isEqualTo(ServantPulse.Step.STAND);
  }

  @Test
  void onlyFreeAndFollowingServantsTakeThePacksPriorityTarget() {
    assertThat(ServantPulse.takesPriority(ServantOrder.FREE)).isTrue();
    assertThat(ServantPulse.takesPriority(ServantOrder.follow())).isTrue();
    assertThat(ServantPulse.takesPriority(hold())).isFalse();
    assertThat(ServantPulse.takesPriority(ServantOrder.follow().command(target, 9L))).isFalse();
  }

  @Test
  void heldServantsOnlyRetaliate() {
    assertThat(gate(hold(), EntityTargetEvent.Reason.TARGET_ATTACKED_ENTITY, target)).isEqualTo(ServantPulse.TargetGate.USUAL);
    assertThat(gate(hold(), EntityTargetEvent.Reason.CLOSEST_ENTITY, target)).isEqualTo(ServantPulse.TargetGate.REFUSED);
    assertThat(gate(hold(), EntityTargetEvent.Reason.CLOSEST_PLAYER, target)).isEqualTo(ServantPulse.TargetGate.REFUSED);
    assertThat(gate(hold(), EntityTargetEvent.Reason.UNKNOWN, target)).isEqualTo(ServantPulse.TargetGate.REFUSED);
    assertThat(gate(ServantOrder.FREE, EntityTargetEvent.Reason.CLOSEST_ENTITY, target)).isEqualTo(ServantPulse.TargetGate.USUAL);
    assertThat(gate(ServantOrder.follow(), EntityTargetEvent.Reason.CLOSEST_ENTITY, target)).isEqualTo(ServantPulse.TargetGate.USUAL);
  }

  @Test
  void aLiveSicTargetPassesTheGateInEveryOrderWhateverTheReason() {
    LivingRef player = mock(PlayerRef.class);
    for (ServantOrder base : new ServantOrder[]{ServantOrder.FREE, ServantOrder.follow(), hold()}) {
      ServantOrder sic = base.command(player, 20_000L);
      for (EntityTargetEvent.Reason reason : new EntityTargetEvent.Reason[]{EntityTargetEvent.Reason.UNKNOWN,
          EntityTargetEvent.Reason.CUSTOM, EntityTargetEvent.Reason.CLOSEST_PLAYER, EntityTargetEvent.Reason.TARGET_ATTACKED_ENTITY}) {
        assertThat(ServantPulse.gate(sic, reason, player, 10_000L)).as("%s %s", base.mode(), reason).isEqualTo(ServantPulse.TargetGate.COMMANDED);
      }
    }
    ServantOrder heldSic = hold().command(player, 20_000L);
    assertThat(ServantPulse.gate(heldSic, EntityTargetEvent.Reason.UNKNOWN, player, 20_000L)).isEqualTo(ServantPulse.TargetGate.REFUSED);
    assertThat(ServantPulse.gate(heldSic, EntityTargetEvent.Reason.UNKNOWN, target, 10_000L)).isEqualTo(ServantPulse.TargetGate.REFUSED);
  }

  @Test
  void theSicTargetPassesTheStanceAndPlayerGatesOnlyWhileTheCommandHolds() {
    LivingRef player = mock(PlayerRef.class);
    LivingRef other = mock(LivingRef.class);
    ServantOrder sic = ServantOrder.follow().command(player, 20_000L);

    assertThat(ServantPulse.commanded(sic, player, 19_999L)).isTrue();
    assertThat(ServantPulse.commanded(sic, player, 20_000L)).isFalse();
    assertThat(ServantPulse.commanded(sic, other, 10_000L)).isFalse();
    assertThat(ServantPulse.commanded(sic, null, 10_000L)).isFalse();
    assertThat(ServantPulse.commanded(ServantOrder.follow(), player, 10_000L)).isFalse();
  }

  private ServantPulse.Senses senses(boolean commandHolds, double ownerDistanceSquared, double anchorDistanceSquared, boolean fighting) {
    return new ServantPulse.Senses(commandHolds, ownerDistanceSquared, anchorDistanceSquared, fighting, LEASH);
  }

  private static ServantPulse.TargetGate gate(ServantOrder order, EntityTargetEvent.Reason reason, LivingRef candidate) {
    return ServantPulse.gate(order, reason, candidate, 10_000L);
  }

  private ServantOrder hold() {
    return ServantOrder.hold(new Loc(world, 0D, 64D, 0D));
  }
}
