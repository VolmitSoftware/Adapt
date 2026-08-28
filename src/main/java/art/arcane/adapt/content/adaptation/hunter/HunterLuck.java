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
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class HunterLuck extends SimpleAdaptation<HunterLuck.Config> {
  private static final Color LUCK_GOLD = Color.fromRGB(255, 215, 0);
  private final Cooldowns fxCooldown = cooldowns();
  private final Map<UUID, Long> luckUntil = playerState();
  private final Map<UUID, Long> unluckUntil = playerState();

  public HunterLuck() {
    super("hunter-luck");
    registerConfiguration(Config.class);
    setIcon(Material.TADPOLE_BUCKET);
    setInterval(9644);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.EMERALD)
        .key("challenge_hunter_luck_200")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_hunter_luck_200", "hunter.luck.activations", 200, 300);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GRAY + AdaptLanguage.text(HunterMessages.LUCK_LORE1));
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
      } else if (applyLuckBuff(p, level)) {
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
    if (!canApplyLuckBuff(p, level) || !activateWithConsumable(p, material, level)) {
      return;
    }
    recordActivation(p);
  }

  private boolean activateWithConsumable(Player p, Material material, int level) {
    AtomicBoolean defaultApplied = new AtomicBoolean();
    AbilityCharge charge = payItemCostDeferred(p, "consumable", new ItemStack(material), 1, () -> {
      if (!p.getInventory().contains(material) || !applyLuckBuff(p, level)) {
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
    if (!applyLuckBuff(p, level)) {
      refundCost(charge.activationId(), AbilityRefundReason.ACTIVATION_FAILED);
      return false;
    }
    settleCost(charge.activationId());
    return true;
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerDeathEvent e) {
    luckUntil.remove(e.getEntity().getUniqueId());
    unluckUntil.remove(e.getEntity().getUniqueId());
  }

  private boolean canApplyLuckBuff(Player p, int level) {
    if (Attributes.LUCK == null || p.hasPotionEffect(PotionEffectType.LUCK)) {
      return false;
    }

    boolean stackBuff = getConfig().stackBuff;
    long now = System.currentTimeMillis();
    Long until = luckUntil.get(p.getUniqueId());
    long remainingTicks = until == null ? 0L : Math.max(0L, (until - now) / 50L);
    if (remainingTicks > 0L && !stackBuff) {
      return false;
    }

    return buffDurationTicks(getConfig().baseEffectbyLevel, level, remainingTicks, stackBuff) > 0L;
  }

  private boolean applyLuckBuff(Player p, int level) {
    if (!canApplyLuckBuff(p, level)) {
      return false;
    }

    boolean stackBuff = getConfig().stackBuff;
    long now = System.currentTimeMillis();
    Long until = luckUntil.get(p.getUniqueId());
    long remainingTicks = until == null ? 0L : Math.max(0L, (until - now) / 50L);
    long durationTicks = buffDurationTicks(getConfig().baseEffectbyLevel, level, remainingTicks, stackBuff);
    AdaptAttributeService.get().applyTimed(p, getName(), "luck", Attributes.LUCK, luckBonus(level), AttributeModifier.Operation.ADD_NUMBER, durationTicks);
    luckUntil.put(p.getUniqueId(), now + (durationTicks * 50L));
    return true;
  }

  private void recordActivation(Player p) {
    addStat(p, "hunter.luck.activations", 1);
    activateFx(p);
  }

  private void starvePenalty(Player p, int level) {
    if (getConfig().poisonPenalty) {
      addPotionStacks(p, PotionEffectType.POISON, getConfig().basePoisonFromLevel - level,
          getConfig().baseHungerDuration, getConfig().stackPoisonPenalty);
      applyUnluckPenalty(p, level);
    }
    starveFx(p);
  }

  private void applyUnluckPenalty(Player p, int level) {
    if (p.hasPotionEffect(PotionEffectType.UNLUCK)) {
      return;
    }

    boolean stackPenalty = getConfig().stackPoisonPenalty;
    long now = System.currentTimeMillis();
    Long until = unluckUntil.get(p.getUniqueId());
    long remainingTicks = until == null ? 0L : Math.max(0L, (until - now) / 50L);
    if (remainingTicks > 0L && !stackPenalty) {
      return;
    }

    long durationTicks = penaltyDurationTicks(getConfig().baseHungerDuration, remainingTicks, stackPenalty);
    if (durationTicks <= 0L) {
      return;
    }

    AdaptAttributeService.get().applyTimed(p, getName(), "unluck", Attributes.LUCK,
        unluckAmount(getConfig().basePoisonFromLevel, level), AttributeModifier.Operation.ADD_NUMBER, durationTicks);
    unluckUntil.put(p.getUniqueId(), now + (durationTicks * 50L));
  }

  static double luckBonus(int level) {
    return level + 1.0D;
  }

  static double unluckAmount(int basePoisonFromLevel, int level) {
    return -(basePoisonFromLevel - level + 1.0D);
  }

  static long buffDurationTicks(int baseTicksPerLevel, int level, long remainingTicks, boolean stackBuff) {
    long base = (long) baseTicksPerLevel * level;
    if (base <= 0L) {
      return 0L;
    }
    return base + (stackBuff ? Math.max(0L, remainingTicks) : 0L);
  }

  static long penaltyDurationTicks(int baseTicks, long remainingTicks, boolean stackPenalty) {
    if (baseTicks <= 0) {
      return 0L;
    }
    return baseTicks + (stackPenalty ? Math.max(0L, remainingTicks) : 0L);
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
