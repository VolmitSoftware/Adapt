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

package art.arcane.adapt.content.adaptation.axe;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
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
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.UUID;

public class AxeSapTap extends SimpleAdaptation<AxeSapTap.Config> {
  private final Cooldowns cooldowns = cooldowns();

  public AxeSapTap() {
    super("axe-sap-tap");
    registerConfiguration(Config.class);
    setIcon(Material.HONEY_BOTTLE);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.HONEY_BOTTLE)
        .key("challenge_axe_sap_tap_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_axe_sap_tap_500", "axe.sap-tap.sap-drawn", 500, 600);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, C.GREEN, "+ ", getYield(level), 1);
    statLore(v, C.GREEN, "+ ", Form.pc(getBonusChance(level), 0), 2);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(PlayerInteractEvent e) {
    if (e.getAction() != Action.RIGHT_CLICK_BLOCK) {
      return;
    }
    if (e.getHand() != null && e.getHand() != EquipmentSlot.HAND) {
      return;
    }

    Player p = e.getPlayer();
    int level = getActiveLevel(p, Player::isSneaking);
    if (level <= 0 || !isAxe(p.getInventory().getItemInMainHand())) {
      return;
    }

    ItemStack offHand = p.getInventory().getItemInOffHand();
    if (offHand.getType() != Material.GLASS_BOTTLE) {
      return;
    }

    Block clicked = e.getClickedBlock();
    if (clicked == null || !isLog(new ItemStack(clicked.getType())) || !isNaturalTree(clicked)) {
      return;
    }

    UUID id = p.getUniqueId();
    if (!cooldowns.isReady(id, getConfig().cooldownMs)) {
      return;
    }

    e.setUseInteractedBlock(Event.Result.DENY);
    e.setUseItemInHand(Event.Result.DENY);
    e.setCancelled(true);
    cooldowns.mark(id);

    consumeGlassBottle(p, offHand);
    damageHand(p, getConfig().durabilityCost);

    int yield = getYield(level);
    ItemStack sap = new ItemStack(Material.HONEY_BOTTLE, yield);
    ItemMeta meta = sap.getItemMeta();
    if (meta != null) {
      meta.setDisplayName(C.GOLD + Localizer.dLocalize("axe.sap_tap.item"));
      sap.setItemMeta(meta);
    }
    safeGiveItem(p, sap);

    boolean bonus = M.r(getBonusChance(level));
    if (bonus) {
      safeGiveItem(p, new ItemStack(bonusMaterial(clicked.getType()), 1));
    }

    addStat(p, "axe.sap-tap.sap-drawn", yield);
    xp(p, yield * getConfig().xpPerSap);

    Location center = clicked.getLocation().add(0.5D, 0.6D, 0.5D);
    fx(center, FxPriority.GAMEPLAY)
        .dustBurst(Color.fromRGB(210, 160, 40), 6, 0.25D, 0.9F)
        .chord(Sound.BLOCK_HONEY_BLOCK_SLIDE, 0.6F, 1.1F, Sound.ITEM_BOTTLE_FILL, 0.7F, 1.0F);
    if (bonus) {
      fx(center, FxPriority.TRANSITION)
          .burst(Particles.VILLAGER_HAPPY, 5, 0.2D)
          .sound(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.4F, 1.6F);
    }
  }

  private boolean isNaturalTree(Block log) {
    int radius = Math.max(1, getConfig().leafScanRadius);
    for (int x = -radius; x <= radius; x++) {
      for (int y = -radius; y <= radius; y++) {
        for (int z = -radius; z <= radius; z++) {
          if (log.getRelative(x, y, z).getType().name().endsWith("_LEAVES")) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private void consumeGlassBottle(Player p, ItemStack offHand) {
    if (offHand.getAmount() > 1) {
      offHand.setAmount(offHand.getAmount() - 1);
      p.getInventory().setItemInOffHand(offHand);
    } else {
      p.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
    }
  }

  private Material bonusMaterial(Material log) {
    String name = log.name();
    if (name.contains("MANGROVE") || name.contains("JUNGLE")) {
      return Material.SLIME_BALL;
    }
    return Material.HONEYCOMB;
  }

  private int getYield(int level) {
    return Math.max(1, getConfig().yieldBase + (int) Math.floor(getLevelPercent(level) * getConfig().yieldFactor));
  }

  private double getBonusChance(int level) {
    return Math.min(1D, getConfig().bonusChanceBase + (getLevelPercent(level) * getConfig().bonusChanceFactor));
  }

  @ConfigDescription("Sneak-right-click a natural log with a glass bottle in your off hand to draw tree sap.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base tree-sap bottles drawn per tap before level scaling.", impact = "Higher values yield more sap per tap.")
    int yieldBase = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional sap bottles unlocked by leveling.", impact = "Higher values raise the maximum sap yield with level.")
    int yieldFactor = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base chance for a resin or slimeball bonus drop before level scaling.", impact = "Higher values make bonus drops more common at low levels.")
    double bonusChanceBase = 0.1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Extra bonus-drop chance granted by leveling.", impact = "Higher values reward leveling with more frequent bonus drops.")
    double bonusChanceFactor = 0.4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Cooldown in milliseconds between sap taps.", impact = "Higher values slow how often sap can be drawn.")
    long cooldownMs = 1500;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Block radius searched for leaves to confirm a natural tree.", impact = "Higher values accept sap taps on larger or sparser trees.")
    int leafScanRadius = 4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Durability spent from the axe per sap tap.", impact = "Higher values wear the axe out faster when tapping sap.")
    int durabilityCost = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "XP granted per sap bottle drawn.", impact = "Higher values accelerate skill progression from tapping sap.")
    double xpPerSap = 4;

    public Config() {
      baseCost = 4;
      costFactor = 0.4;
      maxLevel = 4;
      initialCost = 3;
    }
  }
}
