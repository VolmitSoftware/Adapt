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

import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.ArchitectMessages;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.ItemCooldowns;
import art.arcane.adapt.api.adaptation.ReceiveCancelledEvents;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.recipe.AdaptRecipe;
import art.arcane.adapt.content.item.BoundRedstoneTorch;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.plugin.ProtectionEventProbe;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.AnaloguePowerable;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Lightable;
import org.bukkit.block.data.Openable;
import org.bukkit.block.data.Powerable;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.NoteBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.Event.Result;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

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
  private static final int MAX_SCHEDULE_ATTEMPTS = 3;
  private static final long SHUTDOWN_RESTORATION_TIMEOUT_MILLIS = 3000L;
  private static final Map<ArchitectRedstonePulse.Emitter, Long> SHUTDOWN_RESTORATION_OWNERS =
      new ConcurrentHashMap<>();
  private static final AtomicLong NEXT_RUNTIME_GENERATION = new AtomicLong();
  private static final BlockFace[] PULSE_NEIGHBOURS = {
      BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST
  };

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
    setIcon(Material.REDSTONE_TORCH);
    registerRecipe(AdaptRecipe.shapeless()
        .key("remote-redstone-torch")
        .ingredient(Material.REDSTONE_TORCH)
        .ingredient(Material.TARGET)
        .ingredient(Material.ENDER_PEARL)
        .result(BoundRedstoneTorch.io.withData(new BoundRedstoneTorch.Data(null, null)))
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.REDSTONE)
        .key("challenge_architect_wireless_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.REDSTONE)
            .key("challenge_architect_wireless_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_architect_wireless_100", "architect.wireless-redstone.pulses", 100, 300);
    registerMilestone("challenge_architect_wireless_5k", "architect.wireless-redstone.pulses", 5000, 1000);
  }

  @Override
  public void addStats(int level, Element element) {
    element.addLore(C.GREEN + AdaptLanguage.text(ArchitectMessages.WIRELESS_REDSTONE_LORE1));
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onPlaceBlock(BlockPlaceEvent event) {
    ItemStack item = event.getItemInHand();
    if (BoundRedstoneTorch.hasItemData(item) && isRedstoneTorch(item)) {
      event.setBuild(false);
      event.setCancelled(true);
    }
  }

  @ReceiveCancelledEvents
  @EventHandler(priority = EventPriority.MONITOR)
  public void onPlayerInteract(PlayerInteractEvent event) {
    if (event.getHand() != EquipmentSlot.HAND && event.getHand() != EquipmentSlot.OFF_HAND) {
      return;
    }

    ItemStack itemInHand = event.getItem();
    if (itemInHand == null || !isRedstoneTorch(itemInHand)
        || !BoundRedstoneTorch.hasItemData(itemInHand)) {
      return;
    }

    boolean vetoed = event.getClickedBlock() != null
        ? event.useInteractedBlock() == Result.DENY
        : event.useItemInHand() == Result.DENY;

    Player player = event.getPlayer();
    withPlayerThread(player, () -> {
      if (resolveInteractContext(player, player.getLocation()) == null) {
        return;
      }

      boolean canUseInCreative = AdaptConfig.get().allowAdaptationsInCreative;
      boolean inCreative = player.getGameMode() == GameMode.CREATIVE;
      if (inCreative && !canUseInCreative) {
        return;
      }

      if (vetoed) {
        return;
      }

      if (BoundRedstoneTorch.io.ensureCooldownGroup(itemInHand)) {
        if (event.getHand() == EquipmentSlot.HAND) {
          player.getInventory().setItemInMainHand(itemInHand);
        } else {
          player.getInventory().setItemInOffHand(itemInHand);
        }
      }

      switch (event.getAction()) {
        case LEFT_CLICK_BLOCK, LEFT_CLICK_AIR -> handleLeftClick(event, player);
        case RIGHT_CLICK_AIR, RIGHT_CLICK_BLOCK -> handleRightClick(event, player);
      }
    });
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onChunkUnload(ChunkUnloadEvent event) {
    World world = event.getWorld();
    UUID worldId = world.getUID();
    int chunkX = event.getChunk().getX();
    int chunkZ = event.getChunk().getZ();
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

  private boolean isRedstoneTorch(ItemStack item) {
    return item.getType() == Material.REDSTONE_TORCH;
  }

  private void handleLeftClick(PlayerInteractEvent event, Player player) {
    if (!player.isSneaking() || event.getHand() != EquipmentSlot.HAND) {
      return;
    }

    if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
      event.setUseItemInHand(Result.DENY);
      event.setUseInteractedBlock(Result.DENY);
    }

    BindingRequest binding = createBindingRequest(event, player);
    if (binding == null) {
      showPulseFailure(player);
      return;
    }

    if (!J.runAt(binding.target(), () -> validateBindingTarget(player, binding))) {
      IllegalStateException failure = new IllegalStateException(
          "Failed to schedule Architect Redstone Remote binding target validation at "
              + binding.target());
      Adapt.error(failure);
      showPulseFailure(player);
    }
  }

  private BindingRequest createBindingRequest(PlayerInteractEvent event, Player player) {
    Block target = event.getClickedBlock();
    BlockFace face = event.getBlockFace();
    if (target == null || !ArchitectRedstonePulse.isBindableFace(face)) {
      return null;
    }

    Location targetLocation = target.getLocation();
    World world = targetLocation.getWorld();
    if (!isValidHeight(world, target.getY())) {
      return null;
    }

    int handSlot = player.getInventory().getHeldItemSlot();
    ItemStack handSnapshot = player.getInventory().getItemInMainHand().clone();
    return new BindingRequest(targetLocation, face, handSlot, handSnapshot);
  }

  private void validateBindingTarget(Player player, BindingRequest binding) {
    if (!isRuntimeRegistered() || AdaptConfig.get().isWorldBlacklisted(binding.target().getWorld())
        || binding.target().getBlock().getType().isAir()) {
      showPulseFailure(player);
      return;
    }

    if (!J.runEntity(player, () -> authorizeAndLinkTorch(player, binding))) {
      IllegalStateException failure = new IllegalStateException(
          "Failed to schedule Architect Redstone Remote binding authorization for "
              + player.getUniqueId());
      Adapt.error(failure);
      showPulseFailure(player);
    }
  }

  private void authorizeAndLinkTorch(Player player, BindingRequest binding) {
    ItemStack hand = player.getInventory().getItemInMainHand();
    boolean canUseInCreative = AdaptConfig.get().allowAdaptationsInCreative;
    if (!isRuntimeRegistered() || !player.isOnline() || !player.isSneaking()
        || (player.getGameMode() == GameMode.CREATIVE && !canUseInCreative)
        || player.getInventory().getHeldItemSlot() != binding.handSlot()
        || !hand.equals(binding.handSnapshot())
        || resolveInteractContext(player, binding.target()) == null
        || !canBlockPlace(player, binding.target())) {
      showPulseFailure(player);
      return;
    }

    linkTorch(player, binding);
  }

  private void linkTorch(Player player, BindingRequest binding) {
    Location targetLocation = binding.target();
    ItemStack hand = player.getInventory().getItemInMainHand();
    AtomicBoolean defaultConsumed = new AtomicBoolean();
    boolean paymentAllowed = payItemCost(player, "bind", new ItemStack(hand.getType()), 1, () -> {
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

    Location targetCenter = targetLocation.clone().add(0.5D, 0.5D, 0.5D);
    fx(player.getEyeLocation(), FxPriority.GAMEPLAY)
        .line(Particle.ELECTRIC_SPARK, targetCenter.getX(), targetCenter.getY(), targetCenter.getZ(), 16);
    fx(targetCenter, FxPriority.GAMEPLAY)
        .dustRing(Color.fromRGB(255, 40, 40), 0.6D, 16, 1.0F)
        .chord(Sound.ENTITY_ENDER_EYE_DEATH, 0.2F, 0.48F,
            Sound.BLOCK_BEACON_POWER_SELECT, 0.4F, 1.6F);
  }

  private void createBoundTorch(
      Player player,
      ItemStack hand,
      BindingRequest binding,
      boolean consumeDefault
  ) {
    if (consumeDefault && hand.getAmount() == 1) {
      BoundRedstoneTorch.setData(hand, binding.target(), binding.face());
      return;
    }

    if (consumeDefault) {
      hand.setAmount(hand.getAmount() - 1);
    }
    ItemStack torch = BoundRedstoneTorch.withData(binding.target(), binding.face());
    player.getInventory().addItem(torch).values()
        .forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
  }

  static boolean shouldCreateProviderBoundOutput(boolean paymentAllowed, boolean defaultConsumed) {
    return paymentAllowed && !defaultConsumed;
  }

  private void handleRightClick(PlayerInteractEvent event, Player player) {
    if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
      event.setUseItemInHand(Result.DENY);
      event.setUseInteractedBlock(Result.DENY);
    }

    UUID playerId = player.getUniqueId();
    if (hasCooldown(player) || !pendingPulsePlayers.add(playerId)) {
      fx(player.getEyeLocation(), FxPriority.TRANSITION)
          .burst(Particles.SMOKE, 1, 0.05D)
          .sound(Sound.BLOCK_REDSTONE_TORCH_BURNOUT, 0.1F, 0.9F);
      return;
    }

    try {
      triggerPulse(player, event.getItem());
    } catch (RuntimeException | Error error) {
      rejectPulse(player);
      throw error;
    }
  }

  private boolean hasCooldown(Player player) {
    return !pulseCd.isReady(player, getConfig().cooldown);
  }

  private void triggerPulse(Player player, ItemStack item) {
    PulseBinding binding = resolveBinding(BoundRedstoneTorch.getBinding(item));
    if (binding == null) {
      rejectPulse(player);
      return;
    }

    Adapt.platform.getChunkAtAsync(binding.target())
        .whenComplete((targetChunk, error) -> {
          if (error != null) {
            Adapt.error("Failed to load an Architect Redstone Remote bound chunk at "
                + binding.target());
            Adapt.error(error);
            rejectPulse(player);
            return;
          }
          if (!J.runAt(binding.target(), () -> validateTargetAndSchedulePulse(player, binding))) {
            IllegalStateException failure = new IllegalStateException(
                "Failed to schedule Architect Redstone Remote target validation at " + binding.target());
            Adapt.error(failure);
            rejectPulse(player);
          }
        });
  }

  private void validateTargetAndSchedulePulse(Player player, PulseBinding binding) {
    if (!isRuntimeRegistered()
        || (J.isFoliaThreading()
        && (!J.isOwnedByCurrentRegion(player)
        || !J.isOwnedByCurrentRegion(binding.target(), 1.0D, 1.0D)))
        || binding.target().getBlock().getType().isAir()) {
      rejectPulse(player);
      return;
    }

    if (!J.runEntity(player, () -> authorizeAndSchedulePulse(player, binding))) {
      IllegalStateException failure = new IllegalStateException(
          "Failed to schedule Architect Redstone Remote authorization for " + player.getUniqueId());
      Adapt.error(failure);
      rejectPulse(player);
    }
  }

  private void authorizeAndSchedulePulse(Player player, PulseBinding binding) {
    if (!isRuntimeRegistered() || !player.isOnline()
        || (J.isFoliaThreading()
        && !J.isOwnedByCurrentRegion(binding.target(), 1.0D, 1.0D))
        || resolveInteractContext(player, player.getLocation()) == null
        || !canInteract(player, binding.target())
        || !canBlockPlace(player, binding.target())) {
      rejectPulse(player);
      return;
    }

    if (!J.runAt(binding.target(), () -> beginPulse(player, binding))) {
      IllegalStateException failure = new IllegalStateException(
          "Failed to schedule Architect Redstone Remote pulse at " + binding.emitter());
      Adapt.error(failure);
      rejectPulse(player);
    }
  }

  private PulseBinding resolveBinding(BoundRedstoneTorch.Data data) {
    if (data == null || data.getLocation() == null || !data.getLocation().isFinite()
        || !data.getLocation().isWorldLoaded()
        || !ArchitectRedstonePulse.isBindableFace(data.getFace())) {
      return null;
    }

    Location stored = data.getLocation();
    World world = stored.getWorld();
    int targetX = stored.getBlockX();
    int targetY = stored.getBlockY();
    int targetZ = stored.getBlockZ();
    Location targetLocation = new Location(world, targetX, targetY, targetZ);
    if (AdaptConfig.get().isWorldBlacklisted(world) || !isValidHeight(world, targetY)
        || !world.getWorldBorder().isInside(targetLocation.clone().add(0.5D, 0.5D, 0.5D))) {
      return null;
    }

    ArchitectRedstonePulse.Emitter emitter = ArchitectRedstonePulse.emitter(
        world.getUID(), targetX, targetY, targetZ);
    return emitter == null ? null : new PulseBinding(targetLocation, data.getFace(), emitter);
  }

  /**
   * Drives the bound block itself, or the redstone components touching it when
   * the block has no powered state of its own. No block is ever placed or
   * swapped; only block data flips for the pulse window.
   */
  private void beginPulse(Player player, PulseBinding binding) {
    if (!isRuntimeRegistered()
        || (J.isFoliaThreading()
        && (!J.isOwnedByCurrentRegion(player)
        || !J.isOwnedByCurrentRegion(binding.target(), 1.0D, 1.0D)))
        || !claimEmitterForCurrentRuntime(binding.emitter())) {
      rejectPulse(player);
      return;
    }

    Block targetBlock = binding.target().getBlock();
    List<ArchitectRedstonePulse.Snapshot> snapshots = planPulse(targetBlock, binding.face());
    if (snapshots.isEmpty()) {
      rejectPulse(player);
      return;
    }
    if (!authorizeSnapshots(player, targetBlock.getWorld(), snapshots)) {
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
        applySnapshots(targetBlock.getWorld(), activation.snapshots(), true);
      } catch (RuntimeException | Error error) {
        rejectPulse(player);
        pulses.complete(activation);
        applySnapshots(targetBlock.getWorld(), activation.snapshots(), false);
        IllegalStateException failure = new IllegalStateException(
            "Failed to energize the Architect Redstone Remote block at " + binding.emitter(), error);
        Adapt.error(failure);
        throw failure;
      }
    }

    acceptPulse(player, binding.target());
    if (!J.runAt(binding.target(), () -> finishPulse(activation), ArchitectRedstonePulse.PULSE_TICKS)) {
      finishPulse(activation);
      Adapt.error(new IllegalStateException("Failed to schedule Architect Redstone Remote cleanup at "
          + binding.emitter()));
    }
  }

  private boolean authorizeSnapshots(Player player, World world,
                                     List<ArchitectRedstonePulse.Snapshot> snapshots) {
    for (ArchitectRedstonePulse.Snapshot snapshot : snapshots) {
      Block block = world.getBlockAt(snapshot.x(), snapshot.y(), snapshot.z());
      Location location = block.getLocation();
      if (!canInteract(player, location)
          || !canBlockPlace(player, location)
          || !ProtectionEventProbe.attemptBlockUse(player, block)) {
        return false;
      }
    }
    return true;
  }

  private List<ArchitectRedstonePulse.Snapshot> planPulse(Block block, BlockFace face) {
    List<ArchitectRedstonePulse.Snapshot> snapshots = new ArrayList<>();
    addSnapshots(block, snapshots);
    if (!snapshots.isEmpty()) {
      return snapshots;
    }

    if (ArchitectRedstonePulse.isBindableFace(face)) {
      addSnapshots(block.getRelative(face), snapshots);
    }
    for (BlockFace neighbourFace : PULSE_NEIGHBOURS) {
      addSnapshots(block.getRelative(neighbourFace), snapshots);
    }

    return snapshots;
  }

  private void addSnapshots(Block block, List<ArchitectRedstonePulse.Snapshot> out) {
    ArchitectRedstonePulse.Snapshot snapshot = snapshot(block, out);
    if (snapshot == null) {
      return;
    }

    out.add(snapshot);
    if (!(block.getBlockData() instanceof Door door)) {
      return;
    }

    Block otherHalf = block.getRelative(door.getHalf() == Bisected.Half.BOTTOM
        ? BlockFace.UP : BlockFace.DOWN);
    ArchitectRedstonePulse.Snapshot half = snapshot(otherHalf, out);
    if (half != null) {
      out.add(half);
    }
  }

  private ArchitectRedstonePulse.Snapshot snapshot(Block block,
                                                   List<ArchitectRedstonePulse.Snapshot> taken) {
    for (ArchitectRedstonePulse.Snapshot existing : taken) {
      if (existing.x() == block.getX() && existing.y() == block.getY()
          && existing.z() == block.getZ()) {
        return null;
      }
    }

    BlockData original = block.getBlockData();
    BlockData powered = poweredCopy(original, true);
    if (powered == null || powered.matches(original)) {
      return null;
    }

    return new ArchitectRedstonePulse.Snapshot(
        block.getX(), block.getY(), block.getZ(), original, powered);
  }

  private void applySnapshots(World world, List<ArchitectRedstonePulse.Snapshot> snapshots,
                              boolean powered) {
    for (ArchitectRedstonePulse.Snapshot snapshot : snapshots) {
      Block block = world.getBlockAt(snapshot.x(), snapshot.y(), snapshot.z());
      BlockData target = powered ? snapshot.powered() : snapshot.original();
      BlockData expected = powered ? snapshot.original() : snapshot.powered();
      if (!block.getBlockData().matches(expected)) {
        continue;
      }

      block.setBlockData(target, true);
      if (powered && target instanceof NoteBlock note) {
        world.playNote(block.getLocation(), note.getInstrument(), note.getNote());
      }
    }
  }

  /**
   * Returns a copy of the block data flipped to the requested power state, or
   * null when the block has no redstone state that can be driven directly.
   */
  static BlockData poweredCopy(BlockData data, boolean powered) {
    if (data == null || !isDriveable(data)) {
      return null;
    }

    BlockData copy = data.clone();
    if (copy instanceof Lightable lightable) {
      lightable.setLit(powered);
      return copy;
    }

    if (copy instanceof AnaloguePowerable analogue) {
      analogue.setPower(powered ? analogue.getMaximumPower() : 0);
      return copy;
    }

    if (copy instanceof Openable openable) {
      openable.setOpen(powered);
      return copy;
    }

    if (copy instanceof Powerable powerable) {
      powerable.setPowered(powered);
      return copy;
    }

    return null;
  }

  static boolean isDriveable(BlockData data) {
    if (data == null) {
      return false;
    }

    if (data instanceof Lightable) {
      return isRedstoneLightable(data.getMaterial());
    }

    return data instanceof AnaloguePowerable || data instanceof Powerable;
  }

  /**
   * Lightable also covers furnaces, campfires, and candles. Only redstone
   * fixtures may be lit by a pulse.
   */
  static boolean isRedstoneLightable(Material material) {
    return material == Material.REDSTONE_LAMP
        || material == Material.REDSTONE_TORCH
        || material == Material.REDSTONE_WALL_TORCH;
  }

  private void finishPulse(ArchitectRedstonePulse.Activation activation) {
    if (!pulses.complete(activation)) {
      return;
    }

    World world = Bukkit.getWorld(activation.emitter().worldId());
    if (world == null || !world.isChunkLoaded(
        activation.emitter().x() >> 4, activation.emitter().z() >> 4)) {
      return;
    }
    restoreEmitter(world, new ArchitectRedstonePulse.Restoration(
        activation.emitter(), activation.snapshots()));
  }

  private void showPulseSuccess(Player player, Location target) {
    Location targetCenter = target.clone().add(0.5D, 0.5D, 0.5D);
    J.runAt(targetCenter, () -> fx(targetCenter, FxPriority.GAMEPLAY)
        .dustRing(Color.fromRGB(255, 40, 40), 0.5D, 12, 1.0F)
        .sound(Sound.BLOCK_BEACON_POWER_SELECT, 0.3F, 1.4F));
    J.runEntity(player, () -> {
      fx(player.getEyeLocation(), FxPriority.GAMEPLAY)
          .burst(Particle.ELECTRIC_SPARK, 4, 0.15D)
          .sound(Sound.BLOCK_NOTE_BLOCK_PLING, 0.5F, 2.0F);
      addStat(player, "architect.wireless-redstone.pulses", 1);
    });
  }

  private void showPulseFailure(Player player) {
    J.runEntity(player, () -> fx(player.getLocation(), FxPriority.TRANSITION)
        .burst(Particles.SMOKE, 1, 0.05D)
        .sound(Sound.BLOCK_REDSTONE_TORCH_BURNOUT, 0.1F, 0.9F));
  }

  private void acceptPulse(Player player, Location target) {
    UUID playerId = player.getUniqueId();
    if (J.runEntity(player, () -> {
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
    Adapt.error(new IllegalStateException("Failed to schedule Architect Redstone Remote success for "
        + playerId));
  }

  private void rejectPulse(Player player) {
    pendingPulsePlayers.remove(player.getUniqueId());
    showPulseFailure(player);
  }

  private boolean restoreEmitter(World world, ArchitectRedstonePulse.Restoration restoration) {
    try {
      applySnapshots(world, restoration.snapshots(), false);
      return true;
    } catch (RuntimeException | Error error) {
      Adapt.error("Failed to restore an Architect Redstone Remote block at "
          + restoration.emitter());
      Adapt.error(error);
      return false;
    }
  }

  private boolean isValidHeight(World world, int y) {
    return y >= world.getMinHeight() && y < world.getMaxHeight();
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

  private boolean isRestorationThreadOwned(Location location) {
    return J.isFoliaThreading()
        ? J.isOwnedByCurrentRegion(location)
        : J.isPrimaryThread();
  }

  private void completeShutdownRestoration(ShutdownRestorationTask task) {
    releaseShutdownRestoration(task.restoration().emitter(), task.ownerGeneration());
    task.completion().complete(null);
  }

  private boolean failShutdownRestoration(ShutdownRestorationTask task, IllegalStateException failure) {
    releaseShutdownRestoration(task.restoration().emitter(), task.ownerGeneration());
    return task.completion().completeExceptionally(failure);
  }

  private void restoreShutdownEmitter(World world, ShutdownRestorationTask task) {
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
    World world = Bukkit.getWorld(emitter.worldId());
    if (world == null) {
      failShutdownRestoration(task, new IllegalStateException(
          "Architect Redstone Remote bound world is unavailable for " + emitter));
      return;
    }

    Location location = new Location(world, emitter.x(), emitter.y(), emitter.z());
    if (isRestorationThreadOwned(location)) {
      if (world.isChunkLoaded(emitter.x() >> 4, emitter.z() >> 4)) {
        restoreShutdownEmitter(world, task);
      } else {
        failShutdownRestoration(task, new IllegalStateException(
            "Architect Redstone Remote bound chunk is not loaded for " + emitter));
      }
      return;
    }
    if (!Adapt.instance.isEnabled()) {
      failShutdownRestoration(task, new IllegalStateException(
          "Adapt disabled before a region-safe Architect Redstone Remote restoration for "
              + emitter));
      return;
    }
    boolean scheduled = J.runAt(location, () -> {
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
    Adapt.error("Architect Redstone Remote restoration failed at " + emitter + ": " + detail);
    if (failure != null) {
      Adapt.error(failure);
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

  private record BindingRequest(Location target, BlockFace face, int handSlot, ItemStack handSnapshot) {
  }

  private record PulseBinding(Location target, BlockFace face,
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
