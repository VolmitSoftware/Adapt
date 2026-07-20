package art.arcane.adapt.content.adaptation.architect;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ArchitectWirelessRedstoneTargetTest {
  private static final Path ADAPT_SOURCE = Path.of("src/main/java/art/arcane/adapt/Adapt.java");
  private static final Path ENGLISH_LOCALE = Path.of("src/main/resources/en_US.toml");
  private static final Path REMOTE_SOURCE = Path.of(
      "src/main/java/art/arcane/adapt/content/adaptation/architect/ArchitectWirelessRedstone.java"
  );
  private static final Path PULSE_SOURCE = Path.of(
      "src/main/java/art/arcane/adapt/content/adaptation/architect/ArchitectRedstonePulse.java"
  );
  private static final Path ITEM_SOURCE = Path.of(
      "src/main/java/art/arcane/adapt/content/item/BoundRedstoneTorch.java"
  );

  @Test
  void bindingDoesNotRestrictTheSelectedBlockMaterial() throws IOException {
    String source = Files.readString(REMOTE_SOURCE);
    String linkTorch = method(source, "private void linkTorch", "private void triggerPulse");

    assertThat(linkTorch).contains(
        "BoundRedstoneTorch.setData(hand, targetLocation, binding.face())",
        "BoundRedstoneTorch.withData(targetLocation, binding.face())"
    );
    assertThat(linkTorch).doesNotContain("Material.TARGET");
  }

  @Test
  void bindingStagesBlockReadsAndItemMutationAcrossTheirOwningRegions() throws IOException {
    String source = Files.readString(REMOTE_SOURCE);
    String bindingFlow = method(source, "private void handleLeftClick", "private void handleRightClick");

    assertThat(bindingFlow).contains(
        "J.runAt(binding.target(), () -> validateBindingTarget(player, binding))",
        "binding.target().getBlock().getType().isAir()",
        "J.runAt(binding.receiver(), () -> validateBindingReceiver(player, binding))",
        "isReceiverAvailable(binding.receiver().getBlock())",
        "J.runEntity(player, () -> authorizeAndLinkTorch(player, binding))",
        "player.getInventory().getHeldItemSlot() != binding.handSlot()",
        "hand.equals(binding.handSnapshot())",
        "resolveInteractContext(player, binding.target())",
        "canBlockPlace(player, binding.receiver())"
    );
    assertThat(bindingFlow).doesNotContain("player.rayTraceBlocks", "private boolean canBind");
  }

  @Test
  void itemLoreExplainsThatAnyBlockCanBeBound() throws IOException {
    String item = localeSection(Files.readString(ENGLISH_LOCALE), "items.bound_redstone_torch");

    assertThat(item).contains("any block", "bound face", "4 ticks");
    assertThat(item).doesNotContain("'Target' Block", "1-Tick Redstone pulse", "2-Tick Redstone pulse");
  }

  @Test
  void bindingPersistsTheClickedFaceWithoutACompatibilityFallback() throws IOException {
    String source = Files.readString(ITEM_SOURCE);

    assertThat(source).contains(
        "private Location location;",
        "private BlockFace face;",
        "new Data(target, face)"
    );
    assertThat(source).doesNotContain("new Data(t)", "getLocation(ItemStack");
  }

  @Test
  void pulseRegistryUsesFourTicksAndGenerationGuardedCleanup() throws IOException {
    String source = Files.readString(PULSE_SOURCE);

    assertThat(source).contains(
        "static final int PULSE_TICKS = 4;",
        "ACTIVE_RECEIVERS.put(receiver, lease)",
        "ACTIVE_RECEIVERS.remove(activation.receiver(), activation.lease())"
    );
  }

  @Test
  void activationRevalidatesTheBoundBlockBeforePlacingTheSource() throws IOException {
    String source = Files.readString(REMOTE_SOURCE);

    assertThat(source).contains(
        "J.runAt(binding.target(), () -> validateTargetAndSchedulePulse(player, binding))",
        "binding.target().getBlock().getType().isAir()",
        "J.runEntity(player, () -> authorizeAndSchedulePulse(player, binding))",
        "canInteract(player, binding.target())",
        "canBlockPlace(player, binding.receiver())",
        "J.runAt(binding.receiver(), () -> beginPulse(player, binding))"
    );
    assertThat(method(source, "private void triggerPulse", "private void validateTargetAndSchedulePulse"))
        .doesNotContain("canInteract(", "canBlockPlace(");
  }

  @Test
  void pulseUsesARealAdjacentSourceAndNeverSwapsTheSelectedBlockThroughNms() throws IOException {
    String remote = Files.readString(REMOTE_SOURCE);
    String pulse = Files.readString(PULSE_SOURCE);

    assertThat(remote).contains(
        "receiverBlock.setType(Material.REDSTONE_BLOCK, true)",
        "block.setType(originalMaterial, true)",
        "J.runAt(binding.receiver(), () -> finishPulse(activation), ArchitectRedstonePulse.PULSE_TICKS)"
    );
    assertThat(pulse).doesNotContain("net.minecraft", "CraftWorld", "LevelChunkSection");
  }

  @Test
  void shutdownRestorationYieldsToNewerRuntimeOwnership() throws IOException {
    String source = Files.readString(REMOTE_SOURCE);

    assertThat(source).contains(
        "SHUTDOWN_RESTORATION_OWNERS.merge(receiver, runtimeGeneration, Long::max)",
        "currentOwner != null && currentOwner >= runtimeGeneration ? currentOwner : null",
        "!claimReceiverForCurrentRuntime(binding.receiverKey())",
        "!claimReceiverForCurrentRuntime(receiver)",
        "if (pulses.owns(receiver))",
        "SHUTDOWN_RESTORATION_OWNERS.remove(receiver, ownerGeneration)",
        "restoreShutdownReceiver(world, task)"
    );
  }

  @Test
  void shutdownOnlyRestoresDirectlyFromAnOwnedExecutionContext() throws IOException {
    String source = Files.readString(REMOTE_SOURCE);
    String scheduling = method(source, "private void scheduleRestoration", "@Override\n  public void unregister");

    assertThat(scheduling).contains(
        "isRestorationThreadOwned(location)",
        "if (!Adapt.instance.isEnabled())",
        "J.runAt(location"
    );
    assertThat(source).contains(
        "J.isOwnedByCurrentRegion(location)",
        "J.isPrimaryThread()"
    );
  }

  @Test
  void managedPreUnloadWaitsForRegionRestorationsWithOneBoundedDeadline() throws IOException {
    String source = Files.readString(REMOTE_SOURCE);
    String adaptSource = Files.readString(ADAPT_SOURCE);
    String unregister = method(source, "public void unregister()", "private record BindingRequest");
    String waiting = method(source, "private void awaitShutdownRestorations",
        "private void timeoutShutdownRestorations");

    assertThat(source).contains(
        "SHUTDOWN_RESTORATION_TIMEOUT_MILLIS = 3000L",
        "CompletableFuture<Void> completion",
        "Timed out after \" + SHUTDOWN_RESTORATION_TIMEOUT_MILLIS",
        "Failed to schedule a region-safe Architect Redstone Remote restoration at"
    );
    assertThat(unregister).contains(
        "long deadlineNanos = System.nanoTime()",
        "restorations.add(registerShutdownRestoration(restoration))",
        "if (System.nanoTime() >= deadlineNanos)",
        "scheduleRestoration(restoration, 1)",
        "awaitShutdownRestorations(restorations, deadlineNanos)"
    );
    assertThat(waiting).contains(
        "remainingNanos = deadlineNanos - System.nanoTime()",
        "task.completion().get(remainingNanos, TimeUnit.NANOSECONDS)"
    );
    assertThat(adaptSource).contains(
        "@EventHandler(priority = EventPriority.MONITOR)",
        "public void onPluginDisable(PluginDisableEvent event)",
        "if (event.getPlugin() != this)",
        "stop();"
    );
  }

  private static String localeSection(String locale, String key) {
    String header = "[" + key + "]";
    int start = locale.indexOf(header);
    if (start < 0) {
      throw new IllegalArgumentException("Missing locale section: " + key);
    }
    int end = locale.indexOf("\n[", start + header.length());
    return end < 0 ? locale.substring(start) : locale.substring(start, end);
  }

  private static String method(String source, String startMarker, String endMarker) {
    int start = source.indexOf(startMarker);
    int end = source.indexOf(endMarker, start);
    if (start < 0 || end < 0) {
      throw new IllegalArgumentException("Missing method markers: " + startMarker + ", " + endMarker);
    }
    return source.substring(start, end);
  }
}
