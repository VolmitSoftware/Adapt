package art.arcane.adapt.bukkit.event;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.AdaptEventBus;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.entity.EntityKnockbackEvent;
import art.arcane.adapt.platform.event.entity.EntityPushedByEntityAttackEvent;
import art.arcane.adapt.platform.event.entity.EntityRemoveEvent;
import art.arcane.adapt.platform.value.Vec3;
import com.destroystokyo.paper.event.entity.EntityKnockbackByEntityEvent;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import org.bukkit.entity.Arrow;
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

class Paper121TranslationsTest {
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
  void knockbackByAttackerPostsPushedSubtypeAndWritesBackAcceleration() {
    Zombie victim = mock(Zombie.class);
    Player attacker = mock(Player.class);
    Vector acceleration = new Vector(1, 0, 0);
    EntityKnockbackByEntityEvent event = new EntityKnockbackByEntityEvent(victim, attacker, 0.4F, acceleration);
    AtomicReference<EntityKnockbackEvent> seen = new AtomicReference<>();
    bus.register(this, EntityKnockbackEvent.class, EventPriority.HIGHEST, core -> {
      seen.set(core);
      core.setKnockback(new Vec3(0, 2, 0));
    });

    assertThat(Paper121Translations.KNOCKBACK.target(event)).isEqualTo(EntityPushedByEntityAttackEvent.class);
    Paper121Translations.KNOCKBACK.bridge(event, core -> bus.post(core, EventPriority.HIGHEST));

    assertThat(((EntityPushedByEntityAttackEvent) seen.get()).pushedBy()).isSameAs(refs.wrap(attacker, PlayerRef.class));
    assertThat(seen.get().cause()).isEqualTo(EntityKnockbackEvent.Cause.ENTITY_ATTACK);
    assertThat(event.getAcceleration()).isSameAs(acceleration).isEqualTo(new Vector(0, 2, 0));
  }

  @Test
  void removalFromWorldPostsRemoveWithUnknownCause() {
    Arrow arrow = mock(Arrow.class);
    AtomicReference<EntityRemoveEvent> seen = new AtomicReference<>();
    bus.register(this, EntityRemoveEvent.class, EventPriority.MONITOR, seen::set);

    Paper121Translations.REMOVE.bridge(new EntityRemoveFromWorldEvent(arrow), core -> bus.post(core, EventPriority.MONITOR));

    assertThat(seen.get().cause()).isEqualTo(EntityRemoveEvent.Cause.UNKNOWN);
  }
}
