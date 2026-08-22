package art.arcane.adapt.content.adaptation;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SecondaryBlockBreakProtectionTest {
  private static final Path AXE_LEAF_VEINMINER = source("axe/AxeLeafVeinminer.java");
  private static final Path AXE_WOOD_VEINMINER = source("axe/AxeWoodVeinminer.java");
  private static final Path EXCAVATION_BURROW = source("excavation/ExcavationBurrow.java");
  private static final Path EXCAVATION_TUNNELER = source("excavation/ExcavationTunneler.java");
  private static final Path PICKAXE_CHISEL = source("pickaxe/PickaxeChisel.java");
  private static final Path PICKAXE_TUNNEL_BORE = source("pickaxe/PickaxeTunnelBore.java");

  @Test
  void axeVeinminersAuthorizeEachCommitAndCountOnlySuccessfulBreaks() throws IOException {
    String leafSource = Files.readString(AXE_LEAF_VEINMINER).replace("\r\n", "\n");
    String leafHandler = section(leafSource, "public void on(BlockBreakEvent e)", "private void mineLeaves");
    String leafCommit = section(leafSource, "private void mineLeaves", "private void emitLeafFeedback");
    String leafFeedback = section(leafSource, "private void emitLeafFeedback", "private int distanceSquared");
    assertThat(leafHandler)
        .contains("siblings.remove(block)", "() -> mineLeaves", "        1\n    );")
        .doesNotContain("leafData, tool, siblings");
    assertOrdered(
        leafCommit,
        "origin.getType() == blockType",
        "ItemStack tool = player.getInventory().getItemInMainHand()",
        "!isAxe(tool)",
        "brokenBlocks.add(origin)",
        "ProtectionEventProbe.attemptBlockBreak(player, target)"
    );
    assertOrdered(
        leafCommit,
        "target.getType() != blockType",
        "!canBlockBreak(player, location)",
        "ProtectionEventProbe.attemptBlockBreak(player, target)",
        "brokenBlocks.add(target)"
    );
    assertThat(leafCommit).doesNotContain("target.breakNaturally", "target.setType(Material.AIR)");
    assertOrdered(
        leafFeedback,
        "int leavesBroken = brokenBlocks.size()",
        "addStat(player, \"axe.leaf-veinminer.leaves-broken\", leavesBroken)"
    );

    String woodSource = Files.readString(AXE_WOOD_VEINMINER).replace("\r\n", "\n");
    String woodHandler = section(woodSource, "public void on(BlockBreakEvent e)", "private void mineWood");
    String woodCommit = section(woodSource, "private void mineWood", "private void emitWoodFeedback");
    String woodFeedback = section(woodSource, "private void emitWoodFeedback", "private boolean isLogMaterial");
    assertThat(woodHandler)
        .contains("siblings.remove(block)", "() -> mineWood", "        1\n    );")
        .doesNotContain("logData, tool, siblings");
    assertOrdered(
        woodCommit,
        "origin.getType() == blockType",
        "ItemStack tool = player.getInventory().getItemInMainHand()",
        "!isAxe(tool)",
        "brokenBlocks.add(origin)",
        "ProtectionEventProbe.attemptBlockBreak(player, target)"
    );
    assertOrdered(
        woodCommit,
        "target.getType() != blockType",
        "!canBlockBreak(player, location)",
        "ProtectionEventProbe.attemptBlockBreak(player, target)",
        "brokenBlocks.add(target)"
    );
    assertThat(woodCommit).doesNotContain("target.breakNaturally", "target.setType(Material.AIR)");
    assertOrdered(
        woodFeedback,
        "int logsVeinmined = brokenBlocks.size()",
        "addStat(player, \"axe.wood-veinminer.logs-veinmined\", logsVeinmined)",
        "grantOnce(player, \"challenge_axe_wood_vein_cascade\")"
    );
  }

  @Test
  void excavationBonusBreaksAuthorizeBeforeMutationAndReward() throws IOException {
    String tunnelerSource = Files.readString(EXCAVATION_TUNNELER).replace("\r\n", "\n");
    String tunnelerHandler = section(tunnelerSource, "public void on(BlockBreakEvent e)", "private void planeSweep");
    String tunnelerCommit = section(tunnelerSource, "private void breakTunnelPlane", "private void planeSweep");
    assertThat(tunnelerHandler)
        .contains("J.runEntity(p, () -> breakTunnelPlane(p, origin, originType, bonus, targetOffsets), 1)");
    assertOrdered(
        tunnelerCommit,
        "origin.getType() == originType",
        "!canBlockBreak(player, targetLocation)",
        "!canApplyDurability(hand, getConfig().durabilityCostPerBonusBlock)",
        "!ProtectionEventProbe.attemptBlockBreakProbe(player, target)",
        "target.breakNaturally(hand)",
        "applyDurability(player, hand, getConfig().durabilityCostPerBonusBlock)",
        "broken++",
        "addStat(player, \"excavation.tunneler.blocks-tunneled\", broken)",
        "xp(player, broken * getConfig().xpPerBonusBlock)"
    );

    String burrowSource = Files.readString(EXCAVATION_BURROW).replace("\r\n", "\n");
    String burrowHandler = section(burrowSource, "public void on(PlayerInteractEvent e)", "private List<Block> planDig");
    String burrowCommit = section(burrowSource, "private boolean digBlock", "private boolean canApplyDurability");
    assertOrdered(
        burrowHandler,
        "action == Action.RIGHT_CLICK_AIR && J.isFoliaThreading()",
        "p.getTargetBlockExact(5)",
        "canApplyDurability(hand, plan.size() * getConfig().durabilityCostPerBlock)",
        "digBlock(p, plan.get(0), 1.1F)",
        "cooldowns.mark(p.getUniqueId())",
        "p.setFoodLevel(",
        "scheduleDig(p, plan, floorData)",
        "addStat(p, \"excavation.burrow.burrows-dug\", 1)"
    );
    assertOrdered(
        burrowCommit,
        "!J.isOwnedByCurrentRegion(player)",
        "!J.isOwnedByCurrentRegion(location)",
        "!canBlockBreak(player, location)",
        "!canApplyDurability(hand, getConfig().durabilityCostPerBlock)",
        "!ProtectionEventProbe.attemptBlockBreakProbe(player, block)",
        "block.breakNaturally(hand)",
        "applyDurability(player, hand, getConfig().durabilityCostPerBlock)",
        "addStat(player, \"excavation.burrow.blocks-burrowed\", 1)",
        "xp(player, getConfig().xpPerBlock)"
    );
  }

  @Test
  void pickaxeSecondaryActionsAuthorizeBeforeCostsDropsAndMutation() throws IOException {
    String chiselSource = Files.readString(PICKAXE_CHISEL).replace("\r\n", "\n");
    String chiselHandler = section(chiselSource, "public void on(PlayerInteractEvent e)", "private ItemStack getDropFor");
    assertOrdered(
        chiselHandler,
        "action == Action.RIGHT_CLICK_AIR && J.isFoliaThreading()",
        "p.getTargetBlockExact(5)",
        "!ProtectionEventProbe.attemptBlockBreakProbe(p, target)",
        "J.isFoliaThreading() ? null : p.rayTraceBlocks(8)",
        "p.setCooldown(",
        "damageHand(p, getDamagePerBlock(",
        "target.getWorld().dropItemNaturally(",
        "addStat(p, \"pickaxe.chisel.extra-ores\", 1)",
        "target.breakNaturally(p.getInventory().getItemInMainHand())"
    );

    String boreSource = Files.readString(PICKAXE_TUNNEL_BORE).replace("\r\n", "\n");
    String boreHandler = section(boreSource, "public void on(BlockBreakEvent e)", "private void breakBorePlane");
    String boreCommit = section(boreSource, "private void breakBorePlane", "private List<Block> collectPlane");
    assertThat(boreHandler)
        .contains("() -> breakBorePlane(p, block, originType, boreWidth, boreHeight, targets)", "        1\n    );");
    assertOrdered(
        boreCommit,
        "origin.getType() == originType",
        "!canBlockBreak(player, location)",
        "!ProtectionEventProbe.attemptBlockBreakProbe(player, target)",
        "target.breakNaturally(tool)",
        "broken++",
        "damageHand(player, broken * getConfig().durabilityPerBonusBlock)",
        "addStat(player, \"pickaxe.tunnel-bore.blocks-bored\", broken)"
    );
    String boreSelection = section(boreSource, "private List<Block> collectPlane", "@ConfigDescription");
    assertOrdered(boreSelection, "if (h == 0 && w == 0)", "continue;");
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
