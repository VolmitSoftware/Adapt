package art.arcane.adapt.content.adaptation.taming;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.fx.ViewerGlowPort;
import art.arcane.adapt.api.minion.MinionIdentity;
import art.arcane.adapt.api.world.EntityMarkerPort;
import art.arcane.adapt.client.protocol.HudGlyph;
import art.arcane.adapt.client.server.hud.AbilityGlyphs;
import art.arcane.adapt.platform.entity.AnimalOps;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.GameMode;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.viewer.GlowColor;
import art.arcane.adapt.platform.world.RayHit;
import art.arcane.adapt.util.common.scheduling.Tasks;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.IntFunction;

/**
 * The bone command: nearby combat pets drop what they are doing and focus a marked target with a timed strength and
 * speed buff, while the owner sees the target glow.
 */
public final class TamePackCommand {
  public static final Id BONE = Id.minecraft("bone");
  private static final int HARD_MAX_PETS = 24;
  private static final int FOCUS_REFRESH_TICKS = 10;
  private static final Set<Id> COMBAT_PETS = Set.of(Id.minecraft("wolf"), Id.minecraft("cat"), Id.minecraft("llama"),
      Id.minecraft("trader_llama"));

  private final Adaptation<?> owner;
  private final Cooldowns commandCd;
  private final Consumer<PlayerRef> onCommand;
  private final Map<UUID, FocusState> activeFocuses = new ConcurrentHashMap<>();
  private final Map<UUID, GlowState> targetGlows = new ConcurrentHashMap<>();
  private final AtomicLong commandSequence = new AtomicLong();
  private final AtomicBoolean closed = new AtomicBoolean(false);

  /** {@code onCommand} runs on the commander's thread after every successful command. */
  public TamePackCommand(Adaptation<?> owner, Cooldowns commandCd, Consumer<PlayerRef> onCommand) {
    this.owner = owner;
    this.commandCd = commandCd;
    this.onCommand = onCommand;
  }

  /** {@code spendsBone}: the command needs and spends a bone from the main hand. */
  public record Tuning(double range, int focusTicks, int amplifier, long cooldownMillis, int petLimit, boolean spendsBone) {
  }

  /** Sends the pets at {@code target}; returns false when nothing was commanded and the gesture should pass through. */
  public boolean command(PlayerRef p, LivingRef target, IntFunction<Tuning> tuningForLevel) {
    if (closed.get() || !isEligibleTarget(p, target) || !owner.preference(p, TamingPreferences.TARGETS).accepts(target)) {
      return false;
    }

    int level = owner.getActiveLevel(p);
    if (level <= 0) {
      return false;
    }

    Tuning tuning = tuningForLevel.apply(level);
    if (tuning.spendsBone() && !owner.preference(p, TamingPreferences.BONES).permits(p.inventory().mainHand().amount(), 1)) {
      return false;
    }
    UUID playerId = p.uuid();
    if (!commandCd.isReady(playerId, tuning.cooldownMillis())) {
      return false;
    }

    List<MobRef> pets = collectPets(p, tuning.range(), target, tuning.petLimit());
    if (pets.isEmpty()) {
      return false;
    }

    int focusTicks = tuning.focusTicks();
    int amplifier = Math.max(0, tuning.amplifier());
    long generation = commandSequence.incrementAndGet();
    int scheduled = 0;
    for (MobRef pet : pets) {
      if (Tasks.runEntity(pet, () -> focusPet(pet, p, playerId, target, focusTicks, amplifier, generation))) {
        scheduled++;
      }
    }
    if (scheduled <= 0) {
      return false;
    }

    commandCd.mark(playerId, tuning.cooldownMillis());
    AbilityGlyphs.duration(playerId, owner, System.currentTimeMillis(), focusTicks * 50L);
    if (tuning.spendsBone()) {
      consumeOneBone(p);
    }
    showTargetGlow(p, target, focusTicks, generation);
    Loc origin = p.location().add(0, 1, 0);
    TamingAlphasCommand.commandFx(owner, origin);
    onCommand.accept(p);
    return true;
  }

  /** The living entity the player is looking at within {@code range}, never the player's own pet. */
  public LivingRef aim(PlayerRef p, double range) {
    Loc eye = p.eyeLocation();
    RayHit hit = p.world().rayTrace(
        eye.position(),
        eye.direction(),
        range,
        false,
        0.6D,
        entity -> entity instanceof LivingRef living
            && !living.uuid().equals(p.uuid())
            && !living.has(EntityTrait.ARMOR_STAND)
            && living.isValid()
            && !living.isDead()
            && !isOwnPet(p, living)
    );
    return hit != null && hit.entity() instanceof LivingRef target ? target : null;
  }

  /** Drops the player's target glow and releases every pet focusing for them. */
  public void clear(PlayerRef p) {
    clearTargetGlow(p);
    clearOwnerFocuses(p.uuid());
  }

  /** Releases every glow and focus; returns false when it was already closed. */
  public boolean close() {
    if (!closed.compareAndSet(false, true)) {
      return false;
    }

    List<GlowState> glows = new ArrayList<>(targetGlows.values());
    targetGlows.clear();
    for (GlowState glow : glows) {
      PlayerRef viewer = glow.viewer();
      if (viewer.isOnline()) {
        Tasks.runEntity(viewer, () -> unsetGlowOwned(viewer, glow));
      }
    }
    ViewerGlowPort.current().clearLayer(ViewerGlowPort.Layer.TAMING_ALPHAS_COMMAND);

    List<FocusState> focuses = new ArrayList<>(activeFocuses.values());
    activeFocuses.clear();
    for (FocusState focus : focuses) {
      Tasks.runEntity(focus.pet(), () -> clearFocusOwned(focus));
    }
    return true;
  }

  private boolean isEligibleTarget(PlayerRef p, LivingRef target) {
    if (target.uuid().equals(p.uuid()) || target.has(EntityTrait.ARMOR_STAND) || !target.isValid() || target.isDead()
        || isOwnPet(p, target)) {
      return false;
    }
    if (target instanceof PlayerRef player && !AdaptCore.platform().viewers().canSee(p, player)) {
      return false;
    }
    return owner.canDamageTarget(p, target);
  }

  private List<MobRef> collectPets(PlayerRef player, double range, LivingRef target, int limit) {
    UUID ownerId = player.uuid();
    List<MobRef> pets = new ArrayList<>(limit);
    for (EntityRef entity : player.nearbyEntities(range, range, range)) {
      if (entity.uuid().equals(target.uuid())) {
        continue;
      }
      if (entity instanceof MobRef mob && isCombatCapableTameable(mob)
          && owner.preference(player, TamingPreferences.PETS).accepts(mob.type()) && animals().isTamed(mob) && mob.isValid() && !mob.isDead() && isOwnedBy(mob, ownerId)) {
        pets.add(mob);
        if (pets.size() >= limit) {
          break;
        }
      }
    }
    return pets;
  }

  private void focusPet(MobRef pet, PlayerRef player, UUID ownerId, LivingRef target, int focusTicks, int amplifier,
                        long generation) {
    if (!pet.isValid() || pet.isDead() || !pet.has(EntityTrait.TAMEABLE) || !animals().isTamed(pet)
        || !isOwnedBy(pet, ownerId)) {
      return;
    }

    UUID petId = pet.uuid();
    FocusState previous = activeFocuses.get(petId);
    boolean restoreSitting = previous != null
        ? previous.restoreSitting()
        : animals().isSitting(pet);
    FocusState focus = new FocusState(petId, pet, player, ownerId, target, generation, restoreSitting);
    activeFocuses.put(petId, focus);
    if (animals().isSitting(pet)) {
      animals().setSitting(pet, false);
    }
    pet.setTarget(target);
    applyFocusBuffs(pet, focusTicks, amplifier);
    TamingAlphasCommand.focusFx(owner, pet);
    scheduleFocusRefresh(focus, focusTicks);
  }

  private void applyFocusBuffs(MobRef pet, int focusTicks, int amplifier) {
    if (focusTicks <= 0) {
      return;
    }
    AdaptAttributeService service = AdaptAttributeService.get();
    service.applyTimed(pet, owner.getName(), "damage", Attributes.ATTACK_DAMAGE, focusDamageBonus(amplifier),
        AttributeOperation.ADD_VALUE, focusTicks);
    service.applyTimed(pet, owner.getName(), "speed", Attributes.MOVEMENT_SPEED, focusSpeedScalar(amplifier),
        AttributeOperation.ADD_MULTIPLIED_TOTAL, focusTicks);
  }

  private void scheduleFocusRefresh(FocusState focus, int remainingTicks) {
    int delay = Math.min(FOCUS_REFRESH_TICKS, remainingTicks);
    if (delay <= 0 || !Tasks.runEntity(focus.pet(), () -> refreshFocusOwned(focus, remainingTicks - delay), delay)) {
      if (activeFocuses.remove(focus.petId(), focus)) {
        clearFocusOwned(focus);
      }
    }
  }

  private void refreshFocusOwned(FocusState focus, int remainingTicks) {
    if (!isCurrentFocus(focus)) {
      return;
    }
    if (closed.get() || remainingTicks <= 0 || !isPetFocusValid(focus)) {
      stopFocusOwned(focus);
      return;
    }

    if (!Tasks.runEntity(focus.target(), () -> validateFocusTargetOwned(focus, remainingTicks))) {
      stopFocusOwned(focus);
    }
  }

  private void validateFocusTargetOwned(FocusState focus, int remainingTicks) {
    LivingRef target = focus.target();
    if (!isCurrentFocus(focus)) {
      return;
    }
    if (!target.isValid() || target.isDead()) {
      stopFocusFromForeignThread(focus);
      return;
    }
    TargetPolicySnapshot snapshot = new TargetPolicySnapshot(
        target.location(),
        target instanceof PlayerRef,
        isProtectedFocusTarget(target, focus.ownerId())
    );
    if (!Tasks.runEntity(focus.owner(), () -> validateFocusPolicyOwned(focus, remainingTicks, snapshot))) {
      activeFocuses.remove(focus.petId(), focus);
    }
  }

  private void validateFocusPolicyOwned(FocusState focus, int remainingTicks, TargetPolicySnapshot snapshot) {
    PlayerRef player = focus.owner();
    if (!isCurrentFocus(focus) || closed.get() || !player.isOnline()
        || !player.uuid().equals(focus.ownerId()) || owner.getActiveLevel(player) <= 0
        || snapshot.protectedFriendly() || !canAffectFocusTarget(player, snapshot)) {
      stopFocusFromForeignThread(focus);
      return;
    }
    if (!Tasks.runEntity(focus.pet(), () -> applyFocusRefreshOwned(focus, remainingTicks))) {
      activeFocuses.remove(focus.petId(), focus);
    }
  }

  private boolean canAffectFocusTarget(PlayerRef player, TargetPolicySnapshot snapshot) {
    if (snapshot.location().world() == null || !player.world().equals(snapshot.location().world())) {
      return false;
    }
    return snapshot.player()
        ? owner.canPVP(player, snapshot.location())
        : owner.canPVE(player, snapshot.location());
  }

  private boolean isProtectedFocusTarget(LivingRef target, UUID ownerId) {
    if (target.isInvulnerable() || AdaptPorts.get(EntityMarkerPort.class, EntityMarkerPort.NONE).marked(target, "NPC")
        || MinionIdentity.isMinion(target)) {
      return true;
    }
    if (target.has(EntityTrait.TAMEABLE) && animals().isTamed(target)) {
      return isOwnedBy(target, ownerId);
    }
    return false;
  }

  private void applyFocusRefreshOwned(FocusState focus, int remainingTicks) {
    MobRef pet = focus.pet();
    if (!isCurrentFocus(focus) || closed.get() || !isPetFocusValid(focus)) {
      stopFocusOwned(focus);
      return;
    }
    if (animals().isSitting(pet)) {
      animals().setSitting(pet, false);
    }
    if (!isTargeting(pet, focus.target())) {
      pet.setTarget(focus.target());
    }
    scheduleFocusRefresh(focus, remainingTicks);
  }

  private boolean isPetFocusValid(FocusState focus) {
    MobRef pet = focus.pet();
    if (!pet.isValid() || pet.isDead() || !pet.has(EntityTrait.TAMEABLE) || !animals().isTamed(pet)) {
      return false;
    }
    return isOwnedBy(pet, focus.ownerId());
  }

  private static boolean isTargeting(MobRef pet, LivingRef target) {
    LivingRef current = pet.target();
    return current != null && current.uuid().equals(target.uuid());
  }

  private boolean isCurrentFocus(FocusState focus) {
    FocusState current = activeFocuses.get(focus.petId());
    return current != null && current.generation() == focus.generation();
  }

  private void stopFocusOwned(FocusState focus) {
    if (activeFocuses.remove(focus.petId(), focus)) {
      clearFocusOwned(focus);
    }
  }

  private void stopFocusFromForeignThread(FocusState focus) {
    if (!Tasks.runEntity(focus.pet(), () -> stopFocusOwned(focus))) {
      activeFocuses.remove(focus.petId(), focus);
    }
  }

  private void clearFocusOwned(FocusState focus) {
    MobRef pet = focus.pet();
    AdaptAttributeService.get().removeAll(pet, owner.getName());
    if (isTargeting(pet, focus.target())) {
      pet.setTarget(null);
    }
    if (focus.restoreSitting()) {
      animals().setSitting(pet, true);
    }
  }

  private void clearOwnerFocuses(UUID ownerId) {
    AbilityGlyphs.clear(ownerId, owner, HudGlyph.DURATION);
    for (FocusState focus : new ArrayList<>(activeFocuses.values())) {
      if (focus.ownerId().equals(ownerId) && activeFocuses.remove(focus.petId(), focus)) {
        Tasks.runEntity(focus.pet(), () -> clearFocusOwned(focus));
      }
    }
  }

  private void consumeOneBone(PlayerRef p) {
    ItemRef bone = p.inventory().mainHand();
    if (!BONE.equals(bone.type()) || bone.amount() <= 0) {
      return;
    }

    int remaining = boneAmountAfterSuccessfulCommand(p.gameMode(), bone.amount());
    if (remaining == bone.amount()) {
      return;
    }
    if (remaining <= 0) {
      p.inventory().setMainHand(AdaptCore.platform().host().items().empty());
      return;
    }
    bone.setAmount(remaining);
    p.inventory().setMainHand(bone);
  }

  private void showTargetGlow(PlayerRef viewer, LivingRef target, int focusTicks, long generation) {
    ViewerGlowPort glowCoordinator = ViewerGlowPort.current();
    if (glowCoordinator == null || !glowCoordinator.isAvailable()) {
      return;
    }

    UUID viewerId = viewer.uuid();
    GlowState glow = new GlowState(viewer, target.uuid(), target.entityId(), generation);
    GlowState previous = targetGlows.put(viewerId, glow);
    if (previous != null
        && (!previous.targetId().equals(glow.targetId())
        || previous.runtimeEntityId() != glow.runtimeEntityId())) {
      unsetGlowOwned(viewer, previous);
    }

    if (!glowCoordinator.set(
        ViewerGlowPort.Layer.TAMING_ALPHAS_COMMAND,
        target,
        viewer,
        GlowColor.RED
    )) {
      targetGlows.remove(viewerId, glow);
      return;
    }
    if (!Tasks.runEntity(viewer, () -> expireTargetGlowOwned(viewer, glow), focusTicks)) {
      expireTargetGlowOwned(viewer, glow);
    }
  }

  private void expireTargetGlowOwned(PlayerRef viewer, GlowState glow) {
    UUID viewerId = viewer.uuid();
    GlowState current = targetGlows.get(viewerId);
    if (current != null && current.generation() == glow.generation() && targetGlows.remove(viewerId, current)) {
      unsetGlowOwned(viewer, current);
    }
  }

  private void clearTargetGlow(PlayerRef viewer) {
    GlowState glow = targetGlows.remove(viewer.uuid());
    if (glow != null) {
      unsetGlowOwned(viewer, glow);
    }
  }

  private void unsetGlowOwned(PlayerRef viewer, GlowState glow) {
    ViewerGlowPort glowCoordinator = ViewerGlowPort.current();
    if (glowCoordinator == null) {
      return;
    }
    glowCoordinator.unset(
        ViewerGlowPort.Layer.TAMING_ALPHAS_COMMAND,
        glow.targetId(),
        glow.runtimeEntityId(),
        viewer
    );
  }

  private static boolean isOwnPet(PlayerRef p, LivingRef target) {
    return target.has(EntityTrait.TAMEABLE) && animals().isTamed(target) && isOwnedBy(target, p.uuid());
  }

  private static boolean isOwnedBy(EntityRef tameable, UUID ownerId) {
    UUID tameOwner = tameable.tameOwner();
    return tameOwner != null && ownerId.equals(tameOwner);
  }

  private static AnimalOps animals() {
    return AdaptCore.platform().host().animals();
  }

  public static int petLimit(int configured) {
    return Math.max(1, Math.min(configured, HARD_MAX_PETS));
  }

  public static boolean isCommandGesture(InteractAction action, Hand hand) {
    return hand == Hand.MAIN_HAND && (action == InteractAction.LEFT_CLICK_AIR || action == InteractAction.LEFT_CLICK_BLOCK);
  }

  public static boolean isCombatCapableTameable(EntityRef entity) {
    return entity instanceof MobRef && entity.has(EntityTrait.TAMEABLE) && COMBAT_PETS.contains(entity.type());
  }

  static int boneAmountAfterSuccessfulCommand(GameMode gameMode, int currentAmount) {
    return gameMode == GameMode.CREATIVE ? currentAmount : Math.max(0, currentAmount - 1);
  }

  static double focusDamageBonus(int amplifier) {
    return 3.0D * (amplifier + 1);
  }

  static double focusSpeedScalar(int amplifier) {
    return 0.2D * (amplifier + 1);
  }

  private record FocusState(UUID petId, MobRef pet, PlayerRef owner, UUID ownerId, LivingRef target, long generation,
                            boolean restoreSitting) {
  }

  private record TargetPolicySnapshot(Loc location, boolean player, boolean protectedFriendly) {
  }

  private record GlowState(PlayerRef viewer, UUID targetId, int runtimeEntityId, long generation) {
  }
}
