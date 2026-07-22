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
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.recipe.AdaptRecipe;
import art.arcane.adapt.content.item.BoundRedstoneTorch;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.Event.Result;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class ArchitectWirelessRedstone extends SimpleAdaptation<ArchitectWirelessRedstone.Config> {
  private static final NamespacedKey JOURNAL_KEY = new NamespacedKey("adapt", "architect-redstone-remote");
  private static final int MAX_JOURNALED_RECEIVERS_PER_CHUNK = 4096;
  private static final int MAX_RECOVERY_CHUNKS_PER_TICK = 32;
  private static final int MAX_SCHEDULE_ATTEMPTS = 3;
  private static final long SHUTDOWN_RESTORATION_TIMEOUT_MILLIS = 3000L;
  private static final Map<ArchitectRedstonePulse.Receiver, Long> SHUTDOWN_RESTORATION_OWNERS =
      new ConcurrentHashMap<>();
  private static final AtomicLong NEXT_RUNTIME_GENERATION = new AtomicLong();

  private final Cooldowns pulseCd = cooldowns();
  private final ArchitectRedstonePulse pulses = new ArchitectRedstonePulse();
  private final Queue<ChunkRecovery> recoveryQueue = new ConcurrentLinkedQueue<>();
  private final Set<ChunkRecovery> queuedRecoveries = ConcurrentHashMap.newKeySet();
  private final Map<ChunkRecovery, Integer> recoveryScheduleFailures = new ConcurrentHashMap<>();
  private final AtomicBoolean recoveryScheduled = new AtomicBoolean();
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
  protected void onRuntimeActivated() {
    queueLoadedChunkRecovery();
  }

  @Override
  public void addStats(int level, Element element) {
    element.addLore(C.GREEN + AdaptLanguage.text(ArchitectMessages.WIRELESS_REDSTONE_LORE1));
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onPlaceBlock(BlockPlaceEvent event) {
    if (isProtectedReceiver(event.getBlockPlaced())) {
      event.setBuild(false);
      event.setCancelled(true);
      return;
    }

    ItemStack item = event.getItemInHand();
    if (BoundRedstoneTorch.hasItemData(item) && isRedstoneTorch(item)) {
      event.setBuild(false);
      event.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onBlockBreak(BlockBreakEvent event) {
    if (isProtectedReceiver(event.getBlock())) {
      event.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onPistonExtend(BlockPistonExtendEvent event) {
    for (Block block : event.getBlocks()) {
      if (isProtectedReceiver(block) || isProtectedReceiver(block.getRelative(event.getDirection()))) {
        event.setCancelled(true);
        return;
      }
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onPistonRetract(BlockPistonRetractEvent event) {
    for (Block block : event.getBlocks()) {
      if (isProtectedReceiver(block)
          || isProtectedReceiver(block.getRelative(event.getDirection().getOppositeFace()))) {
        event.setCancelled(true);
        return;
      }
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onBlockExplode(BlockExplodeEvent event) {
    event.blockList().removeIf(this::isProtectedReceiver);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onEntityExplode(EntityExplodeEvent event) {
    event.blockList().removeIf(this::isProtectedReceiver);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onEntityChangeBlock(EntityChangeBlockEvent event) {
    if (isProtectedReceiver(event.getBlock())) {
      event.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onPlayerInteract(PlayerInteractEvent event) {
    if (event.getHand() != EquipmentSlot.HAND && event.getHand() != EquipmentSlot.OFF_HAND) {
      return;
    }

    ItemStack itemInHand = event.getItem();
    if (itemInHand == null || !isRedstoneTorch(itemInHand)
        || !BoundRedstoneTorch.hasItemData(itemInHand)) {
      return;
    }

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

      switch (event.getAction()) {
        case LEFT_CLICK_BLOCK, LEFT_CLICK_AIR -> handleLeftClick(event, player);
        case RIGHT_CLICK_AIR, RIGHT_CLICK_BLOCK -> handleRightClick(event, player);
      }
    });
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onChunkLoad(ChunkLoadEvent event) {
    Chunk chunk = event.getChunk();
    queueChunkRecovery(chunk.getWorld(), chunk.getX(), chunk.getZ());
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onChunkUnload(ChunkUnloadEvent event) {
    World world = event.getWorld();
    UUID worldId = world.getUID();
    int chunkX = event.getChunk().getX();
    int chunkZ = event.getChunk().getZ();
    for (ArchitectRedstonePulse.Receiver receiver : pulses.receivers()) {
      if (!receiver.isInChunk(worldId, chunkX, chunkZ)) {
        continue;
      }
      ArchitectRedstonePulse.Restoration restoration = pulses.cancel(receiver);
      if (restoration != null) {
        restoreReceiver(world, restoration);
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
      failure.printStackTrace();
      showPulseFailure(player);
    }
  }

  private BindingRequest createBindingRequest(PlayerInteractEvent event, Player player) {
    Block target = event.getClickedBlock();
    BlockFace face = event.getBlockFace();
    if (target == null || !ArchitectRedstonePulse.isReceiverFace(face)) {
      return null;
    }

    Location targetLocation = target.getLocation();
    World world = targetLocation.getWorld();
    ArchitectRedstonePulse.Receiver receiver = ArchitectRedstonePulse.receiver(
        world.getUID(), target.getX(), target.getY(), target.getZ(), face);
    if (receiver == null || !isValidHeight(world, target.getY()) || !isValidHeight(world, receiver.y())) {
      return null;
    }
    Location receiverLocation = new Location(world, receiver.x(), receiver.y(), receiver.z());
    int handSlot = player.getInventory().getHeldItemSlot();
    ItemStack handSnapshot = player.getInventory().getItemInMainHand().clone();
    return new BindingRequest(targetLocation, face, receiverLocation, handSlot, handSnapshot);
  }

  private void validateBindingTarget(Player player, BindingRequest binding) {
    if (!isRuntimeRegistered() || AdaptConfig.get().isWorldBlacklisted(binding.target().getWorld())
        || binding.target().getBlock().getType().isAir()) {
      showPulseFailure(player);
      return;
    }

    if (!J.runAt(binding.receiver(), () -> validateBindingReceiver(player, binding))) {
      IllegalStateException failure = new IllegalStateException(
          "Failed to schedule Architect Redstone Remote binding receiver validation at "
              + binding.receiver());
      failure.printStackTrace();
      showPulseFailure(player);
    }
  }

  private void validateBindingReceiver(Player player, BindingRequest binding) {
    if (!isRuntimeRegistered() || !isReceiverAvailable(binding.receiver().getBlock())) {
      showPulseFailure(player);
      return;
    }

    if (!J.runEntity(player, () -> authorizeAndLinkTorch(player, binding))) {
      IllegalStateException failure = new IllegalStateException(
          "Failed to schedule Architect Redstone Remote binding authorization for "
              + player.getUniqueId());
      failure.printStackTrace();
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
        || !canBlockPlace(player, binding.receiver())) {
      showPulseFailure(player);
      return;
    }

    linkTorch(player, binding);
  }

  private void linkTorch(Player player, BindingRequest binding) {
    Location targetLocation = binding.target();
    Location targetCenter = targetLocation.clone().add(0.5D, 0.5D, 0.5D);
    fx(player.getEyeLocation(), FxPriority.GAMEPLAY)
        .line(Particle.ELECTRIC_SPARK, targetCenter.getX(), targetCenter.getY(), targetCenter.getZ(), 16);
    fx(targetCenter, FxPriority.GAMEPLAY)
        .dustRing(Color.fromRGB(255, 40, 40), 0.6D, 16, 1.0F)
        .chord(Sound.ENTITY_ENDER_EYE_DEATH, 0.2F, 0.48F,
            Sound.BLOCK_BEACON_POWER_SELECT, 0.4F, 1.6F);
    ItemStack hand = player.getInventory().getItemInMainHand();
    if (hand.getAmount() == 1) {
      BoundRedstoneTorch.setData(hand, targetLocation, binding.face());
      return;
    }

    hand.setAmount(hand.getAmount() - 1);
    ItemStack torch = BoundRedstoneTorch.withData(targetLocation, binding.face());
    player.getInventory().addItem(torch).values()
        .forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
  }

  private void handleRightClick(PlayerInteractEvent event, Player player) {
    if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
      event.setUseItemInHand(Result.DENY);
      event.setUseInteractedBlock(Result.DENY);
    }

    if (hasCooldown(player)) {
      fx(player.getEyeLocation(), FxPriority.TRANSITION)
          .burst(Particles.SMOKE, 1, 0.05D)
          .sound(Sound.BLOCK_REDSTONE_TORCH_BURNOUT, 0.1F, 0.9F);
      return;
    }

    pulseCd.mark(player.getUniqueId());
    showPlayerCooldown(player);
    triggerPulse(player, event.getItem());
  }

  private void showPlayerCooldown(Player player) {
    int cooldownTicks = Math.max(1, (int) Math.ceil(getConfig().cooldown / 50D));
    player.setCooldown(Material.REDSTONE_TORCH, cooldownTicks);
  }

  private boolean hasCooldown(Player player) {
    return !pulseCd.isReady(player.getUniqueId(), getConfig().cooldown);
  }

  private void triggerPulse(Player player, ItemStack item) {
    PulseBinding binding = resolveBinding(BoundRedstoneTorch.getBinding(item));
    if (binding == null) {
      showPulseFailure(player);
      return;
    }

    Adapt.platform.getChunkAtAsync(binding.target())
        .thenCompose(targetChunk -> Adapt.platform.getChunkAtAsync(binding.receiver()))
        .whenComplete((receiverChunk, error) -> {
          if (error != null) {
            Adapt.error("Failed to load an Architect Redstone Remote receiver chunk at "
                + binding.receiver());
            error.printStackTrace();
            showPulseFailure(player);
            return;
          }
          if (!J.runAt(binding.target(), () -> validateTargetAndSchedulePulse(player, binding))) {
            IllegalStateException failure = new IllegalStateException(
                "Failed to schedule Architect Redstone Remote target validation at " + binding.target());
            failure.printStackTrace();
            showPulseFailure(player);
          }
        });
  }

  private void validateTargetAndSchedulePulse(Player player, PulseBinding binding) {
    if (!isRuntimeRegistered() || binding.target().getBlock().getType().isAir()) {
      showPulseFailure(player);
      return;
    }

    if (!J.runEntity(player, () -> authorizeAndSchedulePulse(player, binding))) {
      IllegalStateException failure = new IllegalStateException(
          "Failed to schedule Architect Redstone Remote authorization for " + player.getUniqueId());
      failure.printStackTrace();
      showPulseFailure(player);
    }
  }

  private void authorizeAndSchedulePulse(Player player, PulseBinding binding) {
    if (!isRuntimeRegistered() || !player.isOnline()
        || resolveInteractContext(player, player.getLocation()) == null
        || !canInteract(player, binding.target())
        || !canBlockPlace(player, binding.receiver())) {
      showPulseFailure(player);
      return;
    }

    if (!J.runAt(binding.receiver(), () -> beginPulse(player, binding))) {
      IllegalStateException failure = new IllegalStateException(
          "Failed to schedule Architect Redstone Remote pulse at " + binding.receiverKey());
      failure.printStackTrace();
      showPulseFailure(player);
    }
  }

  private PulseBinding resolveBinding(BoundRedstoneTorch.Data data) {
    if (data == null || data.getLocation() == null || !data.getLocation().isFinite()
        || !data.getLocation().isWorldLoaded()
        || !ArchitectRedstonePulse.isReceiverFace(data.getFace())) {
      return null;
    }

    Location stored = data.getLocation();
    World world = stored.getWorld();
    int targetX = stored.getBlockX();
    int targetY = stored.getBlockY();
    int targetZ = stored.getBlockZ();
    if (AdaptConfig.get().isWorldBlacklisted(world) || !isValidHeight(world, targetY)) {
      return null;
    }

    ArchitectRedstonePulse.Receiver receiver = ArchitectRedstonePulse.receiver(
        world.getUID(), targetX, targetY, targetZ, data.getFace());
    Location receiverLocation = receiver == null ? null
        : new Location(world, receiver.x(), receiver.y(), receiver.z());
    if (receiver == null || !isValidHeight(world, receiver.y())
        || !world.getWorldBorder().isInside(receiverLocation.clone().add(0.5D, 0.5D, 0.5D))) {
      return null;
    }

    return new PulseBinding(
        new Location(world, targetX, targetY, targetZ),
        receiverLocation,
        receiver
    );
  }

  private void beginPulse(Player player, PulseBinding binding) {
    if (!isRuntimeRegistered() || !claimReceiverForCurrentRuntime(binding.receiverKey())) {
      showPulseFailure(player);
      return;
    }

    Block receiverBlock = binding.receiver().getBlock();
    boolean overlapping = pulses.owns(binding.receiverKey());
    if ((!overlapping && !isReceiverAvailable(receiverBlock))
        || (overlapping && receiverBlock.getType() != Material.REDSTONE_BLOCK)
        || !journalReceiver(receiverBlock, overlapping
        ? pulses.originalMaterial(binding.receiverKey()) : receiverBlock.getType())) {
      if (overlapping && receiverBlock.getType() != Material.REDSTONE_BLOCK) {
        pulses.cancel(binding.receiverKey());
        unjournalReceiver(receiverBlock);
      }
      showPulseFailure(player);
      return;
    }

    ArchitectRedstonePulse.Activation activation = pulses.begin(
        binding.receiverKey(), receiverBlock.getType());
    if (activation == null) {
      if (!overlapping) {
        unjournalReceiver(receiverBlock);
      }
      showPulseFailure(player);
      return;
    }

    if (activation.firstPulse()) {
      try {
        receiverBlock.setType(Material.REDSTONE_BLOCK, true);
      } catch (RuntimeException | Error error) {
        pulses.complete(activation);
        restoreReceiver(receiverBlock, activation.originalMaterial());
        IllegalStateException failure = new IllegalStateException(
            "Failed to place Architect Redstone Remote source at " + binding.receiverKey(), error);
        failure.printStackTrace();
        throw failure;
      }
      if (receiverBlock.getType() != Material.REDSTONE_BLOCK) {
        pulses.complete(activation);
        unjournalReceiver(receiverBlock);
        showPulseFailure(player);
        return;
      }
    }

    showPulseSuccess(player, binding.target());
    if (!J.runAt(binding.receiver(), () -> finishPulse(activation), ArchitectRedstonePulse.PULSE_TICKS)) {
      finishPulse(activation);
      new IllegalStateException("Failed to schedule Architect Redstone Remote cleanup at "
          + binding.receiverKey()).printStackTrace();
    }
  }

  private void finishPulse(ArchitectRedstonePulse.Activation activation) {
    if (!pulses.complete(activation)) {
      return;
    }

    World world = Bukkit.getWorld(activation.receiver().worldId());
    if (world == null || !world.isChunkLoaded(
        activation.receiver().x() >> 4, activation.receiver().z() >> 4)) {
      return;
    }
    restoreReceiver(world, new ArchitectRedstonePulse.Restoration(
        activation.receiver(), activation.originalMaterial()));
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

  private boolean journalReceiver(Block block, Material originalMaterial) {
    if (originalMaterial == null || !originalMaterial.isAir()) {
      return false;
    }
    Chunk chunk = block.getChunk();
    PersistentDataContainer data = chunk.getPersistentDataContainer();
    int encoded = ArchitectRedstonePulse.encodeBlock(
        block.getX() & 15, block.getY(), block.getZ() & 15,
        block.getWorld().getMinHeight(), originalMaterial);
    int[] journal = data.get(JOURNAL_KEY, PersistentDataType.INTEGER_ARRAY);
    if (journal == null) {
      data.set(JOURNAL_KEY, PersistentDataType.INTEGER_ARRAY, new int[]{encoded});
      return true;
    }
    for (int existing : journal) {
      if (existing == encoded) {
        return true;
      }
    }
    if (journal.length >= MAX_JOURNALED_RECEIVERS_PER_CHUNK) {
      return false;
    }

    int[] expanded = Arrays.copyOf(journal, journal.length + 1);
    expanded[journal.length] = encoded;
    data.set(JOURNAL_KEY, PersistentDataType.INTEGER_ARRAY, expanded);
    return true;
  }

  private void unjournalReceiver(Block block) {
    PersistentDataContainer data = block.getChunk().getPersistentDataContainer();
    int[] journal = data.get(JOURNAL_KEY, PersistentDataType.INTEGER_ARRAY);
    if (journal == null || journal.length == 0) {
      return;
    }

    int found = -1;
    for (int index = 0; index < journal.length; index++) {
      int encoded = journal[index];
      if (matchesJournalPosition(block, encoded)) {
        found = index;
        break;
      }
    }
    if (found < 0) {
      return;
    }
    if (journal.length == 1) {
      data.remove(JOURNAL_KEY);
      return;
    }

    int[] compacted = new int[journal.length - 1];
    System.arraycopy(journal, 0, compacted, 0, found);
    System.arraycopy(journal, found + 1, compacted, found, journal.length - found - 1);
    data.set(JOURNAL_KEY, PersistentDataType.INTEGER_ARRAY, compacted);
  }

  private void queueLoadedChunkRecovery() {
    for (World world : Bukkit.getWorlds()) {
      for (Chunk chunk : world.getLoadedChunks()) {
        queueChunkRecovery(world, chunk.getX(), chunk.getZ());
      }
    }
  }

  private void queueChunkRecovery(World world, int chunkX, int chunkZ) {
    if (!isRuntimeRegistered()) {
      return;
    }

    ChunkRecovery recovery = new ChunkRecovery(world, chunkX, chunkZ);
    recoveryScheduleFailures.remove(recovery);
    enqueueRecovery(recovery);
  }

  private void enqueueRecovery(ChunkRecovery recovery) {
    if (!queuedRecoveries.add(recovery)) {
      return;
    }
    recoveryQueue.add(recovery);
    scheduleRecoveryDrain();
  }

  private void scheduleRecoveryDrain() {
    ChunkRecovery anchorRecovery = recoveryQueue.peek();
    if (anchorRecovery == null || !recoveryScheduled.compareAndSet(false, true)) {
      return;
    }

    Location anchor = new Location(anchorRecovery.world(), (anchorRecovery.chunkX() << 4) + 8,
        anchorRecovery.world().getMinHeight(), (anchorRecovery.chunkZ() << 4) + 8);
    if (J.runAt(anchor, this::drainRecoveryQueue, 1)) {
      return;
    }

    recoveryScheduled.set(false);
    retryRecoveryDrain(anchorRecovery);
  }

  private void retryRecoveryDrain(ChunkRecovery recovery) {
    int attempts = recoveryScheduleFailures.merge(recovery, 1, Integer::sum);
    if (isRuntimeRegistered() && attempts < MAX_SCHEDULE_ATTEMPTS) {
      CompletableFuture.delayedExecutor(50L, TimeUnit.MILLISECONDS).execute(() -> {
        if (isRuntimeRegistered()) {
          scheduleRecoveryDrain();
        }
      });
      return;
    }

    recoveryScheduleFailures.remove(recovery);
    recoveryQueue.remove(recovery);
    queuedRecoveries.remove(recovery);
    IllegalStateException failure = new IllegalStateException(
        "Failed to schedule Architect Redstone Remote recovery queue for chunk "
            + recovery.world().getName() + "[" + recovery.chunkX() + "," + recovery.chunkZ() + "]");
    failure.printStackTrace();
    scheduleRecoveryDrain();
  }

  private void drainRecoveryQueue() {
    if (!isRuntimeRegistered()) {
      recoveryQueue.clear();
      queuedRecoveries.clear();
      recoveryScheduled.set(false);
      return;
    }

    int dispatched = 0;
    ChunkRecovery recovery;
    while (dispatched < MAX_RECOVERY_CHUNKS_PER_TICK && (recovery = recoveryQueue.poll()) != null) {
      ChunkRecovery current = recovery;
      queuedRecoveries.remove(current);
      Location anchor = new Location(current.world(), (current.chunkX() << 4) + 8,
          current.world().getMinHeight(), (current.chunkZ() << 4) + 8);
      if (J.runAt(anchor, () -> recoverChunk(current))) {
        recoveryScheduleFailures.remove(current);
      } else {
        retryRecovery(current);
      }
      dispatched++;
    }

    recoveryScheduled.set(false);
    scheduleRecoveryDrain();
  }

  private void retryRecovery(ChunkRecovery recovery) {
    int attempts = recoveryScheduleFailures.merge(recovery, 1, Integer::sum);
    if (isRuntimeRegistered() && attempts < MAX_SCHEDULE_ATTEMPTS) {
      CompletableFuture.delayedExecutor(50L, TimeUnit.MILLISECONDS).execute(() -> {
        if (isRuntimeRegistered()) {
          enqueueRecovery(recovery);
        }
      });
      return;
    }

    recoveryScheduleFailures.remove(recovery);
    IllegalStateException failure = new IllegalStateException(
        "Failed to schedule Architect Redstone Remote recovery for chunk "
            + recovery.world().getName() + "[" + recovery.chunkX() + "," + recovery.chunkZ() + "]");
    failure.printStackTrace();
  }

  private void recoverChunk(ChunkRecovery recovery) {
    World world = recovery.world();
    if (!world.isChunkLoaded(recovery.chunkX(), recovery.chunkZ())) {
      return;
    }

    Chunk chunk = world.getChunkAt(recovery.chunkX(), recovery.chunkZ());
    PersistentDataContainer data = chunk.getPersistentDataContainer();
    int[] journal = data.get(JOURNAL_KEY, PersistentDataType.INTEGER_ARRAY);
    if (journal == null || journal.length == 0) {
      return;
    }

    int limit = Math.min(journal.length, MAX_JOURNALED_RECEIVERS_PER_CHUNK);
    int[] retained = new int[journal.length];
    int retainedCount = 0;
    for (int index = 0; index < limit; index++) {
      int encoded = journal[index];
      int y = ArchitectRedstonePulse.decodeY(encoded, world.getMinHeight());
      Material originalMaterial = ArchitectRedstonePulse.decodeOriginalMaterial(encoded);
      if (!isValidHeight(world, y) || originalMaterial == null) {
        continue;
      }

      ArchitectRedstonePulse.Receiver receiver = new ArchitectRedstonePulse.Receiver(
          world.getUID(),
          (recovery.chunkX() << 4) + ArchitectRedstonePulse.decodeX(encoded),
          y,
          (recovery.chunkZ() << 4) + ArchitectRedstonePulse.decodeZ(encoded)
      );
      if (!claimReceiverForCurrentRuntime(receiver)) {
        retained[retainedCount++] = encoded;
        continue;
      }
      if (pulses.owns(receiver)) {
        retained[retainedCount++] = encoded;
        continue;
      }

      Block block = world.getBlockAt(receiver.x(), receiver.y(), receiver.z());
      if (block.getType() == Material.REDSTONE_BLOCK
          && !restoreReceiver(block, originalMaterial, false)) {
        retained[retainedCount++] = encoded;
      }
    }
    for (int index = limit; index < journal.length; index++) {
      retained[retainedCount++] = journal[index];
    }

    if (retainedCount == 0) {
      data.remove(JOURNAL_KEY);
    } else {
      data.set(JOURNAL_KEY, PersistentDataType.INTEGER_ARRAY, Arrays.copyOf(retained, retainedCount));
    }
    if (journal.length > limit && retainedCount < journal.length) {
      queueChunkRecovery(world, recovery.chunkX(), recovery.chunkZ());
    }
  }

  private boolean isProtectedReceiver(Block block) {
    ArchitectRedstonePulse.Receiver receiver = new ArchitectRedstonePulse.Receiver(
        block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    if (pulses.owns(receiver)) {
      return true;
    }

    int[] journal = block.getChunk().getPersistentDataContainer()
        .get(JOURNAL_KEY, PersistentDataType.INTEGER_ARRAY);
    if (journal == null) {
      return false;
    }
    for (int encoded : journal) {
      if (matchesJournalPosition(block, encoded)) {
        return true;
      }
    }
    return false;
  }

  private boolean matchesJournalPosition(Block block, int encoded) {
    return ArchitectRedstonePulse.decodeX(encoded) == (block.getX() & 15)
        && ArchitectRedstonePulse.decodeY(encoded, block.getWorld().getMinHeight()) == block.getY()
        && ArchitectRedstonePulse.decodeZ(encoded) == (block.getZ() & 15);
  }

  private boolean isReceiverAvailable(Block block) {
    Location center = block.getLocation().add(0.5D, 0.5D, 0.5D);
    return block.getType().isAir()
        && block.getWorld().getWorldBorder().isInside(center)
        && block.getWorld().getNearbyEntities(BoundingBox.of(block)).isEmpty();
  }

  private boolean restoreReceiver(World world, ArchitectRedstonePulse.Restoration restoration) {
    ArchitectRedstonePulse.Receiver receiver = restoration.receiver();
    return restoreReceiver(world.getBlockAt(receiver.x(), receiver.y(), receiver.z()),
        restoration.originalMaterial());
  }

  private boolean restoreReceiver(Block block, Material originalMaterial) {
    return restoreReceiver(block, originalMaterial, true);
  }

  private boolean restoreReceiver(Block block, Material originalMaterial, boolean clearJournal) {
    try {
      if (block.getType() == Material.REDSTONE_BLOCK) {
        block.setType(originalMaterial, true);
        if (block.getType() == Material.REDSTONE_BLOCK) {
          new IllegalStateException("Architect Redstone Remote source remained powered at "
              + block.getLocation()).printStackTrace();
          return false;
        }
      }
      if (clearJournal) {
        unjournalReceiver(block);
      }
      return true;
    } catch (RuntimeException | Error error) {
      Adapt.error("Failed to restore Architect Redstone Remote source at " + block.getLocation());
      error.printStackTrace();
      return false;
    }
  }

  private boolean isValidHeight(World world, int y) {
    return y >= world.getMinHeight() && y < world.getMaxHeight();
  }

  private boolean claimReceiverForCurrentRuntime(ArchitectRedstonePulse.Receiver receiver) {
    Long owner = SHUTDOWN_RESTORATION_OWNERS.compute(receiver, (key, currentOwner) ->
        currentOwner != null && currentOwner >= runtimeGeneration ? currentOwner : null);
    return owner == null;
  }

  private ShutdownRestorationTask registerShutdownRestoration(
      ArchitectRedstonePulse.Restoration restoration) {
    ArchitectRedstonePulse.Receiver receiver = restoration.receiver();
    ShutdownRestorationTask task = new ShutdownRestorationTask(
        restoration, runtimeGeneration, new CompletableFuture<>());
    SHUTDOWN_RESTORATION_OWNERS.merge(receiver, runtimeGeneration, Long::max);
    return task;
  }

  private void releaseShutdownRestoration(ArchitectRedstonePulse.Receiver receiver,
                                          long ownerGeneration) {
    SHUTDOWN_RESTORATION_OWNERS.remove(receiver, ownerGeneration);
  }

  private boolean isRestorationThreadOwned(Location location) {
    return J.isFoliaThreading()
        ? J.isOwnedByCurrentRegion(location)
        : J.isPrimaryThread();
  }

  private void completeShutdownRestoration(ShutdownRestorationTask task) {
    releaseShutdownRestoration(task.restoration().receiver(), task.ownerGeneration());
    task.completion().complete(null);
  }

  private boolean failShutdownRestoration(ShutdownRestorationTask task, IllegalStateException failure) {
    releaseShutdownRestoration(task.restoration().receiver(), task.ownerGeneration());
    return task.completion().completeExceptionally(failure);
  }

  private void restoreShutdownReceiver(World world, ShutdownRestorationTask task) {
    ArchitectRedstonePulse.Restoration restoration = task.restoration();
    ArchitectRedstonePulse.Receiver receiver = restoration.receiver();
    long ownerGeneration = task.ownerGeneration();
    Long currentOwner = SHUTDOWN_RESTORATION_OWNERS.get(receiver);
    if (currentOwner == null || currentOwner != ownerGeneration) {
      completeShutdownRestoration(task);
      return;
    }
    if (pulses.owns(receiver)) {
      completeShutdownRestoration(task);
      return;
    }
    if (!SHUTDOWN_RESTORATION_OWNERS.remove(receiver, ownerGeneration)) {
      completeShutdownRestoration(task);
      return;
    }
    if (restoreReceiver(world, restoration)) {
      completeShutdownRestoration(task);
      return;
    }
    failShutdownRestoration(task, new IllegalStateException(
        "Architect Redstone Remote could not restore receiver " + receiver));
  }

  private void scheduleRestoration(ShutdownRestorationTask task, int attempt) {
    if (task.completion().isDone()) {
      return;
    }

    ArchitectRedstonePulse.Restoration restoration = task.restoration();
    ArchitectRedstonePulse.Receiver receiver = restoration.receiver();
    World world = Bukkit.getWorld(receiver.worldId());
    if (world == null) {
      failShutdownRestoration(task, new IllegalStateException(
          "Architect Redstone Remote receiver world is unavailable for " + receiver));
      return;
    }

    Location location = new Location(world, receiver.x(), receiver.y(), receiver.z());
    if (isRestorationThreadOwned(location)) {
      if (world.isChunkLoaded(receiver.x() >> 4, receiver.z() >> 4)) {
        restoreShutdownReceiver(world, task);
      } else {
        failShutdownRestoration(task, new IllegalStateException(
            "Architect Redstone Remote receiver chunk is not loaded for " + receiver));
      }
      return;
    }
    if (!Adapt.instance.isEnabled()) {
      failShutdownRestoration(task, new IllegalStateException(
          "Adapt disabled before a region-safe Architect Redstone Remote restoration for "
              + receiver));
      return;
    }
    boolean scheduled = J.runAt(location, () -> {
      if (world.isChunkLoaded(receiver.x() >> 4, receiver.z() >> 4)) {
        restoreShutdownReceiver(world, task);
      } else {
        failShutdownRestoration(task, new IllegalStateException(
            "Architect Redstone Remote receiver chunk is not loaded for " + receiver));
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
        "Failed to schedule a region-safe Architect Redstone Remote restoration at " + receiver
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
              + task.restoration().receiver());
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
              + task.restoration().receiver(), interruption);
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
    ArchitectRedstonePulse.Receiver receiver = task.restoration().receiver();
    String detail = failure == null ? "unknown failure" : failure.getMessage();
    Adapt.error("Architect Redstone Remote restoration failed at " + receiver + ": " + detail);
    if (failure != null) {
      failure.printStackTrace();
    }
  }

  @Override
  public void unregister() {
    super.unregister();
    recoveryQueue.clear();
    queuedRecoveries.clear();
    recoveryScheduleFailures.clear();
    recoveryScheduled.set(false);
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

  private record BindingRequest(Location target, BlockFace face, Location receiver,
                                int handSlot, ItemStack handSnapshot) {
  }

  private record PulseBinding(Location target, Location receiver,
                              ArchitectRedstonePulse.Receiver receiverKey) {
  }

  private record ChunkRecovery(World world, int chunkX, int chunkZ) {
  }

  private record ShutdownRestorationTask(ArchitectRedstonePulse.Restoration restoration,
                                         long ownerGeneration,
                                         CompletableFuture<Void> completion) {
  }

  @ConfigDescription("Use a crafted redstone remote to toggle redstone at a distance.")
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
