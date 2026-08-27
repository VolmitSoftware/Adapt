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

package art.arcane.adapt.content.adaptation.architect;

import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.ArchitectMessages;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.compat.PaperCompat;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public class ArchitectStonecutterSavant extends SimpleAdaptation<ArchitectStonecutterSavant.Config> {
  private static final int STONECUTTER_LEVELS = 1;

  public ArchitectStonecutterSavant() {
    super("architect-stonecutter-savant");
    registerConfiguration(ArchitectStonecutterSavant.Config.class);
    setMaxLevel(STONECUTTER_LEVELS);
    setIcon(Material.STONECUTTER);
    setInterval(24420);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.STONECUTTER)
        .key("challenge_architect_stonecutter_savant_50")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.STONECUTTER)
            .key("challenge_architect_stonecutter_savant_500")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_architect_stonecutter_savant_50", "architect.stonecutter-savant.uses", 50, 300);
    registerMilestone("challenge_architect_stonecutter_savant_500", "architect.stonecutter-savant.uses", 500, 1000);
  }

  @Override
  protected boolean shouldCanonicalizeConfigOnLoad() {
    return true;
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + AdaptLanguage.text(ArchitectMessages.STONECUTTER_SAVANT_LORE1));
    v.addLore(C.YELLOW + AdaptLanguage.text(
        getConfig().requireOffhand
            ? ArchitectMessages.STONECUTTER_SAVANT_LORE3
            : ArchitectMessages.STONECUTTER_SAVANT_LORE2
    ));
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(PlayerInteractEvent e) {
    Action action = e.getAction();
    if (action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK) {
      return;
    }

    if (e.getHand() != null && e.getHand() != EquipmentSlot.HAND) {
      return;
    }

    Player p = e.getPlayer();
    if (!p.isSneaking()) {
      return;
    }

    PlayerInventory inventory = p.getInventory();
    ItemStack hand = inventory.getItemInMainHand();
    if (isItem(hand) && hand.getType() != Material.AIR) {
      return;
    }

    if (!hasStonecutter(inventory)) {
      return;
    }
    if (resolveInteractContext(p, p.getLocation(), Player::isSneaking) == null) {
      return;
    }

    withPlayerThread(p, () -> {
      PaperCompat.openStonecutter(p);
      fx(p.getLocation(), FxPriority.GAMEPLAY)
          .helix(Particles.ENCHANTMENT_TABLE, 0.5D, 1.6D, 12, 0)
          .chord(Sound.BLOCK_GRINDSTONE_USE, 0.8f, 1.4f, Sound.BLOCK_STONE_PLACE, 0.4f, 0.8f);
      addStat(p, "architect.stonecutter-savant.uses", 1);
      xp(p, getConfig().xpPerUse);
    });
  }

  private boolean hasStonecutter(PlayerInventory inventory) {
    if (getConfig().requireOffhand) {
      return inventory.getItemInOffHand().getType() == Material.STONECUTTER;
    }

    return inventory.contains(Material.STONECUTTER);
  }

  @ConfigDescription("Sneak-punch the air with an empty hand while carrying a stonecutter to open it anywhere.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Requires the stonecutter item to be in the offhand specifically.", impact = "True only accepts a stonecutter held in the offhand; false accepts a stonecutter anywhere in the inventory.")
    boolean requireOffhand = false;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Adaptation xp granted per stonecutter opened.", impact = "Higher values speed up adaptation progression from uses.")
    double xpPerUse = 2;

    public Config() {
      costFactor = 0.5;
      maxLevel = STONECUTTER_LEVELS;
    }
  }
}
