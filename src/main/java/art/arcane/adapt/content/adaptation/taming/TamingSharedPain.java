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

package art.arcane.adapt.content.adaptation.taming;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;

public class TamingSharedPain extends SimpleAdaptation<TamingSharedPain.Config> {
  private final Cooldowns redirectFx = cooldowns();

  public TamingSharedPain() {
    super("tame-shared-pain");
    registerConfiguration(Config.class);
    setLocalizationKey("taming.shared_pain");
    setIcon(Material.POPPY);
    setInterval(1700);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.SHIELD)
        .key("challenge_taming_shared_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.TOTEM_OF_UNDYING)
            .key("challenge_taming_shared_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_taming_shared_500", "taming.shared-pain.damage-taken", 500, 400);
    registerMilestone("challenge_taming_shared_5k", "taming.shared-pain.damage-taken", 5000, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.pc(getRedirectPercent(level), 0), 1);
    statLore(v, C.YELLOW, "* ", Form.f(getOwnerHealthFloor(level), 1), 2);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(EntityDamageEvent e) {
    if (!(e.getEntity() instanceof Tameable tameable) || !tameable.isTamed() || !(tameable.getOwner() instanceof Player owner)) {
      return;
    }

    int level = getActiveDamageLevel(owner, tameable);
    if (level <= 0) {
      return;
    }

    double raw = e.getDamage() * getRedirectPercent(level);
    if (raw <= 0) {
      return;
    }

    double floor = getOwnerHealthFloor(level);
    double allowed = Math.max(0, owner.getHealth() - floor);
    double redirect = Math.min(raw, allowed);
    if (redirect <= 0.01) {
      return;
    }

    boolean clamped = allowed < raw;
    e.setDamage(Math.max(0, e.getDamage() - redirect));
    if (e.getDamage() <= 0.01) {
      e.setCancelled(true);
    }

    owner.damage(redirect);
    addStat(owner, "taming.shared-pain.damage-taken", redirect);
    xp(owner, redirect * getConfig().xpPerRedirectedDamage);

    if (redirectFx.isReady(tameable.getUniqueId(), 500L)) {
      redirectFx.mark(tameable.getUniqueId());
      Location ownerChest = owner.getLocation().add(0, 1, 0);
      Location petChest = tameable.getLocation().add(0, 1, 0);
      fx(petChest, FxPriority.COMBAT)
          .line(Particle.CRIT, ownerChest.getX(), ownerChest.getY(), ownerChest.getZ(), 6)
          .sound(Sound.ENTITY_WOLF_WHINE, 0.55F, 1.2F);
      fx(ownerChest, FxPriority.COMBAT)
          .particle(Particle.DAMAGE_INDICATOR, 3, 0, 0, 0, 0.15D, 0)
          .chord(Sound.BLOCK_AMETHYST_CLUSTER_HIT, 0.5F, 0.6F, Sound.ENTITY_PLAYER_HURT, 0.25F, 1.0F);
      if (clamped) {
        fx(ownerChest, FxPriority.TRANSITION)
            .dustBurst(Color.fromRGB(0xF2C14E), 2, 0.3D, 1.0F)
            .sound(Sound.BLOCK_CONDUIT_ACTIVATE, 0.25F, 1.5F);
      }
    }
  }

  private double getRedirectPercent(int level) {
    return Math.min(getConfig().maxRedirectPercent, getConfig().redirectPercentBase + (getLevelPercent(level) * getConfig().redirectPercentFactor));
  }

  private double getOwnerHealthFloor(int level) {
    return Math.max(1.0, getConfig().ownerHealthFloorBase + (getLevelPercent(level) * getConfig().ownerHealthFloorFactor));
  }


  @ConfigDescription("Redirect part of your pet's incoming damage to you, preserving companion survivability.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Redirect Percent Base for the Taming Shared Pain adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double redirectPercentBase = 0.2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Redirect Percent Factor for the Taming Shared Pain adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double redirectPercentFactor = 0.35;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Redirect Percent for the Taming Shared Pain adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxRedirectPercent = 0.7;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Owner Health Floor Base for the Taming Shared Pain adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double ownerHealthFloorBase = 2.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Owner Health Floor Factor for the Taming Shared Pain adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double ownerHealthFloorFactor = 4.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Redirected Damage for the Taming Shared Pain adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerRedirectedDamage = 2.0;

    public Config() {
      costFactor = 0.72;
      initialCost = 4;
    }
  }
}
