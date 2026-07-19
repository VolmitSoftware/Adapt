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

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.fx.FxPriority;
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

public class NetherStriderBond extends SimpleAdaptation<NetherStriderBond.Config> {
  private static final String SLOT_RIDE = "ride";
  private static final long SPEED_REFRESH_MILLIS = 500L;

  public NetherStriderBond() {
    super("nether-strider-bond");
    registerConfiguration(Config.class);
    setIcon(Material.WARPED_FUNGUS_ON_A_STICK);
    setInterval(2000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.WARPED_FUNGUS_ON_A_STICK)
        .key("challenge_nether_strider_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.SADDLE)
            .key("challenge_nether_strider_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
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
      refreshStriderSpeed(p, strider, level);

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

  @ReflectiveHandler
  public void on(EntityDismountEvent e) {
    if (!(e.getEntity() instanceof Player p) || !(e.getDismounted() instanceof Strider)) {
      return;
    }

    int level = getActiveLevel(p);
    if (level < getConfig().safetyUnlockLevel) {
      return;
    }

    int radius = getSearchRadius(level);
    J.runEntity(p, () -> {
      if (!p.isOnline()) {
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

      p.teleport(safe);
      p.setFallDistance(0F);
      addStat(p, "nether.strider-bond.lava-rescues", 1);
      xp(p, getConfig().xpPerRescue);
      fx(safe, FxPriority.TRANSITION)
          .dustRing(0.8D, 12, 1.0F)
          .particle(Particle.CLOUD, 6, 0D, 0.2D, 0D, 0.3D, 0.02D)
          .chord(Sound.ENTITY_STRIDER_HAPPY, 0.6F, 1.2F, Sound.ENTITY_ENDERMAN_TELEPORT, 0.4F, 1.4F);
      if (AdaptConfig.get().isAdvancements() && !getPlayer(p).getData().isGranted("challenge_nether_strider_rescue")) {
        getPlayer(p).getAdvancementHandler().grant("challenge_nether_strider_rescue");
      }
    }, 2);
  }

  private boolean isOverLava(Location loc) {
    return loc.getBlock().getType() == Material.LAVA
        || loc.clone().add(0D, -1D, 0D).getBlock().getType() == Material.LAVA;
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

          for (int dy = 2; dy >= -4; dy--) {
            int x = ox + dx;
            int y = oy + dy;
            int z = oz + dz;
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
