package art.arcane.adapt.content.adaptation.tragoul;

import art.arcane.adapt.api.minion.ServantOrder;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityTargetEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ServantPulseTest {
  private final World world = mock(World.class);
  private final LivingEntity target = mock(Zombie.class);

  @Test
  void aFreeServantKeepsItsUsualHunting() {
    assertThat(ServantPulse.step(ServantOrder.FREE, false, 400D, Double.NaN, false)).isEqualTo(ServantPulse.Step.HUNT);
  }

  @Test
  void aHeldCommandAttacksItsTargetInEveryMode() {
    assertThat(ServantPulse.step(ServantOrder.FREE.command(target, 9L), true, 400D, Double.NaN, false)).isEqualTo(ServantPulse.Step.ATTACK);
    assertThat(ServantPulse.step(ServantOrder.follow().command(target, 9L), true, 400D, Double.NaN, false)).isEqualTo(ServantPulse.Step.ATTACK);
    assertThat(ServantPulse.step(hold().command(target, 9L), true, 400D, 100D, false)).isEqualTo(ServantPulse.Step.ATTACK);
  }

  @Test
  void followingServantsReturnToTheOwnerOnlyWhenMoreThanFourBlocksAway() {
    assertThat(ServantPulse.step(ServantOrder.follow(), false, 16D, Double.NaN, true)).isEqualTo(ServantPulse.Step.HUNT);
    assertThat(ServantPulse.step(ServantOrder.follow(), false, 16.01D, Double.NaN, true)).isEqualTo(ServantPulse.Step.FOLLOW);
    assertThat(ServantPulse.step(ServantOrder.follow(), false, Double.NaN, Double.NaN, false)).isEqualTo(ServantPulse.Step.HUNT);
  }

  @Test
  void heldServantsStandTheirGroundAndOnlyWalkBackWhenIdle() {
    assertThat(ServantPulse.step(hold(), false, 400D, 0.5D, false)).isEqualTo(ServantPulse.Step.STAND);
    assertThat(ServantPulse.step(hold(), false, 400D, 9D, true)).isEqualTo(ServantPulse.Step.STAND);
    assertThat(ServantPulse.step(hold(), false, 400D, 9D, false)).isEqualTo(ServantPulse.Step.RETURN);
    assertThat(ServantPulse.step(hold(), false, 400D, Double.NaN, false)).isEqualTo(ServantPulse.Step.STAND);
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
    assertThat(ServantPulse.allowsTarget(hold(), EntityTargetEvent.TargetReason.TARGET_ATTACKED_ENTITY)).isTrue();
    assertThat(ServantPulse.allowsTarget(hold(), EntityTargetEvent.TargetReason.CLOSEST_ENTITY)).isFalse();
    assertThat(ServantPulse.allowsTarget(hold(), EntityTargetEvent.TargetReason.CLOSEST_PLAYER)).isFalse();
    assertThat(ServantPulse.allowsTarget(ServantOrder.FREE, EntityTargetEvent.TargetReason.CLOSEST_ENTITY)).isTrue();
    assertThat(ServantPulse.allowsTarget(ServantOrder.follow(), EntityTargetEvent.TargetReason.CLOSEST_ENTITY)).isTrue();
  }

  private ServantOrder hold() {
    return ServantOrder.hold(new Location(world, 0D, 64D, 0D));
  }
}
