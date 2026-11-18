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
import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
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
import art.arcane.adapt.localization.catalog.ChronosMessages;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;
import art.arcane.adapt.platform.world.BrewingStandRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.world.FurnaceRef;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;


import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class ChronosAccelerate extends SimpleAdaptation<ChronosAccelerate.Config> {
  public static final PlayerPreference<CommonPreferences.Toggle> CROPS = CommonPreferences.toggle("crops", ChronosMessages.PREFERENCE_CHRONOSACCELERATE_CROPS, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> FURNACES = CommonPreferences.toggle("furnaces", ChronosMessages.PREFERENCE_CHRONOSACCELERATE_FURNACES, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> BREWING = CommonPreferences.toggle("brewing", ChronosMessages.PREFERENCE_CHRONOSACCELERATE_BREWING, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Scale> RADIUS = CommonPreferences.scale("radius", ChronosMessages.PREFERENCE_CHRONOSACCELERATE_RADIUS);

  private static final Rgb AURA_COLOR = new Rgb(230, 210, 150);
  private static final Set<Id> FURNACE_TYPES = Set.of(Id.minecraft("furnace"), Id.minecraft("blast_furnace"), Id.minecraft("smoker"));
  private static final Id BREWING_STAND = Id.minecraft("brewing_stand");
  private static final int HARD_MAX_PLAYERS_PER_PASS = 512;
  private static final int HARD_MAX_SAMPLES_PER_PASS = 8192;
  private static final int MAX_CATCH_UP_PULSES = 4;

  private final Map<UUID, Long> nextPulseAt = playerState();
  private int playerCursor;

  public ChronosAccelerate() {
    super("chronos-accelerate");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("sugar"));
    setInterval(getConfig().pulseIntervalMillis);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("sugar"))
        .key("challenge_chronos_accelerate_1k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_chronos_accelerate_1k", "chronos.accelerate.blocks-accelerated", 1000, 600);
  }


  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, CROPS, FURNACES, BREWING, RADIUS);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.f(getRadius(level)), 1);
    statLore(v, C.YELLOW, "+ ", Math.round(getGrowChance(level) * 100D) + "%", 2);
    statLore(v, C.GRAY, "* ", Math.round(getCookBoostFraction(level) * 100D) + "%", 3);
  }

  @Handle
  public void on(PlayerQuitEvent e) {
    nextPulseAt.remove(e.player().uuid());
  }

  private double getRadius(int level) {
    return getConfig().baseRadius + (Math.max(1, level) * getConfig().radiusPerLevel);
  }

  private double getGrowChance(int level) {
    return Math.max(0D, Math.min(1D, getConfig().baseGrowChance + (Math.max(1, level) * getConfig().growChancePerLevel)));
  }

  private int getSamples(int level) {
    return Math.min(getConfig().maxSamplesPerPulse, getConfig().baseSamplesPerPulse + level);
  }

  private double getCookBoostFraction(int level) {
    return Math.min(getConfig().maxCookBoostFraction,
        getConfig().baseCookBoostFraction + (Math.max(1, level) * getConfig().cookBoostFractionPerLevel));
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
    AtomicInteger sampleBudget = new AtomicInteger(
        ChronosWorkBudget.bounded(getConfig().maxSamplesPerPass, 1, HARD_MAX_SAMPLES_PER_PASS));
    for (int i = 0; i < batch.count(); i++) {
      AdaptPlayer adaptPlayer = candidates.get((batch.start() + i) % candidates.size());
      PlayerRef player = adaptPlayer.getPlayer();
      if (player != null) {
        Tasks.runEntity(player, () -> prepareAcceleration(player, now, sampleBudget));
      }
    }
    playerCursor = batch.nextCursor();
  }

  private void prepareAcceleration(PlayerRef player, long now, AtomicInteger sampleBudget) {
    if (!player.isOnline()) {
      return;
    }

    UUID playerId = player.uuid();
    int level = getActiveLevel(player);
    if (level <= 0) {
      nextPulseAt.remove(playerId);
      return;
    }

    long interval = Math.max(1L, getConfig().pulseIntervalMillis);
    ChronosWorkBudget.Pulse pulse = ChronosWorkBudget.pulse(
        now, nextPulseAt.get(playerId), interval, MAX_CATCH_UP_PULSES);
    nextPulseAt.put(playerId, pulse.nextAt());
    if (pulse.count() <= 0) {
      return;
    }

    int requested = Math.max(1, getSamples(level)) * pulse.count();
    int granted = claimSamples(sampleBudget, requested);
    if (granted <= 0) {
      return;
    }

    Loc center = player.location();
    WorldRef world = center.world();
    if (world == null) {
      return;
    }

    AccelerationBatch acceleration = new AccelerationBatch(player, center, level, granted);
    ThreadLocalRandom random = ThreadLocalRandom.current();
    int radius = Math.max(1, (int) Math.round(getRadius(level) * preference(player, RADIUS).multiplier()));
    for (int i = 0; i < granted; i++) {
      int x = center.blockX() + random.nextInt(-radius, radius + 1);
      int y = center.blockY() + random.nextInt(-2, 3);
      int z = center.blockZ() + random.nextInt(-radius, radius + 1);
      Loc target = new Loc(world, x, y, z);
      if (!Tasks.runAt(target, () -> acceleration.complete(accelerateBlock(player, target, level)))) {
        acceleration.complete(false);
      }
    }
  }

  private boolean accelerateBlock(PlayerRef player, Loc target, int level) {
    WorldRef world = target.world();
    if (world == null) {
      return false;
    }

    int chunkX = target.blockX() >> 4;
    int chunkZ = target.blockZ() >> 4;
    if (!Tasks.isOwnedByCurrentRegion(world, chunkX, chunkZ) || !world.isChunkLoaded(chunkX, chunkZ)) {
      return false;
    }

    BlockRef block = world.blockAt(target);
    Id type = block.type();
    if (block.isAir()) {
      return false;
    }

    if (FURNACE_TYPES.contains(type)) {
      if (preferenceEnabled(player, FURNACES)) {
        FurnaceRef furnace = block.furnace().orElse(null);
        if (furnace != null && accelerateFurnace(player, block, furnace, level)) {
          emitStationFx(world, target.blockX(), target.blockY(), target.blockZ(), true);
          return true;
        }
      }
      return false;
    }

    if (BREWING_STAND.equals(type)) {
      if (preferenceEnabled(player, BREWING)) {
        BrewingStandRef stand = block.brewingStand().orElse(null);
        if (stand != null && accelerateBrewingStand(player, block, stand, level)) {
          emitStationFx(world, target.blockX(), target.blockY(), target.blockZ(), false);
          return true;
        }
      }
      return false;
    }

    BlockStateRef data = block.state();
    int age = data.age();
    if (!preferenceEnabled(player, CROPS) || AdaptConfig.get().isGrowthBlacklisted(type)
        || age < 0
        || age >= data.maxAge()
        || ThreadLocalRandom.current().nextDouble() >= getGrowChance(level)) {
      return false;
    }
    if (!canAccelerateTarget(player, block, false)
        || !canBlockPlace(player, block.location())
        || !ProtectionProbes.attemptBlockPlaceProbe(player, block)) {
      return false;
    }

    block.setState(data.with("age", String.valueOf(age + 1)), true);
    emitCropFx(world, target.blockX(), target.blockY(), target.blockZ());
    return true;
  }

  private int claimSamples(AtomicInteger remaining, int requested) {
    int observed = remaining.get();
    while (observed > 0) {
      int claimed = Math.min(observed, requested);
      if (remaining.compareAndSet(observed, observed - claimed)) {
        return claimed;
      }
      observed = remaining.get();
    }
    return 0;
  }

  private boolean accelerateFurnace(PlayerRef player, BlockRef block, FurnaceRef furnace, int level) {
    if (furnace.burnTime() <= 0) {
      return false;
    }

    ItemRef smelting = furnace.smelting();
    if (smelting.isEmpty() || smelting.type().isAir()) {
      return false;
    }

    int total = furnace.cookTimeTotal();
    int current = furnace.cookTime();
    if (total <= 0 || current >= total - 1) {
      return false;
    }

    int bonus = (int) Math.round(total * getCookBoostFraction(level));
    if (bonus <= 0) {
      return false;
    }
    if (!canAccelerateTarget(player, block, true)) {
      return false;
    }

    furnace.setCookTime((short) Math.min(total - 1, current + bonus));
    return true;
  }

  private boolean accelerateBrewingStand(PlayerRef player, BlockRef block, BrewingStandRef stand, int level) {
    int brewingTime = stand.brewingTime();
    if (brewingTime <= 1) {
      return false;
    }

    int bonus = (int) Math.round(400D * getCookBoostFraction(level));
    if (bonus <= 0) {
      return false;
    }
    if (!canAccelerateTarget(player, block, true)) {
      return false;
    }

    stand.setBrewingTime(Math.max(1, brewingTime - bonus));
    return true;
  }

  private boolean canAccelerateTarget(PlayerRef player, BlockRef block, boolean container) {
    if (!Tasks.isOwnedByCurrentRegion(player)
        || !Tasks.isOwnedByCurrentRegion(block.location())
        || !player.isOnline()
        || !canInteract(player, block.location())
        || (container && !canAccessChest(player, block.location()))) {
      return false;
    }
    try {
      return ProtectionProbes.attemptBlockUse(player, block);
    } catch (Throwable error) {
      AdaptLog.error("Chronos Accelerate could not verify target access for " + player.uuid() + ".");
      AdaptLog.error(error);
      return false;
    }
  }

  private void emitCropFx(WorldRef world, int x, int y, int z) {
    fx(new Loc(world, x + 0.5, y + 0.9, z + 0.5), FxPriority.AMBIENT)
        .particle(Particles.VILLAGER_HAPPY, 3, 0, 0.1D, 0, 0.2D, 0.01D)
        .particle(Particles.COMPOSTER, 1, 0, 0.2D, 0, 0.15D, 0);
  }

  private void emitStationFx(WorldRef world, int x, int y, int z, boolean furnace) {
    fx(new Loc(world, x + 0.5, y + 0.6, z + 0.5), FxPriority.AMBIENT)
        .particle(Particles.ENCHANTMENT_TABLE, 2, 0, 0.3D, 0, 0.25D, 0.02D)
        .particle(furnace ? Particles.SMOKE : Particles.SPLASH, 1, 0, 0.3D, 0, 0.1D, 0.01D)
        .sound(Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.3F, 1.6F);
  }

  @Override
  protected void onConfigReload(Config previousConfig, Config newConfig) {
    super.onConfigReload(previousConfig, newConfig);
    setInterval(Math.max(1L, newConfig.pulseIntervalMillis));
  }

  @ConfigDescription("Passively accelerate time around you, occasionally growing nearby crops and speeding furnaces and brewing stands.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Milliseconds between acceleration pulses.", impact = "Lower values pulse more often at more server cost.")
    long pulseIntervalMillis = 3000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base aura radius in blocks.", impact = "Higher values sample blocks from a wider area.")
    double baseRadius = 4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Extra aura radius per adaptation level.", impact = "Higher values make leveling widen the aura faster.")
    double radiusPerLevel = 0.8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base number of random blocks sampled per pulse.", impact = "Higher values accelerate more blocks at more server cost.")
    int baseSamplesPerPulse = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Hard cap on blocks sampled per pulse.", impact = "Higher values raise the per pulse work ceiling.")
    int maxSamplesPerPulse = 8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum learned players prepared in one acceleration pass.", impact = "Higher values refresh more players immediately; lower values spread owner-thread scheduling across passes.")
    int maxPlayersPerPass = 512;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum block-region jobs admitted by one acceleration pass.", impact = "Higher values preserve more simultaneous aura pulses; lower values shed work sooner under extreme populations.")
    int maxSamplesPerPass = 8192;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base chance for a sampled crop to advance one growth stage.", impact = "Higher values grow crops faster.")
    double baseGrowChance = 0.3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Extra crop growth chance per adaptation level.", impact = "Higher values make leveling grow crops faster.")
    double growChancePerLevel = 0.06;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base fraction of total cook or brew time fast-forwarded per pulse hit.", impact = "Higher values complete cooks and brews in fewer pulses.")
    double baseCookBoostFraction = 0.25;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Extra cook fast-forward fraction per adaptation level.", impact = "Higher values make leveling speed cooking faster.")
    double cookBoostFractionPerLevel = 0.07;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Hard cap on the cook fast-forward fraction per pulse hit.", impact = "Higher values allow nearly instant cooks at high levels.")
    double maxCookBoostFraction = 0.6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "XP granted per accelerated block.", impact = "Higher values grant more skill XP from the aura.")
    double xpPerAcceleratedBlock = 1.2;

    public Config() {
      baseCost = 5;
      costFactor = 0.4;
      initialCost = 4;
    }
  }

  private final class AccelerationBatch {
    private final PlayerRef player;
    private final Loc center;
    private final int level;
    private final AtomicInteger remaining;
    private final AtomicInteger accelerated;
    private final AtomicBoolean finalized;

    private AccelerationBatch(PlayerRef player, Loc center, int level, int samples) {
      this.player = player;
      this.center = center;
      this.level = level;
      remaining = new AtomicInteger(samples);
      accelerated = new AtomicInteger();
      finalized = new AtomicBoolean(false);
    }

    private void complete(boolean changed) {
      if (changed) {
        accelerated.incrementAndGet();
      }
      if (remaining.decrementAndGet() != 0 || !finalized.compareAndSet(false, true)) {
        return;
      }
      Tasks.runEntity(player, this::finish);
    }

    private void finish() {
      int count = accelerated.get();
      if (!player.isOnline() || count <= 0) {
        return;
      }
      addStat(player, "chronos.accelerate.blocks-accelerated", count);
      xpSilent(player, count * getConfig().xpPerAcceleratedBlock, "chronos:accelerate");
      fx(center, FxPriority.AMBIENT).dustRing(AURA_COLOR, getRadius(level), 8, 0.9F);
    }
  }
}
