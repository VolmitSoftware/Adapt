package art.arcane.adapt.api.adaptation;

import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.damage.DamageSource;
import io.papermc.paper.event.entity.EntityKnockbackEvent;
import org.bukkit.util.Vector;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SimpleAdaptationPlayerDamageTest {
  @Test
  void selfDamageSuppressesOnlyItsOwnDamageKnockbackAndClearsContext() {
    Player player = mock(Player.class);
    Player other = mock(Player.class);
    DamageSource source = mock(DamageSource.class);
    SimpleAdaptation.PlayerDamageListener listener = new SimpleAdaptation.PlayerDamageListener();
    doAnswer(call -> {
      EntityKnockbackEvent own = knockback(player, EntityKnockbackEvent.Cause.DAMAGE);
      EntityKnockbackEvent unrelated = knockback(other, EntityKnockbackEvent.Cause.DAMAGE);
      EntityKnockbackEvent attack = knockback(player, EntityKnockbackEvent.Cause.ENTITY_ATTACK);
      listener.on(own);
      listener.on(unrelated);
      listener.on(attack);
      assertThat(own.isCancelled()).isTrue();
      assertThat(unrelated.isCancelled()).isFalse();
      assertThat(attack.isCancelled()).isFalse();
      return null;
    }).when(player).damage(5D, source);

    SimpleAdaptation.dispatchPlayerDamage(player, 5D, source);

    EntityKnockbackEvent later = knockback(player, EntityKnockbackEvent.Cause.DAMAGE);
    listener.on(later);
    assertThat(later.isCancelled()).isFalse();
  }

  @Test
  void nestedDamageRestoresOuterContextEvenWhenInnerDamageThrows() {
    Player outer = mock(Player.class);
    Player inner = mock(Player.class);
    DamageSource source = mock(DamageSource.class);
    SimpleAdaptation.PlayerDamageListener listener = new SimpleAdaptation.PlayerDamageListener();
    doAnswer(call -> {
      throw new IllegalStateException("damage failed");
    }).when(inner).damage(1D, source);
    doAnswer(call -> {
      assertThatThrownBy(() -> SimpleAdaptation.dispatchPlayerDamage(inner, 1D, source))
          .isInstanceOf(IllegalStateException.class);
      EntityKnockbackEvent event = knockback(outer, EntityKnockbackEvent.Cause.DAMAGE);
      listener.on(event);
      assertThat(event.isCancelled()).isTrue();
      return null;
    }).when(outer).damage(5D, source);

    SimpleAdaptation.dispatchPlayerDamage(outer, 5D, source);

    EntityKnockbackEvent event = knockback(inner, EntityKnockbackEvent.Cause.DAMAGE);
    listener.on(event);
    assertThat(event.isCancelled()).isFalse();
  }

  private static EntityKnockbackEvent knockback(Player player, EntityKnockbackEvent.Cause cause) {
    return new EntityKnockbackEvent(player, cause, new Vector(0.4D, 0.2D, 0D));
  }

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
