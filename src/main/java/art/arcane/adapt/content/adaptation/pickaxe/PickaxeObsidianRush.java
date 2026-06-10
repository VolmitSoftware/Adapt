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
import art.arcane.adapt.util.reflect.registries.PotionEffectTypes;
import art.arcane.volmlib.util.inventorygui.Element;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

public class PickaxeObsidianRush extends SimpleAdaptation<PickaxeObsidianRush.Config> {
  public PickaxeObsidianRush() {
    super("pickaxe-obsidian-rush");
    registerConfiguration(PickaxeObsidianRush.Config.class);
    setDescription(Localizer.dLocalize("pickaxe.obsidian_rush.description"));
    setDisplayName(Localizer.dLocalize("pickaxe.obsidian_rush.name"));
    setIcon(Material.CRYING_OBSIDIAN);
    setBaseCost(getConfig().baseCost);
    setMaxLevel(getConfig().maxLevel);
    setInitialCost(getConfig().initialCost);
    setCostFactor(getConfig().costFactor);
    setInterval(6233);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.OBSIDIAN)
        .key("challenge_pickaxe_obsidianrush_1k")
        .title(Localizer.dLocalize("advancement.challenge_pickaxe_obsidianrush_1k.title"))
        .description(Localizer.dLocalize("advancement.challenge_pickaxe_obsidianrush_1k.description"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_pickaxe_obsidianrush_1k", "pickaxe.obsidian-rush.obsidian-mined", 1000, 500);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + Localizer.dLocalize("pickaxe.obsidian_rush.lore1"));
    v.addLore(C.GREEN + "" + (getAmplifier(level) + 1) + C.GRAY + " " + Localizer.dLocalize("pickaxe.obsidian_rush.lore2"));
    v.addLore(C.ITALIC + Localizer.dLocalize("pickaxe.obsidian_rush.lore3"));
  }

  private int getAmplifier(int level) {
    return Math.min(getConfig().maxAmplifier, getConfig().amplifierBase + level);
  }

  private boolean isRushTarget(Material type) {
    return type == Material.OBSIDIAN || type == Material.CRYING_OBSIDIAN;
  }

  private boolean isRushPickaxe(ItemStack is) {
    if (!isItem(is)) {
      return false;
    }

    return switch (is.getType()) {
      case DIAMOND_PICKAXE, NETHERITE_PICKAXE -> true;
      default -> false;
    };
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(BlockDamageEvent e) {
    if (!isRushTarget(e.getBlock().getType())) {
      return;
    }

    Player p = e.getPlayer();
    if (!isRushPickaxe(p.getInventory().getItemInMainHand())) {
      return;
    }

    Adaptation.BlockActionContext context = resolveInteractContext(p, e.getBlock().getLocation());
    if (context == null) {
      return;
    }

    p.addPotionEffect(new PotionEffect(PotionEffectTypes.FAST_DIGGING, getConfig().durationTicks, getAmplifier(context.level()), false, false, true));
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(BlockBreakEvent e) {
    if (!isRushTarget(e.getBlock().getType())) {
      return;
    }

    Player p = e.getPlayer();
    if (!isRushPickaxe(p.getInventory().getItemInMainHand()) || getActiveLevel(p) <= 0) {
      return;
    }

    getPlayer(p).getData().addStat("pickaxe.obsidian-rush.obsidian-mined", 1);
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
  @ConfigDescription("Gain a strong Haste burst while mining obsidian with a diamond or netherite pickaxe.")
  protected static class Config {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
    boolean permanent = false;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
    boolean enabled = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
    int baseCost = 5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
    int initialCost = 4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
    double costFactor = 0.55;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
    int maxLevel = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Haste amplifier added on top of the adaptation level while mining obsidian.", impact = "Higher values make obsidian mine faster at every level.")
    int amplifierBase = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum Haste amplifier this adaptation can grant.", impact = "Higher values allow stronger Haste at high levels.")
    int maxAmplifier = 7;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Duration in ticks of the Haste burst applied when damaging obsidian.", impact = "Higher values keep the burst active longer between swings.")
    int durationTicks = 120;
  }
}
