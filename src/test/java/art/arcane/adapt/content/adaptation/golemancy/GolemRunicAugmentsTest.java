package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemCapacity;
import art.arcane.adapt.api.golem.GolemHandle;
import art.arcane.adapt.api.golem.GolemMastery;
import art.arcane.adapt.api.golem.GolemRune;
import art.arcane.adapt.api.golem.GolemRuneEffects;
import art.arcane.adapt.api.golem.GolemRuneLayout;
import art.arcane.adapt.api.golem.GolemRuneType;
import art.arcane.adapt.api.golem.GolemRunes;
import art.arcane.adapt.api.golem.GolemStat;
import art.arcane.adapt.api.golem.GolemStatDelta;
import art.arcane.adapt.api.golem.GolemStatSource;
import art.arcane.adapt.api.golem.GolemState;
import art.arcane.adapt.api.golem.GolemStats;
import art.arcane.adapt.api.golem.GolemWorkSpeed;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.api.golem.plinth.InfusionRecipe;
import art.arcane.adapt.api.golem.plinth.PlinthService;
import art.arcane.adapt.api.recipe.AdaptRecipe;
import art.arcane.adapt.api.recipe.ConfiguredRecipeParser;
import art.arcane.adapt.api.recipe.ExactIngredient;
import art.arcane.adapt.api.recipe.ExactIngredientBook;
import art.arcane.adapt.api.recipe.ExactItemResolver;
import art.arcane.adapt.api.recipe.RecipeItemCatalog;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.entity.Entity;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Snowman;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static org.mockito.Answers.RETURNS_SELF;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GolemRunicAugmentsTest extends AdaptTestBase {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000401");
  private static final NamespacedKey BLANK_RUNE_KEY = new NamespacedKey("adapt", "golemancy-blank-rune");

  private final Map<String, Material> materials = new HashMap<>();
  private final ExactIngredientBook book = new ExactIngredientBook();
  private MockedStatic<ConfiguredRecipeParser> parsers;
  private MockedStatic<GolemancyRuntime> runtime;
  private GolemRunicAugments adaptation;

  @BeforeEach
  void construct() {
    when(plugin.namespace()).thenReturn("adapt");
    for (String name : List.of("AMETHYST_SHARD", "SMOOTH_STONE")) {
      Material material = mock(Material.class);
      when(material.isItem()).thenReturn(true);
      when(material.name()).thenReturn(name);
      materials.put(name, material);
    }
    ConfiguredRecipeParser parser = new ConfiguredRecipeParser(new FakeCatalog(), new FakeResolver(), book);
    parsers = mockStatic(ConfiguredRecipeParser.class, CALLS_REAL_METHODS);
    parsers.when(() -> ConfiguredRecipeParser.bukkit(GolemItems.resolver(), GolemItems.recipeBook())).thenReturn(parser);
    runtime = mockStatic(GolemancyRuntime.class);
    runtime.when(() -> GolemancyRuntime.handle(any())).thenReturn(Optional.empty());
    adaptation = new GolemRunicAugments();
  }

  @AfterEach
  void close() {
    runtime.close();
    parsers.close();
  }

  @Test
  void defaultsMatchTheBrief() {
    GolemRunicAugments.Config config = new GolemRunicAugments.Config();

    assertThat(config.extraSlotsByLevel).containsExactly(0, 0, 1, 1, 2);
    assertThat(config.maxTierByLevel).containsExactly(1, 1, 2, 2, 3);
    assertThat(config.frostSlots).isEqualTo(1);
    assertThat(config.copperSlots).isEqualTo(2);
    assertThat(config.ironSlots).isEqualTo(3);
    assertThat(config.runeBodyBlocks).containsExactly("ember:frost");
    assertThat(config.swiftnessSpeedByTier).containsExactly(0.10D, 0.20D, 0.30D);
    assertThat(config.swiftnessWorkIntervalByTier).containsExactly(0.10D, 0.20D, 0.30D);
    assertThat(config.mightCarryByTier).containsExactly(0.5D, 1.0D, 1.5D);
    assertThat(config.mightAttackByTier).containsExactly(2D, 4D, 6D);
    assertThat(config.emberIgniteSecondsByTier).containsExactly(2, 4, 6);
    assertThat(config.emberFireImmuneByTier).containsExactly(false, true, true);
    assertThat(config.emberLavaImmuneByTier).containsExactly(false, false, true);
    assertThat(config.tideRadiusByTier).containsExactly(4D, 8D, 12D);
    assertThat(config.tideWaterBreathingByTier).containsExactly(false, true, true);
    assertThat(config.tideSwimSpeedByTier).containsExactly(0D, 0D, 1D);
    assertThat(config.orderChannelsByTier).containsExactly(1, 2, 3);
    assertThat(config.orderReclaimDelayByTier).containsExactly(0D, 0.5D, 0.75D);
    assertThat(config.entropyBonusYieldByTier).containsExactly(0.05D, 0.10D, 0.15D);
    assertThat(config.entropyWeaknessLevelByTier).containsExactly(0, 1, 2);
    assertThat(config.entropyWeaknessSecondsByTier).containsExactly(0, 3, 3);
    assertThat(config.bulwarkArmorByTier).containsExactly(4D, 8D, 12D);
    assertThat(config.bulwarkToughnessByTier).containsExactly(0D, 2D, 4D);
    assertThat(config.bulwarkKnockbackResistanceByTier).containsExactly(0D, 0D, 0.5D);
    assertThat(config.requires).containsExactly("golem-animus-binding:2");
    assertThat(config.maxLevel).isEqualTo(5);
  }

  @Test
  void configurationLoadsWithFiveLevelsAndRegistersNothingBeforeTheRuntimeStarts() {
    assertThat(adaptation.reloadConfigFromDisk(false)).isTrue();
    assertThat(adaptation.getName()).isEqualTo("golem-runic-augments");
    assertThat(adaptation.getMaxLevel()).isEqualTo(5);
    for (int level = 1; level <= adaptation.getMaxLevel(); level++) {
      assertThat(adaptation.getCostFor(level)).as("cost at level %s", level).isPositive();
    }
    assertThat(adaptation.isRuntimeRegistered()).isFalse();
    assertThat(GolemStats.sources()).extracting(GolemStatSource::id).doesNotContain(GolemRunicAugments.SOURCE);
    assertThat(GolemRunes.augments()).isEmpty();
  }

  @Test
  void perTierFlagsReloadFromTheConfigFile() {
    assertThat(adaptation.reloadConfigSnapshot("emberFireImmuneByTier = [true, true, true]\ntideWaterBreathingByTier = [false, false, true]\n",
        dataFolder.toPath().resolve("flags.toml").toFile(), false)).isTrue();

    assertThat(adaptation.getConfig().emberFireImmuneByTier).containsExactly(true, true, true);
    assertThat(adaptation.getConfig().tideWaterBreathingByTier).containsExactly(false, false, true);
  }

  @Test
  void everyLevelShowsItsExtraSlotsTierAndThePlinth() {
    assertThat(lore(1)).containsExactly("+ 1 Highest Usable Rune Tier", "+ Animus Plinth and Blank Rune Recipe");
    assertThat(lore(2)).containsExactly("+ 1 Highest Usable Rune Tier", "+ Animus Plinth and Blank Rune Recipe");
    assertThat(lore(3)).containsExactly("+ 1 Extra Rune Slots per Golem", "+ 2 Highest Usable Rune Tier", "+ Animus Plinth and Blank Rune Recipe");
    assertThat(lore(5)).containsExactly("+ 2 Extra Rune Slots per Golem", "+ 3 Highest Usable Rune Tier", "+ Animus Plinth and Blank Rune Recipe");
  }

  @Test
  void theBlankRuneRecipeRegistersAtLevelOneWithoutATithe() {
    List<AdaptRecipe> recipes = adaptation.getRecipes();

    assertThat(recipes).extracting(AdaptRecipe::getNSKey).containsExactly(BLANK_RUNE_KEY);
    assertThat(recipes).allMatch(recipe -> recipe.getRequiredLevel() == 1);
    ShapedRecipe keyed = mock(ShapedRecipe.class);
    when(keyed.getKey()).thenReturn(BLANK_RUNE_KEY);
    assertThat(GolemItems.recipeTithe().levels(keyed)).isZero();
  }

  @Test
  void plinthLoadingAndEveryRuneInfusionUnlockThroughThisAdaptation() {
    GolemRunicAugments.Config config = new GolemRunicAugments.Config();
    GolemRuneLayout table = RunicAugmentsRules.layout(1, config.extraSlotsByLevel, config.maxTierByLevel, config.maxLevel, Set.of(), config.maxLevel, 0);
    int runeRecipes = 0;
    for (InfusionRecipe recipe : InfusionRecipe.defaults().values()) {
      if (!recipe.getResult().startsWith("golemancy:rune/")) {
        continue;
      }
      runeRecipes++;
      int tier = Integer.parseInt(recipe.getResult().substring(recipe.getResult().lastIndexOf('/') + 1));
      assertThat(recipe.getUnlockAdaptation()).isEqualTo(adaptation.getName());
      assertThat(recipe.getUnlockLevel()).as("unlock level of %s", recipe.getResult()).isEqualTo(table.tierUnlockLevel(tier));
    }

    assertThat(runeRecipes).isEqualTo(21);
    assertThat(PlinthService.LOADING_ADAPTATION).isEqualTo(adaptation.getName());
  }

  @Test
  void runeSourcesAndAugmentsRegisterOnlyWhileTheAdaptationRuns() {
    adaptation.onRuntimeActivated();
    try {
      assertThat(GolemStats.sources()).extracting(GolemStatSource::id).contains(GolemRunicAugments.SOURCE);
      assertThat(GolemCapacity.sources()).extracting(GolemCapacity.Source::id).contains(GolemRunicAugments.SOURCE);
      assertThat(GolemWorkSpeed.sources()).extracting(GolemWorkSpeed.Source::id).contains(GolemRunicAugments.SOURCE);
      assertThat(GolemRunes.augments()).isPresent();
    } finally {
      adaptation.unregister();
    }

    assertThat(GolemStats.sources()).extracting(GolemStatSource::id).doesNotContain(GolemRunicAugments.SOURCE);
    assertThat(GolemCapacity.sources()).extracting(GolemCapacity.Source::id).doesNotContain(GolemRunicAugments.SOURCE);
    assertThat(GolemWorkSpeed.sources()).extracting(GolemWorkSpeed.Source::id).doesNotContain(GolemRunicAugments.SOURCE);
    assertThat(GolemRunes.augments()).isEmpty();
  }

  @Test
  void golemsWithoutRunesNeverLookUpTheirOwner() {
    GolemHandle bare = bound(golem(IronGolem.class), GolemBody.IRON, false);
    when(plugin.getAdaptServer()).thenThrow(new AssertionError("owner level looked up for a golem without runes"));
    adaptation.onRuntimeActivated();
    try {
      assertThat(statSource().deltas(bare)).isEmpty();
      assertThat(GolemCapacity.carry(64, List.of(capacitySource().carry(bare)))).isEqualTo(64);
      assertThat(GolemWorkSpeed.intervalMultiplier(bare)).isEqualTo(1D);
      assertThat(GolemRunes.effects(bare)).isEqualTo(GolemRuneEffects.NONE);
    } finally {
      adaptation.unregister();
    }
  }

  @Test
  void theOwnersLevelDecidesOpenSlotsAndTheHighestUsableTierForEachBody() {
    GolemRunicAugments learned = learned(3);

    GolemRuneLayout frost = learned.layout(GolemBody.FROST, OWNER, false);
    GolemRuneLayout iron = learned.layout(GolemBody.IRON, OWNER, false);

    assertThat(frost.unlockedSlots()).isEqualTo(2);
    assertThat(frost.slotUnlockLevels()).containsExactly(1, 3, 5);
    assertThat(frost.blocks(GolemRuneType.EMBER)).isTrue();
    assertThat(iron.unlockedSlots()).isEqualTo(4);
    assertThat(iron.slotUnlockLevels()).containsExactly(1, 1, 1, 3, 5);
    assertThat(iron.maxTier()).isEqualTo(2);
    assertThat(iron.blocks(GolemRuneType.EMBER)).isFalse();
    assertThat(learned.layout(GolemBody.BONE, OWNER, false).maxSlots()).isZero();
    assertThat(learned(0).layout(GolemBody.IRON, OWNER, false).unlockedSlots()).isZero();
  }

  @Test
  void overTierRunesRunesInLockedSlotsAndEmberInAFrostGolemAreInert() {
    GolemHandle copper = bound(golem(IronGolem.class), GolemBody.COPPER, false,
        rune(GolemRuneType.MIGHT, 3), rune(GolemRuneType.SWIFTNESS, 1), rune(GolemRuneType.TIDE, 1));
    GolemHandle frost = bound(golem(Snowman.class), GolemBody.FROST, false, rune(GolemRuneType.EMBER, 1));

    assertThat(learned(1).runes(copper).slotted()).containsExactlyEntriesOf(Map.of(GolemRuneType.SWIFTNESS, 1));
    assertThat(learned(5).runes(frost).isEmpty()).isTrue();
    assertThat(learned(0).effects(copper)).isEqualTo(GolemRuneEffects.NONE);
  }

  @Test
  void tideOrderAndEntropyRunesShapeTheEffectsCoresRead() {
    GolemHandle iron = bound(golem(IronGolem.class), GolemBody.IRON, false,
        rune(GolemRuneType.TIDE, 2), rune(GolemRuneType.ORDER, 3), rune(GolemRuneType.ENTROPY, 1));

    GolemRuneEffects effects = learned(5).effects(iron);

    assertThat(effects.radiusBonus()).isEqualTo(8D);
    assertThat(effects.channelSlots()).isEqualTo(4);
    assertThat(effects.reclaimDelayMultiplier()).isCloseTo(0.25D, offset(1e-9));
    assertThat(effects.bonusYield()).isEqualTo(0.05D);
    assertThat(GolemRunicAugments.runeEffects(new GolemRunicAugments.Config(), ActiveRunes.slotted(Map.of(GolemRuneType.ORDER, 1))))
        .isEqualTo(new GolemRuneEffects(0D, 1, 1D, 0D));
  }

  @Test
  void eachAspectsStatsCarryAndWorkSpeedFollowItsTierTable() {
    GolemRunicAugments.Config config = new GolemRunicAugments.Config();

    assertThat(GolemRunicAugments.runeStats(config, ActiveRunes.slotted(Map.of(GolemRuneType.SWIFTNESS, 2))))
        .containsExactly(GolemStatDelta.multiplyBase(GolemStat.MOVEMENT_SPEED, 0.20D));
    assertThat(GolemRunicAugments.runeStats(config, ActiveRunes.slotted(Map.of(GolemRuneType.MIGHT, 3)))).containsExactly(GolemStatDelta.add(GolemStat.ATTACK_DAMAGE, 6D));
    assertThat(GolemRunicAugments.runeStats(config, ActiveRunes.slotted(Map.of(GolemRuneType.TIDE, 1)))).containsExactly(GolemStatDelta.add(GolemStat.FOLLOW_RANGE, 4D));
    assertThat(GolemRunicAugments.runeStats(config, ActiveRunes.slotted(Map.of(GolemRuneType.TIDE, 3))))
        .containsExactly(GolemStatDelta.add(GolemStat.FOLLOW_RANGE, 12D), GolemStatDelta.add(GolemStat.WATER_MOVEMENT_EFFICIENCY, 1D));
    assertThat(GolemRunicAugments.runeStats(config, ActiveRunes.slotted(Map.of(GolemRuneType.BULWARK, 1)))).containsExactly(GolemStatDelta.add(GolemStat.ARMOR, 4D));
    assertThat(GolemRunicAugments.runeStats(config, ActiveRunes.slotted(Map.of(GolemRuneType.BULWARK, 3)))).containsExactly(GolemStatDelta.add(GolemStat.ARMOR, 12D),
        GolemStatDelta.add(GolemStat.ARMOR_TOUGHNESS, 4D), GolemStatDelta.add(GolemStat.KNOCKBACK_RESISTANCE, 0.5D));
    assertThat(GolemRunicAugments.runeStats(config, ActiveRunes.slotted(Map.of(GolemRuneType.EMBER, 3, GolemRuneType.ORDER, 3, GolemRuneType.ENTROPY, 3)))).isEmpty();
    assertThat(GolemRunicAugments.runeCarry(config, ActiveRunes.slotted(Map.of(GolemRuneType.MIGHT, 2)))).isEqualTo(GolemCapacity.Contribution.bonus(1D));
    assertThat(GolemRunicAugments.runeCarry(config, ActiveRunes.slotted(Map.of()))).isEqualTo(GolemCapacity.Contribution.NONE);
    assertThat(GolemRunicAugments.runeWorkInterval(config, ActiveRunes.slotted(Map.of(GolemRuneType.SWIFTNESS, 3)))).isCloseTo(0.7D, offset(1e-9));
    assertThat(GolemRunicAugments.runeWorkInterval(config, ActiveRunes.slotted(Map.of()))).isEqualTo(1D);
  }

  @Test
  void emberRunesSetWhatTheGolemHitsOnFireForTheirTiersSeconds() {
    IronGolem golem = golem(IronGolem.class);
    bound(golem, GolemBody.IRON, false, rune(GolemRuneType.EMBER, 2));
    Zombie victim = mock(Zombie.class);

    learned(3).on(hit(golem, victim));

    verify(victim).setFireTicks(80);
  }

  @Test
  void sleepingUnboundAndUnlearnedGolemsIgniteNothing() {
    IronGolem sleeping = golem(IronGolem.class);
    bound(sleeping, GolemBody.IRON, true, rune(GolemRuneType.EMBER, 1));
    IronGolem wild = golem(IronGolem.class);
    IronGolem unlearned = golem(IronGolem.class);
    bound(unlearned, GolemBody.IRON, false, rune(GolemRuneType.EMBER, 1));
    Zombie victim = mock(Zombie.class);

    learned(5).on(hit(sleeping, victim));
    learned(5).on(hit(wild, victim));
    learned(0).on(hit(unlearned, victim));

    verify(victim, never()).setFireTicks(anyInt());
  }

  @Test
  void entropyRunesWeakenWhatTheGolemHitsFromTierTwo() {
    IronGolem first = golem(IronGolem.class);
    bound(first, GolemBody.IRON, false, rune(GolemRuneType.ENTROPY, 1));
    IronGolem second = golem(IronGolem.class);
    bound(second, GolemBody.IRON, false, rune(GolemRuneType.ENTROPY, 2));
    IronGolem third = golem(IronGolem.class);
    bound(third, GolemBody.IRON, false, rune(GolemRuneType.ENTROPY, 3));
    Zombie firstVictim = mock(Zombie.class);
    Zombie secondVictim = mock(Zombie.class);
    Zombie thirdVictim = mock(Zombie.class);
    GolemRunicAugments learned = learned(5);

    learned.on(hit(first, firstVictim));
    learned.on(hit(second, secondVictim));
    learned.on(hit(third, thirdVictim));

    verify(learned, never()).weaken(eq(firstVictim), anyInt(), anyInt());
    verify(learned).weaken(secondVictim, 0, 60);
    verify(learned).weaken(thirdVictim, 1, 60);
  }

  @Test
  void emberAndTideRunesShieldTheGolemFromFireLavaAndDrowning() {
    IronGolem warded = golem(IronGolem.class);
    bound(warded, GolemBody.IRON, false, rune(GolemRuneType.EMBER, 2), rune(GolemRuneType.TIDE, 2));
    IronGolem molten = golem(IronGolem.class);
    bound(molten, GolemBody.IRON, false, rune(GolemRuneType.EMBER, 3));
    IronGolem damp = golem(IronGolem.class);
    bound(damp, GolemBody.IRON, false, rune(GolemRuneType.TIDE, 1));

    assertThat(cancels(learned(3), warded, DamageCause.FIRE_TICK)).isTrue();
    assertThat(cancels(learned(3), warded, DamageCause.FIRE)).isTrue();
    assertThat(cancels(learned(3), warded, DamageCause.LAVA)).isFalse();
    assertThat(cancels(learned(3), warded, DamageCause.DROWNING)).isTrue();
    assertThat(cancels(learned(3), warded, DamageCause.FALL)).isFalse();
    assertThat(cancels(learned(5), molten, DamageCause.LAVA)).isTrue();
    assertThat(cancels(learned(5), damp, DamageCause.DROWNING)).isFalse();
  }

  @Test
  void aMasterworkGainsItsExtraSlotsOnlyWhileItsOwnerHasLearnedMasterwork() {
    GolemMastery.Source source = mastery(new GolemMastery.Perks(1, false, false, 0D));
    try {
      GolemRunicAugments learned = learned(3);
      GolemRuneLayout plain = learned.layout(GolemBody.IRON, OWNER, false);
      GolemRuneLayout masterwork = learned.layout(GolemBody.IRON, OWNER, true);

      assertThat(plain.unlockedSlots()).isEqualTo(4);
      assertThat(masterwork.unlockedSlots()).isEqualTo(5);
      assertThat(masterwork.slotUnlockLevels()).containsExactly(1, 1, 1, 1, 3, 5);
      assertThat(learned.layout(GolemBody.IRON, OWNER, true)).isSameAs(masterwork);
      assertThat(learned.layout(GolemBody.IRON, OWNER, false)).isSameAs(plain);
      assertThat(learned.layout(GolemBody.BONE, OWNER, true).maxSlots()).isZero();

      when(source.perks(OWNER)).thenReturn(GolemMastery.Perks.NONE);
      assertThat(learned.layout(GolemBody.IRON, OWNER, true).unlockedSlots()).isEqualTo(4);
    } finally {
      GolemMastery.unregister(source);
    }
  }

  @Test
  void theFifthRuneOfAMasterworkIronGolemIsActive() {
    GolemMastery.Source source = mastery(new GolemMastery.Perks(1, false, false, 0D));
    try {
      GolemRune[] five = {rune(GolemRuneType.MIGHT, 1), rune(GolemRuneType.SWIFTNESS, 1), rune(GolemRuneType.TIDE, 1),
          rune(GolemRuneType.ORDER, 1), rune(GolemRuneType.BULWARK, 1)};
      GolemHandle masterwork = masterwork(golem(IronGolem.class), GolemBody.IRON, five);
      GolemHandle plain = bound(golem(IronGolem.class), GolemBody.IRON, false, five);

      assertThat(learned(3).runes(masterwork).slotted()).containsKey(GolemRuneType.BULWARK).hasSize(5);
      assertThat(learned(3).runes(plain).slotted()).doesNotContainKey(GolemRuneType.BULWARK).hasSize(4);
    } finally {
      GolemMastery.unregister(source);
    }
  }

  @Test
  void tieredUpRunesUseTheNextTiersNumbersAndTierThreeGainsTheBoost() {
    GolemRunicAugments.Config config = new GolemRunicAugments.Config();
    ActiveRunes tieredUp = new ActiveRunes(Map.of(GolemRuneType.SWIFTNESS, 1, GolemRuneType.BULWARK, 3, GolemRuneType.MIGHT, 2), true, 0.25D);

    assertThat(GolemRunicAugments.runeStats(config, tieredUp)).containsExactly(
        GolemStatDelta.multiplyBase(GolemStat.MOVEMENT_SPEED, 0.20D),
        GolemStatDelta.add(GolemStat.ATTACK_DAMAGE, 6D),
        GolemStatDelta.add(GolemStat.ARMOR, 15D),
        GolemStatDelta.add(GolemStat.ARMOR_TOUGHNESS, 5D),
        GolemStatDelta.add(GolemStat.KNOCKBACK_RESISTANCE, 0.625D));
    assertThat(GolemRunicAugments.runeCarry(config, tieredUp)).isEqualTo(GolemCapacity.Contribution.bonus(1.5D));
    assertThat(GolemRunicAugments.runeWorkInterval(config, tieredUp)).isCloseTo(0.8D, offset(1e-9));
    GolemRuneEffects effects = GolemRunicAugments.runeEffects(config, new ActiveRunes(Map.of(GolemRuneType.ORDER, 3, GolemRuneType.ENTROPY, 3), true, 0.25D));
    assertThat(effects.extraChannels()).isEqualTo(4);
    assertThat(effects.reclaimDelayMultiplier()).isCloseTo(0.0625D, offset(1e-9));
    assertThat(effects.bonusYield()).isCloseTo(0.1875D, offset(1e-9));
  }

  @Test
  void tierUpLeavesBooleanWardsAndOverTierInertnessOnTheSlottedTier() {
    GolemMastery.Source source = mastery(new GolemMastery.Perks(1, true, true, 0.25D));
    try {
      IronGolem ember = golem(IronGolem.class);
      masterwork(ember, GolemBody.IRON, rune(GolemRuneType.EMBER, 1));
      IronGolem overTier = golem(IronGolem.class);
      GolemHandle unpowered = masterwork(overTier, GolemBody.IRON, rune(GolemRuneType.SWIFTNESS, 2));

      assertThat(cancels(learned(3), ember, DamageCause.FIRE)).isFalse();
      assertThat(learned(1).runes(unpowered).isEmpty()).isTrue();
      assertThat(learned(1).effects(unpowered)).isEqualTo(GolemRuneEffects.NONE);
    } finally {
      GolemMastery.unregister(source);
    }
  }

  @Test
  void aTieredUpEmberRuneBurnsForTheNextTiersSeconds() {
    GolemMastery.Source source = mastery(new GolemMastery.Perks(1, true, true, 0.25D));
    try {
      IronGolem masterwork = golem(IronGolem.class);
      masterwork(masterwork, GolemBody.IRON, rune(GolemRuneType.EMBER, 1));
      IronGolem plain = golem(IronGolem.class);
      bound(plain, GolemBody.IRON, false, rune(GolemRuneType.EMBER, 1));
      Zombie burned = mock(Zombie.class);
      Zombie singed = mock(Zombie.class);

      learned(3).on(hit(masterwork, burned));
      learned(3).on(hit(plain, singed));

      verify(burned).setFireTicks(80);
      verify(singed).setFireTicks(40);
    } finally {
      GolemMastery.unregister(source);
    }
  }

  private boolean cancels(GolemRunicAugments learned, Mob golem, DamageCause cause) {
    EntityDamageEvent event = mock(EntityDamageEvent.class);
    lenient().when(event.getCause()).thenReturn(cause);
    lenient().when(event.getEntity()).thenReturn(golem);
    boolean[] cancelled = {false};
    lenient().doAnswer(invocation -> {
      cancelled[0] = invocation.getArgument(0);
      return null;
    }).when(event).setCancelled(any(Boolean.class));
    learned.on(event);
    return cancelled[0];
  }

  private GolemRunicAugments learned(int level) {
    GolemRunicAugments learned = spy(adaptation);
    lenient().doReturn(level).when(learned).level(any(UUID.class));
    lenient().doNothing().when(learned).weaken(any(LivingEntity.class), anyInt(), anyInt());
    return learned;
  }

  private GolemHandle bound(Mob golem, GolemBody body, boolean dormant, GolemRune... runes) {
    GolemState state = mock(GolemState.class);
    lenient().when(state.runes()).thenReturn(List.of(runes));
    GolemHandle handle = mock(GolemHandle.class);
    lenient().when(handle.id()).thenReturn(UUID.randomUUID());
    lenient().when(handle.owner()).thenReturn(OWNER);
    lenient().when(handle.body()).thenReturn(body);
    lenient().when(handle.entity()).thenReturn(golem);
    lenient().when(handle.state()).thenReturn(state);
    lenient().when(handle.isDormant()).thenReturn(dormant);
    runtime.when(() -> GolemancyRuntime.handle(golem)).thenReturn(Optional.of(handle));
    return handle;
  }

  private GolemHandle masterwork(Mob golem, GolemBody body, GolemRune... runes) {
    GolemHandle handle = bound(golem, body, false, runes);
    when(handle.state().masterwork()).thenReturn(true);
    return handle;
  }

  private static GolemMastery.Source mastery(GolemMastery.Perks perks) {
    GolemMastery.Source source = mock(GolemMastery.Source.class);
    lenient().when(source.perks(OWNER)).thenReturn(perks);
    GolemMastery.register(source);
    return source;
  }

  private static <T extends Mob> T golem(Class<T> type) {
    T golem = mock(type);
    lenient().when(golem.getUniqueId()).thenReturn(UUID.randomUUID());
    return golem;
  }

  private static GolemRune rune(GolemRuneType aspect, int tier) {
    return new GolemRune(aspect, tier);
  }

  private static EntityDamageByEntityEvent hit(Entity golem, LivingEntity victim) {
    EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
    lenient().when(event.getDamager()).thenReturn(golem);
    lenient().when(event.getEntity()).thenReturn(victim);
    return event;
  }

  private static GolemStatSource statSource() {
    return GolemStats.sources().stream().filter(source -> source.id().equals(GolemRunicAugments.SOURCE)).findFirst().orElseThrow();
  }

  private static GolemCapacity.Source capacitySource() {
    return GolemCapacity.sources().stream().filter(source -> source.id().equals(GolemRunicAugments.SOURCE)).findFirst().orElseThrow();
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

  private final class FakeCatalog implements RecipeItemCatalog {
    @Override
    public Material itemMaterial(String name) {
      return materials.get(name);
    }

    @Override
    public Tag<Material> itemTag(NamespacedKey key) {
      return null;
    }
  }

  private static final class FakeResolver implements ExactItemResolver {
    @Override
    public ExactIngredient ingredient(NamespacedKey id) {
      return null;
    }

    @Override
    public ItemStack result(NamespacedKey id) {
      ItemStack template = mock(ItemStack.class);
      when(template.clone()).thenReturn(mock(ItemStack.class));
      return template;
    }
  }
}
