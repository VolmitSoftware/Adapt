package art.arcane.adapt.bukkit.event;

import art.arcane.adapt.platform.event.AdaptEventBus;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.player.PlayerDeathEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EntityDamageTranslationsTest {
  private final List<Throwable> failures = new ArrayList<>();
  private final AdaptEventBus bus = new AdaptEventBus((owner, event, error) -> failures.add(error));
  private FakeRefs refs;
  private Player player;

  @BeforeEach
  void setUp() {
    refs = new FakeRefs();
    player = mock(Player.class);
    lenient().when(player.getUniqueId()).thenReturn(UUID.randomUUID());
  }

  @AfterEach
  void tearDown() {
    refs.close();
    assertThat(failures).isEmpty();
  }

  @Test
  void damageByEntityTranslatesAndWritesBackDamage() {
    Zombie victim = mock(Zombie.class);
    DamageSource source = mock(DamageSource.class);
    when(source.getCausingEntity()).thenReturn(player);
    org.bukkit.event.entity.EntityDamageByEntityEvent event = new org.bukkit.event.entity.EntityDamageByEntityEvent(
        player, victim, org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK, source, 6.0D);
    AtomicReference<EntityDamageEvent> seen = new AtomicReference<>();
    AtomicReference<Double> finalSeen = new AtomicReference<>();
    bus.register(this, EntityDamageEvent.class, EventPriority.HIGH, core -> {
      seen.set(core);
      core.setDamage(2.5D);
      finalSeen.set(core.finalDamage());
    });

    assertThat(EntityTranslations.DAMAGE.target(event)).isEqualTo(EntityDamageByEntityEvent.class);
    EntityTranslations.DAMAGE.bridge(event, core -> bus.post(core, EventPriority.HIGH));

    assertThat(seen.get()).isInstanceOf(EntityDamageByEntityEvent.class);
    EntityDamageByEntityEvent byEntity = (EntityDamageByEntityEvent) seen.get();
    assertThat(byEntity.cause()).isEqualTo(DamageCause.ENTITY_ATTACK);
    assertThat(byEntity.damageType()).isEqualTo(Id.minecraft("generic"));
    assertThat(byEntity.damager()).isSameAs(byEntity.causingEntity());
    assertThat(finalSeen.get()).isCloseTo(2.5D, within(1.0e-9));
    assertThat(event.getDamage()).isCloseTo(2.5D, within(1.0e-9));
    assertThat(event.isCancelled()).isFalse();
  }

  @Test
  void deathDropsListIsReplacedAndPlayerFlagsWrittenBack() {
    ItemStack first = mock(ItemStack.class);
    ItemStack second = mock(ItemStack.class);
    ItemStack added = mock(ItemStack.class);
    ItemRef addedRef = refs.wrap(added, ItemRef.class);
    DamageSource source = mock(DamageSource.class);
    List<ItemStack> drops = new ArrayList<>(List.of(first, second));
    org.bukkit.event.entity.PlayerDeathEvent event = new org.bukkit.event.entity.PlayerDeathEvent(player, source, drops, 10,
        "died");
    bus.register(this, PlayerDeathEvent.class, EventPriority.NORMAL, core -> {
      core.drops().removeFirst();
      core.drops().add(addedRef);
      core.setDroppedExp(0);
      core.setKeepInventory(true);
      core.setNewLevel(3);
    });

    EntityTranslations.DEATH.bridge(event, core -> bus.post(core, EventPriority.NORMAL));

    assertThat(event.getDrops()).containsExactly(second, added);
    assertThat(event.getDrops()).isSameAs(drops);
    assertThat(event.getDroppedExp()).isZero();
    assertThat(event.getKeepInventory()).isTrue();
    assertThat(event.getNewLevel()).isEqualTo(3);
  }

  @Test
  void untouchedDeathDropsAreNotRewritten() {
    ItemStack first = mock(ItemStack.class);
    ItemStack second = mock(ItemStack.class);
    AtomicInteger clears = new AtomicInteger();
    List<ItemStack> drops = new ArrayList<>(List.of(first, second)) {
      @Override
      public void clear() {
        clears.incrementAndGet();
        super.clear();
      }
    };
    org.bukkit.event.entity.PlayerDeathEvent event = new org.bukkit.event.entity.PlayerDeathEvent(player,
        mock(DamageSource.class), drops, 10, "died");
    bus.register(this, PlayerDeathEvent.class, EventPriority.NORMAL, core -> core.setKeepLevel(true));

    EntityTranslations.DEATH.bridge(event, core -> bus.post(core, EventPriority.NORMAL));

    assertThat(clears).hasValue(0);
    assertThat(event.getDrops()).containsExactly(first, second);
  }

  @Test
  void entityDeathHandlersReceivePlayerDeaths() {
    DamageSource source = mock(DamageSource.class);
    org.bukkit.event.entity.PlayerDeathEvent event = new org.bukkit.event.entity.PlayerDeathEvent(player, source,
        new ArrayList<>(), 0, "died");
    List<String> seen = new ArrayList<>();
    bus.register(this, art.arcane.adapt.platform.event.entity.EntityDeathEvent.class, EventPriority.NORMAL,
        core -> seen.add(core.getClass().getSimpleName()));

    assertThat(EntityTranslations.DEATH.target(event)).isEqualTo(PlayerDeathEvent.class);
    EntityTranslations.DEATH.bridge(event, core -> bus.post(core, EventPriority.NORMAL));

    assertThat(seen).containsExactly("PlayerDeathEvent");
  }
}
