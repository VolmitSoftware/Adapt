package art.arcane.adapt.engine.adaptation;

import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.entity.EntityKnockbackEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestContent;
import art.arcane.adapt.testkit.TestPlatform;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SimpleAdaptationPlayerDamageTest {
  private static final Id PLAYER_ATTACK = Id.minecraft("player_attack");
  private static final AdaptationHost HOST = mock(AdaptationHost.class, CALLS_REAL_METHODS);

  @TempDir
  Path data;

  @Test
  void selfDamageSuppressesOnlyItsOwnDamageKnockbackAndClearsContext() {
    PlayerRef player = player();
    PlayerRef other = player();
    AdaptationHost.PlayerDamageListener listener = new AdaptationHost.PlayerDamageListener();
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
    }).when(player).damage(5D, PLAYER_ATTACK);

    HOST.applyPlayerDamage(player, 5D);

    EntityKnockbackEvent later = knockback(player, EntityKnockbackEvent.Cause.DAMAGE);
    listener.on(later);
    assertThat(later.isCancelled()).isFalse();
  }

  @Test
  void nestedDamageRestoresOuterContextEvenWhenInnerDamageThrows() {
    PlayerRef outer = player();
    PlayerRef inner = player();
    AdaptationHost.PlayerDamageListener listener = new AdaptationHost.PlayerDamageListener();
    doAnswer(call -> {
      throw new IllegalStateException("damage failed");
    }).when(inner).damage(1D, PLAYER_ATTACK);
    doAnswer(call -> {
      assertThatThrownBy(() -> HOST.applyPlayerDamage(inner, 1D))
          .isInstanceOf(IllegalStateException.class);
      EntityKnockbackEvent event = knockback(outer, EntityKnockbackEvent.Cause.DAMAGE);
      listener.on(event);
      assertThat(event.isCancelled()).isTrue();
      return null;
    }).when(outer).damage(5D, PLAYER_ATTACK);

    HOST.applyPlayerDamage(outer, 5D);

    EntityKnockbackEvent event = knockback(inner, EntityKnockbackEvent.Cause.DAMAGE);
    listener.on(event);
    assertThat(event.isCancelled()).isFalse();
  }

  private static EntityKnockbackEvent knockback(PlayerRef player, EntityKnockbackEvent.Cause cause) {
    return new EntityKnockbackEvent(player, cause, new Vec3(0.4D, 0.2D, 0D));
  }

  private static PlayerRef player() {
    PlayerRef player = mock(PlayerRef.class);
    when(player.uuid()).thenReturn(UUID.randomUUID());
    return player;
  }

  @Test
  void skillDamageUsesAnExplicitBukkitDamageSource() {
    PlayerRef player = player();

    HOST.applyPlayerDamage(player, 5D);

    verify(player).damage(5D, PLAYER_ATTACK);
    verify(player, never()).setHealth(anyDouble());
  }

  @Test
  void invalidSkillDamageDoesNotReachTheServer() {
    PlayerRef player = player();
    when(player.isDead()).thenReturn(false);

    try (TestAdapt adapt = TestAdapt.start(TestPlatform.builder(data).build(), List.of())) {
      TestAdaptation adaptation = TestContent.adaptation(Id.adapt("test"), TestAdaptation::new);
      adaptation.applyDamage(player, Double.NaN);
      adaptation.applyDamage(player, 0D);
      adaptation.applyDamage(player, -1D);
    }

    verify(player, never()).damage(anyDouble(), any(EntityRef.class));
    verify(player, never()).damage(anyDouble(), any(Id.class));
  }

  private static final class TestAdaptation extends SimpleAdaptation<AdaptationConfig> {
    private TestAdaptation() {
      super(Id.adapt("test/player_damage"));
      registerConfiguration(AdaptationConfig.class);
    }

    private void applyDamage(PlayerRef player, double amount) {
      applyPlayerDamage(player, amount);
    }

    @Override
    public void addStats(int level, StatLines lines) {
    }
  }
}
