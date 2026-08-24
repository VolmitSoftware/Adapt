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

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.plugin.ProtectionEventProbe;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.common.world.LoadedChunkAccess;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BrewingStand;
import org.bukkit.block.Furnace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class ChronosAccelerate extends SimpleAdaptation<ChronosAccelerate.Config> {
  private static final Color AURA_COLOR = Color.fromRGB(230, 210, 150);
  private static final int HARD_MAX_PLAYERS_PER_PASS = 512;
  private static final int HARD_MAX_SAMPLES_PER_PASS = 8192;
  private static final int MAX_CATCH_UP_PULSES = 4;

  private final Map<UUID, Long> nextPulseAt = playerState();
  private int playerCursor;

  public ChronosAccelerate() {
    super("chronos-accelerate");
    registerConfiguration(Config.class);
    setIcon(Material.SUGAR);
    setInterval(getConfig().pulseIntervalMillis);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.SUGAR)
        .key("challenge_chronos_accelerate_1k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_chronos_accelerate_1k", "chronos.accelerate.blocks-accelerated", 1000, 600);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(getRadius(level)), 1);
    statLore(v, C.YELLOW, "+ ", Math.round(getGrowChance(level) * 100D) + "%", 2);
    statLore(v, C.GRAY, "* ", Math.round(getCookBoostFraction(level) * 100D) + "%", 3);
  }

  @EventHandler
  public void on(PlayerQuitEvent e) {
    nextPulseAt.remove(e.getPlayer().getUniqueId());
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
      Player player = adaptPlayer.getPlayer();
      if (player != null) {
        J.runEntity(player, () -> prepareAcceleration(player, now, sampleBudget));
      }
    }
    playerCursor = batch.nextCursor();
  }

  private void prepareAcceleration(Player player, long now, AtomicInteger sampleBudget) {
    if (!player.isOnline()) {
      return;
    }

    UUID playerId = player.getUniqueId();
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

    Location center = player.getLocation().clone();
    World world = center.getWorld();
    if (world == null) {
      return;
    }

    AccelerationBatch acceleration = new AccelerationBatch(player, center, level, granted);
    ThreadLocalRandom random = ThreadLocalRandom.current();
    int radius = Math.max(1, (int) Math.round(getRadius(level)));
    for (int i = 0; i < granted; i++) {
      int x = center.getBlockX() + random.nextInt(-radius, radius + 1);
      int y = center.getBlockY() + random.nextInt(-2, 3);
      int z = center.getBlockZ() + random.nextInt(-radius, radius + 1);
      Location target = new Location(world, x, y, z);
      if (!J.runAt(target, () -> acceleration.complete(accelerateBlock(player, target, level)))) {
        acceleration.complete(false);
      }
    }
  }

  private boolean accelerateBlock(Player player, Location target, int level) {
    World world = target.getWorld();
    if (world == null) {
      return false;
    }

    LoadedChunkAccess loaded = new LoadedChunkAccess(world, 0);
    if (!loaded.canRead(target.getBlockX(), target.getBlockZ())) {
      return false;
    }

    Block block = world.getBlockAt(target);
    Material type = block.getType();
    if (type.isAir()) {
      return false;
    }

    if (type == Material.FURNACE || type == Material.BLAST_FURNACE || type == Material.SMOKER) {
      if (block.getState() instanceof Furnace furnace && accelerateFurnace(player, block, furnace, level)) {
        emitStationFx(world, target.getBlockX(), target.getBlockY(), target.getBlockZ(), true);
        return true;
      }
      return false;
    }

    if (type == Material.BREWING_STAND) {
      if (block.getState() instanceof BrewingStand stand && accelerateBrewingStand(player, block, stand, level)) {
        emitStationFx(world, target.getBlockX(), target.getBlockY(), target.getBlockZ(), false);
        return true;
      }
      return false;
    }

    BlockData data = block.getBlockData();
    if (!(data instanceof Ageable ageable)
        || ageable.getAge() >= ageable.getMaximumAge()
        || ThreadLocalRandom.current().nextDouble() >= getGrowChance(level)) {
      return false;
    }
    if (!canAccelerateTarget(player, block, false)
        || !canBlockPlace(player, block.getLocation())
        || !ProtectionEventProbe.attemptBlockPlaceProbe(player, block)) {
      return false;
    }

    ageable.setAge(ageable.getAge() + 1);
    block.setBlockData(data, true);
    emitCropFx(world, target.getBlockX(), target.getBlockY(), target.getBlockZ());
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

  private boolean accelerateFurnace(Player player, Block block, Furnace furnace, int level) {
    if (furnace.getBurnTime() <= 0) {
      return false;
    }

    ItemStack smelting = furnace.getInventory().getSmelting();
    if (smelting == null || smelting.getType().isAir()) {
      return false;
    }

    int total = furnace.getCookTimeTotal();
    int current = furnace.getCookTime();
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
    furnace.update(true, true);
    return true;
  }

  private boolean accelerateBrewingStand(Player player, Block block, BrewingStand stand, int level) {
    int brewingTime = stand.getBrewingTime();
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
    stand.update(true, true);
    return true;
  }

  private boolean canAccelerateTarget(Player player, Block block, boolean container) {
    if (!J.isOwnedByCurrentRegion(player)
        || !J.isOwnedByCurrentRegion(block.getLocation())
        || !player.isOnline()
        || !canInteract(player, block.getLocation())
        || (container && !canAccessChest(player, block.getLocation()))) {
      return false;
    }
    try {
      return ProtectionEventProbe.attemptBlockUse(player, block);
    } catch (Throwable error) {
      Adapt.error("Chronos Accelerate could not verify target access for " + player.getUniqueId() + ".");
      Adapt.error(error);
      return false;
    }
  }

  private void emitCropFx(World world, int x, int y, int z) {
    fx(new Location(world, x + 0.5, y + 0.9, z + 0.5), FxPriority.AMBIENT)
        .particle(Particles.VILLAGER_HAPPY, 3, 0, 0.1D, 0, 0.2D, 0.01D)
        .particle(Particle.COMPOSTER, 1, 0, 0.2D, 0, 0.15D, 0);
  }

  private void emitStationFx(World world, int x, int y, int z, boolean furnace) {
    fx(new Location(world, x + 0.5, y + 0.6, z + 0.5), FxPriority.AMBIENT)
        .particle(Particles.ENCHANTMENT_TABLE, 2, 0, 0.3D, 0, 0.25D, 0.02D)
        .particle(furnace ? Particles.SMOKE : Particle.SPLASH, 1, 0, 0.3D, 0, 0.1D, 0.01D)
        .sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.3F, 1.6F);
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
    private final Player player;
    private final Location center;
    private final int level;
    private final AtomicInteger remaining;
    private final AtomicInteger accelerated;
    private final AtomicBoolean finalized;

    private AccelerationBatch(Player player, Location center, int level, int samples) {
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
      J.runEntity(player, this::finish);
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
