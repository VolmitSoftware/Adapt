/*------------------------------------------------------------------------------
 -   Adapt is a Skill/Integration plugin  for Minecraft Bukkit Servers
 -   Copyright (c) 2022 Arcane Arts (Volmit Software)
 -
 -   This program is free software: you can redistribute it and/or modify
 -   it under the terms of the GNU General Public License as published by
 -   the Free Software Foundation, either version 3 of the License, or
 -   (at your option) any later version.
 -
 -   This program is distributed in the hope that it will be useful,
 -   but WITHOUT ANY WARRANTY; without even the implied warranty of
 -   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 -   GNU General Public License for more details.
 -
 -   You should have received a copy of the GNU General Public License
 -   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 -----------------------------------------------------------------------------*/

package art.arcane.adapt.content.adaptation.chronos;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.ReceiveCancelledEvents;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.protection.ProtectionProbes;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.content.item.ChronoTimeBottle;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.ChronosMessages;
import art.arcane.adapt.platform.entity.AnimalOps;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockChange;
import art.arcane.adapt.platform.event.inventory.CraftItemEvent;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEntityEvent;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerItemConsumeEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.item.PotionContents;
import art.arcane.adapt.platform.recipe.Ingredient;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;
import art.arcane.adapt.platform.world.BrewingStandRef;
import art.arcane.adapt.platform.world.CampfireRef;
import art.arcane.adapt.platform.world.FurnaceRef;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class ChronosTimeInABottle extends SimpleAdaptation<ChronosTimeInABottle.Config> {
  public static final PlayerPreference<CommonPreferences.Toggle> CLOCK_SOUNDS = CommonPreferences.toggle("clock-sounds", ChronosMessages.PREFERENCE_CHRONOSTIMEINABOTTLE_CLOCK_SOUNDS, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> TREES = CommonPreferences.toggle("trees", ChronosMessages.PREFERENCE_CHRONOSTIMEINABOTTLE_TREES, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> FURNACES = CommonPreferences.toggle("furnaces", ChronosMessages.PREFERENCE_CHRONOSTIMEINABOTTLE_FURNACES, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> BREWING = CommonPreferences.toggle("brewing", ChronosMessages.PREFERENCE_CHRONOSTIMEINABOTTLE_BREWING, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> CAMPFIRES = CommonPreferences.toggle("campfires", ChronosMessages.PREFERENCE_CHRONOSTIMEINABOTTLE_CAMPFIRES, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> GROWTH = CommonPreferences.toggle("growth", ChronosMessages.PREFERENCE_CHRONOSTIMEINABOTTLE_GROWTH, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> ANIMALS = CommonPreferences.toggle("animals", ChronosMessages.PREFERENCE_CHRONOSTIMEINABOTTLE_ANIMALS, CommonPreferences.Toggle.ON);

  private static final String RECIPE_KEY = "chronos-time-in-a-bottle";
  private static final long CHARGE_INTERVAL_MILLIS = 1000L;
  private static final int HARD_MAX_PLAYERS_PER_PASS = 32;
  private static final int MAX_CATCH_UP_PULSES = 4;
  private static final int TREE_PREFLIGHT_RADIUS = 32;
  private static final int SAPLING_MAX_STAGE = 1;
  private static final String SAPLING_STAGE = "stage";
  private static final Id POTION = Id.minecraft("potion");
  private static final Id SWIFTNESS = Id.minecraft("swiftness");
  private static final Set<String> CROPS = Set.of("wheat", "carrots", "potatoes", "beetroots", "cocoa", "torchflower_crop",
      "pitcher_crop");
  private static final Set<String> STEMS = Set.of("pumpkin_stem", "melon_stem", "attached_melon_stem", "attached_pumpkin_stem");
  private static final Set<String> VINES = Set.of("vine", "weeping_vines", "weeping_vines_plant", "twisting_vines",
      "twisting_vines_plant");

  private final Map<UUID, Long> nextChargeAt = playerState();
  private final Map<UUID, Boolean> pendingCharges = playerState();
  private int playerCursor;

  public ChronosTimeInABottle() {
    super("chronos-time-bottle");
    registerConfiguration(Config.class);
    setLocalizationKey("chronos.time_in_a_bottle");
    setIcon(Id.minecraft("clock"));
    setInterval(CHARGE_INTERVAL_MILLIS);

    registerRecipe(new RecipeSpec.Shapeless(Id.adapt(RECIPE_KEY),
        List.of(Ingredient.of(Id.minecraft("clock")), Ingredient.of(POTION), Ingredient.of(Id.minecraft("glass_bottle"))),
        ChronoTimeBottle.withStoredSeconds(0)));
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("clock"))
        .key("challenge_chronos_bottle_seconds_1k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("recovery_compass"))
            .key("challenge_chronos_bottle_seconds_25k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone(
        "challenge_chronos_bottle_seconds_1k",
        "chronos.time-bottle.seconds-spent",
        1000,
        500
    );
    registerMilestone(
        "challenge_chronos_bottle_seconds_25k",
        "chronos.time-bottle.seconds-spent",
        25000,
        2000
    );
  }

  @Handle
  public void on(PlayerQuitEvent e) {
    nextChargeAt.remove(e.player().uuid());
    pendingCharges.remove(e.player().uuid());
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(CraftItemEvent e) {
    if (e.recipeKey() == null || !e.recipeKey().path().equals(RECIPE_KEY)) {
      return;
    }

    boolean hasSwiftnessPotion = false;
    for (ItemRef item : e.matrix()) {
      if (item == null || item.isEmpty() || !item.type().equals(POTION)) {
        continue;
      }
      Optional<PotionContents> contents = item.potionContents();
      if (contents.isPresent() && SWIFTNESS.equals(contents.get().potion())) {
        hasSwiftnessPotion = true;
        break;
      }
    }

    if (!hasSwiftnessPotion) {
      e.setCancelled(true);
      PlayerRef p = e.player();
      if ((getConfig().playClockSounds && preferenceEnabled(p, CLOCK_SOUNDS))) {
        ChronosSoundFX.playClockReject(p);
      }
    }
  }


  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, CLOCK_SOUNDS, TREES, FURNACES, BREWING, CAMPFIRES, GROWTH, ANIMALS);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.f(getConfig().chargePerSecond + (level * getConfig().chargePerSecondPerLevel), 2), 1);
    statLore(v, C.YELLOW, "+ ", Math.round(getCookTicksPerStoredSecond(level)), 2);
    statLore(v, C.AQUA, "+ ", formatStoredSeconds(getMaxStoredSeconds(level)),
        ChronosMessages.TIME_IN_A_BOTTLE_MAX_STORED_LORE);
    v.addLore(C.GRAY + "* " + AdaptLanguage.text(ChronosMessages.TIME_IN_A_BOTTLE_LORE3));
  }

  private double getMaxStoredSeconds(int level) {
    return maxStoredSeconds(getConfig().baseMaxStoredSeconds, getConfig().maxStoredSecondsPerLevel, level);
  }

  static double maxStoredSeconds(double base, double perLevel, int level) {
    double scaled = base + (Math.max(0, level) * perLevel);
    return Double.isFinite(scaled) ? Math.max(0D, scaled) : 0D;
  }

  static String formatStoredSeconds(double seconds) {
    return Form.duration((long) (Math.max(0D, seconds) * 1000D), 1);
  }

  private double getCookTicksPerStoredSecond(int level) {
    return getConfig().baseCookTicksPerStoredSecond + (level * getConfig().cookTicksPerSecondPerLevel);
  }

  private int getMaxCookTicksPerUse(int level) {
    return getConfig().maxCookTicksPerUse + (level * getConfig().maxCookTicksPerUsePerLevel);
  }

  private double getBrewingTicksPerStoredSecond(int level) {
    return getConfig().baseBrewingTicksPerStoredSecond + (level * getConfig().brewingTicksPerSecondPerLevel);
  }

  private int getMaxBrewingTicksPerUse(int level) {
    return getConfig().maxBrewingTicksPerUse + (level * getConfig().maxBrewingTicksPerUsePerLevel);
  }

  private double getCampfireTicksPerStoredSecond(int level) {
    return getConfig().baseCampfireTicksPerStoredSecond + (level * getConfig().campfireTicksPerSecondPerLevel);
  }

  private int getMaxCampfireTicksPerUse(int level) {
    return getConfig().maxCampfireTicksPerUse + (level * getConfig().maxCampfireTicksPerUsePerLevel);
  }

  private double getFurnaceSpendMultiplier() {
    return Math.max(0.01, getConfig().furnaceSpendMultiplier);
  }

  private double getBrewingSpendMultiplier() {
    return Math.max(0.01, getConfig().brewingSpendMultiplier);
  }

  private double getCampfireSpendMultiplier() {
    return Math.max(0.01, getConfig().campfireSpendMultiplier);
  }

  private double getEntityAgeTicksPerStoredSecond(int level) {
    return getConfig().baseEntityAgeTicksPerStoredSecond + (level * getConfig().entityAgeTicksPerSecondPerLevel);
  }

  private int getMaxEntityAgeTicksPerUse(int level) {
    return getConfig().maxEntityAgeTicksPerUse + (level * getConfig().maxEntityAgeTicksPerUsePerLevel);
  }

  private double getEntitySpendMultiplier() {
    return Math.max(0.01, getConfig().entitySpendMultiplier);
  }

  private int getMaxGrowthStepsPerUse(int level) {
    return getConfig().maxGrowthStepsPerUse + (level * getConfig().maxGrowthStepsPerUsePerLevel);
  }

  private double getGrowthLevelScale(int level) {
    return Math.max(getConfig().minGrowthCostLevelScale, 1D - (level * getConfig().growthCostReductionPerLevel));
  }

  private double getSaplingGrowChance(int level) {
    return Math.max(0, Math.min(1, getConfig().saplingGrowChanceBase + (level * getConfig().saplingGrowChancePerLevel)));
  }

  private Id getTreeFeature(Id type) {
    String feature = switch (type.isMinecraft() ? type.path() : "") {
      case "oak_sapling" -> ThreadLocalRandom.current().nextBoolean() ? "oak" : "fancy_oak";
      case "spruce_sapling" -> ThreadLocalRandom.current().nextBoolean() ? "spruce" : "pine";
      case "birch_sapling" -> "birch";
      case "jungle_sapling" -> ThreadLocalRandom.current().nextBoolean() ? "jungle_tree_no_vine" : "mega_jungle_tree";
      case "acacia_sapling" -> "acacia";
      case "dark_oak_sapling" -> "dark_oak";
      case "cherry_sapling" -> "cherry";
      case "mangrove_propagule" -> ThreadLocalRandom.current().nextBoolean() ? "mangrove" : "tall_mangrove";
      case "azalea", "flowering_azalea" -> "azalea_tree";
      default -> null;
    };
    return feature == null ? null : Id.minecraft(feature);
  }

  private static boolean isSapling(BlockStateRef state) {
    return state.property(SAPLING_STAGE) != null;
  }

  private static int saplingStage(BlockStateRef state) {
    return Integer.parseInt(state.property(SAPLING_STAGE));
  }

  private static boolean isAgeable(BlockStateRef state) {
    return state.maxAge() >= 0;
  }

  private static boolean isGrowthBlacklisted(Id type) {
    return AdaptConfig.get().isGrowthBlacklisted(type);
  }

  private double getGrowthStepCostSeconds(BlockRef target, int level) {
    GrowthProfile profile = detectGrowthProfile(target.type());
    double naturalSeconds = getNaturalGrowthSeconds(profile);
    int steps = getEstimatedGrowthSteps(target, profile);
    double baseStepSeconds = naturalSeconds / Math.max(1, steps);
    double profileMultiplier = getGrowthProfileCostMultiplier(profile);
    double scaled = baseStepSeconds * profileMultiplier * getConfig().growthCostMultiplier * getGrowthLevelScale(level);
    return Math.max(getConfig().minGrowthStepSeconds, scaled);
  }

  private int getEstimatedGrowthSteps(BlockRef target, GrowthProfile profile) {
    BlockStateRef data = target.state();
    if (isAgeable(data)) {
      return Math.max(1, data.maxAge());
    }

    if (isSapling(data)) {
      return Math.max(1, getConfig().saplingGrowthSteps);
    }

    return switch (profile) {
      case SAPLING -> Math.max(1, getConfig().saplingGrowthSteps);
      case STEM -> Math.max(1, getConfig().stemGrowthSteps);
      case BERRY_BUSH -> Math.max(1, getConfig().berryGrowthSteps);
      case VINE -> Math.max(1, getConfig().vineGrowthSteps);
      case CAVE_VINE -> Math.max(1, getConfig().caveVineGrowthSteps);
      case KELP -> Math.max(1, getConfig().kelpGrowthSteps);
      default -> Math.max(1, getConfig().defaultGrowthSteps);
    };
  }

  private double getNaturalGrowthSeconds(GrowthProfile profile) {
    return switch (profile) {
      case CROP -> getConfig().cropNaturalSeconds;
      case NETHER_WART -> getConfig().netherWartNaturalSeconds;
      case SAPLING -> getConfig().saplingNaturalSeconds;
      case STEM -> getConfig().stemNaturalSeconds;
      case BERRY_BUSH -> getConfig().berryBushNaturalSeconds;
      case VINE -> getConfig().vineNaturalSeconds;
      case CAVE_VINE -> getConfig().caveVineNaturalSeconds;
      case KELP -> getConfig().kelpNaturalSeconds;
      default -> getConfig().defaultGrowableNaturalSeconds;
    };
  }

  private double getGrowthProfileCostMultiplier(GrowthProfile profile) {
    return switch (profile) {
      case CROP -> getConfig().cropCostMultiplier;
      case NETHER_WART -> getConfig().netherWartCostMultiplier;
      case SAPLING -> getConfig().saplingCostMultiplier;
      case STEM -> getConfig().stemCostMultiplier;
      case BERRY_BUSH -> getConfig().berryBushCostMultiplier;
      case VINE -> getConfig().vineCostMultiplier;
      case CAVE_VINE -> getConfig().caveVineCostMultiplier;
      case KELP -> getConfig().kelpCostMultiplier;
      default -> getConfig().defaultGrowableCostMultiplier;
    };
  }

  private GrowthProfile detectGrowthProfile(Id type) {
    if (!type.isMinecraft()) {
      return GrowthProfile.DEFAULT;
    }
    String name = type.path();

    if (name.equals("nether_wart")) {
      return GrowthProfile.NETHER_WART;
    }

    if (name.equals("sweet_berry_bush")) {
      return GrowthProfile.BERRY_BUSH;
    }

    if (name.equals("kelp") || name.equals("kelp_plant")) {
      return GrowthProfile.KELP;
    }

    if (name.equals("cave_vines") || name.equals("cave_vines_plant")) {
      return GrowthProfile.CAVE_VINE;
    }

    if (VINES.contains(name)) {
      return GrowthProfile.VINE;
    }

    if (name.endsWith("_sapling") || name.equals("mangrove_propagule") || name.equals("azalea")
        || name.equals("flowering_azalea")) {
      return GrowthProfile.SAPLING;
    }

    if (STEMS.contains(name)) {
      return GrowthProfile.STEM;
    }

    if (CROPS.contains(name)) {
      return GrowthProfile.CROP;
    }

    return GrowthProfile.DEFAULT;
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(PlayerItemConsumeEvent e) {
    PlayerRef p = e.player();
    if (ChronoTimeBottle.isBindableItem(e.item())) {
      e.setCancelled(true);
      if ((getConfig().playClockSounds && preferenceEnabled(p, CLOCK_SOUNDS))) {
        ChronosSoundFX.playClockReject(e.player());
      }
    }
  }

  @ReceiveCancelledEvents
  @Handle(priority = EventPriority.HIGHEST)
  public void on(PlayerInteractEvent e) {
    InteractAction action = e.action();
    if (action != InteractAction.RIGHT_CLICK_BLOCK && action != InteractAction.RIGHT_CLICK_AIR) {
      return;
    }

    PlayerRef p = e.player();
    Hand handSlot = e.hand();
    if (handSlot == null) {
      return;
    }

    PlayerInventoryRef inventory = p.inventory();
    ItemRef hand = inventory.hand(handSlot);
    if (!ChronoTimeBottle.isBindableItem(hand)) {
      return;
    }

    boolean vetoed = e.clickedBlock() != null
        ? e.useBlock() == UseResult.DENY
        : e.useItem() == UseResult.DENY;

    // Chrono bottles are never drinkable; always deny vanilla potion use.
    e.setUseItem(UseResult.DENY);

    if (vetoed) {
      return;
    }
    if (action == InteractAction.RIGHT_CLICK_AIR && Tasks.isFoliaThreading()) {
      return;
    }

    BlockRef clicked = action == InteractAction.RIGHT_CLICK_BLOCK ? e.clickedBlock() : p.targetBlock(5);
    if (clicked == null) {
      return;
    }
    int level = getActiveInteractLevel(p, clicked.location());
    if (level <= 0) {
      return;
    }
    double storedSeconds = ChronoTimeBottle.getStoredSeconds(hand);
    if (storedSeconds <= 0 || !supportsBlockAcceleration(clicked, storedSeconds, level)) {
      return;
    }

    if (action == InteractAction.RIGHT_CLICK_AIR && !ProtectionProbes.attemptBlockUse(p, clicked)) {
      return;
    }
    if ((clicked.furnace().isPresent() || clicked.brewingStand().isPresent())
        && !canAccessChest(p, clicked.location())) {
      return;
    }

    TimeSpendResult result = accelerateTarget(p, clicked, storedSeconds, level);
    if (!result.applied()) {
      return;
    }

    e.setCancelled(true);
    double newStored = Math.max(0, storedSeconds - result.spentSeconds());
    ChronoTimeBottle.setStoredSeconds(hand, newStored);
    inventory.setHand(handSlot, hand);
    addStat(p, "chronos.time-bottle.seconds-spent", result.spentSeconds());

    Loc burstAt = clicked.location().add(0.5, 1.0, 0.5);
    if ((getConfig().playClockSounds && preferenceEnabled(p, CLOCK_SOUNDS))) {
      ChronosSoundFX.playBottleUse(p, burstAt, result.effectTicks());
    }
    emitBlockUseFx(clicked, burstAt);
    if (newStored <= 0) {
      emitBottleEmptyFx(burstAt);
    }

    xp(p, burstAt, Math.min(getConfig().maxXPPerUse, result.xpGain()));
  }

  private void emitBlockUseFx(BlockRef clicked, Loc at) {
    if (clicked.furnace().isPresent()) {
      fx(at, FxPriority.TRANSITION)
          .particle(Particles.FLAME, 6, 0, 0.2D, 0, 0.2D, 0.01D)
          .particle(Particles.SMOKE, 4, 0, 0.3D, 0, 0.15D, 0.02D)
          .sound(Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.4F, 1.4F);
      return;
    }

    if (clicked.brewingStand().isPresent()) {
      fx(at, FxPriority.TRANSITION)
          .particle(Particles.WITCH, 6, 0, 0.3D, 0, 0.2D, 0.02D)
          .particle(Particles.ENCHANTMENT_TABLE, 8, 0, 0.3D, 0, 0.25D, 0.03D)
          .sound(Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.4F, 1.5F);
      return;
    }

    if (clicked.campfire().isPresent()) {
      fx(at, FxPriority.TRANSITION)
          .particle(Particles.FLAME, 8, 0, 0.2D, 0, 0.25D, 0.02D)
          .particle(Particles.SMOKE, 3, 0, 0.4D, 0, 0.1D, 0.02D)
          .sound(Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.4F, 1.3F);
      return;
    }

    fx(at, FxPriority.TRANSITION)
        .particle(Particles.VILLAGER_HAPPY, 10, 0, 0.3D, 0, 0.3D, 0.01D)
        .particle(Particles.COMPOSTER, 6, 0, 0.3D, 0, 0.2D, 0.02D)
        .column(Particles.END_ROD, 4, 1.0D);
  }

  private void emitBottleEmptyFx(Loc at) {
    fx(at, FxPriority.TRANSITION)
        .burst(Particles.SMOKE, 3, 0.2D)
        .sound(Sounds.BLOCK_NOTE_BLOCK_BASS, 0.3F, 0.7F);
  }

  private void emitTreeFlourish(Loc base) {
    Loc at = base.add(0.5, 0, 0.5);
    fx(at, FxPriority.TRANSITION)
        .chord(Sounds.BLOCK_AZALEA_LEAVES_PLACE, 0.7F, 0.8F, Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.6F, 1.4F);
    timeline(at)
        .duration(6)
        .priority(FxPriority.TRANSITION)
        .cullRadius(24)
        .frame((f, tick, progress) -> {
          double height = 0.5D + (progress * 4.0D);
          f.particle(Particles.VILLAGER_HAPPY, 2, 0, height, 0, 0.3D, 0.01D);
          f.particle(Particles.END_ROD, 1, 0, height, 0, 0.05D, 0.02D);
        })
        .start();
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(PlayerInteractEntityEvent e) {
    if (e.hand() != Hand.MAIN_HAND) {
      return;
    }

    PlayerRef p = e.player();
    ItemRef hand = p.inventory().mainHand();
    if (!ChronoTimeBottle.isBindableItem(hand)) {
      return;
    }

    EntityRef ageable = e.clicked();
    AnimalOps animals = AdaptCore.platform().host().animals();
    if (!preferenceEnabled(p, ANIMALS)) {
      return;
    }

    int level = getActiveInteractLevel(p, ageable.location());
    if (level <= 0) {
      return;
    }

    int currentAge = animals.age(ageable);
    if (currentAge == 0) {
      return;
    }
    double storedSeconds = ChronoTimeBottle.getStoredSeconds(hand);
    if (storedSeconds <= 0) {
      return;
    }

    TimeSpendResult result = accelerateAgeableEntity(animals, ageable, storedSeconds, level);
    if (!result.applied()) {
      return;
    }

    e.setCancelled(true);
    double newStored = Math.max(0, storedSeconds - result.spentSeconds());
    ChronoTimeBottle.setStoredSeconds(hand, newStored);
    p.inventory().setMainHand(hand);
    addStat(p, "chronos.time-bottle.seconds-spent", result.spentSeconds());

    Loc entityBurst = ageable.location().add(0, 1.0, 0);
    if ((getConfig().playClockSounds && preferenceEnabled(p, CLOCK_SOUNDS))) {
      ChronosSoundFX.playBottleUse(p, entityBurst, result.effectTicks());
    }
    fx(entityBurst, FxPriority.TRANSITION)
        .particle(Particles.WAX_ON, 6, 0, 0.4D, 0, 0.3D, 0.02D)
        .particle(Particles.HEART, 2, 0, 0.5D, 0, 0.2D, 0.0D)
        .particle(Particles.ENCHANTMENT_TABLE, 8, 0, 0.4D, 0, 0.25D, 0.03D);
    if (newStored <= 0) {
      emitBottleEmptyFx(entityBurst);
    }

    xp(p, entityBurst, Math.min(getConfig().maxXPPerUse, result.xpGain()));
  }

  private boolean supportsBlockAcceleration(BlockRef clicked, double storedSeconds, int level) {
    if (clicked.furnace().isPresent()) {
      int affordableTicks = (int) Math.floor(
          (storedSeconds / getFurnaceSpendMultiplier()) * getCookTicksPerStoredSecond(level));
      return Math.min(affordableTicks, getMaxCookTicksPerUse(level)) > 0;
    }
    if (clicked.brewingStand().isPresent()) {
      int affordableTicks = (int) Math.floor(
          (storedSeconds / getBrewingSpendMultiplier()) * getBrewingTicksPerStoredSecond(level));
      return Math.min(affordableTicks, getMaxBrewingTicksPerUse(level)) > 0;
    }
    if (clicked.campfire().isPresent()) {
      int affordableTicks = (int) Math.floor(
          (storedSeconds / getCampfireSpendMultiplier()) * getCampfireTicksPerStoredSecond(level));
      return Math.min(affordableTicks, getMaxCampfireTicksPerUse(level)) > 0;
    }
    if (getMaxGrowthStepsPerUse(level) <= 0
        || isGrowthBlacklisted(clicked.type())
        || storedSeconds + 1.0E-6 < getGrowthStepCostSeconds(clicked, level)) {
      return false;
    }

    BlockStateRef data = clicked.state();
    if (isSapling(data)) {
      return saplingStage(data) < SAPLING_MAX_STAGE
          || (getConfig().allowSaplingTreeGeneration && getTreeFeature(clicked.type()) != null);
    }
    return isAgeable(data) && data.age() < data.maxAge();
  }

  private TimeSpendResult accelerateTarget(PlayerRef player, BlockRef clicked, double storedSeconds, int level) {
    Optional<FurnaceRef> furnace = clicked.furnace();
    if (furnace.isPresent()) {
      if (!preferenceEnabled(player, FURNACES)) {
        return TimeSpendResult.none();
      }
      return accelerateFurnace(furnace.get(), storedSeconds, level);
    }

    Optional<BrewingStandRef> brewingStand = clicked.brewingStand();
    if (brewingStand.isPresent()) {
      if (!preferenceEnabled(player, BREWING)) {
        return TimeSpendResult.none();
      }
      return accelerateBrewingStand(brewingStand.get(), storedSeconds, level);
    }

    Optional<CampfireRef> campfire = clicked.campfire();
    if (campfire.isPresent()) {
      if (!preferenceEnabled(player, CAMPFIRES)) {
        return TimeSpendResult.none();
      }
      return accelerateCampfire(player, clicked, campfire.get(), storedSeconds, level);
    }

    return preferenceEnabled(player, GROWTH) ? accelerateGrowables(player, clicked, storedSeconds, level) : TimeSpendResult.none();
  }

  private TimeSpendResult accelerateFurnace(FurnaceRef furnace, double storedSeconds, int level) {
    ItemRef smelting = furnace.smelting();
    if (smelting == null || smelting.isEmpty() || smelting.type().isAir()) {
      return TimeSpendResult.none();
    }

    int totalCookTime = furnace.cookTimeTotal();
    int currentCookTime = furnace.cookTime();
    int remainingCookTime = Math.max(0, totalCookTime - currentCookTime);
    if (remainingCookTime <= 0) {
      return TimeSpendResult.none();
    }

    double cookTicksPerStoredSecond = getCookTicksPerStoredSecond(level);
    double spendMultiplier = getFurnaceSpendMultiplier();
    int affordableTicks = (int) Math.floor((storedSeconds / spendMultiplier) * cookTicksPerStoredSecond);
    int advanceTicks = Math.min(remainingCookTime, Math.min(affordableTicks, getMaxCookTicksPerUse(level)));
    if (advanceTicks <= 0) {
      return TimeSpendResult.none();
    }

    furnace.setCookTime((short) Math.min(totalCookTime, currentCookTime + advanceTicks));

    double spentSeconds = (advanceTicks / cookTicksPerStoredSecond) * spendMultiplier;
    return new TimeSpendResult(spentSeconds, advanceTicks, advanceTicks * getConfig().xpPerCookTick);
  }

  private TimeSpendResult accelerateBrewingStand(BrewingStandRef stand, double storedSeconds, int level) {
    int brewingTime = stand.brewingTime();
    if (brewingTime <= 0) {
      return TimeSpendResult.none();
    }

    double brewTicksPerStoredSecond = getBrewingTicksPerStoredSecond(level);
    double spendMultiplier = getBrewingSpendMultiplier();
    int affordableTicks = (int) Math.floor((storedSeconds / spendMultiplier) * brewTicksPerStoredSecond);
    int advanceTicks = Math.min(brewingTime, Math.min(affordableTicks, getMaxBrewingTicksPerUse(level)));
    if (advanceTicks <= 0) {
      return TimeSpendResult.none();
    }

    stand.setBrewingTime(Math.max(0, brewingTime - advanceTicks));

    double spentSeconds = (advanceTicks / brewTicksPerStoredSecond) * spendMultiplier;
    return new TimeSpendResult(spentSeconds, advanceTicks, advanceTicks * getConfig().xpPerBrewTick);
  }

  private TimeSpendResult accelerateCampfire(PlayerRef player, BlockRef block, CampfireRef campfire,
                                             double storedSeconds, int level) {
    double campfireTicksPerStoredSecond = getCampfireTicksPerStoredSecond(level);
    double spendMultiplier = getCampfireSpendMultiplier();
    int affordableTicks = (int) Math.floor((storedSeconds / spendMultiplier) * campfireTicksPerStoredSecond);
    int budgetTicks = Math.min(affordableTicks, getMaxCampfireTicksPerUse(level));
    if (budgetTicks <= 0) {
      return TimeSpendResult.none();
    }

    int usedTicks = 0;
    int[] cookTimes = new int[campfire.size()];
    for (int i = 0; i < campfire.size(); i++) {
      cookTimes[i] = -1;
    }
    for (int i = 0; i < campfire.size() && usedTicks < budgetTicks; i++) {
      ItemRef item = campfire.item(i);
      if (item == null || item.isEmpty() || item.type().isAir()) {
        continue;
      }

      int total = campfire.cookTimeTotal(i);
      int current = campfire.cookTime(i);
      int remaining = Math.max(0, total - current);
      if (remaining <= 0) {
        continue;
      }

      int step = Math.min(remaining, budgetTicks - usedTicks);
      cookTimes[i] = current + step;
      usedTicks += step;
    }

    if (usedTicks <= 0) {
      return TimeSpendResult.none();
    }
    if (!canMutateBlock(player, block)) {
      return TimeSpendResult.none();
    }

    for (int i = 0; i < cookTimes.length; i++) {
      if (cookTimes[i] >= 0) {
        campfire.setCookTime(i, cookTimes[i]);
      }
    }
    double spentSeconds = (usedTicks / campfireTicksPerStoredSecond) * spendMultiplier;
    return new TimeSpendResult(spentSeconds, usedTicks, usedTicks * getConfig().xpPerCampfireTick);
  }

  private TimeSpendResult accelerateAgeableEntity(AnimalOps animals, EntityRef entity, double storedSeconds, int level) {
    int currentAge = animals.age(entity);
    if (currentAge == 0) {
      return TimeSpendResult.none();
    }

    double ageTicksPerStoredSecond = getEntityAgeTicksPerStoredSecond(level);
    double spendMultiplier = getEntitySpendMultiplier();
    int affordableTicks = (int) Math.floor((storedSeconds / spendMultiplier) * ageTicksPerStoredSecond);
    int advanceTicks = Math.min(Math.abs(currentAge), Math.min(affordableTicks, getMaxEntityAgeTicksPerUse(level)));
    if (advanceTicks <= 0) {
      return TimeSpendResult.none();
    }

    if (currentAge < 0) {
      animals.setAge(entity, Math.min(0, currentAge + advanceTicks));
    } else {
      animals.setAge(entity, Math.max(0, currentAge - advanceTicks));
    }

    double spentSeconds = (advanceTicks / ageTicksPerStoredSecond) * spendMultiplier;
    return new TimeSpendResult(spentSeconds, advanceTicks, advanceTicks * getConfig().xpPerEntityAgeTick);
  }

  private TimeSpendResult accelerateGrowables(PlayerRef player, BlockRef clicked, double storedSeconds, int level) {
    int attempts = getMaxGrowthStepsPerUse(level);
    if (attempts <= 0 || storedSeconds <= 0) {
      return TimeSpendResult.none();
    }

    double remainingSeconds = storedSeconds;
    double spentSeconds = 0;
    int successes = 0;
    for (int i = 0; i < attempts; i++) {
      BlockRef target = clicked.world().blockAt(clicked.location());
      double stepCost = getGrowthStepCostSeconds(target, level);
      if (remainingSeconds + 1.0E-6 < stepCost) {
        break;
      }

      if (!applyDirectGrowthStep(player, target, level)) {
        break;
      }

      remainingSeconds -= stepCost;
      spentSeconds += stepCost;
      successes++;
    }

    if (successes <= 0 || spentSeconds <= 0) {
      return TimeSpendResult.none();
    }

    int effectTicks = Math.max(8, successes * 20);
    return new TimeSpendResult(spentSeconds, effectTicks, successes * getConfig().xpPerGrowthStep);
  }

  private boolean applyDirectGrowthStep(PlayerRef player, BlockRef block, int level) {
    if (isGrowthBlacklisted(block.type())) {
      return false;
    }
    BlockStateRef data = block.state();
    if (isSapling(data)) {
      int stage = saplingStage(data);
      if (stage < SAPLING_MAX_STAGE) {
        if (!canMutateBlock(player, block)) {
          return false;
        }
        block.setState(data.with(SAPLING_STAGE, String.valueOf(Math.min(SAPLING_MAX_STAGE, stage + 1))), true);
        return true;
      }

      if (!getConfig().allowSaplingTreeGeneration || !preferenceEnabled(player, TREES)) {
        return false;
      }

      if (ThreadLocalRandom.current().nextDouble() > getSaplingGrowChance(level)) {
        return false;
      }

      Id treeFeature = getTreeFeature(block.type());
      if (treeFeature == null) {
        return false;
      }
      if (!canMutateBlock(player, block)) {
        return false;
      }

      boolean generated = generateAuthorizedTree(player, block, treeFeature);
      if (generated) {
        emitTreeFlourish(block.location());
      }
      return generated;
    }

    if (isAgeable(data) && data.age() < data.maxAge()) {
      if (!canMutateBlock(player, block)) {
        return false;
      }
      block.setState(data.with("age", String.valueOf(Math.min(data.maxAge(), data.age() + 1))), true);
      return true;
    }

    return false;
  }

  private boolean canMutateBlock(PlayerRef player, BlockRef block) {
    return canBlockPlace(player, block.location())
        && ProtectionProbes.attemptBlockPlaceProbe(player, block);
  }

  private boolean generateAuthorizedTree(PlayerRef player, BlockRef block, Id treeFeature) {
    if (!preflightTreeRegion(player, block)) {
      return false;
    }

    long seed = ThreadLocalRandom.current().nextLong();
    List<BlockChange> plannedStates = block.world().planTree(treeFeature, block.pos(), seed);
    if (plannedStates.isEmpty()) {
      return false;
    }

    List<TreeChange> plannedChanges = new ArrayList<>(plannedStates.size());
    for (BlockChange state : plannedStates) {
      Loc location = state.block().location();
      if ((Tasks.isFoliaThreading()
          && (!Tasks.isOwnedByCurrentRegion(player) || !Tasks.isOwnedByCurrentRegion(location)))
          || !canBlockPlace(player, location)) {
        return false;
      }
      plannedChanges.add(TreeChange.from(state));
    }

    if (!ProtectionProbes.attemptTreeGrow(player, block, treeFeature, plannedStates)) {
      return false;
    }
    if (!matchesTreePlan(plannedChanges, block.world().planTree(treeFeature, block.pos(), seed))) {
      return false;
    }

    return block.world().growTree(treeFeature, block.pos(), seed);
  }

  private boolean preflightTreeRegion(PlayerRef player, BlockRef block) {
    if (!Tasks.isFoliaThreading()) {
      return true;
    }
    if (!Tasks.isOwnedByCurrentRegion(player)) {
      return false;
    }

    int minimumChunkX = Math.floorDiv(block.x() - TREE_PREFLIGHT_RADIUS, 16);
    int maximumChunkX = Math.floorDiv(block.x() + TREE_PREFLIGHT_RADIUS, 16);
    int minimumChunkZ = Math.floorDiv(block.z() - TREE_PREFLIGHT_RADIUS, 16);
    int maximumChunkZ = Math.floorDiv(block.z() + TREE_PREFLIGHT_RADIUS, 16);
    for (int chunkX = minimumChunkX; chunkX <= maximumChunkX; chunkX++) {
      for (int chunkZ = minimumChunkZ; chunkZ <= maximumChunkZ; chunkZ++) {
        Loc anchor = new Loc(block.world(), chunkX << 4, block.y(), chunkZ << 4);
        if (!Tasks.isOwnedByCurrentRegion(anchor)) {
          return false;
        }
      }
    }
    return true;
  }

  private boolean matchesTreePlan(List<TreeChange> plannedChanges, List<BlockChange> eventStates) {
    if (plannedChanges.size() != eventStates.size()) {
      return false;
    }
    for (int i = 0; i < plannedChanges.size(); i++) {
      if (!plannedChanges.get(i).matches(eventStates.get(i))) {
        return false;
      }
    }
    return true;
  }

  @Override
  protected boolean usesLearnerBoundTicking() {
    return true;
  }

  @Override
  public void onTick() {
    long now = System.currentTimeMillis();
    List<AdaptPlayer> candidates = learnedCandidates(now);
    ChronosWorkBudget.Batch batch = ChronosWorkBudget.batch(
        candidates.size(), playerCursor, getConfig().maxPlayersPerPass, HARD_MAX_PLAYERS_PER_PASS);
    for (int i = 0; i < batch.count(); i++) {
      AdaptPlayer adaptPlayer = candidates.get((batch.start() + i) % candidates.size());
      PlayerRef player = adaptPlayer.getPlayer();
      if (player != null && isChargeDue(player.uuid(), now)) {
        queueBottleCharge(player);
      }
    }
    playerCursor = batch.nextCursor();
  }

  private boolean isChargeDue(UUID playerId, long now) {
    Long due = nextChargeAt.get(playerId);
    return due == null || now >= due;
  }

  private void queueBottleCharge(PlayerRef player) {
    UUID playerId = player.uuid();
    if (pendingCharges.putIfAbsent(playerId, true) != null) {
      return;
    }
    boolean scheduled = Tasks.runEntity(player, () -> {
      try {
        chargeBottles(player, System.currentTimeMillis());
      } finally {
        pendingCharges.remove(playerId);
      }
    });
    if (!scheduled) {
      pendingCharges.remove(playerId);
    }
  }

  private void chargeBottles(PlayerRef player, long now) {
    if (!player.isOnline()) {
      return;
    }

    UUID playerId = player.uuid();
    int level = getActiveLevel(player);
    if (level <= 0) {
      nextChargeAt.remove(playerId);
      return;
    }

    ChronosWorkBudget.Pulse pulse = ChronosWorkBudget.pulse(
        now, nextChargeAt.get(playerId), CHARGE_INTERVAL_MILLIS, MAX_CATCH_UP_PULSES);
    nextChargeAt.put(playerId, pulse.nextAt());
    if (pulse.count() <= 0) {
      return;
    }

    double charge = (getConfig().chargePerSecond + (level * getConfig().chargePerSecondPerLevel)) * pulse.count();
    double maximum = getMaxStoredSeconds(level);
    boolean reachedFull = false;
    PlayerInventoryRef inventory = player.inventory();
    for (int slot = 0; slot < inventory.size(); slot++) {
      ItemRef stack = inventory.item(slot);
      if (!ChronoTimeBottle.isBindableItem(stack)) {
        continue;
      }

      double stored = ChronoTimeBottle.getStoredSeconds(stack);
      double capped = Math.min(maximum, stored + charge);
      if (capped <= stored) {
        continue;
      }

      ChronoTimeBottle.setStoredSeconds(stack, capped);
      inventory.setItem(slot, stack);
      reachedFull = reachedFull || (stored < maximum && capped >= maximum);
    }

    if (reachedFull) {
      fx(player.location(), FxPriority.AMBIENT)
          .particle(Particles.WAX_ON, 1, 0, 1.0D, 0, 0.1D, 0.0D)
          .sound(Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.3F, 1.9F);
    }
  }

  private enum GrowthProfile {
    CROP,
    NETHER_WART,
    SAPLING,
    STEM,
    BERRY_BUSH,
    VINE,
    CAVE_VINE,
    KELP,
    DEFAULT
  }

  private record TimeSpendResult(double spentSeconds, int effectTicks,
                                 double xpGain) {
    private static TimeSpendResult none() {
      return new TimeSpendResult(0, 0, 0);
    }

    private boolean applied() {
      return spentSeconds > 0;
    }
  }

  private record TreeChange(UUID worldId, int x, int y, int z, String blockData) {
    private static TreeChange from(BlockChange state) {
      BlockRef block = state.block();
      return new TreeChange(
          block.world().uid(),
          block.x(),
          block.y(),
          block.z(),
          state.state().asString()
      );
    }

    private boolean matches(BlockChange state) {
      BlockRef block = state.block();
      return block.world().uid().equals(worldId)
          && block.x() == x
          && block.y() == y
          && block.z() == z
          && state.state().asString().equals(blockData);
    }
  }

  @ConfigDescription("Carry a temporal bottle that stores time to accelerate timed blocks and baby animals.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Play Clock Sounds for the Chronos Time In ABottle adaptation.", impact = "True enables this behavior and false disables it.")
    boolean playClockSounds = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum seconds a bottle can store before any per-level scaling.", impact = "Higher values raise the stored time cap at every level.")
    double baseMaxStoredSeconds = 900;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Extra seconds added to the stored time cap per adaptation level.", impact = "Higher values make leveling raise the stored time cap faster.")
    double maxStoredSecondsPerLevel = 180;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Charge Per Second for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double chargePerSecond = 0.1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Charge Per Second Per Level for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double chargePerSecondPerLevel = 0.02;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum learned players processed in one bottle charge pass.", impact = "Higher values refresh more inventories immediately; lower values spread owner-thread inventory work across passes.")
    int maxPlayersPerPass = 32;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Base Cook Ticks Per Stored Second for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double baseCookTicksPerStoredSecond = 20;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cook Ticks Per Second Per Level for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cookTicksPerSecondPerLevel = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Cook Ticks Per Use for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int maxCookTicksPerUse = 140;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Cook Ticks Per Use Per Level for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int maxCookTicksPerUsePerLevel = 35;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Furnace Spend Multiplier for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double furnaceSpendMultiplier = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Base Brewing Ticks Per Stored Second for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double baseBrewingTicksPerStoredSecond = 20;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Brewing Ticks Per Second Per Level for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double brewingTicksPerSecondPerLevel = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Brewing Ticks Per Use for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int maxBrewingTicksPerUse = 140;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Brewing Ticks Per Use Per Level for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int maxBrewingTicksPerUsePerLevel = 35;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Brewing Spend Multiplier for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double brewingSpendMultiplier = 1.05;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Base Campfire Ticks Per Stored Second for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double baseCampfireTicksPerStoredSecond = 20;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Campfire Ticks Per Second Per Level for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double campfireTicksPerSecondPerLevel = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Campfire Ticks Per Use for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int maxCampfireTicksPerUse = 160;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Campfire Ticks Per Use Per Level for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int maxCampfireTicksPerUsePerLevel = 40;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Campfire Spend Multiplier for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double campfireSpendMultiplier = 0.9;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Base Entity Age Ticks Per Stored Second for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double baseEntityAgeTicksPerStoredSecond = 20;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Entity Age Ticks Per Second Per Level for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double entityAgeTicksPerSecondPerLevel = 4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Entity Age Ticks Per Use for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int maxEntityAgeTicksPerUse = 180;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Entity Age Ticks Per Use Per Level for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int maxEntityAgeTicksPerUsePerLevel = 55;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Entity Spend Multiplier for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double entitySpendMultiplier = 1.35;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Growth Steps Per Use for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int maxGrowthStepsPerUse = 6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Growth Steps Per Use Per Level for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int maxGrowthStepsPerUsePerLevel = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Allow Sapling Tree Generation for the Chronos Time In ABottle adaptation.", impact = "True enables this behavior and false disables it.")
    boolean allowSaplingTreeGeneration = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Sapling Grow Chance Base for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double saplingGrowChanceBase = 0.18;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Sapling Grow Chance Per Level for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double saplingGrowChancePerLevel = 0.04;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Growth Cost Multiplier for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double growthCostMultiplier = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Growth Cost Reduction Per Level for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double growthCostReductionPerLevel = 0.05;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Min Growth Cost Level Scale for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double minGrowthCostLevelScale = 0.45;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Min Growth Step Seconds for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double minGrowthStepSeconds = 0.06;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Sapling Growth Steps for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int saplingGrowthSteps = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Stem Growth Steps for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int stemGrowthSteps = 7;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Berry Growth Steps for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int berryGrowthSteps = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Vine Growth Steps for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int vineGrowthSteps = 5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cave Vine Growth Steps for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int caveVineGrowthSteps = 5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Kelp Growth Steps for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int kelpGrowthSteps = 5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Default Growth Steps for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int defaultGrowthSteps = 4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Crop Natural Seconds for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cropNaturalSeconds = 300;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Nether Wart Natural Seconds for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double netherWartNaturalSeconds = 420;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Sapling Natural Seconds for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double saplingNaturalSeconds = 900;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Stem Natural Seconds for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double stemNaturalSeconds = 660;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Berry Bush Natural Seconds for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double berryBushNaturalSeconds = 260;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Vine Natural Seconds for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double vineNaturalSeconds = 300;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cave Vine Natural Seconds for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double caveVineNaturalSeconds = 280;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Kelp Natural Seconds for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double kelpNaturalSeconds = 240;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Default Growable Natural Seconds for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double defaultGrowableNaturalSeconds = 420;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Crop Cost Multiplier for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cropCostMultiplier = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Nether Wart Cost Multiplier for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double netherWartCostMultiplier = 1.2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Sapling Cost Multiplier for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double saplingCostMultiplier = 2.2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Stem Cost Multiplier for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double stemCostMultiplier = 1.4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Berry Bush Cost Multiplier for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double berryBushCostMultiplier = 0.8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Vine Cost Multiplier for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double vineCostMultiplier = 0.85;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cave Vine Cost Multiplier for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double caveVineCostMultiplier = 0.9;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Kelp Cost Multiplier for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double kelpCostMultiplier = 0.75;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Default Growable Cost Multiplier for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double defaultGrowableCostMultiplier = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Cook Tick for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerCookTick = 0.08;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Brew Tick for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerBrewTick = 0.08;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Campfire Tick for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerCampfireTick = 0.08;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Entity Age Tick for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerEntityAgeTick = 0.06;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Growth Step for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerGrowthStep = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max XPPer Use for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxXPPerUse = 55;

    public Config() {
      baseCost = 6;
      costFactor = 0.35;
      initialCost = 6;
    }
  }
}
