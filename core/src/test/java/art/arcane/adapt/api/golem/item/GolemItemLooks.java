package art.arcane.adapt.api.golem.item;

import art.arcane.adapt.api.golem.GolemRune;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.misc.CustomModel;
import org.mockito.MockedStatic;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public final class GolemItemLooks implements AutoCloseable {
  private static final String MASTERWORK_HEART = "masterwork_heart";
  private static final Id FIREWORK_STAR = Id.minecraft("firework_star");

  private final MockedStatic<CustomModel> models;

  private GolemItemLooks(MockedStatic<CustomModel> models) {
    this.models = models;
  }

  public static GolemItemLooks open() {
    MockedStatic<CustomModel> models = mockStatic(CustomModel.class);
    models.when(() -> CustomModel.get(any(Id.class), any(String[].class))).thenAnswer(call -> model(call.getArgument(0)));
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

  static CustomModel model(Id material) {
    CustomModel model = mock(CustomModel.class);
    when(model.material()).thenReturn(material);
    when(model.toItemStack()).thenAnswer(call -> new FakeItem(material, 1));
    when(model.toItemStack(any(ItemRef.class))).thenAnswer(call -> call.getArgument(0));
    return model;
  }

  public record Look(String name, String base, String tint, Integer tier) {
  }
}
