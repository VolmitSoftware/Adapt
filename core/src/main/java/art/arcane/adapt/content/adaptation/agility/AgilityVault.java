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

package art.arcane.adapt.content.adaptation.agility;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.AdaptationOwnerPulse;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.support.PlatformNeed;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.localization.catalog.AgilityMessages;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.GameMode;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerDeathEvent;
import art.arcane.adapt.platform.event.player.PlayerGameModeChangeEvent;
import art.arcane.adapt.platform.event.player.PlayerJumpEvent;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.player.PlayerTeleportEvent;
import art.arcane.adapt.platform.event.server.ResourcesReloadedEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.common.world.BlockTagSet;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

public class AgilityVault extends SimpleAdaptation<AgilityVault.Config> {
  public static final PlayerPreference<Trigger> TRIGGER = new PlayerPreference<>(Trigger.class,
      new PlayerPreference.Definition<>("trigger", AgilityMessages.AGILITYVAULT_PREFERENCE_TRIGGER, Trigger.ANY, List.of(
          new PlayerPreference.Choice<>(Trigger.ANY, AgilityMessages.AGILITYVAULT_PREFERENCE_TRIGGER_ANY, Id.minecraft("feather"), 1),
          new PlayerPreference.Choice<>(Trigger.SPRINT, AgilityMessages.AGILITYVAULT_PREFERENCE_TRIGGER_SPRINT, Id.minecraft("sugar"), 1),
          new PlayerPreference.Choice<>(Trigger.SNEAK, AgilityMessages.AGILITYVAULT_PREFERENCE_TRIGGER_SNEAK, Id.minecraft("leather_boots"), 1))));

  private static final String SLOT_VAULT = "vault";
  private static final int VAULT_LEVELS = 1;
  private static final long RECONCILE_INTERVAL_MILLIS = 1000L;
  private static final double MINIMUM_VAULT_HEIGHT = 1.65D;
  private static final double MINIMUM_DIRECTION_SQUARED = 1.0E-6D;
  private static final double FENCE_PROBE_START = 0.35D;
  private static final double FENCE_PROBE_STEP = 0.20D;
  private static final double FENCE_PROBE_LIMIT = 1.60D;
  private static final BlockTagSet FENCES = new BlockTagSet(Id.minecraft("fences"));

  private final Map<UUID, Double> armedBonuses = playerState();
  private final AdaptationOwnerPulse.Registration ownerMaintenance;

  public AgilityVault() {
    super("agility-vault");
    needs(PlatformNeed.attribute(Attributes.JUMP_STRENGTH));
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("oak_fence"));
    setCostFactor(0D);
    setMaxLevel(VAULT_LEVELS);
    setInterval(RECONCILE_INTERVAL_MILLIS);
    ownerMaintenance = AdaptationOwnerPulse.register(
        this,
        this::getInterval,
        () -> !armedBonuses.isEmpty(),
        this::requiresOwnerMaintenance,
        this::reconcilePlayer
    );
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("oak_fence"))
        .key("challenge_agility_vault_250")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("nether_brick_fence"))
            .key("challenge_agility_vault_2500")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_agility_vault_250", "agility.vault.vaults", 250, 300);
    registerMilestone("challenge_agility_vault_2500", "agility.vault.vaults", 2500, 1200);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, TRIGGER);
  }

  @Override
  public void onPlayerPreferencesChanged(AdaptPlayer player) {
    clearArmedState(player.getPlayer());
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.f(vaultHeight(getConfig().jumpHeight), 2), 1);
  }

  @Override
  public void unregister() {
    ownerMaintenance.unregister();
    armedBonuses.clear();
    super.unregister();
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerMoveEvent e) {
    if (e.to() == null) {
      return;
    }

    PlayerRef p = e.player();
    if (!p.isOnGround() || !isVaultEligible(p)) {
      if (!armedBonuses.isEmpty()) {
        withPlayerThread(p, e, () -> disarm(p));
      }
      return;
    }

    Loc target = e.to();
    Vec3 direction = horizontalDirection(target.position().subtract(e.from().position()), target.direction());
    withPlayerThread(p, e, () -> refreshPreArm(p, target, direction));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerJumpEvent e) {
    PlayerRef p = e.player();
    if (!hasActiveAdaptation(p)) {
      return;
    }

    Loc origin = e.from();
    Vec3 direction = horizontalDirection(e.to().position().subtract(origin.position()), origin.direction());
    withPlayerThread(p, e, () -> attemptVault(p, origin, direction));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerTeleportEvent e) {
    withPlayerThread(e.player(), e, () -> clearArmedState(e.player()));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerGameModeChangeEvent e) {
    clearArmedState(e.player());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerDeathEvent e) {
    clearArmedState(e.player());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    clearArmedState(e.player());
  }

  @Handle
  public void on(ResourcesReloadedEvent e) {
    FENCES.invalidate();
  }

  static Vec3 horizontalDirection(Vec3 movement, Vec3 facing) {
    Vec3 direction = movement == null ? Vec3.ZERO : movement.withY(0D);
    if (!direction.isFinite() || direction.lengthSquared() < MINIMUM_DIRECTION_SQUARED) {
      direction = facing == null ? Vec3.ZERO : facing.withY(0D);
    }
    if (!direction.isFinite() || direction.lengthSquared() < MINIMUM_DIRECTION_SQUARED) {
      return Vec3.ZERO;
    }
    return direction.normalize();
  }

  static double vaultHeight(double configuredHeight) {
    if (!Double.isFinite(configuredHeight)) {
      return MINIMUM_VAULT_HEIGHT;
    }
    return Math.max(MINIMUM_VAULT_HEIGHT, configuredHeight);
  }

  static boolean shouldPreArm(int level, boolean eligible, boolean grounded, boolean fenceAhead) {
    return level > 0 && eligible && grounded && fenceAhead;
  }

  static double correctedVerticalVelocity(double existingVelocity, double risenHeight, double targetHeight) {
    double safeExisting = Double.isFinite(existingVelocity) ? existingVelocity : 0D;
    double safeRise = Double.isFinite(risenHeight) ? Math.max(0D, risenHeight) : 0D;
    double remainingHeight = Math.max(0D, vaultHeight(targetHeight) - safeRise);
    return Math.max(safeExisting, AgilityJumpPhysics.strengthForHeight(remainingHeight));
  }

  static Vec3 correctedVelocity(Vec3 existingVelocity, double risenHeight, double targetHeight) {
    Vec3 corrected = existingVelocity == null ? Vec3.ZERO : existingVelocity;
    return corrected.withY(correctedVerticalVelocity(corrected.y(), risenHeight, targetHeight));
  }

  static void synchronizeCorrectedVelocity(PlayerRef player, Vec3 existingVelocity, double risenHeight, double targetHeight) {
    player.setVelocity(correctedVelocity(existingVelocity, risenHeight, targetHeight));
  }

  private void refreshPreArm(PlayerRef p, Loc origin, Vec3 direction) {
    boolean grounded = p.isOnGround();
    boolean eligible = grounded && isVaultEligible(p);
    int level = eligible ? resolvePreArmLevel(p) : 0;
    BlockRef fence = level > 0 && direction.lengthSquared() >= MINIMUM_DIRECTION_SQUARED
        ? findFence(origin, direction)
        : null;
    if (!shouldPreArm(level, eligible, grounded, fence != null)) {
      disarm(p);
      return;
    }
    arm(p);
  }

  private int resolvePreArmLevel(PlayerRef p) {
    return getActiveLevel(p);
  }

  private void attemptVault(PlayerRef p, Loc origin, Vec3 direction) {
    if (getActiveLevel(p) <= 0 || !isVaultEligible(p)) {
      disarm(p);
      return;
    }

    BlockRef fence = findFence(origin, direction);
    if (fence == null) {
      disarm(p);
      return;
    }

    arm(p);
    double originY = origin.y();
    WorldRef originWorld = origin.world();
    if (!Tasks.runEntity(p, () -> finishVaultJump(p, originWorld, originY), 1)) {
      disarm(p);
    }

    fx(origin.add(0D, 0.2D, 0D), FxPriority.GAMEPLAY)
        .particle(Particles.blockCrack(fence.state()), 5, 0D, 0.1D, 0D, 0.25D, 0.05D)
        .chord(Sounds.ITEM_ARMOR_EQUIP_LEATHER, 0.7F, 1.2F, Sounds.ENTITY_PHANTOM_FLAP, 0.4F, 1.5F);

    addStat(p, "agility.vault.vaults", 1);
    xp(p, getConfig().xpPerVault);
  }

  private void arm(PlayerRef p) {
    double bonus = AgilityJumpPhysics.bonusForHeight(vaultHeight(getConfig().jumpHeight));
    Double armedBonus = armedBonuses.get(p.uuid());
    if (armedBonus != null && Double.compare(armedBonus, bonus) == 0) {
      return;
    }

    AdaptAttributeService.get().apply(p, getName(), SLOT_VAULT, Attributes.JUMP_STRENGTH,
        bonus, AttributeOperation.ADD_VALUE);
    armedBonuses.put(p.uuid(), bonus);
  }

  private void disarm(PlayerRef p) {
    if (armedBonuses.remove(p.uuid()) == null) {
      return;
    }
    AdaptAttributeService.get().remove(p, getName(), SLOT_VAULT, Attributes.JUMP_STRENGTH);
  }

  private void clearArmedState(PlayerRef p) {
    armedBonuses.remove(p.uuid());
    AdaptAttributeService.get().remove(p, getName(), SLOT_VAULT, Attributes.JUMP_STRENGTH);
  }

  private void finishVaultJump(PlayerRef p, WorldRef originWorld, double originY) {
    disarm(p);
    if (!isRuntimeRegistered() || !p.isOnline() || !p.isValid() || p.isDead() || p.isOnGround()
        || !p.world().uid().equals(originWorld.uid()) || getActiveLevel(p) <= 0 || !isVaultEligible(p)) {
      return;
    }

    synchronizeCorrectedVelocity(p, p.velocity(), p.location().y() - originY, getConfig().jumpHeight);
  }

  private boolean requiresOwnerMaintenance(UUID playerId) {
    return armedBonuses.containsKey(playerId) || getServer().hasOnlineLearner(playerId, getName());
  }

  private void reconcilePlayer(PlayerRef p) {
    Loc origin = p.location();
    refreshPreArm(p, origin, horizontalDirection(null, origin.direction()));
  }

  static BlockRef findFence(Loc origin, Vec3 direction) {
    return findFence(origin, direction, FENCES::contains);
  }

  static BlockRef findFence(Loc origin, Vec3 direction, Predicate<Id> fencePredicate) {
    WorldRef world = origin.world();
    for (double distance = FENCE_PROBE_START; distance <= FENCE_PROBE_LIMIT; distance += FENCE_PROBE_STEP) {
      BlockRef block = world.blockAt(
          (int) Math.floor(origin.x() + (direction.x() * distance)),
          (int) Math.floor(origin.y()),
          (int) Math.floor(origin.z() + (direction.z() * distance)));
      if (fencePredicate.test(block.type())) {
        return block;
      }
      if (!block.isPassable()) {
        return null;
      }
    }
    return null;
  }

  private boolean isVaultEligible(PlayerRef p) {
    if ((preference(p, TRIGGER) == Trigger.SPRINT && !p.isSprinting())
        || (preference(p, TRIGGER) == Trigger.SNEAK && !p.isSneaking())) {
      return false;
    }
    GameMode mode = p.gameMode();
    if (mode != GameMode.SURVIVAL && mode != GameMode.ADVENTURE) {
      return false;
    }

    return p.isOnline()
        && !p.isDead()
        && !p.isSwimming()
        && !p.isFlying()
        && !p.isGliding()
        && !p.isClimbing()
        && p.vehicle() == null;
  }

  @ConfigDescription("Jump toward a fence to clear it.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Jump apex in blocks when clearing a fence.", impact = "Higher values clear fences with more vertical margin.")
    double jumpHeight = 1.75D;
    @ConfigDoc(value = "Experience granted per successful vault.", impact = "Higher values reward vaulting with more skill xp.")
    double xpPerVault = 3D;

    public Config() {
      baseCost = 3;
      costFactor = 0D;
      maxLevel = VAULT_LEVELS;
      initialCost = 4;
    }
  }

  public enum Trigger { ANY, SPRINT, SNEAK }
}
