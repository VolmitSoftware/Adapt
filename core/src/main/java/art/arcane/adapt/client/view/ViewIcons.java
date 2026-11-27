package art.arcane.adapt.client.view;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.engine.component.ComponentHost;
import art.arcane.adapt.engine.mutation.MutatedSkill;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.inventorygui.GuiConfig;
import art.arcane.adapt.util.common.misc.CustomModel;

/**
 * The icon every client view sends for a skill or an adaptation: the configured item and model as the fallback, and
 * the sprite the client draws. A mutated skill is its lead's item under its own emblem and trim; a mutated adaptation
 * or signature has the art {@link MutationIcons} gives it; any other skill or adaptation has its art at the
 * {@link IconSprites} convention path.
 */
public final class ViewIcons {
  private ViewIcons() {
  }

  public static IconRef skill(Skill<?> skill) {
    Skill<?> item = skill instanceof MutatedSkill mutated ? mutated.getLead() : skill;
    IconRef model = model(GuiConfig.skillModel(SkillHost.lineKey(item), item.getIcon().item(), ComponentHost.of(item).getModel()));
    return IconSprites.withSprite(MutationIcons.skill(skill, model), IconSprites.skill(skill.getId()));
  }

  public static IconRef adaptation(Adaptation<?> adaptation) {
    IconRef model = model(GuiConfig.adaptationModel(adaptation.getId().toString(), adaptation.getIcon().item(), ComponentHost.of(adaptation).getModel()));
    return IconSprites.withSprite(MutationIcons.adaptation(adaptation, model), IconSprites.adaptation(adaptation.getId()));
  }

  /** Knowledge as a resource: the knowledge mark, the book's knowledge model as the fallback. */
  public static IconRef knowledge() {
    return IconSprites.withSprite(model(CustomModel.get(Id.minecraft("book"), "snippets", "gui", "knowledge")),
        IconSprites.mark("knowledge"));
  }

  /** The item {@code model} draws, with its custom model data and item model. */
  public static IconRef model(CustomModel model) {
    Id modelKey = model.modelKey();
    String itemModel = modelKey == null || CustomModel.EMPTY_KEY.equals(modelKey) ? null : modelKey.toString();
    return new IconRef(model.material().toString(), model.model(), itemModel, null);
  }
}
