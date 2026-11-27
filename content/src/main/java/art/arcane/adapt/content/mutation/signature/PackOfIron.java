package art.arcane.adapt.content.mutation.signature;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.ReceiveCancelledEvents;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.content.adaptation.ranged.HeartseekerGuidance;
import art.arcane.adapt.content.adaptation.taming.TameAttributeBoost;
import art.arcane.adapt.content.adaptation.taming.TamePackBuff;
import art.arcane.adapt.content.adaptation.taming.upgraded.Alpha;
import art.arcane.adapt.content.adaptation.taming.upgraded.BloodBond;
import art.arcane.adapt.content.adaptation.taming.upgraded.OneMind;
import art.arcane.adapt.content.golem.GolemBrain;
import art.arcane.adapt.content.golem.GolemCombat;
import art.arcane.adapt.content.golem.GolemHandle;
import art.arcane.adapt.content.golem.GolemRegistry;
import art.arcane.adapt.content.golem.GolemTeleport;
import art.arcane.adapt.content.golem.GolemancyRuntime;
import art.arcane.adapt.api.mutation.MutationSlot;
import art.arcane.adapt.api.mutation.Signature;
import art.arcane.adapt.api.mutation.BlockUse;
import art.arcane.adapt.api.mutation.Outcome;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.player.PlayerInteractEntityEvent;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Taming leading Golemancy, replacing Convergence: your golems count as pets. With Alpha they take its damage, health
 * and aura buffs; with Blood Bond they take a share of the hits you take; the Beastmaster's Whistle sends them at
 * what you point at and calls them back. Commands only override their work while they last.
 */
public final class PackOfIron extends Signature<PackOfIron.Config> {
  private static final Id GENERIC = Id.minecraft("generic");
  private static final Id WHISTLE = Id.adapt("taming/tool/whistle");
  private static final Set<DamageCause> SHARED = EnumSet.of(DamageCause.ENTITY_ATTACK, DamageCause.ENTITY_SWEEP_ATTACK,
      DamageCause.PROJECTILE, DamageCause.ENTITY_EXPLOSION, DamageCause.BLOCK_EXPLOSION, DamageCause.THORNS,
      DamageCause.SONIC_BOOM, DamageCause.DRAGON_BREATH);
  private final TameAttributeBoost damage = new TameAttributeBoost(Id.adapt("signature-pack-of-iron-damage"),
      Attributes.ATTACK_DAMAGE, (LivingRef golem) -> { });
  private final TameAttributeBoost health = new TameAttributeBoost(Id.adapt("signature-pack-of-iron-health"),
      Attributes.MAX_HEALTH, (LivingRef golem) -> { });
  private final Map<UUID, Map<UUID, MobRef>> buffed = playerState();

  public PackOfIron(MutationSlot slot) {
    super(slot);
    setLocalizationKey(PairMessages.signatureKey(slot));
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("iron_block"));
    setInterval(1000);
  }

  @Override
  public void addSignatureStats(int level, StatLines v) {
    statLore(v, Format.pc(buff(level), 0), 1);
    statLore(v, Format.pc(share(level), 0), 2);
    statLore(v, Format.f(whistleRange(level), 0), 3);
  }

  @Override
  public long cooldownMillis(int level) {
    return Math.max(0L, getConfig().cooldownMillis);
  }

  @Override
  protected boolean usesLearnerBoundTicking() {
    return true;
  }

  @Override
  public void unregister() {
    for (Map<UUID, MobRef> golems : buffed.values()) {
      golems.values().forEach(this::strip);
    }
    buffed.clear();
    super.unregister();
  }

  @Override
  public void onTick() {
    for (PlayerProfile data : learnedCandidates(System.currentTimeMillis())) {
      PlayerRef player = data.player();
      if (player != null) {
        AdaptApi.scheduler().runEntity(player, () -> lead(player));
      }
    }
  }

  /** Alpha's buffs on every golem inside Alpha's aura; golems that left it, or an owner without Alpha, lose them. */
  private void lead(PlayerRef player) {
    int level = getActiveLevel(player);
    Alpha alpha = level > 0 && usable() ? PairParts.owned(Id.adapt("taming/upgrade/alpha"), Alpha.class) : null;
    int alphaLevel = alpha != null ? alpha.getActiveLevel(player) : 0;
    Map<UUID, MobRef> now = new HashMap<>();
    if (alphaLevel > 0) {
      double amount = buff(level);
      int amplifier = alpha.getAuraAmplifier(alphaLevel);
      int ticks = alpha.getAuraTicks();
      for (GolemHandle handle : golems(player, alpha.getAuraRadius(alphaLevel))) {
        MobRef golem = handle.entity();
        now.put(golem.uuid(), golem);
        AdaptApi.scheduler().runEntity(golem, () -> {
          damage.update(golem, level, amount);
          health.update(golem, level, amount);
          TamePackBuff.apply(this, golem, amplifier, ticks, true, true);
        });
      }
    }
    Map<UUID, MobRef> before = now.isEmpty() ? buffed.remove(player.uuid()) : buffed.put(player.uuid(), now);
    if (before != null) {
      for (Map.Entry<UUID, MobRef> entry : before.entrySet()) {
        if (!now.containsKey(entry.getKey())) {
          strip(entry.getValue());
        }
      }
    }
  }

  private void strip(MobRef golem) {
    AdaptApi.scheduler().runEntity(golem, () -> {
      damage.update(golem, 0, 0D);
      health.update(golem, 0, 0D);
    });
  }

  /** Moves a share of a blow, shot or blast you take, after armour, onto your golems; at most Blood Bond's share. */
  @Handle(priority = EventPriority.HIGH)
  public void on(EntityDamageEvent e) {
    if (e.isCancelled() || !(e.entity() instanceof PlayerRef owner) || !SHARED.contains(e.cause())) {
      return;
    }
    int level = getActiveLevel(owner);
    BloodBond bond = level > 0 ? PairParts.owned(Id.adapt("taming/upgrade/blood_bond"), BloodBond.class) : null;
    int bondLevel = bond == null ? 0 : bond.getActiveLevel(owner);
    double landing = bondLevel > 0 ? e.finalDamage() : 0D;
    if (landing <= getConfig().minimumShared) {
      return;
    }
    List<LivingRef> sharers = new ArrayList<>();
    for (GolemHandle handle : golems(owner, bond.getShareRadius())) {
      MobRef golem = handle.entity();
      if (golem.health() > golem.maxHealth() * getConfig().golemHealthFloor) {
        sharers.add(golem);
        if (sharers.size() >= getConfig().maxSharers) {
          break;
        }
      }
    }
    if (sharers.isEmpty()) {
      return;
    }
    double moved = landing * Math.min(share(level), bond.getSharePercent(bondLevel));
    double each = moved / sharers.size();
    e.setDamage(e.damage() * ((landing - moved) / landing));
    for (LivingRef golem : sharers) {
      AdaptApi.scheduler().runEntity(golem, () -> golem.damage(each, GENERIC));
    }
  }

  @ReceiveCancelledEvents
  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerInteractEvent e) {
    if (!e.protectionProbe() && e.hand() == Hand.MAIN_HAND && e.action().isRightClick() && whistles(e.player())) {
      whistle(e.player(), null);
    }
  }

  @ReceiveCancelledEvents
  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerInteractEntityEvent e) {
    if (!e.protectionProbe() && e.hand() == Hand.MAIN_HAND && e.clicked() instanceof LivingRef clicked
        && whistles(e.player())) {
      whistle(e.player(), clicked);
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    Map<UUID, MobRef> golems = buffed.remove(e.player().uuid());
    if (golems != null) {
      golems.values().forEach(this::strip);
    }
  }

  private boolean whistles(PlayerRef player) {
    if (!AdaptApi.content().item(WHISTLE).map(whistle -> whistle.is(player.inventory().mainHand())).orElse(false)
        || getActiveLevel(player) <= 0) {
      return false;
    }
    OneMind mind = PairParts.owned(Id.adapt("taming/upgrade/one_mind"), OneMind.class);
    return mind != null && mind.getActiveLevel(player) > 0;
  }

  private void whistle(PlayerRef player, @Nullable LivingRef clicked) {
    if (player.isSneaking()) {
      fire(player, null, player.location(), false);
      return;
    }
    LivingRef target = clicked != null ? clicked : HeartseekerGuidance.lockCandidate(player, whistleRange(getActiveLevel(player)));
    if (target != null && !(target instanceof PlayerRef) && target.tameOwner() == null
        && GolemancyRuntime.handle(target).isEmpty() && canPVE(player, target)) {
      fire(player, target, null, false);
    }
  }

  @Override
  public Outcome run(BlockUse use) {
    PlayerRef player = use.player();
    LivingRef target = use.target();
    List<GolemHandle> golems = golems(player, whistleRange(use.level()));
    if (golems.isEmpty()) {
      return Outcome.MISSED;
    }
    long until = System.currentTimeMillis() + getConfig().commandMillis;
    Loc at = player.location();
    for (GolemHandle handle : golems) {
      MobRef golem = handle.entity();
      if (target == null) {
        AdaptApi.scheduler().runEntity(golem, () -> GolemancyRuntime.brain(golem).ifPresent(GolemBrain::clearCommand));
        GolemTeleport.teleport(handle, at);
      } else if (GolemCombat.enabled(handle)) {
        AdaptApi.scheduler().runEntity(golem, () -> GolemancyRuntime.brain(golem).ifPresent((GolemBrain brain) -> brain.command(target, until)));
      }
    }
    fx(target != null ? target.location().add(0, target.height(), 0) : at, FxPriority.GAMEPLAY).burst(Particles.CRIT, 8, 0.4D);
    return Outcome.INSTANT;
  }

  /** The player's awake golems within {@code radius}. */
  private static List<GolemHandle> golems(PlayerRef player, double radius) {
    Optional<GolemRegistry> registry = GolemancyRuntime.registry();
    if (registry.isEmpty()) {
      return List.of();
    }
    Loc at = player.location();
    List<GolemHandle> near = new ArrayList<>();
    for (GolemHandle handle : registry.get().loadedOf(player.uuid())) {
      MobRef golem = handle.entity();
      if (!handle.isRetired() && !handle.isDormant() && golem != null && golem.isValid() && !golem.isDead()
          && golem.location().sameWorld(at) && golem.location().distanceSquared(at) <= radius * radius) {
        near.add(handle);
      }
    }
    return near;
  }

  double buff(int level) {
    return SignatureKit.scaled(level, getMaxLevel(), getConfig().buffAtFirstLevel, getConfig().buffAtTopLevel);
  }

  double share(int level) {
    return SignatureKit.scaled(level, getMaxLevel(), getConfig().shareAtFirstLevel, getConfig().shareAtTopLevel);
  }

  double whistleRange(int level) {
    return SignatureKit.scaled(level, getMaxLevel(), getConfig().whistleRangeAtFirstLevel, getConfig().whistleRangeAtTopLevel);
  }

  @ConfigDescription("Your golems count as pets for Alpha, Blood Bond and the Beastmaster's Whistle.")
  public static class Config extends Signature.Config {
    @ConfigDoc(value = "Golem damage and health bonus inside Alpha's aura at level 1. 0.15 is 15%.", impact = "Higher values make golems in the pack stronger.")
    double buffAtFirstLevel = 0.15;
    @ConfigDoc(value = "Golem damage and health bonus inside Alpha's aura at the top level.", impact = "Higher values make golems in the pack stronger.")
    double buffAtTopLevel = 0.35;
    @ConfigDoc(value = "Share of each hit you take moved to your golems at level 1, with Blood Bond, at most Blood Bond's own share.", impact = "Higher values move more of each hit onto golems.")
    double shareAtFirstLevel = 0.1;
    @ConfigDoc(value = "Share of each hit you take moved to your golems at the top level, with Blood Bond, at most Blood Bond's own share.", impact = "Higher values move more of each hit onto golems.")
    double shareAtTopLevel = 0.2;
    @ConfigDoc(value = "Most golems that share one hit.", impact = "Higher values spread each hit thinner.")
    int maxSharers = 2;
    @ConfigDoc(value = "Share of its maximum health a golem keeps before it stops sharing. 0.25 is 25%.", impact = "Higher values keep golems healthier.")
    double golemHealthFloor = 0.25;
    @ConfigDoc(value = "Smallest hit that is shared.", impact = "Higher values ignore chip damage.")
    double minimumShared = 1;
    @ConfigDoc(value = "Reach of the whistle's command and recall at level 1, in blocks.", impact = "Higher values command golems farther away.")
    double whistleRangeAtFirstLevel = 16;
    @ConfigDoc(value = "Reach of the whistle's command and recall at the top level, in blocks.", impact = "Higher values command golems farther away.")
    double whistleRangeAtTopLevel = 32;
    @ConfigDoc(value = "How long a whistled golem attacks before it returns to its work, in milliseconds.", impact = "Higher values keep golems on the target longer.")
    long commandMillis = 15000;
    @ConfigDoc(value = "Cooldown between two whistle commands to golems, in milliseconds.", impact = "Higher values space commands further apart.")
    long cooldownMillis = 2000;
  }
}
