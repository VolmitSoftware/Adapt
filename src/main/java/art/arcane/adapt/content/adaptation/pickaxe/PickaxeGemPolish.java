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
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.math.M;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

public class PickaxeGemPolish extends SimpleAdaptation<PickaxeGemPolish.Config> {
  public PickaxeGemPolish() {
    super("pickaxe-gem-polish");
    registerConfiguration(PickaxeGemPolish.Config.class);
    setIcon(Material.DIAMOND);
    setInterval(6844);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.EMERALD)
        .key("challenge_pickaxe_gempolish_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_pickaxe_gempolish_500", "pickaxe.gem-polish.gems-polished", 500, 400);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + Localizer.dLocalize("pickaxe.gem_polish.lore1"));
    statLore(v, Form.pc(getGemChance(level), 0), 2);
    statLore(v, getBonusXp(level), 3);
  }

  public double getGemChance(int level) {
    return Math.min(getConfig().maxGemChance, getConfig().gemChanceBase + (level * getConfig().gemChancePerLevel));
  }

  public int getBonusXp(int level) {
    return getConfig().bonusXpBase + (level * getConfig().bonusXpPerLevel);
  }

  private Material getGemFor(Material type) {
    return switch (type) {
      case DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE -> Material.DIAMOND;
      case EMERALD_ORE, DEEPSLATE_EMERALD_ORE -> Material.EMERALD;
      case LAPIS_ORE, DEEPSLATE_LAPIS_ORE -> Material.LAPIS_LAZULI;
      case AMETHYST_CLUSTER -> Material.AMETHYST_SHARD;
      default -> null;
    };
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void on(BlockBreakEvent e) {
    Material gem = getGemFor(e.getBlock().getType());
    if (gem == null) {
      return;
    }

    Player p = e.getPlayer();
    ItemStack hand = p.getInventory().getItemInMainHand();
    if (!isPickaxe(hand)) {
      return;
    }

    if (getConfig().preventSilkTouchDoubleDip && hand.getEnchantments().containsKey(Enchantment.SILK_TOUCH)) {
      return;
    }

    Adaptation.BlockActionContext context = resolveBlockBreakContext(p, e.getBlock().getLocation());
    if (context == null) {
      return;
    }

    Material blockType = e.getBlock().getType();
    Location drop = e.getBlock().getLocation().add(0.5, 0.5, 0.5);
    int bonusXp = getBonusXp(context.level());
    if (bonusXp > 0) {
      e.getBlock().getWorld().spawn(drop, org.bukkit.entity.ExperienceOrb.class).setExperience(bonusXp);
      fx(drop, FxPriority.AMBIENT)
          .column(Particles.END_ROD, 4, 0.8D)
          .sound(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.4f);
    }

    if (M.r(getGemChance(context.level()))) {
      e.getBlock().getWorld().dropItemNaturally(drop, new ItemStack(gem));
      addStat(p, "pickaxe.gem-polish.gems-polished", 1);
      Color gemColor = gemColor(gem);
      timeline(drop)
          .duration(5)
          .priority(FxPriority.TRANSITION)
          .frame((fxE, tick, progress) -> {
            double r = 0.7D - (0.5D * progress);
            fxE.ring(Particles.END_ROD, r, 4, 0.4D);
            fxE.dustRing(gemColor, r, 6, 1.0F);
            if (tick == 0) {
              fxE.chord(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, 1.6f, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 0.4f, 1.2f);
            }
          })
          .start();
      if (blockType == Material.AMETHYST_CLUSTER) {
        fx(drop, FxPriority.AMBIENT).sound(Sound.BLOCK_AMETHYST_CLUSTER_HIT, 0.4f, 1.3f);
      }
    }
  }

  private static Color gemColor(Material gem) {
    return switch (gem) {
      case EMERALD -> Color.fromRGB(0x2ECC71);
      case LAPIS_LAZULI -> Color.fromRGB(0x1E5AA8);
      case AMETHYST_SHARD -> Color.fromRGB(0x9B59B6);
      default -> Color.fromRGB(0x4DD0E1);
    };
  }


  @ConfigDescription("Mining gem ores grants bonus XP orbs and a chance for an extra matching gem.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Skips all bonuses when the pickaxe has Silk Touch.", impact = "True prevents double-dipping by silk-touching ores and mining them again.")
    boolean preventSilkTouchDoubleDip = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base chance for an extra gem drop when mining a gem ore.", impact = "Higher values drop extra gems more often at every level.")
    double gemChanceBase = 0.04;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional extra-gem chance gained per adaptation level.", impact = "Higher values drop extra gems more often at higher levels.")
    double gemChancePerLevel = 0.05;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum total extra-gem chance.", impact = "Higher values allow more frequent extra gems at max level.")
    double maxGemChance = 0.4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base bonus XP orb value granted per mined gem ore.", impact = "Higher values grant more XP at every level.")
    int bonusXpBase = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional bonus XP orb value gained per adaptation level.", impact = "Higher values grant more XP at higher levels.")
    int bonusXpPerLevel = 2;

    public Config() {
      baseCost = 6;
      costFactor = 0.7;
      initialCost = 4;
    }
  }
}
