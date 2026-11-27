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

package art.arcane.adapt.content.adaptation.rift;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.PlatformNeed;
import art.arcane.adapt.api.content.ReceiveCancelledEvents;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.item.ItemKinds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.content.item.BoundEnderPearl;
import art.arcane.adapt.api.item.ItemCooldowns;
import art.arcane.adapt.api.world.Chunks;
import art.arcane.adapt.platform.EngineFeature;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.entity.TeleportCause;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.ProjectileHitEvent;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.player.PlayerTeleportEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Box;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;


public class RiftEnderTaglock extends SimpleAdaptation<RiftEnderTaglock.Config> {
  public static final Id ID = Id.adapt("rift/ender_taglock");
  public static final PlayerPreference<Targets> TARGETS = PlayerPreference.of("targets", Targets.class, Targets.ALL, List.of(
          new PlayerPreference.Choice<>(Targets.ALL, Text.key("adapt.riftendertaglock.preferences.preference_targets_all"), Id.minecraft("ender_pearl"), 1),
          new PlayerPreference.Choice<>(Targets.HOSTILES, Text.key("adapt.riftendertaglock.preferences.preference_targets_hostiles"), Id.minecraft("zombie_head"), 1),
          new PlayerPreference.Choice<>(Targets.ANIMALS, Text.key("adapt.riftendertaglock.preferences.preference_targets_animals"), Id.minecraft("wheat"), 1),
          new PlayerPreference.Choice<>(Targets.PLAYERS, Text.key("adapt.riftendertaglock.preferences.preference_targets_players"), Id.minecraft("player_head"), 1))).labelled(Text.key("adapt.riftendertaglock.preferences.preference_targets"));
  public static final PlayerPreference<CommonPreferences.Toggle> CONFIRM = CommonPreferences.toggle("confirm", Text.key("adapt.riftendertaglock.preferences.preference_confirm"), CommonPreferences.Toggle.OFF);

  public static final double LARGE_WIDTH = 1.3;
  public static final double LARGE_HEIGHT = 2.35;
  private static final double PEARL_TELEPORT_DAMAGE = 5.0;
  private static final int MAX_PENDING_TELEPORTS = 2048;
  private static final int MAX_TARGET_SNAPSHOT_ATTEMPTS = 2;
  private static final long OPERATION_TIMEOUT_MILLIS = 60_000L;
  private static final long TELEPORT_COMPLETION_TIMEOUT_MILLIS = 300_000L;
  private static final long MIN_SUPPRESSION_MILLIS = 50L;
  private static final long MAX_SUPPRESSION_MILLIS = 2000L;
  static final String TARGET_KEY_NAME = "rift_taglock_target_uuid";
  /**
   * Taglocks ride on an ender pearl, so the throw cooldown lives in its own
   * group. A taglock on cooldown must never gray out or block plain pearls.
   */
  public static final Id COOLDOWN_GROUP = Id.adapt("item_rift_ender_taglock");
  private static final Id ENDER_PEARL = Id.minecraft("ender_pearl");
  private static final Id VILLAGER = Id.minecraft("villager");
  private final ItemCooldowns throwCooldown = ItemCooldowns.forGroup(COOLDOWN_GROUP).ownedBy(this);
  private final DataKey<String> targetKey;
  private final Map<UUID, Long> suppressPearlTeleportUntil = playerState();
  private final TeleportAdmission pendingTeleports = new TeleportAdmission(MAX_PENDING_TELEPORTS);
  private volatile boolean acceptingTeleports = true;

  public RiftEnderTaglock() {
    super(ID);
    needs(PlatformNeed.partialFeature(EngineFeature.ITEM_COOLDOWN_GROUPS, "no cooldown sweep on the item"));
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("ender_pearl"));
    setInterval(1200);
    targetKey = DataKey.adapt(TARGET_KEY_NAME, DataCodecs.STRING);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("ender_pearl"))
        .key("challenge_rift_taglock_100")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("ender_eye"))
            .key("challenge_rift_taglock_500")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_rift_taglock_100", "rift.ender-taglock.entities-tagged", 100, 400);
    registerMilestone("challenge_rift_taglock_500", "rift.ender-taglock.taglocked-teleports", 500, 1000);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, TARGETS, CONFIRM);
  }

  @Override
  public void unregister() {
    acceptingTeleports = false;
    pendingTeleports.clear();
    suppressPearlTeleportUntil.clear();
    super.unregister();
  }

  @Override
  public void onTick() {
    long now = System.currentTimeMillis();
    suppressPearlTeleportUntil.entrySet().removeIf(i -> i.getValue() <= now);
    pendingTeleports.purgeExpired(now);
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(TextFormat.GREEN + "+ " + Text.key("adapt.rift.ender_taglock.lore1").resolve());
    if (level >= 2) {
      v.addLore(TextFormat.GREEN + "+ " + Text.key("adapt.rift.ender_taglock.lore2").resolve());
    }
    if (level >= 3) {
      v.addLore(TextFormat.GREEN + "+ " + Text.key("adapt.rift.ender_taglock.lore3").resolve());
    }
    statLore(v, TextFormat.YELLOW, "* ", Format.duration(getThrowCooldownTicks(level) * 50D, 1), 4);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityDamageByEntityEvent e) {
    if (!(e.damager() instanceof PlayerRef p) || !(e.entity() instanceof LivingRef target)) {
      return;
    }

    int level = getActiveDamageLevel(p, target);
    if (level <= 0 || !p.isSneaking()) {
      return;
    }

    ItemRef hand = p.inventory().mainHand();
    if (!ItemKinds.isItem(hand) || !hand.type().equals(ENDER_PEARL) || BoundEnderPearl.isBindableItem(hand)) {
      return;
    }

    Targets targets = preference(p, TARGETS);
    if (!isTaggable(target, level)
        || (targets == Targets.HOSTILES && !target.has(EntityTrait.MONSTER))
        || (targets == Targets.ANIMALS && !target.has(EntityTrait.ANIMAL))
        || (targets == Targets.PLAYERS && !(target instanceof PlayerRef))) {
      return;
    }

    if (preferenceEnabled(p, CONFIRM) && !confirm(p, "bind:" + target.uuid(), hand)) {
      e.setCancelled(true);
      return;
    }
    if (!tagIntoPearl(p, hand, target)) {
      return;
    }

    e.setCancelled(true);
    fx(p, FxPriority.COMBAT).sound(Sounds.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.55f, 1.4f);
    timeline(target)
        .duration(8)
        .priority(FxPriority.COMBAT)
        .cullRadius(24)
        .frame((fx, tick, progress) -> {
          fx.ring(Particles.PORTAL, 1.2 - (1.1 * progress), 8, 1.0);
          if (tick == 7) {
            fx.particle(Particles.REVERSE_PORTAL, 6, 0, 1.0, 0, 0.2, 0.03);
          }
          if (tick % 3 == 0) {
            fx.sound(Sounds.BLOCK_AMETHYST_BLOCK_RESONATE, 0.5f, (float) (1.0 + (progress * 0.6)));
          }
        })
        .start();
    addStat(p, "rift.ender-taglock.entities-tagged", 1);
    xp(p, getConfig().xpOnTag);
  }

  @ReceiveCancelledEvents
  @Handle(priority = EventPriority.HIGHEST)
  public void on(PlayerInteractEvent e) {
    Hand slot = e.hand();
    if (slot == null) {
      return;
    }

    InteractAction action = e.action();
    if (action != InteractAction.RIGHT_CLICK_AIR && action != InteractAction.RIGHT_CLICK_BLOCK) {
      return;
    }

    PlayerRef p = e.player();
    ItemRef hand = slot == Hand.MAIN_HAND
        ? p.inventory().mainHand()
        : p.inventory().offHand();

    UUID target = getTaggedTarget(hand);
    if (target == null) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      e.setCancelled(true);
      return;
    }

    if (e.useItem() == UseResult.DENY) {
      return;
    }

    if (e.clickedBlock() != null && e.useBlock() == UseResult.DENY) {
      e.setCancelled(true);
      return;
    }

    e.setCancelled(true);
    upgradeLegacyTaglock(p, slot, hand);
    long throwCooldownMillis = getThrowCooldownTicks(level) * 50L;
    if (!throwCooldown.isReady(p, throwCooldownMillis)) {
      fx(p.eyeLocation(), FxPriority.TRANSITION)
          .burst(Particles.SMOKE, 2, 0.15)
          .sound(Sounds.BLOCK_NOTE_BLOCK_DIDGERIDOO, 0.55f, 0.6f);
      AdaptApi.log().verbose("Ender Taglock throw declined for " + p.uuid() + ": "
          + throwCooldown.remaining(p, throwCooldownMillis) + "ms cooldown left.");
      return;
    }

    if (!decrementTaggedPearl(p, slot, hand)) {
      return;
    }

    String targetId = target.toString();
    // The target key must land before the projectile spawns; ProjectileLaunchEvent fires during the
    // spawn and other pearl adaptations claim anything that still looks like a plain pearl there.
    p.launchProjectile(ENDER_PEARL, (ProjectileRef pearl) -> pearl.data().set(targetKey, targetId));
    throwCooldown.mark(p, throwCooldownMillis);
    Vec3 dir = p.location().direction();
    fx(p.eyeLocation(), FxPriority.TRANSITION)
        .trail(Particles.REVERSE_PORTAL, dir.x(), dir.y(), dir.z(), 1.5, 8)
        .chord(Sounds.ENTITY_ENDER_EYE_LAUNCH, 0.65f, 1.25f, Sounds.BLOCK_AMETHYST_BLOCK_HIT, 0.4f, 1.4f);
    xp(p, getConfig().xpOnThrow);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(PlayerTeleportEvent e) {
    if (e.cause() != TeleportCause.ENDER_PEARL) {
      return;
    }

    UUID id = e.player().uuid();
    Long armedUntil = suppressPearlTeleportUntil.remove(id);
    if (!shouldSuppressPearlTeleport(armedUntil, System.currentTimeMillis())) {
      return;
    }

    e.setCancelled(true);
    e.player().setFallDistance(0f);
  }

  @ReceiveCancelledEvents
  @Handle(priority = EventPriority.MONITOR)
  public void on(ProjectileHitEvent e) {
    ProjectileRef pearl = e.projectile();
    if (!pearl.type().equals(ENDER_PEARL) || !(pearl.shooter() instanceof PlayerRef thrower)) {
      return;
    }

    if (pearl.isDead() || !pearl.isValid()) {
      return;
    }

    String raw = pearl.data().get(targetKey);
    UUID targetId = parseTargetId(raw);
    if (targetId == null) {
      return;
    }

    if (vanillaPearlTeleportStillRuns(e.isCancelled(), e.hitBlock() != null)) {
      suppressPearlTeleportUntil.put(
          thrower.uuid(),
          System.currentTimeMillis() + getSuppressPearlTeleportWindowMillis()
      );
      if (e.isCancelled()) {
        pearl.remove();
      }
    }

    if (e.isCancelled()) {
      return;
    }

    Loc destination = resolveDestination(e);
    pearl.remove();
    AdaptApi.scheduler().runEntity(thrower, () -> prepareThrowerOwned(thrower, targetId, destination));
  }

  @Handle
  public void on(PlayerQuitEvent e) {
    UUID playerId = e.player().uuid();
    suppressPearlTeleportUntil.remove(playerId);
    pendingTeleports.cancel(playerId);
  }

  /**
   * A taglocked pearl only keeps the vanilla thrower teleport once the hit resolves, so suppression is
   * armed there rather than at launch. Paper still runs the pearl's block impact when the hit event is
   * cancelled, so a cancelled block hit teleports the thrower and must stay suppressed anyway. Folia
   * never posts the ender pearl teleport, so the pearl is also discarded inside its hit event: vanilla
   * skips the impact of a removed pearl on every platform.
   */
  static boolean vanillaPearlTeleportStillRuns(boolean hitCancelled, boolean blockHit) {
    return !hitCancelled || blockHit;
  }

  static boolean shouldSuppressPearlTeleport(Long armedUntil, long now) {
    return armedUntil != null && armedUntil > now;
  }

  private void prepareThrowerOwned(PlayerRef thrower, UUID targetId, Loc destination) {
    if (!acceptingTeleports) {
      return;
    }

    UUID throwerId = thrower.uuid();
    int level = getActiveLevel(thrower);
    if (!thrower.isOnline() || level <= 0 || throwerId.equals(targetId)) {
      return;
    }

    EntityRef entity = AdaptApi.platform().host().entity(targetId);
    if (!(entity instanceof LivingRef target)) {
      return;
    }

    long token = pendingTeleports.admit(throwerId, System.currentTimeMillis(), OPERATION_TIMEOUT_MILLIS);
    if (token < 0L) {
      return;
    }

    TaglockOperation operation = new TaglockOperation(
        thrower,
        throwerId,
        target,
        targetId,
        destination,
        level,
        getConfig().damageSender,
        token
    );
    if (!AdaptApi.scheduler().runEntity(target, () -> prepareTargetOwned(operation, 0))) {
      pendingTeleports.complete(throwerId, token);
    }
  }

  private void prepareTargetOwned(TaglockOperation operation, int attempt) {
    if (!isCurrent(operation)) {
      return;
    }

    LivingRef target = operation.target();
    if (!isUsableTargetOwned(operation)) {
      pendingTeleports.complete(operation.throwerId(), operation.token());
      return;
    }

    TargetSnapshot snapshot = new TargetSnapshot(target.location(), target instanceof PlayerRef);
    if (!AdaptApi.scheduler().runEntity(operation.thrower(), () -> validatePolicyOwned(operation, snapshot, attempt))) {
      pendingTeleports.complete(operation.throwerId(), operation.token());
    }
  }

  private void validatePolicyOwned(TaglockOperation operation, TargetSnapshot snapshot, int attempt) {
    if (!isCurrent(operation)) {
      return;
    }

    PlayerRef thrower = operation.thrower();
    if (!thrower.isOnline() || getActiveLevel(thrower) <= 0) {
      pendingTeleports.complete(operation.throwerId(), operation.token());
      return;
    }

    boolean allowed = snapshot.playerTarget()
        ? canPVP(thrower, snapshot.location()) && canPVP(thrower, operation.destination())
        : canPVE(thrower, snapshot.location()) && canPVE(thrower, operation.destination());
    if (!allowed) {
      pendingTeleports.complete(operation.throwerId(), operation.token());
      return;
    }

    Chunks.load(operation.destination(), () -> scheduleTargetApply(operation, snapshot, attempt));
  }

  private void scheduleTargetApply(TaglockOperation operation, TargetSnapshot snapshot, int attempt) {
    if (!isCurrent(operation)) {
      return;
    }

    if (!AdaptApi.scheduler().runEntity(operation.target(), () -> applyTargetOwned(operation, snapshot, attempt))) {
      pendingTeleports.complete(operation.throwerId(), operation.token());
    }
  }

  private void applyTargetOwned(TaglockOperation operation, TargetSnapshot snapshot, int attempt) {
    if (!isCurrent(operation)) {
      return;
    }

    if (!isUsableTargetOwned(operation)) {
      pendingTeleports.complete(operation.throwerId(), operation.token());
      return;
    }

    LivingRef target = operation.target();
    Loc origin = target.location();
    if (!snapshot.sameBlock(origin)) {
      if (attempt + 1 < MAX_TARGET_SNAPSHOT_ATTEMPTS) {
        prepareTargetOwned(operation, attempt + 1);
      } else {
        pendingTeleports.complete(operation.throwerId(), operation.token());
      }
      return;
    }

    if (!pendingTeleports.markTeleporting(
        operation.throwerId(),
        operation.token(),
        System.currentTimeMillis(),
        TELEPORT_COMPLETION_TIMEOUT_MILLIS
    )) {
      return;
    }

    CompletableFuture<Boolean> completion;
    try {
      completion = target.teleport(operation.destination(), TeleportCause.PLUGIN);
    } catch (RuntimeException error) {
      pendingTeleports.complete(operation.throwerId(), operation.token());
      reportTeleportFailure(operation.targetId(), error);
      return;
    }

    if (completion == null) {
      pendingTeleports.complete(operation.throwerId(), operation.token());
      return;
    }
    completion.whenComplete((Boolean success, Throwable error) -> finishTeleport(operation, origin, success, error));
  }

  private void finishTeleport(TaglockOperation operation, Loc origin, Boolean success, Throwable error) {
    if (error != null) {
      pendingTeleports.complete(operation.throwerId(), operation.token());
      reportTeleportFailure(operation.targetId(), error);
      return;
    }

    if (!Boolean.TRUE.equals(success)
        || !pendingTeleports.complete(operation.throwerId(), operation.token())) {
      return;
    }

    AdaptApi.scheduler().runEntity(operation.target(), () -> finishTargetOwned(operation, origin));
    AdaptApi.scheduler().runEntity(operation.thrower(), () -> rewardThrowerOwned(operation));
  }

  private void finishTargetOwned(TaglockOperation operation, Loc origin) {
    LivingRef target = operation.target();
    if (!operation.damageSender() && target.isValid() && !target.isDead()) {
      if (target instanceof PlayerRef player) {
        applyPlayerDamage(player, PEARL_TELEPORT_DAMAGE);
      } else {
        target.damage(PEARL_TELEPORT_DAMAGE, (EntityRef) null);
      }
    }

    double shockRadius = getShockRadius(operation.level());
    fx(origin, FxPriority.COMBAT)
        .particle(Particles.REVERSE_PORTAL, 10, 0, 1.0, 0, 0.3, 0.05);
    timeline(operation.destination())
        .duration(5)
        .priority(FxPriority.COMBAT)
        .cullRadius(28)
        .frame((fx, tick, progress) -> {
          fx.ring(Particles.END_ROD, 0.3 + ((shockRadius - 0.3) * progress), 16, 0.1);
          if (tick == 0) {
            fx.chord(Sounds.ENTITY_ENDERMAN_TELEPORT, 0.75f, 1.35f, Sounds.ENTITY_ELDER_GUARDIAN_CURSE, 0.5f, 1.9f);
          }
        })
        .start();
    if (target instanceof PlayerRef victim && victim.isOnline()) {
      fx(victim, FxPriority.COMBAT).sound(Sounds.ENTITY_ELDER_GUARDIAN_CURSE, 0.75f, 1.9f);
    }
  }

  private void rewardThrowerOwned(TaglockOperation operation) {
    PlayerRef thrower = operation.thrower();
    if (!thrower.isOnline()) {
      return;
    }

    if (operation.damageSender() && thrower.isValid() && !thrower.isDead()) {
      applyPlayerDamage(thrower, PEARL_TELEPORT_DAMAGE);
    }
    addStat(thrower, "rift.ender-taglock.taglocked-teleports", 1);
    xp(thrower, getConfig().xpOnTeleport);
  }

  private boolean isUsableTargetOwned(TaglockOperation operation) {
    LivingRef target = operation.target();
    if (!target.isValid() || target.isDead() || !operation.targetId().equals(target.uuid())) {
      return false;
    }
    if (isProtectedFriendly(null, target)) {
      return false;
    }
    return !operation.throwerId().equals(target.tameOwner());
  }

  private boolean isCurrent(TaglockOperation operation) {
    return pendingTeleports.isCurrent(
        operation.throwerId(),
        operation.token(),
        System.currentTimeMillis()
    );
  }

  private double getShockRadius(int level) {
    return level >= 3 ? 2.0 : (level == 2 ? 1.6 : 1.4);
  }

  private void reportTeleportFailure(UUID targetId, Throwable error) {
    AdaptApi.log().warn("Failed to complete an Ender Taglock teleport for target " + targetId + ".");
    AdaptApi.log().error("RiftEnderTaglock failed", error);
  }

  private Loc resolveDestination(ProjectileHitEvent e) {
    Loc base = e.projectile().location();
    if (e.hitBlock() != null && e.hitFace() != null) {
      BlockFace face = e.hitFace();
      Vec3 n = new Vec3(face.dx(), face.dy(), face.dz()).normalize();
      base = e.hitBlock().location().add(0.5, 0.5, 0.5).add(n.multiply(0.6));
    }

    return base.add(0, 0.05, 0);
  }

  private boolean decrementTaggedPearl(PlayerRef p, Hand slot, ItemRef stack) {
    if (!ItemKinds.isItem(stack)) {
      return false;
    }

    return payItemCost(p, "consume", AdaptApi.platform().host().items().create(stack.type(), 1), 1, () -> {
      if (stack.amount() <= 1) {
        p.inventory().setHand(slot, AdaptApi.platform().host().items().empty());
        return true;
      }

      stack.setAmount(stack.amount() - 1);
      p.inventory().setHand(slot, stack);
      return true;
    });
  }

  private boolean tagIntoPearl(PlayerRef p, ItemRef hand, LivingRef target) {
    ItemRef tagged = makeTaggedPearl(target);
    AtomicBoolean releasedMainHandSlot = new AtomicBoolean();

    if (!payItemCost(p, "bind", AdaptApi.platform().host().items().create(hand.type(), 1), 1, () -> {
      if (hand.amount() <= 1) {
        p.inventory().setMainHand(AdaptApi.platform().host().items().empty());
        releasedMainHandSlot.set(true);
        return true;
      }

      hand.setAmount(hand.amount() - 1);
      p.inventory().setMainHand(hand);
      return true;
    })) {
      return false;
    }

    if (releasedMainHandSlot.get()) {
      p.inventory().setMainHand(tagged);
    } else {
      for (ItemRef leftover : p.inventory().addItem(tagged)) {
        p.world().dropItemNaturally(p.location().position(), leftover);
      }
    }
    return true;
  }

  private ItemRef makeTaggedPearl(LivingRef target) {
    ItemRef item = AdaptApi.platform().host().items().create(ENDER_PEARL, 1);
    item.data().set(targetKey, target.uuid().toString());
    item.setDisplayName(TextFormat.LIGHT_PURPLE + Text.key("adapt.rift.ender_taglock.item_name").resolve());
    List<String> lore = new ArrayList<>();
    lore.add(TextFormat.GRAY + Text.key("adapt.rift.ender_taglock.target").with("target", TextFormat.WHITE + getTargetName(target)).resolve());
    lore.add(TextFormat.DARK_GRAY + Text.key("adapt.rift.ender_taglock.throw").resolve());
    item.setLore(lore);
    ItemCooldowns.stampGroup(item, COOLDOWN_GROUP);
    return item;
  }

  /**
   * Taglocks minted before the cooldown group existed still carry the plain
   * ender pearl group, so the sweep would land on vanilla pearls instead. Stamp
   * them the first time an owner actually uses one.
   */
  private void upgradeLegacyTaglock(PlayerRef p, Hand slot, ItemRef hand) {
    if (!ItemCooldowns.stampGroup(hand, COOLDOWN_GROUP)) {
      return;
    }

    p.inventory().setHand(slot, hand);
  }

  private UUID getTaggedTarget(ItemRef item) {
    if (!ItemKinds.isItem(item) || !item.type().equals(ENDER_PEARL)) {
      return null;
    }

    String raw = item.data().get(targetKey);
    return parseTargetId(raw);
  }

  private UUID parseTargetId(String raw) {
    if (raw == null || raw.length() != 36) {
      return null;
    }

    try {
      return UUID.fromString(raw);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private String getTargetName(LivingRef target) {
    if (target instanceof PlayerRef player) {
      return player.name();
    }

    String customName = target.customName();
    if (customName != null && !customName.isEmpty()) {
      return customName;
    }

    return Format.capitalizeWords(target.type().path().toLowerCase().replaceAll("\\Q_\\E", " "));
  }

  private boolean isTaggable(LivingRef target, int level) {
    return taggable(target, level, getConfig().largeWidthThreshold, getConfig().largeHeightThreshold);
  }

  /**
   * Whether a taglock of {@code level} can tag {@code target}: small mobs at 1, villagers and large mobs from 2,
   * anything including players at 3. A mob is large from {@code largeWidth} wide or {@code largeHeight} tall.
   */
  public static boolean taggable(LivingRef target, int level, double largeWidth, double largeHeight) {
    if (target instanceof PlayerRef) {
      return level >= 3;
    }

    if (level >= 3) {
      return true;
    }

    boolean large = isLargeTarget(target, largeWidth, largeHeight);
    if (level == 2) {
      return isLevel1Taggable(target, large) || target.type().equals(VILLAGER) || large;
    }

    return isLevel1Taggable(target, large);
  }

  private static boolean isLevel1Taggable(LivingRef target, boolean large) {
    if (!(target instanceof MobRef)) {
      return false;
    }

    if (target.type().equals(VILLAGER)) {
      return false;
    }

    if (large) {
      return false;
    }

    return target.has(EntityTrait.ANIMAL)
        || target.has(EntityTrait.MONSTER)
        || target.has(EntityTrait.WATER_MOB)
        || target.has(EntityTrait.AMBIENT)
        || target.has(EntityTrait.SLIME);
  }

  private static boolean isLargeTarget(LivingRef target, double largeWidth, double largeHeight) {
    Box box = target.boundingBox();
    double width = Math.max(box.widthX(), box.widthZ());
    double height = box.height();
    return width >= largeWidth || height >= largeHeight;
  }

  private int getThrowCooldownTicks(int level) {
    return Math.max(4, (int) Math.round(getConfig().throwCooldownTicksBase - (getLevelPercent(level) * getConfig().throwCooldownTicksFactor)));
  }

  private long getSuppressPearlTeleportWindowMillis() {
    return Math.max(MIN_SUPPRESSION_MILLIS, Math.min(MAX_SUPPRESSION_MILLIS, getConfig().suppressPearlTeleportWindowMillis));
  }

  @ConfigDescription("Tag entities into ender pearls and throw those pearls to reposition the tagged target.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Controls Throw Cooldown Ticks Base for the Rift Ender Taglock adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double throwCooldownTicksBase = 30;
    @ConfigDoc(value = "Controls Throw Cooldown Ticks Factor for the Rift Ender Taglock adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double throwCooldownTicksFactor = 14;
    @ConfigDoc(value = "How long the thrower's vanilla pearl teleport stays suppressed after a taglocked pearl lands.", impact = "Backstop only; the suppression is armed on impact and consumed by the teleport that follows it in the same tick, so this only needs to survive that hand-off. Clamped to 50-2000ms.")
    long suppressPearlTeleportWindowMillis = 250L;
    @ConfigDoc(value = "Controls Large Width Threshold for the Rift Ender Taglock adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double largeWidthThreshold = LARGE_WIDTH;
    @ConfigDoc(value = "Controls Large Height Threshold for the Rift Ender Taglock adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double largeHeightThreshold = LARGE_HEIGHT;
    @ConfigDoc(value = "Controls XP On Tag for the Rift Ender Taglock adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpOnTag = 8;
    @ConfigDoc(value = "Controls XP On Throw for the Rift Ender Taglock adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpOnThrow = 5;
    @ConfigDoc(value = "Controls who takes the ender pearl teleport damage when a taglocked pearl lands.", impact = "True damages the thrower like a normal pearl; false damages the taglocked target instead.")
    boolean damageSender = true;
    @ConfigDoc(value = "Controls XP On Teleport for the Rift Ender Taglock adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpOnTeleport = 14;

    public Config() {
      baseCost = 5;
      costFactor = 0.6;
      maxLevel = 3;
      initialCost = 3;
    }
  }

  private record TaglockOperation(
      PlayerRef thrower,
      UUID throwerId,
      LivingRef target,
      UUID targetId,
      Loc destination,
      int level,
      boolean damageSender,
      long token
  ) {
  }

  private record TargetSnapshot(Loc location, boolean playerTarget) {
    private boolean sameBlock(Loc current) {
      return location.sameWorld(current)
          && location.blockX() == current.blockX()
          && location.blockY() == current.blockY()
          && location.blockZ() == current.blockZ();
    }
  }

  static final class TeleportAdmission {
    private final int capacity;
    private final Map<UUID, PendingTeleport> pending = new HashMap<>();
    private long nextToken;

    TeleportAdmission(int capacity) {
      this.capacity = Math.max(1, capacity);
    }

    synchronized long admit(UUID ownerId, long now, long timeoutMillis) {
      PendingTeleport existing = pending.get(ownerId);
      if (existing != null && existing.expiresAt() > now) {
        return -1L;
      }
      if (existing != null) {
        pending.remove(ownerId);
      }
      if (pending.size() >= capacity) {
        purgeExpired(now);
        if (pending.size() >= capacity) {
          return -1L;
        }
      }

      long token = ++nextToken;
      pending.put(ownerId, new PendingTeleport(token, deadline(now, timeoutMillis), false));
      return token;
    }

    synchronized boolean isCurrent(UUID ownerId, long token, long now) {
      PendingTeleport operation = pending.get(ownerId);
      if (operation == null || operation.token() != token) {
        return false;
      }
      if (operation.expiresAt() <= now) {
        pending.remove(ownerId);
        return false;
      }
      return true;
    }

    synchronized boolean markTeleporting(UUID ownerId, long token, long now, long timeoutMillis) {
      if (!isCurrent(ownerId, token, now)) {
        return false;
      }

      PendingTeleport operation = pending.get(ownerId);
      if (operation.teleporting()) {
        return false;
      }
      pending.put(ownerId, new PendingTeleport(token, deadline(now, timeoutMillis), true));
      return true;
    }

    synchronized boolean complete(UUID ownerId, long token) {
      PendingTeleport operation = pending.get(ownerId);
      if (operation == null || operation.token() != token) {
        return false;
      }
      pending.remove(ownerId);
      return true;
    }

    synchronized void cancel(UUID ownerId) {
      PendingTeleport operation = pending.get(ownerId);
      if (operation != null && !operation.teleporting()) {
        pending.remove(ownerId);
      }
    }

    synchronized int purgeExpired(long now) {
      int before = pending.size();
      pending.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
      return before - pending.size();
    }

    synchronized int size() {
      return pending.size();
    }

    synchronized void clear() {
      pending.clear();
    }

    private static long deadline(long now, long timeoutMillis) {
      long timeout = Math.max(1L, timeoutMillis);
      if (now > Long.MAX_VALUE - timeout) {
        return Long.MAX_VALUE;
      }
      return now + timeout;
    }

    private record PendingTeleport(long token, long expiresAt, boolean teleporting) {
    }
  }

  public enum Targets { ALL, HOSTILES, ANIMALS, PLAYERS }
}
