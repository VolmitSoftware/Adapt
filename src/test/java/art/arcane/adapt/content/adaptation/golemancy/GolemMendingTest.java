package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemCoreType;
import art.arcane.adapt.api.golem.GolemHandle;
import art.arcane.adapt.api.golem.GolemMend;
import art.arcane.adapt.api.golem.GolemRegistry;
import art.arcane.adapt.api.golem.GolemRepairXp;
import art.arcane.adapt.api.golem.GolemRoster;
import art.arcane.adapt.api.golem.GolemSettings;
import art.arcane.adapt.api.golem.GolemSnapshot;
import art.arcane.adapt.api.golem.GolemState;
import art.arcane.adapt.api.golem.GolemVitals;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.item.GolemItemKind;
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.api.golem.item.PackedGolem;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.CopperGolem;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Snowman;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.PluginManager;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static org.mockito.Answers.RETURNS_SELF;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GolemMendingTest extends AdaptTestBase {
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-00000000b1a1");
  private static final double MAX_HEALTH = 20D;

  private final List<Double> repairs = new ArrayList<>();
  private GolemMending adaptation;
  private MockedStatic<GolemancyRuntime> runtime;
  private MockedStatic<J> scheduling;
  private MockedStatic<GolemVitals> vitals;
  private MockedStatic<Bukkit> bukkit;
  private MockedStatic<GolemItems> items;
  private GolemRegistrations<GolemMend.Mender> menders;
  private World world;
  private Player player;
  private PlayerInventory inventory;

  @BeforeEach
  void construct() {
    when(plugin.namespace()).thenReturn("adapt");
    runtime = mockStatic(GolemancyRuntime.class);
    runtime.when(() -> GolemancyRuntime.handle(any())).thenReturn(Optional.empty());
    runtime.when(GolemancyRuntime::settings).thenReturn(GolemSettings.defaults());
    runtime.when(GolemancyRuntime::registry).thenReturn(Optional.empty());
    scheduling = mockStatic(J.class);
    scheduling.when(() -> J.isOwnedByCurrentRegion(any(Entity.class))).thenReturn(true);
    scheduling.when(() -> J.runEntity(any(Entity.class), any(Runnable.class))).thenReturn(true);
    scheduling.when(() -> J.runEntity(any(Entity.class), any(Runnable.class), anyInt())).thenReturn(true);
    vitals = mockStatic(GolemVitals.class);
    vitals.when(() -> GolemVitals.maxHealth(any())).thenReturn(MAX_HEALTH);
    vitals.when(() -> GolemVitals.heal(any(), anyDouble(), any())).thenCallRealMethod();
    vitals.when(() -> GolemVitals.fullHealth(any())).thenCallRealMethod();
    bukkit = mockStatic(Bukkit.class);
    bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
    items = mockStatic(GolemItems.class, CALLS_REAL_METHODS);
    menders = GolemRegistrations.menders();
    adaptation = new GolemMending();
    world = mock(World.class);
    lenient().when(world.getUID()).thenReturn(WORLD);
    player = mock(Player.class);
    inventory = mock(PlayerInventory.class);
    lenient().when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    lenient().when(player.getName()).thenReturn("Alex");
    lenient().when(player.getInventory()).thenReturn(inventory);
    lenient().when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
    lenient().when(player.getEyeLocation()).thenReturn(new Location(world, 0.5D, 65.6D, 2D));
    GolemRepairXp.setSink((healer, golem, healed) -> repairs.add(healed));
  }

  @AfterEach
  void close() {
    GolemRepairXp.setSink(GolemRepairXp.Sink.NONE);
    menders.close();
    items.close();
    bukkit.close();
    vitals.close();
    scheduling.close();
    runtime.close();
  }

  @Test
  void defaultsMatchTheBrief() {
    GolemMending.Config config = new GolemMending.Config();

    assertThat(config.healBonusByLevel).containsExactly(0.2D, 0.4D, 0.6D, 0.8D, 1.0D);
    assertThat(config.copperIngotHeal).isEqualTo(4D);
    assertThat(config.snowBlockHeal).isEqualTo(2D);
    assertThat(config.boneMealHeal).isEqualTo(2D);
    assertThat(config.ownerRegenLevel).isEqualTo(2);
    assertThat(config.ownerRegenAmount).isEqualTo(0.5D);
    assertThat(config.ownerRegenRadius).isEqualTo(16D);
    assertThat(config.homeRegenLevel).isEqualTo(3);
    assertThat(config.homeRegenAmount).isEqualTo(1D);
    assertThat(config.homeRegenRadius).isEqualTo(4D);
    assertThat(config.regenIntervalSeconds).isEqualTo(5);
    assertThat(config.shatterLevel).isEqualTo(4);
    assertThat(config.rebornLevel).isEqualTo(5);
    assertThat(config.rebornResistanceSeconds).isEqualTo(10);
    assertThat(config.rebornResistanceAmplifier).isEqualTo(1);
    assertThat(config.requires).containsExactly("golem-animus-binding:1");
    assertThat(config.maxLevel).isEqualTo(5);
  }

  @Test
  void configurationLoadsWithFiveLevelsAndRegistersItsMenderOnlyAtRuntime() {
    assertThat(adaptation.reloadConfigFromDisk(false)).isTrue();
    assertThat(adaptation.getName()).isEqualTo("golem-mending");
    assertThat(adaptation.getMaxLevel()).isEqualTo(5);
    for (int level = 1; level <= adaptation.getMaxLevel(); level++) {
      assertThat(adaptation.getCostFor(level)).as("cost at level %s", level).isPositive();
    }
    assertThat(adaptation.isRuntimeRegistered()).isFalse();
    assertThat(menders.registered()).isEmpty();

    adaptation.onRuntimeActivated();
    try {
      assertThat(menders.registered()).singleElement().satisfies(mender -> assertThat(mender.regenIntervalMillis()).isEqualTo(5_000L));
    } finally {
      adaptation.unregister();
    }
    assertThat(menders.unregistered()).isEqualTo(menders.registered());
  }

  @Test
  void loadingClampsNegativeAmountsTheIntervalAndTheAmplifier() {
    String toml = "copperIngotHeal = -3.0\nownerRegenRadius = -1.0\nregenIntervalSeconds = 0\nrebornResistanceSeconds = -4\n"
        + "rebornResistanceAmplifier = 900\n";
    assertThat(adaptation.reloadConfigSnapshot(toml, dataFolder.toPath().resolve("clamp.toml").toFile(), false)).isTrue();

    GolemMending.Config config = adaptation.getConfig();
    assertThat(config.copperIngotHeal).isZero();
    assertThat(config.ownerRegenRadius).isZero();
    assertThat(config.regenIntervalSeconds).isEqualTo(1);
    assertThat(config.rebornResistanceSeconds).isZero();
    assertThat(config.rebornResistanceAmplifier).isEqualTo(255);
  }

  @Test
  void everyLevelShowsItsHealingRegenAndUnlocks() {
    String heals = "+ Copper Ingots, Snow Blocks and Bone Meal Heal Your Golems";
    assertThat(lore(1)).containsExactly("+ 20% Bonus Healing from Repairs", heals);
    assertThat(lore(2)).containsExactly("+ 40% Bonus Healing from Repairs", heals, "+ 0.5 Health Regenerated near You",
        "* 5 Seconds Between Regeneration");
    assertThat(lore(3)).containsExactly("+ 60% Bonus Healing from Repairs", heals, "+ 0.5 Health Regenerated near You",
        "+ 1 Health Regenerated While Idle at Home", "* 5 Seconds Between Regeneration");
    assertThat(lore(4)).containsExactly("+ 80% Bonus Healing from Repairs", heals, "+ 0.5 Health Regenerated near You",
        "+ 1 Health Regenerated While Idle at Home", "* 5 Seconds Between Regeneration", "+ Shattered Core: Rebuild Fallen Golems");
    assertThat(lore(5)).containsExactly("+ 100% Bonus Healing from Repairs", heals, "+ 0.5 Health Regenerated near You",
        "+ 1 Health Regenerated While Idle at Home", "* 5 Seconds Between Regeneration", "+ Shattered Core: Rebuild Fallen Golems",
        "+ 10 Seconds of Resistance for Reborn Golems", "+ Reborn Golems Keep Their Copper State");
  }

  @Test
  void aCopperIngotHealsYourBoundCopperGolemByTheLevelBonusAndFeedsRepairXp() {
    AtomicReference<Double> health = new AtomicReference<>(5D);
    CopperGolem golem = living(CopperGolem.class, health);
    bound(golem, GolemBody.COPPER, player.getUniqueId());
    ItemStack ingots = held(Material.COPPER_INGOT, 3, EquipmentSlot.HAND);
    GolemMending learned = learned(1);
    PlayerInteractEntityEvent click = click(golem, EquipmentSlot.HAND);

    learned.on(click);

    assertThat(health.get()).isCloseTo(9.8D, offset(1e-9));
    assertThat(click.isCancelled()).isTrue();
    verify(ingots).setAmount(2);
    verify(learned).mendFx(golem, MendingRules.Remedy.COPPER_INGOT);
    assertThat(repairs).singleElement().satisfies(healed -> assertThat(healed).isCloseTo(4.8D, offset(1e-9)));
  }

  @Test
  void aSnowBlockHealsYourFrostGolemAndHealingStopsAtFullHealth() {
    AtomicReference<Double> health = new AtomicReference<>(18D);
    Snowman golem = living(Snowman.class, health);
    bound(golem, GolemBody.FROST, player.getUniqueId());
    ItemStack snow = held(Material.SNOW_BLOCK, 1, EquipmentSlot.OFF_HAND);
    GolemMending learned = learned(5);

    learned.on(click(golem, EquipmentSlot.OFF_HAND));

    assertThat(health.get()).isEqualTo(MAX_HEALTH);
    verify(inventory).setItem(EquipmentSlot.OFF_HAND, null);
    assertThat(repairs).singleElement().satisfies(healed -> assertThat(healed).isCloseTo(2D, offset(1e-9)));
  }

  @Test
  void aDormantFrostGolemIsNeverSpentAsASnowBlockRemedy() {
    AtomicReference<Double> health = new AtomicReference<>(10D);
    Snowman golem = living(Snowman.class, health);
    bound(golem, GolemBody.FROST, player.getUniqueId());
    ItemStack dormant = held(Material.SNOW_BLOCK, 1, EquipmentSlot.HAND);
    items.when(() -> GolemItems.isGolemItem(dormant)).thenReturn(true);
    GolemMending learned = learned(5);
    PlayerInteractEntityEvent click = click(golem, EquipmentSlot.HAND);

    learned.on(click);

    assertThat(health.get()).isEqualTo(10D);
    assertThat(click.isCancelled()).isFalse();
    verify(dormant, never()).setAmount(anyInt());
    verify(inventory, never()).setItem(any(EquipmentSlot.class), any());
    assertThat(repairs).isEmpty();
  }

  @Test
  void aGolemAtFullHealthRefusesTheItem() {
    AtomicReference<Double> health = new AtomicReference<>(MAX_HEALTH);
    CopperGolem golem = living(CopperGolem.class, health);
    bound(golem, GolemBody.COPPER, player.getUniqueId());
    ItemStack ingots = held(Material.COPPER_INGOT, 3, EquipmentSlot.HAND);
    GolemMending learned = learned(3);
    PlayerInteractEntityEvent click = click(golem, EquipmentSlot.HAND);

    learned.on(click);

    assertThat(health.get()).isEqualTo(MAX_HEALTH);
    verify(ingots, never()).setAmount(anyInt());
    verify(learned, never()).mendFx(any(), any());
    assertThat(repairs).isEmpty();
    assertThat(click.isCancelled()).isTrue();
  }

  @Test
  void creativePlayersKeepTheirItem() {
    AtomicReference<Double> health = new AtomicReference<>(5D);
    CopperGolem golem = living(CopperGolem.class, health);
    bound(golem, GolemBody.COPPER, player.getUniqueId());
    ItemStack ingots = held(Material.COPPER_INGOT, 3, EquipmentSlot.HAND);
    when(player.getGameMode()).thenReturn(GameMode.CREATIVE);

    learned(1).on(click(golem, EquipmentSlot.HAND));

    assertThat(health.get()).isGreaterThan(5D);
    verify(ingots, never()).setAmount(anyInt());
  }

  @Test
  void itemsOnlyHealYourOwnGolemOfTheirBodyAndNeedTheAdaptation() {
    AtomicReference<Double> health = new AtomicReference<>(5D);
    CopperGolem someoneElses = living(CopperGolem.class, health);
    bound(someoneElses, GolemBody.COPPER, UUID.randomUUID());
    Snowman frost = living(Snowman.class, health);
    bound(frost, GolemBody.FROST, player.getUniqueId());
    CopperGolem wild = living(CopperGolem.class, health);
    CopperGolem mine = living(CopperGolem.class, health);
    bound(mine, GolemBody.COPPER, player.getUniqueId());
    held(Material.COPPER_INGOT, 3, EquipmentSlot.HAND);
    GolemMending learned = learned(5);
    List<PlayerInteractEntityEvent> clicks = List.of(click(someoneElses, EquipmentSlot.HAND), click(frost, EquipmentSlot.HAND),
        click(wild, EquipmentSlot.HAND));

    for (PlayerInteractEntityEvent click : clicks) {
      learned.on(click);
    }
    PlayerInteractEntityEvent unlearned = click(mine, EquipmentSlot.HAND);
    learned(0).on(unlearned);

    assertThat(health.get()).isEqualTo(5D);
    assertThat(clicks).noneMatch(PlayerInteractEntityEvent::isCancelled);
    assertThat(unlearned.isCancelled()).isFalse();
  }

  @Test
  void boneMealHealsYourOwnTragoulServantWithoutRepairXp() {
    AtomicReference<Double> health = new AtomicReference<>(4D);
    Skeleton servant = living(Skeleton.class, health);
    servantOf(servant, player.getUniqueId());
    Skeleton strangers = living(Skeleton.class, new AtomicReference<>(4D));
    servantOf(strangers, UUID.randomUUID());
    held(Material.BONE_MEAL, 5, EquipmentSlot.HAND);
    GolemMending learned = learned(1);

    learned.on(click(servant, EquipmentSlot.HAND));
    learned.on(click(strangers, EquipmentSlot.HAND));

    assertThat(health.get()).isCloseTo(6.4D, offset(1e-9));
    assertThat(strangers.getHealth()).isEqualTo(4D);
    assertThat(repairs).isEmpty();
  }

  @Test
  void boneMealAlsoHealsYourOwnBoneGolemButNotSomeoneElses() {
    AtomicReference<Double> health = new AtomicReference<>(4D);
    Skeleton mine = living(Skeleton.class, health);
    bound(mine, GolemBody.BONE, player.getUniqueId());
    Skeleton strangers = living(Skeleton.class, new AtomicReference<>(4D));
    bound(strangers, GolemBody.BONE, UUID.randomUUID());
    held(Material.BONE_MEAL, 5, EquipmentSlot.HAND);
    GolemMending learned = learned(1);

    learned.on(click(mine, EquipmentSlot.HAND));
    learned.on(click(strangers, EquipmentSlot.HAND));

    assertThat(health.get()).isCloseTo(6.4D, offset(1e-9));
    assertThat(strangers.getHealth()).isEqualTo(4D);
    assertThat(repairs).isEmpty();
  }

  @Test
  void aShatterLevelMenderNeverShattersABoneGolem() {
    GolemHandle handle = mock(GolemHandle.class);
    when(handle.body()).thenReturn(GolemBody.BONE);
    when(handle.isValidated()).thenReturn(true);
    GolemMending learned = learned(5);
    doReturn(5).when(learned).ownerLevel(handle);
    doReturn(true).when(learned).ownerOnline(handle);
    learned.onRuntimeActivated();
    try {
      assertThat(menders.registered().getFirst().shatters(handle)).isFalse();
    } finally {
      learned.unregister();
    }
  }

  @Test
  void anIronIngotRepairOfABoundIronGolemGainsTheBonusOfTheVanillaTwentyFive() {
    IronGolem golem = living(IronGolem.class, new AtomicReference<>(50D));
    bound(golem, GolemBody.IRON, UUID.randomUUID());
    held(Material.IRON_INGOT, 1, EquipmentSlot.HAND);
    GolemMending learned = learned(1);
    doReturn(3).when(learned).ownerLevel(any(GolemHandle.class));

    learned.onIngotRepair(click(golem, EquipmentSlot.HAND));
    EntityRegainHealthEvent repair = new EntityRegainHealthEvent(golem, 25D, RegainReason.CUSTOM);
    learned.on(repair);
    EntityRegainHealthEvent later = new EntityRegainHealthEvent(golem, 25D, RegainReason.CUSTOM);
    learned.on(later);

    assertThat(repair.getAmount()).isCloseTo(40D, offset(1e-9));
    assertThat(later.getAmount()).isEqualTo(25D);
    assertThat(learned.hasTickDemand()).isFalse();
  }

  @Test
  void theMenderShattersOnlyValidatedGolemsOfAnOnlineOwnerAtTheShatterLevel() {
    GolemMending learned = learned(0);
    GolemHandle handle = mock(GolemHandle.class);
    when(handle.isValidated()).thenReturn(true);
    doReturn(true).when(learned).ownerOnline(handle);
    doReturn(4).when(learned).ownerLevel(handle);
    learned.onRuntimeActivated();
    try {
      GolemMend.Mender mender = menders.registered().getFirst();
      assertThat(mender.shatters(handle)).isTrue();
      doReturn(3).when(learned).ownerLevel(handle);
      assertThat(mender.shatters(handle)).isFalse();
      doReturn(5).when(learned).ownerLevel(handle);
      doReturn(false).when(learned).ownerOnline(handle);
      assertThat(mender.shatters(handle)).isFalse();
      doReturn(true).when(learned).ownerOnline(handle);
      when(handle.isValidated()).thenReturn(false);
      assertThat(mender.shatters(handle)).isFalse();
      assertThat(mender.regen(handle, new GolemMend.RegenSenses(2D, 2D, true))).isEqualTo(1D);
    } finally {
      learned.unregister();
    }
  }

  @Test
  void aReadyRebuildRestoresThenConsumesTheCoreThenSavesThePlayer() {
    Rebuild rebuild = rebuild(4);

    rebuild.adaptation.on(rebuild.click);

    assertThat(rebuild.click.isCancelled()).isTrue();
    InOrder order = inOrder(rebuild.registry, inventory, player);
    order.verify(rebuild.registry).restore(player, rebuild.golem, new GolemRegistry.Restore(rebuild.item, GolemRoster.Status.SHATTERED));
    order.verify(inventory).setItemInMainHand(null);
    order.verify(player).saveData();
    verify(rebuild.handle).restoreFullHealth();
    verify(rebuild.adaptation, never()).applyRebornBoon(any(), any());
    verify(rebuild.adaptation).rebornFx(rebuild.golem);
  }

  @Test
  void levelFiveRebornGolemsKeepTheirCopperAndGainResistance() {
    Rebuild rebuild = rebuild(5);

    rebuild.adaptation.on(rebuild.click);

    verify(rebuild.adaptation).applyRebornBoon(rebuild.golem, rebuild.snapshot);
    verify(player).saveData();
  }

  @Test
  void aCoreWhoseGolemWasReleasedCrumblesWithoutRebuilding() {
    Rebuild rebuild = rebuild(5);
    rebuild.roster.remove(rebuild.snapshot.id());

    rebuild.adaptation.on(rebuild.click);

    verify(rebuild.registry, never()).restore(any(), any(), any());
    verify(inventory).setItemInMainHand(null);
    verify(rebuild.adaptation).crumbleFx(player);
    verify(player, never()).saveData();
  }

  @Test
  void anotherPlayersCoreIsRefusedAndKept() {
    Rebuild rebuild = rebuild(5);
    PackedGolem foreign = new PackedGolem(rebuild.snapshot.id(), UUID.randomUUID(), rebuild.snapshot.toJson());
    ItemStack core = rebuild.core;
    items.when(() -> GolemItems.packedGolemOf(core)).thenReturn(Optional.of(foreign));

    rebuild.adaptation.on(rebuild.click);

    verify(rebuild.registry, never()).restore(any(), any(), any());
    verify(inventory, never()).setItemInMainHand(any());
  }

  @Test
  void rebuildingNeedsTheShatterLevelAndABodyInReach() {
    Rebuild locked = rebuild(3);
    locked.adaptation.on(locked.click);
    verify(locked.registry, never()).restore(any(), any(), any());

    Rebuild far = rebuild(5);
    when(player.getEyeLocation()).thenReturn(new Location(world, 0.5D, 65.6D, 9D));
    far.adaptation.on(far.click);
    verify(far.registry, never()).restore(any(), any(), any());
    verify(inventory, never()).setItemInMainHand(any());
  }

  @Test
  void sneakClickingAMobThatIsNoGolemBodyWithAShatteredCoreIsLeftAlone() {
    Rebuild rebuild = rebuild(5);
    PlayerInteractEntityEvent cow = click(living(Cow.class, new AtomicReference<>(10D)), EquipmentSlot.HAND);
    PlayerInteractEntityEvent skeleton = click(living(Skeleton.class, new AtomicReference<>(10D)), EquipmentSlot.HAND);

    rebuild.adaptation.on(cow);
    rebuild.adaptation.on(skeleton);

    assertThat(cow.isCancelled()).isFalse();
    assertThat(skeleton.isCancelled()).isFalse();
    verify(rebuild.registry, never()).restore(any(), any(), any());
    verify(inventory, never()).setItemInMainHand(any());
  }

  @Test
  void aCoreWhoseEntryChangedBeforeTheSwapCrumbles() {
    Rebuild rebuild = rebuild(5);
    when(rebuild.registry.restore(any(), any(), any())).thenReturn(Optional.empty());

    rebuild.adaptation.on(rebuild.click);

    verify(inventory).setItemInMainHand(null);
    verify(player, never()).saveData();
    verify(rebuild.handle, never()).restoreFullHealth();
  }

  @Test
  void anOlderCopyOfTheCoreCrumblesBecauseTheEntryIsSealedToTheNewestOne() {
    Rebuild rebuild = rebuild(5);
    rebuild.roster.unstow(rebuild.snapshot.id(), GolemRoster.Status.SHATTERED, rebuild.item.state());
    rebuild.roster.stow(rebuild.snapshot.id(), GolemRoster.Status.SHATTERED, "{\"newer\":true}");

    rebuild.adaptation.on(rebuild.click);

    verify(rebuild.registry, never()).restore(any(), any(), any());
    verify(inventory).setItemInMainHand(null);
    verify(rebuild.adaptation).crumbleFx(player);
  }

  @Test
  void aFailedRestoreKeepsTheCore() {
    Rebuild rebuild = rebuild(5);
    doThrow(new IllegalStateException("boom")).when(rebuild.registry).restore(any(), any(), any());

    rebuild.adaptation.on(rebuild.click);

    verify(inventory, never()).setItemInMainHand(any());
    verify(player, never()).saveData();
  }

  private Rebuild rebuild(int level) {
    UUID golemId = UUID.randomUUID();
    GolemState state = GolemState.create(new GolemState.Seed(golemId, player.getUniqueId(), GolemBody.IRON, GolemCoreType.GUARDIAN,
        GolemState.Anchor.of(WORLD, 0, 64, 0)));
    GolemSnapshot snapshot = GolemSnapshot.capture(state, new GolemSnapshot.Traits(null, 0D, 0, false, true), 1L);
    GolemRoster roster = new GolemRoster();
    roster.add(new GolemRoster.Binding(golemId, GolemBody.IRON, GolemCoreType.GUARDIAN, WORLD, 0L, 0L));
    PackedGolem packed = new PackedGolem(golemId, player.getUniqueId(), snapshot.toJson());
    roster.stow(golemId, GolemRoster.Status.SHATTERED, packed.state());
    IronGolem golem = living(IronGolem.class, new AtomicReference<>(100D));
    when(golem.isPlayerCreated()).thenReturn(true);
    when(golem.getBoundingBox()).thenReturn(new BoundingBox(0D, 64D, 0D, 1D, 65D, 1D));
    ItemStack core = held(Material.FIREWORK_STAR, 1, EquipmentSlot.HAND);
    when(inventory.getItemInMainHand()).thenReturn(core);
    when(player.isSneaking()).thenReturn(true);
    items.when(() -> GolemItems.kindOf(core)).thenReturn(Optional.of(GolemItemKind.SHATTERED_CORE));
    items.when(() -> GolemItems.packedGolemOf(core)).thenReturn(Optional.of(packed));
    GolemRegistry registry = mock(GolemRegistry.class);
    GolemHandle handle = mock(GolemHandle.class);
    when(registry.restoreBlock(golem, snapshot)).thenReturn(Optional.empty());
    lenient().when(registry.restore(any(), any(), any())).thenReturn(Optional.of(handle));
    runtime.when(GolemancyRuntime::registry).thenReturn(Optional.of(registry));
    GolemAnimusBinding binding = mock(GolemAnimusBinding.class);
    when(binding.bodyUnlocked(player, GolemBody.IRON)).thenReturn(true);
    GolemMending learned = learned(level);
    doReturn(Optional.of(binding)).when(learned).binding();
    doReturn(Optional.of(roster)).when(learned).roster(player);
    lenient().doNothing().when(learned).applyRebornBoon(any(), any());
    return new Rebuild(learned, registry, roster, golem, core, packed, snapshot, handle, click(golem, EquipmentSlot.HAND));
  }

  private GolemMending learned(int level) {
    GolemMending learned = spy(adaptation);
    lenient().doReturn(level).when(learned).getActiveLevel(player);
    lenient().doReturn(true).when(learned).canInteract(any(Player.class), any(Location.class));
    lenient().doReturn(true).when(learned).canBlockPlace(any(Player.class), any(Location.class));
    lenient().doNothing().when(learned).mendFx(any(), any());
    lenient().doNothing().when(learned).rebornFx(any());
    lenient().doNothing().when(learned).crumbleFx(any());
    return learned;
  }

  private void bound(Mob golem, GolemBody body, UUID owner) {
    GolemHandle handle = mock(GolemHandle.class);
    lenient().when(handle.body()).thenReturn(body);
    lenient().when(handle.owner()).thenReturn(owner);
    lenient().when(handle.entity()).thenReturn(golem);
    runtime.when(() -> GolemancyRuntime.handle(golem)).thenReturn(Optional.of(handle));
  }

  private void servantOf(Skeleton servant, UUID owner) {
    PersistentDataContainer data = servant.getPersistentDataContainer();
    when(data.has(any(NamespacedKey.class), eq(PersistentDataType.STRING))).thenReturn(true);
    when(data.get(any(NamespacedKey.class), eq(PersistentDataType.STRING))).thenReturn(owner.toString());
  }

  private <T extends LivingEntity> T living(Class<T> type, AtomicReference<Double> health) {
    T entity = mock(type);
    lenient().when(entity.getUniqueId()).thenReturn(UUID.randomUUID());
    lenient().when(entity.getLocation()).thenReturn(new Location(world, 0.5D, 64D, 0.5D));
    lenient().when(entity.getWorld()).thenReturn(world);
    lenient().when(entity.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));
    lenient().when(entity.getHealth()).thenAnswer(invocation -> health.get());
    lenient().doAnswer(invocation -> {
      health.set(invocation.getArgument(0));
      return null;
    }).when(entity).setHealth(anyDouble());
    return entity;
  }

  private ItemStack held(Material material, int amount, EquipmentSlot hand) {
    ItemStack item = mock(ItemStack.class);
    lenient().when(item.getType()).thenReturn(material);
    lenient().when(item.getAmount()).thenReturn(amount);
    lenient().when(inventory.getItem(hand)).thenReturn(item);
    return item;
  }

  private PlayerInteractEntityEvent click(Entity target, EquipmentSlot hand) {
    return new PlayerInteractEntityEvent(player, target, hand);
  }

  private List<String> lore(int level) {
    List<String> lines = new ArrayList<>();
    Element stats = mock(Element.class, RETURNS_SELF);
    when(stats.addLore(anyString())).thenAnswer(invocation -> {
      lines.add(invocation.<String>getArgument(0).replaceAll("§[0-9a-fk-orx]", ""));
      return stats;
    });
    adaptation.addStats(level, stats);
    return lines;
  }

  private record Rebuild(GolemMending adaptation, GolemRegistry registry, GolemRoster roster, IronGolem golem, ItemStack core, PackedGolem item,
                         GolemSnapshot snapshot, GolemHandle handle, PlayerInteractEntityEvent click) {
  }
}
