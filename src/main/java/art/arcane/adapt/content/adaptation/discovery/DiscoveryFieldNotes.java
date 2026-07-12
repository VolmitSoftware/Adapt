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
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.world.PlayerData;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;

public class DiscoveryFieldNotes extends SimpleAdaptation<DiscoveryFieldNotes.Config> {
  public DiscoveryFieldNotes() {
    super("discovery-field-notes");
    registerConfiguration(Config.class);
    setIcon(Material.WRITABLE_BOOK);
    setInterval(4400);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.WRITABLE_BOOK)
        .key("challenge_discovery_fieldnotes_25")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.WRITTEN_BOOK)
            .key("challenge_discovery_fieldnotes_100")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_discovery_fieldnotes_25", "discovery.field-notes.species", 25, 500);
    registerMilestone("challenge_discovery_fieldnotes_100", "discovery.field-notes.species", 100, 2000);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(perSpeciesCap(getLevelPercent(level), getConfig().perSpeciesCapBase, getConfig().perSpeciesCapFactor), 1), 1);
    statLore(v, Form.f(firstKillXp(getLevelPercent(level), getConfig().firstKillXpBase, getConfig().firstKillXpFactor), 0), 2);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityDeathEvent e) {
    LivingEntity victim = e.getEntity();
    Player killer = victim.getKiller();
    if (killer == null || victim instanceof Player || !hasActiveAdaptation(killer)) {
      return;
    }

    EntityType species = victim.getType();
    Location where = victim.getLocation().add(0, victim.getHeight() * 0.5D, 0);
    J.runEntity(killer, () -> recordKillOwned(killer, species, where));
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void on(EntityDamageByEntityEvent e) {
    if (!(e.getEntity() instanceof LivingEntity victim) || victim instanceof Player) {
      return;
    }

    Player attacker = resolveAttacker(e.getDamager());
    if (attacker == null || attacker == victim) {
      return;
    }

    int level = getActiveLevel(attacker);
    if (level <= 0) {
      return;
    }

    double cap = perSpeciesCap(getLevelPercent(level), getConfig().perSpeciesCapBase, getConfig().perSpeciesCapFactor);
    double banked = getPlayer(attacker).getData().getStat(bonusKey(victim.getType()));
    double bonus = Math.min(cap, banked);
    if (bonus <= 0) {
      return;
    }

    e.setDamage(e.getDamage() + bonus);
  }

  private void recordKillOwned(Player killer, EntityType species, Location where) {
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
            f.chord(Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.4F, 1.2F, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6F, 1.5F);
          }
        })
        .onComplete(() -> fx(where, FxPriority.GAMEPLAY).particle(Particles.TOTEM, 10, 0, 0, 0, 0.2, 0.08))
        .start();
  }

  private Player resolveAttacker(Entity damager) {
    if (damager instanceof Player direct) {
      return direct;
    }

    if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
      return shooter;
    }

    return null;
  }

  private String bonusKey(EntityType species) {
    return "discovery.field-notes.bonus." + species.name();
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
      costFactor = 0.5;
      maxLevel = 5;
      initialCost = 4;
    }
  }
}
