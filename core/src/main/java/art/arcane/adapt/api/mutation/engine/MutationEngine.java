package art.arcane.adapt.api.mutation.engine;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.BonusBlockBreaks;
import art.arcane.adapt.api.golem.item.GolemItemKind;
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.api.mutation.MutatedSkill;
import art.arcane.adapt.api.mutation.SkillMutation;
import art.arcane.adapt.api.mutation.blocks.BarterGesture;
import art.arcane.adapt.api.mutation.blocks.BlockRef;
import art.arcane.adapt.api.mutation.blocks.BlockSlot;
import art.arcane.adapt.api.mutation.blocks.MutatedAdaptation;
import art.arcane.adapt.api.mutation.blocks.MutationOverride;
import art.arcane.adapt.api.mutation.blocks.MutationOverrides;
import art.arcane.adapt.api.mutation.blocks.MutationRecipes;
import art.arcane.adapt.api.mutation.blocks.Recipe;
import art.arcane.adapt.api.mutation.blocks.SkillBlockCatalog;
import art.arcane.adapt.api.mutation.blocks.SkillBlocks;
import art.arcane.adapt.api.mutation.blocks.StrikeForm;
import art.arcane.adapt.api.mutation.signature.Signature;
import art.arcane.adapt.api.mutation.sound.LingeringSound;
import art.arcane.adapt.api.mutation.sound.MutationCue;
import art.arcane.adapt.api.mutation.sound.SoundPalettes;
import art.arcane.adapt.api.mutation.sound.SoundStage;
import art.arcane.adapt.api.mutation.upgrade.SkillUpgrades;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.tick.TickedObject;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.AdaptServer;
import art.arcane.adapt.api.world.PlayerData;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerItemHeldEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.event.player.PlayerSwapHandItemsEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSneakEvent;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.util.common.scheduling.Tasks;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runs the five recipes for every mutation from its blocks. Graft, Echo and Convergence run when a Trigger they use
 * fires; Barter runs on its pair's {@link BarterGesture}; Attunement runs on this object's one-second tick. A fight
 * Trigger uses the fight Strike, a work Trigger the work Strike, and an either Trigger the fight Strike only while a
 * hostile is within the hostile radius. A Strike that pays out is skipped when the action that set it off was farmed,
 * a Trigger never runs a Boon (only Barter, paid by its Fuel, and Attunement grant Boons), and nothing a block does
 * while it runs can set off another Trigger of the same player. A recipe a signature replaces runs that signature
 * instead: from its own handlers through {@link #signature}, or here on Barter's gesture and Attunement's interval.
 */
public final class MutationEngine extends TickedObject {
  private final Map<UUID, Map<String, Long>> readyAt = new ConcurrentHashMap<>();
  private final Map<UUID, Long> lastSneakPress = new ConcurrentHashMap<>();
  private final Set<UUID> running = ConcurrentHashMap.newKeySet();

  public MutationEngine() {
    super("mutations", UUID.randomUUID() + "-mutations", 1000);
  }

  public static MutationEngine current() {
    return AdaptCore.get().getAdaptServer().getSkillRegistry().getMutationEngine();
  }

  /** A block's level for a player: the highest level among the held mutated adaptations that use it. */
  public static int blockLevel(MutationBlock<?> block, PlayerData data) {
    if (data == null || data.getMutations().isEmpty()) {
      return 0;
    }
    SkillRegistry registry = block.getServer().getSkillRegistry();
    int level = 0;
    for (SkillMutation mutation : data.getMutations()) {
      if (!mutation.involves(block.getSkillId())) {
        continue;
      }
      MutatedSkill skill = registry.getMutatedSkill(mutation.id());
      PlayerSkillLine line = data.getSkillLineNullable(mutation.id());
      if (skill == null || line == null) {
        continue;
      }
      for (MutationAdaptation adaptation : skill.getMutationAdaptations()) {
        if (adaptation.uses(block) && adaptation.isEnabled()) {
          level = Math.max(level, Math.min(line.getAdaptationLevel(adaptation.getName()), adaptation.getMaxLevel()));
        }
      }
    }
    return level;
  }

  /**
   * Ids of every block used by a mutated adaptation the player has learned, read from the recipes so it does not
   * depend on which skills are registered. The server indexes it as the blocks' learners.
   */
  public static Set<String> heldBlocks(PlayerData data) {
    List<SkillMutation> mutations = data == null ? List.of() : data.getMutations();
    if (mutations.isEmpty()) {
      return Set.of();
    }
    Set<String> held = new HashSet<>();
    for (SkillMutation mutation : mutations) {
      PlayerSkillLine line = data.getSkillLineNullable(mutation.id());
      SkillBlocks lead = SkillBlockCatalog.byId(mutation.lead()).orElse(null);
      SkillBlocks partner = SkillBlockCatalog.byId(mutation.partner()).orElse(null);
      if (line == null || line.getAdaptations().isEmpty() || lead == null || partner == null) {
        continue;
      }
      for (MutatedAdaptation recipe : MutationRecipes.compose(lead, partner)) {
        if (line.getAdaptationLevel(MutationAdaptation.id(mutation, recipe.recipe())) > 0) {
          for (BlockRef ref : recipe.blocks()) {
            held.add(MutationBlocks.id(ref.skillId(), ref.slot()));
          }
        }
      }
    }
    return Set.copyOf(held);
  }

  /** Ids of the signatures of every mutated adaptation the player has learned; indexed as their learners. */
  public static Set<String> heldSignatures(PlayerData data) {
    List<SkillMutation> mutations = data == null ? List.of() : data.getMutations();
    if (mutations.isEmpty()) {
      return Set.of();
    }
    Set<String> held = new HashSet<>();
    for (SkillMutation mutation : mutations) {
      PlayerSkillLine line = data.getSkillLineNullable(mutation.id());
      if (line == null || line.getAdaptations().isEmpty()) {
        continue;
      }
      for (Recipe recipe : Recipe.values()) {
        MutationOverride row = MutationOverrides.find(mutation.lead(), mutation.partner(), recipe);
        if (row != null && row.isSignature() && line.getAdaptationLevel(MutationAdaptation.id(mutation, recipe)) > 0) {
          held.add(Signature.id(row));
        }
      }
    }
    return Set.copyOf(held);
  }

  /**
   * Runs {@code signature} for the player when they hold the mutated adaptation it replaces and it is ready. An effect
   * that pays out does not run when {@code farmed} or its target is farmed. True when it landed.
   */
  public boolean signature(Signature<?> signature, PlayerRef player, @Nullable EntityRef target, @Nullable Loc at,
                           boolean farmed) {
    PlayerData data = data(player);
    if (data == null || running.contains(player.uuid()) || BonusBlockBreaks.isActive(player)
        || !data.getMutations().contains(signature.getMutation())) {
      return false;
    }
    MutationAdaptation adaptation = adaptation(signature.getMutation(), signature.getRecipe());
    if (adaptation == null || adaptation.getSignature() != signature) {
      return false;
    }
    return runSignature(adaptation, player, target, at, farmed);
  }

  private boolean runSignature(MutationAdaptation adaptation, PlayerRef player, @Nullable EntityRef target,
                               @Nullable Loc at, boolean farmed) {
    Signature<?> signature = adaptation.getSignature();
    int level = adaptation.getActiveLevel(player);
    if (signature == null || !signature.usable() || level <= 0 || !ready(player, adaptation)) {
      return false;
    }
    LivingRef living = target instanceof LivingRef candidate && candidate.isValid() && !candidate.isDead()
        && !candidate.uuid().equals(player.uuid()) ? candidate : null;
    if (signature.paysOut() && (farmed || (living != null && MutationPayouts.farmed(player, living)))) {
      return false;
    }
    String initiator = adaptation.getMutation().lead();
    BlockUse use = new BlockUse(player, adaptation, level, living, at, initiator);
    Outcome outcome;
    running.add(player.uuid());
    try {
      outcome = signature.dispatched(use);
    } finally {
      running.remove(player.uuid());
    }
    if (!outcome.landed()) {
      return false;
    }
    mark(player, adaptation, adaptation.getCooldownMillis(level));
    MutationCue cue = switch (adaptation.getRecipe()) {
      case BARTER, ATTUNEMENT -> MutationCue.BOON;
      case GRAFT, ECHO, CONVERGENCE -> living != null ? MutationCue.FIGHT : MutationCue.WORK;
    };
    sound(adaptation, player, initiator, cue);
    if (outcome.lasts()) {
      EntityRef entity = outcome.lingerOn();
      LingeringSound sound = SkillUpgrades.SOUNDS.linger(signature.getSoundPalette(), SoundStage.following(adaptation, entity));
      Tasks.runEntity(entity, sound::stop, outcome.lastingTicks());
    }
    return true;
  }

  void fire(TriggerBlock<?> trigger, TriggerEvent event) {
    PlayerRef player = event.player();
    PlayerData data = data(player);
    if (data == null || running.contains(player.uuid()) || BonusBlockBreaks.isActive(player)) {
      return;
    }
    for (SkillMutation mutation : data.getMutations()) {
      MutatedSkill skill = mutation.involves(trigger.getSkillId()) ? registry().getMutatedSkill(mutation.id()) : null;
      if (skill == null) {
        continue;
      }
      for (MutationAdaptation adaptation : skill.getMutationAdaptations()) {
        Recipe recipe = adaptation.getRecipe();
        if ((recipe == Recipe.GRAFT || recipe == Recipe.ECHO || recipe == Recipe.CONVERGENCE) && adaptation.uses(trigger)) {
          runTriggered(adaptation, trigger, event);
        }
      }
    }
  }

  private void runTriggered(MutationAdaptation adaptation, TriggerBlock<?> trigger, TriggerEvent event) {
    PlayerRef player = event.player();
    int level = adaptation.getActiveLevel(player);
    if (level <= 0 || !allUsable(adaptation) || !ready(player, adaptation) || !statesHold(adaptation, player)) {
      return;
    }
    double radius = AdaptConfig.get().getMutations().getHostileRadius();
    StrikeForm form = adaptation.getRecipeData().form();
    LivingRef target = form == StrikeForm.WORK ? null : fightTarget(player, event, radius);
    BlockSlot slot = switch (form) {
      case FIGHT -> BlockSlot.STRIKE_FIGHT;
      case WORK -> BlockSlot.STRIKE_WORK;
      case EITHER, NONE -> target != null ? BlockSlot.STRIKE_FIGHT : BlockSlot.STRIKE_WORK;
    };
    if (slot == BlockSlot.STRIKE_FIGHT && target == null) {
      return;
    }
    Loc at = slot == BlockSlot.STRIKE_WORK ? (event.at() != null ? event.at() : player.location()) : null;
    BlockUse use = new BlockUse(player, adaptation, level, slot == BlockSlot.STRIKE_FIGHT ? target : null, at,
        trigger.getSkillId());
    boolean farmed = event.farmed() || (use.target() != null && MutationPayouts.farmed(player, use.target()));
    List<StrikeBlock<?>> strikes = payable(adaptation.strikes(slot), farmed);
    MutationCue cue = slot == BlockSlot.STRIKE_FIGHT ? MutationCue.FIGHT : MutationCue.WORK;
    run(use, strikes, List.of(), cue);
  }

  /** Casts every Barter of the player's mutations that uses this gesture; true when one ran. */
  boolean barter(PlayerRef player, BarterGesture gesture, long sneakGapMillis) {
    PlayerData data = data(player);
    if (data == null || running.contains(player.uuid()) || holdsCrook(player)) {
      return false;
    }
    boolean ran = false;
    for (SkillMutation mutation : data.getMutations()) {
      if (BarterGesture.of(mutation.lead(), mutation.partner()) != gesture) {
        continue;
      }
      MutationAdaptation adaptation = adaptation(mutation, Recipe.BARTER);
      if (adaptation == null || (gesture == BarterGesture.DOUBLE_SNEAK
          && sneakGapMillis > AdaptConfig.get().getMutations().getDoubleSneakWindowMillis())) {
        continue;
      }
      ran |= runPassive(player, adaptation);
    }
    return ran;
  }

  private static boolean holdsCrook(PlayerRef player) {
    return GolemItems.kindOf(player.inventory().mainHand())
        .filter((GolemItemKind kind) -> kind == GolemItemKind.CROOK).isPresent();
  }

  private void attune(PlayerRef player) {
    PlayerData data = data(player);
    if (data == null || running.contains(player.uuid())) {
      return;
    }
    for (SkillMutation mutation : data.getMutations()) {
      MutationAdaptation adaptation = adaptation(mutation, Recipe.ATTUNEMENT);
      if (adaptation != null && runPassive(player, adaptation)) {
        mark(player, adaptation, AdaptConfig.get().getMutations().getAttunementIntervalMillis());
      }
    }
  }

  private boolean runPassive(PlayerRef player, MutationAdaptation adaptation) {
    if (adaptation.getSignature() != null) {
      return runSignature(adaptation, player, null, player.location(), adaptation.getRecipe() == Recipe.ATTUNEMENT);
    }
    int level = adaptation.getActiveLevel(player);
    if (level <= 0 || !allUsable(adaptation) || !ready(player, adaptation) || !statesHold(adaptation, player)) {
      return false;
    }
    String initiator = initiator(adaptation);
    List<StrikeBlock<?>> strikes = payable(adaptation.strikes(BlockSlot.STRIKE_WORK), !paidFor(adaptation));
    LivingRef target = null;
    if (!adaptation.strikes(BlockSlot.STRIKE_FIGHT).isEmpty()) {
      target = nearestHostile(player, AdaptConfig.get().getMutations().getHostileRadius(), null);
      if (target != null) {
        strikes.addAll(payable(adaptation.strikes(BlockSlot.STRIKE_FIGHT), MutationPayouts.farmed(player, target)));
      }
    }
    BlockUse use = new BlockUse(player, adaptation, level, target, player.location(), initiator);
    return run(use, strikes, boons(adaptation), MutationCue.BOON);
  }

  private boolean run(BlockUse use, List<StrikeBlock<?>> strikes, List<BoonBlock<?>> boons, MutationCue cue) {
    if (strikes.isEmpty() && boons.isEmpty()) {
      return false;
    }
    MutationAdaptation adaptation = use.adaptation();
    List<FuelBlock<?>> fuels = new ArrayList<>();
    for (MutationBlock<?> block : adaptation.getBlocks()) {
      if (block instanceof FuelBlock<?> fuel) {
        if (!fuel.canPay(use)) {
          return false;
        }
        fuels.add(fuel);
      }
    }
    PlayerRef player = use.player();
    List<Lasting> lasting = new ArrayList<>();
    boolean landed = false;
    running.add(player.uuid());
    try {
      for (FuelBlock<?> fuel : fuels) {
        if (!fuel.pay(use)) {
          return false;
        }
      }
      for (StrikeBlock<?> strike : strikes) {
        LivingRef target = strike.getSlot() == BlockSlot.STRIKE_FIGHT ? use.target() : null;
        Loc at = strike.getSlot() == BlockSlot.STRIKE_WORK ? use.center() : null;
        Outcome outcome = strike.strike(new BlockUse(player, adaptation, use.level(), target, at, use.initiator()));
        landed |= outcome.landed();
        if (outcome.lasts()) {
          lasting.add(new Lasting(strike, outcome));
        }
      }
      for (BoonBlock<?> boon : boons) {
        Outcome outcome = boon.grant(use);
        landed |= outcome.landed();
        if (outcome.lasts()) {
          lasting.add(new Lasting(boon, outcome));
        }
      }
    } finally {
      running.remove(player.uuid());
    }
    if (!landed && fuels.isEmpty()) {
      return false;
    }
    mark(player, adaptation, adaptation.getCooldownMillis(use.level()));
    sound(adaptation, player, use.initiator(), cue);
    for (Lasting effect : lasting) {
      EntityRef entity = effect.outcome().lingerOn();
      LingeringSound sound = SkillUpgrades.SOUNDS.linger(effect.block().getSoundPalette(),
          SoundStage.following(adaptation, entity));
      Tasks.runEntity(entity, sound::stop, effect.outcome().lastingTicks());
    }
    return true;
  }

  private record Lasting(MutationBlock<?> block, Outcome outcome) {
  }

  private void sound(MutationAdaptation adaptation, PlayerRef player, String initiator, MutationCue cue) {
    SkillMutation mutation = adaptation.getMutation();
    SoundStage stage = SoundStage.following(adaptation, player);
    if (adaptation.getRecipe() == Recipe.CONVERGENCE) {
      SkillUpgrades.SOUNDS.convergence(player.uuid(), adaptation.getName(), SoundPalettes.of(mutation.lead()),
          SoundPalettes.of(mutation.partner()), stage);
      return;
    }
    String responder = initiator.equals(mutation.lead()) ? mutation.partner() : mutation.lead();
    SkillUpgrades.SOUNDS.cue(player.uuid(), adaptation.getName(), SoundPalettes.of(initiator),
        SoundPalettes.of(responder), cue, stage);
  }

  /**
   * Whether a passive run is paid for by a Fuel. Barter is; Attunement costs nothing, so it counts as a farmed action and
   * runs no Strike that pays out.
   */
  private static boolean paidFor(MutationAdaptation adaptation) {
    for (MutationBlock<?> block : adaptation.getBlocks()) {
      if (block instanceof FuelBlock<?>) {
        return true;
      }
    }
    return false;
  }

  /** The skill whose Fuel (Barter) or State (Attunement) starts a passive recipe. */
  private static String initiator(MutationAdaptation adaptation) {
    for (MutationBlock<?> block : adaptation.getBlocks()) {
      if (block instanceof FuelBlock<?> || block instanceof StateBlock<?>) {
        return block.getSkillId();
      }
    }
    return adaptation.getMutation().lead();
  }

  /** The strikes that may run: all of them, or only those that pay nothing when the action or target is farmed. */
  private static List<StrikeBlock<?>> payable(List<StrikeBlock<?>> strikes, boolean farmed) {
    List<StrikeBlock<?>> payable = new ArrayList<>();
    for (StrikeBlock<?> strike : strikes) {
      if (!farmed || !strike.paysOut()) {
        payable.add(strike);
      }
    }
    return payable;
  }

  private static List<BoonBlock<?>> boons(MutationAdaptation adaptation) {
    List<BoonBlock<?>> boons = new ArrayList<>();
    for (MutationBlock<?> block : adaptation.getBlocks()) {
      if (block instanceof BoonBlock<?> boon) {
        boons.add(boon);
      }
    }
    return boons;
  }

  private static boolean allUsable(MutationAdaptation adaptation) {
    for (MutationBlock<?> block : adaptation.getBlocks()) {
      if (!block.usable()) {
        return false;
      }
    }
    return true;
  }

  private static boolean statesHold(MutationAdaptation adaptation, PlayerRef player) {
    long windowMillis = adaptation.getRecipeData().stateWindowSeconds() * 1000L;
    for (MutationBlock<?> block : adaptation.getBlocks()) {
      if (block instanceof StateBlock<?> state
          && !(windowMillis > 0L ? state.heldWithin(player, windowMillis) : state.holds(player))) {
        return false;
      }
    }
    return true;
  }

  /** The Trigger's living target unless it died from the action, else the nearest other hostile in the radius. */
  private static @Nullable LivingRef fightTarget(PlayerRef player, TriggerEvent event, double radius) {
    EntityRef target = event.target();
    if (!event.slain() && target instanceof LivingRef living && living.isValid() && !living.isDead()
        && !living.uuid().equals(player.uuid())) {
      return living;
    }
    return nearestHostile(player, radius, target == null ? null : target.uuid());
  }

  /** The nearest living monster within {@code radius} of the player, not a player, not their pet, not {@code skip}. */
  public static @Nullable LivingRef nearestHostile(PlayerRef player, double radius, @Nullable UUID skip) {
    Loc center = player.location();
    double limit = radius * radius;
    LivingRef nearest = null;
    double nearestDistance = Double.MAX_VALUE;
    for (EntityRef entity : player.nearbyEntities(radius, radius, radius)) {
      if (!(entity instanceof LivingRef living) || entity instanceof PlayerRef || !living.isValid() || living.isDead()
          || living.uuid().equals(skip) || player.uuid().equals(living.tameOwner())
          || !(living.has(EntityTrait.MONSTER) || living.has(EntityTrait.ENEMY))) {
        continue;
      }
      double distance = living.location().distanceSquared(center);
      if (distance <= limit && distance < nearestDistance) {
        nearest = living;
        nearestDistance = distance;
      }
    }
    return nearest;
  }

  private static @Nullable MutationAdaptation adaptation(SkillMutation mutation, Recipe recipe) {
    MutatedSkill skill = registry().getMutatedSkill(mutation.id());
    return skill == null ? null : skill.getMutationAdaptation(recipe);
  }

  private static SkillRegistry registry() {
    return AdaptCore.get().getAdaptServer().getSkillRegistry();
  }

  private static @Nullable PlayerData data(PlayerRef player) {
    AdaptServer server = AdaptCore.get().getAdaptServer();
    AdaptPlayer adaptPlayer = server == null ? null : server.getOnlineAdaptPlayer(player.uuid());
    return adaptPlayer == null || !adaptPlayer.isRuntimeReady() ? null : adaptPlayer.getData();
  }

  private boolean ready(PlayerRef player, MutationAdaptation adaptation) {
    Map<String, Long> times = readyAt.get(player.uuid());
    Long until = times == null ? null : times.get(adaptation.getName());
    return until == null || System.currentTimeMillis() >= until;
  }

  private void mark(PlayerRef player, MutationAdaptation adaptation, long millis) {
    if (millis > 0L) {
      readyAt.computeIfAbsent(player.uuid(), (UUID ignored) -> new ConcurrentHashMap<>())
          .put(adaptation.getName(), System.currentTimeMillis() + millis);
    }
  }

  @Handle(priority = EventPriority.HIGH)
  public void on(PlayerSwapHandItemsEvent e) {
    PlayerRef player = e.player();
    if (player.isSneaking() && barter(player, BarterGesture.SNEAK_SWAP, 0L)) {
      e.setCancelled(true);
    }
  }

  @Handle(priority = EventPriority.HIGH, ignoreCancelled = false)
  public void on(PlayerInteractEvent e) {
    PlayerRef player = e.player();
    if (e.action() == InteractAction.LEFT_CLICK_AIR && e.hand() != Hand.OFF_HAND && player.isSneaking()
        && barter(player, BarterGesture.SNEAK_LEFT_CLICK, 0L)) {
      e.setUseItem(UseResult.DENY);
    }
  }

  @Handle(priority = EventPriority.HIGH)
  public void on(PlayerItemHeldEvent e) {
    PlayerRef player = e.player();
    if (player.isSneaking() && barter(player, BarterGesture.SNEAK_SCROLL, 0L)) {
      e.setCancelled(true);
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerToggleSneakEvent e) {
    if (!e.isSneaking()) {
      return;
    }
    PlayerRef player = e.player();
    long now = System.currentTimeMillis();
    Long previous = lastSneakPress.put(player.uuid(), now);
    if (previous != null && barter(player, BarterGesture.DOUBLE_SNEAK, now - previous)) {
      lastSneakPress.remove(player.uuid());
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    UUID id = e.player().uuid();
    readyAt.remove(id);
    lastSneakPress.remove(id);
    running.remove(id);
  }

  @Override
  public void onTick() {
    AdaptServer server = AdaptCore.get().getAdaptServer();
    if (server == null) {
      return;
    }
    for (UUID holder : server.getOnlineMutationHolders()) {
      AdaptPlayer adaptPlayer = server.getOnlineAdaptPlayer(holder);
      PlayerRef player = adaptPlayer == null ? null : adaptPlayer.getPlayer();
      if (player != null && !adaptPlayer.getData().getMutations().isEmpty()) {
        Tasks.runEntity(player, () -> attune(player));
      }
    }
  }
}
