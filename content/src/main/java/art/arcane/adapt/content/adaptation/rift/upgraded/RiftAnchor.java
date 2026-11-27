package art.arcane.adapt.content.adaptation.rift.upgraded;

import art.arcane.adapt.api.item.CustomItem;
import art.arcane.adapt.api.mutation.MutationTool;
import art.arcane.adapt.api.mutation.ToolUse;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.content.item.ToolItems;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.recipe.Ingredient;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Id;

import java.util.List;
import java.util.Map;

/** A Rift Anchor waiting to be placed: right-click a block to set it beside that block. */
public class RiftAnchor extends MutationTool<CustomItem.Plain> {
  private final PocketRealm realm;

  public RiftAnchor(PocketRealm realm) {
    super(ToolItems.plain(Id.adapt("rift/tool/anchor"), Id.minecraft("lodestone")).build(), realm, 1);
    this.realm = realm;
  }

  @Override
  public List<RecipeSpec> recipes() {
    return List.of(RecipeSpec.shaped(Id.adapt("rift/tool/anchor"), List.of(" C ", "PLP", " C "), Map.of('C', Ingredient.of(Id.minecraft("crying_obsidian")), 'P', Ingredient.of(Id.minecraft("ender_pearl")),
            'L', Ingredient.of(Id.minecraft("lodestone"))), create(CustomItem.Plain.INSTANCE)));
  }

  @Override
  public void applyLore(CustomItem.Plain data, List<String> lines) {
    lines.add(TextFormat.GRAY + Text.key("adapt.tool.rift.anchor.usage").resolve());
  }

  @Override
  public void use(ToolUse use) {
    if (use.action() == InteractAction.RIGHT_CLICK_BLOCK
        && use.block() != null && use.face() != null) {
      realm.placeAnchor(use.player(), use.block().relative(use.face()), use.item(), use.level());
    }
  }
}
