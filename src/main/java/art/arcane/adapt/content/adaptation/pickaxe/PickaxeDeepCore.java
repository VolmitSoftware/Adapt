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
import org.bukkit.potion.PotionEffect;

import java.util.EnumSet;
import java.util.Set;

public class PickaxeDeepCore extends SimpleAdaptation<PickaxeDeepCore.Config> {
  private static final Set<Material> DEEPSLATE_BLOCKS = EnumSet.of(
      Material.DEEPSLATE, Material.COBBLED_DEEPSLATE, Material.POLISHED_DEEPSLATE,
      Material.DEEPSLATE_BRICKS, Material.DEEPSLATE_TILES,
      Material.DEEPSLATE_COAL_ORE, Material.DEEPSLATE_COPPER_ORE,
      Material.DEEPSLATE_IRON_ORE, Material.DEEPSLATE_GOLD_ORE,
      Material.DEEPSLATE_REDSTONE_ORE, Material.DEEPSLATE_LAPIS_ORE,
      Material.DEEPSLATE_DIAMOND_ORE, Material.DEEPSLATE_EMERALD_ORE);

  public PickaxeDeepCore() {
    super("pickaxe-deep-core");
    registerConfiguration(PickaxeDeepCore.Config.class);
    setDescription(Localizer.dLocalize("pickaxe.deep_core.description"));
    setDisplayName(Localizer.dLocalize("pickaxe.deep_core.name"));
    setIcon(Material.DEEPSLATE);
    setBaseCost(getConfig().baseCost);
    setMaxLevel(getConfig().maxLevel);
    setInitialCost(getConfig().initialCost);
    setCostFactor(getConfig().costFactor);
    setInterval(5825);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.DEEPSLATE)
        .key("challenge_pickaxe_deepcore_5k")
        .title(Localizer.dLocalize("advancement.challenge_pickaxe_deepcore_5k.title"))
        .description(Localizer.dLocalize("advancement.challenge_pickaxe_deepcore_5k.description"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_pickaxe_deepcore_5k", "pickaxe.deep-core.deepslate-mined", 5000, 400);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + Localizer.dLocalize("pickaxe.deep_core.lore1"));
    v.addLore(C.GREEN + "" + (getAmplifier(level) + 1) + C.GRAY + " " + Localizer.dLocalize("pickaxe.deep_core.lore2"));
  }

  private int getAmplifier(int level) {
    return Math.min(getConfig().maxAmplifier, getConfig().amplifierBase + level - 1);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(BlockDamageEvent e) {
    if (!DEEPSLATE_BLOCKS.contains(e.getBlock().getType())) {
      return;
    }

    Player p = e.getPlayer();
    if (!isPickaxe(p.getInventory().getItemInMainHand())) {
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
    if (!DEEPSLATE_BLOCKS.contains(e.getBlock().getType())) {
      return;
    }

    Player p = e.getPlayer();
    if (!isPickaxe(p.getInventory().getItemInMainHand()) || getActiveLevel(p) <= 0) {
      return;
    }

    getPlayer(p).getData().addStat("pickaxe.deep-core.deepslate-mined", 1);
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
  @ConfigDescription("Gain Haste while mining deepslate so it digs like normal stone.")
  protected static class Config {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
    boolean permanent = false;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
    boolean enabled = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
    int baseCost = 4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
    int initialCost = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
    double costFactor = 0.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
    int maxLevel = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Haste amplifier granted at level 1 while mining deepslate.", impact = "Higher values make deepslate mine faster at every level.")
    int amplifierBase = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum Haste amplifier this adaptation can grant.", impact = "Higher values allow stronger Haste at high levels.")
    int maxAmplifier = 5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Duration in ticks of the Haste effect applied when damaging deepslate.", impact = "Higher values keep the effect active longer between swings.")
    int durationTicks = 60;
  }
}
