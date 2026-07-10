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

package art.arcane.adapt.content.adaptation.pickaxe;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.math.M;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;

public class PickaxeChisel extends SimpleAdaptation<PickaxeChisel.Config> {
  public PickaxeChisel() {
    super("pickaxe-chisel");
    registerConfiguration(Config.class);
    setIcon(Material.IRON_NUGGET);
    setInterval(7433);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.IRON_PICKAXE)
        .key("challenge_pickaxe_chisel_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_pickaxe_chisel_500", "pickaxe.chisel.extra-ores", 500, 400);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.pc(getDropChance(getLevelPercent(level)), 0), 1);
    statLore(v, C.RED, "- ", getDamagePerBlock(getLevelPercent(level)), 2);
  }

  private int getCooldownTime(double levelPercent) {
    return getConfig().cooldownTime;
  }

  private double getDropChance(double levelPercent) {
    return ((levelPercent) * getConfig().dropChanceFactor) + getConfig().dropChanceBase;
  }

  private double getBreakChance(double levelPercent) {
    return getConfig().breakChance;
  }

  private int getDamagePerBlock(double levelPercent) {
    return (int) (getConfig().damagePerBlockBase + (getConfig().damageFactorInverseMultiplier * ((1D - levelPercent))));
  }


  @EventHandler
  public void on(PlayerInteractEvent e) {
    Action action = e.getAction();
    if (action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR) {
      return;
    }

    if (e.getHand() != null && e.getHand() != EquipmentSlot.HAND) {
      return;
    }

    Player p = e.getPlayer();
    if (!isPickaxe(p.getInventory().getItemInMainHand()) || !hasActiveAdaptation(p)) {
      return;
    }

    if (p.getInventory().getItemInMainHand().getEnchantments().containsKey(Enchantment.SILK_TOUCH) || p.getInventory().getItemInMainHand().getEnchantments().containsKey(Enchantment.MENDING)) {
      return;
    }
    if (p.getCooldown(p.getInventory().getItemInMainHand().getType()) > 0) {
      return;
    }

    Block target = action == Action.RIGHT_CLICK_BLOCK ? e.getClickedBlock() : p.getTargetBlockExact(5);
    if (target == null) {
      return;
    }

    if (!canBlockBreak(p, target.getLocation())) {
      return;
    }
    BlockData b = target.getBlockData();
    if (isOre(b)) {
      RayTraceResult ray = p.rayTraceBlocks(8);
      Location c = ray != null ? ray.getHitPosition().toLocation(p.getWorld()) : target.getLocation().add(0.5, 0.5, 0.5);

      fx(c, FxPriority.GAMEPLAY)
          .particle(Particle.CRIT, 6, 0, 0, 0, 0.08, 0.1)
          .particle(Particle.ELECTRIC_SPARK, 4, 0, 0, 0, 0.06, 0.05)
          .chord(Sound.BLOCK_DEEPSLATE_PLACE, 1.25f, 1.4f, Sound.BLOCK_METAL_HIT, 1.25f, 1.7f, Sound.BLOCK_STONE_HIT, 0.6f, 1.9f);

      p.setCooldown(p.getInventory().getItemInMainHand().getType(), getCooldownTime(getLevelPercent(p)));
      damageHand(p, getDamagePerBlock(getLevelPercent(p)));

      ItemStack is = getDropFor(b);
      if (M.r(getDropChance(getLevelPercent(p)))) {
        fx(c, FxPriority.GAMEPLAY)
            .particle(Particles.ITEM_CRACK, 14, 0, 0, 0, 0.08, 0.1, is)
            .chord(Sound.BLOCK_DEEPSLATE_PLACE, 1.25f, 0.787f, Sound.BLOCK_AMETHYST_BLOCK_PLACE, 0.55f, 1.89f);
        timeline(c)
            .duration(6)
            .priority(FxPriority.TRANSITION)
            .frame((fxE, tick, progress) -> {
              fxE.ring(Particles.END_ROD, 0.6D - (0.5D * progress), 6, 0.1D);
              if (tick == 0 || tick == 3 || tick == 5) {
                fxE.sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.35F, (float) (1.2D + (progress * 0.7D)));
              }
            })
            .start();
        target.getWorld().dropItemNaturally(c.clone().subtract(p.getLocation().getDirection().clone().multiply(0.1)), is);
        addStat(p, "pickaxe.chisel.extra-ores", 1);
      } else {
        fx(c, FxPriority.TRANSITION)
            .particle(Particles.ITEM_CRACK, 3, 0, 0, 0, 0.02, 0.1, is)
            .particle(Particles.BLOCK_CRACK, 9, 0, 0, 0, 0.1, 0.0, b)
            .particle(Particles.SMOKE, 2, 0, 0.1, 0, 0.05, 0.01);
      }

      if (M.r(getBreakChance(getLevelPercent(p)))) {
        fx(c, FxPriority.COMBAT)
            .particle(Particles.BLOCK_CRACK, 18, 0, 0, 0, 0.12, 0.05, b)
            .ring(Particles.CRIT_MAGIC, 0.7D, 12, 0.1D)
            .chord(Sound.BLOCK_BASALT_BREAK, 1.25f, 0.4f, Sound.BLOCK_DEEPSLATE_PLACE, 1.25f, 0.887f);
        target.breakNaturally(p.getInventory().getItemInMainHand());
      }
    }
  }

  private ItemStack getDropFor(BlockData b) {
    return switch (b.getMaterial()) {
      case COAL_ORE, DEEPSLATE_COAL_ORE -> new ItemStack(Material.COAL);
      case COPPER_ORE, DEEPSLATE_COPPER_ORE ->
          new ItemStack(Material.RAW_COPPER);
      case GOLD_ORE, DEEPSLATE_GOLD_ORE, NETHER_GOLD_ORE ->
          new ItemStack(Material.RAW_GOLD);
      case IRON_ORE, DEEPSLATE_IRON_ORE -> new ItemStack(Material.RAW_IRON);
      case DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE ->
          new ItemStack(Material.DIAMOND);
      case LAPIS_ORE, DEEPSLATE_LAPIS_ORE ->
          new ItemStack(Material.LAPIS_LAZULI);
      case EMERALD_ORE, DEEPSLATE_EMERALD_ORE ->
          new ItemStack(Material.EMERALD);
      case NETHER_QUARTZ_ORE -> new ItemStack(Material.QUARTZ);
      case REDSTONE_ORE -> new ItemStack(Material.REDSTONE);

      default -> new ItemStack(Material.AIR);
    };
  }


  @ConfigDescription("Right-click ores to chisel extra ore at a severe durability cost.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Time for the Pickaxe Chisel adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int cooldownTime = 5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Drop Chance Base for the Pickaxe Chisel adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double dropChanceBase = 0.07;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Drop Chance Factor for the Pickaxe Chisel adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double dropChanceFactor = 0.22;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Break Chance for the Pickaxe Chisel adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double breakChance = 0.25;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Damage Per Block Base for the Pickaxe Chisel adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double damagePerBlockBase = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Damage Factor Inverse Multiplier for the Pickaxe Chisel adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double damageFactorInverseMultiplier = 2;

    public Config() {
      baseCost = 6;
      costFactor = 0.4;
      maxLevel = 7;
      initialCost = 5;
    }
  }
}
