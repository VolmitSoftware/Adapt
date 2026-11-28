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
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.menu.MenuClick;
import art.arcane.adapt.platform.menu.MenuLayout;
import art.arcane.adapt.platform.menu.MenuRole;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.util.common.inventorygui.GuiConfig;
import art.arcane.adapt.util.common.inventorygui.GuiElement;
import art.arcane.adapt.util.common.inventorygui.GuiFrame;
import art.arcane.adapt.util.common.misc.MenuSounds;
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
  private static final int COLUMNS = 7;

  private KeptGui() {
  }

  /** The kept originals' item for a mutated skill's footer; null for any other skill or when it works with none. */
  public static GuiElement entry(Skill<?> skill, PlayerRef player) {
    if (!(skill instanceof MutatedSkill mutated)) {
      return null;
    }
    List<Held> held = held(mutated, player);
    if (held.isEmpty()) {
      return null;
    }
    List<String> names = new ArrayList<>(held.size());
    for (Held entry : held) {
      names.add(entry.line());
    }
    return GuiTheme.element("mutation-kept", KEPT, "gui", "navigation", "kept")
        .setName(TextFormat.GREEN + AdaptLanguage.text(MutatedMessages.KEPT))
        .addLore(LoreLists.lines(TextFormat.GRAY.toString(), names))
        .setRole(MenuRole.NAVIGATION)
        .onLeftClick((MenuClick e) -> open(player, mutated, 0));
  }

  /** Lists the originals; picking one opens its page. */
  public static void open(PlayerRef player, MutatedSkill mutated, int page) {
    if (!AdaptApi.scheduler().isTickThread()) {
      AdaptApi.scheduler().runEntity(player, () -> open(player, mutated, page));
      return;
    }
    MenuSounds.play(player, MenuSounds.Action.OPEN);
    List<Held> held = held(mutated, player);
    GuiFrame.Layout<Held> layout = GuiFrame.grid(held, COLUMNS);
    int currentPage = layout.clampedPage(page);
    GuiWindow window = new GuiWindow(player);
    GuiFrame.apply(window, "skill/" + SkillHost.lineKey(mutated), layout.bodyRows());
    window.setLayout(MenuLayout.GRID);
    GuiFrame.subject(window, SkillCards.icon("kept-skill", mutated).setEnchanted(true).setRole(MenuRole.HEADER));
    GuiFrame.body(window, layout.page(currentPage), (GuiFrame.Cell<Held> cell) -> element(cell.item(), player));
    GuiFrame.back(window, () -> ComponentHost.of(mutated).openGui(player));
    GuiFrame.pager(window, currentPage, layout.pageCount(), held.size(), (int target) -> open(player, mutated, target));
    window.setTitle(AdaptLanguage.text(MutatedMessages.KEPT));
    window.open();
  }

  private static GuiElement element(Held entry, PlayerRef player) {
    Adaptation<?> adaptation = entry.adaptation();
    CustomModel model = GuiConfig.adaptationModel(adaptation.getId().toString(), adaptation.getIcon().item(), ComponentHost.of(adaptation).getModel());
    return new GuiElement("kept-" + adaptation.getId().toString())
        .setMaterial(model.material())
        .setModel(model)
        .setName(adaptation.getDisplayName(entry.level()))
        .setEnchanted(true)
        .addLore(TextFormat.GREEN + entry.state())
        .addLore(TextFormat.GRAY + adaptation.getDescription())
        .setRole(MenuRole.NAVIGATION)
        .onLeftClick((MenuClick e) -> ComponentHost.of(adaptation).openGui(player));
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
