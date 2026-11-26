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
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.TragoulMessages;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.event.player.PlayerDeathEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.fx.ParticleData;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;

import java.util.List;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

public class TragoulLance extends SimpleAdaptation<TragoulLance.Config> {
  private static final Rgb LANCE_MAROON = new Rgb(128, 0, 0);
  private static final ParticleData.Dust LANCE_TIP = new ParticleData.Dust(new Rgb(235, 20, 35), 1.35F);
  private final TragoulLances lances = new TragoulLances(this, new TragoulLances.Settings() {
    @Override
    public boolean ignorePassiveMobs() {
      return getConfig().ignorePassiveMobs;
    }

    @Override
    public int seekerDelay() {
      return getConfig().seekerDelay;
    }

    @Override
    public double seekerDamageMultiplier() {
      return getConfig().seekerDamageMultiplier;
    }

    @Override
    public double unarmoredDamageMultiplier() {
      return getConfig().unarmoredDamageMultiplier;
    }

    @Override
    public double selfDamage(int level) {
      return TragoulLances.selfDamageForLevel(level, getMaxLevel(), getConfig().selfDamageAtFirstLevel,
          getConfig().selfDamageAtMaxLevel);
    }
  }, cooldowns(), (PlayerRef owner, boolean killed) -> {
    if (killed) {
      addStat(owner, "tragoul.lance.lance-kills", 1);
    }
    addStat(owner, "tragoul.lance.lances-spawned", 1);
  }, this::applyPlayerDamage);

  public TragoulLance() {
    super("tragoul-lance");
    registerConfiguration(TragoulLance.Config.class);
    setIcon(Id.minecraft("trident"));
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("iron_sword"))
        .key("challenge_tragoul_lance_200")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("diamond_sword"))
        .key("challenge_tragoul_lance_kills_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_tragoul_lance_200", "tragoul.lance.lances-spawned", 200, 400);
    registerMilestone("challenge_tragoul_lance_kills_100", "tragoul.lance.lance-kills", 100, 1000);
  }

  @Override
  protected boolean shouldCanonicalizeConfigOnLoad() {
    return true;
  }

  @Override
  public void unregister() {
    lances.stop();
    super.unregister();
  }

  @Handle(priority = EventPriority.LOWEST)
  public void onEntityDeath(EntityDeathEvent event) {
    lances.launch(event);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent event) {
    lances.cancel(event.player().uuid());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerDeathEvent event) {
    lances.cancel(event.player().uuid());
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, TragoulPreferences.PASSIVE, TragoulPreferences.TARGETS);
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(C.GREEN + AdaptLanguage.text(TragoulMessages.LANCE_LORE1));
    v.addLore(C.YELLOW + AdaptLanguage.text(TragoulMessages.LANCE_LORE2));
    v.addLore(C.YELLOW + AdaptLanguage.text(
        TragoulMessages.LANCE_MAX,
        trusted("level", level)
    ));
  }

  static void launchFx(Adaptation<?> owner, Loc origin) {
    Fx.now(owner, origin.add(0, 1.0, 0), FxPriority.COMBAT)
        .burst(Particles.SCULK_SOUL, 6, 0.3)
        .chord(Sounds.ITEM_TRIDENT_RIPTIDE_1, 0.6F, 1.3F, Sounds.ENTITY_WITHER_SHOOT, 0.3F, 1.8F);
  }

  static void travelFx(Adaptation<?> owner, Loc origin, Loc target, int delayTicks) {
    Loc start = origin.add(0, 1.0, 0);
    double dx = target.x() - start.x();
    double dy = (target.y() + 1.0) - start.y();
    double dz = target.z() - start.z();
    FxTimeline.at(owner, start)
        .duration(Math.max(4, delayTicks))
        .priority(FxPriority.TRAIL)
        .cullRadius(32)
        .frame((fx, tick, progress) -> {
          fx.particle(Particles.DUST, 2, dx * progress, dy * progress, dz * progress, 0.025D, 0D, LANCE_TIP);
          double tail = Math.max(0D, progress - 0.08D);
          fx.particle(Particles.END_ROD, 2, dx * tail, dy * tail, dz * tail, 0.02D, 0D);
        })
        .start();
  }

  static void hitFx(Adaptation<?> owner, LivingRef target, float pitch) {
    Fx.now(owner, target, FxPriority.COMBAT)
        .burst(Particles.CRIT, 8, 0.4)
        .dustBurst(LANCE_MAROON, 6, 0.4, 1.0F)
        .chord(Sounds.ITEM_TRIDENT_HIT, 0.7F, pitch, Sounds.ENTITY_ARROW_HIT, 0.4F, 0.7F);
  }

  @ConfigDescription("Killing an enemy spawns a lance that seeks and damages a nearby enemy.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Exclude passive and neutral mobs from every seeking lance and chain hit.", impact = "When enabled, lances skip protected mobs and can seek hostile mobs farther away. Direct attacks and player targeting are unchanged.")
    boolean ignorePassiveMobs = false;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Seeker Delay for the Tragoul Lance adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int seekerDelay = 12;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Seeker Damage Multiplier for the Tragoul Lance adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double seekerDamageMultiplier = 1.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Flat damage dealt to the caster when a level-one lance hits.", impact = "Higher values make early corpse lances cost more after absorption, armor, and effects.")
    double selfDamageAtFirstLevel = 6.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Flat damage dealt to the caster when a max-level lance hits.", impact = "Lower values make leveling reduce the mitigated corpse lance cost further.")
    double selfDamageAtMaxLevel = 2.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Damage multiplier applied when the owner has no armor equipped.", impact = "Higher values reward fighting without any equipped armor.")
    double unarmoredDamageMultiplier = 3.0;

    public Config() {
      baseCost = 3;
      costFactor = 0.6;
      initialCost = 2;
    }
  }
}
