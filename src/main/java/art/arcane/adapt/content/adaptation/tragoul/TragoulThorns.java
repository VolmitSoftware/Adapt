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

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.UUID;


public class TragoulThorns extends SimpleAdaptation<TragoulThorns.Config> {
  private static final Color THORN_CRIMSON = Color.fromRGB(140, 10, 10);
  private static final Color THORN_LETHAL = Color.fromRGB(120, 0, 0);
  private final Cooldowns cooldowns = cooldowns();

  public TragoulThorns() {
    super("tragoul-thorns");
    registerConfiguration(TragoulThorns.Config.class);
    setIcon(Material.CACTUS);
    setInterval(25000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.CACTUS)
        .key("challenge_tragoul_thorns_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Material.IRON_CHESTPLATE)
            .key("challenge_tragoul_thorns_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_tragoul_thorns_500", "tragoul.thorns.damage-reflected", 500, 400);
    registerMilestone("challenge_tragoul_thorns_5k", "tragoul.thorns.damage-reflected", 5000, 1500);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.CACTUS)
        .key("challenge_tragoul_thorns_kill")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(getConfig().damageMultiplierPerLevel * level, 2), 1);
  }


  @EventHandler
  public void on(EntityDamageByEntityEvent e) {
    if (TragoulReactiveDamage.isActive()) {
      return;
    }
    if (e.getEntity() instanceof Player p) {
      withAdaptedPlayer(p, e, () -> {
        int level = getActiveLevel(p);
        if (level <= 0) {
          return;
        }

        UUID id = p.getUniqueId();
        if (!cooldowns.isReady(id, 1500L)) {
          return;
        }

        LivingEntity le = null;
        if (e.getDamager() instanceof LivingEntity living) {
          le = living;
        } else if (e.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof LivingEntity shooter) {
          le = shooter;
        }

        if (le != null && canDamageTarget(p, le)) {
          cooldowns.mark(id);
          LivingEntity attacker = le;
          double reflectedDamage = getConfig().damageMultiplierPerLevel * level;
          double healthBefore = attacker.getHealth();
          fx(attacker, FxPriority.COMBAT)
              .ring(Particles.CRIT_MAGIC, 0.6D, 10, 1.0D)
              .dustBurst(THORN_CRIMSON, 4, 0.4D, 1.0F)
              .chord(Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.5F, 1.4F, Sound.ENTITY_ARROW_HIT, 0.4F, 0.8F);
          TragoulReactiveDamage.apply(() -> attacker.damage(reflectedDamage, p));
          addStat(p, "tragoul.thorns.damage-reflected", (int) reflectedDamage);
          if (healthBefore <= reflectedDamage) {
            fx(attacker, FxPriority.COMBAT)
                .particle(Particle.DAMAGE_INDICATOR, 6, 0, 1.0D, 0, 0.3D, 0.05D)
                .dustBurst(THORN_LETHAL, 10, 0.4D, 1.0F)
                .chord(Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.7F, 0.7F, Sound.BLOCK_BONE_BLOCK_BREAK, 0.5F, 1.2F);
            grantOnce(p, "challenge_tragoul_thorns_kill");
          }
        }
      });
    }
  }



  @ConfigDescription("Reflect damage back to your attacker.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Damage Multiplier Per Level for the Tragoul Thorns adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double damageMultiplierPerLevel = 1.75;

    public Config() {
      costFactor = 0.72;
      initialCost = 4;
    }
  }
}
