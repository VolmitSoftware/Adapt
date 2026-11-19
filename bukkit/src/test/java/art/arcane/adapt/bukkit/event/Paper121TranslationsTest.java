package art.arcane.adapt.bukkit.event;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.AdaptEventBus;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.entity.EntityKnockbackEvent;
import art.arcane.adapt.platform.event.entity.EntityPushedByEntityAttackEvent;
import art.arcane.adapt.platform.value.Vec3;
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
  void pushedByAttackPostsSubtypeAndWritesBackKnockback() {
    Zombie victim = mock(Zombie.class);
    Player attacker = mock(Player.class);
    io.papermc.paper.event.entity.EntityPushedByEntityAttackEvent event = new io.papermc.paper.event.entity.EntityPushedByEntityAttackEvent(
        victim, io.papermc.paper.event.entity.EntityKnockbackEvent.Cause.ENTITY_ATTACK, attacker, new Vector(1, 0, 0));
    AtomicReference<EntityKnockbackEvent> seen = new AtomicReference<>();
    bus.register(this, EntityKnockbackEvent.class, EventPriority.HIGHEST, core -> {
      seen.set(core);
      core.setKnockback(new Vec3(0, 2, 0));
    });

    assertThat(Paper121Translations.KNOCKBACK.target(event)).isEqualTo(EntityPushedByEntityAttackEvent.class);
    Paper121Translations.KNOCKBACK.bridge(event, core -> bus.post(core, EventPriority.HIGHEST));

    assertThat(seen.get()).isInstanceOf(EntityPushedByEntityAttackEvent.class);
    assertThat(((EntityPushedByEntityAttackEvent) seen.get()).pushedBy()).isSameAs(refs.wrap(attacker, PlayerRef.class));
    assertThat(seen.get().cause()).isEqualTo(EntityKnockbackEvent.Cause.ENTITY_ATTACK);
    assertThat(event.getKnockback()).isEqualTo(new Vector(0, 2, 0));
  }
}
