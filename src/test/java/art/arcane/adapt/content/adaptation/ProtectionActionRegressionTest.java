package art.arcane.adapt.content.adaptation;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ProtectionActionRegressionTest {
  private static final Path ARCHITECT_WIRELESS_REDSTONE = source("architect/ArchitectWirelessRedstone.java");
  private static final Path AXE_CHOP = source("axe/AxeChop.java");
  private static final Path CHRONOS_ACCELERATE = source("chronos/ChronosAccelerate.java");
  private static final Path CHRONOS_TIME_IN_A_BOTTLE = source("chronos/ChronosTimeInABottle.java");
  private static final Path COMPOST_CASCADE = source("herbalism/HerbalismCompostCascade.java");
  private static final Path HERBALISM_BEE_SHEPHERD = source("herbalism/HerbalismBeeShepherd.java");
  private static final Path HERBALISM_GROWTH_AURA = source("herbalism/HerbalismGrowthAura.java");
  private static final Path HERBALISM_REPLANT = source("herbalism/HerbalismReplant.java");
  private static final Path HERBALISM_SPORE_BLOOM = source("herbalism/HerbalismSporeBloom.java");
  private static final Path PICKAXE_VEINMINER = source("pickaxe/PickaxeVeinminer.java");
  private static final Path RANGED_WEB_BOMB = source("ranged/RangedWebBomb.java");
  private static final Path RIFT_ACCESS = source("rift/RiftAccess.java");
  private static final Path RIFT_CONDUIT = source("rift/RiftConduit.java");

  @Test
  void timeBottleAirTargetsAreAuthorizedBeforeAccelerationAndSpending() throws IOException {
    String handler = section(
        Files.readString(CHRONOS_TIME_IN_A_BOTTLE),
        "public void on(PlayerInteractEvent e)",
        "private void emitBlockUseFx"
    );

    assertThat(handler)
        .contains("action == Action.RIGHT_CLICK_AIR && !ProtectionEventProbe.attemptBlockUse(p, clicked, handSlot)");
    assertOrdered(
        handler,
        "int level = getActiveInteractLevel(p, clicked.getLocation())",
        "double storedSeconds = ChronoTimeBottle.getStoredSeconds(hand)",
        "supportsBlockAcceleration(clicked, clickedState, storedSeconds, level)",
        "ProtectionEventProbe.attemptBlockUse(p, clicked, handSlot)",
        "TimeSpendResult result = accelerateTarget(p, clicked, storedSeconds, level)",
        "ChronoTimeBottle.setStoredSeconds(hand, newStored)"
    );
    String directGrowth = section(
        Files.readString(CHRONOS_TIME_IN_A_BOTTLE),
        "private boolean applyDirectGrowthStep",
        "private boolean generateAuthorizedTree"
    );
    assertOrdered(
        directGrowth,
        "sapling.getStage() < sapling.getMaximumStage()",
        "!canMutateBlock(player, block)",
        "block.setBlockData(data, true)"
    );
    assertOrdered(
        directGrowth,
        "ThreadLocalRandom.current().nextDouble()",
        "!canMutateBlock(player, block)",
        "generateAuthorizedTree(player, block, treeType)"
    );
    String treeGrowth = section(
        Files.readString(CHRONOS_TIME_IN_A_BOTTLE),
        "private boolean generateAuthorizedTree",
        "@Override\n  protected boolean usesLearnerBoundTicking"
    );
    assertOrdered(
        treeGrowth,
        "!preflightTreeRegion(player, block)",
        "List<BlockState> plannedStates = planTree(block, treeType, seed)",
        "!J.isOwnedByCurrentRegion(location)",
        "!canBlockPlace(player, location)",
        "ProtectionEventProbe.dispatch(growEvent)",
        "growEvent.isCancelled()",
        "matchesTreePlan(plannedChanges, planTree(block, treeType, seed))",
        "generateTree(block.getLocation(), new Random(seed), treeType)"
    );
  }

  @Test
  void passiveAccelerationAuthorizesEveryTargetBeforeMutation() throws IOException {
    String source = Files.readString(CHRONOS_ACCELERATE);
    String crop = section(source, "private boolean accelerateBlock", "private int claimSamples");
    String furnace = section(source, "private boolean accelerateFurnace", "private boolean accelerateBrewingStand");
    String brewing = section(source, "private boolean accelerateBrewingStand", "private boolean canAccelerateTarget");
    String authorization = section(source, "private boolean canAccelerateTarget", "private void emitCropFx");

    assertOrdered(
        crop,
        "canAccelerateTarget(player, block, false)",
        "canBlockPlace(player, block.getLocation())",
        "ageable.setAge(",
        "block.setBlockData("
    );
    assertOrdered(furnace, "canAccelerateTarget(player, block, true)", "furnace.setCookTime(", "furnace.update(");
    assertOrdered(brewing, "canAccelerateTarget(player, block, true)", "stand.setBrewingTime(", "stand.update(");
    assertThat(authorization)
        .contains(
            "!player.isOnline()",
            "!J.isOwnedByCurrentRegion(player)",
            "!J.isOwnedByCurrentRegion(block.getLocation())",
            "!canInteract(player, block.getLocation())",
            "container && !canAccessChest(player, block.getLocation())",
            "ProtectionEventProbe.attemptBlockUse(player, block)",
            "return false;"
        );
  }

  @Test
  void passiveHerbalismGrowthAuthorizesEachTargetBeforeCostOrMutation() throws IOException {
    String auraSource = Files.readString(HERBALISM_GROWTH_AURA);
    String auraDispatch = section(auraSource, "private void processMutations", "private void applyMutation");
    String auraMutation = section(auraSource, "private void applyMutation", "private void completeGrowthSample");
    String beeSource = Files.readString(HERBALISM_BEE_SHEPHERD);
    String beeGrowth = section(beeSource, "private void growSample", "private void completeGrowthSample");

    assertOrdered(auraDispatch, "J.runAt(mutation.location", "applyMutation(mutation)");
    assertOrdered(
        auraMutation,
        "!J.isOwnedByCurrentRegion(p)",
        "adaptPlayer == null",
        "block.getBlockData() instanceof Ageable",
        "canInteract(p, block.getLocation())",
        "canBlockPlace(p, block.getLocation())",
        "ProtectionEventProbe.attemptBlockUse(p, block)",
        "ProtectionEventProbe.attemptBlockPlaceProbe(p, block)",
        "payHungerCost(p",
        "block.setBlockData(current, true)"
    );
    assertOrdered(
        beeGrowth,
        "!J.isOwnedByCurrentRegion(player)",
        "block.getBlockData() instanceof Ageable",
        "canInteract(player, block.getLocation())",
        "canBlockPlace(player, block.getLocation())",
        "ProtectionEventProbe.attemptBlockUse(player, block)",
        "ProtectionEventProbe.attemptBlockPlaceProbe(player, block)",
        "chargeGrowthPulse(sample.pulse)",
        "block.setBlockData(currentAfterCharge, true)"
    );
  }

  @Test
  void webBombAuthorizesEachPlacementAtCommit() throws IOException {
    String source = Files.readString(RANGED_WEB_BOMB);
    String authorization = section(source, "private void authorizeImpact", "private WebImpact captureImpact");
    String placement = section(source, "private void scheduleAuthorizedWeb", "private void scheduleWebRemoval");

    assertOrdered(
        authorization,
        "J.isOwnedByCurrentRegion(placement.location())",
        "scheduleAuthorizedWeb(p, placement"
    );
    assertOrdered(
        placement,
        "!J.isOwnedByCurrentRegion(player)",
        "canBlockPlace(player, location)",
        "ProtectionEventProbe.attemptBlockPlaceProbe(player, block)",
        "journalWeb(block, expiresAt)",
        "block.setType(Material.COBWEB, false)",
        "addStat(player, \"ranged.web-bomb.mobs-trapped\", 1)"
    );
  }

  @Test
  void replantAndSporeBloomAuthorizeEveryDirectMutation() throws IOException {
    String replantSource = Files.readString(HERBALISM_REPLANT);
    String replantHandler = section(replantSource, "public void on(PlayerInteractEvent e)", "private boolean hit");
    String replantHit = section(replantSource, "private boolean hit", "private boolean consumeSeed");
    String sporeSource = Files.readString(HERBALISM_SPORE_BLOOM);
    String sporeStart = section(sporeSource, "private void startBloom", "private List<Block> buildSpiderPath");
    String sporeMutation = section(sporeSource, "private int spreadAt", "private Block resolveTopSurfaceSoil");
    String sporeAttempt = section(sporeSource, "private void attemptBloom", "private boolean chargeCatalyst");

    assertOrdered(
        replantHandler,
        "action == Action.RIGHT_CLICK_AIR && J.isFoliaThreading()",
        "p.getTargetBlockExact(5)",
        "J.isOwnedByCurrentRegion(target.getLocation(), footprint, footprint)",
        "hit(p, target, harvestTool)",
        "damageOffHand(p",
        "p.setCooldown("
    );
    assertOrdered(
        replantHit,
        "!J.isOwnedByCurrentRegion(p)",
        "ProtectionEventProbe.attemptBlockBreakProbe(p, b)",
        "b.getDrops(harvestTool, p)",
        "ProtectionEventProbe.attemptBlockPlaceProbe(p, b)",
        "b.getType() != cropType",
        "b.setBlockData(commitData, true)"
    );
    assertOrdered(
        sporeAttempt,
        "double ownershipRadius = Math.ceil(radius + 0.35D)",
        "J.isOwnedByCurrentRegion(center.getLocation(), ownershipRadius, ownershipRadius)",
        "List<Block> path = buildSpiderPath(",
        "BloomCharge charge = new BloomCharge(",
        "startBloom(player, center, catalyst, spreadSurface, level, path, ownershipRadius, charge)"
    );
    assertOrdered(
        sporeStart,
        "!J.isOwnedByCurrentRegion(player)",
        "spreadAt(player, cell",
        "bloomTask[0].run()"
    );
    assertOrdered(
        sporeMutation,
        "ProtectionEventProbe.attemptBlockBreakProbe(player, ground)",
        "ProtectionEventProbe.attemptBlockPlaceProbe(player, ground)",
        "ensureCharged.getAsBoolean()",
        "ground.setType(spreadSurface, false)",
        "ProtectionEventProbe.attemptBlockBreakProbe(player, above)",
        "ProtectionEventProbe.attemptBlockPlaceProbe(player, above)",
        "ensureCharged.getAsBoolean()",
        "above.setType(replacement, false)"
    );
  }

  @Test
  void wirelessRedstoneAuthorizesTheCompletePulseFootprint() throws IOException {
    String source = Files.readString(ARCHITECT_WIRELESS_REDSTONE);
    String validation = section(
        source,
        "private void validateTargetAndSchedulePulse",
        "private void authorizeAndSchedulePulse"
    );
    String pulse = section(source, "private void beginPulse", "private boolean authorizeSnapshots");
    String authorization = section(source, "private boolean authorizeSnapshots", "private List<ArchitectRedstonePulse.Snapshot> planPulse");
    String acceptance = section(source, "private void acceptPulse", "private void rejectPulse");

    assertOrdered(
        validation,
        "!J.isOwnedByCurrentRegion(player)",
        "J.isOwnedByCurrentRegion(binding.target(), 1.0D, 1.0D)",
        "binding.target().getBlock().getType()"
    );
    assertOrdered(
        pulse,
        "J.isOwnedByCurrentRegion(binding.target(), 1.0D, 1.0D)",
        "planPulse(targetBlock, binding.face())",
        "authorizeSnapshots(player, targetBlock.getWorld(), snapshots)",
        "pulses.begin(binding.emitter(), snapshots)",
        "applySnapshots(targetBlock.getWorld(), activation.snapshots(), true)",
        "acceptPulse(player, binding.target())"
    );
    assertOrdered(
        authorization,
        "canInteract(player, location)",
        "canBlockPlace(player, location)",
        "ProtectionEventProbe.attemptBlockUse(player, block)"
    );
    assertOrdered(
        acceptance,
        "pendingPulsePlayers.remove(playerId)",
        "pulseCd.mark(player, getConfig().cooldown)",
        "showPulseSuccess(player, target)"
    );
  }

  @Test
  void portkeyBindingAuthorizesTheWholeContainerBeforeCostOrLink() throws IOException {
    String source = Files.readString(RIFT_ACCESS);
    String interaction = section(source, "private void handleEnderPearlInteraction", "private void linkPearl");
    String link = section(source, "private void linkPearl", "static boolean shouldReplaceBoundPearl");

    assertOrdered(
        interaction,
        "J.isOwnedByCurrentRegion(target.getLocation())",
        "isStorage(target.getBlockData())",
        "ProtectionEventProbe.containerBlocks(target, inventory)",
        "canOpenContainers(player, containerBlocks, target.getLocation())",
        "linkPearl(player, target, event)"
    );
    assertThat(interaction).doesNotContain("payItemCost(");
    assertThat(link).contains("payItemCost(player, \"bind\"");
  }

  @Test
  void advancedChestNoOpCannotActivateAStaleInventoryView() throws IOException {
    String completeOpen = section(
        Files.readString(RIFT_ACCESS),
        "private void completeRemoteOpen",
        "private void closeViewIfCurrent"
    );

    assertOrdered(
        completeOpen,
        "Inventory previousTop = player.getOpenInventory().getTopInventory()",
        "openPage(player, 1)",
        "openedView == null || openedView.getTopInventory() == previousTop",
        "cancelSession(session)",
        "activeViews.activate(session, view.getTopInventory())",
        "addStat(player, \"rift.access.remote-opens\", 1)"
    );
  }

  @Test
  void conduitGesturesHonorTheOriginalBlockDenialBeforeCaptureOrBind() throws IOException {
    String handler = section(
        Files.readString(RIFT_CONDUIT),
        "public void on(PlayerInteractEvent e)",
        "static ConduitGesture resolveGesture"
    );
    String bind = section(handler, "case BIND ->", "case CAPTURE ->");
    String capture = section(handler, "case CAPTURE ->", "    }\n  }");

    assertOrdered(
        handler,
        "boolean blockUseDenied = clicked != null && e.useInteractedBlock() == Event.Result.DENY",
        "switch (resolveGesture"
    );
    assertOrdered(bind, "e.setCancelled(true)", "if (blockUseDenied", "canAccessContainer(p, clicked)", "completeBind(");
    assertOrdered(capture, "e.setCancelled(true)", "if (!blockUseDenied", "canAccessContainer(p, clicked)", "beginCapture(");
  }

  @Test
  void conduitReauthorizesDeferredEndpointsAndRestoresRejectedTransfers() throws IOException {
    String source = Files.readString(RIFT_CONDUIT);
    String sourceWrite = section(source, "private void writeSourceThenPartner", "private void finishBind");
    String partnerWrite = section(source, "private void finishBind", "private void confirmSourceAndFinalize");
    String confirmation = section(source, "private void confirmSourceAndFinalize", "private void finalizeBindOwned");
    String finalization = section(source, "private void finalizeBindOwned", "private boolean settleBindReservation");
    String extraction = section(source, "private void flowFromSource", "private void depositToPartnerSafely");
    String deposit = section(source, "private void depositToPartner(Player", "private void restoreToSource");
    String authorization = section(source, "private boolean canUseContainer", "private LinkRef readLink");

    assertOrdered(sourceWrite, "canUseContainer(operation.player(), aContainer)", "snapshotLink(aContainer)", "writeLink(aContainer");
    assertOrdered(partnerWrite, "canUseContainer(operation.player(), bContainer)", "snapshotLink(bContainer)", "writeLink(bContainer");
    assertOrdered(confirmation, "canUseContainer(operation.player(), source)", "linkMatches(source", "finalizeBindOwned(operation)");
    assertOrdered(finalization, "settleBindReservation", "rollbackEndpoints(operation)");
    assertOrdered(extraction, "canUseContainer(p, source)", "extractItems(source.getInventory(), throughput)");
    assertOrdered(
        extraction,
        "extractItems(source.getInventory(), throughput)",
        "boolean recoveryScheduled = J.runAt(sourceLoc",
        "if (!recoveryScheduled)",
        "dropAt(sourceLoc, addItems(source.getInventory(), moving))",
        "loadChunkAsync(partnerLoc"
    );
    assertOrdered(deposit, "canUseContainer(p, partner)", "restoreToSource(sourceLoc, moving)", "addItems(partner.getInventory(), moving)");
    assertThat(authorization)
        .contains(
            "!player.isOnline()",
            "!J.isOwnedByCurrentRegion(player)",
            "!J.isOwnedByCurrentRegion(block.getLocation())",
            "!canAccessChest(player, block.getLocation())",
            "ProtectionEventProbe.attemptContainerOpen(player, blocks)",
            "return false;"
        );
  }

  @Test
  void nativeBreakCountersOnlyAdvanceForSuccessfulPlayerBreaks() throws IOException {
    String axeSource = Files.readString(AXE_CHOP);
    String axeHandler = section(axeSource, "public void on(PlayerInteractEvent e)", "private int getRange");
    String axeBreak = section(axeSource, "private boolean breakStuff", "@ConfigDescription");
    String pickaxeSource = Files.readString(PICKAXE_VEINMINER);
    String normalVein = section(pickaxeSource, "public void on(BlockBreakEvent e)", "private int distanceSquared");
    String normalFeedback = section(pickaxeSource, "private void completeVeinFeedback", "private int distanceSquared");
    String hiddenVein = section(
        pickaxeSource,
        "private void chainHiddenVein",
        "@ConfigDescription"
    );

    assertOrdered(
        axeHandler,
        "action == Action.RIGHT_CLICK_AIR && J.isFoliaThreading()",
        "p.getTargetBlockExact(5)"
    );
    assertOrdered(
        axeHandler,
        "if (breakStuff(target, getRange(getLevel(p)), p))",
        "logsChopped++",
        "if (logsChopped > 0)",
        "addStat(p, \"axe.chop.trees-felled\", 1)"
    );
    assertOrdered(
        axeBreak,
        "VEIN_MINED.add(last)",
        "return player.breakBlock(last)",
        "finally {",
        "VEIN_MINED.remove(last)"
    );
    assertOrdered(
        normalVein,
        "J.runEntity(p, () -> mineConnectedVein(p, block, targetFamily, siblings), 1)",
        "veinFamily(origin.getType()) == targetFamily",
        "int successful = 1",
        "ProtectionEventProbe.attemptBlockBreak(player, sibling)",
        "successful++",
        "completeVeinFeedback(player, origin, successful)"
    );
    assertThat(normalFeedback).contains("addStat(player, \"pickaxe.veinminer.ores-veinmined\", successful)");
    assertOrdered(
        hiddenVein,
        "block.getType() == originType",
        "int successful = 0",
        "ProtectionEventProbe.attemptBlockBreak(p, target)",
        "successful++",
        "if (successful > 0)",
        "addStat(p, \"pickaxe.veinminer.ores-veinmined\", successful)"
    );
    assertThat(hiddenVein).contains("finally {", "VEIN_MINED.remove(target)");
  }

  @Test
  void compostPartialPickupReportsTheUnconsumedRemainderBeforeMutation() throws IOException {
    String source = Files.readString(COMPOST_CASCADE);
    String handler = section(source, "public void on(PlayerInteractEvent e)", "private void processDroppedItems");
    String droppedItems = section(
        source,
        "private void processDroppedItems",
        "private void scanGrowth"
    );
    String growth = section(source, "private void scanGrowth", "private void harvestMatureCrop");
    String harvest = section(source, "private void harvestMatureCrop", "private void processInventoryItems");

    assertOrdered(
        handler,
        "e.isCancelled()",
        "e.useInteractedBlock() == Event.Result.DENY",
        "ProtectionEventProbe.attemptBlockUse(p, composter)",
        "processDroppedItems(p, world"
    );

    assertOrdered(
        droppedItems,
        "!J.isOwnedByCurrentRegion(item)",
        "canSnatchItem(p, item)",
        "int transferable = compostProcessCount(stack.getAmount()",
        "int remaining = stack.getAmount() - transferable",
        "ProtectionEventProbe.attemptItemPickup(p, item, remaining)",
        "ItemStack current = item.getItemStack()",
        "if (compostStack(p, stack, state, fillChance) <= 0)",
        "item.remove()",
        "item.setItemStack(stack)"
    );
    assertThat(source)
        .contains("payItemCost(p, \"compost\", new ItemStack(stack.getType()), processCount");
    assertOrdered(
        growth,
        "!J.isOwnedByCurrentRegion(new Location(world, blockX, blockY, blockZ))",
        "Block b = world.getBlockAt(blockX, blockY, blockZ)",
        "BlockData data = b.getBlockData()",
        "Material expectedLeafType = b.getType()",
        "ProtectionEventProbe.attemptBlockBreakProbe(p, b)",
        "isCurrentLeaf(p, b, expectedLeafType)",
        "isCurrentLeaf(p, b, expectedLeafType)",
        "compostStack(p, leaves, state, leafFillChance)",
        "isCurrentLeaf(p, b, expectedLeafType)",
        "b.setType(Material.AIR, false)"
    );
    assertOrdered(
        harvest,
        "Material expectedCropType = b.getType()",
        "ProtectionEventProbe.attemptBlockBreakProbe(p, b)",
        "ProtectionEventProbe.attemptBlockPlaceProbe(p, b)",
        "currentCropData(p, b, expectedCropType, true)",
        "b.getDrops()",
        "currentCropData(p, b, expectedCropType, true)",
        "composted += compostStack(p, drop, state, fillChance)",
        "Ageable commitData = currentCropData(p, b, expectedCropType, true)",
        "commitData.setAge(0)",
        "b.setBlockData(commitData, true)"
    );

    String maturation = section(source, "private int matureCrops", "private int compostStack");
    assertOrdered(
        maturation,
        "Material expectedCropType = b.getType()",
        "currentCropData(p, b, expectedCropType, false)",
        "ProtectionEventProbe.attemptBlockPlaceProbe(p, b)",
        "Ageable commitData = currentCropData(p, b, expectedCropType, false)",
        "commitData.setAge(",
        "b.setBlockData(commitData, true)"
    );
  }

  private static Path source(String relativePath) {
    return Path.of("src/main/java/art/arcane/adapt/content/adaptation").resolve(relativePath);
  }

  private static String section(String source, String start, String end) {
    int startIndex = source.indexOf(start);
    int endIndex = source.indexOf(end, startIndex + start.length());
    assertThat(startIndex).as("section start: %s", start).isGreaterThanOrEqualTo(0);
    assertThat(endIndex).as("section end: %s", end).isGreaterThan(startIndex);
    return source.substring(startIndex, endIndex);
  }

  private static void assertOrdered(String source, String... fragments) {
    int previous = -1;
    for (String fragment : fragments) {
      int current = source.indexOf(fragment, previous + 1);
      assertThat(current).as("ordered fragment: %s", fragment).isGreaterThan(previous);
      previous = current;
    }
  }
}
