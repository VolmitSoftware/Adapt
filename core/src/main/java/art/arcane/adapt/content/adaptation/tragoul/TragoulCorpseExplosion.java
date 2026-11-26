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

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.FxTimeline;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.golem.GolemKillCredit;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.TragoulMessages;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;

import java.util.List;

public class TragoulCorpseExplosion extends SimpleAdaptation<TragoulCorpseExplosion.Config> {
  private static final Rgb NOVA_CRIMSON = new Rgb(150, 12, 12);
  private static final Rgb NOVA_BONE = new Rgb(200, 190, 170);
  private final TragoulNova nova = new TragoulNova(this, new TragoulNova.Settings() {
    @Override
    public boolean ignorePassiveMobs() {
      return getConfig().ignorePassiveMobs;
    }

    @Override
    public long chainSuppressionMillis() {
      return getConfig().chainSuppressionMillis;
    }

    @Override
    public int maxTargets() {
      return getConfig().maxTargets;
    }

    @Override
    public double radius(int level) {
      return getRadius(level);
    }

    @Override
    public double damage(int level, double victimMaxHealth) {
      return getNovaDamage(level, victimMaxHealth);
    }
  }, (PlayerRef owner, List<LivingRef> hits) -> {
    addStat(owner, "tragoul.corpse-explosion.mobs-detonated", hits.size());
    xp(owner, hits.size() * getConfig().xpPerMobHit);
  });

  public TragoulCorpseExplosion() {
    super("tragoul-corpse-explosion");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("wither_rose"));
    setInterval(25000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("wither_rose"))
        .key("challenge_tragoul_corpse_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("netherrack"))
            .key("challenge_tragoul_corpse_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_tragoul_corpse_500", "tragoul.corpse-explosion.mobs-detonated", 500, 400);
    registerMilestone("challenge_tragoul_corpse_5k", "tragoul.corpse-explosion.mobs-detonated", 5000, 1500);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, TragoulPreferences.PASSIVE, TragoulPreferences.TARGETS);
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(C.GREEN + AdaptLanguage.text(TragoulMessages.CORPSE_EXPLOSION_LORE1));
    statLore(v, Form.f(getRadius(level), 1), 2);
    statLore(v, Form.pc(getVictimHealthFraction(level), 0), 3);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDeathEvent e) {
    PlayerRef killer = e.entity().killer();
    if (killer == null || GolemKillCredit.isGolemKill(e.entity())) {
      return;
    }
    nova.request(killer, e.entity(), false);
  }

  static void detonateServantKill(TragoulCorpseExplosion explosion, PlayerRef owner, LivingRef victim) {
    explosion.nova.request(owner, victim, true);
  }

  private double getRadius(int level) {
    double configured = Math.max(1D, getConfig().radiusBase + (getLevelPercent(level) * getConfig().radiusFactor));
    return Math.min(TragoulNova.MAX_RADIUS, configured);
  }

  private double getVictimHealthFraction(int level) {
    return Math.max(0, getConfig().victimHealthFractionBase + (getLevelPercent(level) * getConfig().victimHealthFractionFactor));
  }

  private double getNovaDamage(int level, double victimMaxHealth) {
    double damage = getConfig().baseDamage + (victimMaxHealth * getVictimHealthFraction(level));
    return Math.min(getConfig().maxDamage, damage);
  }

  static void novaFx(Adaptation<?> owner, Loc center, double radius, boolean servant) {
    Rgb tint = servant ? NOVA_BONE : NOVA_CRIMSON;
    double inner = radius * 0.3;
    int ringPoints = (int) Math.max(12, Math.min(28, radius * 6));
    FxTimeline.at(owner, center)
        .duration(3)
        .priority(FxPriority.GAMEPLAY)
        .cullRadius(radius + 8)
        .frame((fx, tick, progress) -> {
          double r = inner + ((radius - inner) * progress);
          fx.dustRing(tint, r, ringPoints, 1.4F);
          if (tick == 0) {
            fx.particle(Particles.DAMAGE_INDICATOR, 8, 0, 1.0, 0, 0.4, 0.04);
            fx.column(Particles.SOUL, 8, 2.0);
            fx.chord(Sounds.ENTITY_GENERIC_EXPLODE, 0.55F, 1.35F, Sounds.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.5F, 0.8F);
          }
        })
        .start();
  }

  static void novaHitFx(Adaptation<?> owner, LivingRef enemy) {
    Fx.now(owner, enemy.location().add(0, 1.0D, 0), FxPriority.COMBAT)
        .particle(Particles.DAMAGE_INDICATOR, 2, 0, 0, 0, 0.2D, 0.02D);
  }

  @ConfigDescription("Mobs you kill detonate in a blood nova that damages nearby hostile mobs.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Exclude passive and neutral mobs from corpse explosion damage, including servant-triggered explosions.", impact = "When enabled, neutral enemy species remain protected even when provoked.")
    boolean ignorePassiveMobs = false;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base nova radius before level scaling.", impact = "Higher values damage hostile mobs further from the corpse.")
    double radiusBase = 3.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional nova radius granted at max level.", impact = "Higher values increase the level-scaled radius growth.")
    double radiusFactor = 3.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Flat nova damage applied to every hostile mob hit.", impact = "Higher values increase the guaranteed damage portion of the nova.")
    double baseDamage = 3.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Fraction of the victim's max health added to nova damage before level scaling.", impact = "Higher values make tanky kills detonate harder.")
    double victimHealthFractionBase = 0.10;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional victim max-health fraction granted at max level.", impact = "Higher values increase the level-scaled damage growth.")
    double victimHealthFractionFactor = 0.40;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Hard cap on nova damage per mob.", impact = "Prevents extreme bosses from producing one-shot novas.")
    double maxDamage = 24.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum hostile mobs damaged per nova, with a hard runtime ceiling of 16.", impact = "Caps per-kill work to protect server performance.")
    int maxTargets = 12;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Window in milliseconds during which a nova-damaged mob cannot trigger another nova.", impact = "Prevents chain-reaction detonations.")
    long chainSuppressionMillis = 5000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "XP granted per hostile mob hit by a nova.", impact = "Higher values accelerate skill progression from this adaptation.")
    double xpPerMobHit = 6;

    public Config() {
      baseCost = 3;
      costFactor = 0.6;
      initialCost = 2;
    }
  }
}
