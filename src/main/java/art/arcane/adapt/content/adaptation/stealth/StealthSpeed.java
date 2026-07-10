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

package art.arcane.adapt.content.adaptation.stealth;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.math.VelocitySpeed;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.math.M;
import org.bukkit.GameMode;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class StealthSpeed extends SimpleAdaptation<StealthSpeed.Config> {
  private static final Sound DEFAULT_ACTIVATION_SOUND = Sound.PARTICLE_SOUL_ESCAPE;
  private final Map<UUID, RuntimeState> states;

  public StealthSpeed() {
    super("stealth-speed");
    registerConfiguration(Config.class);
    setIcon(Material.MUSHROOM_STEW);
    setInterval(getConfig().setInterval);
    states = new ConcurrentHashMap<>();
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.LEATHER_BOOTS)
        .key("challenge_stealth_speed_5k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_stealth_speed_5k", "stealth.speed.blocks-sneak-sprinted", 5000, 400);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + "+ " + Form.pc(getSpeed(getLevelPercent(level)), 0) + C.GRAY + Localizer.dLocalize("stealth.speed.lore1"));
  }

  @EventHandler
  public void on(PlayerQuitEvent e) {
    clearAndRemoveState(e.getPlayer());
  }

  @EventHandler
  public void on(PlayerDeathEvent e) {
    clearAndRemoveState(e.getEntity());
  }

  @EventHandler
  public void on(PlayerToggleSneakEvent e) {
    Player player = e.getPlayer();
    if (e.isSneaking()) {
      startSession(player);
      return;
    }

    RuntimeState state = states.get(player.getUniqueId());
    if (state != null && !isCrawlingOnLand(player)) {
      clearAndRemoveState(player);
    }
  }

  @EventHandler
  public void on(PlayerMoveEvent e) {
    Player player = e.getPlayer();
    if (states.containsKey(player.getUniqueId())) {
      return;
    }

    if ((player.isSneaking() || isCrawlingOnLand(player)) && hasActiveAdaptation(player)) {
      startSession(player);
    }
  }

  @Override
  public void unregister() {
    super.unregister();
    for (RuntimeState state : states.values()) {
      state.refreshScheduled.set(false);
      Player owner = state.owner;
      J.runEntity(owner, () -> clearBoost(owner, state));
    }
    states.clear();
  }

  private void startSession(Player player) {
    UUID playerId = player.getUniqueId();
    RuntimeState state = states.computeIfAbsent(playerId, key -> new RuntimeState(player));
    if (state.refreshScheduled.compareAndSet(false, true)) {
      refreshSession(player, state);
    }
  }

  private void refreshSession(Player p, RuntimeState state) {
    UUID playerId = p.getUniqueId();
    if (states.get(playerId) != state || !p.isOnline()) {
      state.refreshScheduled.set(false);
      return;
    }

    boolean crawling = isCrawlingOnLand(p);
    if ((!p.isSneaking() && !crawling) || !hasActiveAdaptation(p)) {
      clearAndRemoveState(p);
      return;
    }

    long now = System.currentTimeMillis();
    long statIntervalMs = Math.max(50L, getConfig().statIntervalMs);

    if (!isEligible(p, crawling)) {
      clearBoost(p, state);
    } else {
      double levelFactor = getLevelPercent(p);
      float targetWalkSpeed = computeTargetWalkSpeed(state, p, levelFactor, crawling);
      applyBoost(p, state, targetWalkSpeed, now);
      applyAutoStep(p, state, now);

      if (isMovingHorizontally(p, getConfig().movementVelocityThreshold)) {
        if (getConfig().showSoulParticles && M.r(getConfig().soulParticleChance)) {
          fx(p.getLocation().clone().add(0, getConfig().soulParticleYOffset, 0), FxPriority.TRAIL)
              .particle(crawling ? Particle.ASH : Particle.SOUL, 1, 0, 0, 0, 0.12D, 0);
        }

        if (now - state.lastStatMillis >= statIntervalMs) {
          addStat(p, "stealth.speed.blocks-sneak-sprinted", 1);
          state.lastStatMillis = now;
        }
      }
    }

    int delayTicks = Math.max(1, (int) Math.ceil(Math.max(50L, getConfig().setInterval) / 50.0D));
    if (!J.runEntity(p, () -> refreshSession(p, state), delayTicks)) {
      clearAndRemoveState(p);
    }
  }

  private void applyBoost(Player p, RuntimeState state, float targetWalkSpeed, long now) {
    if (!state.boosting) {
      state.boosting = true;
      state.originalWalkSpeed = p.getWalkSpeed();

      long cooldown = Math.max(0, getConfig().activationSoundCooldownMs);
      if (cooldown <= 0 || now - state.lastSoundMillis >= cooldown) {
        Vector back = p.getLocation().getDirection().setY(0).multiply(-1);
        fx(p.getLocation().add(0, 0.15D, 0), FxPriority.TRANSITION)
            .particle(Particle.SOUL, 6, back.getX() * 0.3D, 0.1D, back.getZ() * 0.3D, 0.15D, 0.02D)
            .particle(Particle.CLOUD, 4, 0, 0.1D, 0, 0.2D, 0.01D)
            .chord(DEFAULT_ACTIVATION_SOUND, getConfig().activationSoundVolume, getConfig().activationSoundPitch, Sound.ITEM_TRIDENT_RIPTIDE_1, 0.3F, 1.4F);
        state.lastSoundMillis = now;
      }
    }

    float current = p.getWalkSpeed();
    if (Math.abs(current - targetWalkSpeed) > 0.0001f) {
      p.setWalkSpeed(targetWalkSpeed);
    }
  }

  private void clearBoost(Player p, RuntimeState state) {
    if (!state.boosting) {
      return;
    }

    state.boosting = false;
    float restore = clampWalkSpeed(state.originalWalkSpeed);
    float current = p.getWalkSpeed();
    if (Math.abs(current - restore) > 0.0001f) {
      p.setWalkSpeed(restore);
    }
  }

  private void clearAndRemoveState(Player p) {
    if (p == null) {
      return;
    }

    RuntimeState state = states.remove(p.getUniqueId());
    if (state == null) {
      return;
    }

    state.refreshScheduled.set(false);
    clearBoost(p, state);
  }

  private boolean isEligible(Player p, boolean crawlingOnLand) {
    if (!p.isSneaking() && !crawlingOnLand) {
      return false;
    }

    GameMode mode = p.getGameMode();
    if (mode != GameMode.SURVIVAL && mode != GameMode.ADVENTURE) {
      return false;
    }

    if (p.isDead() || p.getVehicle() != null || p.isFlying() || p.isGliding()) {
      return false;
    }

    if ((p.isSwimming() || p.isInWater()) && !crawlingOnLand && !getConfig().allowWhileInWater) {
      return false;
    }

    return !getConfig().requireGrounded || p.isOnGround();
  }

  private boolean isCrawlingOnLand(Player p) {
    if (p.getBoundingBox().getHeight() > getConfig().crawlHeightMax) {
      return false;
    }

    return !p.getEyeLocation().getBlock().isLiquid() && !p.getLocation().getBlock().isLiquid();
  }

  private float computeTargetWalkSpeed(RuntimeState state, Player p, double levelFactor, boolean crawling) {
    float base = state.boosting ? state.originalWalkSpeed : clampWalkSpeed(p.getWalkSpeed());
    if (!state.boosting && Math.abs(base) < 0.0001f) {
      base = clampWalkSpeed(getConfig().baselineWalkSpeed);
    }

    double bonus = getSpeed(levelFactor);
    if (crawling) {
      bonus *= Math.max(0, getConfig().crawlBonusMultiplier);
    }

    return clampWalkSpeed(base + (float) bonus);
  }

  private void applyAutoStep(Player p, RuntimeState state, long now) {
    if (!getConfig().enableAutoStep || !p.isOnGround()) {
      return;
    }

    long cooldown = Math.max(0, getConfig().autoStepCooldownMs);
    if (cooldown > 0 && now - state.lastStepMillis < cooldown) {
      return;
    }

    Vector direction = resolveAutoStepDirection(p);
    if (direction.lengthSquared() <= VelocitySpeed.EPSILON) {
      return;
    }

    double probe = Math.max(0.1, getConfig().autoStepProbeDistance);
    org.bukkit.Location feet = p.getLocation();
    org.bukkit.Location front = feet.clone().add(direction.multiply(probe));

    if (getConfig().enableAutoStepUp && tryStepUp(p, front, direction)) {
      state.lastStepMillis = now;
      return;
    }

    if (getConfig().enableAutoStepDown && tryStepDown(p, front, direction)) {
      state.lastStepMillis = now;
    }
  }

  private Vector resolveAutoStepDirection(Player p) {
    if (getConfig().autoStepUseInput) {
      try {
        Input input = p.getCurrentInput();
        if (input != null) {
          VelocitySpeed.InputSnapshot snapshot = new VelocitySpeed.InputSnapshot(input.isForward(), input.isBackward(), input.isLeft(), input.isRight());
          if (snapshot.hasHorizontal()) {
            Vector inputDirection = VelocitySpeed.resolveHorizontalDirection(p, snapshot);
            if (inputDirection.lengthSquared() > VelocitySpeed.EPSILON) {
              return inputDirection;
            }
          }
        }
      } catch (NoSuchMethodError ex) {
        // Runtime does not expose input API. Use velocity-based fallback.
        Adapt.verbose("Player input API is unavailable on this runtime; using velocity fallback for stealth auto-step.");
      }
    }

    Vector movement = new Vector(p.getVelocity().getX(), 0, p.getVelocity().getZ());
    double velocityThreshold = Math.max(0, getConfig().autoStepVelocityThreshold);
    if (movement.lengthSquared() <= velocityThreshold * velocityThreshold) {
      return new Vector();
    }

    return movement.normalize();
  }

  private boolean tryStepUp(Player p, org.bukkit.Location front, Vector direction) {
    if (!isStepObstacle(front, 0)) {
      return false;
    }

    if (!hasStepHeadroom(p, front)) {
      return false;
    }

    org.bukkit.Location destination = p.getLocation().clone()
        .add(direction.clone().multiply(Math.max(0.05, getConfig().autoStepForwardPush)))
        .add(0, 1, 0);
    if (!isDestinationSafe(p, destination, true)) {
      return false;
    }

    J.teleport(p, destination);
    stepFx(destination);
    return true;
  }

  private boolean tryStepDown(Player p, org.bukkit.Location front, Vector direction) {
    if (!isPassable(front, 0)) {
      return false;
    }

    if (!isPassable(front, -1)) {
      return false;
    }

    if (!isSolid(front, -2)) {
      return false;
    }

    org.bukkit.Location destination = p.getLocation().clone()
        .add(direction.clone().multiply(Math.max(0.05, getConfig().autoStepForwardPush)))
        .add(0, -1, 0);
    if (!isDestinationSafe(p, destination, true)) {
      return false;
    }

    J.teleport(p, destination);
    p.setFallDistance(0);
    stepFx(destination);
    return true;
  }

  private void stepFx(Location destination) {
    fx(destination, FxPriority.TRAIL)
        .particle(Particle.CLOUD, 2, 0, 0.05D, 0, 0.08D, 0.01D)
        .sound(Sound.BLOCK_WOOL_STEP, 0.2F, 1.2F);
  }

  private boolean isStepObstacle(org.bukkit.Location base, int yOffset) {
    org.bukkit.block.Block block = base.clone().add(0, yOffset, 0).getBlock();
    if (block.isLiquid() || block.isPassable() || !block.getType().isSolid()) {
      return false;
    }

    double obstacleHeight = block.getBoundingBox().getHeight();
    return obstacleHeight >= Math.max(0.05, getConfig().stepObstacleMinHeight);
  }

  private boolean isSolid(org.bukkit.Location base, int yOffset) {
    org.bukkit.block.Block block = base.clone().add(0, yOffset, 0).getBlock();
    return block.getType().isSolid() && !block.isLiquid() && !block.isPassable();
  }

  private boolean isPassable(org.bukkit.Location base, int yOffset) {
    org.bukkit.block.Block block = base.clone().add(0, yOffset, 0).getBlock();
    return block.isPassable() && !block.isLiquid();
  }

  private boolean hasStepHeadroom(Player p, org.bukkit.Location front) {
    if (!isPassable(front, 1)) {
      return false;
    }

    if (requiresDoubleHeadroom(p) && !isPassable(front, 2)) {
      return false;
    }

    return true;
  }

  private boolean isDestinationSafe(Player p, org.bukkit.Location destination, boolean requireFloor) {
    if (!isPassable(destination, 0)) {
      return false;
    }

    if (requiresDoubleHeadroom(p) && !isPassable(destination, 1)) {
      return false;
    }

    if (requireFloor && !isSolid(destination, -1)) {
      return false;
    }

    return true;
  }

  private boolean requiresDoubleHeadroom(Player p) {
    return p.getBoundingBox().getHeight() >= Math.max(0.5, getConfig().doubleHeadroomHeightThreshold);
  }

  private boolean isMovingHorizontally(Player p, double threshold) {
    Vector horizontal = new Vector(p.getVelocity().getX(), 0, p.getVelocity().getZ());
    double t = Math.max(0, threshold);
    return horizontal.lengthSquared() > t * t;
  }

  private float clampWalkSpeed(float value) {
    float min = Math.max(-1f, getConfig().minWalkSpeed);
    float max = Math.min(1f, Math.max(min, getConfig().maxWalkSpeed));
    return Math.max(min, Math.min(max, value));
  }

  private double getSpeed(double factor) {
    return Math.max(0, factor * getConfig().maxSpeedBonus);
  }

  private static class RuntimeState {
    private final Player owner;
    private final AtomicBoolean refreshScheduled = new AtomicBoolean();
    private boolean boosting;
    private float originalWalkSpeed = 0.2f;
    private long lastSoundMillis;
    private long lastStatMillis;
    private long lastStepMillis;

    private RuntimeState(Player owner) {
      this.owner = owner;
    }
  }

  @ConfigDescription("Gain speed while sneaking.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Tick interval (ms) used to update stealth speed.", impact = "Lower values feel more responsive but run updates more frequently.")
    long setInterval = 50;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Fallback baseline walk speed if no original speed has been captured yet.", impact = "Usually keep this at vanilla default unless another plugin changes baseline speeds globally.")
    float baselineWalkSpeed = 0.2f;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum additional walk speed granted at max level.", impact = "Higher values make stealth speed more noticeable.")
    double maxSpeedBonus = 0.8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Multiplier applied to bonus speed while crawling on land.", impact = "Higher values make crawling keep pace with sneaking.")
    double crawlBonusMultiplier = 1.15;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Minimum walk speed clamp used when applying the boost.", impact = "Keep near default to avoid unexpected slowdowns from conflicting systems.")
    float minWalkSpeed = -1f;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum walk speed clamp used when applying the boost.", impact = "Lower values are safer for anticheat; higher values feel faster.")
    float maxWalkSpeed = 1f;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Enables automatic vertical stepping while stealth speed is active.", impact = "Helps smooth sneaking over one-block terrain changes.")
    boolean enableAutoStep = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Allows stepping up one block while moving.", impact = "Reduces sneak interruption when encountering small ledges.")
    boolean enableAutoStepUp = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Allows stepping down one block while moving.", impact = "Only steps down when the drop is exactly one block.")
    boolean enableAutoStepDown = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Forward probe distance for auto-step checks.", impact = "Higher values detect ledges earlier but can feel more aggressive.")
    double autoStepProbeDistance = 0.45;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Horizontal push applied during each auto-step teleport.", impact = "Higher values move farther onto/off the next block and reduce repeat stepping in place.")
    double autoStepForwardPush = 0.36;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Uses direct movement input for auto-step direction when available.", impact = "Helps auto-step trigger while pressing into obstacles, even when velocity is near zero.")
    boolean autoStepUseInput = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Minimum horizontal velocity required before auto-step runs.", impact = "Higher values avoid accidental stepping while nearly idle.")
    double autoStepVelocityThreshold = 0.01;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Minimum delay between auto-step teleports.", impact = "Higher values reduce repeated stepping in tight terrain.")
    long autoStepCooldownMs = 90;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Minimum obstacle collision height that counts as a step-up blocker.", impact = "Higher values ignore small lips/slabs; lower values step up more aggressively.")
    double stepObstacleMinHeight = 0.75;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Bounding-box height above which two-block headroom is required for step-up.", impact = "Lower values are stricter; higher values allow sneaking/crawling to step in tighter spaces.")
    double doubleHeadroomHeightThreshold = 1.7;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum bounding-box height counted as crawling on land.", impact = "Higher values make crawl detection more permissive.")
    double crawlHeightMax = 0.61;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Requires players to be grounded for stealth speed to run.", impact = "True avoids midair acceleration and keeps behavior stable.")
    boolean requireGrounded = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Allows stealth speed to run while the player is in water.", impact = "False prevents stealth from overriding seaborne-style underwater movement effects.")
    boolean allowWhileInWater = false;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Minimum horizontal velocity used to count the player as moving for FX/stat tracking.", impact = "Higher values reduce effects while nearly stationary.")
    double movementVelocityThreshold = 0.005;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Shows a subtle soul particle near the player's feet while stealth speed is active.", impact = "Visual feedback visible only to the boosted player.")
    boolean showSoulParticles = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Chance per tick to spawn a soul particle while moving.", impact = "Higher values make the effect denser; lower values are subtler.")
    double soulParticleChance = 0.3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Vertical offset for the soul particle effect.", impact = "Small positive values keep particles around floor level.")
    double soulParticleYOffset = 0.02;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Activation sound volume heard by the boosted player.", impact = "Higher values are louder; lower values are subtler.")
    float activationSoundVolume = 1.6f;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Activation sound pitch heard by the boosted player.", impact = "Higher values raise tone; lower values deepen it.")
    float activationSoundPitch = 0.9f;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Minimum time between activation sounds.", impact = "Higher values reduce audio spam when repeatedly starting/stopping.")
    long activationSoundCooldownMs = 250;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Minimum time between progression stat increments while moving with stealth speed.", impact = "Controls how quickly the sneak-speed progression stat accumulates.")
    long statIntervalMs = 200;

    public Config() {
      baseCost = 2;
      costFactor = 0.6;
      initialCost = 5;
    }
  }
}
