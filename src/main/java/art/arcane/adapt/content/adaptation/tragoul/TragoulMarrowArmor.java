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

package art.arcane.adapt.content.adaptation.tragoul;

import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.TragoulMessages;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class TragoulMarrowArmor extends SimpleAdaptation<TragoulMarrowArmor.Config> {
  private static final Color BONE_WHITE = Color.fromRGB(235, 230, 210);
  private final Cooldowns cooldowns = cooldowns();
  private final Cooldowns clickFx = cooldowns();

  public TragoulMarrowArmor() {
    super("tragoul-marrow-armor");
    registerConfiguration(Config.class);
    setIcon(Material.BONE_MEAL);
    setInterval(25000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.BONE_MEAL)
        .key("challenge_tragoul_marrow_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Material.BONE_BLOCK)
            .key("challenge_tragoul_marrow_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_tragoul_marrow_500", "tragoul.marrow-armor.damage-absorbed", 500, 400);
    registerMilestone("challenge_tragoul_marrow_5k", "tragoul.marrow-armor.damage-absorbed", 5000, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + AdaptLanguage.text(TragoulMessages.MARROW_ARMOR_LORE1));
    statLore(v, Form.pc(getAbsorbPercent(level), 0), 2);
    statLore(v, C.YELLOW, "* ", Form.duration((double) getInternalCooldownMillis(level), 1), 3);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(EntityDamageEvent e) {
    if (!(e.getEntity() instanceof Player p)) {
      return;
    }

    if (e.getFinalDamage() < getConfig().minDamageToTrigger) {
      return;
    }

    withAdaptedPlayer(p, e, () -> {
      int level = getActiveLevel(p);
      if (level <= 0) {
        return;
      }

      UUID id = p.getUniqueId();
      if (!cooldowns.isReady(id, getInternalCooldownMillis(level))) {
        return;
      }

      if (!p.getInventory().containsAtLeast(new ItemStack(Material.BONE), 1)) {
        if (clickFx.isReady(id, 1500L)) {
          clickFx.mark(id);
          fx(p.getLocation().add(0, 1.0, 0), FxPriority.TRANSITION)
              .burst(Particles.SMOKE, 1, 0.1)
              .sound(Sound.BLOCK_BONE_BLOCK_HIT, 0.3F, 0.6F);
        }
        return;
      }

      if (!payItemCost(p, "absorb", new ItemStack(Material.BONE), 1, () -> {
        p.getInventory().removeItem(new ItemStack(Material.BONE, 1));
        return true;
      })) {
        return;
      }
      cooldowns.mark(id);
      double absorbed = e.getDamage() * getAbsorbPercent(level);
      e.setDamage(Math.max(0, e.getDamage() - absorbed));
      playMarrowShield(p);
      addStat(p, "tragoul.marrow-armor.damage-absorbed", absorbed);
      xp(p, getConfig().xpPerAbsorb);
    });
  }

  private void playMarrowShield(Player p) {
    BlockData boneData = Material.BONE_BLOCK.createBlockData();
    timeline(p)
        .duration(4)
        .priority(FxPriority.TRANSITION)
        .cullRadius(24)
        .frame((fx, tick, progress) -> {
          if (tick < 3) {
            fx.dustRing(BONE_WHITE, 1.1 - (0.15 * tick), 14, 1.2F);
            fx.dustHelix(BONE_WHITE, 0.8, 2.0, 8, progress * Math.PI, 1.0F);
          }
          if (tick == 0) {
            fx.sound(Sound.BLOCK_BONE_BLOCK_PLACE, 0.5F, 0.8F);
          }
          if (tick == 3) {
            fx.particle(Particles.BLOCK_CRACK, 4, 0, 1.0, 0, 0.35, 0.05, boneData);
            fx.sound(Sound.BLOCK_BONE_BLOCK_BREAK, 0.8F, 1.1F);
          }
        })
        .start();
  }

  private double getAbsorbPercent(int level) {
    return Math.min(getConfig().maxAbsorbPercent,
        Math.max(0, getConfig().absorbPercentBase + (getLevelPercent(level) * getConfig().absorbPercentFactor)));
  }

  private long getInternalCooldownMillis(int level) {
    return Math.max(500L, (long) Math.round(getConfig().internalCooldownMillisBase - (getLevelPercent(level) * getConfig().internalCooldownMillisFactor)));
  }


  @ConfigDescription("Consume a bone from your inventory to absorb part of incoming damage.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Minimum final damage required before a bone is consumed.", impact = "Higher values ignore chip damage and save bones for real hits.")
    double minDamageToTrigger = 2.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Fraction of the hit absorbed before level scaling.", impact = "Higher values absorb more damage per bone.")
    double absorbPercentBase = 0.20;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional absorbed fraction granted at max level.", impact = "Higher values increase level-scaled absorption growth.")
    double absorbPercentFactor = 0.30;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Hard cap on the absorbed fraction of a hit.", impact = "Prevents full damage immunity at high levels.")
    double maxAbsorbPercent = 0.6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Internal cooldown in milliseconds before level scaling.", impact = "Higher values stop damage bursts from draining the bone supply.")
    double internalCooldownMillisBase = 4000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Cooldown reduction in milliseconds granted at max level.", impact = "Higher values let high levels absorb hits more often.")
    double internalCooldownMillisFactor = 2000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "XP granted per absorbed hit.", impact = "Higher values accelerate skill progression from this adaptation.")
    double xpPerAbsorb = 8;

    public Config() {
      costFactor = 0.72;
      initialCost = 4;
    }
  }
}
