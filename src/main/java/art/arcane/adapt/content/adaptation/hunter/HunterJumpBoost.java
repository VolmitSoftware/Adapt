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

import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.HunterMessages;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.ability.AbilityCharge;
import art.arcane.adapt.api.ability.AbilityRefundReason;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.fx.FxPresets;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Attributes;
import art.arcane.adapt.util.reflect.registries.PotionEffectTypes;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class HunterJumpBoost extends SimpleAdaptation<HunterJumpBoost.Config> {
  private final Cooldowns fxCooldown = cooldowns();
  private final Map<UUID, Long> jumpUntil = playerState();

  public HunterJumpBoost() {
    super("hunter-jumpboost");
    registerConfiguration(Config.class);
    setLocalizationKey("hunter.jump_boost");
    setIcon(Material.PUFFERFISH_BUCKET);
    setInterval(9544);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.RABBIT_FOOT)
        .key("challenge_hunter_jump_200")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_hunter_jump_200", "hunter.jump-boost.activations", 200, 300);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GRAY + AdaptLanguage.text(HunterMessages.JUMP_BOOST_LORE1));
    statLore(v, level, 2);
    statLore(v, C.RED, "- ", (5 + level), 3);
    statLore(v, C.GRAY, "* ", level, 4);
    statLore(v, C.GRAY, "* ", level, 5);
    statLore(v, C.GRAY, "- ", level, C.RED, HunterMessages.PENALTY_LORE1);

  }


  @EventHandler(ignoreCancelled = true)
  public void on(EntityDamageEvent e) {
    if (!(e.getEntity() instanceof Player p) || !isAdaptableDamageCause(e)) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0
        || (AdaptConfig.get().isPreventHunterSkillsWhenHungerApplied()
        && p.hasPotionEffect(PotionEffectType.HUNGER))) {
      return;
    }

    if (!getConfig().useConsumable) {
      if (p.getFoodLevel() == 0) {
        starvePenalty(p, level);
      } else if (applyJumpBuff(p, level)) {
        addPotionStacks(p, PotionEffectType.HUNGER, getConfig().baseHungerFromLevel - level,
            getConfig().baseHungerDuration * level, getConfig().stackHungerPenalty);
        recordActivation(p);
      }
      return;
    }

    Material material = getConfig().consumable == null ? null : Material.getMaterial(getConfig().consumable);
    if (material == null) {
      return;
    }
    if (!p.getInventory().contains(material)) {
      starvePenalty(p, level);
      return;
    }
    if (!canApplyJumpBuff(p, level) || !activateWithConsumable(p, material, level)) {
      return;
    }
    recordActivation(p);
  }

  private boolean activateWithConsumable(Player p, Material material, int level) {
    AtomicBoolean defaultApplied = new AtomicBoolean();
    AbilityCharge charge = payItemCostDeferred(p, "consumable", new ItemStack(material), 1, () -> {
      if (!p.getInventory().contains(material) || !applyJumpBuff(p, level)) {
        return false;
      }
      p.getInventory().removeItem(new ItemStack(material, 1));
      defaultApplied.set(true);
      return true;
    });
    if (!charge.allowed()) {
      return false;
    }
    if (defaultApplied.get()) {
      settleCost(charge.activationId());
      return true;
    }
    if (!applyJumpBuff(p, level)) {
      refundCost(charge.activationId(), AbilityRefundReason.ACTIVATION_FAILED);
      return false;
    }
    settleCost(charge.activationId());
    return true;
  }

  private boolean canApplyJumpBuff(Player p, int level) {
    if (Attributes.JUMP_STRENGTH == null || Attributes.SAFE_FALL_DISTANCE == null) {
      return false;
    }

    PotionEffectType jumpBoost = PotionEffectTypes.JUMP;
    if (jumpBoost != null && p.hasPotionEffect(jumpBoost)) {
      return false;
    }

    boolean stackBuff = getConfig().stackBuff;
    long now = System.currentTimeMillis();
    Long until = jumpUntil.get(p.getUniqueId());
    long remainingTicks = until == null ? 0L : Math.max(0L, (until - now) / 50L);
    if (remainingTicks > 0L && !stackBuff) {
      return false;
    }

    return buffDurationTicks(getConfig().baseEffectbyLevel, level, remainingTicks, stackBuff) > 0L;
  }

  private boolean applyJumpBuff(Player p, int level) {
    if (!canApplyJumpBuff(p, level)) {
      return false;
    }

    boolean stackBuff = getConfig().stackBuff;
    long now = System.currentTimeMillis();
    Long until = jumpUntil.get(p.getUniqueId());
    long remainingTicks = until == null ? 0L : Math.max(0L, (until - now) / 50L);
    long durationTicks = buffDurationTicks(getConfig().baseEffectbyLevel, level, remainingTicks, stackBuff);
    AdaptAttributeService attributes = AdaptAttributeService.get();
    attributes.applyTimed(p, getName(), "jump", Attributes.JUMP_STRENGTH, jumpStrengthBonus(level), AttributeModifier.Operation.ADD_NUMBER, durationTicks);
    attributes.applyTimed(p, getName(), "fall", Attributes.SAFE_FALL_DISTANCE, safeFallBonus(level), AttributeModifier.Operation.ADD_NUMBER, durationTicks);
    jumpUntil.put(p.getUniqueId(), now + (durationTicks * 50L));
    return true;
  }

  private void recordActivation(Player p) {
    addStat(p, "hunter.jump-boost.activations", 1);
    activateFx(p);
  }

  private void starvePenalty(Player p, int level) {
    if (getConfig().poisonPenalty) {
      addPotionStacks(p, PotionEffectType.POISON, getConfig().basePoisonFromLevel - level,
          getConfig().baseHungerDuration, getConfig().stackPoisonPenalty);
    }
    starveFx(p);
  }

  static double jumpStrengthBonus(int level) {
    return 0.1D * (level + 1);
  }

  static double safeFallBonus(int level) {
    return level + 1.0D;
  }

  static long buffDurationTicks(int baseTicksPerLevel, int level, long remainingTicks, boolean stackBuff) {
    long base = (long) baseTicksPerLevel * level;
    if (base <= 0L) {
      return 0L;
    }
    return base + (stackBuff ? Math.max(0L, remainingTicks) : 0L);
  }

  private void activateFx(Player p) {
    if (!fxCooldown.isReady(p.getUniqueId(), 1200L)) {
      return;
    }
    fxCooldown.mark(p.getUniqueId());
    Location feet = p.getLocation();
    fx(feet, FxPriority.TRANSITION)
        .ring(Particle.CLOUD, 0.6D, 10, 0.05D)
        .column(Particle.CLOUD, 6, 1.2D)
        .chord(Sound.BLOCK_NOTE_BLOCK_PLING, 0.7F, 1.2F, Sound.BLOCK_NOTE_BLOCK_BASS, 0.3F, 0.8F);
  }

  private void starveFx(Player p) {
    if (!fxCooldown.isReady(p.getUniqueId(), 1200L)) {
      return;
    }
    fxCooldown.mark(p.getUniqueId());
    FxPresets.failFizzle(this, p);
  }


  @ConfigDescription("Gain jump boost when struck, at the cost of hunger.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Use Consumable for the Hunter Jump Boost adaptation.", impact = "True enables this behavior and false disables it.")
    boolean useConsumable = false;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Poison Penalty for the Hunter Jump Boost adaptation.", impact = "True enables this behavior and false disables it.")
    boolean poisonPenalty = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Stack Hunger Penalty for the Hunter Jump Boost adaptation.", impact = "True enables this behavior and false disables it.")
    boolean stackHungerPenalty = false;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Stack Poison Penalty for the Hunter Jump Boost adaptation.", impact = "True enables this behavior and false disables it.")
    boolean stackPoisonPenalty = false;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Stack Buff for the Hunter Jump Boost adaptation.", impact = "True enables this behavior and false disables it.")
    boolean stackBuff = false;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Base Effectby Level for the Hunter Jump Boost adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int baseEffectbyLevel = 100;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Base Hunger From Level for the Hunter Jump Boost adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int baseHungerFromLevel = 10;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Base Hunger Duration for the Hunter Jump Boost adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int baseHungerDuration = 50;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Base Poison From Level for the Hunter Jump Boost adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int basePoisonFromLevel = 6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Consumable for the Hunter Jump Boost adaptation.", impact = "Changing this alters the identifier or text used by the feature.")
    String consumable = "ROTTEN_FLESH";

    public Config() {
      costFactor = 0.4;
      initialCost = 8;
    }
  }
}
