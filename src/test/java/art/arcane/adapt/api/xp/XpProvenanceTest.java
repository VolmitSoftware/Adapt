package art.arcane.adapt.api.xp;

import art.arcane.adapt.api.data.unit.PlacementStamp;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class XpProvenanceTest {
  @Test
  void permanentPlacementEvidenceDoesNotExpireWithTheXpTtl() {
    assertThat(XpProvenance.hasPermanentPlacementRecord(null)).isFalse();
    assertThat(XpProvenance.hasPermanentPlacementRecord(new PlacementStamp(0, 0, 0))).isFalse();
    assertThat(XpProvenance.hasPermanentPlacementRecord(new PlacementStamp(1, 0, 0))).isTrue();
    assertThat(XpProvenance.hasPermanentPlacementRecord(new PlacementStamp(0, 1, 0))).isTrue();
  }

  @Test
  void dragonEggMovementProvenanceRunsAfterCommittedMovement() throws Exception {
    assertCommittedMovementHandler(BlockFromToEvent.class);
    assertCommittedMovementHandler(EntityChangeBlockEvent.class);
  }

  private static void assertCommittedMovementHandler(Class<?> eventType) throws Exception {
    Method handler = XpProvenanceListener.class.getDeclaredMethod("on", eventType);
    EventHandler annotation = handler.getAnnotation(EventHandler.class);

    assertThat(annotation.priority()).isEqualTo(EventPriority.MONITOR);
    assertThat(annotation.ignoreCancelled()).isTrue();
  }
}
