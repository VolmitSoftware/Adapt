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

package art.arcane.adapt.content.adaptation.blocking;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
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
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

public class BlockingTemperedGuard extends SimpleAdaptation<BlockingTemperedGuard.Config> {
  public BlockingTemperedGuard() {
    super("blocking-tempered-guard");
    registerConfiguration(Config.class);
    setIcon(Material.ANVIL);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.ANVIL)
        .key("challenge_blocking_tempered_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.NETHERITE_INGOT)
            .key("challenge_blocking_tempered_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_blocking_tempered_500", "blocking.tempered-guard.durability-repaired", 500, 500);
    registerMilestone("challenge_blocking_tempered_5k", "blocking.tempered-guard.durability-repaired", 5000, 2000);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.pc(getRepairChance(level), 0), 1);
    statLore(v, Form.f(getRepairAmount(level), 0), 2);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityDamageByEntityEvent e) {
    if (!(e.getEntity() instanceof Player p) || !p.isBlocking()) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0 || !hasShield(p)) {
      return;
    }

    if (!M.r(getRepairChance(level))) {
      return;
    }

    int amount = getRepairAmount(level);
    J.runEntity(p, () -> applyRepair(p, amount));
  }

  private void applyRepair(Player p, int amount) {
    if (!p.isOnline()) {
      return;
    }

    PlayerInventory inventory = p.getInventory();
    int repaired = repairShield(inventory, amount);
    repaired += repairArmor(inventory, amount);
    if (repaired <= 0) {
      return;
    }

    addStat(p, "blocking.tempered-guard.durability-repaired", repaired);
    xp(p, repaired * getConfig().xpPerDurabilityRepaired);
    fx(p.getLocation().add(0, 1.2D, 0), FxPriority.AMBIENT)
        .particle(Particle.WAX_ON, 3, 0, 0.2D, 0, 0.15D, 0.02D)
        .particle(Particles.ENCHANTMENT_TABLE, 2, 0, 0.3D, 0, 0.1D, 0.05D)
        .sound(Sound.BLOCK_ANVIL_LAND, 0.25F, 1.8F);
  }

  private int repairShield(PlayerInventory inventory, int amount) {
    ItemStack off = inventory.getItemInOffHand();
    if (isItem(off) && off.getType() == Material.SHIELD) {
      int restored = restoreDurability(off, amount);
      if (restored > 0) {
        inventory.setItemInOffHand(off);
        return restored;
      }
    }

    ItemStack main = inventory.getItemInMainHand();
    if (isItem(main) && main.getType() == Material.SHIELD) {
      int restored = restoreDurability(main, amount);
      if (restored > 0) {
        inventory.setItemInMainHand(main);
        return restored;
      }
    }

    return 0;
  }

  private int repairArmor(PlayerInventory inventory, int amount) {
    ItemStack[] armor = inventory.getArmorContents();
    for (int i = 0; i < armor.length; i++) {
      ItemStack piece = armor[i];
      if (!isItem(piece)) {
        continue;
      }

      int restored = restoreDurability(piece, amount);
      if (restored > 0) {
        armor[i] = piece;
        inventory.setArmorContents(armor);
        return restored;
      }
    }

    return 0;
  }

  private int restoreDurability(ItemStack item, int amount) {
    ItemMeta meta = item.getItemMeta();
    if (!(meta instanceof Damageable damageable)) {
      return 0;
    }

    int damage = damageable.getDamage();
    if (damage <= 0) {
      return 0;
    }

    int restored = Math.min(damage, amount);
    damageable.setDamage(damage - restored);
    item.setItemMeta(damageable);
    return restored;
  }

  private boolean hasShield(Player p) {
    ItemStack main = p.getInventory().getItemInMainHand();
    ItemStack off = p.getInventory().getItemInOffHand();
    return (isItem(main) && main.getType() == Material.SHIELD) || (isItem(off) && off.getType() == Material.SHIELD);
  }

  private double getRepairChance(int level) {
    return Math.min(getConfig().maxRepairChance, getConfig().repairChanceBase + (getLevelPercent(level) * getConfig().repairChanceFactor));
  }

  private int getRepairAmount(int level) {
    return Math.max(1, (int) Math.round(getConfig().repairAmountBase + (getLevelPercent(level) * getConfig().repairAmountFactor)));
  }


  @ConfigDescription("Blocked hits can temper your gear, restoring a sliver of shield and armor durability.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Repair Chance Base for the Blocking Tempered Guard adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double repairChanceBase = 0.15;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Repair Chance Factor for the Blocking Tempered Guard adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double repairChanceFactor = 0.4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Repair Chance for the Blocking Tempered Guard adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxRepairChance = 0.55;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Repair Amount Base for the Blocking Tempered Guard adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double repairAmountBase = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Repair Amount Factor for the Blocking Tempered Guard adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double repairAmountFactor = 6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Durability Repaired for the Blocking Tempered Guard adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerDurabilityRepaired = 2.0;

    public Config() {
      costFactor = 0.6;
      initialCost = 3;
    }
  }
}
