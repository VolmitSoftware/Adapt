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

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.scheduling.J;
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
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.ThreadLocalRandom;

public class ChronosAccelerate extends SimpleAdaptation<ChronosAccelerate.Config> {
  private static final Color AURA_COLOR = Color.fromRGB(230, 210, 150);

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
    v.addLore(C.GREEN + "+ " + Form.f(getRadius(level)) + " " + Localizer.dLocalize("chronos.accelerate.lore1"));
    v.addLore(C.YELLOW + "+ " + Math.round(getGrowChance(level) * 100D) + "% " + Localizer.dLocalize("chronos.accelerate.lore2"));
    v.addLore(C.GRAY + "* " + Math.round(getCookBoostFraction(level) * 100D) + "% " + Localizer.dLocalize("chronos.accelerate.lore3"));
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
  public void onTick() {
    long now = System.currentTimeMillis();
    for (AdaptPlayer adaptPlayer : learnedCandidates(now)) {
      Player p = adaptPlayer.getPlayer();
      if (p == null || !p.isOnline()) {
        continue;
      }

      int level = getActiveLevel(p);
      if (level <= 0) {
        continue;
      }

      Location center = p.getLocation().clone();
      Runnable task = () -> accelerateAround(p, center, level);
      if (J.isFoliaThreading()) {
        J.runAt(center, task);
      } else {
        task.run();
      }
    }
  }

  private void accelerateAround(Player p, Location center, int level) {
    World world = center.getWorld();
    if (world == null) {
      return;
    }

    ThreadLocalRandom random = ThreadLocalRandom.current();
    int radius = Math.max(1, (int) Math.round(getRadius(level)));
    int samples = getSamples(level);
    double growChance = getGrowChance(level);
    int accelerated = 0;

    for (int i = 0; i < samples; i++) {
      int x = center.getBlockX() + random.nextInt(-radius, radius + 1);
      int y = center.getBlockY() + random.nextInt(-2, 3);
      int z = center.getBlockZ() + random.nextInt(-radius, radius + 1);
      Block block = world.getBlockAt(x, y, z);
      Material type = block.getType();
      if (type.isAir()) {
        continue;
      }

      if (type == Material.FURNACE || type == Material.BLAST_FURNACE || type == Material.SMOKER) {
        if (canInteract(p, block.getLocation()) && block.getState() instanceof Furnace furnace && accelerateFurnace(furnace, level)) {
          accelerated++;
          emitStationFx(world, x, y, z, true);
        }
        continue;
      }

      if (type == Material.BREWING_STAND) {
        if (canInteract(p, block.getLocation()) && block.getState() instanceof BrewingStand stand && accelerateBrewingStand(stand, level)) {
          accelerated++;
          emitStationFx(world, x, y, z, false);
        }
        continue;
      }

      BlockData data = block.getBlockData();
      if (data instanceof Ageable ageable
          && ageable.getAge() < ageable.getMaximumAge()
          && random.nextDouble() < growChance
          && canInteract(p, block.getLocation())) {
        ageable.setAge(ageable.getAge() + 1);
        block.setBlockData(data, true);
        accelerated++;
        emitCropFx(world, x, y, z);
      }
    }

    if (accelerated > 0) {
      addStat(p, "chronos.accelerate.blocks-accelerated", accelerated);
      xpSilent(p, accelerated * getConfig().xpPerAcceleratedBlock, "chronos:accelerate");
      fx(center, FxPriority.AMBIENT).dustRing(AURA_COLOR, getRadius(level), 8, 0.9F);
    }
  }

  private boolean accelerateFurnace(Furnace furnace, int level) {
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

    furnace.setCookTime((short) Math.min(total - 1, current + bonus));
    furnace.update(true, true);
    return true;
  }

  private boolean accelerateBrewingStand(BrewingStand stand, int level) {
    int brewingTime = stand.getBrewingTime();
    if (brewingTime <= 1) {
      return false;
    }

    int bonus = (int) Math.round(400D * getCookBoostFraction(level));
    if (bonus <= 0) {
      return false;
    }

    stand.setBrewingTime(Math.max(1, brewingTime - bonus));
    stand.update(true, true);
    return true;
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
}
