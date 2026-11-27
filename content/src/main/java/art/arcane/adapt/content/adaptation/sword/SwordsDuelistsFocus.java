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

package art.arcane.adapt.content.adaptation.sword;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.Cooldowns;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.item.ItemKinds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;

import java.util.List;

public class SwordsDuelistsFocus extends SimpleAdaptation<SwordsDuelistsFocus.Config> {
  public static final Id ID = Id.adapt("sword/duelists_focus");
  private static final Rgb FOCUS = Rgb.of(0x59C0FF);
  private static final Id GLOWING = Id.minecraft("glowing");
  private final Cooldowns fxThrottle = cooldowns();

  public SwordsDuelistsFocus() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("shield"));
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("iron_sword"))
        .key("challenge_swords_duelist_200")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("netherite_sword"))
            .key("challenge_swords_duelist_2500")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_swords_duelist_200", "swords.duelists-focus.focused-hits", 200, 400);
    registerMilestone("challenge_swords_duelist_2500", "swords.duelists-focus.focused-hits", 2500, 1500);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, SwordPreferences.GLOW);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Format.pc(getBonusDamage(level), 0), 1);
    statLore(v, Format.pc(getDamageReduction(level), 0), 2);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityDamageByEntityEvent e) {
    MeleeContext combat = resolveMeleeContext(e, ItemKinds::isSword);
    if (combat != null && countEngaged(combat.attacker()) == 1) {
      PlayerRef attacker = combat.attacker();
      e.setDamage(e.damage() * (1D + getBonusDamage(combat.level())));
      xp(attacker, getConfig().xpPerFocusedHit);
      addStat(attacker, "swords.duelists-focus.focused-hits", 1);
      focusFx(attacker, true);
    }

    if (e.entity() instanceof PlayerRef victim) {
      int level = getActiveLevel(victim);
      if (level > 0 && ItemKinds.isSword(victim.inventory().mainHand()) && countEngaged(victim) == 1) {
        e.setDamage(Math.max(0D, e.damage() * (1D - getDamageReduction(level))));
        focusFx(victim, false);
        if (preferenceEnabled(victim, SwordPreferences.GLOW)) {
          showThreatGlow(resolveThreat(e.damager()));
        }
      }
    }
  }

  private int countEngaged(PlayerRef p) {
    return SwordDuels.countEngaged(p, getConfig().engageRadius);
  }

  private void focusFx(PlayerRef p, boolean offensive) {
    if (!fxThrottle.isReady(p.uuid(), 260L)) {
      return;
    }

    fxThrottle.mark(p.uuid());
    if (offensive) {
      fx(p.location().add(0, 1, 0), FxPriority.AMBIENT)
          .dustBurst(FOCUS, 3, 0.25D, 0.9F)
          .particle(Particles.CRIT, 4, 0, 0, 0, 0.2D, 0.05D)
          .sound(Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.3F, 1.6F);
    } else {
      fx(p.location().add(0, 1, 0), FxPriority.COMBAT)
          .dustRing(FOCUS, 0.9D, 8, 0.9F)
          .sound(Sounds.ITEM_SHIELD_BLOCK, 0.4F, 1.4F);
    }
  }

  static LivingRef resolveThreat(EntityRef damager) {
    if (damager instanceof ProjectileRef projectile) {
      EntityRef shooter = projectile.shooter();
      if (shooter instanceof LivingRef living) {
        return living;
      }
    }
    return damager instanceof LivingRef living ? living : null;
  }

  private void showThreatGlow(LivingRef threat) {
    if (threat == null) {
      return;
    }
    AdaptApi.scheduler().runEntity(threat, () -> {
      if (!threat.isValid() || threat.isDead()) {
        return;
      }
      int glowTicks = Math.max(1, Math.min(getConfig().threatGlowTicks, 100));
      EffectInstance current = threat.effect(GLOWING);
      if (current == null || current.durationTicks() < glowTicks) {
        threat.addEffect(EffectInstance.of(GLOWING, glowTicks, 0).withParticles(false).withIcon(false));
      }
    });
  }

  private double getBonusDamage(int level) {
    return getConfig().bonusDamageBase + (getLevelPercent(level) * getConfig().bonusDamageFactor);
  }

  private double getDamageReduction(int level) {
    return Math.min(getConfig().maxReduction, getConfig().reductionBase + (getLevelPercent(level) * getConfig().reductionFactor));
  }


  @ConfigDescription("Bonus damage and damage reduction while exactly one hostile is engaged with you.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Controls Bonus Damage Base for the Swords Duelists Focus adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double bonusDamageBase = 0.10;
    @ConfigDoc(value = "Controls Bonus Damage Factor for the Swords Duelists Focus adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double bonusDamageFactor = 0.35;
    @ConfigDoc(value = "Controls Reduction Base for the Swords Duelists Focus adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double reductionBase = 0.08;
    @ConfigDoc(value = "Controls Reduction Factor for the Swords Duelists Focus adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double reductionFactor = 0.30;
    @ConfigDoc(value = "Controls Max Reduction for the Swords Duelists Focus adaptation.", impact = "Caps damage reduction at the requested maximum.")
    double maxReduction = 0.40;
    @ConfigDoc(value = "Radius in blocks used to detect engaged hostiles.", impact = "Higher values count hostiles from farther away when deciding if you are in a duel.")
    double engageRadius = 7;
    @ConfigDoc(value = "Ticks the current dueling threat glows after it attacks you.", impact = "Higher values keep the defensive focus notification visible longer.")
    int threatGlowTicks = 30;
    @ConfigDoc(value = "Controls Xp Per Focused Hit for the Swords Duelists Focus adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerFocusedHit = 4;

    public Config() {
      baseCost = 3;
      costFactor = 0.6;
      maxLevel = 5;
      initialCost = 2;
    }
  }
}
