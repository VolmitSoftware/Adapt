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

import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.misc.SoundPlayer;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.math.M;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

public class PickaxeUnbreakablePact extends SimpleAdaptation<PickaxeUnbreakablePact.Config> {
  public PickaxeUnbreakablePact() {
    super("pickaxe-unbreakable-pact");
    registerConfiguration(PickaxeUnbreakablePact.Config.class);
    setDescription(Localizer.dLocalize("pickaxe.unbreakable_pact.description"));
    setDisplayName(Localizer.dLocalize("pickaxe.unbreakable_pact.name"));
    setIcon(Material.ANVIL);
    setBaseCost(getConfig().baseCost);
    setMaxLevel(getConfig().maxLevel);
    setInitialCost(getConfig().initialCost);
    setCostFactor(getConfig().costFactor);
    setInterval(9122);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.NETHERITE_PICKAXE)
        .key("challenge_pickaxe_pact_100")
        .title(Localizer.dLocalize("advancement.challenge_pickaxe_pact_100.title"))
        .description(Localizer.dLocalize("advancement.challenge_pickaxe_pact_100.description"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_pickaxe_pact_100", "pickaxe.unbreakable-pact.saves", 100, 400);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + Localizer.dLocalize("pickaxe.unbreakable_pact.lore1"));
    v.addLore(C.GREEN + "+ " + Form.pc(getIgnoreChance(level), 0) + C.GRAY + " " + Localizer.dLocalize("pickaxe.unbreakable_pact.lore2"));
  }

  private double getIgnoreChance(int level) {
    return Math.min(getConfig().maxIgnoreChance, level * getConfig().ignoreChancePerLevel);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(PlayerItemDamageEvent e) {
    ItemStack item = e.getItem();
    if (!isPickaxe(item)) {
      return;
    }

    Player p = e.getPlayer();
    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    if (M.r(getIgnoreChance(level))) {
      e.setCancelled(true);
      getPlayer(p).getData().addStat("pickaxe.unbreakable-pact.damage-ignored", e.getDamage());
      return;
    }

    if (!(item.getItemMeta() instanceof Damageable damageable)) {
      return;
    }

    int maxDurability = item.getType().getMaxDurability();
    if (damageable.getDamage() + e.getDamage() < maxDurability) {
      return;
    }

    e.setCancelled(true);
    damageable.setDamage(maxDurability - 1);
    item.setItemMeta(damageable);
    getPlayer(p).getData().addStat("pickaxe.unbreakable-pact.saves", 1);
    SoundPlayer.of(p.getWorld()).play(p.getLocation(), Sound.BLOCK_ANVIL_PLACE, 0.4f, 1.8f);
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
  @ConfigDescription("Your pickaxe refuses to break, surviving at 1 durability.")
  protected static class Config {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
    boolean permanent = false;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
    boolean enabled = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
    int baseCost = 6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
    int initialCost = 5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
    double costFactor = 0.65;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
    int maxLevel = 5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Chance per level to ignore pickaxe durability loss entirely.", impact = "Higher values make the pickaxe lose durability less often at higher levels.")
    double ignoreChancePerLevel = 0.04;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum total chance to ignore durability loss.", impact = "Higher values allow more durability loss to be ignored at max level.")
    double maxIgnoreChance = 0.25;
  }
}
