package art.arcane.adapt.content.adaptation;

import art.arcane.adapt.content.adaptation.axe.AxeDropToInventory;
import art.arcane.adapt.content.adaptation.excavation.ExcavationDropToInventory;
import art.arcane.adapt.content.adaptation.herbalism.HerbalismDropToInventory;
import art.arcane.adapt.content.adaptation.hunter.HunterDropToInventory;
import art.arcane.adapt.content.adaptation.pickaxe.PickaxeDropToInventory;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockDropItemEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DropToInventoryOverflowTest {
  private static final List<Path> SOURCES = List.of(
      Path.of("src/main/java/art/arcane/adapt/content/adaptation/axe/AxeDropToInventory.java"),
      Path.of("src/main/java/art/arcane/adapt/content/adaptation/excavation/ExcavationDropToInventory.java"),
      Path.of("src/main/java/art/arcane/adapt/content/adaptation/herbalism/HerbalismDropToInventory.java"),
      Path.of("src/main/java/art/arcane/adapt/content/adaptation/hunter/HunterDropToInventory.java"),
      Path.of("src/main/java/art/arcane/adapt/content/adaptation/pickaxe/PickaxeDropToInventory.java")
  );
  private static final List<Class<?>> BLOCK_DROP_ADAPTATIONS = List.of(
      AxeDropToInventory.class,
      ExcavationDropToInventory.class,
      HerbalismDropToInventory.class,
      HunterDropToInventory.class,
      PickaxeDropToInventory.class
  );

  @Test
  void everyDropToInventoryPathDropsOnlyReturnedLeftovers() throws Exception {
    for (Path sourcePath : SOURCES) {
      String source = Files.readString(sourcePath);

      assertThat(source)
          .as(sourcePath.toString())
          .contains("leftovers.values().forEach(item -> p.getWorld().dropItem(p.getLocation(), item))")
          .doesNotContain(
              "p.getWorld().dropItem(p.getLocation(), i.getItemStack())",
              "p.getWorld().dropItem(p.getLocation(), i);"
          );
    }
  }

  @Test
  void everyBlockDropTransferKeepsProtectionDeniedItemsAtTheSource() throws Exception {
    for (Path sourcePath : SOURCES) {
      String source = Files.readString(sourcePath);

      assertThat(source)
          .as(sourcePath.toString())
          .contains(
              "ProtectionEventProbe.attemptBlockDropPickup",
              "e.getItems().remove(i)"
          )
          .doesNotContain("e.getItems().clear()");
    }
  }

  @Test
  void everyBlockDropTransferIgnoresAlreadyClaimedDrops() throws Exception {
    for (Class<?> adaptation : BLOCK_DROP_ADAPTATIONS) {
      Method handler = adaptation.getDeclaredMethod("on", BlockDropItemEvent.class);
      EventHandler annotation = handler.getAnnotation(EventHandler.class);

      assertThat(annotation)
          .as(adaptation.getSimpleName())
          .isNotNull();
      assertThat(annotation.ignoreCancelled())
          .as(adaptation.getSimpleName())
          .isTrue();
    }
  }
}
