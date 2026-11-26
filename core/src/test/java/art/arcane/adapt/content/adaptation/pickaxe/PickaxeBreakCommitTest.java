package art.arcane.adapt.content.adaptation.pickaxe;

import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.block.BlockDropItemEvent;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerItemDamageEvent;
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
  private static final Path REPAIR_SOURCE = Path.of(
      "src/main/java/art/arcane/adapt/content/adaptation/pickaxe/PickaxeRepair.java");

  @Test
  void autosmeltRunsOnlyFromThePostCommitDropEvent() throws Exception {
    Method handler = PickaxeAutosmelt.class.getDeclaredMethod("on", BlockDropItemEvent.class);
    Handle annotation = handler.getAnnotation(Handle.class);

    assertThat(annotation.ignoreCancelled()).isTrue();
    assertThat(annotation.priority()).isEqualTo(EventPriority.MONITOR);

    String source = Files.readString(AUTOSMELT_SOURCE);
    int replacementStart = source.indexOf("public static boolean replaceRawDrops");
    int replacementEnd = source.indexOf("static Id getIngotFor");
    String replacement = source.substring(replacementStart, replacementEnd);
    assertThat(source)
        .contains(
            "Id ore = e.brokenState().type();",
            "Iterator<ItemEntityRef> iterator = event.items().iterator();",
            "nativeAmount += item.item().amount();",
            "getCommittedSmeltAmount(nativeAmount, level",
            "converted.setItem(AdaptCore.platform().host().items().create(ingot, amount));"
        )
        .doesNotContain("e.items().clear();")
        .doesNotContain("public void on(BlockBreakEvent");
    assertThat(replacement).doesNotContain("getFortuneOreMultiplier(");
  }

  @Test
  void dropToInventoryRunsAfterAutosmeltAtTheFinalDropGate() throws Exception {
    Method handler = PickaxeDropToInventory.class.getDeclaredMethod("on", BlockDropItemEvent.class);
    Handle annotation = handler.getAnnotation(Handle.class);

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
    Handle prepareAnnotation = prepare.getAnnotation(Handle.class);
    Handle commitAnnotation = commit.getAnnotation(Handle.class);

    assertThat(prepareAnnotation.ignoreCancelled()).isTrue();
    assertThat(prepareAnnotation.priority()).isEqualTo(EventPriority.HIGH);
    assertThat(commitAnnotation.priority()).isEqualTo(EventPriority.MONITOR);
    assertThat(commitAnnotation.ignoreCancelled()).isFalse();

    String source = Files.readString(SILK_SPAWNER_SOURCE);
    int cancellation = source.indexOf("if (event.isCancelled())");
    int stat = source.indexOf("addStat(plan.player()");
    assertThat(source)
        .contains(
            "ItemRef spawner = createSpawnerItem(event.brokenSpawnerType());",
            "event.items().add(item);",
            "pendingDrops.remove(DropKey.of(event))"
        )
        .doesNotContain("callEvent(", "setDropItems(false)", "BlockBreakEvent");
    assertThat(stat).isGreaterThan(cancellation);
  }

  @Test
  void repairRhythmWaitsForTheCommittedItemDamageAndCancelsThatWearOnAProc() throws Exception {
    Method prepare = PickaxeRepairRhythm.class.getDeclaredMethod("on", BlockBreakEvent.class);
    Method commit = PickaxeRepairRhythm.class.getDeclaredMethod("on", PlayerItemDamageEvent.class);
    Handle prepareAnnotation = prepare.getAnnotation(Handle.class);
    Handle commitAnnotation = commit.getAnnotation(Handle.class);

    assertThat(prepareAnnotation.priority()).isEqualTo(EventPriority.MONITOR);
    assertThat(prepareAnnotation.ignoreCancelled()).isTrue();
    assertThat(commitAnnotation.priority()).isEqualTo(EventPriority.HIGHEST);
    assertThat(commitAnnotation.ignoreCancelled()).isFalse();

    String source = Files.readString(REPAIR_SOURCE);
    assertThat(source)
        .contains(
            "pendingRepairs.put(playerId, pending);",
            "Tasks.runEntity(p, () -> applyDeferredRepair(p, playerId, pending), 1);",
            "e.setCancelled(true);",
            "onRepaired.accept(p, restored);"
        );
  }

  @Test
  void dropToInventoryIgnoresAlreadyClaimedDropsAndChiselObservesProtectionAtMonitor() throws Exception {
    Handle drop = PickaxeDropToInventory.class.getDeclaredMethod("on", BlockDropItemEvent.class).getAnnotation(Handle.class);
    Handle chisel = PickaxeChisel.class.getDeclaredMethod("on", PlayerInteractEvent.class).getAnnotation(Handle.class);

    assertThat(drop).isNotNull();
    assertThat(drop.ignoreCancelled()).isTrue();
    assertThat(chisel.priority()).isEqualTo(EventPriority.MONITOR);
    assertThat(chisel.ignoreCancelled()).isTrue();
  }
}
