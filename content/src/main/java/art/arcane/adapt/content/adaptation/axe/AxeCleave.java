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

package art.arcane.adapt.content.adaptation.axe;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.item.ItemKinds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;

import java.util.List;

public class AxeCleave extends SimpleAdaptation<AxeCleave.Config> {
  public static final Id ID = Id.adapt("axe/cleave");
  private final AxeCleaveArc arc = new AxeCleaveArc(this, () -> getConfig().ignorePassiveMobs);

  public AxeCleave() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("diamond_axe"));
    setInterval(9973);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("diamond_axe"))
        .key("challenge_axe_cleave_1k")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("netherite_axe"))
            .key("challenge_axe_cleave_10k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_axe_cleave_1k", "axe.cleave.targets-hit", 1000, 500);
    registerMilestone("challenge_axe_cleave_10k", "axe.cleave.targets-hit", 10000, 1800);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, AxePreferences.IGNORE_PASSIVE, AxePreferences.PLAYERS);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, TextFormat.RED, "+ ", (int) Math.round(getHalfArcDegrees(level) * 2D) + "°", 1);
    statLore(v, TextFormat.RED, "x ", getTargetCap(level), 2);
    statLore(v, TextFormat.RED, "+ ", Format.pc(getDamageShare(level), 0), 3);
  }

  @Handle(priority = EventPriority.HIGH)
  public void on(EntityDamageByEntityEvent e) {
    long now = System.currentTimeMillis();
    if (arc.isCleaving(e.entity().uuid(), now)) {
      return;
    }

    Adaptation.MeleeContext combat = resolveMeleeContext(e, ItemKinds::isAxe);
    if (combat == null) {
      return;
    }

    double baseDamage = e.damage();
    if (baseDamage <= 0D) {
      return;
    }

    PlayerRef p = combat.attacker();
    LivingRef primary = combat.target();
    int level = combat.level();
    double secondary = baseDamage * getDamageShare(level);
    if (secondary <= 0D) {
      return;
    }

    List<LivingRef> targets = arc.targets(p, primary, getRadius(level), getHalfArcDegrees(level), getTargetCap(level),
        preferenceEnabled(p, AxePreferences.PLAYERS),
        getConfig().ignorePassiveMobs || preferenceEnabled(p, AxePreferences.IGNORE_PASSIVE));
    for (LivingRef target : targets) {
      arc.strike(target, p, secondary, now, (LivingRef struck) ->
          fx(struck.location().add(0D, struck.height() * 0.6D, 0D), FxPriority.COMBAT)
              .burst(Particles.CRIT, 5, 0.25D));
    }

    if (!targets.isEmpty()) {
      int total = targets.size();
      AdaptApi.scheduler().runEntity(p, () -> {
        damageHand(p, getConfig().durabilityCost);
        addStat(p, "axe.cleave.targets-hit", total);
        xp(p, total * getConfig().xpPerTarget);
      });
      Loc origin = p.eyeLocation();
      Loc tip = origin.add(origin.direction().normalize().multiply(1.6D));
      fx(tip, FxPriority.COMBAT)
          .particle(Particles.CRIT, Math.min(10, total * 3 + 3), 0D, 0D, 0D, 0.35D, 0.08D)
          .chord(Sounds.ENTITY_PLAYER_ATTACK_SWEEP, 1.0F, 0.8F, Sounds.ITEM_AXE_STRIP, 0.35F, 1.2F);
    }
  }

  @Override
  public void onTick() {
    arc.prune(System.currentTimeMillis());
  }

  private double getHalfArcDegrees(int level) {
    return getConfig().halfArcDegreesBase + (getLevelPercent(level) * getConfig().halfArcDegreesFactor);
  }

  private double getRadius(int level) {
    return getConfig().radiusBase + (getLevelPercent(level) * getConfig().radiusFactor);
  }

  private int getTargetCap(int level) {
    return getConfig().targetCapBase + (int) Math.floor(getLevelPercent(level) * getConfig().targetCapMaxBonus);
  }

  private double getDamageShare(int level) {
    return getConfig().damageShareBase + (getLevelPercent(level) * getConfig().damageShareFactor);
  }

  @Override
  public void unregister() {
    arc.clear();
    super.unregister();
  }

  @ConfigDescription("Melee axe swings cleave extra enemies in a short frontal arc.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Exclude passive and neutral mobs from area damage and its secondary effects.", impact = "When enabled, protected mobs do not consume target limits. Direct attacks and player targeting are unchanged.")
    boolean ignorePassiveMobs = false;
    @ConfigDoc(value = "Half the cleave arc width in degrees before level scaling.", impact = "Higher values widen the frontal cone that cleave can hit.")
    double halfArcDegreesBase = 25;
    @ConfigDoc(value = "Extra half-arc degrees granted by leveling.", impact = "Higher values widen the cleave cone faster with level.")
    double halfArcDegreesFactor = 25;
    @ConfigDoc(value = "Cleave reach in blocks before level scaling.", impact = "Higher values let cleave strike enemies farther out.")
    double radiusBase = 2.5;
    @ConfigDoc(value = "Extra cleave reach in blocks granted by leveling.", impact = "Higher values extend cleave range with level.")
    double radiusFactor = 1.5;
    @ConfigDoc(value = "Base number of secondary targets cleave can strike.", impact = "Higher values cleave more enemies at low levels.")
    int targetCapBase = 2;
    @ConfigDoc(value = "Additional secondary targets unlocked at maximum level.", impact = "Higher values raise the cleave target cap toward its ceiling.")
    int targetCapMaxBonus = 2;
    @ConfigDoc(value = "Fraction of the primary hit's damage dealt to each cleaved target.", impact = "Higher values make cleaved targets take more damage.")
    double damageShareBase = 0.25;
    @ConfigDoc(value = "Extra damage-share fraction granted by leveling.", impact = "Higher values reward leveling with stronger cleave damage.")
    double damageShareFactor = 0.45;
    @ConfigDoc(value = "Durability spent from the axe when a cleave connects.", impact = "Higher values wear the axe out faster when cleaving.")
    int durabilityCost = 1;
    @ConfigDoc(value = "XP granted per cleaved target.", impact = "Higher values accelerate skill progression from cleaving.")
    double xpPerTarget = 4;

    public Config() {
      baseCost = 3;
      costFactor = 0.6;
      maxLevel = 3;
      initialCost = 2;
    }
  }
}
