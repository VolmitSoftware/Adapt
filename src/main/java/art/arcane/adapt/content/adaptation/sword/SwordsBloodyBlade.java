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

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.content.adaptation.sword.effects.DamagingBleedEffect;
import art.arcane.adapt.content.item.ItemListings;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import de.slikey.effectlib.effect.BleedEffect;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.Map;
import java.util.UUID;

public class SwordsBloodyBlade extends SimpleAdaptation<SwordsBloodyBlade.Config> {
  private static final Color BLOOD = Color.fromRGB(0x9E1414);
  private static final long KILL_CREDIT_GRACE_MS = 4000L;
  private final Cooldowns cooldowns = cooldowns();
  private final Map<UUID, BleedMark> bleedSource = playerState();

  public SwordsBloodyBlade() {
    super("sword-bloody-blade");
    registerConfiguration(Config.class);
    setIcon(Material.RED_DYE);
    setInterval(5534);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.IRON_SWORD)
        .key("challenge_swords_bloody_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_swords_bloody_500", "swords.bloody-blade.bleed-damage", 500, 400);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.DIAMOND_SWORD)
        .key("challenge_swords_bloody_kills_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_swords_bloody_kills_100", "swords.bloody-blade.bleed-kills", 100, 1000);
  }

  @Override
  public void addStats(int level, Element v) {
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


  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(EntityDamageByEntityEvent e) {
    if (e.getDamager() instanceof Player p && hasActiveAdaptation(p) && ItemListings.getToolSwords().contains(p.getInventory().getItemInMainHand().getType())) {
      UUID id = p.getUniqueId();
      if (!cooldowns.isReady(id, getCooldown(getLevel(p)))) {
        return;
      }
      Entity victim = e.getEntity();
      if (!canDamageTarget(p, victim)) return;
      cooldowns.mark(id);
      BleedEffect blood = victim instanceof LivingEntity l ? new DamagingBleedEffect(Adapt.instance.adaptEffectManager, getConfig().damagePerBleedProc, l) : new BleedEffect(Adapt.instance.adaptEffectManager);
      blood.setEntity(victim);
      blood.material = areParticlesEnabled() ? Material.CRIMSON_ROOTS : Material.VOID_AIR;
      blood.height = -1;
      blood.iterations = Math.toIntExact(2 * (3 + (getDurationOfEffect(getLevel(p)) / 1000)));
      blood.period = 5;
      blood.hurt = false;
      blood.start();
      long now = System.currentTimeMillis();
      pruneExpired(now);
      bleedSource.put(victim.getUniqueId(), new BleedMark(id, now + getDurationOfEffect(getLevel(p)) + KILL_CREDIT_GRACE_MS));
      addStat(p, "swords.bloody-blade.bleed-damage", 1);
      fx(victim.getLocation().add(0, 1, 0), FxPriority.COMBAT)
          .dustBurst(BLOOD, 8, 0.3D, 1.1F)
          .particle(Particle.DAMAGE_INDICATOR, 3, 0, 0, 0, 0.1D, 0.05D)
          .chord(Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.6F, 0.8F, Sound.BLOCK_HONEY_BLOCK_SLIDE, 0.3F, 0.6F);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityDeathEvent e) {
    UUID victimId = e.getEntity().getUniqueId();
    BleedMark mark = bleedSource.remove(victimId);
    if (mark == null || mark.expiresAt() < System.currentTimeMillis()) {
      return;
    }
    Player source = Bukkit.getPlayer(mark.source());
    if (source != null && source.isOnline()) {
      addStat(source, "swords.bloody-blade.bleed-kills", 1);
    }
    fx(e.getEntity().getLocation().add(0, 0.8D, 0), FxPriority.TRANSITION)
        .dustBurst(BLOOD, 10, 0.4D, 1.1F)
        .particle(Particles.SMOKE, 4, 0, 0, 0, 0.05D, 0.01D)
        .sound(Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.7F, 0.7F);
  }

  private void pruneExpired(long now) {
    bleedSource.values().removeIf(mark -> mark.expiresAt() < now);
  }

  private record BleedMark(UUID source, long expiresAt) {
  }



  @ConfigDescription("Sword strikes cause bleeding over time.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown for the Swords Bloody Blade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    public long cooldown = 5000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Damage Per Bleed Proc for the Swords Bloody Blade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    public double damagePerBleedProc = 0.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Effect Duration for the Swords Bloody Blade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    public long effectDuration = 1000;

    public Config() {
      baseCost = 7;
      costFactor = 0.325;
      maxLevel = 7;
      initialCost = 7;
    }

  }
}
