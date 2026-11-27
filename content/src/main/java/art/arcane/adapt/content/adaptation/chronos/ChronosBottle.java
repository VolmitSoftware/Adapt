package art.arcane.adapt.content.adaptation.chronos;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.protection.ProtectionProbes;
import art.arcane.adapt.api.xp.XpProvenance;
import art.arcane.adapt.content.item.ChronoTimeBottle;
import art.arcane.adapt.platform.entity.AnimalOps;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
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
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;
import art.arcane.adapt.platform.world.BrewingStandRef;
import art.arcane.adapt.platform.world.CampfireRef;
import art.arcane.adapt.platform.world.FurnaceRef;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

import static art.arcane.adapt.content.adaptation.chronos.ChronosTimeInABottle.ANIMALS;
import static art.arcane.adapt.content.adaptation.chronos.ChronosTimeInABottle.BREWING;
import static art.arcane.adapt.content.adaptation.chronos.ChronosTimeInABottle.CAMPFIRES;
import static art.arcane.adapt.content.adaptation.chronos.ChronosTimeInABottle.CLOCK_SOUNDS;
import static art.arcane.adapt.content.adaptation.chronos.ChronosTimeInABottle.FURNACES;
import static art.arcane.adapt.content.adaptation.chronos.ChronosTimeInABottle.GROWTH;
import static art.arcane.adapt.content.adaptation.chronos.ChronosTimeInABottle.TREES;

/**
 * Time in a Bottle's bottle: carried chrono bottles fill with time on a pulse, and right-clicking a furnace, brewing
 * stand, campfire, growable or baby animal spends that time to hurry it along.
 */
public final class ChronosBottle {
  static final long CHARGE_INTERVAL_MILLIS = 1000L;
  private static final int HARD_MAX_PLAYERS_PER_PASS = 32;
  private static final int MAX_CATCH_UP_PULSES = 4;
  private static final int TREE_PREFLIGHT_RADIUS = 32;
  private static final int SAPLING_MAX_STAGE = 1;
  private static final String SAPLING_STAGE = "stage";
  private static final Id POTION = Id.minecraft("potion");
  private static final Set<String> CROPS = Set.of("wheat", "carrots", "potatoes", "beetroots", "cocoa", "torchflower_crop",
      "pitcher_crop");
  private static final Set<String> STEMS = Set.of("pumpkin_stem", "melon_stem", "attached_melon_stem", "attached_pumpkin_stem");
  private static final Set<String> VINES = Set.of("vine", "weeping_vines", "weeping_vines_plant", "twisting_vines",
      "twisting_vines_plant");

  private final SimpleAdaptation<?> owner;
  private final Supplier<ChronosTimeInABottle.Config> config;
  private final DoubleSupplier storageMultiplier;
  private final DoubleSupplier chargeMultiplier;
  private final BiConsumer<PlayerRef, TimeSpendResult> onSpent;
  private final Map<UUID, Long> nextChargeAt;
  private final Map<UUID, Boolean> pendingCharges;
  private int playerCursor;

  /**
   * {@code config} supplies Time in a Bottle's tuning; the multipliers scale how much a bottle holds and how fast it
   * fills; {@code onSpent} receives what each use spent. The maps come from the owner's player state.
   */
  public ChronosBottle(SimpleAdaptation<?> owner, Supplier<ChronosTimeInABottle.Config> config,
                       DoubleSupplier storageMultiplier, DoubleSupplier chargeMultiplier,
                       BiConsumer<PlayerRef, TimeSpendResult> onSpent, Map<UUID, Long> nextChargeAt,
                       Map<UUID, Boolean> pendingCharges) {
    this.owner = owner;
    this.config = config;
    this.storageMultiplier = storageMultiplier;
    this.chargeMultiplier = chargeMultiplier;
    this.onSpent = onSpent;
    this.nextChargeAt = nextChargeAt;
    this.pendingCharges = pendingCharges;
  }

  public void forget(PlayerQuitEvent e) {
    nextChargeAt.remove(e.player().uuid());
    pendingCharges.remove(e.player().uuid());
  }

  public void craft(CraftItemEvent e) {
    if (e.recipeKey() == null || !e.recipeKey().path().equals(ChronosTimeInABottle.RECIPE_KEY)) {
      return;
    }

    boolean hasSwiftnessPotion = false;
    for (ItemRef item : e.matrix()) {
      if (item == null || item.isEmpty() || !item.type().equals(POTION)) {
        continue;
      }
      Optional<PotionContents> contents = item.potionContents();
      if (contents.isPresent() && ChronosTimeInABottle.SWIFTNESS.equals(contents.get().potion())) {
        hasSwiftnessPotion = true;
        break;
      }
    }

    if (!hasSwiftnessPotion) {
      e.setCancelled(true);
      PlayerRef p = e.player();
      if ((config.get().playClockSounds && owner.preferenceEnabled(p, CLOCK_SOUNDS))) {
        ChronosSoundFX.playClockReject(p);
      }
    }
  }

  /** Most seconds one bottle holds at {@code level}. */
  public double getMaxStoredSeconds(int level) {
    return ChronosTimeInABottle.maxStoredSeconds(config.get().baseMaxStoredSeconds, config.get().maxStoredSecondsPerLevel, level)
        * storageMultiplier.getAsDouble();
  }

  /** Seconds a bottle gains per second at {@code level}. */
  public double getChargePerSecond(int level) {
    return (config.get().chargePerSecond + (level * config.get().chargePerSecondPerLevel)) * chargeMultiplier.getAsDouble();
  }

  public double getCookTicksPerStoredSecond(int level) {
    return config.get().baseCookTicksPerStoredSecond + (level * config.get().cookTicksPerSecondPerLevel);
  }

  private int getMaxCookTicksPerUse(int level) {
    return config.get().maxCookTicksPerUse + (level * config.get().maxCookTicksPerUsePerLevel);
  }

  private double getBrewingTicksPerStoredSecond(int level) {
    return config.get().baseBrewingTicksPerStoredSecond + (level * config.get().brewingTicksPerSecondPerLevel);
  }

  private int getMaxBrewingTicksPerUse(int level) {
    return config.get().maxBrewingTicksPerUse + (level * config.get().maxBrewingTicksPerUsePerLevel);
  }

  private double getCampfireTicksPerStoredSecond(int level) {
    return config.get().baseCampfireTicksPerStoredSecond + (level * config.get().campfireTicksPerSecondPerLevel);
  }

  private int getMaxCampfireTicksPerUse(int level) {
    return config.get().maxCampfireTicksPerUse + (level * config.get().maxCampfireTicksPerUsePerLevel);
  }

  private double getFurnaceSpendMultiplier() {
    return Math.max(0.01, config.get().furnaceSpendMultiplier);
  }

  private double getBrewingSpendMultiplier() {
    return Math.max(0.01, config.get().brewingSpendMultiplier);
  }

  private double getCampfireSpendMultiplier() {
    return Math.max(0.01, config.get().campfireSpendMultiplier);
  }

  private double getEntityAgeTicksPerStoredSecond(int level) {
    return config.get().baseEntityAgeTicksPerStoredSecond + (level * config.get().entityAgeTicksPerSecondPerLevel);
  }

  private int getMaxEntityAgeTicksPerUse(int level) {
    return config.get().maxEntityAgeTicksPerUse + (level * config.get().maxEntityAgeTicksPerUsePerLevel);
  }

  private double getEntitySpendMultiplier() {
    return Math.max(0.01, config.get().entitySpendMultiplier);
  }

  private int getMaxGrowthStepsPerUse(int level) {
    return config.get().maxGrowthStepsPerUse + (level * config.get().maxGrowthStepsPerUsePerLevel);
  }

  private double getGrowthLevelScale(int level) {
    return Math.max(config.get().minGrowthCostLevelScale, 1D - (level * config.get().growthCostReductionPerLevel));
  }

  private double getSaplingGrowChance(int level) {
    return Math.max(0, Math.min(1, config.get().saplingGrowChanceBase + (level * config.get().saplingGrowChancePerLevel)));
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
    return AdaptApi.settings().isGrowthBlacklisted(type);
  }

  private double getGrowthStepCostSeconds(BlockRef target, int level) {
    GrowthProfile profile = detectGrowthProfile(target.type());
    double naturalSeconds = getNaturalGrowthSeconds(profile);
    int steps = getEstimatedGrowthSteps(target, profile);
    double baseStepSeconds = naturalSeconds / Math.max(1, steps);
    double profileMultiplier = getGrowthProfileCostMultiplier(profile);
    double scaled = baseStepSeconds * profileMultiplier * config.get().growthCostMultiplier * getGrowthLevelScale(level);
    return Math.max(config.get().minGrowthStepSeconds, scaled);
  }

  private int getEstimatedGrowthSteps(BlockRef target, GrowthProfile profile) {
    BlockStateRef data = target.state();
    if (isAgeable(data)) {
      return Math.max(1, data.maxAge());
    }

    if (isSapling(data)) {
      return Math.max(1, config.get().saplingGrowthSteps);
    }

    return switch (profile) {
      case SAPLING -> Math.max(1, config.get().saplingGrowthSteps);
      case STEM -> Math.max(1, config.get().stemGrowthSteps);
      case BERRY_BUSH -> Math.max(1, config.get().berryGrowthSteps);
      case VINE -> Math.max(1, config.get().vineGrowthSteps);
      case CAVE_VINE -> Math.max(1, config.get().caveVineGrowthSteps);
      case KELP -> Math.max(1, config.get().kelpGrowthSteps);
      default -> Math.max(1, config.get().defaultGrowthSteps);
    };
  }

  private double getNaturalGrowthSeconds(GrowthProfile profile) {
    return switch (profile) {
      case CROP -> config.get().cropNaturalSeconds;
      case NETHER_WART -> config.get().netherWartNaturalSeconds;
      case SAPLING -> config.get().saplingNaturalSeconds;
      case STEM -> config.get().stemNaturalSeconds;
      case BERRY_BUSH -> config.get().berryBushNaturalSeconds;
      case VINE -> config.get().vineNaturalSeconds;
      case CAVE_VINE -> config.get().caveVineNaturalSeconds;
      case KELP -> config.get().kelpNaturalSeconds;
      default -> config.get().defaultGrowableNaturalSeconds;
    };
  }

  private double getGrowthProfileCostMultiplier(GrowthProfile profile) {
    return switch (profile) {
      case CROP -> config.get().cropCostMultiplier;
      case NETHER_WART -> config.get().netherWartCostMultiplier;
      case SAPLING -> config.get().saplingCostMultiplier;
      case STEM -> config.get().stemCostMultiplier;
      case BERRY_BUSH -> config.get().berryBushCostMultiplier;
      case VINE -> config.get().vineCostMultiplier;
      case CAVE_VINE -> config.get().caveVineCostMultiplier;
      case KELP -> config.get().kelpCostMultiplier;
      default -> config.get().defaultGrowableCostMultiplier;
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

  public void consume(PlayerItemConsumeEvent e) {
    PlayerRef p = e.player();
    if (ChronoTimeBottle.isBindableItem(e.item())) {
      e.setCancelled(true);
      if ((config.get().playClockSounds && owner.preferenceEnabled(p, CLOCK_SOUNDS))) {
        ChronosSoundFX.playClockReject(e.player());
      }
    }
  }

  public void interact(PlayerInteractEvent e) {
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
    if (action == InteractAction.RIGHT_CLICK_AIR && AdaptApi.scheduler().isRegionized()) {
      return;
    }

    BlockRef clicked = action == InteractAction.RIGHT_CLICK_BLOCK ? e.clickedBlock() : p.targetBlock(5);
    if (clicked == null) {
      return;
    }
    int level = owner.getActiveInteractLevel(p, clicked.location());
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
        && !owner.canAccessChest(p, clicked.location())) {
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
    onSpent.accept(p, result);

    Loc burstAt = clicked.location().add(0.5, 1.0, 0.5);
    if ((config.get().playClockSounds && owner.preferenceEnabled(p, CLOCK_SOUNDS))) {
      ChronosSoundFX.playBottleUse(p, burstAt, result.effectTicks());
    }
    ChronosTimeInABottle.blockUseFx(owner, clicked, burstAt);
    if (newStored <= 0) {
      ChronosTimeInABottle.bottleEmptyFx(owner, burstAt);
    }

    owner.xp(p, burstAt, Math.min(config.get().maxXPPerUse, result.xpGain()));
  }

  public void interactEntity(PlayerInteractEntityEvent e) {
    if (e.hand() != Hand.MAIN_HAND) {
      return;
    }

    PlayerRef p = e.player();
    ItemRef hand = p.inventory().mainHand();
    if (!ChronoTimeBottle.isBindableItem(hand)) {
      return;
    }

    EntityRef ageable = e.clicked();
    AnimalOps animals = AdaptApi.platform().host().animals();
    if (!owner.preferenceEnabled(p, ANIMALS)) {
      return;
    }

    int level = owner.getActiveInteractLevel(p, ageable.location());
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
    onSpent.accept(p, result);

    Loc entityBurst = ageable.location().add(0, 1.0, 0);
    if ((config.get().playClockSounds && owner.preferenceEnabled(p, CLOCK_SOUNDS))) {
      ChronosSoundFX.playBottleUse(p, entityBurst, result.effectTicks());
    }
    ChronosTimeInABottle.entityUseFx(owner, entityBurst);
    if (newStored <= 0) {
      ChronosTimeInABottle.bottleEmptyFx(owner, entityBurst);
    }

    owner.xp(p, entityBurst, Math.min(config.get().maxXPPerUse, result.xpGain()));
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
          || (config.get().allowSaplingTreeGeneration && getTreeFeature(clicked.type()) != null);
    }
    return isAgeable(data) && data.age() < data.maxAge();
  }

  private TimeSpendResult accelerateTarget(PlayerRef player, BlockRef clicked, double storedSeconds, int level) {
    Optional<FurnaceRef> furnace = clicked.furnace();
    if (furnace.isPresent()) {
      if (!owner.preferenceEnabled(player, FURNACES)) {
        return TimeSpendResult.none();
      }
      return accelerateFurnace(furnace.get(), storedSeconds, level);
    }

    Optional<BrewingStandRef> brewingStand = clicked.brewingStand();
    if (brewingStand.isPresent()) {
      if (!owner.preferenceEnabled(player, BREWING)) {
        return TimeSpendResult.none();
      }
      return accelerateBrewingStand(brewingStand.get(), storedSeconds, level);
    }

    Optional<CampfireRef> campfire = clicked.campfire();
    if (campfire.isPresent()) {
      if (!owner.preferenceEnabled(player, CAMPFIRES)) {
        return TimeSpendResult.none();
      }
      return accelerateCampfire(player, clicked, campfire.get(), storedSeconds, level);
    }

    return owner.preferenceEnabled(player, GROWTH) ? accelerateGrowables(player, clicked, storedSeconds, level) : TimeSpendResult.none();
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
    return new TimeSpendResult(spentSeconds, advanceTicks, advanceTicks * config.get().xpPerCookTick);
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
    return new TimeSpendResult(spentSeconds, advanceTicks, advanceTicks * config.get().xpPerBrewTick);
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
    return new TimeSpendResult(spentSeconds, usedTicks, usedTicks * config.get().xpPerCampfireTick);
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
    return new TimeSpendResult(spentSeconds, advanceTicks, advanceTicks * config.get().xpPerEntityAgeTick);
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
    return new TimeSpendResult(spentSeconds, effectTicks, successes * config.get().xpPerGrowthStep);
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

      if (!config.get().allowSaplingTreeGeneration || !owner.preferenceEnabled(player, TREES)) {
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
        ChronosTimeInABottle.treeFlourishFx(owner, block.location());
      }
      return generated;
    }

    if (isAgeable(data) && data.age() < data.maxAge()) {
      if (!canMutateBlock(player, block)) {
        return false;
      }
      block.setState(data.with("age", String.valueOf(Math.min(data.maxAge(), data.age() + 1))), true);
      XpProvenance.recordBonemeal(block);
      return true;
    }

    return false;
  }

  private boolean canMutateBlock(PlayerRef player, BlockRef block) {
    return owner.canBlockPlace(player, block.location())
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
      if ((AdaptApi.scheduler().isRegionized()
          && (!AdaptApi.scheduler().owns(player) || !AdaptApi.scheduler().owns(location)))
          || !owner.canBlockPlace(player, location)) {
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
    if (!AdaptApi.scheduler().isRegionized()) {
      return true;
    }
    if (!AdaptApi.scheduler().owns(player)) {
      return false;
    }

    int minimumChunkX = Math.floorDiv(block.x() - TREE_PREFLIGHT_RADIUS, 16);
    int maximumChunkX = Math.floorDiv(block.x() + TREE_PREFLIGHT_RADIUS, 16);
    int minimumChunkZ = Math.floorDiv(block.z() - TREE_PREFLIGHT_RADIUS, 16);
    int maximumChunkZ = Math.floorDiv(block.z() + TREE_PREFLIGHT_RADIUS, 16);
    for (int chunkX = minimumChunkX; chunkX <= maximumChunkX; chunkX++) {
      for (int chunkZ = minimumChunkZ; chunkZ <= maximumChunkZ; chunkZ++) {
        Loc anchor = new Loc(block.world(), chunkX << 4, block.y(), chunkZ << 4);
        if (!AdaptApi.scheduler().owns(anchor)) {
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

  public void tick() {
    long now = System.currentTimeMillis();
    List<PlayerProfile> candidates = owner.learnedCandidates(now);
    ChronosWorkBudget.Batch batch = ChronosWorkBudget.batch(
        candidates.size(), playerCursor, config.get().maxPlayersPerPass, HARD_MAX_PLAYERS_PER_PASS);
    for (int i = 0; i < batch.count(); i++) {
      PlayerProfile adaptPlayer = candidates.get((batch.start() + i) % candidates.size());
      PlayerRef player = adaptPlayer.player();
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
    boolean scheduled = AdaptApi.scheduler().runEntity(player, () -> {
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
    int level = owner.getActiveLevel(player);
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

    double charge = getChargePerSecond(level) * pulse.count();
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
      ChronosTimeInABottle.bottleFullFx(owner, player);
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

  /** What one use of a bottle spent and achieved. */
  public record TimeSpendResult(double spentSeconds, int effectTicks, double xpGain) {
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
}
