package art.arcane.adapt.api.golem.item;

import art.arcane.adapt.api.golem.GolemRune;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.misc.CustomModel;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkEffectMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public final class GolemItemLooks implements AutoCloseable {
  private static final String MASTERWORK_HEART = "masterwork_heart";

  private final MockedConstruction<ItemStack> stacks;
  private final MockedStatic<CustomModel> models;

  private GolemItemLooks(MockedConstruction<ItemStack> stacks, MockedStatic<CustomModel> models) {
    this.stacks = stacks;
    this.models = models;
  }

  public static GolemItemLooks open() {
    MockedConstruction<ItemStack> stacks = mockConstruction(ItemStack.class,
        (item, context) -> GolemItemStacks.wire(item, (Material) context.arguments().getFirst()));
    MockedStatic<CustomModel> models = mockStatic(CustomModel.class);
    models.when(() -> CustomModel.get(any(Material.class), any(String[].class))).thenAnswer(call -> model(call.getArgument(0)));
    return new GolemItemLooks(stacks, models);
  }

  public Look look(NamespacedKey id) {
    ItemStack item = MASTERWORK_HEART.equals(id.getKey()) ? GolemItems.masterworkHeart() : GolemItems.resolve(id);
    if (item == null) {
      throw new IllegalArgumentException("No Golemancy item " + id);
    }
    ArgumentCaptor<String> name = ArgumentCaptor.forClass(String.class);
    verify(item.getItemMeta()).setDisplayName(name.capture());
    Optional<GolemRune> rune = GolemItems.runeOf(item);
    return new Look(C.stripColor(name.getValue()), item.getType().name(), tint(item), rune.map(GolemRune::tier).orElse(null));
  }

  @Override
  public void close() {
    models.close();
    stacks.close();
  }

  private static String tint(ItemStack item) {
    if (item.getType() != Material.FIREWORK_STAR) {
      return null;
    }
    ArgumentCaptor<FireworkEffect> effect = ArgumentCaptor.forClass(FireworkEffect.class);
    verify((FireworkEffectMeta) item.getItemMeta()).setEffect(effect.capture());
    Color color = effect.getValue().getColors().getFirst();
    return String.format("#%06X", color.asRGB());
  }

  private static CustomModel model(Material material) {
    CustomModel model = mock(CustomModel.class);
    when(model.material()).thenReturn(material);
    when(model.toItemStack()).thenAnswer(call -> new ItemStack(material));
    when(model.toItemStack(any(ItemStack.class))).thenAnswer(call -> call.getArgument(0));
    return model;
  }

  public record Look(String name, String base, String tint, Integer tier) {
  }
}
