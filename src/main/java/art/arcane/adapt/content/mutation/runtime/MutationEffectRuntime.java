package art.arcane.adapt.content.mutation.runtime;

import art.arcane.adapt.api.mutation.MutationConfig;
import art.arcane.adapt.api.mutation.MutationLimits;
import art.arcane.adapt.api.mutation.MutationType;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionEffectTypeCategory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class MutationEffectRuntime {
  private static final double MOLT_POSITION_EPSILON_SQUARED = 0.01D;
  private static final int COPY_DURATION_HARD_CAP_TICKS = 20 * 60 * 10;
  private static final int MAX_NEARBY_INSPECTIONS = 16;
  private static final int CONSENT_RECHECK_TICKS = 20;

  private final MutationRuntimeAccess access;
  private final MutationRuntimeStore store;
  private final MutationBlockProvenance provenance;
  private final Map<MutationRuntimeStore.EffectKey, MutationRuntimeStore.CopiedEffect> copyOwners = new ConcurrentHashMap<>();
  private final Map<MutationRuntimeStore.EffectKey, LivingEntity> copyRecipients = new ConcurrentHashMap<>();
  private final Map<MutationRuntimeStore.EffectKey, MutationRuntimeAccess.CooperativeConsent> copyAuthorizations = new ConcurrentHashMap<>();
  private final Map<UUID, Set<MutationRuntimeStore.EffectKey>> recipientCopies = new ConcurrentHashMap<>();
  private final Map<UUID, RootConsentSession> rootConsentSessions = new ConcurrentHashMap<>();
  private final Map<MutationRuntimeStore.EffectKey, InternalEffectAction> internalApplications = new ConcurrentHashMap<>();

  MutationEffectRuntime(MutationRuntimeAccess access, MutationRuntimeStore store, MutationBlockProvenance provenance) {
    this.access = access;
    this.store = store;
    this.provenance = provenance;
  }

  void onToggleSneak(PlayerToggleSneakEvent event) {
    Player player = event.getPlayer();
    if (!access.expressed(player, MutationType.VERDANT_MOLT)) {
      return;
    }
    if (!event.isSneaking()) {
      interruptMolt(player);
      return;
    }
    startMolt(player);
  }

  void onDamage(EntityDamageEvent event) {
    if (event.isCancelled() || event.getFinalDamage() <= 0D || !(event.getEntity() instanceof Player player)) {
      return;
    }
    interruptMolt(player);
    if (access.expressed(player, MutationType.MYCELIAL_NERVE)
        && !access.perfect(player)
        && isFire(event.getCause())) {
      severNetwork(player, true);
    }
  }

  void onPotionEffect(EntityPotionEffectEvent event) {
    boolean enabled = access.enabled();
    if (!shouldInspectPotionEvent(enabled, !internalApplications.isEmpty(), !copyOwners.isEmpty())) {
      return;
    }
    LivingEntity entity = event.getEntity();
    PotionEffectType type = event.getModifiedType();
    if (type == null) {
      return;
    }
    MutationRuntimeStore.EffectKey effectKey = new MutationRuntimeStore.EffectKey(entity.getUniqueId(), type);
    InternalEffectAction internalAction = internalApplications.remove(effectKey);
    if (internalAction == InternalEffectAction.COPY) {
      return;
    }
    releaseCopyOwnership(effectKey);
    if (internalAction != null) {
      return;
    }
    if (!enabled || !(entity instanceof Player player)) {
      return;
    }
    trackNonCleansable(player, event);
    applyMoltRecoveryGuard(player, event);
    if (event.isCancelled()) {
      return;
    }
    if (event.getAction() == EntityPotionEffectEvent.Action.REMOVED
        || event.getAction() == EntityPotionEffectEvent.Action.CLEARED) {
      removeOwnedCopies(player, type);
      return;
    }
    PotionEffect source = event.getNewEffect();
    if (source == null
        || source.getType().getCategory() != PotionEffectTypeCategory.BENEFICIAL
        || source.getType().isInstant()
        || !access.expressed(player, MutationType.MYCELIAL_NERVE)
        || !isSelfApplied(player, event)) {
      return;
    }
    MutationRuntimeStore.PlayerRuntimeState runtime = store.player(player.getUniqueId());
    synchronized (runtime) {
      if (runtime.mycelial.reconnectAt > System.currentTimeMillis()) {
        return;
      }
    }
    propagate(player, source);
    if (!access.perfect(player)) {
      shortenRootEffect(player, source, event);
    }
  }

  void interruptMolt(Player player) {
    if (player == null || !access.enabled()) {
      return;
    }
    MutationRuntimeStore.PlayerRuntimeState runtime = store.player(player.getUniqueId());
    synchronized (runtime) {
      runtime.molt.origin = null;
      runtime.molt.generation++;
    }
  }

  void cleanup(Player player) {
    if (player == null) {
      return;
    }
    removeReceivedCopies(player);
    MutationRuntimeStore.PlayerRuntimeState runtime = store.existing(player.getUniqueId());
    if (runtime == null) {
      return;
    }
    severNetwork(player, false);
    synchronized (runtime) {
      runtime.molt.clear();
      runtime.mycelial.clear();
    }
  }

  void reconcileRecipient(Player recipient) {
    if (recipient == null) {
      return;
    }
    Set<MutationRuntimeStore.EffectKey> keys = recipientCopies.get(recipient.getUniqueId());
    if (keys == null || keys.isEmpty()) {
      return;
    }
    for (MutationRuntimeStore.EffectKey key : List.copyOf(keys)) {
      MutationRuntimeStore.CopiedEffect copy = copyOwners.get(key);
      MutationRuntimeAccess.CooperativeConsent consent = copyAuthorizations.get(key);
      if (copy != null && consent != null && !access.consented(consent, recipient)) {
        removeCopyOnRecipient(recipient, copy);
      }
    }
  }

  private void startMolt(Player player) {
    if (!isNaturalFooting(player)) {
      return;
    }
    MutationRuntimeStore.PlayerRuntimeState runtime = store.player(player.getUniqueId());
    long generation;
    synchronized (runtime) {
      long now = System.currentTimeMillis();
      if (runtime.molt.readyAt > now || runtime.molt.origin != null) {
        return;
      }
      runtime.molt.origin = player.getLocation().clone();
      runtime.molt.generation++;
      generation = runtime.molt.generation;
    }
    int chargeTicks = access.config().getVerdantMolt().getChargeTicks();
    J.runEntity(player, () -> completeMolt(player, generation), chargeTicks);
  }

  private void completeMolt(Player player, long generation) {
    if (!player.isOnline() || !player.isSneaking() || !access.expressed(player, MutationType.VERDANT_MOLT)
        || !isNaturalFooting(player)) {
      interruptMolt(player);
      return;
    }
    MutationRuntimeStore.PlayerRuntimeState runtime = store.player(player.getUniqueId());
    Location origin;
    synchronized (runtime) {
      if (runtime.molt.generation != generation || runtime.molt.origin == null) {
        return;
      }
      origin = runtime.molt.origin.clone();
    }
    if (origin.getWorld() != player.getWorld() || origin.distanceSquared(player.getLocation()) > MOLT_POSITION_EPSILON_SQUARED) {
      interruptMolt(player);
      return;
    }
    MutationConfig.VerdantMolt config = access.config().getVerdantMolt();
    List<PotionEffect> effects = player.getActivePotionEffects().stream()
        .limit(config.getMaximumEffects())
        .toList();
    PotionEffect longestHarmful = effects.stream()
        .filter(effect -> effect.getType().getCategory() == PotionEffectTypeCategory.HARMFUL)
        .filter(effect -> isCleansable(runtime, effect.getType()))
        .max(Comparator.comparingInt(PotionEffect::getDuration))
        .orElse(null);
    for (PotionEffect effect : effects) {
      boolean harmful = effect.getType().getCategory() == PotionEffectTypeCategory.HARMFUL;
      boolean beneficial = effect.getType().getCategory() == PotionEffectTypeCategory.BENEFICIAL;
      if ((harmful || (beneficial && !access.perfect(player))) && isCleansable(runtime, effect.getType())) {
        player.removePotionEffect(effect.getType());
      }
    }
    long now = System.currentTimeMillis();
    synchronized (runtime) {
      runtime.molt.origin = null;
      runtime.molt.readyAt = now + config.getCooldownMillis();
      runtime.molt.recoveryUntil = now + (config.getRecoveryTicks() * 50L);
      runtime.molt.guardedType = longestHarmful == null ? null : longestHarmful.getType();
    }
    if (!access.perfect(player)) {
      player.setSaturation((float) Math.max(0D, player.getSaturation() - config.getSaturationCost()));
    }
    access.tell(player, MutationType.VERDANT_MOLT, Particle.SPORE_BLOSSOM_AIR, 14);
  }

  private void applyMoltRecoveryGuard(Player player, EntityPotionEffectEvent event) {
    if (!access.expressed(player, MutationType.VERDANT_MOLT) || event.getNewEffect() == null) {
      return;
    }
    if (event.getCause() == EntityPotionEffectEvent.Cause.COMMAND || event.getCause() == EntityPotionEffectEvent.Cause.PLUGIN) {
      return;
    }
    MutationRuntimeStore.PlayerRuntimeState runtime = store.player(player.getUniqueId());
    synchronized (runtime) {
      if (runtime.molt.recoveryUntil <= System.currentTimeMillis()) {
        runtime.molt.recoveryUntil = 0L;
        runtime.molt.guardedType = null;
        return;
      }
      event.setCancelled(true);
    }
  }

  private void propagate(Player root, PotionEffect source) {
    MutationConfig.MycelialNerve config = access.config().getMycelialNerve();
    int copiedDuration = MutationRuntimePolicy.copiedDuration(
        source.getDuration(),
        config.getCopiedDurationFactor(),
        COPY_DURATION_HARD_CAP_TICKS
    );
    if (copiedDuration <= 0) {
      return;
    }
    NetworkContext context = new NetworkContext(
        root.getUniqueId(),
        root.getWorld().getUID(),
        access.cooperativeConsent(root)
    );
    if (!context.consent().enabled() || context.consent().mode() == MutationConfig.ConsentMode.DISABLED) {
      return;
    }
    UUID rootId = root.getUniqueId();
    int[] inspectedEntities = {0};
    Collection<Entity> nearby = root.getWorld().getNearbyEntities(
        root.getLocation(),
        config.getRange(),
        config.getRange(),
        config.getRange(),
        candidate -> {
          if (!(candidate instanceof LivingEntity) || candidate.getUniqueId().equals(rootId)) {
            return false;
          }
          if (inspectedEntities[0] >= MAX_NEARBY_INSPECTIONS) {
            return false;
          }
          inspectedEntities[0]++;
          return true;
        }
    );
    MutationRuntimeStore.PlayerRuntimeState runtime = store.player(root.getUniqueId());
    long generation;
    synchronized (runtime) {
      generation = runtime.mycelial.generation;
    }
    int recipientLimit = copyRecipientLimit(config.getMaximumRecipients());
    int reserved = 0;
    for (Entity candidate : nearby) {
      if (reserved >= recipientLimit) {
        break;
      }
      LivingEntity living = (LivingEntity) candidate;
      if (reserveCopy(living, source, copiedDuration, generation, context)) {
        reserved++;
      }
    }
    if (reserved > 0) {
      ensureRootConsentCheck(root, generation, context);
      access.tell(root, MutationType.MYCELIAL_NERVE, Particle.SPORE_BLOSSOM_AIR, Math.min(16, reserved * 2));
    }
  }

  private boolean reserveCopy(
      LivingEntity recipient,
      PotionEffect source,
      int duration,
      long generation,
      NetworkContext context
  ) {
    MutationRuntimeStore.EffectKey key = new MutationRuntimeStore.EffectKey(recipient.getUniqueId(), source.getType());
    long expiresAt = System.currentTimeMillis() + (duration * 50L);
    MutationRuntimeStore.CopiedEffect owned = new MutationRuntimeStore.CopiedEffect(
        context.rootId(),
        recipient.getUniqueId(),
        source.getType(),
        source.getAmplifier(),
        expiresAt,
        generation
    );
    MutationRuntimeStore.PlayerRuntimeState runtime = store.player(context.rootId());
    synchronized (runtime) {
      if (runtime.mycelial.generation != generation
          || runtime.mycelial.copies.size() >= MutationLimits.MYCELIAL_EFFECT_COPIES
          || exceedsRecipientLimit(
              runtime.mycelial.copies,
              owned.recipientId(),
              copyRecipientLimit(access.config().getMycelialNerve().getMaximumRecipients())
          )
          || copyOwners.putIfAbsent(key, owned) != null) {
        return false;
      }
      runtime.mycelial.copies.put(key, owned);
      copyRecipients.put(key, recipient);
      copyAuthorizations.put(key, context.consent());
      recipientCopies.computeIfAbsent(recipient.getUniqueId(), ignored -> ConcurrentHashMap.newKeySet()).add(key);
    }
    boolean scheduled = J.runEntity(recipient, () -> applyCopyOnRecipient(recipient, source, duration, owned, context));
    if (!scheduled) {
      releaseCopyOwnership(key, owned);
    }
    return scheduled;
  }

  static boolean exceedsRecipientLimit(
      Map<MutationRuntimeStore.EffectKey, MutationRuntimeStore.CopiedEffect> copies,
      UUID recipientId,
      int recipientLimit
  ) {
    Set<UUID> recipients = new HashSet<>(MutationLimits.MYCELIAL_RECIPIENTS);
    for (MutationRuntimeStore.EffectKey existing : copies.keySet()) {
      recipients.add(existing.recipientId());
    }
    return !recipients.contains(recipientId)
        && recipients.size() >= Math.max(1, Math.min(MutationLimits.MYCELIAL_RECIPIENTS, recipientLimit));
  }

  private void applyCopyOnRecipient(
      LivingEntity recipient,
      PotionEffect source,
      int duration,
      MutationRuntimeStore.CopiedEffect owned,
      NetworkContext context
  ) {
    MutationRuntimeStore.EffectKey key = new MutationRuntimeStore.EffectKey(owned.recipientId(), owned.type());
    if (!isCurrentCopy(key, owned) || !isAllowedRecipient(context, recipient)) {
      releaseCopyOwnership(key, owned);
      return;
    }
    internalApplications.put(key, InternalEffectAction.COPY);
    boolean applied = recipient.addPotionEffect(new PotionEffect(
        source.getType(),
        duration,
        source.getAmplifier(),
        source.isAmbient(),
        source.hasParticles(),
        source.hasIcon()
    ));
    if (!applied) {
      internalApplications.remove(key);
      releaseCopyOwnership(key, owned);
      return;
    }
    if (recipient instanceof Player player) {
      scheduleRecipientConsentCheck(player, owned, context);
    }
    boolean scheduled = J.runEntity(recipient, () -> expireCopyOnRecipient(recipient, owned), duration + 1);
    if (!scheduled) {
      removeCopyOnRecipient(recipient, owned);
    }
  }

  private void expireCopyOnRecipient(LivingEntity recipient, MutationRuntimeStore.CopiedEffect owned) {
    MutationRuntimeStore.EffectKey key = new MutationRuntimeStore.EffectKey(owned.recipientId(), owned.type());
    if (!isCurrentCopy(key, owned)) {
      return;
    }
    PotionEffect current = recipient.getPotionEffect(owned.type());
    releaseCopyOwnership(key, owned);
    if (matchesOwnedCopy(current, owned)) {
      internalApplications.put(key, InternalEffectAction.REMOVE);
      recipient.removePotionEffect(owned.type());
    }
  }

  private boolean isCurrentCopy(MutationRuntimeStore.EffectKey key, MutationRuntimeStore.CopiedEffect owned) {
    if (copyOwners.get(key) != owned) {
      return false;
    }
    MutationRuntimeStore.PlayerRuntimeState runtime = store.players.get(owned.rootId());
    if (runtime == null) {
      return false;
    }
    synchronized (runtime) {
      return runtime.mycelial.generation == owned.generation()
          && runtime.mycelial.copies.get(key) == owned;
    }
  }

  private boolean isAllowedRecipient(NetworkContext context, LivingEntity recipient) {
    if (recipient.getUniqueId().equals(context.rootId()) || !recipient.getWorld().getUID().equals(context.worldId())) {
      return false;
    }
    if (!context.consent().enabled() || context.consent().mode() == MutationConfig.ConsentMode.DISABLED) {
      return false;
    }
    if (recipient instanceof Player player) {
      return access.consented(context.consent(), player);
    }
    return recipient instanceof Tameable tameable && context.rootId().equals(tameable.getOwnerUniqueId());
  }

  private void ensureRootConsentCheck(Player root, long generation, NetworkContext context) {
    RootConsentSession candidate = new RootConsentSession(generation, context);
    UUID rootId = root.getUniqueId();
    while (true) {
      RootConsentSession existing = rootConsentSessions.get(rootId);
      if (existing != null && existing.generation() == generation && existing.context().equals(context)) {
        return;
      }
      boolean installed = existing == null
          ? rootConsentSessions.putIfAbsent(rootId, candidate) == null
          : rootConsentSessions.replace(rootId, existing, candidate);
      if (installed) {
        break;
      }
    }
    if (!J.runEntity(root, () -> validateRootConsent(root, candidate), CONSENT_RECHECK_TICKS)) {
      rootConsentSessions.remove(rootId, candidate);
      severNetwork(root, false);
    }
  }

  private void validateRootConsent(Player root, RootConsentSession session) {
    if (rootConsentSessions.get(root.getUniqueId()) != session) {
      return;
    }
    MutationRuntimeStore.PlayerRuntimeState runtime = store.players.get(root.getUniqueId());
    boolean active;
    if (runtime == null) {
      active = false;
    } else {
      synchronized (runtime) {
        active = runtime.mycelial.generation == session.generation()
            && !runtime.mycelial.copies.isEmpty();
      }
    }
    if (!root.isOnline()) {
      rootConsentSessions.remove(root.getUniqueId(), session);
      if (active) {
        severNetwork(root, false);
      }
      return;
    }
    NetworkContext current = new NetworkContext(
        root.getUniqueId(),
        root.getWorld().getUID(),
        access.cooperativeConsent(root)
    );
    if (!active || !access.expressed(root, MutationType.MYCELIAL_NERVE) || !session.context().equals(current)) {
      rootConsentSessions.remove(root.getUniqueId(), session);
      if (active) {
        severNetwork(root, false);
      }
      return;
    }
    if (!J.runEntity(root, () -> validateRootConsent(root, session), CONSENT_RECHECK_TICKS)) {
      rootConsentSessions.remove(root.getUniqueId(), session);
      severNetwork(root, false);
    }
  }

  private void scheduleRecipientConsentCheck(
      Player recipient,
      MutationRuntimeStore.CopiedEffect owned,
      NetworkContext context
  ) {
    boolean scheduled = J.runEntity(
        recipient,
        () -> validateRecipientConsent(recipient, owned, context),
        CONSENT_RECHECK_TICKS
    );
    if (!scheduled) {
      removeCopyOnRecipient(recipient, owned);
    }
  }

  private void validateRecipientConsent(
      Player recipient,
      MutationRuntimeStore.CopiedEffect owned,
      NetworkContext context
  ) {
    MutationRuntimeStore.EffectKey key = new MutationRuntimeStore.EffectKey(owned.recipientId(), owned.type());
    if (!isCurrentCopy(key, owned)) {
      return;
    }
    if (!isAllowedRecipient(context, recipient)) {
      removeCopyOnRecipient(recipient, owned);
      return;
    }
    scheduleRecipientConsentCheck(recipient, owned, context);
  }

  static int copyRecipientLimit(int configuredMaximum) {
    return Math.max(0, Math.min(MutationLimits.MYCELIAL_RECIPIENTS, configuredMaximum));
  }

  static boolean shouldInspectPotionEvent(boolean enabled, boolean hasInternalApplication, boolean hasOwnedCopy) {
    return enabled || hasInternalApplication || hasOwnedCopy;
  }

  private void shortenRootEffect(Player root, PotionEffect source, EntityPotionEffectEvent event) {
    int shortenedDuration = MutationRuntimePolicy.copiedDuration(
        source.getDuration(),
        access.config().getMycelialNerve().getRootDurationFactor(),
        COPY_DURATION_HARD_CAP_TICKS
    );
    if (shortenedDuration <= 0 || shortenedDuration >= source.getDuration()) {
      return;
    }
    event.setCancelled(true);
    MutationRuntimeStore.EffectKey key = new MutationRuntimeStore.EffectKey(root.getUniqueId(), source.getType());
    J.runEntity(root, () -> {
      internalApplications.put(key, InternalEffectAction.ROOT);
      root.addPotionEffect(new PotionEffect(
          source.getType(),
          shortenedDuration,
          source.getAmplifier(),
          source.isAmbient(),
          source.hasParticles(),
          source.hasIcon()
      ));
    }, 1);
  }

  private void severNetwork(Player root, boolean applyLock) {
    rootConsentSessions.remove(root.getUniqueId());
    MutationRuntimeStore.PlayerRuntimeState runtime = applyLock
        ? store.player(root.getUniqueId())
        : store.existing(root.getUniqueId());
    if (runtime == null) {
      return;
    }
    List<MutationRuntimeStore.CopiedEffect> copies;
    synchronized (runtime) {
      copies = new ArrayList<>(runtime.mycelial.copies.values());
      runtime.mycelial.copies.clear();
      runtime.mycelial.generation++;
      if (applyLock) {
        runtime.mycelial.reconnectAt = System.currentTimeMillis() + access.config().getMycelialNerve().getReconnectLockMillis();
      }
    }
    for (MutationRuntimeStore.CopiedEffect copy : copies) {
      removeCopy(copy);
    }
    if (applyLock) {
      access.tell(root, MutationType.MYCELIAL_NERVE, Particle.SMOKE, 8);
    }
  }

  private void removeOwnedCopies(Player root, PotionEffectType type) {
    MutationRuntimeStore.PlayerRuntimeState runtime = store.player(root.getUniqueId());
    ArrayList<MutationRuntimeStore.CopiedEffect> removed = new ArrayList<>();
    synchronized (runtime) {
      runtime.mycelial.copies.entrySet().removeIf(entry -> {
        if (entry.getKey().type() != type) {
          return false;
        }
        removed.add(entry.getValue());
        return true;
      });
    }
    for (MutationRuntimeStore.CopiedEffect copy : removed) {
      removeCopy(copy);
    }
  }

  private void removeCopy(MutationRuntimeStore.CopiedEffect copy) {
    MutationRuntimeStore.EffectKey key = new MutationRuntimeStore.EffectKey(copy.recipientId(), copy.type());
    LivingEntity recipient = copyRecipients.get(key);
    if (!releaseCopyOwnership(key, copy) || recipient == null) {
      return;
    }
    J.runEntity(recipient, () -> removeEffectOnRecipient(recipient, copy));
  }

  private void releaseCopyOwnership(MutationRuntimeStore.EffectKey key) {
    MutationRuntimeStore.CopiedEffect copy = copyOwners.get(key);
    if (copy == null) {
      return;
    }
    releaseCopyOwnership(key, copy);
  }

  private boolean releaseCopyOwnership(
      MutationRuntimeStore.EffectKey key,
      MutationRuntimeStore.CopiedEffect copy
  ) {
    if (!copyOwners.remove(key, copy)) {
      return false;
    }
    MutationRuntimeStore.PlayerRuntimeState runtime = store.players.get(copy.rootId());
    if (runtime != null) {
      synchronized (runtime) {
        runtime.mycelial.copies.remove(key, copy);
      }
    }
    copyRecipients.remove(key);
    copyAuthorizations.remove(key);
    Set<MutationRuntimeStore.EffectKey> copies = recipientCopies.get(copy.recipientId());
    if (copies != null) {
      copies.remove(key);
      if (copies.isEmpty()) {
        recipientCopies.remove(copy.recipientId(), copies);
      }
    }
    return true;
  }

  private void removeCopyOnRecipient(LivingEntity recipient, MutationRuntimeStore.CopiedEffect copy) {
    MutationRuntimeStore.EffectKey key = new MutationRuntimeStore.EffectKey(copy.recipientId(), copy.type());
    if (!releaseCopyOwnership(key, copy)) {
      return;
    }
    removeEffectOnRecipient(recipient, copy);
  }

  private void removeEffectOnRecipient(LivingEntity recipient, MutationRuntimeStore.CopiedEffect copy) {
    PotionEffect current = recipient.getPotionEffect(copy.type());
    if (!matchesOwnedCopy(current, copy)) {
      return;
    }
    MutationRuntimeStore.EffectKey key = new MutationRuntimeStore.EffectKey(copy.recipientId(), copy.type());
    internalApplications.put(key, InternalEffectAction.REMOVE);
    recipient.removePotionEffect(copy.type());
  }

  private void removeReceivedCopies(Player recipient) {
    Set<MutationRuntimeStore.EffectKey> copies = recipientCopies.get(recipient.getUniqueId());
    if (copies == null || copies.isEmpty()) {
      return;
    }
    for (MutationRuntimeStore.EffectKey key : List.copyOf(copies)) {
      MutationRuntimeStore.CopiedEffect copy = copyOwners.get(key);
      if (copy != null) {
        removeCopyOnRecipient(recipient, copy);
      }
    }
  }

  private boolean matchesOwnedCopy(PotionEffect current, MutationRuntimeStore.CopiedEffect copy) {
    if (current == null || current.getAmplifier() != copy.amplifier()) {
      return false;
    }
    int expected = Math.max(0, (int) Math.ceil((copy.expiresAt() - System.currentTimeMillis()) / 50D));
    return current.getDuration() <= expected + 3 && current.getDuration() >= Math.max(0, expected - 8);
  }

  private boolean isSelfApplied(Player player, EntityPotionEffectEvent event) {
    if (event.getSource() != null && event.getSource().getUniqueId().equals(player.getUniqueId())) {
      return true;
    }
    return switch (event.getCause()) {
      case POTION_DRINK, FOOD, TOTEM -> true;
      default -> false;
    };
  }

  private boolean isNaturalFooting(Player player) {
    Block footing = player.getLocation().subtract(0D, 1D, 0D).getBlock();
    return !footing.getType().isAir() && !footing.isLiquid() && !provenance.isPlayerPlaced(footing) && !provenance.isTemporary(footing);
  }

  private boolean isFire(EntityDamageEvent.DamageCause cause) {
    return cause == EntityDamageEvent.DamageCause.FIRE
        || cause == EntityDamageEvent.DamageCause.FIRE_TICK
        || cause == EntityDamageEvent.DamageCause.LAVA
        || cause == EntityDamageEvent.DamageCause.HOT_FLOOR
        || cause == EntityDamageEvent.DamageCause.CAMPFIRE;
  }

  private void trackNonCleansable(Player player, EntityPotionEffectEvent event) {
    MutationRuntimeStore.PlayerRuntimeState runtime = store.player(player.getUniqueId());
    synchronized (runtime) {
      if (event.getAction() == EntityPotionEffectEvent.Action.REMOVED
          || event.getAction() == EntityPotionEffectEvent.Action.CLEARED) {
        runtime.molt.nonCleansableEffects.remove(event.getModifiedType());
      } else if (event.getCause() == EntityPotionEffectEvent.Cause.COMMAND
          || event.getCause() == EntityPotionEffectEvent.Cause.PLUGIN) {
        runtime.molt.nonCleansableEffects.add(event.getModifiedType());
      }
    }
  }

  private boolean isCleansable(MutationRuntimeStore.PlayerRuntimeState runtime, PotionEffectType type) {
    synchronized (runtime) {
      return !runtime.molt.nonCleansableEffects.contains(type);
    }
  }

  private record NetworkContext(
      UUID rootId,
      UUID worldId,
      MutationRuntimeAccess.CooperativeConsent consent
  ) {
  }

  private record RootConsentSession(long generation, NetworkContext context) {
  }

  private enum InternalEffectAction {
    COPY,
    ROOT,
    REMOVE
  }
}
