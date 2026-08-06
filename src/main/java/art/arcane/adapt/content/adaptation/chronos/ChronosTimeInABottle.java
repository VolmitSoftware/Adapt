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

import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.ChronosMessages;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.ReceiveCancelledEvents;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.recipe.AdaptRecipe;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.content.item.ChronoTimeBottle;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Keyed;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.TreeType;
import org.bukkit.block.Block;
import org.bukkit.block.BrewingStand;
import org.bukkit.block.Campfire;
import org.bukkit.block.Furnace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Sapling;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class ChronosTimeInABottle extends SimpleAdaptation<ChronosTimeInABottle.Config> {
  private static final String RECIPE_KEY = "chronos-time-in-a-bottle";
  private static final long CHARGE_INTERVAL_MILLIS = 1000L;
  private static final int HARD_MAX_PLAYERS_PER_PASS = 32;
  private static final int MAX_CATCH_UP_PULSES = 4;

  private final Map<UUID, Long> nextChargeAt = playerState();
  private final Map<UUID, Boolean> pendingCharges = playerState();
  private int playerCursor;

  public ChronosTimeInABottle() {
    super("chronos-time-bottle");
    registerConfiguration(Config.class);
    setLocalizationKey("chronos.time_in_a_bottle");
    setIcon(Material.CLOCK);
    setInterval(CHARGE_INTERVAL_MILLIS);

    registerRecipe(AdaptRecipe.shapeless()
        .key(RECIPE_KEY)
        .ingredient(Material.CLOCK)
        .ingredient(Material.POTION)
        .ingredient(Material.GLASS_BOTTLE)
        .result(ChronoTimeBottle.withStoredSeconds(0))
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.CLOCK)
        .key("challenge_chronos_bottle_seconds_1k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.RECOVERY_COMPASS)
            .key("challenge_chronos_bottle_seconds_25k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
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

  @EventHandler
  public void on(PlayerQuitEvent e) {
    nextChargeAt.remove(e.getPlayer().getUniqueId());
    pendingCharges.remove(e.getPlayer().getUniqueId());
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(CraftItemEvent e) {
    if (!(e.getRecipe() instanceof Keyed keyed) || !keyed.getKey().getKey().equals(RECIPE_KEY)) {
      return;
    }

    boolean hasSwiftnessPotion = false;
    for (ItemStack item : e.getInventory().getMatrix()) {
      if (item == null || item.getType() != Material.POTION) {
        continue;
      }
      if (item.getItemMeta() instanceof PotionMeta meta && meta.getBasePotionType() == PotionType.SWIFTNESS) {
        hasSwiftnessPotion = true;
        break;
      }
    }

    if (!hasSwiftnessPotion) {
      e.setCancelled(true);
      if (e.getWhoClicked() instanceof Player p && getConfig().playClockSounds) {
        ChronosSoundFX.playClockReject(p);
      }
    }
  }

  @Override
  public void addStats(int level, Element v) {
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

  private TreeType getTreeType(Material type) {
    return switch (type) {
      case OAK_SAPLING ->
          ThreadLocalRandom.current().nextBoolean() ? TreeType.TREE : TreeType.BIG_TREE;
      case SPRUCE_SAPLING ->
          ThreadLocalRandom.current().nextBoolean() ? TreeType.REDWOOD : TreeType.TALL_REDWOOD;
      case BIRCH_SAPLING -> TreeType.BIRCH;
      case JUNGLE_SAPLING ->
          ThreadLocalRandom.current().nextBoolean() ? TreeType.SMALL_JUNGLE : TreeType.JUNGLE;
      case ACACIA_SAPLING -> TreeType.ACACIA;
      case DARK_OAK_SAPLING -> TreeType.DARK_OAK;
      case CHERRY_SAPLING -> TreeType.CHERRY;
      case MANGROVE_PROPAGULE ->
          ThreadLocalRandom.current().nextBoolean() ? TreeType.MANGROVE : TreeType.TALL_MANGROVE;
      case AZALEA, FLOWERING_AZALEA -> TreeType.AZALEA;
      default -> null;
    };
  }

  private double getGrowthStepCostSeconds(Block target, int level) {
    GrowthProfile profile = detectGrowthProfile(target.getType());
    double naturalSeconds = getNaturalGrowthSeconds(profile);
    int steps = getEstimatedGrowthSteps(target, profile);
    double baseStepSeconds = naturalSeconds / Math.max(1, steps);
    double profileMultiplier = getGrowthProfileCostMultiplier(profile);
    double scaled = baseStepSeconds * profileMultiplier * getConfig().growthCostMultiplier * getGrowthLevelScale(level);
    return Math.max(getConfig().minGrowthStepSeconds, scaled);
  }

  private int getEstimatedGrowthSteps(Block target, GrowthProfile profile) {
    BlockData data = target.getBlockData();
    if (data instanceof Ageable ageable) {
      return Math.max(1, ageable.getMaximumAge());
    }

    if (data instanceof Sapling) {
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

  private GrowthProfile detectGrowthProfile(Material type) {
    String name = type.name();

    if (type == Material.NETHER_WART) {
      return GrowthProfile.NETHER_WART;
    }

    if (type == Material.SWEET_BERRY_BUSH) {
      return GrowthProfile.BERRY_BUSH;
    }

    if (type == Material.KELP || type == Material.KELP_PLANT) {
      return GrowthProfile.KELP;
    }

    if (type == Material.CAVE_VINES || type == Material.CAVE_VINES_PLANT) {
      return GrowthProfile.CAVE_VINE;
    }

    if (type == Material.VINE || type == Material.WEEPING_VINES || type == Material.WEEPING_VINES_PLANT
        || type == Material.TWISTING_VINES || type == Material.TWISTING_VINES_PLANT) {
      return GrowthProfile.VINE;
    }

    if (name.endsWith("_SAPLING") || type == Material.MANGROVE_PROPAGULE || type == Material.AZALEA
        || type == Material.FLOWERING_AZALEA) {
      return GrowthProfile.SAPLING;
    }

    if (type == Material.PUMPKIN_STEM || type == Material.MELON_STEM || type == Material.ATTACHED_MELON_STEM
        || type == Material.ATTACHED_PUMPKIN_STEM) {
      return GrowthProfile.STEM;
    }

    if (type == Material.WHEAT || type == Material.CARROTS || type == Material.POTATOES || type == Material.BEETROOTS
        || type == Material.COCOA || type == Material.TORCHFLOWER_CROP || type == Material.PITCHER_CROP) {
      return GrowthProfile.CROP;
    }

    return GrowthProfile.DEFAULT;
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(PlayerItemConsumeEvent e) {
    if (ChronoTimeBottle.isBindableItem(e.getItem())) {
      e.setCancelled(true);
      if (getConfig().playClockSounds) {
        ChronosSoundFX.playClockReject(e.getPlayer());
      }
    }
  }

  @ReceiveCancelledEvents
  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(PlayerInteractEvent e) {
    Action action = e.getAction();
    if (action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR) {
      return;
    }

    Player p = e.getPlayer();
    EquipmentSlot handSlot = e.getHand();
    if (handSlot == null) {
      return;
    }

    ItemStack hand = handSlot == EquipmentSlot.OFF_HAND
        ? p.getInventory().getItemInOffHand()
        : p.getInventory().getItemInMainHand();
    if (!ChronoTimeBottle.isBindableItem(hand)) {
      return;
    }

    boolean vetoed = e.getClickedBlock() != null
        ? e.useInteractedBlock() == Event.Result.DENY
        : e.useItemInHand() == Event.Result.DENY;

    // Chrono bottles are never drinkable; always deny vanilla potion use.
    e.setUseItemInHand(Event.Result.DENY);

    if (vetoed) {
      return;
    }

    Block clicked = action == Action.RIGHT_CLICK_BLOCK ? e.getClickedBlock() : p.getTargetBlockExact(5);
    if (clicked == null) {
      return;
    }
    int level = getActiveInteractLevel(p, clicked.getLocation());
    if (level <= 0) {
      return;
    }
    double storedSeconds = ChronoTimeBottle.getStoredSeconds(hand);
    if (storedSeconds <= 0) {
      return;
    }

    TimeSpendResult result = accelerateTarget(clicked, storedSeconds, level);
    if (!result.applied()) {
      return;
    }

    e.setCancelled(true);
    double newStored = Math.max(0, storedSeconds - result.spentSeconds());
    ChronoTimeBottle.setStoredSeconds(hand, newStored);
    addStat(p, "chronos.time-bottle.seconds-spent", result.spentSeconds());

    Location burstAt = clicked.getLocation().add(0.5, 1.0, 0.5);
    if (getConfig().playClockSounds) {
      ChronosSoundFX.playBottleUse(p, burstAt, result.effectTicks());
    }
    emitBlockUseFx(clicked, burstAt);
    if (newStored <= 0) {
      emitBottleEmptyFx(burstAt);
    }

    xp(p, burstAt, Math.min(getConfig().maxXPPerUse, result.xpGain()));
  }

  private void emitBlockUseFx(Block clicked, Location at) {
    if (clicked.getState() instanceof Furnace) {
      fx(at, FxPriority.TRANSITION)
          .particle(Particle.FLAME, 6, 0, 0.2D, 0, 0.2D, 0.01D)
          .particle(Particles.SMOKE, 4, 0, 0.3D, 0, 0.15D, 0.02D)
          .sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.4F, 1.4F);
      return;
    }

    if (clicked.getState() instanceof BrewingStand) {
      fx(at, FxPriority.TRANSITION)
          .particle(Particle.WITCH, 6, 0, 0.3D, 0, 0.2D, 0.02D)
          .particle(Particles.ENCHANTMENT_TABLE, 8, 0, 0.3D, 0, 0.25D, 0.03D)
          .sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.4F, 1.5F);
      return;
    }

    if (clicked.getState() instanceof Campfire) {
      fx(at, FxPriority.TRANSITION)
          .particle(Particle.FLAME, 8, 0, 0.2D, 0, 0.25D, 0.02D)
          .particle(Particles.SMOKE, 3, 0, 0.4D, 0, 0.1D, 0.02D)
          .sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.4F, 1.3F);
      return;
    }

    fx(at, FxPriority.TRANSITION)
        .particle(Particles.VILLAGER_HAPPY, 10, 0, 0.3D, 0, 0.3D, 0.01D)
        .particle(Particle.COMPOSTER, 6, 0, 0.3D, 0, 0.2D, 0.02D)
        .column(Particles.END_ROD, 4, 1.0D);
  }

  private void emitBottleEmptyFx(Location at) {
    fx(at, FxPriority.TRANSITION)
        .burst(Particles.SMOKE, 3, 0.2D)
        .sound(Sound.BLOCK_NOTE_BLOCK_BASS, 0.3F, 0.7F);
  }

  private void emitTreeFlourish(Location base) {
    Location at = base.clone().add(0.5, 0, 0.5);
    fx(at, FxPriority.TRANSITION)
        .chord(Sound.BLOCK_AZALEA_LEAVES_PLACE, 0.7F, 0.8F, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6F, 1.4F);
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

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(PlayerInteractEntityEvent e) {
    if (e.getHand() != EquipmentSlot.HAND) {
      return;
    }

    Player p = e.getPlayer();
    ItemStack hand = p.getInventory().getItemInMainHand();
    if (!ChronoTimeBottle.isBindableItem(hand)) {
      return;
    }

    if (!(e.getRightClicked() instanceof org.bukkit.entity.Ageable ageable)) {
      return;
    }

    int level = getActiveInteractLevel(p, e.getRightClicked().getLocation());
    if (level <= 0) {
      return;
    }

    int currentAge = ageable.getAge();
    if (currentAge == 0) {
      return;
    }
    double storedSeconds = ChronoTimeBottle.getStoredSeconds(hand);
    if (storedSeconds <= 0) {
      return;
    }

    TimeSpendResult result = accelerateAgeableEntity(ageable, storedSeconds, level);
    if (!result.applied()) {
      return;
    }

    e.setCancelled(true);
    double newStored = Math.max(0, storedSeconds - result.spentSeconds());
    ChronoTimeBottle.setStoredSeconds(hand, newStored);
    addStat(p, "chronos.time-bottle.seconds-spent", result.spentSeconds());

    Location entityBurst = ageable.getLocation().add(0, 1.0, 0);
    if (getConfig().playClockSounds) {
      ChronosSoundFX.playBottleUse(p, entityBurst, result.effectTicks());
    }
    fx(entityBurst, FxPriority.TRANSITION)
        .particle(Particle.WAX_ON, 6, 0, 0.4D, 0, 0.3D, 0.02D)
        .particle(Particle.HEART, 2, 0, 0.5D, 0, 0.2D, 0.0D)
        .particle(Particles.ENCHANTMENT_TABLE, 8, 0, 0.4D, 0, 0.25D, 0.03D);
    if (newStored <= 0) {
      emitBottleEmptyFx(entityBurst);
    }

    xp(p, entityBurst, Math.min(getConfig().maxXPPerUse, result.xpGain()));
  }

  private TimeSpendResult accelerateTarget(Block clicked, double storedSeconds, int level) {
    if (clicked.getState() instanceof Furnace furnace) {
      return accelerateFurnace(furnace, storedSeconds, level);
    }

    if (clicked.getState() instanceof BrewingStand brewingStand) {
      return accelerateBrewingStand(brewingStand, storedSeconds, level);
    }

    if (clicked.getState() instanceof Campfire campfire) {
      return accelerateCampfire(campfire, storedSeconds, level);
    }

    return accelerateGrowables(clicked, storedSeconds, level);
  }

  private TimeSpendResult accelerateFurnace(Furnace furnace, double storedSeconds, int level) {
    if (furnace.getInventory().getSmelting() == null || furnace.getInventory().getSmelting().getType().isAir()) {
      return TimeSpendResult.none();
    }

    int totalCookTime = furnace.getCookTimeTotal();
    int currentCookTime = furnace.getCookTime();
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
    furnace.update(true, true);

    double spentSeconds = (advanceTicks / cookTicksPerStoredSecond) * spendMultiplier;
    return new TimeSpendResult(spentSeconds, advanceTicks, advanceTicks * getConfig().xpPerCookTick);
  }

  private TimeSpendResult accelerateBrewingStand(BrewingStand stand, double storedSeconds, int level) {
    int brewingTime = stand.getBrewingTime();
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
    stand.update(true, true);

    double spentSeconds = (advanceTicks / brewTicksPerStoredSecond) * spendMultiplier;
    return new TimeSpendResult(spentSeconds, advanceTicks, advanceTicks * getConfig().xpPerBrewTick);
  }

  private TimeSpendResult accelerateCampfire(Campfire campfire, double storedSeconds, int level) {
    double campfireTicksPerStoredSecond = getCampfireTicksPerStoredSecond(level);
    double spendMultiplier = getCampfireSpendMultiplier();
    int affordableTicks = (int) Math.floor((storedSeconds / spendMultiplier) * campfireTicksPerStoredSecond);
    int budgetTicks = Math.min(affordableTicks, getMaxCampfireTicksPerUse(level));
    if (budgetTicks <= 0) {
      return TimeSpendResult.none();
    }

    int usedTicks = 0;
    for (int i = 0; i < campfire.getSize() && usedTicks < budgetTicks; i++) {
      ItemStack item = campfire.getItem(i);
      if (item == null || item.getType().isAir()) {
        continue;
      }

      int total = campfire.getCookTimeTotal(i);
      int current = campfire.getCookTime(i);
      int remaining = Math.max(0, total - current);
      if (remaining <= 0) {
        continue;
      }

      int step = Math.min(remaining, budgetTicks - usedTicks);
      campfire.setCookTime(i, current + step);
      usedTicks += step;
    }

    if (usedTicks <= 0) {
      return TimeSpendResult.none();
    }

    campfire.update(true, true);
    double spentSeconds = (usedTicks / campfireTicksPerStoredSecond) * spendMultiplier;
    return new TimeSpendResult(spentSeconds, usedTicks, usedTicks * getConfig().xpPerCampfireTick);
  }

  private TimeSpendResult accelerateAgeableEntity(org.bukkit.entity.Ageable entity, double storedSeconds, int level) {
    int currentAge = entity.getAge();
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
      entity.setAge(Math.min(0, currentAge + advanceTicks));
    } else {
      entity.setAge(Math.max(0, currentAge - advanceTicks));
    }

    double spentSeconds = (advanceTicks / ageTicksPerStoredSecond) * spendMultiplier;
    return new TimeSpendResult(spentSeconds, advanceTicks, advanceTicks * getConfig().xpPerEntityAgeTick);
  }

  private TimeSpendResult accelerateGrowables(Block clicked, double storedSeconds, int level) {
    int attempts = getMaxGrowthStepsPerUse(level);
    if (attempts <= 0 || storedSeconds <= 0) {
      return TimeSpendResult.none();
    }

    double remainingSeconds = storedSeconds;
    double spentSeconds = 0;
    int successes = 0;
    for (int i = 0; i < attempts; i++) {
      Block target = clicked.getWorld().getBlockAt(clicked.getLocation());
      double stepCost = getGrowthStepCostSeconds(target, level);
      if (remainingSeconds + 1.0E-6 < stepCost) {
        break;
      }

      if (!applyDirectGrowthStep(target, level)) {
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

  private boolean applyDirectGrowthStep(Block block, int level) {
    BlockData data = block.getBlockData();
    if (data instanceof Sapling sapling) {
      if (sapling.getStage() < sapling.getMaximumStage()) {
        sapling.setStage(Math.min(sapling.getMaximumStage(), sapling.getStage() + 1));
        block.setBlockData(data, true);
        return true;
      }

      if (!getConfig().allowSaplingTreeGeneration) {
        return false;
      }

      if (ThreadLocalRandom.current().nextDouble() > getSaplingGrowChance(level)) {
        return false;
      }

      TreeType treeType = getTreeType(block.getType());
      if (treeType == null) {
        return false;
      }

      boolean generated = block.getWorld().generateTree(block.getLocation(), treeType);
      if (generated) {
        emitTreeFlourish(block.getLocation());
      }
      return generated;
    }

    if (data instanceof Ageable ageable && ageable.getAge() < ageable.getMaximumAge()) {
      ageable.setAge(Math.min(ageable.getMaximumAge(), ageable.getAge() + 1));
      block.setBlockData(data, true);
      return true;
    }

    return false;
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
      Player player = adaptPlayer.getPlayer();
      if (player != null && isChargeDue(player.getUniqueId(), now)) {
        queueBottleCharge(player);
      }
    }
    playerCursor = batch.nextCursor();
  }

  private boolean isChargeDue(UUID playerId, long now) {
    Long due = nextChargeAt.get(playerId);
    return due == null || now >= due;
  }

  private void queueBottleCharge(Player player) {
    UUID playerId = player.getUniqueId();
    if (pendingCharges.putIfAbsent(playerId, true) != null) {
      return;
    }
    boolean scheduled = J.runEntity(player, () -> {
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

  private void chargeBottles(Player player, long now) {
    if (!player.isOnline()) {
      return;
    }

    UUID playerId = player.getUniqueId();
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
    for (ItemStack stack : player.getInventory().getContents()) {
      if (!ChronoTimeBottle.isBindableItem(stack)) {
        continue;
      }

      double stored = ChronoTimeBottle.getStoredSeconds(stack);
      double capped = Math.min(maximum, stored + charge);
      if (capped <= stored) {
        continue;
      }

      ChronoTimeBottle.setStoredSeconds(stack, capped);
      reachedFull = reachedFull || (stored < maximum && capped >= maximum);
    }

    if (reachedFull) {
      fx(player.getLocation(), FxPriority.AMBIENT)
          .particle(Particle.WAX_ON, 1, 0, 1.0D, 0, 0.1D, 0.0D)
          .sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.3F, 1.9F);
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
