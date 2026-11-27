package art.arcane.adapt.engine.gui;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.engine.component.ComponentHost;
import art.arcane.adapt.engine.mutation.MutatedSkill;
import art.arcane.adapt.engine.mutation.upgrade.SkillUpgrades;
import art.arcane.adapt.engine.mutation.upgrade.Lending;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.MutatedMessages;
import art.arcane.adapt.localization.catalog.SnippetsMessages;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.menu.MenuClick;
import art.arcane.adapt.platform.menu.MenuLayout;
import art.arcane.adapt.platform.menu.MenuRole;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.util.common.inventorygui.GuiConfig;
import art.arcane.adapt.util.common.inventorygui.GuiElement;
import art.arcane.adapt.util.common.inventorygui.GuiLayout;
import art.arcane.adapt.util.common.inventorygui.GuiTheme;
import art.arcane.adapt.util.common.inventorygui.GuiWindow;
import art.arcane.adapt.util.common.misc.CustomModel;
import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.text.Format;

import java.util.ArrayList;
import java.util.List;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

/**
 * The originals a mutated skill works with without learning them: the ones it keeps, and the ones a held upgrade lends a
 * level while their own code runs. Each opens its own page, where its settings can be changed.
 */
public final class KeptGui {
  private static final Id KEPT = Id.minecraft("chest");
  private static final int COLUMNS = GuiLayout.WIDTH;

  private KeptGui() {
  }

  /** The kept originals' item for a mutated skill's page; null for any other skill or when it works with none. */
  public static GuiElement entry(Skill<?> skill, PlayerRef player) {
    if (!(skill instanceof MutatedSkill mutated)) {
      return null;
    }
    List<Held> held = held(mutated, player);
    if (held.isEmpty()) {
      return null;
    }
    GuiElement element = new GuiElement("mutation-kept")
        .setMaterial(KEPT)
        .setName(TextFormat.GREEN + AdaptLanguage.text(MutatedMessages.KEPT))
        .setRole(MenuRole.NAVIGATION)
        .onLeftClick((MenuClick e) -> open(player, mutated));
    for (Held entry : held) {
      element.addLore(TextFormat.GRAY + entry.line());
    }
    return element;
  }

  /** Lists the originals; picking one opens its page. */
  public static void open(PlayerRef player, MutatedSkill mutated) {
    if (!AdaptApi.scheduler().isTickThread()) {
      AdaptApi.scheduler().runEntity(player, () -> open(player, mutated));
      return;
    }
    List<Held> held = held(mutated, player);
    int rows = Math.max(1, (held.size() + COLUMNS - 1) / COLUMNS);
    GuiWindow window = new GuiWindow(player);
    GuiTheme.apply(window, "skill/" + SkillHost.lineKey(mutated));
    window.setViewportHeight(rows + 1);
    window.setLayout(MenuLayout.GRID);
    for (int index = 0; index < held.size(); index++) {
      Held entry = held.get(index);
      Adaptation<?> adaptation = entry.adaptation();
      int row = index / COLUMNS;
      int inRow = Math.min(COLUMNS, held.size() - row * COLUMNS);
      CustomModel model = GuiConfig.adaptationModel(adaptation.getId().toString(), adaptation.getIcon().item(), ComponentHost.of(adaptation).getModel());
      window.setElement(GuiLayout.centeredPosition(index % COLUMNS, inRow), row, new GuiElement("kept-" + adaptation.getId().toString())
          .setMaterial(model.material())
          .setModel(model)
          .setName(adaptation.getDisplayName(entry.level()))
          .addLore(TextFormat.GREEN + entry.state())
          .addLore(TextFormat.GRAY + adaptation.getDescription())
          .setRole(MenuRole.NAVIGATION)
          .onLeftClick((MenuClick e) -> ComponentHost.of(adaptation).openGui(player)));
    }
    window.setElement(0, rows, GuiTheme.element("kept-back", Id.minecraft("arrow"), "gui", "navigation", "back")
        .setName(TextFormat.GRAY + AdaptLanguage.text(SnippetsMessages.GUI_BACK))
        .onLeftClick((MenuClick e) -> ComponentHost.of(mutated).openGui(player)));
    window.setTitle(TextFormat.GRAY + AdaptLanguage.text(MutatedMessages.KEPT));
    window.open();
  }

  private static List<Held> held(MutatedSkill mutated, PlayerRef player) {
    List<Held> held = new ArrayList<>();
    AdaptCore core = AdaptCore.current();
    AdaptPlayer adaptPlayer = core == null || core.getAdaptServer() == null || player == null ? null
        : core.getAdaptServer().getPlayer(player);
    for (Adaptation<?> adaptation : mutated.getKept()) {
      int kept = SkillUpgrades.keptLevel(adaptation);
      int level = adaptPlayer == null ? kept : Math.max(kept, SkillUpgrades.heritageLevel(adaptation, adaptPlayer.getData()));
      held.add(new Held(adaptation, kept, level, null));
    }
    if (adaptPlayer != null) {
      for (Lending.Running running : Lending.running(player, adaptPlayer, core.getAdaptServer().getSkillRegistry(),
          mutated.getPair())) {
        held.add(new Held(running.original(), running.level(), running.level(), running.upgrade()));
      }
    }
    return held;
  }

  /**
   * An original the player works with at {@code level}, lent by {@code upgrade} or kept at {@code kept} when that is
   * null; a kept original above {@code kept} was raised with knowledge.
   */
  private record Held(Adaptation<?> adaptation, int kept, int level, Adaptation<?> upgrade) {
    String line() {
      return upgrade == null && level == kept ? MutatedSkill.keptLine(adaptation)
          : TextFormat.strip(adaptation.getDisplayName()) + " " + Format.toRoman(level);
    }

    String state() {
      if (upgrade != null) {
        return AdaptLanguage.text(MutatedMessages.LENT_STATE, trusted("level", Format.toRoman(level)),
            trusted("upgrade", TextFormat.strip(upgrade.getDisplayName())));
      }
      return level > kept
          ? AdaptLanguage.text(MutatedMessages.RAISED_STATE, trusted("kept", Format.toRoman(kept)), trusted("level", Format.toRoman(level)))
          : AdaptLanguage.text(MutatedMessages.KEPT_STATE, trusted("level", Format.toRoman(level)));
    }
  }
}
