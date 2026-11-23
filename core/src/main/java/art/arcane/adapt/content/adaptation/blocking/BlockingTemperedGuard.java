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
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.platform.entity.EquipmentSlot;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerItemDamageEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.math.M;

import java.util.List;

public class BlockingTemperedGuard extends SimpleAdaptation<BlockingTemperedGuard.Config> {
  private static final Id SHIELD = Id.minecraft("shield");
  private static final List<EquipmentSlot> ARMOR_SLOTS = List.of(EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD);

  public BlockingTemperedGuard() {
    super("blocking-tempered-guard");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("anvil"));
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("anvil"))
        .key("challenge_blocking_tempered_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("netherite_ingot"))
            .key("challenge_blocking_tempered_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_blocking_tempered_500", "blocking.tempered-guard.durability-repaired", 500, 500);
    registerMilestone("challenge_blocking_tempered_5k", "blocking.tempered-guard.durability-repaired", 5000, 2000);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, BlockingPreferences.SHIELD, BlockingPreferences.ARMOR);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.pc(getRepairChance(level), 0), 1);
    statLore(v, Form.f(getRepairAmount(level), 0), 2);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerItemDamageEvent e) {
    if (!isShieldDamage(e.item()) || e.damage() <= 0) {
      return;
    }

    PlayerRef p = e.player();
    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    if (!M.r(getRepairChance(level))) {
      return;
    }

    int amount = getRepairAmount(level);
    Tasks.runEntity(p, () -> applyRepair(p, amount), 1);
  }

  private void applyRepair(PlayerRef p, int amount) {
    if (!p.isOnline()) {
      return;
    }

    PlayerInventoryRef inventory = p.inventory();
    int repaired = preferenceEnabled(p, BlockingPreferences.SHIELD) ? repairShield(inventory, amount) : 0;
    repaired += preferenceEnabled(p, BlockingPreferences.ARMOR) ? repairArmor(inventory, amount) : 0;
    if (repaired <= 0) {
      return;
    }

    addStat(p, "blocking.tempered-guard.durability-repaired", repaired);
    xp(p, repaired * getConfig().xpPerDurabilityRepaired);
    fx(p.location().add(0, 1.2D, 0), FxPriority.AMBIENT)
        .particle(Particles.WAX_ON, 3, 0, 0.2D, 0, 0.15D, 0.02D)
        .particle(Particles.ENCHANTMENT_TABLE, 2, 0, 0.3D, 0, 0.1D, 0.05D)
        .sound(Sounds.BLOCK_ANVIL_LAND, 0.25F, 1.8F);
  }

  private int repairShield(PlayerInventoryRef inventory, int amount) {
    ItemRef off = inventory.offHand();
    if (isItem(off) && SHIELD.equals(off.type())) {
      int restored = restoreDurability(off, amount);
      if (restored > 0) {
        inventory.setOffHand(off);
        return restored;
      }
    }

    ItemRef main = inventory.mainHand();
    if (isItem(main) && SHIELD.equals(main.type())) {
      int restored = restoreDurability(main, amount);
      if (restored > 0) {
        inventory.setMainHand(main);
        return restored;
      }
    }

    return 0;
  }

  private int repairArmor(PlayerInventoryRef inventory, int amount) {
    for (EquipmentSlot slot : ARMOR_SLOTS) {
      ItemRef piece = inventory.armor(slot);
      if (!isItem(piece)) {
        continue;
      }

      int restored = restoreDurability(piece, amount);
      if (restored > 0) {
        inventory.setArmor(slot, piece);
        return restored;
      }
    }

    return 0;
  }

  private int restoreDurability(ItemRef item, int amount) {
    int damage = item.damage();
    if (damage <= 0) {
      return 0;
    }

    int restored = Math.min(damage, amount);
    item.setDamage(damage - restored);
    return restored;
  }

  static boolean isShieldDamage(ItemRef item) {
    return item != null && SHIELD.equals(item.type());
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
      baseCost = 2;
      costFactor = 0.5;
      initialCost = 1;
    }
  }
}
