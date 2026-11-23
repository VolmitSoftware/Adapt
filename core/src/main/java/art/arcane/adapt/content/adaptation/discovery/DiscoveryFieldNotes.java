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

package art.arcane.adapt.content.adaptation.discovery;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.golem.GolemKillCredit;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.world.PlayerData;
import art.arcane.adapt.localization.catalog.DiscoveryMessages;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.List;

public class DiscoveryFieldNotes extends SimpleAdaptation<DiscoveryFieldNotes.Config> {
  public static final PlayerPreference<CommonPreferences.Toggle> DAMAGE = CommonPreferences.toggle("species-damage", DiscoveryMessages.PREFERENCE_DISCOVERYFIELDNOTES_DAMAGE, CommonPreferences.Toggle.ON);

  public DiscoveryFieldNotes() {
    super("discovery-field-notes");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("writable_book"));
    setInterval(4400);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("writable_book"))
        .key("challenge_discovery_fieldnotes_25")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("written_book"))
            .key("challenge_discovery_fieldnotes_100")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_discovery_fieldnotes_25", "discovery.field-notes.species", 25, 500);
    registerMilestone("challenge_discovery_fieldnotes_100", "discovery.field-notes.species", 100, 2000);
  }


  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, DAMAGE);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.f(perSpeciesCap(getLevelPercent(level), getConfig().perSpeciesCapBase, getConfig().perSpeciesCapFactor), 1), 1);
    statLore(v, Form.f(firstKillXp(getLevelPercent(level), getConfig().firstKillXpBase, getConfig().firstKillXpFactor), 0), 2);
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityDeathEvent e) {
    if (e.platformCancelled()) {
      return;
    }
    LivingRef victim = e.entity();
    PlayerRef killer = victim.killer();
    if (killer == null || victim instanceof PlayerRef || GolemKillCredit.isGolemKill(victim)) {
      return;
    }

    Id species = victim.type();
    Loc where = victim.location().add(0, victim.height() * 0.5D, 0);
    Tasks.runEntity(killer, () -> recordKillOwned(killer, species, where));
  }

  @Handle(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void on(EntityDamageByEntityEvent e) {
    if (!(e.entity() instanceof LivingRef victim) || victim instanceof PlayerRef) {
      return;
    }

    PlayerRef attacker = resolveAttacker(e.damager());
    if (attacker == null || attacker.uuid().equals(victim.uuid())) {
      return;
    }

    int level = preferenceEnabled(attacker, DAMAGE) ? getActiveLevel(attacker) : 0;
    if (level <= 0) {
      return;
    }

    double cap = perSpeciesCap(getLevelPercent(level), getConfig().perSpeciesCapBase, getConfig().perSpeciesCapFactor);
    double banked = getPlayer(attacker).getData().getStat(bonusKey(victim.type()));
    double bonus = Math.min(cap, banked);
    if (bonus <= 0) {
      return;
    }

    e.setDamage(e.damage() + bonus);
  }

  private void recordKillOwned(PlayerRef killer, Id species, Loc where) {
    int level = getActiveLevel(killer);
    if (level <= 0) {
      return;
    }

    PlayerData data = getPlayer(killer).getData();
    String key = bonusKey(species);
    double current = data.getStat(key);
    double cap = perSpeciesCap(getLevelPercent(level), getConfig().perSpeciesCapBase, getConfig().perSpeciesCapFactor);
    double target = bankTarget(current, getConfig().bonusPerKill, cap);
    if (target > current) {
      data.addStat(key, target - current);
    }

    if (current > 0) {
      return;
    }

    data.addStat("discovery.field-notes.species", 1);
    xp(killer, firstKillXp(getLevelPercent(level), getConfig().firstKillXpBase, getConfig().firstKillXpFactor));
    timeline(where)
        .duration(14)
        .priority(FxPriority.GAMEPLAY)
        .cullRadius(28)
        .frame((f, tick, progress) -> {
          f.dustHelix(0.4D, 1.4D, 6, progress * Math.PI * 2.0D, 0.9F);
          if (tick == 0) {
            f.chord(Sounds.UI_TOAST_CHALLENGE_COMPLETE, 0.4F, 1.2F, Sounds.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6F, 1.5F);
          }
        })
        .onComplete(() -> fx(where, FxPriority.GAMEPLAY).particle(Particles.TOTEM, 10, 0, 0, 0, 0.2, 0.08))
        .start();
  }

  private PlayerRef resolveAttacker(EntityRef damager) {
    if (damager instanceof PlayerRef direct) {
      return direct;
    }

    if (damager instanceof ProjectileRef projectile && projectile.shooter() instanceof PlayerRef shooter) {
      return shooter;
    }

    return null;
  }

  private String bonusKey(Id species) {
    return "discovery.field-notes.bonus." + DiscoveryNames.enumName(species);
  }

  static double perSpeciesCap(double levelPercent, double base, double factor) {
    return Math.max(0D, base + (levelPercent * factor));
  }

  static double bankTarget(double current, double increment, double cap) {
    return Math.min(cap, current + Math.max(0D, increment));
  }

  static double firstKillXp(double levelPercent, double base, double factor) {
    return Math.max(0D, base + (levelPercent * factor));
  }


  @ConfigDescription("Your first kill of each mob species pays big XP and banks a small permanent damage bonus against that species.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls First Kill Xp Base for the Discovery Field Notes adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double firstKillXpBase = 120;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls First Kill Xp Factor for the Discovery Field Notes adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double firstKillXpFactor = 240;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Damage bonus banked against a species per kill until the cap is reached.", impact = "Higher values reach the per-species cap in fewer kills.")
    double bonusPerKill = 0.15;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Per Species Cap Base for the Discovery Field Notes adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double perSpeciesCapBase = 0.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Per Species Cap Factor for the Discovery Field Notes adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double perSpeciesCapFactor = 2.5;

    public Config() {
      baseCost = 3;
      costFactor = 0.6;
      maxLevel = 5;
      initialCost = 2;
    }
  }
}
