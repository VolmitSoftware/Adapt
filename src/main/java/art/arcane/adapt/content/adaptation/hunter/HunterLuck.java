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

package art.arcane.adapt.content.adaptation.hunter;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPresets;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

public class HunterLuck extends SimpleAdaptation<HunterLuck.Config> {
  private static final Color LUCK_GOLD = Color.fromRGB(255, 215, 0);
  private final Cooldowns fxCooldown = cooldowns();

  public HunterLuck() {
    super("hunter-luck");
    registerConfiguration(Config.class);
    setIcon(Material.TADPOLE_BUCKET);
    setInterval(9644);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.EMERALD)
        .key("challenge_hunter_luck_200")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_hunter_luck_200", "hunter.luck.activations", 200, 300);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GRAY + Localizer.dLocalize("hunter.luck.lore1"));
    statLore(v, level, 2);
    statLore(v, C.RED, "- ", (5 + level), 3);
    statLore(v, C.GRAY, "* ", level, 4);
    statLore(v, C.GRAY, "* ", level, 5);
    v.addLore(C.GRAY + "- " + level + C.RED + " " + Localizer.dLocalize("hunter.penalty.lore1"));

  }


  @EventHandler
  public void on(EntityDamageEvent e) {
    if (e.getEntity() instanceof org.bukkit.entity.Player p && isAdaptableDamageCause(e) && hasActiveAdaptation(p)) {
      if (AdaptConfig.get().isPreventHunterSkillsWhenHungerApplied() && p.hasPotionEffect(PotionEffectType.HUNGER)) {
        return;
      }

      if (!getConfig().useConsumable) {
        if (p.getFoodLevel() == 0) {
          if (getConfig().poisonPenalty) {
            addPotionStacks(p, PotionEffectType.POISON, getConfig().basePoisonFromLevel - getLevel(p), getConfig().baseHungerDuration, getConfig().stackPoisonPenalty);
            addPotionStacks(p, PotionEffectType.UNLUCK, getConfig().basePoisonFromLevel - getLevel(p), getConfig().baseHungerDuration, getConfig().stackPoisonPenalty);
          }
          starveFx(p);

        } else {
          addPotionStacks(p, PotionEffectType.HUNGER, getConfig().baseHungerFromLevel - getLevel(p), getConfig().baseHungerDuration * getLevel(p), getConfig().stackHungerPenalty);
          addPotionStacks(p, PotionEffectType.LUCK, getLevel(p), getConfig().baseEffectbyLevel * getLevel(p), getConfig().stackBuff);
          addStat(p, "hunter.luck.activations", 1);
          activateFx(p);
        }
      } else {
        if (getConfig().consumable != null && Material.getMaterial(getConfig().consumable) != null) {
          Material mat = Material.getMaterial(getConfig().consumable);
          if (mat != null && p.getInventory().contains(mat)) {
            p.getInventory().removeItem(new ItemStack(mat, 1));
            addPotionStacks(p, PotionEffectType.LUCK, getLevel(p), getConfig().baseEffectbyLevel * getLevel(p), getConfig().stackBuff);
            addStat(p, "hunter.luck.activations", 1);
            activateFx(p);
          } else {
            if (getConfig().poisonPenalty) {
              addPotionStacks(p, PotionEffectType.POISON, getConfig().basePoisonFromLevel - getLevel(p), getConfig().baseHungerDuration, getConfig().stackPoisonPenalty);
              addPotionStacks(p, PotionEffectType.UNLUCK, getConfig().basePoisonFromLevel - getLevel(p), getConfig().baseHungerDuration, getConfig().stackPoisonPenalty);
            }
            starveFx(p);
          }
        }
      }
    }
  }

  private void activateFx(Player p) {
    if (!fxCooldown.isReady(p.getUniqueId(), 1200L)) {
      return;
    }
    fxCooldown.mark(p.getUniqueId());
    Location loc = p.getLocation().add(0, 1.0D, 0);
    fx(loc, FxPriority.TRANSITION)
        .particle(Particles.VILLAGER_HAPPY, 12, 0, 0, 0, 0.4D, 0)
        .dustHelix(LUCK_GOLD, 0.6D, 1.4D, 8, 0, 1.0F)
        .chord(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5F, 1.3F, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.4F, 1.9F);
  }

  private void starveFx(Player p) {
    if (!fxCooldown.isReady(p.getUniqueId(), 1200L)) {
      return;
    }
    fxCooldown.mark(p.getUniqueId());
    FxPresets.failFizzle(this, p);
  }

  @Override
  public void onTick() {

  }

  @ConfigDescription("Gain luck when struck, at the cost of hunger.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Use Consumable for the Hunter Luck adaptation.", impact = "True enables this behavior and false disables it.")
    boolean useConsumable = false;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Poison Penalty for the Hunter Luck adaptation.", impact = "True enables this behavior and false disables it.")
    boolean poisonPenalty = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Stack Hunger Penalty for the Hunter Luck adaptation.", impact = "True enables this behavior and false disables it.")
    boolean stackHungerPenalty = false;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Stack Poison Penalty for the Hunter Luck adaptation.", impact = "True enables this behavior and false disables it.")
    boolean stackPoisonPenalty = false;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Stack Buff for the Hunter Luck adaptation.", impact = "True enables this behavior and false disables it.")
    boolean stackBuff = false;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Base Effectby Level for the Hunter Luck adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int baseEffectbyLevel = 100;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Base Hunger From Level for the Hunter Luck adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int baseHungerFromLevel = 10;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Base Hunger Duration for the Hunter Luck adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int baseHungerDuration = 50;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Base Poison From Level for the Hunter Luck adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int basePoisonFromLevel = 6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Consumable for the Hunter Luck adaptation.", impact = "Changing this alters the identifier or text used by the feature.")
    String consumable = "ROTTEN_FLESH";

    public Config() {
      costFactor = 0.4;
      initialCost = 8;
    }
  }
}
