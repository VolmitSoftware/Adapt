package art.arcane.adapt.localization;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.skill.SimpleSkill;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.util.common.inventorygui.GuiConfig;
import art.arcane.volmlib.util.localization.BukkitLanguageEditorPresentation;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

public final class AdaptLanguageEditor {
  private final Supplier<SkillRegistry> registry;
  private volatile IconCatalog catalog;

  AdaptLanguageEditor(Supplier<SkillRegistry> registry) {
    this.registry = Objects.requireNonNull(registry, "registry");
  }

  public static BukkitLanguageEditorPresentation presentation() {
    AdaptLanguageEditor editor = new AdaptLanguageEditor(AdaptLanguageEditor::registry);
    return new BukkitLanguageEditorPresentation(BukkitLanguageEditorPresentation.Layout.FOUR_ROWS, editor::icon);
  }

  Optional<ItemStack> icon(String messageId) {
    SkillRegistry current = registry.get();
    if (current == null) {
      return Optional.empty();
    }
    long revision = current.getCatalogRevision();
    IconCatalog active = catalog;
    if (active == null || active.registry() != current || active.revision() != revision) {
      active = index(current, revision);
      catalog = active;
    }
    String prefix = messageId;
    while (true) {
      Supplier<ItemStack> icon = active.icons().get(prefix);
      if (icon != null) {
        return Optional.of(icon.get());
      }
      int separator = prefix.lastIndexOf('.');
      if (separator < 0) {
        return Optional.empty();
      }
      prefix = prefix.substring(0, separator);
    }
  }

  private static SkillRegistry registry() {
    Adapt plugin = Adapt.instance;
    return plugin == null || plugin.getAdaptServer() == null
        ? null : plugin.getAdaptServer().getSkillRegistry();
  }

  private IconCatalog index(SkillRegistry registry, long revision) {
    Map<String, Supplier<ItemStack>> icons = new HashMap<>();
    for (Skill<?> skill : registry.getAllSkills()) {
      Supplier<ItemStack> skillIcon = () -> GuiConfig.skillModel(
          skill.getName(), skill.getIcon(), skill.getModel()).toItemStack();
      icons.put(skill.getName(), skillIcon);
      if (skill instanceof SimpleSkill<?> simple) {
        String name = simple.getPresentation().name().id();
        icons.put(name.substring(0, name.lastIndexOf('.')), skillIcon);
      }
      for (Adaptation<?> adaptation : skill.getAdaptations()) {
        if (adaptation instanceof SimpleAdaptation<?> simple) {
          String prefix = simple.getLocalizationKey();
          icons.put(prefix, () -> GuiConfig.adaptationModel(
              adaptation.getName(), adaptation.getIcon(), adaptation.getModel()).toItemStack());
          int separator = prefix.indexOf('.');
          if (separator > 0) {
            icons.putIfAbsent(prefix.substring(0, separator), skillIcon);
          }
        }
      }
    }
    return new IconCatalog(registry, revision, Map.copyOf(icons));
  }

  private record IconCatalog(SkillRegistry registry, long revision, Map<String, Supplier<ItemStack>> icons) {
  }
}
