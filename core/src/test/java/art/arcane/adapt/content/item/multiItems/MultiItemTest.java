package art.arcane.adapt.content.item.multiItems;

import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MultiItemTest {
  @TempDir
  Path dataDirectory;

  private TestAdapt adapt;

  @BeforeEach
  void start() {
    adapt = TestAdapt.start(TestPlatform.builder(dataDirectory).build(), List.of());
  }

  @AfterEach
  void stop() {
    adapt.close();
  }

  @Test
  void mergedToolsSwitchAndExplodeBackIntoTheirParts() {
    OmniTool omniTool = new OmniTool();
    ItemRef pickaxe = FakeItem.tool("iron_pickaxe", 250);
    ItemRef shovel = FakeItem.tool("iron_shovel", 250);

    ItemRef merged = omniTool.build(pickaxe, shovel);

    assertThat(omniTool.isMultiItem(merged)).isTrue();
    assertThat(merged.type()).isEqualTo(Id.minecraft("iron_pickaxe"));
    assertThat(omniTool.getItems(merged)).extracting(ItemRef::type).containsExactly(Id.minecraft("iron_shovel"));

    ItemRef switched = omniTool.nextShovel(merged);

    assertThat(switched.type()).isEqualTo(Id.minecraft("iron_shovel"));
    assertThat(omniTool.isMultiItem(switched)).isTrue();
    assertThat(omniTool.getItems(switched)).extracting(ItemRef::type).containsExactly(Id.minecraft("iron_pickaxe"));
    assertThat(omniTool.explode(switched)).extracting(ItemRef::type)
        .containsExactly(Id.minecraft("iron_shovel"), Id.minecraft("iron_pickaxe"));
    assertThat(omniTool.isMultiItem(omniTool.getRealItem(switched))).isFalse();
  }

  @Test
  void anUnreadableStoredPartFailsInsteadOfBeingDroppedFromTheItem() {
    OmniTool omniTool = new OmniTool();
    ItemRef merged = omniTool.build(FakeItem.tool("iron_pickaxe", 250), FakeItem.tool("iron_shovel", 250));
    DataKey<String> key = DataKey.adapt(omniTool.getKey(), DataCodecs.STRING);
    String corrupt = merged.data().get(key).replace("]", ",\"\"]");
    merged.data().set(key, corrupt);

    assertThatThrownBy(() -> omniTool.nextShovel(merged)).isInstanceOf(IllegalStateException.class);
    assertThat(merged.data().get(key)).isEqualTo(corrupt);
  }

  @Test
  void ownLoreIsRestoredWhenTheToolIsSplit() {
    OmniTool omniTool = new OmniTool();
    ItemRef pickaxe = FakeItem.tool("iron_pickaxe", 250);
    pickaxe.setLoreJson(List.of("{\"text\":\"Heirloom\"}"));

    ItemRef merged = omniTool.build(pickaxe, FakeItem.tool("iron_axe", 250));

    assertThat(merged.loreJson()).hasSize(4).startsWith("{\"text\":\"Heirloom\"}");
    assertThat(omniTool.getRealItem(merged).loreJson()).containsExactly("{\"text\":\"Heirloom\"}");
  }

  @Test
  void mergingAMultiItemAddsItsComponentsInsteadOfNestingIt() {
    OmniTool omniTool = new OmniTool();
    ItemRef inner = omniTool.build(FakeItem.tool("iron_axe", 250), FakeItem.tool("iron_hoe", 250));

    ItemRef merged = omniTool.build(FakeItem.tool("iron_pickaxe", 250), inner);

    assertThat(omniTool.explode(merged)).extracting(ItemRef::type).containsExactly(
        Id.minecraft("iron_pickaxe"), Id.minecraft("iron_axe"), Id.minecraft("iron_hoe"));
  }

  @Test
  void multiArmorSwitchesBetweenChestplateAndElytra() {
    MultiArmor armor = new MultiArmor();
    ItemRef merged = armor.build(FakeItem.tool("diamond_chestplate", 528), FakeItem.tool("elytra", 432));

    ItemRef elytra = armor.nextElytra(merged);

    assertThat(elytra.type()).isEqualTo(Id.minecraft("elytra"));
    assertThat(armor.nextChestplate(elytra).type()).isEqualTo(Id.minecraft("diamond_chestplate"));
  }

  @Test
  void legacyTextBecomesStyledComponentJson() {
    assertThat(LegacyTextJson.toJson("plain")).isEqualTo("{\"text\":\"plain\"}");
    assertThat(LegacyTextJson.toJson("§7Iron Pickaxe")).isEqualTo("{\"text\":\"Iron Pickaxe\",\"color\":\"gray\"}");
    assertThat(LegacyTextJson.toJson("-> §a§lGo"))
        .isEqualTo("{\"text\":\"\",\"extra\":[{\"text\":\"-> \"},{\"text\":\"Go\",\"color\":\"green\",\"bold\":true}]}");
    assertThat(LegacyTextJson.toJson("§x§F§f§0§0§a§aHex"))
        .isEqualTo("{\"text\":\"Hex\",\"color\":\"#ff00aa\"}");
    assertThat(LegacyTextJson.toJson("§lBold§rPlain"))
        .isEqualTo("{\"text\":\"\",\"extra\":[{\"text\":\"Bold\",\"bold\":true},{\"text\":\"Plain\"}]}");
    assertThat(LegacyTextJson.toJson("")).isEqualTo("{\"text\":\"\"}");
  }
}
