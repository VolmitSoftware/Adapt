package art.arcane.adapt.api.adaptation;

import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SimpleAdaptationPlayerDamageTest {
  @Test
  void skillDamageUsesAnExplicitBukkitDamageSource() {
    Player player = mock(Player.class);
    DamageSource source = mock(DamageSource.class);

    SimpleAdaptation.dispatchPlayerDamage(player, 5D, source);

    verify(player).damage(5D, source);
    verify(player, never()).damage(anyDouble());
    verify(player, never()).setHealth(anyDouble());
  }

  @Test
  void invalidSkillDamageDoesNotReachTheServer() {
    Player player = mock(Player.class);
    when(player.isDead()).thenReturn(false);

    new TestAdaptation().applyDamage(player, Double.NaN);
    new TestAdaptation().applyDamage(player, 0D);
    new TestAdaptation().applyDamage(player, -1D);

    verify(player, never()).damage(anyDouble(), any(DamageSource.class));
  }

  private static final class TestAdaptation extends SimpleAdaptation<AdaptationConfig> {
    private TestAdaptation() {
      super("test-player-damage");
    }

    private void applyDamage(Player player, double amount) {
      applyPlayerDamage(player, amount);
    }

    @Override
    public void addStats(int level, Element element) {
    }
  }
}
