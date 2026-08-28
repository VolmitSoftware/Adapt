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
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TamingSharedPain extends SimpleAdaptation<TamingSharedPain.Config> {
  private static final int HARD_MAX_PETS = 16;
  private static final double MINIMUM_TRANSFER = 0.01D;
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
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Material.TOTEM_OF_UNDYING)
            .key("challenge_taming_shared_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_taming_shared_500", "taming.shared-pain.damage-taken", 500, 400);
    registerMilestone("challenge_taming_shared_5k", "taming.shared-pain.damage-taken", 5000, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.pc(getRedirectPercent(level), 0), 1);
    statLore(v, C.YELLOW, "* ", Form.f(getPetHealthFloor(level), 1), 2);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(EntityDamageEvent e) {
    if (!(e.getEntity() instanceof Player owner)) {
      return;
    }

    int level = getActiveLevel(owner);
    if (level <= 0) {
      return;
    }

    double incomingDamage = e.getDamage();
    double requestedTransfer = incomingDamage * getRedirectPercent(level);
    if (requestedTransfer <= MINIMUM_TRANSFER) {
      return;
    }

    UUID ownerId = owner.getUniqueId();
    List<PetTarget> pets = findEligiblePets(owner, ownerId, getRadius(level), getPetHealthFloor(level), getPetLimit());
    if (pets.isEmpty()) {
      return;
    }

    double[] capacities = new double[pets.size()];
    for (int i = 0; i < pets.size(); i++) {
      capacities[i] = pets.get(i).capacity();
    }
    double[] shares = damageShares(requestedTransfer, capacities);
    Location ownerChest = owner.getLocation().add(0, 1, 0);
    double transferred = applyShares(pets, shares, ownerChest);
    if (transferred <= MINIMUM_TRANSFER) {
      return;
    }

    e.setDamage(Math.max(0, incomingDamage - transferred));

    addStat(owner, "taming.shared-pain.damage-taken", transferred);
    xp(owner, transferred * getConfig().xpPerRedirectedDamage);

    if (redirectFx.isReady(ownerId, 500L)) {
      redirectFx.mark(ownerId);
      fx(ownerChest, FxPriority.COMBAT)
          .particle(Particle.DAMAGE_INDICATOR, 3, 0, 0, 0, 0.15D, 0)
          .dustBurst(Color.fromRGB(0xF2C14E), 2, 0.3D, 1.0F)
          .chord(Sound.BLOCK_AMETHYST_CLUSTER_HIT, 0.55F, 0.8F, Sound.BLOCK_CONDUIT_ACTIVATE, 0.3F, 1.5F);
    }
  }

  static double[] damageShares(double requestedDamage, double[] capacities) {
    double[] shares = new double[capacities.length];
    double remaining = Math.max(0, requestedDamage);
    boolean[] available = new boolean[capacities.length];
    int availableCount = 0;
    for (int i = 0; i < capacities.length; i++) {
      if (Double.isFinite(capacities[i]) && capacities[i] > MINIMUM_TRANSFER) {
        available[i] = true;
        availableCount++;
      }
    }

    while (remaining > MINIMUM_TRANSFER && availableCount > 0) {
      double equalShare = remaining / availableCount;
      double allocated = 0;
      for (int i = 0; i < capacities.length; i++) {
        if (!available[i]) {
          continue;
        }
        double capacity = Math.max(0, capacities[i] - shares[i]);
        double amount = Math.min(equalShare, capacity);
        shares[i] += amount;
        allocated += amount;
        if (capacity - amount <= MINIMUM_TRANSFER) {
          available[i] = false;
          availableCount--;
        }
      }
      if (allocated <= MINIMUM_TRANSFER) {
        break;
      }
      remaining -= allocated;
    }
    return shares;
  }

  static double transferredDamage(double beforeHealth, double beforeAbsorption, double afterHealth,
                                  double afterAbsorption, double requestedDamage) {
    double before = Math.max(0, beforeHealth) + Math.max(0, beforeAbsorption);
    double after = Math.max(0, afterHealth) + Math.max(0, afterAbsorption);
    return Math.min(Math.max(0, requestedDamage), Math.max(0, before - after));
  }

  private List<PetTarget> findEligiblePets(Player owner, UUID ownerId, double radius, double healthFloor, int limit) {
    List<PetTarget> pets = new ArrayList<>(limit);
    for (Entity entity : owner.getNearbyEntities(radius, radius, radius)) {
      if (pets.size() >= limit) {
        break;
      }
      if (!(entity instanceof LivingEntity living) || !(entity instanceof Tameable tameable)
          || !J.isOwnedByCurrentRegion(living)) {
        continue;
      }
      if (!tameable.isTamed() || !living.isValid() || living.isDead() || !isOwnedBy(tameable, ownerId)) {
        continue;
      }
      double capacity = living.getHealth() + living.getAbsorptionAmount() - healthFloor;
      if (capacity > MINIMUM_TRANSFER) {
        pets.add(new PetTarget(living, capacity));
      }
    }
    return pets;
  }

  private double applyShares(List<PetTarget> pets, double[] shares, Location ownerChest) {
    double transferred = 0;
    for (int i = 0; i < pets.size(); i++) {
      double share = shares[i];
      if (share <= MINIMUM_TRANSFER) {
        continue;
      }

      LivingEntity pet = pets.get(i).pet();
      double beforeHealth = pet.getHealth();
      double beforeAbsorption = pet.getAbsorptionAmount();
      pet.damage(share);
      double actual = transferredDamage(beforeHealth, beforeAbsorption, pet.getHealth(), pet.getAbsorptionAmount(), share);
      if (actual <= MINIMUM_TRANSFER) {
        continue;
      }

      transferred += actual;
      UUID petId = pet.getUniqueId();
      if (redirectFx.isReady(petId, 500L)) {
        redirectFx.mark(petId);
        Location petChest = pet.getLocation().add(0, 1, 0);
        fx(petChest, FxPriority.COMBAT)
            .line(Particle.CRIT, ownerChest.getX(), ownerChest.getY(), ownerChest.getZ(), 6)
            .sound(Sound.ENTITY_WOLF_WHINE, 0.4F, 1.35F);
      }
    }
    return transferred;
  }

  private boolean isOwnedBy(Tameable tameable, UUID ownerId) {
    AnimalTamer petOwner = tameable.getOwner();
    return petOwner != null && ownerId.equals(petOwner.getUniqueId());
  }

  private double getRedirectPercent(int level) {
    return clampRedirectPercent(
        getConfig().redirectPercentBase + (getLevelPercent(level) * getConfig().redirectPercentFactor),
        getConfig().maxRedirectPercent);
  }

  static double clampRedirectPercent(double calculated, double configuredMaximum) {
    return Math.max(0D, Math.min(1D, Math.min(calculated, configuredMaximum)));
  }

  private double getPetHealthFloor(int level) {
    return Math.max(1.0, getConfig().petHealthFloorBase + (getLevelPercent(level) * getConfig().petHealthFloorFactor));
  }

  private double getRadius(int level) {
    return Math.max(1.0, getConfig().radiusBase + (getLevelPercent(level) * getConfig().radiusFactor));
  }

  private int getPetLimit() {
    return Math.max(1, Math.min(getConfig().maxPets, HARD_MAX_PETS));
  }

  private record PetTarget(LivingEntity pet, double capacity) {
  }

  @ConfigDescription("Spread part of your incoming damage across nearby owned companions without reducing them below their health floor.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Redirect Percent Base for the Taming Shared Pain adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double redirectPercentBase = 0.2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Redirect Percent Factor for the Taming Shared Pain adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double redirectPercentFactor = 0.35;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Redirect Percent for the Taming Shared Pain adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxRedirectPercent = 0.7;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Minimum health preserved on each companion before it stops receiving Shared Pain damage.", impact = "Higher values protect companions more aggressively but redirect less owner damage.")
    double petHealthFloorBase = 1.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional companion health floor reached at maximum adaptation level.", impact = "Higher values preserve more companion health as Shared Pain levels up.")
    double petHealthFloorFactor = 1.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base radius searched for owned companions that can share incoming damage.", impact = "Higher values allow more distant companions to participate.")
    double radiusBase = 8.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional companion search radius reached at maximum adaptation level.", impact = "Higher values expand Shared Pain's range as it levels up.")
    double radiusFactor = 8.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum companions included in one damage split, capped internally at 16.", impact = "Higher values spread damage more thinly but perform more nested damage events.")
    int maxPets = 8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Redirected Damage for the Taming Shared Pain adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerRedirectedDamage = 2.0;

    public Config() {
      costFactor = 0.72;
      initialCost = 4;
    }
  }
}
