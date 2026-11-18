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

package art.arcane.adapt.content.adaptation.chronos;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.ReceiveCancelledEvents;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.projectile.ProjectileCooperativeKeys;
import art.arcane.adapt.content.adaptation.ranged.RangedHeartseeker;
import art.arcane.adapt.localization.catalog.ChronosMessages;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.ProjectileHitEvent;
import art.arcane.adapt.platform.event.entity.ProjectileLaunchEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ChronosTemporalEcho extends SimpleAdaptation<ChronosTemporalEcho.Config> {
  public static final PlayerPreference<CommonPreferences.Toggle> ARROW = CommonPreferences.toggle("arrow", ChronosMessages.PREFERENCE_CHRONOSTEMPORALECHO_ARROW, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> SNOWBALL = CommonPreferences.toggle("snowball", ChronosMessages.PREFERENCE_CHRONOSTEMPORALECHO_SNOWBALL, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> EGG = CommonPreferences.toggle("egg", ChronosMessages.PREFERENCE_CHRONOSTEMPORALECHO_EGG, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> ENDER_PEARL = CommonPreferences.toggle("ender-pearl", ChronosMessages.PREFERENCE_CHRONOSTEMPORALECHO_ENDER_PEARL, CommonPreferences.Toggle.ON);

  private static final Id ARROW_TYPE = Id.minecraft("arrow");
  private static final Id SNOWBALL_TYPE = Id.minecraft("snowball");
  private static final Id EGG_TYPE = Id.minecraft("egg");
  private static final Id ENDER_PEARL_TYPE = Id.minecraft("ender_pearl");
  private static final Map<UUID, UUID> ECHO_OWNERS = new ConcurrentHashMap<>();
  private final Cooldowns cooldowns = cooldowns();

  public ChronosTemporalEcho() {
    super("chronos-temporal-echo");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("amethyst_cluster"));
    setInterval(1600);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("spectral_arrow"))
        .key("challenge_chronos_echo_200")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_chronos_echo_200", "chronos.temporal-echo.echo-hits", 200, 400);
  }


  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, ARROW, SNOWBALL, EGG, ENDER_PEARL);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.duration(getEchoDelayTicks(level) * 50D, 1), 1);
    statLore(v, Form.pc(getEchoVelocityFactor(level), 0), 2);
    statLore(v, C.YELLOW, "* ", Form.duration(getCooldownMillis(level), 1), 3);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(ProjectileLaunchEvent e) {
    ProjectileRef launched = e.projectile();
    if (RangedHeartseeker.isSeekingProjectile(launched)
        || !(launched.shooter() instanceof PlayerRef p)
        || !hasActiveAdaptation(p)
        || isEcho(launched)) {
      return;
    }

    EchoType echoType = getEchoType(launched);
    if (echoType == null || !preferenceEnabled(p, switch (echoType) {
      case ARROW -> ARROW;
      case SNOWBALL -> SNOWBALL;
      case EGG -> EGG;
      case ENDER_PEARL -> ENDER_PEARL;
    })) {
      return;
    }

    if (echoType == EchoType.ENDER_PEARL && !ProjectileCooperativeKeys.isUnclaimed(launched.data())) {
      return;
    }

    int level = getActiveLevel(p);
    if (!cooldowns.isReady(p.uuid(), getCooldownMillis(level))) {
      return;
    }

    cooldowns.mark(p.uuid());
    Vec3 originalVelocity = launched.velocity();
    int delay = getEchoDelayTicks(level);
    Tasks.runEntity(p, () -> spawnEcho(p, echoType, originalVelocity), delay);
    fx(p.eyeLocation(), FxPriority.TRAIL).particle(Particles.ENCHANTMENT_TABLE, 2, 0, 0, 0, 0.2D, 0.02D);
  }

  @ReceiveCancelledEvents
  @Handle(priority = EventPriority.LOWEST)
  public void on(ProjectileHitEvent e) {
    ProjectileRef projectile = e.projectile();
    if (!isEcho(projectile)) {
      return;
    }

    // Echo pearls must never teleport the shooter. Cancelling the hit does not stop the
    // vanilla pearl teleport on 26.2; only removing the entity before it resolves does.
    if (ENDER_PEARL_TYPE.equals(projectile.type())) {
      projectile.remove();
    }

    if (e.isCancelled() || !(e.hitEntity() instanceof LivingRef target)) {
      return;
    }

    UUID ownerId = getEchoOwnerId(projectile);
    if (isProtectedEchoTarget(ownerId, target)) {
      return;
    }

    target.setNoDamageTicks(0);
    target.setLastHurtAmount(0.0D);

    if (projectile.shooter() instanceof PlayerRef shooter) {
      Tasks.runEntity(shooter, () -> rewardEchoHit(shooter));
    }
  }

  private void rewardEchoHit(PlayerRef shooter) {
    if (resolveDelayedEchoLevel(shooter) <= 0) {
      return;
    }
    addStat(shooter, "chronos.temporal-echo.echo-hits", 1);
  }

  private void spawnEcho(PlayerRef p, EchoType type, Vec3 velocity) {
    int activeLevel = resolveDelayedEchoLevel(p);
    if (activeLevel <= 0) {
      return;
    }

    Loc spawnLocation = p.eyeLocation().add(p.location().direction().normalize().multiply(0.35));
    Id entityType = switch (type) {
      case ARROW -> ARROW_TYPE;
      case SNOWBALL -> SNOWBALL_TYPE;
      case EGG -> EGG_TYPE;
      case ENDER_PEARL -> ENDER_PEARL_TYPE;
    };
    if (!(p.world().spawn(entityType, spawnLocation, spawned -> {
    }) instanceof ProjectileRef echo)) {
      return;
    }
    echo.setShooter(p);
    echo.setVelocity(velocity.multiply(getEchoVelocityFactor(activeLevel)));
    pruneEchoOwners();
    ECHO_OWNERS.put(echo.uuid(), p.uuid());

    Loc spawnAt = echo.location();
    fx(spawnAt, FxPriority.COMBAT)
        .ring(Particles.REVERSE_PORTAL, 0.6D, 6, 0.0D)
        .chord(Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.75F, 1.35F, Sounds.BLOCK_AMETHYST_BLOCK_HIT, 0.4F, 1.1F);
    timeline(echo)
        .duration(4)
        .priority(FxPriority.TRAIL)
        .cullRadius(32)
        .frame((f, tick, progress) -> f.particle(Particles.REVERSE_PORTAL, 2, 0, 0, 0, 0.05D, 0.01D))
        .start();

    xp(p, getConfig().xpPerEcho);
  }

  int resolveDelayedEchoLevel(PlayerRef player) {
    if (!player.isOnline() || player.isDead()) {
      return 0;
    }
    return Math.max(0, getActiveLevel(player));
  }

  private static boolean isEcho(EntityRef projectile) {
    return ECHO_OWNERS.containsKey(projectile.uuid());
  }

  private static UUID getEchoOwnerId(EntityRef projectile) {
    return ECHO_OWNERS.get(projectile.uuid());
  }

  private static void pruneEchoOwners() {
    ECHO_OWNERS.keySet().removeIf(id -> {
      EntityRef entity = AdaptCore.platform().host().entity(id);
      return entity == null || !entity.isValid();
    });
  }

  private boolean isProtectedEchoTarget(UUID ownerId, LivingRef target) {
    if (isProtectedFriendly(null, target)) {
      return true;
    }
    if (ownerId == null || !target.has(EntityTrait.TAMEABLE)) {
      return false;
    }
    UUID tamer = target.tameOwner();
    return tamer != null && ownerId.equals(tamer);
  }

  private EchoType getEchoType(ProjectileRef projectile) {
    Id type = projectile.type();
    if (ARROW_TYPE.equals(type)) {
      return EchoType.ARROW;
    }

    if (SNOWBALL_TYPE.equals(type)) {
      return EchoType.SNOWBALL;
    }

    if (EGG_TYPE.equals(type)) {
      return EchoType.EGG;
    }

    if (ENDER_PEARL_TYPE.equals(type)) {
      return EchoType.ENDER_PEARL;
    }

    return null;
  }

  private int getEchoDelayTicks(int level) {
    return Math.max(1, (int) Math.round(getConfig().echoDelayTicksBase - (getLevelPercent(level) * getConfig().echoDelayTicksFactor)));
  }

  private double getEchoVelocityFactor(int level) {
    return Math.min(getConfig().maxEchoVelocityFactor, getConfig().echoVelocityFactorBase + (getLevelPercent(level) * getConfig().echoVelocityFactorFactor));
  }

  private long getCooldownMillis(int level) {
    return Math.max(500L, (long) Math.round(getConfig().cooldownMillisBase - (getLevelPercent(level) * getConfig().cooldownMillisFactor)));
  }


  private enum EchoType {
    ARROW,
    SNOWBALL,
    EGG,
    ENDER_PEARL
  }

  @ConfigDescription("Replays your last projectile action shortly after firing at reduced power.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Echo Delay Ticks Base for the Chronos Temporal Echo adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double echoDelayTicksBase = 18;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Echo Delay Ticks Factor for the Chronos Temporal Echo adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double echoDelayTicksFactor = 10;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Echo Velocity Factor Base for the Chronos Temporal Echo adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double echoVelocityFactorBase = 0.45;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Echo Velocity Factor Factor for the Chronos Temporal Echo adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double echoVelocityFactorFactor = 0.45;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Echo Velocity Factor for the Chronos Temporal Echo adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxEchoVelocityFactor = 0.92;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Millis Base for the Chronos Temporal Echo adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownMillisBase = 5000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Millis Factor for the Chronos Temporal Echo adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownMillisFactor = 2600;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Echo for the Chronos Temporal Echo adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerEcho = 12;

    public Config() {
      baseCost = 5;
      costFactor = 0.75;
      initialCost = 5;
    }
  }
}
