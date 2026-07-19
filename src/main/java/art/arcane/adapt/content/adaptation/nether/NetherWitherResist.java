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

package art.arcane.adapt.content.adaptation.nether;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.inventorygui.Element;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.ThreadLocalRandom;

public class NetherWitherResist extends SimpleAdaptation<NetherWitherResist.Config> {

  public NetherWitherResist() {
    super("nether-wither-resist");
    registerConfiguration(Config.class);
    setIcon(Material.NETHERITE_CHESTPLATE);
    setInterval(9283);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.WITHER_ROSE)
        .key("challenge_nether_wither_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.NETHER_STAR)
            .key("challenge_nether_wither_1k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_nether_wither_100", "nether.wither-resist.negated", 100, 300);
    registerMilestone("challenge_nether_wither_1k", "nether.wither-resist.negated", 1000, 1000);
  }

  @Override
  public void addStats(int level, Element v) {
    int chance = (int) (getConfig().basePieceChance + getConfig().getChanceAddition() * level);
    statLore(v, chance + "%", 1);
    v.addLore(C.GRAY + " " + Localizer.dLocalize("nether.wither_resist.lore1") + C.DARK_GRAY + Localizer.dLocalize("nether.wither_resist.lore2"));
  }

  @EventHandler
  public void onEntityDamage(EntityDamageEvent e) {
    if (e.getCause() == EntityDamageEvent.DamageCause.WITHER && e.getEntity() instanceof Player p) {
      withAdaptedPlayer(p, e, () -> {
        double chance = getTotalChange(p);
        double roll = ThreadLocalRandom.current().nextDouble(100D);
        if (winsChanceRoll(chance, roll)) {
          e.setCancelled(true);
          addStat(p, "nether.wither-resist.negated", 1);
          boolean mastery = chance >= 100D;
          Location center = p.getLocation().add(0D, 1.0D, 0D);
          FxEmitter emit = fx(center, FxPriority.COMBAT)
              .dome(Particle.SOUL, mastery ? 1.2D : 0.9D, mastery ? 20 : 12)
              .dome(Particle.SCULK_SOUL, mastery ? 1.2D : 0.9D, 6)
              .burst(Particle.CRIT, 4, 0.6D)
              .chord(Sound.PARTICLE_SOUL_ESCAPE, 0.35F, 0.8F, Sound.BLOCK_NOTE_BLOCK_BASS, 0.4F, 0.5F);
          if (mastery) {
            emit.column(Particles.END_ROD, 6, 1.4D).sound(Sound.PARTICLE_SOUL_ESCAPE, 0.35F, 1.1F);
          }
        }
      });
    }
  }

  static boolean winsChanceRoll(double chance, double roll) {
    if (!Double.isFinite(chance) || !Double.isFinite(roll) || roll < 0D || roll >= 100D) {
      return false;
    }

    double boundedChance = Math.max(0D, Math.min(100D, chance));
    return roll < boundedChance;
  }

  private double getTotalChange(Player p) {
    return getChance(p, EquipmentSlot.HEAD) + getChance(p, EquipmentSlot.CHEST) + getChance(p, EquipmentSlot.LEGS) + getChance(p, EquipmentSlot.FEET);
  }

  private double getChance(Player p, EquipmentSlot slot) {
    if (p.getEquipment() == null)
      return 0.0;
    ItemStack item = p.getEquipment().getItem(slot);
    if (item.getType() == Material.NETHERITE_HELMET || item.getType() == Material.NETHERITE_CHESTPLATE || item.getType() == Material.NETHERITE_LEGGINGS || item.getType() == Material.NETHERITE_BOOTS)
      return getConfig().basePieceChance + getConfig().chanceAddition * getLevel(p);
    return 0.0D;
  }

  @Getter
  @Setter
  @ConfigDescription("Wearing Netherite Armor has a chance to negate the wither effect.")
  public static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Base Piece Chance for the Nether Wither Resist adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    private double basePieceChance = 10;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Chance Addition for the Nether Wither Resist adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    private double chanceAddition = 5;

    public Config() {
      baseCost = 3;
      costFactor = 1;
      maxLevel = 3;
      initialCost = 5;
    }
  }
}
