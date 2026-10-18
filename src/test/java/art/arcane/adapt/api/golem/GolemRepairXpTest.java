package art.arcane.adapt.api.golem;

import org.bukkit.entity.CopperGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class GolemRepairXpTest {
  @Test
  void onlyHealsThatRestoredHealthReachTheSink() {
    Player player = mock(Player.class);
    LivingEntity golem = mock(CopperGolem.class);
    List<Double> paid = new ArrayList<>();
    GolemRepairXp.setSink((healer, healedGolem, healed) -> paid.add(healed));
    try {
      GolemRepairXp.repaired(player, golem, 3.5D);
      GolemRepairXp.repaired(player, golem, 0D);
      GolemRepairXp.repaired(player, golem, -2D);
      GolemRepairXp.repaired(player, golem, Double.NaN);
      GolemRepairXp.repaired(player, golem, Double.POSITIVE_INFINITY);
    } finally {
      GolemRepairXp.setSink(GolemRepairXp.Sink.NONE);
    }

    assertThat(paid).containsExactly(3.5D);
  }

  @Test
  void repairsPayUnderOneRewardKey() {
    assertThat(GolemRepairXp.REWARD_KEY).isEqualTo("golemancy:repair");
  }
}
