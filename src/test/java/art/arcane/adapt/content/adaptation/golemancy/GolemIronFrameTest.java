package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemColossus;
import art.arcane.adapt.api.golem.GolemHandle;
import art.arcane.adapt.api.golem.GolemSettings;
import art.arcane.adapt.api.golem.GolemStat;
import art.arcane.adapt.api.golem.GolemStatDelta;
import art.arcane.adapt.api.golem.GolemStatSource;
import art.arcane.adapt.api.golem.GolemState;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.minion.MinionIdentity;
import art.arcane.adapt.api.minion.MinionKind;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.world.AdaptServer;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.math.M;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static org.mockito.Answers.RETURNS_SELF;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GolemIronFrameTest extends AdaptTestBase {
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-0000000000a9");
  private static final Set<Entity> MARKED_MINIONS = ConcurrentHashMap.newKeySet();

  @BeforeAll
  static void registerMarkedMinions() {
    MinionIdentity.registerKind(new MinionKind() {
      @Override
      public boolean matches(Entity entity) {
        return MARKED_MINIONS.contains(entity);
      }

      @Override
      public UUID ownerOf(Entity entity) {
        return null;
      }
    });
  }

  private GolemIronFrame adaptation;
  private MockedStatic<GolemancyRuntime> runtime;
  private MockedStatic<J> scheduling;
  private GolemRegistrations<GolemStatSource> statSources;
  private World world;
  private Player owner;
  private final List<Entity> nearby = new ArrayList<>();

  @BeforeEach
  void construct() {
    when(plugin.namespace()).thenReturn("adapt");
    runtime = mockStatic(GolemancyRuntime.class);
    runtime.when(() -> GolemancyRuntime.handle(any())).thenReturn(Optional.empty());
    runtime.when(GolemancyRuntime::settings).thenReturn(GolemSettings.defaults());
    scheduling = mockStatic(J.class);
    scheduling.when(() -> J.runEntity(any(Entity.class), any(Runnable.class), anyInt())).thenReturn(true);
    scheduling.when(() -> J.runEntity(any(Entity.class), any(Runnable.class), anyInt(), any(Runnable.class))).thenReturn(true);
    scheduling.when(() -> J.runEntity(any(Entity.class), any(Runnable.class))).thenReturn(true);
    statSources = GolemRegistrations.stats();
    adaptation = new GolemIronFrame();
    world = mock(World.class);
    lenient().when(world.getUID()).thenReturn(WORLD);
    lenient().when(world.getNearbyEntities(any(BoundingBox.class), any())).thenAnswer(invocation -> {
      Predicate<Entity> filter = invocation.getArgument(1);
      return nearby.stream().filter(filter).toList();
    });
    owner = mock(Player.class);
    lenient().when(owner.getUniqueId()).thenReturn(UUID.randomUUID());
  }

  @AfterEach
  void close() {
    statSources.close();
    scheduling.close();
    runtime.close();
  }

  @Test
  void defaultsMatchTheBrief() {
    GolemIronFrame.Config config = new GolemIronFrame.Config();

    assertThat(config.healthBonusByLevel).containsExactly(10, 20, 30, 40, 50);
    assertThat(config.armorBonusByLevel).containsExactly(2, 4, 6, 8, 10);
    assertThat(config.attackBonusByLevel).containsExactly(1.5D, 3D, 4.5D, 6D, 7.5D);
    assertThat(config.ingotHealBonusByLevel).containsExactly(5, 10, 15, 20, 25);
    assertThat(config.sweepLevel).isEqualTo(3);
    assertThat(config.sweepTargetsByLevel).containsExactly(0, 0, 1, 1, 2);
    assertThat(config.sweepDamageFraction).isEqualTo(0.5D);
    assertThat(config.sweepRadius).isEqualTo(2D);
    assertThat(config.colossusLevel).isEqualTo(5);
    assertThat(config.colossusScale).isEqualTo(1D);
    assertThat(config.colossusAttackBonus).isEqualTo(0.20D);
    assertThat(config.colossusGrowTicks).isEqualTo(20);
    assertThat(config.requires).containsExactly("golem-animus-binding:1");
    assertThat(config.maxLevel).isEqualTo(5);
  }

  @Test
  void configurationLoadsWithFiveLevelsAndRuntimeOnlySources() {
    assertThat(adaptation.reloadConfigFromDisk(false)).isTrue();
    assertThat(adaptation.getName()).isEqualTo("golem-iron-frame");
    assertThat(adaptation.getMaxLevel()).isEqualTo(5);
    for (int level = 1; level <= adaptation.getMaxLevel(); level++) {
      assertThat(adaptation.getCostFor(level)).as("cost at level %s", level).isPositive();
    }
    assertThat(adaptation.isRuntimeRegistered()).isFalse();
    assertThat(statSources.registered()).isEmpty();
    assertThat(GolemColossus.form()).isEmpty();
  }

  @Test
  void loadingClampsTheSweepRadiusToEightBlocks() {
    assertThat(adaptation.reloadConfigSnapshot("sweepRadius = 40.0\n", dataFolder.toPath().resolve("sweep.toml").toFile(), false)).isTrue();

    assertThat(adaptation.getConfig().sweepRadius).isEqualTo(8D);
  }

  @Test
  void loadingClampsTheColossusScale() {
    assertThat(adaptation.reloadConfigSnapshot("colossusScale = 4.0\n", dataFolder.toPath().resolve("huge.toml").toFile(), false)).isTrue();
    assertThat(adaptation.getConfig().colossusScale).isEqualTo(1.5D);

    assertThat(adaptation.reloadConfigSnapshot("colossusScale = 0.1\n", dataFolder.toPath().resolve("tiny.toml").toFile(), false)).isTrue();
    assertThat(adaptation.getConfig().colossusScale).isEqualTo(0.36D);
  }

  @Test
  void everyLevelShowsItsStatsRepairsAndUnlocks() {
    assertThat(lore(1)).containsExactly("+ 10 Bonus Health for Iron Golems", "+ 2 Bonus Armor for Iron Golems",
        "+ 1.5 Bonus Attack Damage for Iron Golems", "+ 5 Extra Health per Iron Ingot Repair");
    assertThat(lore(2)).containsExactly("+ 20 Bonus Health for Iron Golems", "+ 4 Bonus Armor for Iron Golems",
        "+ 3 Bonus Attack Damage for Iron Golems", "+ 10 Extra Health per Iron Ingot Repair");
    assertThat(lore(3)).containsExactly("+ 30 Bonus Health for Iron Golems", "+ 6 Bonus Armor for Iron Golems",
        "+ 4.5 Bonus Attack Damage for Iron Golems", "+ 15 Extra Health per Iron Ingot Repair", "+ 1 Extra Hostiles Hit per Punch");
    assertThat(lore(4)).containsExactly("+ 40 Bonus Health for Iron Golems", "+ 8 Bonus Armor for Iron Golems",
        "+ 6 Bonus Attack Damage for Iron Golems", "+ 20 Extra Health per Iron Ingot Repair", "+ 1 Extra Hostiles Hit per Punch");
    assertThat(lore(5)).containsExactly("+ 50 Bonus Health for Iron Golems", "+ 10 Bonus Armor for Iron Golems",
        "+ 7.5 Bonus Attack Damage for Iron Golems", "+ 25 Extra Health per Iron Ingot Repair", "+ 2 Extra Hostiles Hit per Punch",
        "+ 20% Attack Damage in Colossus Form");
  }

  @Test
  void theFrameAndColossusSourcesStrengthenIronGolemsOnly() {
    GolemIronFrame.Config config = new GolemIronFrame.Config();

    assertThat(GolemIronFrame.STAT_SOURCE).isEqualTo("iron_frame");
    assertThat(GolemIronFrame.COLOSSUS_SOURCE).isEqualTo("colossus");
    assertThat(GolemIronFrame.frameStats(config, 2)).containsExactly(GolemStatDelta.add(GolemStat.MAX_HEALTH, 20D),
        GolemStatDelta.add(GolemStat.ARMOR, 4D), GolemStatDelta.add(GolemStat.ATTACK_DAMAGE, 3D));
    assertThat(GolemIronFrame.frameStats(config, 0)).isEmpty();
    assertThat(GolemIronFrame.colossusStats(config, 5)).containsExactly(GolemStatDelta.multiplyTotal(GolemStat.ATTACK_DAMAGE, 0.20D));
    assertThat(GolemIronFrame.colossusStats(config, 4)).isEmpty();
  }

  @Test
  void theRegisteredSourcesSkipTheOwnerLookupForOtherBodies() {
    GolemHandle copper = mock(GolemHandle.class);
    when(copper.body()).thenReturn(GolemBody.COPPER);
    when(plugin.getAdaptServer()).thenThrow(new AssertionError("owner level looked up for a copper golem"));
    adaptation.onRuntimeActivated();
    try {
      assertThat(statSource(GolemIronFrame.STAT_SOURCE).deltas(copper)).isEmpty();
      assertThat(statSource(GolemIronFrame.COLOSSUS_SOURCE).deltas(copper)).isEmpty();
      assertThat(GolemColossus.form().orElseThrow().available(copper)).isFalse();
    } finally {
      adaptation.unregister();
    }
    assertThat(GolemColossus.form()).isEmpty();
  }

  @Test
  void anIronIngotRepairOfABoundIronGolemHealsTheLevelsBonusInTheSameTick() {
    IronGolem golem = ironGolem(at(0, 64, 0));
    bound(golem, false);
    GolemIronFrame learned = learned(3);

    learned.on(new PlayerInteractEntityEvent(holding(Material.IRON_INGOT, EquipmentSlot.HAND), golem, EquipmentSlot.HAND));
    EntityRegainHealthEvent repair = new EntityRegainHealthEvent(golem, 25D, RegainReason.CUSTOM);
    learned.on(repair);
    EntityRegainHealthEvent later = new EntityRegainHealthEvent(golem, 25D, RegainReason.CUSTOM);
    learned.on(later);

    assertThat(repair.getAmount()).isEqualTo(40D);
    assertThat(later.getAmount()).isEqualTo(25D);
    ArgumentCaptor<Runnable> cleanup = ArgumentCaptor.forClass(Runnable.class);
    scheduling.verify(() -> J.runEntity(eq(golem), cleanup.capture(), eq(1)));
    assertThat(learned.hasTickDemand()).isFalse();
  }

  @Test
  void ingotRepairsInTheOffHandCountAndOtherHealsOrGolemsDoNot() {
    IronGolem golem = ironGolem(at(0, 64, 0));
    bound(golem, false);
    IronGolem wild = ironGolem(at(4, 64, 0));
    GolemIronFrame learned = learned(5);

    learned.on(new PlayerInteractEntityEvent(holding(Material.IRON_INGOT, EquipmentSlot.OFF_HAND), golem, EquipmentSlot.OFF_HAND));
    EntityRegainHealthEvent natural = new EntityRegainHealthEvent(golem, 25D, RegainReason.REGEN);
    learned.on(natural);
    EntityRegainHealthEvent offHand = new EntityRegainHealthEvent(golem, 25D, RegainReason.CUSTOM);
    learned.on(offHand);
    learned.on(new PlayerInteractEntityEvent(holding(Material.IRON_INGOT, EquipmentSlot.HAND), wild, EquipmentSlot.HAND));
    EntityRegainHealthEvent wildRepair = new EntityRegainHealthEvent(wild, 25D, RegainReason.CUSTOM);
    learned.on(wildRepair);
    learned.on(new PlayerInteractEntityEvent(holding(Material.IRON_NUGGET, EquipmentSlot.HAND), golem, EquipmentSlot.HAND));
    EntityRegainHealthEvent nugget = new EntityRegainHealthEvent(golem, 25D, RegainReason.CUSTOM);
    learned.on(nugget);

    assertThat(natural.getAmount()).isEqualTo(25D);
    assertThat(offHand.getAmount()).isEqualTo(50D);
    assertThat(wildRepair.getAmount()).isEqualTo(25D);
    assertThat(nugget.getAmount()).isEqualTo(25D);
  }

  @Test
  void sweepingFistsPunchesTheNearestOtherHostilesForHalfTheDamageAsTheGolem() {
    IronGolem golem = ironGolem(at(0, 64, 0));
    bound(golem, false);
    Zombie victim = living(Zombie.class, at(1, 64, 0));
    Zombie near = living(Zombie.class, at(2, 64, 0));
    Zombie second = living(Zombie.class, at(1, 64, 1.5D));
    Zombie third = living(Zombie.class, at(1, 64, -1.9D));
    Zombie far = living(Zombie.class, at(4, 64, 0));
    Cow cow = living(Cow.class, at(1.5D, 64, 0));
    Zombie invulnerable = living(Zombie.class, at(1.2D, 64, 0));
    when(invulnerable.isInvulnerable()).thenReturn(true);
    GolemIronFrame learned = learned(5);
    doAnswer(invocation -> {
      learned.on(hit(golem, near, 3D));
      return null;
    }).when(near).damage(anyDouble(), any(Entity.class));

    learned.on(hit(golem, victim, 12D));

    verify(near).damage(6D, golem);
    verify(second).damage(6D, golem);
    verify(third, never()).damage(anyDouble(), any(Entity.class));
    verify(far, never()).damage(anyDouble(), any(Entity.class));
    verify(cow, never()).damage(anyDouble(), any(Entity.class));
    verify(invulnerable, never()).damage(anyDouble(), any(Entity.class));
    verify(victim, never()).damage(anyDouble(), any(Entity.class));
  }

  @Test
  void sweepingFistsNeverPunchesAHostileAdaptMinion() {
    IronGolem golem = ironGolem(at(0, 64, 0));
    bound(golem, false);
    Zombie victim = living(Zombie.class, at(1, 64, 0));
    Skeleton servant = living(Skeleton.class, at(1.5D, 64, 0));
    MARKED_MINIONS.add(servant);
    Zombie near = living(Zombie.class, at(1, 64, 1.5D));

    learned(3).on(hit(golem, victim, 12D));

    assertThat(MinionIdentity.isMinion(servant)).isTrue();
    verify(servant, never()).damage(anyDouble(), any(Entity.class));
    verify(near).damage(6D, golem);
  }

  @Test
  void sweepingFistsNeedsTheUnlockLevelAnAwakeBoundGolemAndPermission() {
    IronGolem golem = ironGolem(at(0, 64, 0));
    bound(golem, false);
    IronGolem sleeping = ironGolem(at(0, 64, 9));
    bound(sleeping, true);
    IronGolem wild = ironGolem(at(0, 64, 18));
    Zombie victim = living(Zombie.class, at(1, 64, 0));
    Zombie near = living(Zombie.class, at(2, 64, 0));
    Zombie victimOfSleeping = living(Zombie.class, at(1, 64, 9));
    Zombie nearSleeping = living(Zombie.class, at(2, 64, 9));
    Zombie victimOfWild = living(Zombie.class, at(1, 64, 18));
    Zombie nearWild = living(Zombie.class, at(2, 64, 18));

    learned(2).on(hit(golem, victim, 12D));
    GolemIronFrame protectedArea = learned(5);
    doReturn(false).when(protectedArea).canPVE(any(Player.class), any(Entity.class));
    protectedArea.on(hit(golem, victim, 12D));
    GolemIronFrame learned = learned(5);
    learned.on(hit(sleeping, victimOfSleeping, 12D));
    learned.on(hit(wild, victimOfWild, 12D));

    verify(near, never()).damage(anyDouble(), any(Entity.class));
    verify(nearSleeping, never()).damage(anyDouble(), any(Entity.class));
    verify(nearWild, never()).damage(anyDouble(), any(Entity.class));
  }

  @Test
  void sweepingFistsStaysOffWhileTheOwnerSwitchesItOff() {
    IronGolem golem = ironGolem(at(0, 64, 0));
    bound(golem, false);
    Zombie victim = living(Zombie.class, at(1, 64, 0));
    Zombie near = living(Zombie.class, at(2, 64, 0));
    GolemIronFrame learned = learned(5);
    doReturn(false).when(learned).sweeps(any());

    learned.on(hit(golem, victim, 12D));

    verify(near, never()).damage(anyDouble(), any(Entity.class));
  }

  @Test
  void theSweepingFistsControlAppearsOnceItsLevelUnlocksAndDefaultsOn() {
    assertThat(adaptation.getPlayerPreferences()).containsExactly(CommonPreferences.ENABLED, GolemancyPreferences.SWEEPING_FISTS);
    GolemancyPreferenceAssertions.assertVisibleFrom(adaptation, GolemancyPreferences.SWEEPING_FISTS, 3);
    UUID ownerId = UUID.randomUUID();
    when(plugin.getAdaptServer()).thenReturn(mock(AdaptServer.class));

    assertThat(adaptation.sweeps(ownerId)).isTrue();

    GolemancyPreferenceAssertions.serverDefault(adaptation, GolemancyPreferences.SWEEPING_FISTS, "OFF");
    assertThat(adaptation.sweeps(ownerId)).isFalse();
  }

  @Test
  void theColossusToggleShowsOnlyForIronGolemsOfALevelFiveOwner() {
    GolemIronFrame learned = learned(5);
    GolemIronFrame lowLevel = learned(4);
    GolemHandle iron = handle(ironGolem(at(0, 64, 0)), new AtomicBoolean(false));
    GolemHandle grown = handle(ironGolem(at(0, 64, 4)), new AtomicBoolean(true));
    GolemHandle copper = mock(GolemHandle.class);
    when(copper.body()).thenReturn(GolemBody.COPPER);

    assertThat(learned.colossusAvailable(iron)).isTrue();
    assertThat(lowLevel.colossusAvailable(iron)).isFalse();
    assertThat(lowLevel.colossusAvailable(grown)).isFalse();
    assertThat(learned.colossusAvailable(copper)).isFalse();
  }

  @Test
  void colossusGrowsOverItsTicksAddsTheAttackSourceAndShrinksBack() {
    IronGolem golem = ironGolem(at(0, 64, 0));
    AtomicBoolean colossus = new AtomicBoolean(false);
    GolemHandle handle = handle(golem, colossus);
    GolemIronFrame learned = learned(5);
    List<Double> fractions = new ArrayList<>();
    doAnswer(invocation -> fractions.add(invocation.getArgument(3))).when(learned).resize(eq(golem), anyDouble(), anyDouble(), anyDouble());

    assertThat(learned.toggleColossus(handle)).isEqualTo(GolemColossus.Toggle.GROW);

    assertThat(colossus).isTrue();
    verify(learned).resize(golem, 0.36D, 1D, 0D);
    verify(learned).refreshStats(handle);
    assertThat(learned.hasTickDemand()).isTrue();
    runSteps(golem, 0, 19);
    assertThat(learned.toggleColossus(handle)).isEqualTo(GolemColossus.Toggle.BUSY);
    runSteps(golem, 19, 20);
    assertThat(fractions).hasSize(21);
    assertThat(fractions.getLast()).isEqualTo(1D);
    assertThat(fractions.get(10)).isCloseTo(0.5D, offset(1e-9));
    assertThat(learned.hasTickDemand()).isFalse();

    assertThat(learned.toggleColossus(handle)).isEqualTo(GolemColossus.Toggle.SHRINK);

    assertThat(colossus).isFalse();
    verify(learned).resize(golem, 1D, 0.36D, 0D);
    verify(learned, times(2)).refreshStats(handle);
  }

  @Test
  void colossusIgnoresTogglesWhileResizingAndStopsWhenTheGolemIsReleased() {
    IronGolem golem = ironGolem(at(0, 64, 0));
    AtomicBoolean colossus = new AtomicBoolean(false);
    GolemHandle handle = handle(golem, colossus);
    GolemIronFrame learned = learned(5);
    GolemIronFrame lowLevel = learned(4);

    assertThat(lowLevel.toggleColossus(handle)).isEqualTo(GolemColossus.Toggle.UNAVAILABLE);
    assertThat(colossus).isFalse();
    verify(lowLevel, never()).resize(any(Mob.class), anyDouble(), anyDouble(), anyDouble());

    assertThat(learned.toggleColossus(handle)).isEqualTo(GolemColossus.Toggle.GROW);
    assertThat(learned.toggleColossus(handle)).isEqualTo(GolemColossus.Toggle.BUSY);
    assertThat(colossus).isTrue();
    verify(learned, never()).resize(golem, 1D, 0.36D, 0D);

    when(handle.isRetired()).thenReturn(true);
    runSteps(golem, 0, 1);

    verify(learned, times(1)).resize(eq(golem), anyDouble(), anyDouble(), anyDouble());
    assertThat(learned.hasTickDemand()).isFalse();
  }

  @Test
  void colossusRefusesToGrowWithoutRoomAndLeavesTheGolemAlone() {
    IronGolem golem = ironGolem(at(0, 64, 0));
    AtomicBoolean colossus = new AtomicBoolean(false);
    GolemHandle handle = handle(golem, colossus);
    GolemIronFrame learned = learned(5);
    doReturn(false).when(learned).hasRoom(golem, 1D);

    assertThat(learned.toggleColossus(handle)).isEqualTo(GolemColossus.Toggle.NO_ROOM);

    assertThat(colossus).isFalse();
    verify(handle, never()).setColossus(any(Boolean.class));
    verify(learned, never()).resize(any(Mob.class), anyDouble(), anyDouble(), anyDouble());
    assertThat(learned.hasTickDemand()).isFalse();
  }

  @Test
  void colossusShrinksOnItsOwnWhenTheOwnerDropsBelowTheUnlockLevel() {
    IronGolem golem = ironGolem(at(0, 64, 0));
    AtomicBoolean colossus = new AtomicBoolean(true);
    GolemHandle handle = handle(golem, colossus);

    learned(5).settleColossus(handle);
    assertThat(colossus).isTrue();

    GolemIronFrame unlearned = learned(4);
    unlearned.settleColossus(handle);
    unlearned.settleColossus(handle);

    assertThat(colossus).isFalse();
    verify(unlearned, times(1)).resize(golem, 1D, 0.36D, 0D);
    verify(unlearned).refreshStats(handle);
  }

  @Test
  void aResizeThatLeaksPastItsDeadlineSnapsToItsEnd() {
    IronGolem golem = ironGolem(at(0, 64, 0));
    GolemHandle handle = handle(golem, new AtomicBoolean(false));
    GolemIronFrame learned = learned(5);
    try (MockedStatic<M> clock = mockStatic(M.class)) {
      clock.when(M::ms).thenReturn(1_000L);
      learned.toggleColossus(handle);
      clock.when(M::ms).thenReturn(IronFrameRules.leakDeadline(1_000L, 20) - 1L);
      learned.onTick();
      assertThat(learned.toggleColossus(handle)).isEqualTo(GolemColossus.Toggle.BUSY);

      clock.when(M::ms).thenReturn(IronFrameRules.leakDeadline(1_000L, 20));
      learned.onTick();
    }

    ArgumentCaptor<Runnable> snap = ArgumentCaptor.forClass(Runnable.class);
    scheduling.verify(() -> J.runEntity(eq(golem), snap.capture()));
    snap.getValue().run();
    verify(learned).resize(golem, 0.36D, 1D, 1D);
    assertThat(learned.hasTickDemand()).isFalse();
    runSteps(golem, 0, 1);
    verify(learned, times(2)).resize(eq(golem), anyDouble(), anyDouble(), anyDouble());
  }

  @Test
  void unregisteringSnapsResizesWithoutRevertingTheFormEvenWhenTasksRunInline() {
    scheduling.when(() -> J.runEntity(any(Entity.class), any(Runnable.class))).thenAnswer(invocation -> {
      invocation.<Runnable>getArgument(1).run();
      return true;
    });
    IronGolem golem = ironGolem(at(0, 64, 0));
    AtomicBoolean colossus = new AtomicBoolean(false);
    GolemHandle handle = handle(golem, colossus);
    GolemIronFrame learned = learned(5);
    learned.onRuntimeActivated();
    learned.toggleColossus(handle);

    learned.unregister();

    assertThat(GolemColossus.form()).isEmpty();
    verify(learned).resize(golem, 0.36D, 1D, 1D);
    verify(handle, never()).setColossus(false);
    assertThat(colossus).isTrue();
    assertThat(learned.hasTickDemand()).isFalse();
  }

  private void runSteps(Mob golem, int alreadyRun, int steps) {
    for (int step = alreadyRun; step < steps; step++) {
      ArgumentCaptor<Runnable> next = ArgumentCaptor.forClass(Runnable.class);
      scheduling.verify(() -> J.runEntity(eq(golem), next.capture(), eq(1), any(Runnable.class)), times(step + 1));
      next.getValue().run();
    }
  }

  private GolemIronFrame learned(int level) {
    GolemIronFrame learned = spy(adaptation);
    doReturn(level).when(learned).ownerLevel(any(GolemHandle.class));
    lenient().doReturn(owner).when(learned).owner(any(GolemHandle.class));
    lenient().doReturn(true).when(learned).sweeps(any());
    lenient().doReturn(true).when(learned).canPVE(any(Player.class), any(Entity.class));
    lenient().doNothing().when(learned).resize(any(Mob.class), anyDouble(), anyDouble(), anyDouble());
    lenient().doNothing().when(learned).refreshStats(any(GolemHandle.class));
    lenient().doReturn(true).when(learned).hasRoom(any(Mob.class), anyDouble());
    return learned;
  }

  private GolemHandle handle(IronGolem golem, AtomicBoolean colossus) {
    GolemState state = mock(GolemState.class);
    lenient().when(state.colossus()).thenAnswer(invocation -> colossus.get());
    GolemHandle handle = mock(GolemHandle.class);
    lenient().when(handle.id()).thenReturn(UUID.randomUUID());
    lenient().when(handle.body()).thenReturn(GolemBody.IRON);
    lenient().when(handle.entity()).thenReturn(golem);
    lenient().when(handle.state()).thenReturn(state);
    lenient().doAnswer(invocation -> {
      colossus.set(invocation.getArgument(0));
      return null;
    }).when(handle).setColossus(any(Boolean.class));
    return handle;
  }

  private GolemHandle bound(IronGolem golem, boolean dormant) {
    GolemHandle handle = handle(golem, new AtomicBoolean(false));
    lenient().when(handle.isDormant()).thenReturn(dormant);
    runtime.when(() -> GolemancyRuntime.handle(golem)).thenReturn(Optional.of(handle));
    return handle;
  }

  private IronGolem ironGolem(Location location) {
    IronGolem golem = living(IronGolem.class, location);
    lenient().when(golem.isValid()).thenReturn(true);
    return golem;
  }

  private <T extends LivingEntity> T living(Class<T> type, Location location) {
    T entity = mock(type);
    lenient().when(entity.getUniqueId()).thenReturn(UUID.randomUUID());
    lenient().when(entity.getLocation()).thenAnswer(invocation -> location.clone());
    lenient().when(entity.getWorld()).thenReturn(world);
    lenient().when(entity.isValid()).thenReturn(true);
    lenient().when(entity.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));
    nearby.add(entity);
    return entity;
  }

  private Player holding(Material material, EquipmentSlot hand) {
    Player player = mock(Player.class);
    PlayerInventory inventory = mock(PlayerInventory.class);
    ItemStack held = mock(ItemStack.class);
    lenient().when(held.getType()).thenReturn(material);
    ItemStack empty = mock(ItemStack.class);
    lenient().when(empty.getType()).thenReturn(Material.AIR);
    lenient().when(inventory.getItem(any(EquipmentSlot.class))).thenAnswer(invocation -> invocation.getArgument(0) == hand ? held : empty);
    lenient().when(player.getInventory()).thenReturn(inventory);
    return player;
  }

  private Location at(double x, double y, double z) {
    return new Location(world, x + 0.5D, y, z + 0.5D);
  }

  private static EntityDamageByEntityEvent hit(IronGolem golem, LivingEntity victim, double damage) {
    EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
    lenient().when(event.getDamager()).thenReturn(golem);
    lenient().when(event.getEntity()).thenReturn(victim);
    lenient().when(event.getDamage()).thenReturn(damage);
    return event;
  }

  private GolemStatSource statSource(String id) {
    return statSources.registered().stream().filter(candidate -> candidate.id().equals(id)).findFirst().orElseThrow();
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
}
