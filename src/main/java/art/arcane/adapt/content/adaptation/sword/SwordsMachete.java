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

package art.arcane.adapt.content.adaptation.sword;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Materials;
import art.arcane.volmlib.util.data.Cuboid;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.math.M;
import art.arcane.volmlib.util.math.RNG;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class SwordsMachete extends SimpleAdaptation<SwordsMachete.Config> {
  private static final Set<Material> FOLIAGE;

  static {
    Set<Material> foliage = EnumSet.of(
        Material.TALL_GRASS, Material.CACTUS, Material.SUGAR_CANE, Material.CARROT, Material.POTATO,
        Material.NETHER_WART, Material.FERN, Material.LARGE_FERN, Material.VINE, Material.ROSE_BUSH,
        Material.WITHER_ROSE, Material.ACACIA_LEAVES, Material.BIRCH_LEAVES, Material.DARK_OAK_LEAVES,
        Material.JUNGLE_LEAVES, Material.OAK_LEAVES, Material.SPRUCE_LEAVES, Material.BROWN_MUSHROOM,
        Material.RED_MUSHROOM, Material.DEAD_BUSH, Material.DANDELION, Material.TALL_SEAGRASS,
        Material.SEAGRASS, Material.WHITE_TULIP, Material.RED_TULIP, Material.PINK_TULIP,
        Material.ORANGE_TULIP, Material.LILY_OF_THE_VALLEY, Material.ALLIUM, Material.AZURE_BLUET,
        Material.SUNFLOWER, Material.CORNFLOWER, Material.CHORUS_FLOWER, Material.BAMBOO,
        Material.BAMBOO_SAPLING, Material.LILAC, Material.PEONY, Material.LILY_PAD, Material.COCOA,
        Material.MANGROVE_LEAVES);
    if (Materials.GRASS != null) {
      foliage.add(Materials.GRASS);
    }
    FOLIAGE = foliage;
  }

  public SwordsMachete() {
    super("sword-machete");
    registerConfiguration(Config.class);
    setIcon(Material.IRON_SWORD);
    setInterval(5234);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.IRON_SWORD)
        .key("challenge_swords_machete_2500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.DIAMOND_SWORD)
            .key("challenge_swords_machete_25k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_swords_machete_2500", "swords.machete.foliage-cut", 2500, 300);
    registerMilestone("challenge_swords_machete_25k", "swords.machete.foliage-cut", 25000, 1000);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, getRadius(level), 1);
    statLore(v, C.YELLOW, "* ", Form.duration(getCooldownTime(getLevelPercent(level)) * 50D, 1), 2);
    statLore(v, C.RED, "- ", getDamagePerBlock(getLevelPercent(level)), 3);
  }

  public double getRadius(int level) {
    return (getLevelPercent(level) * getConfig().radiusFactor) + getConfig().radiusBase;
  }

  @EventHandler
  public void on(PlayerInteractEvent e) {
    Action action = e.getAction();
    if ((action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK) || e.getHand() != EquipmentSlot.HAND) {
      return;
    }

    Player p = e.getPlayer();
    ItemStack is = e.getItem();
    if (is == null || !isSword(is) || p.hasCooldown(is.getType()) || !hasActiveAdaptation(p)) {
      return;
    }

    int dmg = 0;
    Location ctr = p.getEyeLocation().clone().add(p.getLocation().getDirection().clone().multiply(2.25)).add(0, -0.5, 0);

    int lvl = getLevel(p);
    Cuboid c = new Cuboid(ctr);
    c = c.expand(Cuboid.CuboidDirection.Up, (int) Math.floor(getRadius(lvl)));
    c = c.expand(Cuboid.CuboidDirection.Down, (int) Math.floor(getRadius(lvl)));
    c = c.expand(Cuboid.CuboidDirection.North, (int) Math.round(getRadius(lvl)));
    c = c.expand(Cuboid.CuboidDirection.South, (int) Math.round(getRadius(lvl)));
    c = c.expand(Cuboid.CuboidDirection.East, (int) Math.round(getRadius(lvl)));
    c = c.expand(Cuboid.CuboidDirection.West, (int) Math.round(getRadius(lvl)));

    for (Block i : c) {
      if (M.r((getLevelPercent(lvl) * 2.8) / (i.getLocation().distanceSquared(ctr)))) {
        if (FOLIAGE.contains(i.getType())) {
          if (!canBlockBreak(p, i.getLocation())) {
            continue;
          }
          BlockBreakEvent ee = new BlockBreakEvent(i, p);
          Bukkit.getPluginManager().callEvent(ee);

          if (!ee.isCancelled()) {
            dmg += 1;
            J.runAt(i.getLocation(), () -> {
              i.breakNaturally();
              sfx(i.getLocation(), Sound.BLOCK_GRASS_BREAK, 0.4F, (float) (ThreadLocalRandom.current().nextDouble() * 1.85D));
            }, RNG.r.i(0, (getMaxLevel() - lvl * 2) + 1));
          }
        }
      }
    }

    if (dmg > 0) {
      p.setCooldown(is.getType(), getCooldownTime(getLevelPercent(lvl)));
      Location tip = p.getEyeLocation().add(p.getLocation().getDirection().multiply(1.6D));
      float sweepPitch = (float) (ThreadLocalRandom.current().nextDouble() / 2D) + 0.65F;
      fx(tip, FxPriority.COMBAT)
          .particle(Particle.CRIT, Math.min(8, dmg + 4), 0, 0, 0, 0.25D, 0.05D)
          .particle(Particle.WAX_ON, 2, 0, 0, 0, 0.05D, 0)
          .chord(Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1F, sweepPitch, Sound.ITEM_AXE_STRIP, 0.35F, 1.4F);
      damageHand(p, dmg * getDamagePerBlock(getLevelPercent(lvl)));
      xp(p, dmg * 11.25, "foliage-cut");
      addStat(p, "swords.machete.foliage-cut", dmg);
    }
  }

  private int getCooldownTime(double levelPercent) {
    return (int) (((int) ((1D - levelPercent) * getConfig().cooldownTicksSlowest)) + getConfig().cooldownTicksBase);
  }

  private int getDamagePerBlock(double levelPercent) {
    return (int) (getConfig().toolDamageBase + (getConfig().toolDamageInverseLevelFactor * ((1D - levelPercent))));
  }


  @ConfigDescription("Cut through foliage with ease using a sword.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Radius Base for the Swords Machete adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double radiusBase = 0.6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Radius Factor for the Swords Machete adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double radiusFactor = 2.36;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Ticks Base for the Swords Machete adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownTicksBase = 7;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Ticks Slowest for the Swords Machete adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownTicksSlowest = 35;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Tool Damage Base for the Swords Machete adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double toolDamageBase = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Tool Damage Inverse Level Factor for the Swords Machete adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double toolDamageInverseLevelFactor = 5;

    public Config() {
      costFactor = 0.225;
      maxLevel = 3;
      initialCost = 7;
    }
  }
}
