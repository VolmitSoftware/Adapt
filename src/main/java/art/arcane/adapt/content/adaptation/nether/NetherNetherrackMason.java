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

package art.arcane.adapt.content.adaptation.nether;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Attributes;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.ThreadLocalRandom;

public class NetherNetherrackMason extends SimpleAdaptation<NetherNetherrackMason.Config> {
  public NetherNetherrackMason() {
    super("nether-netherrack-mason");
    registerConfiguration(Config.class);
    setIcon(Material.BLACKSTONE);
    setInterval(1500);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.NETHERRACK)
        .key("challenge_nether_mason_1k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.GILDED_BLACKSTONE)
            .key("challenge_nether_mason_25k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_nether_mason_1k", "nether.netherrack-mason.blocks-mined", 1000, 300);
    registerMilestone("challenge_nether_mason_25k", "nether.netherrack-mason.blocks-mined", 25000, 1000);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, getHasteTier(level), 1);
    statLore(v, Form.pc(getBonusDropChance(level), 0), 2);
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void on(BlockDamageEvent e) {
    Player p = e.getPlayer();
    withAdaptedPlayer(p, e, () -> {
      if (!isNether(p) || !isMasonBlock(e.getBlock().getType())) {
        return;
      }

      long now = System.currentTimeMillis();
      if (getStorageLong(p, "masonHasteNext", 0L) > now && getStorageLong(p, "masonHasteUntil", 0L) > now) {
        return;
      }

      int hasteDurationTicks = getConfig().hasteDurationTicks;
      if (hasteDurationTicks <= 0) {
        return;
      }

      int level = getActiveLevel(p);
      AdaptAttributeService.get().applyTimed(p, getName(), "haste", Attributes.BLOCK_BREAK_SPEED, breakSpeedBonus(getHasteTier(level)), AttributeModifier.Operation.MULTIPLY_SCALAR_1, hasteDurationTicks);
      setStorage(p, "masonHasteNext", now + getConfig().hasteRefreshMillis);
      setStorage(p, "masonHasteUntil", now + (hasteDurationTicks * 50L));
    });
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(BlockBreakEvent e) {
    Player p = e.getPlayer();
    Material material = e.getBlock().getType();
    if (!isMasonBlock(material)) {
      return;
    }

    Location dropAt = e.getBlock().getLocation().add(0.5D, 0.5D, 0.5D);
    withAdaptedPlayer(p, e, () -> {
      if (!isNether(p)) {
        return;
      }

      int level = getActiveLevel(p);
      addStat(p, "nether.netherrack-mason.blocks-mined", 1);
      xp(p, getConfig().xpPerBlock);

      if (ThreadLocalRandom.current().nextDouble() > getBonusDropChance(level)) {
        return;
      }

      ItemStack bonus = bonusDropFor(material);
      J.runAt(dropAt, () -> {
        if (dropAt.getWorld() != null) {
          dropAt.getWorld().dropItemNaturally(dropAt, bonus);
        }
      });
      xp(p, getConfig().xpOnBonusDrop);
      fx(dropAt, FxPriority.GAMEPLAY)
          .burst(Particle.LAVA, 3, 0.25D)
          .particle(Particle.CRIT, 4, 0D, 0.1D, 0D, 0.2D, 0.1D)
          .sound(Sound.BLOCK_NETHERRACK_BREAK, 0.6F, 1.3F);
    });
  }

  private ItemStack bonusDropFor(Material mined) {
    if (ThreadLocalRandom.current().nextDouble() < getConfig().premiumDropChance) {
      return switch (ThreadLocalRandom.current().nextInt(4)) {
        case 0 -> new ItemStack(Material.GOLD_NUGGET, 1 + ThreadLocalRandom.current().nextInt(2));
        case 1 -> new ItemStack(Material.QUARTZ, 1);
        case 2 -> new ItemStack(Material.IRON_NUGGET, 1 + ThreadLocalRandom.current().nextInt(3));
        default -> new ItemStack(Material.NETHER_BRICK, 1);
      };
    }

    return new ItemStack(mined, 1);
  }

  private boolean isNether(Player p) {
    return p.getWorld().getEnvironment().name().contains("NETHER");
  }

  private boolean isMasonBlock(Material m) {
    return switch (m) {
      case NETHERRACK, BASALT, POLISHED_BASALT, SMOOTH_BASALT, BLACKSTONE, POLISHED_BLACKSTONE,
          GILDED_BLACKSTONE, CHISELED_POLISHED_BLACKSTONE, POLISHED_BLACKSTONE_BRICKS,
          CRACKED_POLISHED_BLACKSTONE_BRICKS -> true;
      default -> false;
    };
  }

  private int getHasteTier(int level) {
    return hasteTier(getLevelPercent(level), getConfig().hasteTierBase, getConfig().hasteTierFactor);
  }

  private double getBonusDropChance(int level) {
    return bonusDropChance(getLevelPercent(level), getConfig().bonusDropChanceBase, getConfig().bonusDropChanceFactor, getConfig().maxBonusDropChance);
  }

  static int hasteTier(double levelPercent, double base, double factor) {
    return Math.max(1, (int) Math.round(base + (levelPercent * factor)));
  }

  static double breakSpeedBonus(int tier) {
    return 0.20D * Math.max(1, tier);
  }

  static double bonusDropChance(double levelPercent, double base, double factor, double cap) {
    return Math.min(cap, base + (levelPercent * factor));
  }


  @ConfigDescription("Mine netherrack, basalt, and blackstone faster in the Nether with occasional bonus drops.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Haste Tier Base for the Nether Netherrack Mason adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double hasteTierBase = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Haste Tier Factor for the Nether Netherrack Mason adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double hasteTierFactor = 1.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Haste Duration Ticks for the Nether Netherrack Mason adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int hasteDurationTicks = 120;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Haste Refresh Millis for the Nether Netherrack Mason adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long hasteRefreshMillis = 4000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Bonus Drop Chance Base for the Nether Netherrack Mason adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double bonusDropChanceBase = 0.08;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Bonus Drop Chance Factor for the Nether Netherrack Mason adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double bonusDropChanceFactor = 0.35;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Bonus Drop Chance for the Nether Netherrack Mason adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxBonusDropChance = 0.4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Premium Drop Chance for the Nether Netherrack Mason adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double premiumDropChance = 0.25;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Block for the Nether Netherrack Mason adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerBlock = 1.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp On Bonus Drop for the Nether Netherrack Mason adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpOnBonusDrop = 5;

    public Config() {
      baseCost = 3;
      costFactor = 0.65;
      maxLevel = 4;
      initialCost = 3;
    }
  }
}
