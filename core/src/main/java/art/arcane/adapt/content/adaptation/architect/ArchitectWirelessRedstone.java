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

package art.arcane.adapt.content.adaptation.architect;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.ItemCooldowns;
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
import art.arcane.adapt.api.protection.ProtectionProbes;
import art.arcane.adapt.api.runtime.AdaptationGate;
import art.arcane.adapt.content.item.BoundRedstoneTorch;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.ArchitectMessages;
import art.arcane.adapt.platform.entity.GameMode;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockPlaceEvent;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.event.world.ChunkEvent;
import art.arcane.adapt.platform.fx.SoundCategory;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.recipe.Ingredient;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class ArchitectWirelessRedstone extends SimpleAdaptation<ArchitectWirelessRedstone.Config> {
  public static final PlayerPreference<CommonPreferences.Toggle> SNEAK = CommonPreferences.toggle("sneak", ArchitectMessages.ARCHITECTWIRELESSREDSTONE_PREFERENCE_SNEAK, CommonPreferences.Toggle.OFF);

  private static final int MAX_SCHEDULE_ATTEMPTS = 3;
  private static final long SHUTDOWN_RESTORATION_TIMEOUT_MILLIS = 3000L;
  private static final Map<ArchitectRedstonePulse.Emitter, Long> SHUTDOWN_RESTORATION_OWNERS =
      new ConcurrentHashMap<>();
  private static final AtomicLong NEXT_RUNTIME_GENERATION = new AtomicLong();
  private static final BlockFace[] PULSE_NEIGHBOURS = {
      BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST
  };
  private static final Id REDSTONE_TORCH = Id.minecraft("redstone_torch");
  private static final Id NOTE_BLOCK = Id.minecraft("note_block");
  private static final String LIT = "lit";
  private static final String POWER = "power";
  private static final String OPEN = "open";
  private static final String POWERED = "powered";
  /** Every vanilla analogue power property runs from 0 to 15. */
  private static final int MAX_POWER = 15;

  /**
   * The remote rides on a redstone torch, so its pulse cooldown lives in the
   * bound torch's own group. A remote on cooldown must never gray out or block
   * placing ordinary redstone torches.
   */
  private final ItemCooldowns pulseCd = ItemCooldowns.forGroup(BoundRedstoneTorch.COOLDOWN_GROUP);
  private final ArchitectRedstonePulse pulses = new ArchitectRedstonePulse();
  private final Set<UUID> pendingPulsePlayers = ConcurrentHashMap.newKeySet();
  private final long runtimeGeneration = NEXT_RUNTIME_GENERATION.incrementAndGet();

  public ArchitectWirelessRedstone() {
    super("architect-wireless-redstone");
    registerConfiguration(ArchitectWirelessRedstone.Config.class);
    setIcon(REDSTONE_TORCH);
    registerRecipe(new RecipeSpec.Shapeless(Id.adapt("remote-redstone-torch"),
        List.of(Ingredient.of(REDSTONE_TORCH), Ingredient.of(Id.minecraft("target")), Ingredient.of(Id.minecraft("ender_pearl"))),
        BoundRedstoneTorch.io.withData(new BoundRedstoneTorch.Data(null, null))));
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("redstone"))
        .key("challenge_architect_wireless_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("redstone"))
            .key("challenge_architect_wireless_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_architect_wireless_100", "architect.wireless-redstone.pulses", 100, 300);
    registerMilestone("challenge_architect_wireless_5k", "architect.wireless-redstone.pulses", 5000, 1000);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, SNEAK);
  }

  @Override
  public void addStats(int level, StatLines element) {
    element.addLore(C.GREEN + AdaptLanguage.text(ArchitectMessages.WIRELESS_REDSTONE_LORE1));
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void onPlaceBlock(BlockPlaceEvent event) {
    ItemRef item = event.itemInHand();
    if (BoundRedstoneTorch.hasItemData(item) && isRedstoneTorch(item)) {
      event.setBuild(false);
      event.setCancelled(true);
    }
  }

  @ReceiveCancelledEvents
  @Handle(priority = EventPriority.MONITOR)
  public void onPlayerInteract(PlayerInteractEvent event) {
    if (event.hand() != Hand.MAIN_HAND && event.hand() != Hand.OFF_HAND) {
      return;
    }

    ItemRef itemInHand = event.item();
    if (itemInHand == null || !isRedstoneTorch(itemInHand)
        || !BoundRedstoneTorch.hasItemData(itemInHand)) {
      return;
    }

    boolean vetoed = event.isUseDenied();

    PlayerRef player = event.player();
    withPlayerThread(player, () -> {
      if (resolveInteractContext(player, player.location()) == null) {
        return;
      }

      boolean canUseInCreative = AdaptConfig.get().allowAdaptationsInCreative;
      boolean inCreative = player.gameMode() == GameMode.CREATIVE;
      if (inCreative && !canUseInCreative) {
        return;
      }

      if (vetoed) {
        return;
      }

      if (BoundRedstoneTorch.io.ensureCooldownGroup(itemInHand)) {
        player.inventory().setHand(event.hand(), itemInHand);
      }

      switch (event.action()) {
        case LEFT_CLICK_BLOCK, LEFT_CLICK_AIR -> handleLeftClick(event, player);
        case RIGHT_CLICK_AIR, RIGHT_CLICK_BLOCK -> handleRightClick(event, player);
        default -> {
        }
      }
    });
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void onChunkUnload(ChunkEvent event) {
    if (event.phase() != ChunkEvent.Phase.UNLOAD) {
      return;
    }
    WorldRef world = event.world();
    UUID worldId = world.uid();
    int chunkX = event.chunk().x();
    int chunkZ = event.chunk().z();
    for (ArchitectRedstonePulse.Emitter emitter : pulses.emitters()) {
      if (!emitter.isInChunk(worldId, chunkX, chunkZ)) {
        continue;
      }
      ArchitectRedstonePulse.Restoration restoration = pulses.cancel(emitter);
      if (restoration != null) {
        restoreEmitter(world, restoration);
      }
    }
  }

  private boolean isRedstoneTorch(ItemRef item) {
    return REDSTONE_TORCH.equals(item.type());
  }

  private void handleLeftClick(PlayerInteractEvent event, PlayerRef player) {
    if (!player.isSneaking() || event.hand() != Hand.MAIN_HAND) {
      return;
    }

    if (event.action() == InteractAction.LEFT_CLICK_BLOCK) {
      event.setUseItem(UseResult.DENY);
      event.setUseBlock(UseResult.DENY);
    }

    BindingRequest binding = createBindingRequest(event, player);
    if (binding == null) {
      showPulseFailure(player);
      return;
    }

    if (!Tasks.runAt(binding.target(), () -> validateBindingTarget(player, binding))) {
      IllegalStateException failure = new IllegalStateException(
          "Failed to schedule Architect Redstone Remote binding target validation at "
              + binding.target());
      AdaptLog.error(failure);
      showPulseFailure(player);
    }
  }

  private BindingRequest createBindingRequest(PlayerInteractEvent event, PlayerRef player) {
    BlockRef target = event.clickedBlock();
    BlockFace face = event.clickedFace();
    if (target == null || !ArchitectRedstonePulse.isBindableFace(face)) {
      return null;
    }

    Loc targetLocation = target.location();
    WorldRef world = targetLocation.world();
    if (!isValidHeight(world, target.y())) {
      return null;
    }

    int handSlot = player.inventory().heldSlot();
    ItemRef handSnapshot = player.inventory().mainHand().copy();
    return new BindingRequest(targetLocation, face, handSlot, handSnapshot);
  }

  private void validateBindingTarget(PlayerRef player, BindingRequest binding) {
    if (!isRuntimeRegistered() || AdaptationGate.isWorldBlacklisted(binding.target().world())
        || binding.target().world().blockAt(binding.target()).type().isAir()) {
      showPulseFailure(player);
      return;
    }

    if (!Tasks.runEntity(player, () -> authorizeAndLinkTorch(player, binding))) {
      IllegalStateException failure = new IllegalStateException(
          "Failed to schedule Architect Redstone Remote binding authorization for "
              + player.uuid());
      AdaptLog.error(failure);
      showPulseFailure(player);
    }
  }

  private void authorizeAndLinkTorch(PlayerRef player, BindingRequest binding) {
    ItemRef hand = player.inventory().mainHand();
    boolean canUseInCreative = AdaptConfig.get().allowAdaptationsInCreative;
    if (!isRuntimeRegistered() || !player.isOnline() || !player.isSneaking()
        || (player.gameMode() == GameMode.CREATIVE && !canUseInCreative)
        || player.inventory().heldSlot() != binding.handSlot()
        || !sameStack(hand, binding.handSnapshot())
        || resolveInteractContext(player, binding.target()) == null
        || !canBlockPlace(player, binding.target())) {
      showPulseFailure(player);
      return;
    }

    linkTorch(player, binding);
  }

  private static boolean sameStack(ItemRef first, ItemRef second) {
    return first.amount() == second.amount() && first.isSimilar(second);
  }

  private void linkTorch(PlayerRef player, BindingRequest binding) {
    Loc targetLocation = binding.target();
    ItemRef hand = player.inventory().mainHand();
    AtomicBoolean defaultConsumed = new AtomicBoolean();
    boolean paymentAllowed = payItemCost(player, "bind", AdaptCore.platform().host().items().create(hand.type(), 1), 1, () -> {
      createBoundTorch(player, hand, binding, true);
      defaultConsumed.set(true);
      return true;
    });
    if (!paymentAllowed) {
      showPulseFailure(player);
      return;
    }

    if (shouldCreateProviderBoundOutput(paymentAllowed, defaultConsumed.get())) {
      createBoundTorch(player, hand, binding, false);
    }

    Loc targetCenter = targetLocation.add(0.5D, 0.5D, 0.5D);
    fx(player.eyeLocation(), FxPriority.GAMEPLAY)
        .line(Particles.ELECTRIC_SPARK, targetCenter.x(), targetCenter.y(), targetCenter.z(), 16);
    fx(targetCenter, FxPriority.GAMEPLAY)
        .dustRing(new Rgb(255, 40, 40), 0.6D, 16, 1.0F)
        .chord(Sounds.ENTITY_ENDER_EYE_DEATH, 0.2F, 0.48F,
            Sounds.BLOCK_BEACON_POWER_SELECT, 0.4F, 1.6F);
  }

  private void createBoundTorch(
      PlayerRef player,
      ItemRef hand,
      BindingRequest binding,
      boolean consumeDefault
  ) {
    if (consumeDefault && hand.amount() == 1) {
      BoundRedstoneTorch.setData(hand, binding.target(), binding.face());
      player.inventory().setMainHand(hand);
      return;
    }

    if (consumeDefault) {
      hand.setAmount(hand.amount() - 1);
      player.inventory().setMainHand(hand);
    }
    ItemRef torch = BoundRedstoneTorch.withData(binding.target(), binding.face());
    player.inventory().addItem(torch)
        .forEach(item -> player.world().dropItemNaturally(player.location().position(), item));
  }

  static boolean shouldCreateProviderBoundOutput(boolean paymentAllowed, boolean defaultConsumed) {
    return paymentAllowed && !defaultConsumed;
  }

  private void handleRightClick(PlayerInteractEvent event, PlayerRef player) {
    if (preferenceEnabled(player, SNEAK) && !player.isSneaking()) {
      return;
    }
    if (event.action() == InteractAction.RIGHT_CLICK_BLOCK) {
      event.setUseItem(UseResult.DENY);
      event.setUseBlock(UseResult.DENY);
    }

    UUID playerId = player.uuid();
    if (hasCooldown(player) || !pendingPulsePlayers.add(playerId)) {
      fx(player.eyeLocation(), FxPriority.TRANSITION)
          .burst(Particles.SMOKE, 1, 0.05D)
          .sound(Sounds.BLOCK_REDSTONE_TORCH_BURNOUT, 0.1F, 0.9F);
      return;
    }

    try {
      triggerPulse(player, event.item());
    } catch (RuntimeException | Error error) {
      rejectPulse(player);
      throw error;
    }
  }

  private boolean hasCooldown(PlayerRef player) {
    return !pulseCd.isReady(player, getConfig().cooldown);
  }

  private void triggerPulse(PlayerRef player, ItemRef item) {
    PulseBinding binding = resolveBinding(BoundRedstoneTorch.getBinding(item));
    if (binding == null) {
      rejectPulse(player);
      return;
    }

    binding.target().world().loadChunkAsync(binding.target().chunkX(), binding.target().chunkZ())
        .whenComplete((loaded, error) -> {
          if (error != null) {
            AdaptLog.error("Failed to load an Architect Redstone Remote bound chunk at "
                + binding.target());
            AdaptLog.error(error);
            rejectPulse(player);
            return;
          }
          if (!Tasks.runAt(binding.target(), () -> validateTargetAndSchedulePulse(player, binding))) {
            IllegalStateException failure = new IllegalStateException(
                "Failed to schedule Architect Redstone Remote target validation at " + binding.target());
            AdaptLog.error(failure);
            rejectPulse(player);
          }
        });
  }

  private void validateTargetAndSchedulePulse(PlayerRef player, PulseBinding binding) {
    if (!isRuntimeRegistered()
        || (Tasks.isFoliaThreading()
        && (!Tasks.isOwnedByCurrentRegion(player)
        || !Tasks.isOwnedByCurrentRegion(binding.target(), 1.0D, 1.0D)))
        || binding.target().world().blockAt(binding.target()).type().isAir()) {
      rejectPulse(player);
      return;
    }

    if (!Tasks.runEntity(player, () -> authorizeAndSchedulePulse(player, binding))) {
      IllegalStateException failure = new IllegalStateException(
          "Failed to schedule Architect Redstone Remote authorization for " + player.uuid());
      AdaptLog.error(failure);
      rejectPulse(player);
    }
  }

  private void authorizeAndSchedulePulse(PlayerRef player, PulseBinding binding) {
    if (!isRuntimeRegistered() || !player.isOnline()
        || (Tasks.isFoliaThreading()
        && !Tasks.isOwnedByCurrentRegion(binding.target(), 1.0D, 1.0D))
        || resolveInteractContext(player, player.location()) == null
        || !canInteract(player, binding.target())
        || !canBlockPlace(player, binding.target())) {
      rejectPulse(player);
      return;
    }

    if (!Tasks.runAt(binding.target(), () -> beginPulse(player, binding))) {
      IllegalStateException failure = new IllegalStateException(
          "Failed to schedule Architect Redstone Remote pulse at " + binding.emitter());
      AdaptLog.error(failure);
      rejectPulse(player);
    }
  }

  private PulseBinding resolveBinding(BoundRedstoneTorch.Data data) {
    if (data == null || data.getLocation() == null || !isFinite(data.getLocation())
        || !data.getLocation().world().isLoaded()
        || !ArchitectRedstonePulse.isBindableFace(data.getFace())) {
      return null;
    }

    Loc stored = data.getLocation();
    WorldRef world = stored.world();
    int targetX = stored.blockX();
    int targetY = stored.blockY();
    int targetZ = stored.blockZ();
    Loc targetLocation = new Loc(world, targetX, targetY, targetZ);
    if (AdaptationGate.isWorldBlacklisted(world) || !isValidHeight(world, targetY)
        || !world.isInsideBorder(targetLocation.add(0.5D, 0.5D, 0.5D).position())) {
      return null;
    }

    ArchitectRedstonePulse.Emitter emitter = ArchitectRedstonePulse.emitter(
        world.uid(), targetX, targetY, targetZ);
    return emitter == null ? null : new PulseBinding(targetLocation, data.getFace(), emitter);
  }

  private static boolean isFinite(Loc location) {
    return Double.isFinite(location.x()) && Double.isFinite(location.y()) && Double.isFinite(location.z());
  }

  /**
   * Drives the bound block itself, or the redstone components touching it when
   * the block has no powered state of its own. No block is ever placed or
   * swapped; only block data flips for the pulse window.
   */
  private void beginPulse(PlayerRef player, PulseBinding binding) {
    if (!isRuntimeRegistered()
        || (Tasks.isFoliaThreading()
        && (!Tasks.isOwnedByCurrentRegion(player)
        || !Tasks.isOwnedByCurrentRegion(binding.target(), 1.0D, 1.0D)))
        || !claimEmitterForCurrentRuntime(binding.emitter())) {
      rejectPulse(player);
      return;
    }

    WorldRef world = binding.target().world();
    BlockRef targetBlock = world.blockAt(binding.target());
    List<ArchitectRedstonePulse.Snapshot> snapshots = planPulse(targetBlock, binding.face());
    if (snapshots.isEmpty()) {
      rejectPulse(player);
      return;
    }
    if (!authorizeSnapshots(player, world, snapshots)) {
      rejectPulse(player);
      return;
    }

    ArchitectRedstonePulse.Activation activation = pulses.begin(binding.emitter(), snapshots);
    if (activation == null) {
      rejectPulse(player);
      return;
    }

    if (activation.firstPulse()) {
      try {
        applySnapshots(world, activation.snapshots(), true);
      } catch (RuntimeException | Error error) {
        rejectPulse(player);
        pulses.complete(activation);
        applySnapshots(world, activation.snapshots(), false);
        IllegalStateException failure = new IllegalStateException(
            "Failed to energize the Architect Redstone Remote block at " + binding.emitter(), error);
        AdaptLog.error(failure);
        throw failure;
      }
    }

    acceptPulse(player, binding.target());
    if (!Tasks.runAt(binding.target(), () -> finishPulse(activation), ArchitectRedstonePulse.PULSE_TICKS)) {
      finishPulse(activation);
      AdaptLog.error(new IllegalStateException("Failed to schedule Architect Redstone Remote cleanup at "
          + binding.emitter()));
    }
  }

  private boolean authorizeSnapshots(PlayerRef player, WorldRef world,
                                     List<ArchitectRedstonePulse.Snapshot> snapshots) {
    for (ArchitectRedstonePulse.Snapshot snapshot : snapshots) {
      BlockRef block = world.blockAt(snapshot.x(), snapshot.y(), snapshot.z());
      Loc location = block.location();
      if (!canInteract(player, location)
          || !canBlockPlace(player, location)
          || !ProtectionProbes.attemptBlockUse(player, block)) {
        return false;
      }
    }
    return true;
  }

  private List<ArchitectRedstonePulse.Snapshot> planPulse(BlockRef block, BlockFace face) {
    List<ArchitectRedstonePulse.Snapshot> snapshots = new ArrayList<>();
    addSnapshots(block, snapshots);
    if (!snapshots.isEmpty()) {
      return snapshots;
    }

    if (ArchitectRedstonePulse.isBindableFace(face)) {
      addSnapshots(block.relative(face), snapshots);
    }
    for (BlockFace neighbourFace : PULSE_NEIGHBOURS) {
      addSnapshots(block.relative(neighbourFace), snapshots);
    }

    return snapshots;
  }

  private void addSnapshots(BlockRef block, List<ArchitectRedstonePulse.Snapshot> out) {
    ArchitectRedstonePulse.Snapshot snapshot = snapshot(block, out);
    if (snapshot == null) {
      return;
    }

    out.add(snapshot);
    BlockFace otherHalfFace = doorOtherHalf(block.state());
    if (otherHalfFace == null) {
      return;
    }

    ArchitectRedstonePulse.Snapshot half = snapshot(block.relative(otherHalfFace), out);
    if (half != null) {
      out.add(half);
    }
  }

  /** Doors are the two-block halves with a hinge: the other half sits above the lower one and below the upper one. */
  static BlockFace doorOtherHalf(BlockStateRef state) {
    String half = state.property("half");
    if (half == null || state.property("hinge") == null) {
      return null;
    }
    return "lower".equals(half) ? BlockFace.UP : BlockFace.DOWN;
  }

  private ArchitectRedstonePulse.Snapshot snapshot(BlockRef block,
                                                   List<ArchitectRedstonePulse.Snapshot> taken) {
    for (ArchitectRedstonePulse.Snapshot existing : taken) {
      if (existing.x() == block.x() && existing.y() == block.y()
          && existing.z() == block.z()) {
        return null;
      }
    }

    BlockStateRef original = block.state();
    BlockStateRef powered = poweredCopy(original, true);
    if (powered == null || matches(powered, original)) {
      return null;
    }

    return new ArchitectRedstonePulse.Snapshot(
        block.x(), block.y(), block.z(), original, powered);
  }

  private static boolean matches(BlockStateRef first, BlockStateRef second) {
    return first.asString().equals(second.asString());
  }

  private void applySnapshots(WorldRef world, List<ArchitectRedstonePulse.Snapshot> snapshots,
                              boolean powered) {
    for (ArchitectRedstonePulse.Snapshot snapshot : snapshots) {
      BlockRef block = world.blockAt(snapshot.x(), snapshot.y(), snapshot.z());
      BlockStateRef target = powered ? snapshot.powered() : snapshot.original();
      BlockStateRef expected = powered ? snapshot.original() : snapshot.powered();
      if (!matches(block.state(), expected)) {
        continue;
      }

      block.setState(target, true);
      if (powered && NOTE_BLOCK.equals(target.type())) {
        playNote(world, block, target);
      }
    }
  }

  /** Plays the note block's instrument at the block corner in the records category at volume 3. */
  private static void playNote(WorldRef world, BlockRef block, BlockStateRef note) {
    Id sound = noteSound(note.property("instrument"));
    if (sound == null) {
      return;
    }
    AdaptCore.platform().fx().sound(world, block.x(), block.y(), block.z(), sound, SoundCategory.RECORDS, 3.0F,
        notePitch(note.property("note")));
  }

  /** The instrument's sound event, or null for player heads, whose sound comes from the head itself. */
  static Id noteSound(String instrument) {
    if (instrument == null || instrument.equals("custom_head")) {
      return null;
    }
    return switch (instrument) {
      case "zombie", "skeleton", "creeper", "wither_skeleton", "piglin" ->
          Id.minecraft("block.note_block.imitate." + instrument);
      case "dragon" -> Id.minecraft("block.note_block.imitate.ender_dragon");
      default -> Id.minecraft("block.note_block." + instrument);
    };
  }

  static float notePitch(String note) {
    int value;
    try {
      value = note == null ? 0 : Integer.parseInt(note);
    } catch (NumberFormatException invalid) {
      value = 0;
    }
    return (float) Math.pow(2.0D, (value - 12.0D) / 12.0D);
  }

  /**
   * Returns a copy of the block state flipped to the requested power state, or
   * null when the block has no redstone state that can be driven directly.
   */
  static BlockStateRef poweredCopy(BlockStateRef data, boolean powered) {
    if (data == null || !isDriveable(data)) {
      return null;
    }

    if (data.property(LIT) != null) {
      return data.with(LIT, Boolean.toString(powered));
    }

    if (data.property(POWER) != null) {
      return data.with(POWER, Integer.toString(powered ? MAX_POWER : 0));
    }

    if (data.property(OPEN) != null) {
      return data.with(OPEN, Boolean.toString(powered));
    }

    if (data.property(POWERED) != null) {
      return data.with(POWERED, Boolean.toString(powered));
    }

    return null;
  }

  static boolean isDriveable(BlockStateRef data) {
    if (data == null) {
      return false;
    }

    if (data.property(LIT) != null) {
      return isRedstoneLightable(data.type());
    }

    return data.property(POWER) != null || data.property(POWERED) != null;
  }

  /**
   * Lightable also covers furnaces, campfires, and candles. Only redstone
   * fixtures may be lit by a pulse.
   */
  static boolean isRedstoneLightable(Id material) {
    return Id.minecraft("redstone_lamp").equals(material)
        || REDSTONE_TORCH.equals(material)
        || Id.minecraft("redstone_wall_torch").equals(material);
  }

  private void finishPulse(ArchitectRedstonePulse.Activation activation) {
    if (!pulses.complete(activation)) {
      return;
    }

    WorldRef world = AdaptCore.platform().host().world(activation.emitter().worldId());
    if (world == null || !world.isChunkLoaded(
        activation.emitter().x() >> 4, activation.emitter().z() >> 4)) {
      return;
    }
    restoreEmitter(world, new ArchitectRedstonePulse.Restoration(
        activation.emitter(), activation.snapshots()));
  }

  private void showPulseSuccess(PlayerRef player, Loc target) {
    Loc targetCenter = target.add(0.5D, 0.5D, 0.5D);
    Tasks.runAt(targetCenter, () -> fx(targetCenter, FxPriority.GAMEPLAY)
        .dustRing(new Rgb(255, 40, 40), 0.5D, 12, 1.0F)
        .sound(Sounds.BLOCK_BEACON_POWER_SELECT, 0.3F, 1.4F));
    Tasks.runEntity(player, () -> {
      fx(player.eyeLocation(), FxPriority.GAMEPLAY)
          .burst(Particles.ELECTRIC_SPARK, 4, 0.15D)
          .sound(Sounds.BLOCK_NOTE_BLOCK_PLING, 0.5F, 2.0F);
      addStat(player, "architect.wireless-redstone.pulses", 1);
    });
  }

  private void showPulseFailure(PlayerRef player) {
    Tasks.runEntity(player, () -> fx(player.location(), FxPriority.TRANSITION)
        .burst(Particles.SMOKE, 1, 0.05D)
        .sound(Sounds.BLOCK_REDSTONE_TORCH_BURNOUT, 0.1F, 0.9F));
  }

  private void acceptPulse(PlayerRef player, Loc target) {
    UUID playerId = player.uuid();
    if (Tasks.runEntity(player, () -> {
      pendingPulsePlayers.remove(playerId);
      if (!player.isOnline()) {
        return;
      }
      pulseCd.mark(player, getConfig().cooldown);
      showPulseSuccess(player, target);
    })) {
      return;
    }

    pendingPulsePlayers.remove(playerId);
    AdaptLog.error(new IllegalStateException("Failed to schedule Architect Redstone Remote success for "
        + playerId));
  }

  private void rejectPulse(PlayerRef player) {
    pendingPulsePlayers.remove(player.uuid());
    showPulseFailure(player);
  }

  private boolean restoreEmitter(WorldRef world, ArchitectRedstonePulse.Restoration restoration) {
    try {
      applySnapshots(world, restoration.snapshots(), false);
      return true;
    } catch (RuntimeException | Error error) {
      AdaptLog.error("Failed to restore an Architect Redstone Remote block at "
          + restoration.emitter());
      AdaptLog.error(error);
      return false;
    }
  }

  private boolean isValidHeight(WorldRef world, int y) {
    return y >= world.minHeight() && y < world.maxHeight();
  }

  private boolean claimEmitterForCurrentRuntime(ArchitectRedstonePulse.Emitter emitter) {
    Long owner = SHUTDOWN_RESTORATION_OWNERS.compute(emitter, (key, currentOwner) ->
        currentOwner != null && currentOwner >= runtimeGeneration ? currentOwner : null);
    return owner == null;
  }

  private ShutdownRestorationTask registerShutdownRestoration(
      ArchitectRedstonePulse.Restoration restoration) {
    ArchitectRedstonePulse.Emitter emitter = restoration.emitter();
    ShutdownRestorationTask task = new ShutdownRestorationTask(
        restoration, runtimeGeneration, new CompletableFuture<>());
    SHUTDOWN_RESTORATION_OWNERS.merge(emitter, runtimeGeneration, Long::max);
    return task;
  }

  private void releaseShutdownRestoration(ArchitectRedstonePulse.Emitter emitter,
                                          long ownerGeneration) {
    SHUTDOWN_RESTORATION_OWNERS.remove(emitter, ownerGeneration);
  }

  private boolean isRestorationThreadOwned(Loc location) {
    return Tasks.isFoliaThreading()
        ? Tasks.isOwnedByCurrentRegion(location)
        : Tasks.isPrimaryThread();
  }

  private void completeShutdownRestoration(ShutdownRestorationTask task) {
    releaseShutdownRestoration(task.restoration().emitter(), task.ownerGeneration());
    task.completion().complete(null);
  }

  private boolean failShutdownRestoration(ShutdownRestorationTask task, IllegalStateException failure) {
    releaseShutdownRestoration(task.restoration().emitter(), task.ownerGeneration());
    return task.completion().completeExceptionally(failure);
  }

  private void restoreShutdownEmitter(WorldRef world, ShutdownRestorationTask task) {
    ArchitectRedstonePulse.Restoration restoration = task.restoration();
    ArchitectRedstonePulse.Emitter emitter = restoration.emitter();
    long ownerGeneration = task.ownerGeneration();
    Long currentOwner = SHUTDOWN_RESTORATION_OWNERS.get(emitter);
    if (currentOwner == null || currentOwner != ownerGeneration) {
      completeShutdownRestoration(task);
      return;
    }
    if (pulses.owns(emitter)) {
      completeShutdownRestoration(task);
      return;
    }
    if (!SHUTDOWN_RESTORATION_OWNERS.remove(emitter, ownerGeneration)) {
      completeShutdownRestoration(task);
      return;
    }
    if (restoreEmitter(world, restoration)) {
      completeShutdownRestoration(task);
      return;
    }
    failShutdownRestoration(task, new IllegalStateException(
        "Architect Redstone Remote could not restore emitter " + emitter));
  }

  private void scheduleRestoration(ShutdownRestorationTask task, int attempt) {
    if (task.completion().isDone()) {
      return;
    }

    ArchitectRedstonePulse.Restoration restoration = task.restoration();
    ArchitectRedstonePulse.Emitter emitter = restoration.emitter();
    WorldRef world = AdaptCore.platform().host().world(emitter.worldId());
    if (world == null) {
      failShutdownRestoration(task, new IllegalStateException(
          "Architect Redstone Remote bound world is unavailable for " + emitter));
      return;
    }

    Loc location = new Loc(world, emitter.x(), emitter.y(), emitter.z());
    if (isRestorationThreadOwned(location)) {
      if (world.isChunkLoaded(emitter.x() >> 4, emitter.z() >> 4)) {
        restoreShutdownEmitter(world, task);
      } else {
        failShutdownRestoration(task, new IllegalStateException(
            "Architect Redstone Remote bound chunk is not loaded for " + emitter));
      }
      return;
    }
    AdaptCore core = AdaptCore.current();
    if (core == null || core.isStopping()) {
      failShutdownRestoration(task, new IllegalStateException(
          "Adapt disabled before a region-safe Architect Redstone Remote restoration for "
              + emitter));
      return;
    }
    boolean scheduled = Tasks.runAt(location, () -> {
      if (world.isChunkLoaded(emitter.x() >> 4, emitter.z() >> 4)) {
        restoreShutdownEmitter(world, task);
      } else {
        failShutdownRestoration(task, new IllegalStateException(
            "Architect Redstone Remote bound chunk is not loaded for " + emitter));
      }
    });
    if (scheduled) {
      return;
    }
    if (attempt < MAX_SCHEDULE_ATTEMPTS) {
      CompletableFuture.delayedExecutor(50L, TimeUnit.MILLISECONDS)
          .execute(() -> scheduleRestoration(task, attempt + 1));
      return;
    }

    failShutdownRestoration(task, new IllegalStateException(
        "Failed to schedule a region-safe Architect Redstone Remote restoration at " + emitter
            + " after " + MAX_SCHEDULE_ATTEMPTS + " attempts"));
  }

  private void awaitShutdownRestorations(List<ShutdownRestorationTask> tasks, long deadlineNanos) {
    for (int index = 0; index < tasks.size(); index++) {
      ShutdownRestorationTask task = tasks.get(index);
      long remainingNanos = deadlineNanos - System.nanoTime();
      if (remainingNanos <= 0L) {
        timeoutShutdownRestorations(tasks, index);
        return;
      }

      try {
        task.completion().get(remainingNanos, TimeUnit.NANOSECONDS);
      } catch (ExecutionException exception) {
        logShutdownRestorationFailure(task, exception.getCause());
      } catch (TimeoutException exception) {
        timeoutShutdownRestorations(tasks, index);
        return;
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        interruptShutdownRestorations(tasks, index, exception);
        return;
      }
    }
  }

  private void timeoutShutdownRestorations(List<ShutdownRestorationTask> tasks, int startIndex) {
    for (int index = startIndex; index < tasks.size(); index++) {
      ShutdownRestorationTask task = tasks.get(index);
      if (task.completion().isDone()) {
        reportCompletedShutdownRestoration(task);
        continue;
      }
      IllegalStateException failure = new IllegalStateException(
          "Timed out after " + SHUTDOWN_RESTORATION_TIMEOUT_MILLIS
              + "ms waiting for Architect Redstone Remote restoration at "
              + task.restoration().emitter());
      if (failShutdownRestoration(task, failure)) {
        logShutdownRestorationFailure(task, failure);
      } else {
        reportCompletedShutdownRestoration(task);
      }
    }
  }

  private void interruptShutdownRestorations(List<ShutdownRestorationTask> tasks, int startIndex,
                                             InterruptedException interruption) {
    for (int index = startIndex; index < tasks.size(); index++) {
      ShutdownRestorationTask task = tasks.get(index);
      if (task.completion().isDone()) {
        reportCompletedShutdownRestoration(task);
        continue;
      }
      IllegalStateException failure = new IllegalStateException(
          "Interrupted while waiting for Architect Redstone Remote restoration at "
              + task.restoration().emitter(), interruption);
      if (failShutdownRestoration(task, failure)) {
        logShutdownRestorationFailure(task, failure);
      } else {
        reportCompletedShutdownRestoration(task);
      }
    }
  }

  private void reportCompletedShutdownRestoration(ShutdownRestorationTask task) {
    try {
      task.completion().join();
    } catch (RuntimeException exception) {
      Throwable failure = exception.getCause() == null ? exception : exception.getCause();
      logShutdownRestorationFailure(task, failure);
    }
  }

  private void logShutdownRestorationFailure(ShutdownRestorationTask task, Throwable failure) {
    ArchitectRedstonePulse.Emitter emitter = task.restoration().emitter();
    String detail = failure == null ? "unknown failure" : failure.getMessage();
    AdaptLog.error("Architect Redstone Remote restoration failed at " + emitter + ": " + detail);
    if (failure != null) {
      AdaptLog.error(failure);
    }
  }

  @Override
  public void unregister() {
    super.unregister();
    pendingPulsePlayers.clear();
    long deadlineNanos = System.nanoTime()
        + TimeUnit.MILLISECONDS.toNanos(SHUTDOWN_RESTORATION_TIMEOUT_MILLIS);
    List<ShutdownRestorationTask> restorations = new ArrayList<>();
    for (ArchitectRedstonePulse.Restoration restoration : pulses.close()) {
      restorations.add(registerShutdownRestoration(restoration));
    }
    for (ShutdownRestorationTask restoration : restorations) {
      if (System.nanoTime() >= deadlineNanos) {
        timeoutShutdownRestorations(restorations, 0);
        return;
      }
      scheduleRestoration(restoration, 1);
    }
    awaitShutdownRestorations(restorations, deadlineNanos);
  }

  private record BindingRequest(Loc target, BlockFace face, int handSlot, ItemRef handSnapshot) {
  }

  private record PulseBinding(Loc target, BlockFace face,
                              ArchitectRedstonePulse.Emitter emitter) {
  }

  private record ShutdownRestorationTask(ArchitectRedstonePulse.Restoration restoration,
                                         long ownerGeneration,
                                         CompletableFuture<Void> completion) {
  }

  @ConfigDescription("Use a crafted redstone remote to make a bound block emit redstone at a distance.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown for the Architect Wireless Redstone adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    public int cooldown = 125;

    public Config() {
      permanent = true;
      baseCost = 5;
      costFactor = 1;
      maxLevel = 1;
      initialCost = 0;
    }
  }
}
