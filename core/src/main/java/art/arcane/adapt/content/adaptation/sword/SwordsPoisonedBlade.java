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

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
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
import art.arcane.adapt.content.adaptation.sword.effects.DamagingBleedEffect;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class SwordsPoisonedBlade extends SimpleAdaptation<SwordsPoisonedBlade.Config> {
  private static final Rgb VENOM = Rgb.of(0x6EDB4C);
  private static final long KILL_CREDIT_GRACE_MS = 4000L;
  private static final Id POISON = Id.minecraft("poison");
  private static final Id BLEED_MATERIAL = Id.minecraft("large_fern");
  private static final Set<Id> POISON_IMMUNE_TYPES = Set.of(
      Id.minecraft("skeleton_horse"), Id.minecraft("zombie_horse"), Id.minecraft("phantom"), Id.minecraft("wither"),
      Id.minecraft("zoglin"), Id.minecraft("giant"));
  private final Cooldowns cooldowns = cooldowns();
  private final Map<UUID, PoisonMark> poisonSource = playerState();

  public SwordsPoisonedBlade() {
    super("sword-poison-blade");
    registerConfiguration(Config.class);
    setLocalizationKey("sword.poisoned_blade");
    setIcon(Id.minecraft("green_dye"));
    setInterval(4984);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("spider_eye"))
        .key("challenge_swords_poison_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_swords_poison_500", "swords.poisoned-blade.poison-applied", 500, 400);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("fermented_spider_eye"))
        .key("challenge_swords_poison_kills_50")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_swords_poison_kills_50", "swords.poisoned-blade.poison-kills", 50, 1000);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, SwordPreferences.TARGETS);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, "", 1);
    statLore(v, C.YELLOW, "* ", Form.duration(getDurationOfEffect(level), 1), 2);
    statLore(v, C.RED, "* ", Form.duration(getCooldown(level), 1), 3);
  }

  public long getCooldown(int level) {
    return Math.max(getConfig().cooldown, getDurationOfEffect(level));
  }

  public long getDurationOfEffect(int level) {
    return getConfig().effectDuration * level;
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityDamageByEntityEvent e) {
    if (e.cause() != DamageCause.ENTITY_ATTACK
        && e.cause() != DamageCause.ENTITY_SWEEP_ATTACK) {
      return;
    }
    if (e.damager() instanceof PlayerRef p && hasActiveAdaptation(p) && isSword(p.inventory().mainHand())) {
      UUID id = p.uuid();
      long cooldownMillis = getCooldown(getLevel(p));
      if (!cooldowns.isReady(id, cooldownMillis)) {
        return;
      }
      EntityRef victim = e.entity();
      if (!(victim instanceof LivingRef eligible) || !preference(p, SwordPreferences.TARGETS).accepts(eligible)
          || !canDamageTarget(p, victim)) {
        return;
      }
      cooldowns.mark(id, cooldownMillis);
      if (victim instanceof PlayerRef pvic) {
        startBleedVisual(pvic, p);
        addPotionStacks(pvic, POISON, 2, 50 * getLevel(p), true);
      } else {
        if (isPoisonImmune(eligible)) {
          DamagingBleedEffect.DamageContext context = new DamagingBleedEffect.DamageContext(1D, eligible, p);
          new DamagingBleedEffect(context).start(BLEED_MATERIAL, bleedIterations(p), 5);
        } else {
          startBleedVisual(eligible, p);
          eligible.addEffect(EffectInstance.of(POISON, Math.max(1, 50 * getLevel(p)), 2));
        }
      }
      long now = System.currentTimeMillis();
      pruneExpired(now);
      poisonSource.put(victim.uuid(), new PoisonMark(id, now + getDurationOfEffect(getLevel(p)) + KILL_CREDIT_GRACE_MS));
      addStat(p, "swords.poisoned-blade.poison-applied", 1);
      fx(victim.location().add(0, 1, 0), FxPriority.COMBAT)
          .dustBurst(VENOM, 10, 0.3D, 1.0F)
          .particle(Particles.SNEEZE, 3, 0, 0, 0, 0.05D, 0.02D)
          .chord(Sounds.ENTITY_BEE_STING, 0.5F, 1.3F, Sounds.ENTITY_WITCH_THROW, 0.35F, 1.5F);
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDeathEvent e) {
    UUID victimId = e.entity().uuid();
    PoisonMark mark = poisonSource.remove(victimId);
    if (mark == null || mark.expiresAt() < System.currentTimeMillis()) {
      return;
    }
    PlayerRef source = AdaptCore.platform().host().player(mark.source());
    if (source != null) {
      Tasks.runEntity(source, () -> {
        if (source.isOnline()) {
          addStat(source, "swords.poisoned-blade.poison-kills", 1);
        }
      });
    }
    fx(e.entity().location().add(0, 0.8D, 0), FxPriority.TRANSITION)
        .particle(Particles.SMOKE, 6, 0, 0, 0, 0.05D, 0.01D)
        .dustBurst(VENOM, 6, 0.35D, 1.0F)
        .sound(Sounds.ENTITY_SPIDER_DEATH, 0.5F, 1.4F);
  }

  private void startBleedVisual(EntityRef victim, PlayerRef source) {
    DamagingBleedEffect.startVisual(victim, BLEED_MATERIAL, bleedIterations(source), 5);
  }

  private int bleedIterations(PlayerRef source) {
    return Math.toIntExact(2 * (3 + (getDurationOfEffect(getLevel(source)) / 1000)));
  }

  static boolean isPoisonImmune(LivingRef living) {
    return living.has(EntityTrait.ZOMBIE)
        || living.has(EntityTrait.SKELETON)
        || living.has(EntityTrait.SPIDER)
        || POISON_IMMUNE_TYPES.contains(living.type());
  }

  private void pruneExpired(long now) {
    poisonSource.values().removeIf(mark -> mark.expiresAt() < now);
  }

  private record PoisonMark(UUID source, long expiresAt) {
  }



  @ConfigDescription("Sword strikes apply poison.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown for the Swords Poisoned Blade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    public long cooldown = 5000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Effect Duration for the Swords Poisoned Blade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    public long effectDuration = 1000;

    public Config() {
      baseCost = 3;
      costFactor = 0.6;
      maxLevel = 7;
      initialCost = 2;
    }
  }
}
