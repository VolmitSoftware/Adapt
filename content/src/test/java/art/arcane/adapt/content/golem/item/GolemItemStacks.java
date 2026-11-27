package art.arcane.adapt.content.golem.item;

import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.testkit.FakeItem;

final class GolemItemStacks {
  static final DataKey<String> KIND_KEY = DataKey.of(Id.of("adapt", "golem_item"), DataCodecs.STRING);
  static final DataKey<String> DATA_KEY = DataKey.of(Id.of("adapt", "golem_item_data"), DataCodecs.STRING);

  private GolemItemStacks() {
  }

  static FakeItem tagged(Id type, String kindId, String data) {
    FakeItem item = new FakeItem(type, 1);
    item.data().set(KIND_KEY, kindId);
    if (data != null) {
      item.data().set(DATA_KEY, data);
    }
    return item;
  }

  static FakeItem plain(Id type) {
    return new FakeItem(type, 1);
  }

  static String stored(ItemRef item, DataKey<String> key) {
    return item.data().get(key);
  }
}
