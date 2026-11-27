package art.arcane.adapt.content.adaptation.sword;

import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.item.ItemRef;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** One lore line a sword adaptation owns on a blade, remembered under its own key so it can be replaced or removed. */
public final class SwordBladeLore {
  private SwordBladeLore() {
  }

  /**
   * Replaces the line stored under {@code key} (and any line containing {@code english}, when given) with {@code rendered}, or
   * removes it when {@code rendered} is null.
   */
  public static void replace(ItemRef item, DataKey<String> key, @Nullable String english, @Nullable String rendered) {
    List<String> lore = new ArrayList<>(item.lore());
    String previous = item.data().get(key);
    lore.removeIf((String line) -> line != null && (line.equals(previous) || (english != null && line.contains(english))));
    if (rendered == null) {
      item.data().remove(key);
    } else {
      lore.add(rendered);
      item.data().set(key, rendered);
    }
    item.setLore(lore);
  }
}
