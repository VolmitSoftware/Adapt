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

import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.ReceiveCancelledEvents;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.FxTimeline;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.content.item.ChronoTimeBottle;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.inventory.CraftItemEvent;
import art.arcane.adapt.platform.event.player.PlayerInteractEntityEvent;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerItemConsumeEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.recipe.Ingredient;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;

import java.util.List;

public class ChronosTimeInABottle extends SimpleAdaptation<ChronosTimeInABottle.Config> {
  public static final Id ID = Id.adapt("chronos/time_bottle");
  public static final Id SWIFTNESS = Id.minecraft("swiftness");
  public static final PlayerPreference<CommonPreferences.Toggle> CLOCK_SOUNDS = CommonPreferences.toggle("clock-sounds", Text.key("adapt.chronos.preferences.chronostimeinabottle.clock-sounds"), CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> TREES = CommonPreferences.toggle("trees", Text.key("adapt.chronos.preferences.chronostimeinabottle.trees"), CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> FURNACES = CommonPreferences.toggle("furnaces", Text.key("adapt.chronos.preferences.chronostimeinabottle.furnaces"), CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> BREWING = CommonPreferences.toggle("brewing", Text.key("adapt.chronos.preferences.chronostimeinabottle.brewing"), CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> CAMPFIRES = CommonPreferences.toggle("campfires", Text.key("adapt.chronos.preferences.chronostimeinabottle.campfires"), CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> GROWTH = CommonPreferences.toggle("growth", Text.key("adapt.chronos.preferences.chronostimeinabottle.growth"), CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> ANIMALS = CommonPreferences.toggle("animals", Text.key("adapt.chronos.preferences.chronostimeinabottle.animals"), CommonPreferences.Toggle.ON);

  static final String RECIPE_KEY = "chronos-time-in-a-bottle";
  private static final Id POTION = Id.minecraft("potion");

  private final ChronosBottle bottle = new ChronosBottle(this, this::getConfig, () -> 1D, () -> 1D,
      (PlayerRef p, ChronosBottle.TimeSpendResult result) -> addStat(p, "chronos.time-bottle.seconds-spent", result.spentSeconds()),
      playerState(), playerState());

  public ChronosTimeInABottle() {
    super(ID);
    registerConfiguration(Config.class);
    registerItem(ChronoTimeBottle.ITEM);
    setLocalizationKey("adapt.chronos.time_in_a_bottle");
    setIcon(Id.minecraft("clock"));
    setInterval(ChronosBottle.CHARGE_INTERVAL_MILLIS);

    registerRecipe(RecipeSpec.shapeless(Id.adapt(RECIPE_KEY), List.of(Ingredient.of(Id.minecraft("clock")), Ingredient.of(POTION), Ingredient.of(Id.minecraft("glass_bottle"))), ChronoTimeBottle.withStoredSeconds(0)));
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("clock"))
        .key("challenge_chronos_bottle_seconds_1k")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("recovery_compass"))
            .key("challenge_chronos_bottle_seconds_25k")
            .frame(AdvancementFrame.CHALLENGE)
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
    bottle.forget(e);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(CraftItemEvent e) {
    bottle.craft(e);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, CLOCK_SOUNDS, TREES, FURNACES, BREWING, CAMPFIRES, GROWTH, ANIMALS);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Format.f(bottle.getChargePerSecond(level), 2), 1);
    statLore(v, TextFormat.YELLOW, "+ ", Math.round(bottle.getCookTicksPerStoredSecond(level)), 2);
    statLore(v, TextFormat.AQUA, "+ ", formatStoredSeconds(bottle.getMaxStoredSeconds(level)), Text.key("adapt.chronos.time_in_a_bottle.max_stored_lore"));
    v.addLore(TextFormat.GRAY + "* " + Text.key("adapt.chronos.time_in_a_bottle.lore3").resolve());
  }

  static double maxStoredSeconds(double base, double perLevel, int level) {
    double scaled = base + (Math.max(0, level) * perLevel);
    return Double.isFinite(scaled) ? Math.max(0D, scaled) : 0D;
  }

  static String formatStoredSeconds(double seconds) {
    return Format.duration((long) (Math.max(0D, seconds) * 1000D), 1);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(PlayerItemConsumeEvent e) {
    bottle.consume(e);
  }

  @ReceiveCancelledEvents
  @Handle(priority = EventPriority.HIGHEST)
  public void on(PlayerInteractEvent e) {
    bottle.interact(e);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(PlayerInteractEntityEvent e) {
    bottle.interactEntity(e);
  }

  @Override
  protected boolean usesLearnerBoundTicking() {
    return true;
  }

  @Override
  public void onTick() {
    bottle.tick();
  }

  public static void blockUseFx(Adaptation<?> owner, BlockRef clicked, Loc at) {
    if (clicked.furnace().isPresent()) {
      Fx.now(owner, at, FxPriority.TRANSITION)
          .particle(Particles.FLAME, 6, 0, 0.2D, 0, 0.2D, 0.01D)
          .particle(Particles.SMOKE, 4, 0, 0.3D, 0, 0.15D, 0.02D)
          .sound(Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.4F, 1.4F);
      return;
    }

    if (clicked.brewingStand().isPresent()) {
      Fx.now(owner, at, FxPriority.TRANSITION)
          .particle(Particles.WITCH, 6, 0, 0.3D, 0, 0.2D, 0.02D)
          .particle(Particles.ENCHANT, 8, 0, 0.3D, 0, 0.25D, 0.03D)
          .sound(Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.4F, 1.5F);
      return;
    }

    if (clicked.campfire().isPresent()) {
      Fx.now(owner, at, FxPriority.TRANSITION)
          .particle(Particles.FLAME, 8, 0, 0.2D, 0, 0.25D, 0.02D)
          .particle(Particles.SMOKE, 3, 0, 0.4D, 0, 0.1D, 0.02D)
          .sound(Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.4F, 1.3F);
      return;
    }

    Fx.now(owner, at, FxPriority.TRANSITION)
        .particle(Particles.VILLAGER_HAPPY, 10, 0, 0.3D, 0, 0.3D, 0.01D)
        .particle(Particles.COMPOSTER, 6, 0, 0.3D, 0, 0.2D, 0.02D)
        .column(Particles.END_ROD, 4, 1.0D);
  }

  public static void bottleEmptyFx(Adaptation<?> owner, Loc at) {
    Fx.now(owner, at, FxPriority.TRANSITION)
        .burst(Particles.SMOKE, 3, 0.2D)
        .sound(Sounds.BLOCK_NOTE_BLOCK_BASS, 0.3F, 0.7F);
  }

  public static void treeFlourishFx(Adaptation<?> owner, Loc base) {
    Loc at = base.add(0.5, 0, 0.5);
    Fx.now(owner, at, FxPriority.TRANSITION)
        .chord(Sounds.BLOCK_AZALEA_LEAVES_PLACE, 0.7F, 0.8F, Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.6F, 1.4F);
    FxTimeline.at(owner, at)
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

  public static void entityUseFx(Adaptation<?> owner, Loc at) {
    Fx.now(owner, at, FxPriority.TRANSITION)
        .particle(Particles.WAX_ON, 6, 0, 0.4D, 0, 0.3D, 0.02D)
        .particle(Particles.HEART, 2, 0, 0.5D, 0, 0.2D, 0.0D)
        .particle(Particles.ENCHANT, 8, 0, 0.4D, 0, 0.25D, 0.03D);
  }

  public static void bottleFullFx(Adaptation<?> owner, PlayerRef player) {
    Fx.now(owner, player.location(), FxPriority.AMBIENT)
        .particle(Particles.WAX_ON, 1, 0, 1.0D, 0, 0.1D, 0.0D)
        .sound(Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.3F, 1.9F);
  }

  @ConfigDescription("Carry a temporal bottle that stores time to accelerate timed blocks and baby animals.")
  public static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Controls Play Clock Sounds for the Chronos Time In ABottle adaptation.", impact = "True enables this behavior and false disables it.")
    boolean playClockSounds = true;
    @ConfigDoc(value = "Maximum seconds a bottle can store before any per-level scaling.", impact = "Higher values raise the stored time cap at every level.")
    double baseMaxStoredSeconds = 900;
    @ConfigDoc(value = "Extra seconds added to the stored time cap per adaptation level.", impact = "Higher values make leveling raise the stored time cap faster.")
    double maxStoredSecondsPerLevel = 180;
    @ConfigDoc(value = "Controls Charge Per Second for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double chargePerSecond = 0.1;
    @ConfigDoc(value = "Controls Charge Per Second Per Level for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double chargePerSecondPerLevel = 0.02;
    @ConfigDoc(value = "Maximum learned players processed in one bottle charge pass.", impact = "Higher values refresh more inventories immediately; lower values spread owner-thread inventory work across passes.")
    int maxPlayersPerPass = 32;
    @ConfigDoc(value = "Controls Base Cook Ticks Per Stored Second for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double baseCookTicksPerStoredSecond = 20;
    @ConfigDoc(value = "Controls Cook Ticks Per Second Per Level for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cookTicksPerSecondPerLevel = 3;
    @ConfigDoc(value = "Controls Max Cook Ticks Per Use for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int maxCookTicksPerUse = 140;
    @ConfigDoc(value = "Controls Max Cook Ticks Per Use Per Level for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int maxCookTicksPerUsePerLevel = 35;
    @ConfigDoc(value = "Controls Furnace Spend Multiplier for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double furnaceSpendMultiplier = 1;
    @ConfigDoc(value = "Controls Base Brewing Ticks Per Stored Second for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double baseBrewingTicksPerStoredSecond = 20;
    @ConfigDoc(value = "Controls Brewing Ticks Per Second Per Level for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double brewingTicksPerSecondPerLevel = 3;
    @ConfigDoc(value = "Controls Max Brewing Ticks Per Use for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int maxBrewingTicksPerUse = 140;
    @ConfigDoc(value = "Controls Max Brewing Ticks Per Use Per Level for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int maxBrewingTicksPerUsePerLevel = 35;
    @ConfigDoc(value = "Controls Brewing Spend Multiplier for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double brewingSpendMultiplier = 1.05;
    @ConfigDoc(value = "Controls Base Campfire Ticks Per Stored Second for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double baseCampfireTicksPerStoredSecond = 20;
    @ConfigDoc(value = "Controls Campfire Ticks Per Second Per Level for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double campfireTicksPerSecondPerLevel = 3;
    @ConfigDoc(value = "Controls Max Campfire Ticks Per Use for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int maxCampfireTicksPerUse = 160;
    @ConfigDoc(value = "Controls Max Campfire Ticks Per Use Per Level for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int maxCampfireTicksPerUsePerLevel = 40;
    @ConfigDoc(value = "Controls Campfire Spend Multiplier for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double campfireSpendMultiplier = 0.9;
    @ConfigDoc(value = "Controls Base Entity Age Ticks Per Stored Second for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double baseEntityAgeTicksPerStoredSecond = 20;
    @ConfigDoc(value = "Controls Entity Age Ticks Per Second Per Level for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double entityAgeTicksPerSecondPerLevel = 4;
    @ConfigDoc(value = "Controls Max Entity Age Ticks Per Use for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int maxEntityAgeTicksPerUse = 180;
    @ConfigDoc(value = "Controls Max Entity Age Ticks Per Use Per Level for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int maxEntityAgeTicksPerUsePerLevel = 55;
    @ConfigDoc(value = "Controls Entity Spend Multiplier for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double entitySpendMultiplier = 1.35;
    @ConfigDoc(value = "Controls Max Growth Steps Per Use for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int maxGrowthStepsPerUse = 6;
    @ConfigDoc(value = "Controls Max Growth Steps Per Use Per Level for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int maxGrowthStepsPerUsePerLevel = 2;
    @ConfigDoc(value = "Controls Allow Sapling Tree Generation for the Chronos Time In ABottle adaptation.", impact = "True enables this behavior and false disables it.")
    boolean allowSaplingTreeGeneration = true;
    @ConfigDoc(value = "Controls Sapling Grow Chance Base for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double saplingGrowChanceBase = 0.18;
    @ConfigDoc(value = "Controls Sapling Grow Chance Per Level for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double saplingGrowChancePerLevel = 0.04;
    @ConfigDoc(value = "Controls Growth Cost Multiplier for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double growthCostMultiplier = 1;
    @ConfigDoc(value = "Controls Growth Cost Reduction Per Level for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double growthCostReductionPerLevel = 0.05;
    @ConfigDoc(value = "Controls Min Growth Cost Level Scale for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double minGrowthCostLevelScale = 0.45;
    @ConfigDoc(value = "Controls Min Growth Step Seconds for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double minGrowthStepSeconds = 0.06;
    @ConfigDoc(value = "Controls Sapling Growth Steps for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int saplingGrowthSteps = 2;
    @ConfigDoc(value = "Controls Stem Growth Steps for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int stemGrowthSteps = 7;
    @ConfigDoc(value = "Controls Berry Growth Steps for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int berryGrowthSteps = 3;
    @ConfigDoc(value = "Controls Vine Growth Steps for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int vineGrowthSteps = 5;
    @ConfigDoc(value = "Controls Cave Vine Growth Steps for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int caveVineGrowthSteps = 5;
    @ConfigDoc(value = "Controls Kelp Growth Steps for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int kelpGrowthSteps = 5;
    @ConfigDoc(value = "Controls Default Growth Steps for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int defaultGrowthSteps = 4;
    @ConfigDoc(value = "Controls Crop Natural Seconds for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cropNaturalSeconds = 300;
    @ConfigDoc(value = "Controls Nether Wart Natural Seconds for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double netherWartNaturalSeconds = 420;
    @ConfigDoc(value = "Controls Sapling Natural Seconds for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double saplingNaturalSeconds = 900;
    @ConfigDoc(value = "Controls Stem Natural Seconds for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double stemNaturalSeconds = 660;
    @ConfigDoc(value = "Controls Berry Bush Natural Seconds for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double berryBushNaturalSeconds = 260;
    @ConfigDoc(value = "Controls Vine Natural Seconds for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double vineNaturalSeconds = 300;
    @ConfigDoc(value = "Controls Cave Vine Natural Seconds for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double caveVineNaturalSeconds = 280;
    @ConfigDoc(value = "Controls Kelp Natural Seconds for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double kelpNaturalSeconds = 240;
    @ConfigDoc(value = "Controls Default Growable Natural Seconds for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double defaultGrowableNaturalSeconds = 420;
    @ConfigDoc(value = "Controls Crop Cost Multiplier for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cropCostMultiplier = 1;
    @ConfigDoc(value = "Controls Nether Wart Cost Multiplier for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double netherWartCostMultiplier = 1.2;
    @ConfigDoc(value = "Controls Sapling Cost Multiplier for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double saplingCostMultiplier = 2.2;
    @ConfigDoc(value = "Controls Stem Cost Multiplier for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double stemCostMultiplier = 1.4;
    @ConfigDoc(value = "Controls Berry Bush Cost Multiplier for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double berryBushCostMultiplier = 0.8;
    @ConfigDoc(value = "Controls Vine Cost Multiplier for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double vineCostMultiplier = 0.85;
    @ConfigDoc(value = "Controls Cave Vine Cost Multiplier for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double caveVineCostMultiplier = 0.9;
    @ConfigDoc(value = "Controls Kelp Cost Multiplier for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double kelpCostMultiplier = 0.75;
    @ConfigDoc(value = "Controls Default Growable Cost Multiplier for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double defaultGrowableCostMultiplier = 1;
    @ConfigDoc(value = "Controls Xp Per Cook Tick for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerCookTick = 0.08;
    @ConfigDoc(value = "Controls Xp Per Brew Tick for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerBrewTick = 0.08;
    @ConfigDoc(value = "Controls Xp Per Campfire Tick for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerCampfireTick = 0.08;
    @ConfigDoc(value = "Controls Xp Per Entity Age Tick for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerEntityAgeTick = 0.06;
    @ConfigDoc(value = "Controls Xp Per Growth Step for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerGrowthStep = 2;
    @ConfigDoc(value = "Controls Max XPPer Use for the Chronos Time In ABottle adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxXPPerUse = 55;

    public Config() {
      baseCost = 5;
      costFactor = 0.6;
      initialCost = 3;
    }
  }
}
