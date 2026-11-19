package art.arcane.adapt.bukkit.event;

import art.arcane.adapt.TestVersion;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.AdaptEventBus;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.entity.EntityAttemptSmashAttackEvent;
import art.arcane.adapt.platform.event.inventory.ItemCraftedEvent;
import art.arcane.adapt.platform.item.ItemRef;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.event.player.PlayerStatisticIncrementEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class Paper26TranslationsTest {
  private final List<Throwable> failures = new ArrayList<>();
  private final AdaptEventBus bus = new AdaptEventBus((owner, event, error) -> failures.add(error));
  private GapRefs refs;

  @BeforeAll
  static void initializeInventoryTypes() {
    TestVersion.initializeInventoryTypes();
  }

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
  void craftStatisticInCraftingGridPostsCraftedItemFromCursor() {
    Player player = crafter(InventoryType.WORKBENCH);
    ItemStack cursor = mock(ItemStack.class);
    ItemStack crafted = mock(ItemStack.class);
    when(cursor.getType()).thenReturn(Material.STICK);
    when(cursor.clone()).thenReturn(crafted);
    when(player.getItemOnCursor()).thenReturn(cursor);
    PlayerStatisticIncrementEvent event = new PlayerStatisticIncrementEvent(player, Statistic.CRAFT_ITEM, 3, 7, Material.STICK);
    AtomicReference<ItemCraftedEvent> seen = new AtomicReference<>();
    bus.register(this, ItemCraftedEvent.class, EventPriority.MONITOR, seen::set);

    assertThat(Paper26Translations.CRAFTED.target(event)).isEqualTo(ItemCraftedEvent.class);
    Paper26Translations.CRAFTED.bridge(event, core -> bus.post(core, EventPriority.MONITOR));

    verify(crafted).setAmount(4);
    assertThat(seen.get().player()).isSameAs(refs.wrap(player, PlayerRef.class));
    assertThat(seen.get().crafted()).isSameAs(refs.wrap(crafted, ItemRef.class));
  }

  @Test
  void cancelledCraftStatisticStillPostsBecauseTheCraftHappened() {
    Player player = crafter(InventoryType.CRAFTING);
    PlayerStatisticIncrementEvent event = new PlayerStatisticIncrementEvent(player, Statistic.CRAFT_ITEM, 0, 1, Material.STICK);
    event.setCancelled(true);

    assertThat(Paper26Translations.CRAFTED.cancelled(event)).isFalse();
    assertThat(Paper26Translations.CRAFTED.target(event)).isEqualTo(ItemCraftedEvent.class);
  }

  @Test
  void statisticOutsideCraftingGridsOrOtherStatisticsIsSkipped() {
    Player furnace = crafter(InventoryType.FURNACE);
    Player grid = crafter(InventoryType.WORKBENCH);

    assertThat(Paper26Translations.CRAFTED.target(
        new PlayerStatisticIncrementEvent(furnace, Statistic.CRAFT_ITEM, 0, 1, Material.IRON_INGOT))).isNull();
    assertThat(Paper26Translations.CRAFTED.target(
        new PlayerStatisticIncrementEvent(grid, Statistic.MINE_BLOCK, 0, 1, Material.STONE))).isNull();
  }

  @Test
  void fallingMaceHitPostsALandedSmashAttempt() {
    Material mace = Material.matchMaterial("mace");
    assumeTrue(mace != null, "no mace before 1.21");
    Player attacker = attacker(mace, 3.0F, false);
    LivingEntity target = mock(LivingEntity.class);
    EntityDamageByEntityEvent hit = hit(attacker, target, EntityDamageEvent.DamageCause.ENTITY_ATTACK);
    AtomicReference<EntityAttemptSmashAttackEvent> seen = new AtomicReference<>();
    bus.register(this, EntityAttemptSmashAttackEvent.class, EventPriority.MONITOR, seen::set);

    assertThat(Paper26Translations.SMASH_ATTEMPT.target(hit)).isEqualTo(EntityAttemptSmashAttackEvent.class);
    Paper26Translations.SMASH_ATTEMPT.bridge(hit, core -> bus.post(core, EventPriority.MONITOR));

    assertThat(seen.get().entity()).isSameAs(refs.wrap(attacker, PlayerRef.class));
    assertThat(seen.get().target()).isSameAs(refs.wrap(target, LivingRef.class));
    assertThat(seen.get().smashes()).isTrue();
  }

  @Test
  void hitsThatAreNoSmashPostNothing() {
    Material mace = Material.matchMaterial("mace");
    assumeTrue(mace != null, "no mace before 1.21");
    LivingEntity target = mock(LivingEntity.class);

    assertThat(Paper26Translations.SMASH_ATTEMPT.target(
        hit(attacker(mace, 1.5F, false), target, EntityDamageEvent.DamageCause.ENTITY_ATTACK))).isNull();
    assertThat(Paper26Translations.SMASH_ATTEMPT.target(
        hit(attacker(mace, 3.0F, true), target, EntityDamageEvent.DamageCause.ENTITY_ATTACK))).isNull();
    assertThat(Paper26Translations.SMASH_ATTEMPT.target(
        hit(attacker(Material.IRON_SWORD, 3.0F, false), target, EntityDamageEvent.DamageCause.ENTITY_ATTACK))).isNull();
    assertThat(Paper26Translations.SMASH_ATTEMPT.target(
        hit(attacker(mace, 3.0F, false), target, EntityDamageEvent.DamageCause.THORNS))).isNull();
  }

  @Test
  void swordHitsAreNeverSmashesOnAnyVersion() {
    assertThat(Paper26Translations.SMASH_ATTEMPT.target(hit(attacker(Material.IRON_SWORD, 3.0F, false),
        mock(LivingEntity.class), EntityDamageEvent.DamageCause.ENTITY_ATTACK))).isNull();
  }

  private static Player attacker(Material held, float fallDistance, boolean gliding) {
    Player attacker = mock(Player.class);
    EntityEquipment equipment = mock(EntityEquipment.class);
    ItemStack weapon = mock(ItemStack.class);
    when(weapon.getType()).thenReturn(held);
    when(equipment.getItemInMainHand()).thenReturn(weapon);
    when(attacker.getEquipment()).thenReturn(equipment);
    when(attacker.getFallDistance()).thenReturn(fallDistance);
    when(attacker.isGliding()).thenReturn(gliding);
    return attacker;
  }

  private static EntityDamageByEntityEvent hit(LivingEntity attacker, LivingEntity target, EntityDamageEvent.DamageCause cause) {
    EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
    when(event.getCause()).thenReturn(cause);
    when(event.getDamager()).thenReturn(attacker);
    when(event.getEntity()).thenReturn(target);
    return event;
  }

  private static Player crafter(InventoryType grid) {
    Player player = mock(Player.class, RETURNS_DEEP_STUBS);
    when(player.getOpenInventory().getTopInventory().getType()).thenReturn(grid);
    return player;
  }
}
