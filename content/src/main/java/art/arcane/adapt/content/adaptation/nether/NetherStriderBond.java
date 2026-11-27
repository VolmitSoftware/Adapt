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

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
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
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.hud.HudStatus;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.TeleportCause;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDismountEvent;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class NetherStriderBond extends SimpleAdaptation<NetherStriderBond.Config> {
  public static final Id ID = Id.adapt("nether/strider_bond");
  public static final PlayerPreference<CommonPreferences.Toggle> SPEED = CommonPreferences.toggle("speed", Text.key("adapt.netherstriderbond.preferences.preference_speed"), CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> RESCUE = CommonPreferences.toggle("rescue", Text.key("adapt.netherstriderbond.preferences.preference_rescue"), CommonPreferences.Toggle.ON);

  private static final String SLOT_RIDE = "ride";
  private static final long SPEED_REFRESH_MILLIS = 500L;
  private static final Id STRIDER = Id.minecraft("strider");
  private static final Id LAVA = Id.minecraft("lava");
  private static final Id MAGMA_BLOCK = Id.minecraft("magma_block");
  private static final Id CAMPFIRE = Id.minecraft("campfire");
  private static final Id SOUL_CAMPFIRE = Id.minecraft("soul_campfire");
  private static final Id FIRE = Id.minecraft("fire");
  private static final Id SOUL_FIRE = Id.minecraft("soul_fire");
  private static final Set<Id> RESCUE_AIR = Set.of(Id.minecraft("air"), Id.minecraft("cave_air"), Id.minecraft("void_air"));

  public NetherStriderBond() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("warped_fungus_on_a_stick"));
    setInterval(2000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("warped_fungus_on_a_stick"))
        .key("challenge_nether_strider_500")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("saddle"))
            .key("challenge_nether_strider_5k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("lava_bucket"))
        .key("challenge_nether_strider_rescue")
        .frame(AdvancementFrame.CHALLENGE)
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
  public void onPlayerPreferencesChanged(PlayerProfile player) {
    if (ridingStrider(player.player()) instanceof LivingRef strider) {
      AdaptApi.scheduler().runEntity(strider, () -> AdaptAttributeService.get().removeAll(strider, getId().toString()));
    }
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, getStriderSpeedAmplifier(level) + 1, 1);
    statLore(v, getSearchRadius(level), 2);
  }

  private static EntityRef ridingStrider(PlayerRef p) {
    EntityRef vehicle = p.vehicle();
    return vehicle != null && vehicle.type().equals(STRIDER) ? vehicle : null;
  }

  @Handle(priority = EventPriority.HIGH)
  public void on(PlayerMoveEvent e) {
    PlayerRef p = e.player();
    if (!(ridingStrider(p) instanceof LivingRef strider)) {
      return;
    }

    withAdaptedPlayer(p, e, () -> {
      int level = getActiveLevel(p);
      AdaptApi.platform().host().animals().setShivering(strider, false);
      if (preferenceEnabled(p, SPEED)) {
        refreshStriderSpeed(p, strider, level);
      }

      Loc from = e.from();
      Loc to = e.to();
      if (to == null || !from.sameWorld(to)) {
        return;
      }

      double dx = to.x() - from.x();
      double dz = to.z() - from.z();
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

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDismountEvent e) {
    if (!(e.entity() instanceof PlayerRef p) || !e.dismounted().type().equals(STRIDER)) {
      return;
    }

    int level = getActiveLevel(p);
    if (level < getConfig().safetyUnlockLevel || !preferenceEnabled(p, RESCUE)) {
      return;
    }

    int radius = getSearchRadius(level);
    AdaptApi.scheduler().runEntity(p, () -> {
      if (!canContinueRescue(p)) {
        return;
      }

      Loc loc = p.location();
      if (!isOverLava(loc)) {
        return;
      }

      Loc safe = findSafeLanding(loc, radius);
      if (safe == null) {
        return;
      }

      beginRescueTeleport(p, safe);
    }, 2);
  }

  private void beginRescueTeleport(PlayerRef p, Loc safe) {
    CompletableFuture<Boolean> teleport;
    try {
      teleport = p.teleport(safe, TeleportCause.PLUGIN);
    } catch (RuntimeException error) {
      AdaptApi.log().error("Strider Bond could not start a lava rescue for " + p.uuid() + ".", null);
      AdaptApi.log().error("NetherStriderBond failed", error);
      return;
    }
    if (teleport == null) {
      return;
    }
    teleport.whenComplete((success, failure) -> finishRescueTeleport(p, success, failure));
  }

  private void finishRescueTeleport(PlayerRef p, Boolean success, Throwable failure) {
    if (failure != null) {
      AdaptApi.log().error("Strider Bond lava rescue failed for " + p.uuid() + ".", null);
      AdaptApi.log().error("NetherStriderBond failed", failure);
    }

    AdaptApi.scheduler().runEntity(p, () -> {
      if (!canContinueRescue(p) || !shouldCommitRescue(success, failure, true)) {
        return;
      }
      PlayerProfile adaptPlayer = getPlayer(p);
      if (adaptPlayer == null) {
        return;
      }

      p.setFallDistance(0F);
      addStat(p, "nether.strider-bond.lava-rescues", 1);
      xp(p, getConfig().xpPerRescue);
      fx(p.location(), FxPriority.TRANSITION)
          .dustRing(0.8D, 12, 1.0F)
          .particle(Particles.CLOUD, 6, 0D, 0.2D, 0D, 0.3D, 0.02D)
          .chord(Sounds.ENTITY_STRIDER_HAPPY, 0.6F, 1.2F, Sounds.ENTITY_ENDERMAN_TELEPORT, 0.4F, 1.4F);
      if (AdaptApi.settings().advancements() && !adaptPlayer.isGranted(getId(), "challenge_nether_strider_rescue")) {
        adaptPlayer.grant(getId(), "challenge_nether_strider_rescue");
      }
    });
  }

  private boolean canContinueRescue(PlayerRef player) {
    return isRuntimeRegistered() && player.isOnline() && !player.isDead() && player.vehicle() == null
        && preferenceEnabled(player, RESCUE) && getActiveLevel(player) >= getConfig().safetyUnlockLevel;
  }

  static boolean shouldCommitRescue(Boolean success, Throwable failure, boolean online) {
    return online && failure == null && Boolean.TRUE.equals(success);
  }

  private boolean isOverLava(Loc loc) {
    WorldRef world = loc.world();
    return isLavaBelowDismount(world.blockAt(loc).type(),
        world.blockAt(loc.add(0D, -1D, 0D)).type(),
        world.blockAt(loc.add(0D, -2D, 0D)).type());
  }

  static boolean isLavaBelowDismount(Id feet, Id below, Id belowStriderHeight) {
    return feet.equals(LAVA)
        || (RESCUE_AIR.contains(feet) && (below.equals(LAVA) || (RESCUE_AIR.contains(below) && belowStriderHeight.equals(LAVA))));
  }

  private Loc findSafeLanding(Loc origin, int radius) {
    WorldRef world = origin.world();
    if (world == null) {
      return null;
    }

    int ox = origin.blockX();
    int oy = origin.blockY();
    int oz = origin.blockZ();
    for (int r = 1; r <= radius; r++) {
      for (int dx = -r; dx <= r; dx++) {
        for (int dz = -r; dz <= r; dz++) {
          if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
            continue;
          }

          int x = ox + dx;
          int z = oz + dz;
          if (AdaptApi.scheduler().isRegionized() && !AdaptApi.scheduler().owns(new Loc(world, x, oy, z))) {
            continue;
          }
          for (int dy = 2; dy >= -4; dy--) {
            int y = oy + dy;
            BlockRef ground = world.blockAt(x, y, z);
            if (!isSolidGround(ground)) {
              continue;
            }

            if (isPassable(world.blockAt(x, y + 1, z)) && isPassable(world.blockAt(x, y + 2, z))) {
              return new Loc(world, x + 0.5D, y + 1D, z + 0.5D, origin.yaw(), origin.pitch());
            }
          }
        }
      }
    }

    return null;
  }

  private boolean isSolidGround(BlockRef b) {
    if (!b.isSolid()) {
      return false;
    }

    Id t = b.type();
    return !t.equals(LAVA)
        && !t.equals(MAGMA_BLOCK)
        && !t.equals(CAMPFIRE)
        && !t.equals(SOUL_CAMPFIRE)
        && !isFire(t);
  }

  private boolean isPassable(BlockRef b) {
    Id t = b.type();
    return b.isPassable() && !t.equals(LAVA) && !isFire(t);
  }

  private boolean isFire(Id t) {
    return t.equals(FIRE) || t.equals(SOUL_FIRE);
  }

  private void refreshStriderSpeed(PlayerRef p, LivingRef strider, int level) {
    int durationTicks = getConfig().speedTicks;
    if (durationTicks <= 0) {
      return;
    }

    int amplifier = getStriderSpeedAmplifier(level);
    long now = System.currentTimeMillis();
    String mountId = strider.uuid().toString();
    boolean sameMount = mountId.equals(getStorageString(p, "striderBondMountId", ""));
    long until = getStorageLong(p, "striderBondSpeedUntil", 0L);
    int applied = getStorageInt(p, "striderBondSpeedAmp", -1);
    if (sameMount && applied >= amplifier && until - now >= SPEED_REFRESH_MILLIS) {
      return;
    }

    setStorage(p, "striderBondMountId", mountId);
    setStorage(p, "striderBondSpeedUntil", now + (durationTicks * 50L));
    HudStatus.duration(p.uuid(), this, now, durationTicks * 50L);
    setStorage(p, "striderBondSpeedAmp", amplifier);
    AdaptAttributeService.get().applyTimed(strider, getId().toString(), SLOT_RIDE, Attributes.MOVEMENT_SPEED, striderSpeedBonus(amplifier), AttributeOperation.ADD_MULTIPLIED_TOTAL, durationTicks);
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
    @ConfigDoc(value = "Controls Strider Speed Amplifier Base for the Nether Strider Bond adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double striderSpeedAmplifierBase = 0;
    @ConfigDoc(value = "Controls Strider Speed Amplifier Factor for the Nether Strider Bond adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double striderSpeedAmplifierFactor = 1.5;
    @ConfigDoc(value = "Controls Speed Ticks for the Nether Strider Bond adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int speedTicks = 60;
    @ConfigDoc(value = "Controls Safety Unlock Level for the Nether Strider Bond adaptation.", impact = "Higher values require more levels before the lava-dismount rescue activates.")
    int safetyUnlockLevel = 2;
    @ConfigDoc(value = "Controls Search Radius Base for the Nether Strider Bond adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double searchRadiusBase = 4;
    @ConfigDoc(value = "Controls Search Radius Factor for the Nether Strider Bond adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double searchRadiusFactor = 4;
    @ConfigDoc(value = "Controls Search Radius Max for the Nether Strider Bond adaptation.", impact = "Caps the block search radius to keep the rescue scan within the local region.")
    int searchRadiusMax = 8;
    @ConfigDoc(value = "Controls Xp Per Ride for the Nether Strider Bond adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerRide = 2;
    @ConfigDoc(value = "Controls Xp Interval Millis for the Nether Strider Bond adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long xpIntervalMillis = 1500;
    @ConfigDoc(value = "Controls Xp Per Rescue for the Nether Strider Bond adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerRescue = 30;

    public Config() {
      baseCost = 1;
      costFactor = 0.5;
      maxLevel = 4;
      initialCost = 1;
    }
  }
}
