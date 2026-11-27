package art.arcane.adapt.content.adaptation.seaborrne.upgraded;

import art.arcane.adapt.api.world.BuildPayment;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.Cooldowns;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.item.ItemWear;
import art.arcane.adapt.api.mutation.MutationCue;
import art.arcane.adapt.api.mutation.SkillUpgrade;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.content.adaptation.seaborrne.SeaborneCatches;
import art.arcane.adapt.content.adaptation.seaborrne.SeaborneDeepSalvager;
import art.arcane.adapt.content.adaptation.seaborrne.SeaborneSeaLife;
import art.arcane.adapt.content.adaptation.seaborrne.SeaborneShimmer;

import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataHolder;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.EquipmentSlot;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.player.PlayerFishEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Box;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.world.BlockStateRef;
import art.arcane.adapt.platform.world.GeneratedStructure;
import art.arcane.adapt.platform.world.WorldRef;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Fisher's Fantasy, Fish Whisperer and Deep Salvager fused: luckier fishing with bonus catches, dolphins and axolotls
 * that join your fights, and chests and barrels around you glowing while you swim. Reeling in over a shipwreck or an
 * ocean ruin salvages one roll of Deep Salvager's treasure, once per structure and player, stamped on the chunk at the
 * structure's centre.
 */
public class DeepFortune extends SkillUpgrade<DeepFortune.Config> {
  public static final Id ID = Id.adapt("seaborne/upgrade/deep_fortune");

  private static final Rgb GLOW = SeaborneShimmer.color(SeaborneDeepSalvager.OutlineColor.AQUA);
  private static final Id FISHING_ROD = Id.minecraft("fishing_rod");
  private static final Id WATER = Id.minecraft("water");
  private static final Set<Id> WRECKS = Set.of(Id.minecraft("shipwreck"), Id.minecraft("ocean_ruin"));
  private static final DataKey<List<String>> SALVAGED_KEY = DataKey.adapt("seaborne_salvage_cast", DataCodecs.STRING_LIST);

  private final Cooldowns catchCooldowns = cooldowns();
  private final Cooldowns scanCooldowns = cooldowns();
  private final SeaborneShimmer shimmer = new SeaborneShimmer(this,
      (PlayerRef player) -> submerged(player) && isPlayerEnabled(player), (PlayerRef player) -> GLOW,
      (PlayerRef player) -> fx(player, FxPriority.AMBIENT).particle(Particles.GLOW, 6, 0D, 1.0D, 0D, 0.6D, 0D));

  public DeepFortune() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("fishing_rod"));
    setInterval(3000);
  }

  @Override
  public void onPlayerPreferencesChanged(PlayerProfile player) {
    if (getActiveLevel(player.player()) <= 0) {
      shimmer.forget(player.player().uuid());
      SeaborneSeaLife.removeLuck(this, player.player());
    }
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Format.pc(getCatchChance(level), 0), 1);
    statLore(v, getLuck(level), 2);
    statLore(v, Format.f(getGlowRange(level), 0), 3);
    statLore(v, getSalvageLuck(level), 4);
  }

  @Override
  protected boolean usesLearnerBoundTicking() {
    return true;
  }

  @Override
  public void unregister() {
    shimmer.clear();
    super.unregister();
  }

  @Override
  public void onTick() {
    for (PlayerProfile adaptPlayer : learnedCandidates(System.currentTimeMillis())) {
      PlayerRef player = adaptPlayer.player();
      withPlayerThread(player, () -> {
        int level = getActiveLevel(player);
        if (!player.isOnline() || level <= 0) {
          return;
        }
        SeaborneSeaLife.applyLuck(this, player, getLuck(level));
        if (submerged(player) && !shimmer.scanning(player.uuid())
            && scanCooldowns.isReady(player.uuid(), getConfig().glowScanMillis)) {
          scanCooldowns.mark(player.uuid());
          shimmer.scan(player, (int) Math.round(getGlowRange(level)), SeaborneDeepSalvager.ContainerTypes.ALL,
              (int) Math.max(20L, Math.min(200L, (getConfig().glowScanMillis / 50L) + 20L)));
        }
      });
    }
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerFishEvent e) {
    PlayerRef player = e.player();
    if (e.state() == PlayerFishEvent.State.CAUGHT_FISH || e.state() == PlayerFishEvent.State.REEL_IN) {
      Loc hook = e.hook().location();
      withAdaptedPlayer(player, e, () -> salvage(player, hook));
    }
    if (e.state() != PlayerFishEvent.State.CAUGHT_FISH) {
      return;
    }
    withAdaptedPlayer(player, e, () -> {
      int level = getActiveLevel(player);
      long cooldown = getConfig().catchCooldownMillis;
      if (level <= 0 || !catchCooldowns.isReady(player.uuid(), cooldown)
          || ThreadLocalRandom.current().nextDouble() >= getCatchChance(level)) {
        return;
      }

      catchCooldowns.mark(player.uuid(), cooldown);
      SeaborneCatches.award(player, SeaborneCatches.rewardXp(level, getConfig().vanillaXpAtLevelOne,
          getConfig().vanillaXpPerLevel, getConfig().maximumVanillaXp));
      addStat(player, "seaborne.deep-fortune.bonus-catches", 1);
      fx(player.location().add(0D, 0.5D, 0D), FxPriority.TRANSITION).particle(Particles.SPLASH, 8, 0D, 0.3D, 0D, 0.3D, 0.05D);
      cue(player, MutationCue.WORK);
    });
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityDamageByEntityEvent e) {
    if (!(e.damager() instanceof PlayerRef player) || !(e.entity() instanceof MobRef victim)) {
      return;
    }
    int level = getActiveLevel(player);
    if (level > 0) {
      SeaborneSeaLife.rally(this, victim, getAssistRange(level), getConfig().dolphinChargeStrength);
    }
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(PlayerQuitEvent e) {
    scanCooldowns.clear(e.player().uuid());
    shimmer.forget(e.player().uuid());
  }

  private void salvage(PlayerRef player, Loc hook) {
    int level = getActiveLevel(player);
    WorldRef world = hook.world();
    GeneratedStructure wreck = level <= 0 || world == null ? null : wreckAt(world, hook);
    if (wreck == null) {
      return;
    }
    Box bounds = wreck.bounds();
    DataHolder stamps = world.chunkData(((int) Math.floor((bounds.minX() + bounds.maxX()) / 2D)) >> 4,
        ((int) Math.floor((bounds.minZ() + bounds.maxZ()) / 2D)) >> 4);
    String stamp = player.uuid() + "@" + (int) Math.floor(bounds.minX()) + "," + (int) Math.floor(bounds.minY()) + ","
        + (int) Math.floor(bounds.minZ());
    List<String> salvaged = stamps == null ? null : stamps.get(SALVAGED_KEY);
    if (stamps == null || salvaged != null && salvaged.contains(stamp)) {
      return;
    }
    EquipmentSlot slot = rodSlot(player);
    int cost = getConfig().salvageRodDurability;
    if (slot == null || !payDurabilityCost(player, "durability", cost, () -> {
      ItemWear.damage(player, slot, cost);
      return true;
    })) {
      return;
    }

    List<String> stamped = salvaged == null ? new ArrayList<>() : new ArrayList<>(salvaged);
    stamped.add(stamp);
    stamps.set(SALVAGED_KEY, stamped);
    BuildPayment.give(player, List.of(SeaborneDeepSalvager.rollTreasure(ThreadLocalRandom.current(), getSalvageLuck(level))));
    addStat(player, "seaborne.deep-fortune.salvages", 1);
    xpOnDiscovery(player, getConfig().salvageXp);
    fx(hook, FxPriority.GAMEPLAY).ring(Particles.GLOW, 0.6D, 12, 0.3D).particle(Particles.BUBBLE, 10, 0D, 0.3D, 0D, 0.4D, 0.05D);
    cue(player, MutationCue.WORK);
  }

  /** The wreck whose bounds hold a hook resting in water, or null. */
  private static GeneratedStructure wreckAt(WorldRef world, Loc hook) {
    BlockStateRef at = world.blockAt(hook).state();
    if (!WATER.equals(at.type()) && !"true".equals(at.property("waterlogged"))) {
      return null;
    }
    for (GeneratedStructure structure : world.structuresAt(hook.chunkX(), hook.chunkZ())) {
      Box box = structure.bounds();
      if (WRECKS.contains(structure.type()) && hook.x() >= box.minX() && hook.x() < box.maxX()
          && hook.z() >= box.minZ() && hook.z() < box.maxZ() && hook.y() >= box.minY() && hook.y() <= box.maxY()) {
        return structure;
      }
    }
    return null;
  }

  /** The hand holding a fishing rod that can lose the salvage durability without breaking, or null. */
  private EquipmentSlot rodSlot(PlayerRef player) {
    if (canWear(player.inventory().mainHand())) {
      return EquipmentSlot.MAIN_HAND;
    }
    return canWear(player.inventory().offHand()) ? EquipmentSlot.OFF_HAND : null;
  }

  private boolean canWear(ItemRef rod) {
    return rod != null && FISHING_ROD.equals(rod.type()) && !rod.isUnbreakable()
        && rod.maxDamage() - rod.damage() > getConfig().salvageRodDurability;
  }

  private static boolean submerged(PlayerRef player) {
    return player.isInWater() || player.isSwimming();
  }

  double getCatchChance(int level) {
    return SeaborneCatches.bonusChance(level, getMaxLevel(), getConfig().catchChanceAtLevelOne, getConfig().catchChanceAtMaxLevel);
  }

  int getLuck(int level) {
    return getConfig().luckBase + level;
  }

  double getGlowRange(int level) {
    return Math.min(SeaborneShimmer.HARD_MAX_RANGE, getConfig().glowRangeBase + (level * getConfig().glowRangePerLevel));
  }

  int getSalvageLuck(int level) {
    return Math.max(0, level * getConfig().salvageLuckPerLevel);
  }

  double getAssistRange(int level) {
    return getConfig().assistRangeBase + (level * getConfig().assistRangePerLevel);
  }

  @ConfigDescription("Luckier fishing with bonus catches, sea life that joins your fights, and glowing sunken chests.")
  public static class Config extends SkillUpgrade.Config {
    @ConfigDoc(value = "Chance of a bonus catch at level 1.", impact = "Higher values make bonus catches more common early.")
    double catchChanceAtLevelOne = 0.3;
    @ConfigDoc(value = "Chance of a bonus catch at the top level.", impact = "Higher values make bonus catches more common at the top level.")
    double catchChanceAtMaxLevel = 0.6;
    @ConfigDoc(value = "Shortest time between two bonus catches, in milliseconds.", impact = "Lower values allow bonus catches in quick succession.")
    long catchCooldownMillis = 3000;
    @ConfigDoc(value = "Vanilla XP dropped with a bonus catch at level 1.", impact = "Higher values make bonus catches feed enchanting more.")
    int vanillaXpAtLevelOne = 4;
    @ConfigDoc(value = "Vanilla XP added per level after the first.", impact = "Higher values reward higher levels more.")
    int vanillaXpPerLevel = 2;
    @ConfigDoc(value = "Most vanilla XP a single bonus catch drops.", impact = "Caps the XP at any level.")
    int maximumVanillaXp = 16;
    @ConfigDoc(value = "Luck before level scaling; each level adds one.", impact = "Higher values improve every catch and loot roll.")
    int luckBase = 1;
    @ConfigDoc(value = "Range in blocks within which dolphins and axolotls join a fight.", impact = "Higher values rally sea life from farther away.")
    double assistRangeBase = 10;
    @ConfigDoc(value = "Assist range added per level, in blocks.", impact = "Higher values widen the gap between levels.")
    double assistRangePerLevel = 3;
    @ConfigDoc(value = "Push given to a dolphin charging your target.", impact = "Higher values make dolphins ram harder.")
    double dolphinChargeStrength = 1.0;
    @ConfigDoc(value = "Radius in which chests and barrels glow, before level scaling.", impact = "Higher values reveal containers from farther away; capped at 9.")
    double glowRangeBase = 5;
    @ConfigDoc(value = "Glow radius added per level, in blocks.", impact = "Higher values widen the gap between levels.")
    double glowRangePerLevel = 1.5;
    @ConfigDoc(value = "Time between container glow scans, in milliseconds.", impact = "Lower values refresh the glow sooner but scan more often.")
    long glowScanMillis = 3000;
    @ConfigDoc(value = "Extra draws at the heart of the sea per level on a salvage cast.", impact = "Higher values make salvaged hearts more likely.")
    int salvageLuckPerLevel = 1;
    @ConfigDoc(value = "Rod durability each salvage cast costs.", impact = "Higher values wear the rod faster.")
    int salvageRodDurability = 10;
    @ConfigDoc(value = "Skill XP for the first salvage of each structure.", impact = "Higher values reward finding wrecks and ruins.")
    double salvageXp = 20;
  }
}
