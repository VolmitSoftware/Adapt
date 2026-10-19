package art.arcane.adapt.api.golem;

import org.bukkit.entity.CopperGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowman;
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
  void eachHealReachesTheCurrentSinkWithItsHealerAndGolem() {
    Player first = mock(Player.class);
    Player second = mock(Player.class);
    LivingEntity copper = mock(CopperGolem.class);
    LivingEntity frost = mock(Snowman.class);
    List<String> replaced = new ArrayList<>();
    List<String> current = new ArrayList<>();
    GolemRepairXp.setSink((healer, golem, healed) -> replaced.add("old"));
    GolemRepairXp.setSink((healer, golem, healed) -> current.add((healer == first ? "first" : "second") + ":" + (golem == copper ? "copper" : "frost")
        + ":" + healed));
    try {
      GolemRepairXp.repaired(first, copper, 4.8D);
      GolemRepairXp.repaired(second, frost, 2D);
    } finally {
      GolemRepairXp.setSink(GolemRepairXp.Sink.NONE);
    }
    GolemRepairXp.repaired(first, copper, 1D);

    assertThat(replaced).isEmpty();
    assertThat(current).containsExactly("first:copper:4.8", "second:frost:2.0");
  }
}
