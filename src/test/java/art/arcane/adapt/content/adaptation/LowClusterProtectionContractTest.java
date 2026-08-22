package art.arcane.adapt.content.adaptation;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LowClusterProtectionContractTest {
  private static final Path ARCHITECT_FOUNDATION = source("architect/ArchitectFoundation.java");
  private static final Path ARCHITECT_PLACEMENT = source("architect/ArchitectPlacement.java");
  private static final Path CRAFTING_DECONSTRUCTION = source("crafting/CraftingDeconstruction.java");
  private static final Path HERBALISM_SEED_SOWER = source("herbalism/HerbalismSeedSower.java");
  private static final Path SEABORNE_CORAL_GARDENER = source("seaborrne/SeaborneCoralGardener.java");

  @Test
  void deconstructionAuthorizesPickupBeforeReplacingTheItemOrChargingTheTool() throws IOException {
    String source = Files.readString(CRAFTING_DECONSTRUCTION);
    String handler = section(source, "public void on(PlayerInteractEvent e)", "private void processItemInteraction");
    String interaction = section(
        source,
        "private void processItemInteraction",
        "@ConfigDescription"
    );

    assertOrdered(
        handler,
        "J.isOwnedByCurrentRegion(player)",
        "player.getInventory().getItemInMainHand()",
        "J.isOwnedByCurrentRegion(eyeLocation, 6.0D, 6.0D)",
        "rayTraceEntities("
    );
    assertOrdered(
        interaction,
        "J.isOwnedByCurrentRegion(itemEntity)",
        "canSnatchItem(player, itemEntity)",
        "itemEntity.getItemStack().clone()",
        "ProtectionEventProbe.attemptItemPickup(player, itemEntity, 0)",
        "ItemStack current = itemEntity.getItemStack()",
        "itemEntity.setItemStack(offering)",
        "addStat(player, \"crafting.deconstruction.items-deconstructed\", 1)",
        "damageable.setDamage(newDamage)"
    );
  }

  @Test
  void buildersWandAuthorizesEveryOwnedReplicaBeforeCostAndMutation() throws IOException {
    String source = Files.readString(ARCHITECT_PLACEMENT);
    String placement = section(source, "public void on(BlockPlaceEvent e)", "private boolean ownsPlacementFootprint");
    String footprint = section(source, "private boolean ownsPlacementFootprint", "private boolean ownsBlock");

    assertOrdered(
        placement,
        "ownsPlacementFootprint(p, e.getBlock(), blocks)",
        "first.getType()",
        "canBlockPlace(p, relative.getLocation())",
        "ProtectionEventProbe.attemptBlockPlaceProbe(p, relative)",
        "payItemCost(p, \"block\"",
        "relative.setBlockData(sourceData)"
    );
    assertOrdered(footprint, "ownsBlock(player, source)", "source.getRelative(face)");
  }

  @Test
  void foundationOwnsTheBlockAndQueryFootprintBeforeAuthorizationAndJournaling() throws IOException {
    String source = Files.readString(ARCHITECT_FOUNDATION);
    String targeting = section(source, "private void addOwnedFoundationTarget", "// prevent piston");
    String foundation = section(source, "public boolean addFoundation", "public void removeFoundation");

    assertOrdered(targeting, "J.isOwnedByCurrentRegion(target)", "world.getBlockAt(x, y, z)");
    assertOrdered(
        foundation,
        "FoliaScheduler.isOwnedByCurrentRegion(location)",
        "block.getType().isAir()",
        "J.isOwnedByCurrentRegion(center, 0.5D, 0.5D)",
        ".getNearbyEntities(center",
        "canBlockPlace(player, location)",
        "ProtectionEventProbe.attemptBlockPlaceProbe(player, block)",
        "journalFoundation(block)",
        "block.setType(Material.TINTED_GLASS, false)",
        "J.runAt(location, () -> removeFoundation(block), durationTicks)"
    );
    assertOrdered(
        foundation,
        "if (!cleanupScheduled)",
        "block.setType(Material.AIR, false)",
        "unjournalFoundation(block)"
    );
  }

  @Test
  void coralGrowthHonorsTheRealClickAndProbesTheTargetBeforeTransactionalMutation() throws IOException {
    String source = Files.readString(SEABORNE_CORAL_GARDENER);
    String interaction = section(source, "public void on(PlayerInteractEvent e)", "private boolean growCoral");
    String growth = section(source, "private boolean growCoral", "private boolean ownsCoralTarget");

    assertOrdered(
        interaction,
        "e.useInteractedBlock() == Event.Result.DENY",
        "ownsCoralTarget(p, clicked)",
        "isFadeableCoral(clicked.getType())"
    );
    assertOrdered(
        growth,
        "ownsCoralTarget(p, target)",
        "target.getType() != Material.WATER",
        "canBlockPlace(p, target.getLocation())",
        "ProtectionEventProbe.attemptBlockPlaceProbe(p, target)",
        "target.setType(coral, false)",
        "consumeBoneMeal(p)",
        "target.setType(Material.WATER, false)",
        "trackCoral(target.getLocation(), level)"
    );
  }

  @Test
  void seedSowerProbesOwnedTargetsBeforeChargeAndKeepsRollbackUnprobed() throws IOException {
    String source = Files.readString(HERBALISM_SEED_SOWER);
    String interaction = section(source, "public void on(PlayerInteractEvent e)", "private int plantNearby");
    String transaction = section(source, "private int plantNearby", "private List<Block> findPlantingTargets");
    String targeting = section(source, "private List<Block> findPlantingTargets", "private int plantTargets");
    String rollback = section(source, "private void rollbackPlanting", "private boolean ownsPlantingTarget");

    assertOrdered(interaction, "if (J.isFoliaThreading())", "p.getTargetBlockExact(5)");
    assertOrdered(
        transaction,
        "findPlantingTargets(p, origin, seedType",
        "payItemCostDeferred(",
        "plantTargets(p, targets, cropType)",
        "rollbackPlanting(targets, planted, cropType)"
    );
    assertOrdered(
        targeting,
        "ownsPlantingTarget(p, baseLocation)",
        "world.getBlockAt(blockX, y, blockZ)",
        "ownsPlantingTarget(p, cropLocation)",
        "world.getBlockAt(blockX, y + 1, blockZ)",
        "canBlockPlace(p, cropLocation)",
        "ProtectionEventProbe.attemptBlockPlaceProbe(p, crop)",
        "targets.add(crop)"
    );
    assertOrdered(
        rollback,
        "J.isOwnedByCurrentRegion(crop.getLocation())",
        "crop.getType() == cropType",
        "crop.setType(Material.AIR)"
    );
    assertThat(rollback).doesNotContain("ProtectionEventProbe");
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
