package art.arcane.adapt.content.golem.item;

import art.arcane.adapt.api.item.ItemArt;
import art.arcane.adapt.content.golem.GolemRune;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.util.common.format.C;
import org.mockito.MockedStatic;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

public final class GolemItemLooks implements AutoCloseable {
  private static final String MASTERWORK_HEART = "masterwork_heart";
  private static final Id FIREWORK_STAR = Id.minecraft("firework_star");

  private final MockedStatic<ItemArt> models;

  private GolemItemLooks(MockedStatic<ItemArt> models) {
    this.models = models;
  }

  public static GolemItemLooks open() {
    MockedStatic<ItemArt> models = mockStatic(ItemArt.class);
    models.when(() -> ItemArt.modelled(any(Id.class), any(String[].class))).thenAnswer(call -> new FakeItem(call.getArgument(0), 1));
    return new GolemItemLooks(models);
  }

  public Look look(Id id) {
    ItemRef item = MASTERWORK_HEART.equals(id.path()) ? GolemItems.masterworkHeart() : GolemItems.resolve(id);
    if (item == null) {
      throw new IllegalArgumentException("No Golemancy item " + id);
    }
    Optional<GolemRune> rune = GolemItems.runeOf(item);
    return new Look(C.stripColor(item.displayName()), item.type().legacyName(), tint(item), rune.map(GolemRune::tier).orElse(null));
  }

  @Override
  public void close() {
    models.close();
  }

  private static String tint(ItemRef item) {
    if (!FIREWORK_STAR.equals(item.type())) {
      return null;
    }
    Rgb color = ((FakeItem) item).fireworkStarColor();
    return String.format("#%06X", color.packed());
  }

  public record Look(String name, String base, String tint, Integer tier) {
  }
}
