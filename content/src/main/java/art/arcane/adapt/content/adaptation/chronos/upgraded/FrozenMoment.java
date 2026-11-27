package art.arcane.adapt.content.adaptation.chronos.upgraded;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.Cooldowns;
import art.arcane.adapt.api.content.PlatformNeed;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.mutation.LingeringSound;
import art.arcane.adapt.api.mutation.MutationCue;
import art.arcane.adapt.api.mutation.SkillUpgrade;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.content.adaptation.chronos.ChronosAberrantTouch;
import art.arcane.adapt.content.adaptation.chronos.ChronosArmedThrows;
import art.arcane.adapt.content.adaptation.chronos.ChronosRoot;
import art.arcane.adapt.content.adaptation.chronos.ChronosSoundFX;
import art.arcane.adapt.content.adaptation.chronos.ChronosStasis;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityRemoveEvent;
import art.arcane.adapt.platform.event.entity.ProjectileHitEvent;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stasis Field, Time Bomb and Aberrant Touch fused: sneak-using an amethyst shard throws it, and where it lands a stasis
 * field opens; your melee hits on anything inside the field root it.
 */
public class FrozenMoment extends SkillUpgrade<FrozenMoment.Config> {
  public static final Id ID = Id.adapt("chronos/upgrade/frozen_moment");

  private static final Id AMETHYST_SHARD = Id.minecraft("amethyst_shard");
  private static final Id SNOWBALL = Id.minecraft("snowball");

  private final Cooldowns throwCooldowns = cooldowns();
  private final Map<UUID, Long> rootReadyAt = new ConcurrentHashMap<>();
  private final ChronosArmedThrows armedThrows = new ChronosArmedThrows();
  private final ChronosStasis stasis = new ChronosStasis(this, () -> new ChronosStasis.Tuning(false,
      getConfig().fieldSlownessAmplifier, getConfig().fieldJumpLockAmplifier, 20, 250L, 12, 400L));

  public FrozenMoment() {
    super(ID);
    registerConfiguration(Config.class);
    needs(PlatformNeed.partialAttribute(Attributes.JUMP_STRENGTH, "creatures in the field can still jump"));
    setIcon(Id.minecraft("packed_ice"));
    setInterval(50);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Format.f(getRadius(level), 1), 1);
    statLore(v, Format.duration(getDurationMillis(level), 1), 2);
    statLore(v, TextFormat.AQUA, "* ", Format.duration(getConfig().rootTicks * 50D, 1), 3);
  }

  @Override
  public void unregister() {
    stasis.shutdown();
    armedThrows.clear();
    rootReadyAt.clear();
    super.unregister();
  }

  @Override
  public void onTick() {
    stasis.tick();
    long now = System.currentTimeMillis();
    armedThrows.expire(now);
    rootReadyAt.values().removeIf((Long readyAt) -> readyAt <= now);
  }

  @Handle
  public void on(PlayerQuitEvent e) {
    stasis.quit(e.player());
    armedThrows.forgetOwner(e.player().uuid());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityRemoveEvent e) {
    if (e.entity() instanceof ProjectileRef projectile) {
      stasis.forgetProjectile(projectile.uuid());
    }
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(PlayerInteractEvent e) {
    InteractAction action = e.action();
    PlayerRef p = e.player();
    Hand hand = e.hand();
    if ((action != InteractAction.RIGHT_CLICK_AIR && action != InteractAction.RIGHT_CLICK_BLOCK)
        || hand == null || !p.isSneaking()) {
      return;
    }
    ItemRef held = p.inventory().hand(hand);
    int level = getActiveLevel(p);
    if (!held.type().equals(AMETHYST_SHARD) || level <= 0) {
      return;
    }

    e.setCancelled(true);
    long cooldownMillis = getConfig().throwCooldownMillis;
    if (!stasis.isAccepting() || !throwCooldowns.isReady(p.uuid(), cooldownMillis)) {
      ChronosSoundFX.playClockReject(p);
      return;
    }
    if (!payItemCost(p, "shard", AdaptApi.platform().host().items().create(AMETHYST_SHARD, 1), 1, () -> {
      held.setAmount(held.amount() - 1);
      p.inventory().setHand(hand, held);
      return true;
    })) {
      return;
    }

    throwCooldowns.mark(p.uuid(), cooldownMillis);
    ProjectileRef thrown = p.launchProjectile(SNOWBALL, (ProjectileRef projectile) -> projectile.setItem(
        AdaptApi.platform().host().items().create(AMETHYST_SHARD, 1)));
    armedThrows.arm(thrown.uuid(), p.uuid(), level, System.currentTimeMillis());
    ChronosSoundFX.playTimeBombArm(p);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(ProjectileHitEvent e) {
    ChronosArmedThrows.Armed armed = armedThrows.land(e.projectile().uuid());
    if (armed == null) {
      return;
    }
    PlayerRef owner = AdaptApi.platform().host().player(armed.owner());
    Loc center = e.projectile().location();
    if (owner == null || !owner.isOnline()) {
      return;
    }

    long now = System.currentTimeMillis();
    long durationMillis = getDurationMillis(armed.level());
    ChronosStasis.StasisBubble bubble = canInteract(owner, center)
        ? stasis.open(owner, center, getRadius(armed.level()), now, now + durationMillis)
        : null;
    if (bubble == null) {
      throwCooldowns.clear(owner.uuid());
      return;
    }
    stasis.castFx(bubble);
    ChronosSoundFX.playTimeBombDetonate(center);
    addStat(owner, "chronos.frozen-moment.fields", 1);
    cue(owner, MutationCue.FIGHT);
    LingeringSound lingering = linger(owner);
    AdaptApi.scheduler().runEntity(owner, lingering::stop, (int) Math.max(1L, durationMillis / 50L));
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityDamageByEntityEvent e) {
    MeleeContext combat = resolveMeleeContext(e);
    long now = System.currentTimeMillis();
    if (combat == null || !stasis.insideOwnBubble(combat.attacker().uuid(), combat.target().location())
        || rootReadyAt.getOrDefault(combat.target().uuid(), 0L) > now) {
      return;
    }

    rootReadyAt.put(combat.target().uuid(), now + getConfig().rootCooldownMillis);
    ChronosRoot.root(combat.target(), getConfig().rootTicks, getConfig().rootAmplifier);
    ChronosAberrantTouch.rootFx(this, combat.target());
    addStat(combat.attacker(), "chronos.frozen-moment.roots", 1);
    cue(combat.attacker(), MutationCue.FIGHT);
  }

  double getRadius(int level) {
    return Math.min(12D, getConfig().radiusBase + (level * getConfig().radiusPerLevel));
  }

  long getDurationMillis(int level) {
    return Math.min(30000L, getConfig().durationMillisBase + (level * getConfig().durationMillisPerLevel));
  }

  @ConfigDescription("Sneak-use an amethyst shard to throw a stasis field; your melee hits inside it root the target.")
  public static class Config extends SkillUpgrade.Config {
    @ConfigDoc(value = "Field radius before level scaling, in blocks.", impact = "Higher values cover more ground; capped at 12.")
    double radiusBase = 3.5;
    @ConfigDoc(value = "Field radius added per level, in blocks.", impact = "Higher values widen the field at higher levels.")
    double radiusPerLevel = 0.75;
    @ConfigDoc(value = "Field duration before level scaling, in milliseconds.", impact = "Higher values keep the field up longer; capped at 30 seconds.")
    long durationMillisBase = 4000;
    @ConfigDoc(value = "Field duration added per level, in milliseconds.", impact = "Higher values reward higher levels more.")
    long durationMillisPerLevel = 1000;
    @ConfigDoc(value = "Time between two throws, in milliseconds.", impact = "Lower values allow throwing more often.")
    long throwCooldownMillis = 15000;
    @ConfigDoc(value = "Slowness amplifier on creatures inside the field.", impact = "Higher values hold them more firmly.")
    int fieldSlownessAmplifier = 4;
    @ConfigDoc(value = "Jump strength change inside the field, in sixths; negative values lower jumps.", impact = "Lower values keep creatures grounded.")
    int fieldJumpLockAmplifier = -6;
    @ConfigDoc(value = "Root duration from a hit inside the field, in ticks.", impact = "Higher values hold the target longer.")
    int rootTicks = 40;
    @ConfigDoc(value = "Slowness amplifier of the root.", impact = "Higher values make the root firmer.")
    int rootAmplifier = 6;
    @ConfigDoc(value = "Time before the same target can be rooted again, in milliseconds.", impact = "Lower values allow chaining roots.")
    long rootCooldownMillis = 3000;
  }
}
