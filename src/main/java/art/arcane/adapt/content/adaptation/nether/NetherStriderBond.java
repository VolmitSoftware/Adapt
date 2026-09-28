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

package art.arcane.adapt.content.adaptation.nether;

import java.util.List;
import art.arcane.adapt.localization.catalog.NetherMessages;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.Adapt;
import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.util.common.compat.PaperCompat;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.events.api.ReflectiveHandler;
import art.arcane.adapt.util.reflect.events.api.entity.EntityDismountEvent;
import art.arcane.adapt.util.reflect.registries.Attributes;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.Strider;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.concurrent.CompletableFuture;
import java.util.Set;

public class NetherStriderBond extends SimpleAdaptation<NetherStriderBond.Config> {
  public static final PlayerPreference<CommonPreferences.Toggle> SPEED = CommonPreferences.toggle("speed", NetherMessages.NETHERSTRIDERBOND_PREFERENCE_SPEED, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> RESCUE = CommonPreferences.toggle("rescue", NetherMessages.NETHERSTRIDERBOND_PREFERENCE_RESCUE, CommonPreferences.Toggle.ON);

  private static final String SLOT_RIDE = "ride";
  private static final long SPEED_REFRESH_MILLIS = 500L;
  private static final Set<Material> RESCUE_AIR = Set.of(Material.AIR, Material.CAVE_AIR, Material.VOID_AIR);

  public NetherStriderBond() {
    super("nether-strider-bond");
    registerConfiguration(Config.class);
    setIcon(Material.WARPED_FUNGUS_ON_A_STICK);
    setInterval(2000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.WARPED_FUNGUS_ON_A_STICK)
        .key("challenge_nether_strider_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Material.SADDLE)
            .key("challenge_nether_strider_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.LAVA_BUCKET)
        .key("challenge_nether_strider_rescue")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.HIDDEN)
        .build());
    registerMilestone("challenge_nether_strider_500", "nether.strider-bond.blocks-ridden", 500, 300);
    registerMilestone("challenge_nether_strider_5k", "nether.strider-bond.blocks-ridden", 5000, 1000);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, SPEED, RESCUE);
  }

  @Override
  public void onPlayerPreferencesChanged(AdaptPlayer player) {
    if (player.getPlayer().getVehicle() instanceof Strider strider) {
      J.runEntity(strider, () -> AdaptAttributeService.get().removeAll(strider, getName()));
    }
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, getStriderSpeedAmplifier(level) + 1, 1);
    statLore(v, getSearchRadius(level), 2);
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void on(PlayerMoveEvent e) {
    Player p = e.getPlayer();
    if (!(p.getVehicle() instanceof Strider strider)) {
      return;
    }

    withAdaptedPlayer(p, e, () -> {
      int level = getActiveLevel(p);
      strider.setShivering(false);
      if (preferenceEnabled(p, SPEED)) {
        refreshStriderSpeed(p, strider, level);
      }

      Location from = e.getFrom();
      Location to = e.getTo();
      if (to == null || to.getWorld() != from.getWorld()) {
        return;
      }

      double dx = to.getX() - from.getX();
      double dz = to.getZ() - from.getZ();
      double dist = Math.sqrt((dx * dx) + (dz * dz));
      if (dist <= 0.02D || dist >= 10D) {
        return;
      }

      addStat(p, "nether.strider-bond.blocks-ridden", dist);
      long now = System.currentTimeMillis();
      if (getStorageLong(p, "striderBondXpNext", 0L) <= now) {
        setStorage(p, "striderBondXpNext", now + getConfig().xpIntervalMillis);
        xp(p, getConfig().xpPerRide);
      }
    });
  }

  @ReflectiveHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityDismountEvent e) {
    if (!(e.getEntity() instanceof Player p) || !(e.getDismounted() instanceof Strider)) {
      return;
    }

    int level = getActiveLevel(p);
    if (level < getConfig().safetyUnlockLevel || !preferenceEnabled(p, RESCUE)) {
      return;
    }

    int radius = getSearchRadius(level);
    J.runEntity(p, () -> {
      if (!canContinueRescue(p)) {
        return;
      }

      Location loc = p.getLocation();
      if (!isOverLava(loc)) {
        return;
      }

      Location safe = findSafeLanding(loc, radius);
      if (safe == null) {
        return;
      }

      beginRescueTeleport(p, safe);
    }, 2);
  }

  private void beginRescueTeleport(Player p, Location safe) {
    CompletableFuture<Boolean> teleport;
    try {
      teleport = PaperCompat.teleportAsync(p, safe, PlayerTeleportEvent.TeleportCause.PLUGIN);
    } catch (RuntimeException error) {
      Adapt.error("Strider Bond could not start a lava rescue for " + p.getUniqueId() + ".");
      Adapt.error(error);
      return;
    }
    if (teleport == null) {
      return;
    }
    teleport.whenComplete((success, failure) -> finishRescueTeleport(p, success, failure));
  }

  private void finishRescueTeleport(Player p, Boolean success, Throwable failure) {
    if (failure != null) {
      Adapt.error("Strider Bond lava rescue failed for " + p.getUniqueId() + ".");
      Adapt.error(failure);
    }

    J.runEntity(p, () -> {
      if (!canContinueRescue(p) || !shouldCommitRescue(success, failure, true)) {
        return;
      }
      AdaptPlayer adaptPlayer = getPlayer(p);
      if (adaptPlayer == null) {
        return;
      }

      p.setFallDistance(0F);
      addStat(p, "nether.strider-bond.lava-rescues", 1);
      xp(p, getConfig().xpPerRescue);
      fx(p.getLocation(), FxPriority.TRANSITION)
          .dustRing(0.8D, 12, 1.0F)
          .particle(Particle.CLOUD, 6, 0D, 0.2D, 0D, 0.3D, 0.02D)
          .chord(Sound.ENTITY_STRIDER_HAPPY, 0.6F, 1.2F, Sound.ENTITY_ENDERMAN_TELEPORT, 0.4F, 1.4F);
      if (AdaptConfig.get().isAdvancements() && !adaptPlayer.getData().isGranted("challenge_nether_strider_rescue")) {
        adaptPlayer.getAdvancementHandler().grant("challenge_nether_strider_rescue");
      }
    });
  }

  private boolean canContinueRescue(Player player) {
    return isRuntimeRegistered() && player.isOnline() && !player.isDead() && !player.isInsideVehicle()
        && preferenceEnabled(player, RESCUE) && getActiveLevel(player) >= getConfig().safetyUnlockLevel;
  }

  static boolean shouldCommitRescue(Boolean success, Throwable failure, boolean online) {
    return online && failure == null && Boolean.TRUE.equals(success);
  }

  private boolean isOverLava(Location loc) {
    return isLavaBelowDismount(loc.getBlock().getType(),
        loc.clone().add(0D, -1D, 0D).getBlock().getType(),
        loc.clone().add(0D, -2D, 0D).getBlock().getType());
  }

  static boolean isLavaBelowDismount(Material feet, Material below, Material belowStriderHeight) {
    return feet == Material.LAVA
        || (RESCUE_AIR.contains(feet) && (below == Material.LAVA || (RESCUE_AIR.contains(below) && belowStriderHeight == Material.LAVA)));
  }

  private Location findSafeLanding(Location origin, int radius) {
    World world = origin.getWorld();
    if (world == null) {
      return null;
    }

    int ox = origin.getBlockX();
    int oy = origin.getBlockY();
    int oz = origin.getBlockZ();
    for (int r = 1; r <= radius; r++) {
      for (int dx = -r; dx <= r; dx++) {
        for (int dz = -r; dz <= r; dz++) {
          if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
            continue;
          }

          int x = ox + dx;
          int z = oz + dz;
          if (J.isFoliaThreading() && !J.isOwnedByCurrentRegion(new Location(world, x, oy, z))) {
            continue;
          }
          for (int dy = 2; dy >= -4; dy--) {
            int y = oy + dy;
            Block ground = world.getBlockAt(x, y, z);
            if (!isSolidGround(ground)) {
              continue;
            }

            if (isPassable(world.getBlockAt(x, y + 1, z)) && isPassable(world.getBlockAt(x, y + 2, z))) {
              return new Location(world, x + 0.5D, y + 1D, z + 0.5D, origin.getYaw(), origin.getPitch());
            }
          }
        }
      }
    }

    return null;
  }

  private boolean isSolidGround(Block b) {
    Material t = b.getType();
    if (!t.isSolid()) {
      return false;
    }

    return t != Material.LAVA
        && t != Material.MAGMA_BLOCK
        && t != Material.CAMPFIRE
        && t != Material.SOUL_CAMPFIRE
        && !isFire(t);
  }

  private boolean isPassable(Block b) {
    Material t = b.getType();
    return b.isPassable() && t != Material.LAVA && !isFire(t);
  }

  private boolean isFire(Material t) {
    return t == Material.FIRE || t == Material.SOUL_FIRE;
  }

  private void refreshStriderSpeed(Player p, Strider strider, int level) {
    int durationTicks = getConfig().speedTicks;
    if (durationTicks <= 0) {
      return;
    }

    int amplifier = getStriderSpeedAmplifier(level);
    long now = System.currentTimeMillis();
    String mountId = strider.getUniqueId().toString();
    boolean sameMount = mountId.equals(getStorageString(p, "striderBondMountId", ""));
    long until = getStorageLong(p, "striderBondSpeedUntil", 0L);
    int applied = getStorageInt(p, "striderBondSpeedAmp", -1);
    if (sameMount && applied >= amplifier && until - now >= SPEED_REFRESH_MILLIS) {
      return;
    }

    setStorage(p, "striderBondMountId", mountId);
    setStorage(p, "striderBondSpeedUntil", now + (durationTicks * 50L));
    setStorage(p, "striderBondSpeedAmp", amplifier);
    AdaptAttributeService.get().applyTimed(strider, getName(), SLOT_RIDE, Attributes.MOVEMENT_SPEED, striderSpeedBonus(amplifier), AttributeModifier.Operation.MULTIPLY_SCALAR_1, durationTicks);
  }

  private int getStriderSpeedAmplifier(int level) {
    return striderSpeedAmplifier(getLevelPercent(level), getConfig().striderSpeedAmplifierBase, getConfig().striderSpeedAmplifierFactor);
  }

  private int getSearchRadius(int level) {
    return searchRadius(getLevelPercent(level), getConfig().searchRadiusBase, getConfig().searchRadiusFactor, getConfig().searchRadiusMax);
  }

  static int striderSpeedAmplifier(double levelPercent, double base, double factor) {
    return Math.max(0, (int) Math.round(base + (levelPercent * factor)));
  }

  static double striderSpeedBonus(int amplifier) {
    return 0.2D * (amplifier + 1);
  }

  static int searchRadius(double levelPercent, double base, double factor, int cap) {
    return Math.min(cap, Math.max(1, (int) Math.round(base + (levelPercent * factor))));
  }


  @ConfigDescription("Ride striders faster, keep their pace out of lava, and land safely when dismounting over lava.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Strider Speed Amplifier Base for the Nether Strider Bond adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double striderSpeedAmplifierBase = 0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Strider Speed Amplifier Factor for the Nether Strider Bond adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double striderSpeedAmplifierFactor = 1.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Speed Ticks for the Nether Strider Bond adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int speedTicks = 60;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Safety Unlock Level for the Nether Strider Bond adaptation.", impact = "Higher values require more levels before the lava-dismount rescue activates.")
    int safetyUnlockLevel = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Search Radius Base for the Nether Strider Bond adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double searchRadiusBase = 4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Search Radius Factor for the Nether Strider Bond adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double searchRadiusFactor = 4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Search Radius Max for the Nether Strider Bond adaptation.", impact = "Caps the block search radius to keep the rescue scan within the local region.")
    int searchRadiusMax = 8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Ride for the Nether Strider Bond adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerRide = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Interval Millis for the Nether Strider Bond adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long xpIntervalMillis = 1500;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Rescue for the Nether Strider Bond adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerRescue = 30;

    public Config() {
      baseCost = 3;
      costFactor = 0.6;
      maxLevel = 4;
      initialCost = 4;
    }
  }
}
