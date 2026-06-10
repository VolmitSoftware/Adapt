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

import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.version.IAttribute;
import art.arcane.adapt.api.version.Version;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.misc.SoundPlayer;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Attributes;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import lombok.NoArgsConstructor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataType;

public class TragoulCorpseExplosion extends SimpleAdaptation<TragoulCorpseExplosion.Config> {
  private static final NamespacedKey NOVA_KEY = NamespacedKey.fromString("adapt:tragoul_nova_stamp");

  public TragoulCorpseExplosion() {
    super("tragoul-corpse-explosion");
    registerConfiguration(Config.class);
    setDescription(Localizer.dLocalize("tragoul.corpse_explosion.description"));
    setDisplayName(Localizer.dLocalize("tragoul.corpse_explosion.name"));
    setIcon(Material.WITHER_ROSE);
    setInterval(25000);
    setBaseCost(getConfig().baseCost);
    setMaxLevel(getConfig().maxLevel);
    setInitialCost(getConfig().initialCost);
    setCostFactor(getConfig().costFactor);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.WITHER_ROSE)
        .key("challenge_tragoul_corpse_500")
        .title(Localizer.dLocalize("advancement.challenge_tragoul_corpse_500.title"))
        .description(Localizer.dLocalize("advancement.challenge_tragoul_corpse_500.description"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.NETHERRACK)
            .key("challenge_tragoul_corpse_5k")
            .title(Localizer.dLocalize("advancement.challenge_tragoul_corpse_5k.title"))
            .description(Localizer.dLocalize("advancement.challenge_tragoul_corpse_5k.description"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_tragoul_corpse_500", "tragoul.corpse-explosion.mobs-detonated", 500, 400);
    registerMilestone("challenge_tragoul_corpse_5k", "tragoul.corpse-explosion.mobs-detonated", 5000, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + Localizer.dLocalize("tragoul.corpse_explosion.lore1"));
    v.addLore(C.GREEN + "+ " + Form.f(getRadius(level), 1) + C.GRAY + " " + Localizer.dLocalize("tragoul.corpse_explosion.lore2"));
    v.addLore(C.GREEN + "+ " + Form.pc(getVictimHealthFraction(level), 0) + C.GRAY + " " + Localizer.dLocalize("tragoul.corpse_explosion.lore3"));
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityDeathEvent e) {
    Player killer = e.getEntity().getKiller();
    if (killer == null) {
      return;
    }

    LivingEntity victim = e.getEntity();
    long now = System.currentTimeMillis();
    Long novaStamp = victim.getPersistentDataContainer().get(NOVA_KEY, PersistentDataType.LONG);
    if (novaStamp != null && now - novaStamp < getConfig().chainSuppressionMillis) {
      return;
    }

    withAdaptedPlayer(killer, () -> detonate(killer, victim, now));
  }

  static void detonateServantKill(TragoulCorpseExplosion nova, Player owner, LivingEntity victim) {
    long now = System.currentTimeMillis();
    Long novaStamp = victim.getPersistentDataContainer().get(NOVA_KEY, PersistentDataType.LONG);
    if (novaStamp != null && now - novaStamp < nova.getConfig().chainSuppressionMillis) {
      return;
    }

    nova.withAdaptedPlayer(owner, () -> nova.detonate(owner, victim, now));
  }

  private void detonate(Player credited, LivingEntity victim, long now) {
    int level = getActiveLevel(credited);
    if (level <= 0 || !canDamageTarget(credited, victim)) {
      return;
    }

    double radius = getRadius(level);
    double damage = getNovaDamage(level, victim);
    int hit = 0;
    for (Entity entity : victim.getNearbyEntities(radius, radius, radius)) {
      if (!(entity instanceof Monster monster) || monster.isDead() || !monster.isValid()) {
        continue;
      }

      if (!canDamageTarget(credited, monster)) {
        continue;
      }

      monster.getPersistentDataContainer().set(NOVA_KEY, PersistentDataType.LONG, now);
      J.runEntity(monster, () -> {
        if (monster.isValid() && !monster.isDead()) {
          monster.damage(damage, credited);
        }
      });
      hit++;
      if (hit >= getConfig().maxTargets) {
        break;
      }
    }

    if (hit <= 0) {
      return;
    }

    if (areParticlesEnabled()) {
      Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(150, 12, 12), 1.4f);
      victim.getWorld().spawnParticle(Particle.DUST, victim.getLocation().add(0, 0.9, 0), 70, radius * 0.4, 0.6, radius * 0.4, 0.01, dust);
      victim.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, victim.getLocation().add(0, 1, 0), 12, 0.5, 0.5, 0.5, 0.04);
    }
    SoundPlayer.of(victim.getWorld()).play(victim.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.55f, 1.35f);
    getPlayer(credited).getData().addStat("tragoul.corpse-explosion.mobs-detonated", hit);
    xp(credited, hit * getConfig().xpPerMobHit);
  }

  private double getRadius(int level) {
    return Math.max(1, getConfig().radiusBase + (getLevelPercent(level) * getConfig().radiusFactor));
  }

  private double getVictimHealthFraction(int level) {
    return Math.max(0, getConfig().victimHealthFractionBase + (getLevelPercent(level) * getConfig().victimHealthFractionFactor));
  }

  private double getNovaDamage(int level, LivingEntity victim) {
    IAttribute attribute = Version.get().getAttribute(victim, Attributes.GENERIC_MAX_HEALTH);
    double victimMaxHealth = attribute == null ? 20D : attribute.getValue();
    double damage = getConfig().baseDamage + (victimMaxHealth * getVictimHealthFraction(level));
    return Math.min(getConfig().maxDamage, damage);
  }

  @Override
  public boolean isEnabled() {
    return getConfig().enabled;
  }

  @Override
  public void onTick() {
  }

  @Override
  public boolean isPermanent() {
    return getConfig().permanent;
  }

  @NoArgsConstructor
  @ConfigDescription("Mobs you kill detonate in a blood nova that damages nearby hostile mobs.")
  protected static class Config {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
    boolean permanent = false;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
    boolean enabled = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
    int baseCost = 4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
    int maxLevel = 5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
    int initialCost = 4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
    double costFactor = 0.72;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base nova radius before level scaling.", impact = "Higher values damage hostile mobs further from the corpse.")
    double radiusBase = 3.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional nova radius granted at max level.", impact = "Higher values increase the level-scaled radius growth.")
    double radiusFactor = 3.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Flat nova damage applied to every hostile mob hit.", impact = "Higher values increase the guaranteed damage portion of the nova.")
    double baseDamage = 2.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Fraction of the victim's max health added to nova damage before level scaling.", impact = "Higher values make tanky kills detonate harder.")
    double victimHealthFractionBase = 0.10;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional victim max-health fraction granted at max level.", impact = "Higher values increase the level-scaled damage growth.")
    double victimHealthFractionFactor = 0.25;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Hard cap on nova damage per mob.", impact = "Prevents extreme bosses from producing one-shot novas.")
    double maxDamage = 16.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum hostile mobs damaged per nova.", impact = "Caps per-kill work to protect server performance.")
    int maxTargets = 12;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Window in milliseconds during which a nova-damaged mob cannot trigger another nova.", impact = "Prevents chain-reaction detonations.")
    long chainSuppressionMillis = 5000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "XP granted per hostile mob hit by a nova.", impact = "Higher values accelerate skill progression from this adaptation.")
    double xpPerMobHit = 6;
  }
}
