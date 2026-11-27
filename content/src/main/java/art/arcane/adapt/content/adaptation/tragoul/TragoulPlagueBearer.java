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

import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;

import java.util.List;

public class TragoulPlagueBearer extends SimpleAdaptation<TragoulPlagueBearer.Config> {
  public static final Id ID = Id.adapt("tragoul/plague_bearer");
  private static final Rgb PLAGUE_POISON = new Rgb(90, 130, 30);
  private static final Rgb PLAGUE_WITHER = new Rgb(30, 30, 30);
  private final TragoulPlague plague = new TragoulPlague(this, new TragoulPlague.Settings() {
    @Override
    public boolean ignorePassiveMobs() {
      return getConfig().ignorePassiveMobs;
    }

    @Override
    public int maxSpreadTargets() {
      return getConfig().maxSpreadTargets;
    }

    @Override
    public int amplifierBonus() {
      return getConfig().amplifierBonus;
    }

    @Override
    public int maxGenerations() {
      return getConfig().maxGenerations;
    }

    @Override
    public long afflictionFreshnessMillis() {
      return getConfig().afflictionFreshnessMillis;
    }

    @Override
    public double spreadRadius(int level) {
      return getSpreadRadius(level);
    }

    @Override
    public int spreadDurationTicks(int level) {
      return getSpreadDurationTicks(level);
    }
  }, (PlayerRef owner, int spread) -> {
    addStat(owner, "tragoul.plague-bearer.mobs-infected", spread);
    xp(owner, spread * Math.max(0D, getConfig().xpPerInfection));
  });

  public TragoulPlagueBearer() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("poisonous_potato"));
    setInterval(25000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("poisonous_potato"))
        .key("challenge_tragoul_plague_100")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("spider_eye"))
            .key("challenge_tragoul_plague_1k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_tragoul_plague_100", "tragoul.plague-bearer.mobs-infected", 100, 400);
    registerMilestone("challenge_tragoul_plague_1k", "tragoul.plague-bearer.mobs-infected", 1000, 1500);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, TragoulPreferences.PASSIVE, TragoulPreferences.TARGETS);
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(TextFormat.GREEN + Text.key("adapt.tragoul.plague_bearer.lore1").resolve());
    statLore(v, Format.f(getSpreadRadius(level), 1), 2);
    statLore(v, Format.duration(getSpreadDurationTicks(level) * 50D, 1), 3);
  }

  @Override
  public void unregister() {
    plague.stop();
    super.unregister();
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDamageByEntityEvent e) {
    plague.mark(e);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDeathEvent e) {
    plague.spread(e);
  }

  private double getSpreadRadius(int level) {
    return TragoulPlague.spreadRadius(TragoulPlague.scaledRadius(getConfig().spreadRadiusStart,
        getConfig().spreadRadiusEnd, level, getMaxLevel()));
  }

  private int getSpreadDurationTicks(int level) {
    return TragoulPlague.durationTicks(getConfig().spreadDurationTicksBase
        + (getLevelPercent(level) * getConfig().spreadDurationTicksFactor));
  }

  static void exhaustedFx(Adaptation<?> owner, LivingRef victim) {
    Fx.now(owner, victim.location().add(0, 0.8, 0), FxPriority.AMBIENT).burst(Particles.SMOKE, 2, 0.2);
  }

  static void spreadFx(Adaptation<?> owner, Loc corpse, List<Loc> hosts, double radius, boolean withered) {
    FxEmitter emitter = Fx.now(owner, corpse.add(0, 0.8, 0), FxPriority.GAMEPLAY);
    emitter.dustBurst(withered ? PLAGUE_WITHER : PLAGUE_POISON, 8, radius * 0.35, 1.2F);
    emitter.particle(Particles.SPORE_BLOSSOM_AIR, 6, 0, 0.6, 0, radius * 0.3, 0.02);
    for (Loc host : hosts) {
      emitter.line(Particles.SPORE_BLOSSOM_AIR, host.x(), host.y() + 1.0, host.z(), 4);
    }
    emitter.chord(Sounds.ENTITY_ZOMBIE_INFECT, 0.6F, 1.4F, Sounds.BLOCK_SCULK_CATALYST_BLOOM, 0.4F, 1.0F);
  }

  static void markFx(Adaptation<?> owner, MobRef mob) {
    Fx.now(owner, mob.location().add(0, 1.0, 0), FxPriority.AMBIENT)
        .particle(Particles.SPORE_BLOSSOM_AIR, 2, 0, 0, 0, 0.2, 0.01);
  }

  @ConfigDescription("Mobs that die poisoned or withered by you spread an amplified affliction to nearby damageable mobs.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Exclude passive and neutral mobs from spreading poison and wither.", impact = "When enabled, protected mobs cannot receive secondary infections. Direct attacks and directly applied effects are unchanged.")
    boolean ignorePassiveMobs = false;
    @ConfigDoc(value = "Spread radius at adaptation level one.", impact = "Higher values infect eligible mobs further from the corpse at low levels.")
    double spreadRadiusStart = 8;
    @ConfigDoc(value = "Spread radius at maximum adaptation level.", impact = "Higher values increase the maximum plague reach up to the runtime ceiling.")
    double spreadRadiusEnd = 20;
    @ConfigDoc(value = "Spread effect duration in ticks before level scaling.", impact = "Higher values keep infected mobs afflicted longer.")
    double spreadDurationTicksBase = 80;
    @ConfigDoc(value = "Additional spread effect duration ticks granted at max level.", impact = "Higher values increase level-scaled duration growth.")
    double spreadDurationTicksFactor = 120;
    @ConfigDoc(value = "Maximum plague generations a single affliction can chain through.", impact = "Caps chain length to prevent infinite plague cascades.")
    int maxGenerations = 3;
    @ConfigDoc(value = "Maximum eligible mobs infected per death.", impact = "Caps per-death work to protect server performance.")
    int maxSpreadTargets = 6;
    @ConfigDoc(value = "Potion amplifier levels added when the affliction spreads.", impact = "Higher values make propagated poison and wither effects stronger.")
    int amplifierBonus = 1;
    @ConfigDoc(value = "Window in milliseconds during which an affliction mark is considered fresh at death.", impact = "Higher values let older poisons still spread on death.")
    long afflictionFreshnessMillis = 15000;
    @ConfigDoc(value = "XP granted per mob infected by the spread.", impact = "Higher values accelerate skill progression from this adaptation.")
    double xpPerInfection = 6;

    public Config() {
      baseCost = 3;
      costFactor = 0.6;
      initialCost = 2;
    }
  }
}
