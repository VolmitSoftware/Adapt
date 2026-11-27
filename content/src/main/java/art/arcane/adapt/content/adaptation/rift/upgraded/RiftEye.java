package art.arcane.adapt.content.adaptation.rift.upgraded;

import art.arcane.adapt.api.item.CustomItem;
import art.arcane.adapt.api.mutation.MutationTool;
import art.arcane.adapt.api.mutation.ToolUse;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.content.item.ToolItems;
import art.arcane.adapt.platform.recipe.Ingredient;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Id;

import java.util.List;

/** An eye bound to the anchor network: right-click anywhere to pick an anchor and travel there. */
public class RiftEye extends MutationTool<CustomItem.Plain> {
  private final PocketRealm realm;

  public RiftEye(PocketRealm realm) {
    super(ToolItems.plain(Id.adapt("rift/tool/eye"), Id.minecraft("ender_eye")).build(), realm, 1);
    this.realm = realm;
  }

  @Override
  public List<RecipeSpec> recipes() {
    return List.of(RecipeSpec.shapeless(Id.adapt("rift/tool/eye"), List.of(Ingredient.of(Id.minecraft("ender_eye")), Ingredient.of(Id.minecraft("crying_obsidian")),
            Ingredient.of(Id.minecraft("amethyst_shard"))), create(CustomItem.Plain.INSTANCE)));
  }

  @Override
  public void applyLore(CustomItem.Plain data, List<String> lines) {
    lines.add(TextFormat.GRAY + Text.key("adapt.tool.rift.eye.usage").resolve());
  }

  @Override
  public void use(ToolUse use) {
    if (use.rightClick()) {
      realm.openFromEye(use.player(), use.block(), use.level());
    }
  }
}
