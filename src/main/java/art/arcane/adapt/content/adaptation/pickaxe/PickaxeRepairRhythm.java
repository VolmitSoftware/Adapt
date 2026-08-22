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
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.math.M;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class PickaxeRepairRhythm extends SimpleAdaptation<PickaxeRepairRhythm.Config> {
  private final Map<UUID, PendingRepair> pendingRepairs = playerState();

  public PickaxeRepairRhythm() {
    super("pickaxe-repair-rhythm");
    registerConfiguration(PickaxeRepairRhythm.Config.class);
    setIcon(Material.EXPERIENCE_BOTTLE);
    setInterval(7561);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.EXPERIENCE_BOTTLE)
        .key("challenge_pickaxe_rhythm_5k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_pickaxe_rhythm_5k", "pickaxe.repair-rhythm.durability-restored", 5000, 500);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + AdaptLanguage.text(PickaxeMessages.REPAIR_RHYTHM_LORE1));
    statLore(v, Form.pc(getRepairChance(level), 0), 2);
  }

  private double getRepairChance(int level) {
    return Math.min(getConfig().maxChance, getConfig().chanceBase + (level * getConfig().chancePerLevel));
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
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

    if (!(hand.getItemMeta() instanceof Damageable damageable)) {
      return;
    }

    int damage = damageable.getDamage();
    if (damage <= 0) {
      return;
    }

    if (!M.r(getRepairChance(context.level()))) {
      return;
    }

    int min = Math.max(1, getConfig().restoreMin);
    int max = Math.max(min, getConfig().restoreMax);
    int restore = min == max ? min : min + ThreadLocalRandom.current().nextInt((max - min) + 1);
    UUID playerId = p.getUniqueId();
    PendingRepair pending = new PendingRepair(p.getInventory().getHeldItemSlot(), hand.clone(), restore);
    pendingRepairs.put(playerId, pending);
    J.runEntity(p, () -> applyDeferredRepair(p, playerId, pending), 1);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(PlayerItemDamageEvent e) {
    Player p = e.getPlayer();
    UUID playerId = p.getUniqueId();
    PendingRepair pending = pendingRepairs.get(playerId);
    if (pending == null || !sameRepairTarget(pending.item(), e.getItem())) {
      return;
    }

    pendingRepairs.remove(playerId, pending);
    int restored = repair(e.getItem(), pending.restore());
    if (restored <= 0) {
      return;
    }

    e.setCancelled(true);
    completeRepair(p, restored);
  }

  static int repairedDamage(int damage, int restore) {
    return Math.max(0, damage - Math.max(0, restore));
  }

  static boolean sameRepairTarget(ItemStack expected, ItemStack actual) {
    if (expected == null || actual == null || expected.getType() != actual.getType()) {
      return false;
    }

    ItemStack expectedCopy = expected.clone();
    ItemStack actualCopy = actual.clone();
    clearDamage(expectedCopy);
    clearDamage(actualCopy);
    return expectedCopy.isSimilar(actualCopy);
  }

  private static void clearDamage(ItemStack item) {
    if (item.getItemMeta() instanceof Damageable damageable) {
      damageable.setDamage(0);
      item.setItemMeta(damageable);
    }
  }

  private void applyDeferredRepair(Player p, UUID playerId, PendingRepair pending) {
    if (!pendingRepairs.remove(playerId, pending)) {
      return;
    }

    ItemStack item = p.getInventory().getItem(pending.slot());
    if (!sameRepairTarget(pending.item(), item)) {
      return;
    }

    int restored = repair(item, pending.restore());
    if (restored > 0) {
      p.getInventory().setItem(pending.slot(), item);
      completeRepair(p, restored);
    }
  }

  private int repair(ItemStack item, int requested) {
    if (!(item.getItemMeta() instanceof Damageable damageable)) {
      return 0;
    }

    int damage = damageable.getDamage();
    int remainingDamage = repairedDamage(damage, requested);
    int restored = damage - remainingDamage;
    if (restored <= 0) {
      return 0;
    }

    damageable.setDamage(remainingDamage);
    item.setItemMeta(damageable);
    return restored;
  }

  private void completeRepair(Player p, int restore) {
    addStat(p, "pickaxe.repair-rhythm.durability-restored", restore);
    fx(p.getLocation().add(0, 1.2, 0), FxPriority.AMBIENT)
        .particle(Particle.WAX_ON, 2, 0, 0.2, 0, 0.15, 0.02)
        .particle(Particles.ENCHANTMENT_TABLE, 1, 0, 0.3, 0, 0.1, 0.05)
        .sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.25f, restore >= 2 ? 1.95f : 1.8f);
  }


  @ConfigDescription("Sustained mining has a chance to restore pickaxe durability per broken block.")
  protected static class Config extends AdaptationConfig {
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

    public Config() {
      baseCost = 5;
      costFactor = 0.6;
      initialCost = 4;
    }
  }

  private record PendingRepair(int slot, ItemStack item, int restore) {
  }
}
