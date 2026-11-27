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

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;

import java.util.List;

public class DiscoveryFieldNotes extends SimpleAdaptation<DiscoveryFieldNotes.Config> {
  public static final Id ID = Id.adapt("discovery/field_notes");
  public static final PlayerPreference<CommonPreferences.Toggle> DAMAGE = CommonPreferences.toggle("species-damage", Text.key("adapt.discovery.preferences.discoveryfieldnotes.species-damage"), CommonPreferences.Toggle.ON);

  public DiscoveryFieldNotes() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("writable_book"));
    setInterval(4400);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("writable_book"))
        .key("challenge_discovery_fieldnotes_25")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("written_book"))
            .key("challenge_discovery_fieldnotes_100")
            .frame(AdvancementFrame.CHALLENGE)
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
    statLore(v, Format.f(perSpeciesCap(getLevelPercent(level), getConfig().perSpeciesCapBase, getConfig().perSpeciesCapFactor), 1), 1);
    statLore(v, Format.f(firstKillXp(getLevelPercent(level), getConfig().firstKillXpBase, getConfig().firstKillXpFactor), 0), 2);
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityDeathEvent e) {
    if (e.platformCancelled()) {
      return;
    }
    LivingRef victim = e.entity();
    PlayerRef killer = SpeciesNotes.notedKiller(victim);
    if (killer == null) {
      return;
    }

    Id species = victim.type();
    Loc where = victim.location().add(0, victim.height() * 0.5D, 0);
    AdaptApi.scheduler().runEntity(killer, () -> recordKillOwned(killer, species, where));
  }

  @Handle(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void on(EntityDamageByEntityEvent e) {
    if (!(e.entity() instanceof LivingRef victim) || victim instanceof PlayerRef) {
      return;
    }

    PlayerRef attacker = SpeciesNotes.attacker(e.damager());
    if (attacker == null || attacker.uuid().equals(victim.uuid())) {
      return;
    }

    int level = preferenceEnabled(attacker, DAMAGE) ? getActiveLevel(attacker) : 0;
    if (level <= 0) {
      return;
    }

    double cap = perSpeciesCap(getLevelPercent(level), getConfig().perSpeciesCapBase, getConfig().perSpeciesCapFactor);
    double bonus = SpeciesNotes.bankedBonus(getPlayer(attacker), victim.type(), cap);
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

    PlayerProfile data = getPlayer(killer);
    double cap = perSpeciesCap(getLevelPercent(level), getConfig().perSpeciesCapBase, getConfig().perSpeciesCapFactor);
    if (!SpeciesNotes.record(data, species, getConfig().bonusPerKill, cap)) {
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

  public static double perSpeciesCap(double levelPercent, double base, double factor) {
    return Math.max(0D, base + (levelPercent * factor));
  }

  static double bankTarget(double current, double increment, double cap) {
    return Math.min(cap, current + Math.max(0D, increment));
  }

  public static double firstKillXp(double levelPercent, double base, double factor) {
    return Math.max(0D, base + (levelPercent * factor));
  }


  @ConfigDescription("Your first kill of each mob species pays big XP and banks a small permanent damage bonus against that species.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Controls First Kill Xp Base for the Discovery Field Notes adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double firstKillXpBase = 120;
    @ConfigDoc(value = "Controls First Kill Xp Factor for the Discovery Field Notes adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double firstKillXpFactor = 240;
    @ConfigDoc(value = "Damage bonus banked against a species per kill until the cap is reached.", impact = "Higher values reach the per-species cap in fewer kills.")
    double bonusPerKill = 0.15;
    @ConfigDoc(value = "Controls Per Species Cap Base for the Discovery Field Notes adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double perSpeciesCapBase = 0.5;
    @ConfigDoc(value = "Controls Per Species Cap Factor for the Discovery Field Notes adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double perSpeciesCapFactor = 2.5;

    public Config() {
      baseCost = 3;
      costFactor = 0.6;
      maxLevel = 5;
      initialCost = 2;
    }
  }
}
