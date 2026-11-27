package art.arcane.adapt.content.adaptation.hunter;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.api.hud.HudGlyph;
import art.arcane.adapt.api.hud.HudStatus;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.IntSupplier;
import java.util.function.IntUnaryOperator;

/**
 * Placed snares that root hostile mobs crossing them, kept per owning adaptation. With captures, an animal or neutral
 * mob that crosses one is caught instead and the snare is spent.
 */
public final class HunterSnares {
  public static final Id TRIPWIRE_HOOK = Id.minecraft("tripwire_hook");
  private static final DataKey<Byte> SNARE_ITEM_KEY = DataKey.adapt("hunter-snare-item", DataCodecs.BYTE);
  private final Adaptation<?> owner;
  private final BiConsumer<PlayerRef, LivingRef> onSnared;
  private final List<Snare> snares = new CopyOnWriteArrayList<>();
  private final Capture capture;

  public HunterSnares(Adaptation<?> owner, BiConsumer<PlayerRef, LivingRef> onSnared) {
    this(owner, onSnared, null);
  }

  public HunterSnares(Adaptation<?> owner, BiConsumer<PlayerRef, LivingRef> onSnared, Capture capture) {
    this.owner = owner;
    this.onSnared = onSnared;
    this.capture = capture;
  }

  /** Where caught creatures go, how long a snare at a level holds one, and how many one trapper may keep. */
  public record Capture(HunterCaptures captures, IntUnaryOperator holdTicksForLevel, IntSupplier maxPerPlayer) {
  }

  public record Settings(double triggerRadius, int rootAmplifier, long rearmBufferMillis, long snareLifetimeTicks,
                         int maxSnaresPerPlayer, int maxActiveSnares, int maxTargetsPerScan) {
  }

  public static double rootSpeedScalar(int amplifier) {
    return -Math.min(1.0D, 0.15D * (Math.max(0, amplifier) + 1.0D));
  }

  public static ItemRef buildSnareItem(int amount) {
    ItemRef item = AdaptApi.platform().host().items().create(TRIPWIRE_HOOK, amount);
    item.setDisplayName(TextFormat.GREEN + Text.key("adapt.hunter.snare_line.item").resolve());
    item.data().set(SNARE_ITEM_KEY, (byte) 1);
    return item;
  }

  public static boolean isSnareItem(ItemRef item) {
    if (item == null || item.isEmpty() || !item.type().equals(TRIPWIRE_HOOK)) {
      return false;
    }
    return item.data().has(SNARE_ITEM_KEY);
  }

  /**
   * Places a snare from the clicked snare item and returns true when one was set. Charges and root ticks follow the
   * placer's level.
   */
  public boolean place(PlayerInteractEvent e, Settings settings, IntUnaryOperator chargesForLevel, IntUnaryOperator rootTicksForLevel) {
    if (e.action() != InteractAction.RIGHT_CLICK_BLOCK || e.clickedBlock() == null) {
      return false;
    }

    Hand handSlot = e.hand();
    if (handSlot == null) {
      return false;
    }

    PlayerRef p = e.player();
    ItemRef hand = handSlot == Hand.OFF_HAND ? p.inventory().offHand() : p.inventory().mainHand();
    if (!isSnareItem(hand)) {
      return false;
    }

    boolean blockUseDenied = e.useBlock() == UseResult.DENY;
    e.setCancelled(true);
    if (!owner.hasActiveAdaptation(p) || (owner.preferenceEnabled(p, HunterPreferences.SNEAK) && !p.isSneaking())) {
      return false;
    }

    if (blockUseDenied) {
      return false;
    }

    BlockRef clicked = e.clickedBlock();
    Loc snareLocation = clicked.location().add(0.5D, 1.0D, 0.5D);
    if (!owner.canInteract(p, snareLocation)) {
      return false;
    }

    if (countOwned(p.uuid()) >= Math.max(1, settings.maxSnaresPerPlayer())) {
      HunterSnareLine.snareLimitFx(owner, p);
      return false;
    }
    if (snares.size() >= Math.max(1, settings.maxActiveSnares())) {
      return false;
    }

    int level = owner.getActiveLevel(p);
    consumeSnareItem(p, handSlot, hand);
    long now = System.currentTimeMillis();
    Snare snare = new Snare(
        p.uuid(),
        snareLocation,
        chargesForLevel.applyAsInt(level),
        rootTicksForLevel.applyAsInt(level),
        level,
        now + (settings.snareLifetimeTicks() * 50L));
    snares.add(snare);
    publishSnares(p.uuid(), settings);

    HunterSnareLine.snarePlacedFx(owner, snareLocation, settings.triggerRadius());
    return true;
  }

  public boolean isEmpty() {
    return snares.isEmpty();
  }

  public void forget(UUID playerId) {
    snares.removeIf((Snare snare) -> snare.owner.equals(playerId));
  }

  public void tick(Settings settings) {
    long now = System.currentTimeMillis();
    for (Snare snare : snares) {
      if (snare.expiresAt <= now || snare.charges.get() <= 0) {
        if (snares.remove(snare)) {
          emitSnareBreak(snare);
          publishSnares(snare.owner, settings);
        }
        continue;
      }
      AdaptApi.scheduler().runAt(snare.location, () -> scanSnare(snare, now, settings));
    }
  }

  private void consumeSnareItem(PlayerRef p, Hand handSlot, ItemRef hand) {
    int remaining = hand.amount() - 1;
    ItemRef replacement = remaining <= 0 ? AdaptApi.platform().host().items().empty() : hand.copyWithAmount(remaining);
    if (handSlot == Hand.OFF_HAND) {
      p.inventory().setOffHand(replacement);
    } else {
      p.inventory().setMainHand(replacement);
    }
  }

  private int countOwned(UUID playerId) {
    int count = 0;
    for (Snare snare : snares) {
      if (snare.owner.equals(playerId)) {
        count++;
      }
    }
    return count;
  }

  private void publishSnares(UUID playerId, Settings settings) {
    if (!HudStatus.capable(playerId)) {
      return;
    }
    int count = countOwned(playerId);
    if (count <= 0) {
      HudStatus.clear(playerId, owner, HudGlyph.CHARGES);
      return;
    }
    HudStatus.charges(playerId, owner, count, Math.max(1, settings.maxSnaresPerPlayer()));
  }

  private void scanSnare(Snare snare, long scheduledAt, Settings settings) {
    WorldRef world = snare.location.world();
    if (world == null || !snares.contains(snare) || snare.charges.get() <= 0) {
      return;
    }

    double radius = settings.triggerRadius();
    HunterSnareLine.snareArmedFx(owner, snare.location, radius);

    int budget = Math.max(1, settings.maxTargetsPerScan());
    int scheduled = 0;
    boolean catching = false;
    for (EntityRef entity : world.nearbyEntities(snare.location.position(), radius, radius, radius)) {
      if (!catching && canCatch(snare, entity)) {
        catching = true;
        LivingRef creature = (LivingRef) entity;
        AdaptApi.scheduler().runEntity(creature, () -> catchCreature(snare, creature, settings));
        continue;
      }
      if (!(entity instanceof LivingRef monster) || !entity.has(EntityTrait.MONSTER)) {
        continue;
      }
      if (scheduled >= budget || snare.charges.get() <= 0) {
        break;
      }
      Long rearm = snare.rearmUntil.get(monster.uuid());
      if (rearm != null && rearm > scheduledAt) {
        continue;
      }
      scheduled++;
      AdaptApi.scheduler().runEntity(monster, () -> rootMonster(snare, monster, settings));
    }
  }

  private void rootMonster(Snare snare, LivingRef monster, Settings settings) {
    if (!monster.isValid() || monster.isDead() || !snares.contains(snare)) {
      return;
    }

    if (owner.isProtectedFriendlyOwned(snare.owner, monster)) {
      return;
    }
    if (!snare.tryConsumeCharge()) {
      return;
    }

    long now = System.currentTimeMillis();
    snare.rearmUntil.put(monster.uuid(), now + (snare.rootDurationTicks * 50L) + settings.rearmBufferMillis());
    if (snare.rootDurationTicks > 0) {
      AdaptAttributeService.get().applyTimed(
          monster,
          owner.getId().toString(),
          "root",
          Attributes.MOVEMENT_SPEED,
          rootSpeedScalar(settings.rootAmplifier()),
          AttributeOperation.ADD_MULTIPLIED_TOTAL,
          snare.rootDurationTicks);
    }
    monster.setVelocity(Vec3.ZERO);

    HunterSnareLine.snaredFx(owner, monster);

    PlayerRef player = AdaptApi.platform().host().player(snare.owner);
    if (player != null) {
      AdaptApi.scheduler().runEntity(player, () -> {
        if (player.isOnline()) {
          onSnared.accept(player, monster);
        }
      });
    }
  }

  private boolean canCatch(Snare snare, EntityRef entity) {
    return capture != null && entity instanceof LivingRef && HunterCaptures.capturable(entity)
        && !capture.captures().isHeld(entity.uuid()) && !owner.isProtectedFriendlyOwned(snare.owner, entity)
        && capture.captures().count(snare.owner) < Math.max(1, capture.maxPerPlayer().getAsInt());
  }

  private void catchCreature(Snare snare, LivingRef creature, Settings settings) {
    PlayerRef trapper = AdaptApi.platform().host().player(snare.owner);
    if (trapper == null || !trapper.isOnline() || !creature.isValid() || creature.isDead() || !canCatch(snare, creature)
        || !owner.canInteract(trapper, creature.location()) || !owner.checkRegion(trapper, creature.location())
        || !snares.remove(snare)) {
      return;
    }
    if (!capture.captures().hold(snare.owner, creature, capture.holdTicksForLevel().applyAsInt(snare.level),
        capture.maxPerPlayer().getAsInt())) {
      snares.add(snare);
      return;
    }
    HunterSnareLine.snaredFx(owner, creature);
    emitSnareBreak(snare);
    publishSnares(snare.owner, settings);
    AdaptApi.scheduler().runEntity(trapper, () -> {
      if (trapper.isOnline()) {
        onSnared.accept(trapper, creature);
      }
    });
  }

  private void emitSnareBreak(Snare snare) {
    AdaptApi.scheduler().runAt(snare.location, () -> HunterSnareLine.snareBrokenFx(owner, snare.location));
  }

  private static final class Snare {
    private final UUID owner;
    private final Loc location;
    private final AtomicInteger charges;
    private final int rootDurationTicks;
    private final int level;
    private final long expiresAt;
    private final Map<UUID, Long> rearmUntil;

    private Snare(UUID owner, Loc location, int charges, int rootDurationTicks, int level, long expiresAt) {
      this.owner = owner;
      this.location = location;
      this.charges = new AtomicInteger(Math.max(1, charges));
      this.rootDurationTicks = rootDurationTicks;
      this.level = level;
      this.expiresAt = expiresAt;
      this.rearmUntil = new ConcurrentHashMap<>();
    }

    private boolean tryConsumeCharge() {
      int current = charges.get();
      while (current > 0) {
        if (charges.compareAndSet(current, current - 1)) {
          return true;
        }
        current = charges.get();
      }
      return false;
    }
  }
}
