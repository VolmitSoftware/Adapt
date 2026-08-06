package art.arcane.adapt.content.adaptation.architect;

import art.arcane.adapt.localization.AdaptMessages;
import art.arcane.volmlib.util.localization.TextValue;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ArchitectWirelessRedstoneTargetTest {
  private static final Path ADAPT_SOURCE = Path.of("src/main/java/art/arcane/adapt/Adapt.java");
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
        "BoundRedstoneTorch.setData(hand, binding.target(), binding.face())",
        "BoundRedstoneTorch.withData(binding.target(), binding.face())"
    );
    assertThat(linkTorch).doesNotContain("Material.TARGET");
  }

  @Test
  void bindingOutputAndSuccessEffectsRequireAnAllowedPayment() throws IOException {
    assertThat(ArchitectWirelessRedstone.shouldCreateProviderBoundOutput(true, false))
        .isTrue();
    assertThat(ArchitectWirelessRedstone.shouldCreateProviderBoundOutput(true, true))
        .isFalse();
    assertThat(ArchitectWirelessRedstone.shouldCreateProviderBoundOutput(false, false))
        .isFalse();

    String source = Files.readString(REMOTE_SOURCE);
    String linkTorch = method(source, "private void linkTorch", "private void handleRightClick");
    int payment = linkTorch.indexOf("boolean paymentAllowed = payItemCost");
    int denied = linkTorch.indexOf("if (!paymentAllowed)");
    int providerOutput = linkTorch.indexOf("shouldCreateProviderBoundOutput");
    int successFx = linkTorch.indexOf("Location targetCenter");

    assertThat(payment).isGreaterThanOrEqualTo(0);
    assertThat(denied).isGreaterThan(payment);
    assertThat(providerOutput).isGreaterThan(denied);
    assertThat(successFx).isGreaterThan(providerOutput);
  }

  @Test
  void bindingStagesBlockReadsAndItemMutationAcrossTheirOwningRegions() throws IOException {
    String source = Files.readString(REMOTE_SOURCE);
    String bindingFlow = method(source, "private void handleLeftClick", "private void linkTorch");

    assertThat(bindingFlow).contains(
        "J.runAt(binding.target(), () -> validateBindingTarget(player, binding))",
        "binding.target().getBlock().getType().isAir()",
        "J.runEntity(player, () -> authorizeAndLinkTorch(player, binding))",
        "player.getInventory().getHeldItemSlot() != binding.handSlot()",
        "hand.equals(binding.handSnapshot())",
        "resolveInteractContext(player, binding.target())",
        "canBlockPlace(player, binding.target())"
    );
    assertThat(bindingFlow).doesNotContain(
        "player.rayTraceBlocks",
        "private boolean canBind",
        "isReceiverAvailable"
    );
  }

  @Test
  void itemLoreExplainsThatAnyBlockCanBeBound() {
    TextValue usage1 = (TextValue) AdaptMessages.require("items.bound_redstone_torch.usage1").englishValue();
    TextValue usage2 = (TextValue) AdaptMessages.require("items.bound_redstone_torch.usage2").englishValue();
    String item = usage1.template() + " " + usage2.template();

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
        "ACTIVE_EMITTERS.put(emitter, lease)",
        "ACTIVE_EMITTERS.remove(activation.emitter(), activation.lease())"
    );
  }

  @Test
  void activationRevalidatesTheBoundBlockBeforeEnergizingIt() throws IOException {
    String source = Files.readString(REMOTE_SOURCE);

    assertThat(source).contains(
        "J.runAt(binding.target(), () -> validateTargetAndSchedulePulse(player, binding))",
        "binding.target().getBlock().getType().isAir()",
        "J.runEntity(player, () -> authorizeAndSchedulePulse(player, binding))",
        "canInteract(player, binding.target())",
        "canBlockPlace(player, binding.target())",
        "J.runAt(binding.target(), () -> beginPulse(player, binding))"
    );
    assertThat(method(source, "private void triggerPulse", "private void validateTargetAndSchedulePulse"))
        .doesNotContain("canInteract(", "canBlockPlace(");
  }

  @Test
  void thePulseNeverPlacesSwapsOrJournalsABlock() throws IOException {
    String remote = Files.readString(REMOTE_SOURCE);
    String pulse = Files.readString(PULSE_SOURCE);

    assertThat(remote).contains(
        "block.setBlockData(target, true)",
        "J.runAt(binding.target(), () -> finishPulse(activation), ArchitectRedstonePulse.PULSE_TICKS)"
    );
    assertThat(remote).doesNotContain(
        "REDSTONE_BLOCK",
        ".setType(",
        "PersistentDataType",
        "isProtectedReceiver"
    );
    assertThat(pulse).doesNotContain("net.minecraft", "CraftWorld", "LevelChunkSection", "Material");
  }

  @Test
  void restoringOnlyRewritesBlocksThatAreStillTheOnesThePulseChanged() throws IOException {
    String source = Files.readString(REMOTE_SOURCE);
    String apply = method(source, "private void applySnapshots", "static BlockData poweredCopy");

    assertThat(apply).contains(
        "BlockData target = powered ? snapshot.powered() : snapshot.original();",
        "BlockData expected = powered ? snapshot.original() : snapshot.powered();",
        "if (!block.getBlockData().matches(expected)) {"
    );
  }

  @Test
  void shutdownRestorationYieldsToNewerRuntimeOwnership() throws IOException {
    String source = Files.readString(REMOTE_SOURCE);

    assertThat(source).contains(
        "SHUTDOWN_RESTORATION_OWNERS.merge(emitter, runtimeGeneration, Long::max)",
        "currentOwner != null && currentOwner >= runtimeGeneration ? currentOwner : null",
        "!claimEmitterForCurrentRuntime(binding.emitter())",
        "if (pulses.owns(emitter))",
        "SHUTDOWN_RESTORATION_OWNERS.remove(emitter, ownerGeneration)",
        "restoreShutdownEmitter(world, task)"
    );
  }

  @Test
  void shutdownOnlyRestoresDirectlyFromAnOwnedExecutionContext() throws IOException {
    String source = Files.readString(REMOTE_SOURCE);
    String scheduling = method(source, "private void scheduleRestoration", "private void awaitShutdownRestorations");

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

  private static String method(String source, String startMarker, String endMarker) {
    int start = source.indexOf(startMarker);
    int end = source.indexOf(endMarker, start);
    if (start < 0 || end < 0) {
      throw new IllegalArgumentException("Missing method markers: " + startMarker + ", " + endMarker);
    }
    return source.substring(start, end);
  }
}
