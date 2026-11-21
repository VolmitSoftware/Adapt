package art.arcane.adapt.client.server;

import art.arcane.adapt.client.protocol.ClientProtocolException;
import art.arcane.adapt.client.protocol.FrameCodec;
import art.arcane.adapt.client.protocol.ItemDescriptor;
import art.arcane.adapt.client.protocol.ServerFrame;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.testkit.FakeItems;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CustomItemCatalogTest {
  private final FakeItems items = new FakeItems();

  @Test
  void describesCustomStacksInOrderAndSkipsPlainEmptyAndRepeatedOnes() {
    ItemRef wand = named("stick", "Chalk Wand", 1);
    List<CustomItemCatalog.Source> sources = List.of(
        new CustomItemCatalog.Source("adapt:orb", named("snowball", "Knowledge Orb", 1)),
        new CustomItemCatalog.Source("adapt:plain", items.create(Id.minecraft("cobweb"), 4)),
        new CustomItemCatalog.Source("adapt:empty", items.empty()),
        new CustomItemCatalog.Source("adapt:chalk-wand", wand),
        new CustomItemCatalog.Source("adapt:chalk-wand-copy", named("stick", "Chalk Wand", 3)),
        new CustomItemCatalog.Source("adapt:orb", named("snowball", "Other Orb", 1)),
        new CustomItemCatalog.Source("adapt:elevator", named("white_wool", "Elevator", 2)));

    List<ItemDescriptor> described = CustomItemCatalog.describe(sources, items);

    assertThat(described).extracting(ItemDescriptor::id)
        .containsExactly("adapt:orb", "adapt:chalk-wand", "adapt:elevator");
    ItemDescriptor chalk = described.get(1);
    assertThat(chalk.baseItemId()).isEqualTo("minecraft:stick");
    assertThat(chalk.name()).isEqualTo("Chalk Wand");
    assertThat(chalk.lore()).containsExactly("lore of Chalk Wand");
    assertThat(chalk.components()).isEqualTo(items.encodeNbt(wand.copyWithAmount(1)));
    assertThat(CustomItemCatalog.describe(sources, items)).isEqualTo(described);
  }

  @Test
  void chunksStayUnderTheChunkSizeAndEndWithAnEmptyFrame() throws ClientProtocolException {
    List<ItemDescriptor> list = new ArrayList<>();
    for (int index = 0; index < 40; index++) {
      list.add(new ItemDescriptor("adapt:item-" + index, "minecraft:stick", "Item " + index, List.of(), null, 0,
          new byte[20000]));
    }

    List<ServerFrame.CustomItems> frames = CustomItemCatalog.frames(list);

    assertThat(frames.size()).isGreaterThan(3);
    assertThat(frames.get(frames.size() - 1).items()).isEmpty();
    List<ItemDescriptor> joined = new ArrayList<>();
    for (ServerFrame.CustomItems frame : frames) {
      assertThat(FrameCodec.encodeServer(frame).length).isLessThanOrEqualTo(CustomItemCatalog.CHUNK_BYTES);
      joined.addAll(frame.items());
    }
    assertThat(joined).isEqualTo(list);
    assertThat(frames.subList(0, frames.size() - 1)).allSatisfy((ServerFrame.CustomItems frame) ->
        assertThat(frame.items()).isNotEmpty());
  }

  @Test
  void anEmptyListIsOnlyTheEmptyFrame() {
    assertThat(CustomItemCatalog.frames(List.of())).containsExactly(new ServerFrame.CustomItems(List.of()));
  }

  private ItemRef named(String type, String name, int amount) {
    ItemRef stack = items.create(Id.minecraft(type), amount);
    stack.setDisplayName(name);
    stack.setLore(List.of("lore of " + name));
    return stack;
  }
}
