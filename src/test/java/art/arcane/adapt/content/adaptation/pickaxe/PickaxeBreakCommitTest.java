package art.arcane.adapt.content.adaptation.pickaxe;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PickaxeBreakCommitTest {
  private static final Path AUTOSMELT_SOURCE = Path.of(
      "src/main/java/art/arcane/adapt/content/adaptation/pickaxe/PickaxeAutosmelt.java");
  private static final Path SILK_SPAWNER_SOURCE = Path.of(
      "src/main/java/art/arcane/adapt/content/adaptation/pickaxe/PickaxeSilkSpawner.java");
  private static final Path REPAIR_RHYTHM_SOURCE = Path.of(
      "src/main/java/art/arcane/adapt/content/adaptation/pickaxe/PickaxeRepairRhythm.java");

  @Test
  void autosmeltRunsOnlyFromThePostCommitDropEvent() throws Exception {
    Method handler = PickaxeAutosmelt.class.getDeclaredMethod("on", BlockDropItemEvent.class);
    EventHandler annotation = handler.getAnnotation(EventHandler.class);

    assertThat(annotation.ignoreCancelled()).isTrue();
    assertThat(annotation.priority()).isEqualTo(EventPriority.MONITOR);

    String source = Files.readString(AUTOSMELT_SOURCE);
    int replacementStart = source.indexOf("private static boolean replaceRawDrops");
    int replacementEnd = source.indexOf("static Material getIngotFor");
    String replacement = source.substring(replacementStart, replacementEnd);
    assertThat(source)
        .contains(
            "Material ore = e.getBlockState().getType();",
            "Iterator<Item> iterator = event.getItems().iterator();",
            "nativeAmount += item.getItemStack().getAmount();",
            "getCommittedSmeltAmount(nativeAmount, level",
            "converted.setItemStack(new ItemStack(ingot, amount));"
        )
        .doesNotContain("e.getItems().clear();")
        .doesNotContain("public void on(BlockBreakEvent");
    assertThat(replacement).doesNotContain("getFortuneOreMultiplier(");
  }

  @Test
  void dropToInventoryRunsAfterAutosmeltAtTheFinalDropGate() throws Exception {
    Method handler = PickaxeDropToInventory.class.getDeclaredMethod("on", BlockDropItemEvent.class);
    EventHandler annotation = handler.getAnnotation(EventHandler.class);

    assertThat(annotation.ignoreCancelled()).isTrue();
    assertThat(annotation.priority()).isEqualTo(EventPriority.MONITOR);

    String skillSource = Files.readString(Path.of(
        "src/main/java/art/arcane/adapt/content/skill/SkillPickaxes.java"));
    assertThat(skillSource.indexOf("registerAdaptation(new PickaxeAutosmelt())"))
        .isLessThan(skillSource.indexOf("registerAdaptation(new PickaxeDropToInventory())"));
  }

  @Test
  void silkSpawnerPreparesItsStatefulItemAndCommitsFeedbackAtMonitor() throws Exception {
    Method prepare = PickaxeSilkSpawner.class.getDeclaredMethod("onBlockDropPrepare", BlockDropItemEvent.class);
    Method commit = PickaxeSilkSpawner.class.getDeclaredMethod("onBlockDropCommit", BlockDropItemEvent.class);
    EventHandler prepareAnnotation = prepare.getAnnotation(EventHandler.class);
    EventHandler commitAnnotation = commit.getAnnotation(EventHandler.class);

    assertThat(prepareAnnotation.ignoreCancelled()).isTrue();
    assertThat(prepareAnnotation.priority()).isEqualTo(EventPriority.HIGH);
    assertThat(commitAnnotation.priority()).isEqualTo(EventPriority.MONITOR);

    String source = Files.readString(SILK_SPAWNER_SOURCE);
    int cancellation = source.indexOf("if (event.isCancelled())");
    int stat = source.indexOf("addStat(plan.player()");
    assertThat(source)
        .contains(
            "ItemStack spawner = createSpawnerItem(state);",
            "meta.getBlockState() instanceof CreatureSpawner target",
            "target.setSpawnedType(spawnedType);",
            "meta.setBlockState(target);",
            "event.getItems().add(item);",
            "pendingDrops.remove(event)"
        )
        .doesNotContain("meta.setBlockState(state);")
        .doesNotContain("callEvent(", "event.setDropItems(false)", "BlockBreakEvent");
    assertThat(stat).isGreaterThan(cancellation);
  }

  @Test
  void repairRhythmWaitsForTheCommittedItemDamageAndCancelsThatWearOnAProc() throws Exception {
    Method prepare = PickaxeRepairRhythm.class.getDeclaredMethod("on", BlockBreakEvent.class);
    Method commit = PickaxeRepairRhythm.class.getDeclaredMethod("on", PlayerItemDamageEvent.class);
    EventHandler prepareAnnotation = prepare.getAnnotation(EventHandler.class);
    EventHandler commitAnnotation = commit.getAnnotation(EventHandler.class);

    assertThat(prepareAnnotation.priority()).isEqualTo(EventPriority.MONITOR);
    assertThat(prepareAnnotation.ignoreCancelled()).isTrue();
    assertThat(commitAnnotation.priority()).isEqualTo(EventPriority.HIGHEST);
    assertThat(commitAnnotation.ignoreCancelled()).isFalse();

    String source = Files.readString(REPAIR_RHYTHM_SOURCE);
    assertThat(source)
        .contains(
            "pendingRepairs.put(playerId, pending);",
            "J.runEntity(p, () -> applyDeferredRepair(p, playerId, pending), 1);",
            "e.setCancelled(true);",
            "completeRepair(p, restored);"
        );
  }
}
