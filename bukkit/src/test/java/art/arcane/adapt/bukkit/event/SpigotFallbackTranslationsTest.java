package art.arcane.adapt.bukkit.event;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.AdaptEventBus;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.entity.EntityKnockbackEvent;
import art.arcane.adapt.platform.event.entity.EntityPushedByEntityAttackEvent;
import art.arcane.adapt.platform.event.player.PlayerJumpEvent;
import art.arcane.adapt.platform.value.Vec3;
import org.bukkit.Location;
import org.bukkit.Statistic;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings("removal")
class SpigotFallbackTranslationsTest {
  private final List<Throwable> failures = new ArrayList<>();
  private final AdaptEventBus bus = new AdaptEventBus((owner, event, error) -> failures.add(error));
  private GapRefs refs;

  @BeforeEach
  void setUp() {
    refs = new GapRefs();
  }

  @AfterEach
  void tearDown() {
    refs.close();
    assertThat(failures).isEmpty();
  }

  @Test
  void knockbackByEntityPostsPushAndWritesBackFinalKnockback() {
    Zombie victim = mock(Zombie.class);
    Player attacker = mock(Player.class);
    org.bukkit.event.entity.EntityKnockbackByEntityEvent event = new org.bukkit.event.entity.EntityKnockbackByEntityEvent(victim,
        attacker, org.bukkit.event.entity.EntityKnockbackEvent.KnockbackCause.ENTITY_ATTACK, 0.4D, new Vector(1, 0, 0),
        new Vector(1, 0, 0));
    AtomicReference<EntityKnockbackEvent> seen = new AtomicReference<>();
    bus.register(this, EntityKnockbackEvent.class, EventPriority.HIGHEST, core -> {
      seen.set(core);
      core.setKnockback(new Vec3(0, 2, 0));
    });

    assertThat(SpigotFallbackTranslations.KNOCKBACK.target(event)).isEqualTo(EntityPushedByEntityAttackEvent.class);
    SpigotFallbackTranslations.KNOCKBACK.bridge(event, core -> bus.post(core, EventPriority.HIGHEST));

    assertThat(((EntityPushedByEntityAttackEvent) seen.get()).pushedBy()).isSameAs(refs.wrap(attacker, PlayerRef.class));
    assertThat(seen.get().cause()).isEqualTo(EntityKnockbackEvent.Cause.ENTITY_ATTACK);
    assertThat(event.getFinalKnockback()).isEqualTo(new Vector(0, 2, 0));
  }

  @Test
  void jumpStatisticPostsJumpAndOtherStatisticsAreSkipped() {
    Player jumper = mock(Player.class);
    when(jumper.getLocation()).thenReturn(new Location(mock(World.class), 1, 64, 2));
    org.bukkit.event.player.PlayerStatisticIncrementEvent jump =
        new org.bukkit.event.player.PlayerStatisticIncrementEvent(jumper, Statistic.JUMP, 4, 5);
    org.bukkit.event.player.PlayerStatisticIncrementEvent walk =
        new org.bukkit.event.player.PlayerStatisticIncrementEvent(jumper, Statistic.LEAVE_GAME, 4, 5);
    AtomicReference<PlayerJumpEvent> seen = new AtomicReference<>();
    bus.register(this, PlayerJumpEvent.class, EventPriority.NORMAL, seen::set);

    assertThat(SpigotFallbackTranslations.JUMP.target(walk)).isNull();
    assertThat(SpigotFallbackTranslations.JUMP.target(jump)).isEqualTo(PlayerJumpEvent.class);
    SpigotFallbackTranslations.JUMP.bridge(jump, core -> bus.post(core, EventPriority.NORMAL));

    assertThat(seen.get().player()).isSameAs(refs.wrap(jumper, PlayerRef.class));
  }
}
