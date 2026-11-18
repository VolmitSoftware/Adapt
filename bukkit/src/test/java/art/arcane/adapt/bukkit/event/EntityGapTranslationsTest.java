package art.arcane.adapt.bukkit.event;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.AdaptEventBus;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.entity.CreatureSpawnEvent;
import art.arcane.adapt.platform.event.entity.EntityKnockbackEvent;
import art.arcane.adapt.platform.event.entity.EntityLungeEvent;
import art.arcane.adapt.platform.event.entity.EntityPushedByEntityAttackEvent;
import art.arcane.adapt.platform.event.entity.PiglinBarterEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Vec3;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Piglin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.ItemStack;
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

class EntityGapTranslationsTest {
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

    assertThat(EntityGapTranslations.KNOCKBACK.target(event)).isEqualTo(EntityPushedByEntityAttackEvent.class);
    EntityGapTranslations.KNOCKBACK.bridge(event, core -> bus.post(core, EventPriority.HIGHEST));

    assertThat(seen.get()).isInstanceOf(EntityPushedByEntityAttackEvent.class);
    assertThat(((EntityPushedByEntityAttackEvent) seen.get()).pushedBy()).isSameAs(refs.wrap(attacker, PlayerRef.class));
    assertThat(seen.get().cause()).isEqualTo(EntityKnockbackEvent.Cause.ENTITY_ATTACK);
    assertThat(event.getKnockback()).isEqualTo(new Vector(0, 2, 0));
  }

  @Test
  void lungePowerIsWrittenBack() {
    Player player = mock(Player.class);
    io.papermc.paper.event.entity.EntityLungeEvent event = new io.papermc.paper.event.entity.EntityLungeEvent(player, 2);
    bus.register(this, EntityLungeEvent.class, EventPriority.HIGHEST, core -> core.setPower(core.power() + 3));

    EntityGapTranslations.LUNGE.bridge(event, core -> bus.post(core, EventPriority.HIGHEST));

    assertThat(event.getLungePower()).isEqualTo(5);
  }

  @Test
  void piglinOutcomeListIsReplaced() {
    Piglin piglin = mock(Piglin.class);
    ItemStack input = mock(ItemStack.class);
    when(input.clone()).thenReturn(input);
    ItemStack original = mock(ItemStack.class);
    ItemStack bonus = mock(ItemStack.class);
    List<ItemStack> outcome = new ArrayList<>(List.of(original));
    org.bukkit.event.entity.PiglinBarterEvent event = new org.bukkit.event.entity.PiglinBarterEvent(piglin, input, outcome);
    bus.register(this, PiglinBarterEvent.class, EventPriority.HIGHEST, core -> core.outcome().add(refs.wrap(bonus, ItemRef.class)));

    EntityGapTranslations.PIGLIN_BARTER.bridge(event, core -> bus.post(core, EventPriority.HIGHEST));

    assertThat(event.getOutcome()).containsExactly(original, bonus);
  }

  @Test
  void spawnTranslationSkipsProjectilesAndMapsReasons() {
    Zombie zombie = mock(Zombie.class);
    World world = mock(World.class);
    when(zombie.getLocation()).thenReturn(new Location(world, 1, 2, 3));
    org.bukkit.event.entity.CreatureSpawnEvent spawn = new org.bukkit.event.entity.CreatureSpawnEvent(zombie,
        org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.SPAWNER);
    org.bukkit.event.entity.ProjectileLaunchEvent launch = new org.bukkit.event.entity.ProjectileLaunchEvent(mock(Arrow.class));
    AtomicReference<CreatureSpawnEvent> seen = new AtomicReference<>();
    bus.register(this, CreatureSpawnEvent.class, EventPriority.MONITOR, seen::set);

    assertThat(EntityGapTranslations.SPAWN.target(launch)).isNull();
    assertThat(EntityGapTranslations.SPAWN.target(spawn)).isEqualTo(CreatureSpawnEvent.class);
    EntityGapTranslations.SPAWN.bridge(spawn, core -> bus.post(core, EventPriority.MONITOR));
    assertThat(seen.get().reason()).isEqualTo(CreatureSpawnEvent.Reason.SPAWNER);
    assertThat(seen.get().location().y()).isEqualTo(2.0D);
  }
}
