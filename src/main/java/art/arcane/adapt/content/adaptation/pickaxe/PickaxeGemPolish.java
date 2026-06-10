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
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.math.M;
import lombok.NoArgsConstructor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
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
    setDescription(Localizer.dLocalize("pickaxe.gem_polish.description"));
    setDisplayName(Localizer.dLocalize("pickaxe.gem_polish.name"));
    setIcon(Material.DIAMOND);
    setBaseCost(getConfig().baseCost);
    setMaxLevel(getConfig().maxLevel);
    setInitialCost(getConfig().initialCost);
    setCostFactor(getConfig().costFactor);
    setInterval(6844);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.EMERALD)
        .key("challenge_pickaxe_gempolish_500")
        .title(Localizer.dLocalize("advancement.challenge_pickaxe_gempolish_500.title"))
        .description(Localizer.dLocalize("advancement.challenge_pickaxe_gempolish_500.description"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_pickaxe_gempolish_500", "pickaxe.gem-polish.gems-polished", 500, 400);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + Localizer.dLocalize("pickaxe.gem_polish.lore1"));
    v.addLore(C.GREEN + "+ " + Form.pc(getGemChance(level), 0) + C.GRAY + " " + Localizer.dLocalize("pickaxe.gem_polish.lore2"));
    v.addLore(C.GREEN + "+ " + getBonusXp(level) + C.GRAY + " " + Localizer.dLocalize("pickaxe.gem_polish.lore3"));
  }

  private double getGemChance(int level) {
    return Math.min(getConfig().maxGemChance, getConfig().gemChanceBase + (level * getConfig().gemChancePerLevel));
  }

  private int getBonusXp(int level) {
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

    Location drop = e.getBlock().getLocation().add(0.5, 0.5, 0.5);
    int bonusXp = getBonusXp(context.level());
    if (bonusXp > 0) {
      e.getBlock().getWorld().spawn(drop, org.bukkit.entity.ExperienceOrb.class).setExperience(bonusXp);
    }

    if (M.r(getGemChance(context.level()))) {
      e.getBlock().getWorld().dropItemNaturally(drop, new ItemStack(gem));
      getPlayer(p).getData().addStat("pickaxe.gem-polish.gems-polished", 1);
      if (areParticlesEnabled()) {
        e.getBlock().getWorld().spawnParticle(Particle.HAPPY_VILLAGER, drop, 6, 0.25, 0.25, 0.25);
      }
    }
  }

  @Override
  public boolean isEnabled() {
    return getConfig().enabled;
  }

  @Override
  public void onTick() {
  }

  @Override
  public boolean isPermanent() {
    return getConfig().permanent;
  }

  @NoArgsConstructor
  @ConfigDescription("Mining gem ores grants bonus XP orbs and a chance for an extra matching gem.")
  protected static class Config {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
    boolean permanent = false;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
    boolean enabled = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Skips all bonuses when the pickaxe has Silk Touch.", impact = "True prevents double-dipping by silk-touching ores and mining them again.")
    boolean preventSilkTouchDoubleDip = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
    int baseCost = 6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
    int initialCost = 4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
    double costFactor = 0.7;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
    int maxLevel = 5;
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
  }
}
