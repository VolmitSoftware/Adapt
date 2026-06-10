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
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

import java.util.concurrent.ThreadLocalRandom;

public class PickaxeRepairRhythm extends SimpleAdaptation<PickaxeRepairRhythm.Config> {
  public PickaxeRepairRhythm() {
    super("pickaxe-repair-rhythm");
    registerConfiguration(PickaxeRepairRhythm.Config.class);
    setDescription(Localizer.dLocalize("pickaxe.repair_rhythm.description"));
    setDisplayName(Localizer.dLocalize("pickaxe.repair_rhythm.name"));
    setIcon(Material.EXPERIENCE_BOTTLE);
    setBaseCost(getConfig().baseCost);
    setMaxLevel(getConfig().maxLevel);
    setInitialCost(getConfig().initialCost);
    setCostFactor(getConfig().costFactor);
    setInterval(7561);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.EXPERIENCE_BOTTLE)
        .key("challenge_pickaxe_rhythm_5k")
        .title(Localizer.dLocalize("advancement.challenge_pickaxe_rhythm_5k.title"))
        .description(Localizer.dLocalize("advancement.challenge_pickaxe_rhythm_5k.description"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_pickaxe_rhythm_5k", "pickaxe.repair-rhythm.durability-restored", 5000, 500);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + Localizer.dLocalize("pickaxe.repair_rhythm.lore1"));
    v.addLore(C.GREEN + "+ " + Form.pc(getRepairChance(level), 0) + C.GRAY + " " + Localizer.dLocalize("pickaxe.repair_rhythm.lore2"));
  }

  private double getRepairChance(int level) {
    return Math.min(getConfig().maxChance, getConfig().chanceBase + (level * getConfig().chancePerLevel));
  }

  @EventHandler(ignoreCancelled = true)
  public void on(BlockBreakEvent e) {
    Player p = e.getPlayer();
    ItemStack hand = p.getInventory().getItemInMainHand();
    if (!isPickaxe(hand)) {
      return;
    }

    Adaptation.BlockActionContext context = resolveBlockBreakContext(p, e.getBlock().getLocation());
    if (context == null) {
      return;
    }

    if (!M.r(getRepairChance(context.level()))) {
      return;
    }

    if (!(hand.getItemMeta() instanceof Damageable damageable)) {
      return;
    }

    int damage = damageable.getDamage();
    if (damage <= 0) {
      return;
    }

    int min = Math.max(1, getConfig().restoreMin);
    int max = Math.max(min, getConfig().restoreMax);
    int restore = Math.min(damage, min == max ? min : min + ThreadLocalRandom.current().nextInt((max - min) + 1));
    damageable.setDamage(damage - restore);
    hand.setItemMeta(damageable);
    p.getInventory().setItemInMainHand(hand);
    getPlayer(p).getData().addStat("pickaxe.repair-rhythm.durability-restored", restore);
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
  @ConfigDescription("Sustained mining has a chance to restore pickaxe durability per broken block.")
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
    double costFactor = 0.6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
    int maxLevel = 5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base chance per broken block to restore durability.", impact = "Higher values trigger repairs more often at every level.")
    double chanceBase = 0.05;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional repair chance gained per adaptation level.", impact = "Higher values trigger repairs more often at higher levels.")
    double chancePerLevel = 0.06;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum total repair chance per broken block.", impact = "Higher values allow more frequent repairs at max level.")
    double maxChance = 0.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Minimum durability restored per repair proc.", impact = "Higher values restore more durability per proc.")
    int restoreMin = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum durability restored per repair proc.", impact = "Higher values restore more durability per proc.")
    int restoreMax = 2;
  }
}
