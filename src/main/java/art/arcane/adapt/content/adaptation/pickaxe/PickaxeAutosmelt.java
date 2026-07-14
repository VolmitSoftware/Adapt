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

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.world.PlayerAdaptation;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.content.item.ItemListings;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Enchantments;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
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
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.BLAST_FURNACE)
            .key("challenge_pickaxe_autosmelt_25k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_pickaxe_autosmelt_1k", "pickaxe.autosmelt.ores-smelted", 1000, 400);
    registerMilestone("challenge_pickaxe_autosmelt_25k", "pickaxe.autosmelt.ores-smelted", 25000, 1500);
  }

  static void autosmeltBlockDTI(Block b, Player p, Adaptation<?> source, int level) {
    Material ingot = getIngotFor(b.getType());
    if (ingot == null || b.getLocation().getWorld() == null) {
      return;
    }

    int fortune = getFortuneOreMultiplier(p.getInventory().getItemInMainHand()
        .getEnchantments().get(Enchantments.LOOT_BONUS_BLOCKS));
    int amount = getSmeltAmount(fortune, level);
    b.setType(Material.AIR);
    HashMap<Integer, ItemStack> excessItems = p.getInventory().addItem(new ItemStack(ingot, amount));
    excessItems.values().forEach(itemStack -> b.getLocation().getWorld().dropItemNaturally(b.getLocation(), itemStack));
    smeltFx(b, source, fortune);
  }

  static void autosmeltBlock(Block b, Player p, Adaptation<?> source, int level) {
    Material ingot = getIngotFor(b.getType());
    if (ingot == null || b.getLocation().getWorld() == null) {
      return;
    }

    int fortune = getFortuneOreMultiplier(p.getInventory().getItemInMainHand()
        .getEnchantments().get(Enchantments.LOOT_BONUS_BLOCKS));
    int amount = getSmeltAmount(fortune, level);
    b.setType(Material.AIR);
    b.getLocation().getWorld().dropItemNaturally(b.getLocation(), new ItemStack(ingot, amount));
    smeltFx(b, source, fortune);
  }

  private static Material getIngotFor(Material ore) {
    return switch (ore) {
      case IRON_ORE, DEEPSLATE_IRON_ORE -> Material.IRON_INGOT;
      case GOLD_ORE, DEEPSLATE_GOLD_ORE -> Material.GOLD_INGOT;
      case COPPER_ORE, DEEPSLATE_COPPER_ORE -> Material.COPPER_INGOT;
      default -> null;
    };
  }

  static double getExtraDropChance(int level) {
    return (level * 1.25D) / 100.0D;
  }

  private static int getSmeltAmount(int fortuneMultiplier, int level) {
    int amount = Math.max(1, fortuneMultiplier);
    if (ThreadLocalRandom.current().nextDouble() < getExtraDropChance(level)) {
      amount++;
    }
    return amount;
  }

  private static void smeltFx(Block b, Adaptation<?> source, int fortune) {
    Location center = b.getLocation().add(0.5, 0.5, 0.5);
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

  // https://minecraft.fandom.com/wiki/Fortune?oldid=2359015#Ore
  private static int getFortuneOreMultiplier(Integer fortuneLevel) {
    if (fortuneLevel == null || fortuneLevel < 1) return 1;

    double averageBonusMultiplier = (1.0 / (fortuneLevel + 2) + (fortuneLevel + 1) / 2.0) - 1;
    int sumOfBonusMultipliers = (fortuneLevel * (fortuneLevel + 1)) / 2;
    double chancePerMultiplier = averageBonusMultiplier / sumOfBonusMultipliers;

    int bonusMultiplier = ((int) (ThreadLocalRandom.current().nextDouble() / chancePerMultiplier)) + 1;

    return bonusMultiplier <= fortuneLevel ? bonusMultiplier + 1 : 1;
  }

  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + Localizer.dLocalize("pickaxe.auto_smelt.lore1"));
    v.addLore(C.GREEN + "" + (level * 1.25) + C.GRAY + Localizer.dLocalize("pickaxe.auto_smelt.lore2"));
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(BlockBreakEvent e) {
    Player p = e.getPlayer();
    if (!e.getBlock().getBlockData().getMaterial().name().endsWith("_ORE") && !ItemListings.getSmeltOre().contains(e.getBlock().getType())) {
      return;
    }

    ItemStack tool = p.getInventory().getItemInMainHand();
    if (!isPickaxe(tool) || !e.getBlock().isPreferredTool(tool)) {
      return;
    }
    if (tool.getEnchantments().containsKey(Enchantment.SILK_TOUCH)) {
      return;
    }

    Adaptation.BlockActionContext context = resolveBlockBreakContext(p, e.getBlock().getLocation());
    if (context == null) {
      return;
    }

    PlayerSkillLine line = getPlayer(p).getData().getSkillLineNullable("pickaxe");
    PlayerAdaptation adaptation = line != null ? line.getAdaptation("pickaxe-drop-to-inventory") : null;
    if (adaptation != null && adaptation.getLevel() > 0) {
      PickaxeAutosmelt.autosmeltBlockDTI(e.getBlock(), p, this, context.level());
    } else {
      PickaxeAutosmelt.autosmeltBlock(e.getBlock(), p, this, context.level());
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
