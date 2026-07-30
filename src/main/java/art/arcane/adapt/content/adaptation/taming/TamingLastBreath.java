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

import art.arcane.adapt.Adapt;
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
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class TamingLastBreath extends SimpleAdaptation<TamingLastBreath.Config> {
  private final Cooldowns saveCd = cooldowns();
  private final Map<UUID, Long> protectedPets = new ConcurrentHashMap<>();

  static long cooldownMillis(double levelPercent, double base, double factor, double min) {
    return (long) Math.max(min, base - (levelPercent * factor));
  }

  public TamingLastBreath() {
    super("tame-last-breath");
    registerConfiguration(Config.class);
    setLocalizationKey("taming.last_breath");
    setIcon(Material.TOTEM_OF_UNDYING);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.TOTEM_OF_UNDYING)
        .key("challenge_taming_lastbreath_50")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.NETHER_STAR)
            .key("challenge_taming_lastbreath_500")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_taming_lastbreath_50", "taming.last-breath.saves", 50, 400);
    registerMilestone("challenge_taming_lastbreath_500", "taming.last-breath.saves", 500, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, C.YELLOW, "* ", Form.duration((double) getCooldownMillis(level), 1), 1);
    statLore(v, C.AQUA, "* ", Form.duration(getInvulnTicks() * 50D, 1), 2);
  }

  @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
  public void onProtectedWindow(EntityDamageEvent e) {
    UUID entityId = e.getEntity().getUniqueId();
    Long until = protectedPets.get(entityId);
    if (until == null) {
      return;
    }

    if (until < System.currentTimeMillis()) {
      protectedPets.remove(entityId);
      return;
    }

    e.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(EntityDamageEvent e) {
    if (!(e.getEntity() instanceof Tameable pet) || !pet.isTamed()
        || !(pet.getOwner() instanceof Player owner)) {
      return;
    }

    LivingEntity living = (LivingEntity) pet;
    double finalDamage = e.getFinalDamage();
    if (living.getHealth() - finalDamage > 0.0) {
      return;
    }

    int level = getActiveLevel(owner);
    if (level <= 0) {
      return;
    }

    UUID petId = pet.getUniqueId();
    if (!saveCd.isReady(petId, getCooldownMillis(level))) {
      return;
    }

    saveCd.mark(petId);
    e.setDamage(0);
    e.setCancelled(true);
    living.setHealth(1.0);
    living.setFireTicks(0);

    long now = System.currentTimeMillis();
    protectedPets.values().removeIf(until -> until < now);
    protectedPets.put(petId, now + (getInvulnTicks() * 50L));

    fx(living.getLocation().add(0, 1, 0), FxPriority.COMBAT)
        .burst(Particles.TOTEM, 24, 0.4D)
        .column(Particles.VILLAGER_HAPPY, 6, 1.4D)
        .chord(Sound.ITEM_TOTEM_USE, 0.7F, 1.2F, Sound.ENTITY_WOLF_WHINE, 0.6F, 1.4F);

    UUID ownerId = owner.getUniqueId();
    J.runEntity(owner, () -> recallPet(owner, ownerId, living));
  }

  private void recallPet(Player owner, UUID ownerId, LivingEntity pet) {
    if (!owner.isOnline()) {
      return;
    }

    Location safe = findSafeLocation(owner);
    if (safe == null) {
      safe = owner.getLocation();
    }

    addStat(owner, "taming.last-breath.saves", 1);
    xp(owner, getConfig().xpPerSave);
    beginRecallTeleport(ownerId, pet, safe.clone());
  }

  private void beginRecallTeleport(UUID ownerId, LivingEntity pet, Location destination) {
    CompletableFuture<Boolean> teleport;
    try {
      teleport = pet.teleportAsync(destination);
    } catch (RuntimeException error) {
      Adapt.error("Last Breath could not start pet teleport for owner " + ownerId + ".");
      error.printStackTrace();
      return;
    }
    if (teleport == null) {
      return;
    }
    teleport.whenComplete((success, failure) ->
        completeRecallTeleport(ownerId, pet, destination, success, failure));
  }

  private void completeRecallTeleport(UUID ownerId, LivingEntity pet, Location destination,
                                      Boolean success, Throwable failure) {
    if (failure != null) {
      Adapt.error("Last Breath pet teleport failed for owner " + ownerId + ".");
      failure.printStackTrace();
    }
    if (!successfulRecallTeleport(success, failure)) {
      return;
    }
    if (!J.runEntity(pet, () -> showRecallArrivalOwned(pet, destination))) {
      Adapt.warn("Last Breath completed pet teleport but could not schedule arrival effects for owner "
          + ownerId + ".");
    }
  }

  private void showRecallArrivalOwned(LivingEntity pet, Location destination) {
    if (!pet.isValid() || pet.isDead()) {
      return;
    }

    fx(destination, FxPriority.TRANSITION)
        .dome(Particle.PORTAL, 0.6D, 8)
        .particle(Particles.VILLAGER_HAPPY, 4, 0, 0.5D, 0, 0.4D, 0)
        .chord(Sound.ENTITY_ENDERMAN_TELEPORT, 0.6F, 1.4F, Sound.ENTITY_WOLF_PANT, 0.6F, 1.3F);
  }

  static boolean successfulRecallTeleport(Boolean success, Throwable failure) {
    return failure == null && Boolean.TRUE.equals(success);
  }

  private Location findSafeLocation(Player p) {
    Location base = p.getLocation();
    int[][] offsets = {
        {0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1},
        {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };

    for (int y = 0; y <= 2; y++) {
      for (int[] offset : offsets) {
        Location candidate = base.clone().add(offset[0], y, offset[1]);
        if (isSafeSpot(candidate)) {
          return candidate;
        }
      }
    }

    return null;
  }

  private boolean isSafeSpot(Location location) {
    Block feet = location.getBlock();
    Block head = location.clone().add(0, 1, 0).getBlock();
    Block below = location.clone().add(0, -1, 0).getBlock();
    return feet.isPassable() && head.isPassable() && below.getType().isSolid();
  }

  private long getCooldownMillis(int level) {
    return cooldownMillis(getLevelPercent(level), getConfig().cooldownMillisBase, getConfig().cooldownMillisFactor, getConfig().minCooldownMillis);
  }

  private int getInvulnTicks() {
    return Math.max(10, getConfig().invulnTicks);
  }


  @ConfigDescription("On a lethal hit a pet drops to 1 HP, is briefly invulnerable, and recalls to your side.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base per-pet cooldown in milliseconds before Last Breath can save the same pet again.", impact = "Higher values make the safety net rarer; leveling lowers this value.")
    double cooldownMillisBase = 300000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Cooldown milliseconds removed at max level.", impact = "Higher values reduce the per-pet cooldown faster as you level.")
    double cooldownMillisFactor = 180000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Minimum per-pet cooldown in milliseconds after level reduction.", impact = "Higher values keep a longer floor on the save cooldown.")
    double minCooldownMillis = 60000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Ticks of invulnerability granted to a pet saved by Last Breath.", impact = "Higher values give the pet a longer protected window after being saved.")
    int invulnTicks = 60;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Xp granted each time Last Breath saves a pet.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerSave = 40;

    public Config() {
      costFactor = 0.7;
      initialCost = 4;
    }
  }
}
