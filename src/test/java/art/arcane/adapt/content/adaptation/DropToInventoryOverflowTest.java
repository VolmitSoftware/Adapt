package art.arcane.adapt.content.adaptation;

import org.junit.jupiter.api.Test;

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
}
