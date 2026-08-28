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

import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.PickaxeMessages;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Iterator;
import java.util.concurrent.ThreadLocalRandom;

public class PickaxeAutosmelt extends SimpleAdaptation<PickaxeAutosmelt.Config> {
  public PickaxeAutosmelt() {
    super("pickaxe-autosmelt");
    registerConfiguration(PickaxeAutosmelt.Config.class);
    setLocalizationKey("pickaxe.auto_smelt");
    setIcon(Material.RAW_GOLD);
    setInterval(7444);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.FURNACE)
        .key("challenge_pickaxe_autosmelt_1k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Material.BLAST_FURNACE)
            .key("challenge_pickaxe_autosmelt_25k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_pickaxe_autosmelt_1k", "pickaxe.autosmelt.ores-smelted", 1000, 400);
    registerMilestone("challenge_pickaxe_autosmelt_25k", "pickaxe.autosmelt.ores-smelted", 25000, 1500);
  }

  private static boolean replaceRawDrops(BlockDropItemEvent event, Material ore, Adaptation<?> source, int level) {
    Material ingot = getIngotFor(ore);
    Location location = event.getBlock().getLocation();
    if (ingot == null || location.getWorld() == null) {
      return false;
    }

    Item converted = null;
    int nativeAmount = 0;
    Iterator<Item> iterator = event.getItems().iterator();
    while (iterator.hasNext()) {
      Item item = iterator.next();
      if (!isNativeRawDrop(ore, item.getItemStack().getType())) {
        continue;
      }
      nativeAmount += item.getItemStack().getAmount();
      if (converted == null) {
        converted = item;
      } else {
        iterator.remove();
        item.remove();
      }
    }
    if (converted == null) {
      return false;
    }

    int amount = getCommittedSmeltAmount(nativeAmount, level, ThreadLocalRandom.current().nextDouble());
    converted.setItemStack(new ItemStack(ingot, amount));
    smeltFx(location, source, nativeAmount);
    return true;
  }

  static Material getIngotFor(Material ore) {
    return switch (ore) {
      case IRON_ORE, DEEPSLATE_IRON_ORE -> Material.IRON_INGOT;
      case GOLD_ORE, DEEPSLATE_GOLD_ORE -> Material.GOLD_INGOT;
      case COPPER_ORE, DEEPSLATE_COPPER_ORE -> Material.COPPER_INGOT;
      default -> null;
    };
  }

  static Material getRawDropFor(Material ore) {
    return switch (ore) {
      case IRON_ORE, DEEPSLATE_IRON_ORE -> Material.RAW_IRON;
      case GOLD_ORE, DEEPSLATE_GOLD_ORE -> Material.RAW_GOLD;
      case COPPER_ORE, DEEPSLATE_COPPER_ORE -> Material.RAW_COPPER;
      default -> null;
    };
  }

  static boolean isNativeRawDrop(Material ore, Material drop) {
    Material rawDrop = getRawDropFor(ore);
    return rawDrop != null && rawDrop == drop;
  }

  static double getExtraDropChance(int level) {
    return (level * 1.25D) / 100.0D;
  }

  static int getCommittedSmeltAmount(int nativeAmount, int level, double roll) {
    return nativeAmount + (roll < getExtraDropChance(level) ? 1 : 0);
  }

  private static void smeltFx(Location location, Adaptation<?> source, int fortune) {
    Location center = location.clone().add(0.5, 0.5, 0.5);
    Fx.now(source, center, FxPriority.TRANSITION)
        .particle(Particle.FLAME, 6, 0, 0.1, 0, 0.16, 0.02)
        .particle(Particle.LAVA, 3, 0, 0.1, 0, 0.2, 0.0)
        .particle(Particles.SMOKE, 3, 0, 0.2, 0, 0.12, 0.02)
        .sound(Sound.BLOCK_LAVA_POP, 1, 1);
    if (fortune > 1) {
      Fx.now(source, center, FxPriority.AMBIENT)
          .particle(Particle.WAX_ON, Math.min(8, fortune), 0, 0.2, 0, 0.22, 0.02)
          .sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.35f, 1.9f);
    }
  }

  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + AdaptLanguage.text(PickaxeMessages.AUTO_SMELT_LORE1));
    statLore(v, C.GREEN, "", level * 1.25, 2);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(BlockDropItemEvent e) {
    Player p = e.getPlayer();
    Material ore = e.getBlockState().getType();
    if (getIngotFor(ore) == null || e.getItems().isEmpty()) {
      return;
    }

    ItemStack tool = p.getInventory().getItemInMainHand();
    if (!isPickaxe(tool) || !e.getBlockState().getBlockData().isPreferredTool(tool)) {
      return;
    }
    if (tool.getEnchantments().containsKey(Enchantment.SILK_TOUCH)) {
      return;
    }

    Adaptation.BlockActionContext context = resolveBlockBreakContext(p, e.getBlock().getLocation());
    if (context == null) {
      return;
    }

    if (!replaceRawDrops(e, ore, this, context.level())) {
      return;
    }
    addStat(p, "pickaxe.autosmelt.ores-smelted", 1);
  }



  @ConfigDescription("Automatically smelt mined ores with a chance for extra drops.")
  protected static class Config extends AdaptationConfig {
    public Config() {
      baseCost = 6;
      costFactor = 0.95;
      maxLevel = 4;
      initialCost = 4;
    }
  }
}
