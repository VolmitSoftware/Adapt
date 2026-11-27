package art.arcane.adapt.engine.skill;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PreferencePolicy;
import art.arcane.adapt.engine.preference.PlayerPreferences;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.GuiMessages;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.menu.MenuClick;
import art.arcane.adapt.platform.menu.MenuHint;
import art.arcane.adapt.platform.menu.MenuRole;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.inventorygui.GuiElement;
import art.arcane.adapt.util.common.inventorygui.GuiTheme;
import art.arcane.adapt.util.common.inventorygui.GuiWindow;
import art.arcane.adapt.util.common.misc.SoundPlayer;
import art.arcane.adapt.util.common.scheduling.Tasks;

import java.util.List;
import java.util.Locale;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

final class SkillPreferenceGuiSupport {
  private static final Id GRAY_PANE = Id.minecraft("gray_stained_glass_pane");
  private static final Id MILK_BUCKET = Id.minecraft("milk_bucket");

  private final Skill<?> skill;
  private final GuiWindow window;
  private final int row;

  private SkillPreferenceGuiSupport(Skill<?> skill, Placement placement) {
    this.skill = skill;
    window = placement.window();
    row = placement.row();
  }

  static void populate(Skill<?> skill, GuiWindow window, int row) {
    new SkillPreferenceGuiSupport(skill, new Placement(window, row)).render(false);
  }

  private void render(boolean update) {
    if (!AdaptConfig.get().isPlayerSettingsEnabled()) {
      if (update) {
        window.setElement(-1, row, null);
        window.setElement(1, row, null);
        window.update();
      }
      return;
    }
    AdaptPlayer player = AdaptCore.get().getAdaptServer().getPlayer(window.getViewer());
    if (player == null || !player.isRuntimeReady()) {
      return;
    }
    CommonPreferences.Toggle value = PlayerPreferences.resolve(skill, player.getData(), CommonPreferences.SKILL_ENABLED);
    PreferencePolicy policy = PlayerPreferences.policy(skill, CommonPreferences.SKILL_ENABLED);
    boolean locked = !policy.playerEditable || policy.allowedValues.size() < 2;
    String owner = PlayerPreferences.ownerKey(skill);
    String current = PlayerPreferences.shown(PlayerPreferences.label(owner, CommonPreferences.SKILL_ENABLED, value));
    GuiElement toggle = GuiTheme.element("skill-preference-enabled", locked ? GRAY_PANE
            : PlayerPreferences.shownIcon(CommonPreferences.SKILL_ENABLED.choice(value)),
            "gui", "preferences", "skill", SkillHost.lineKey(skill), "enabled",
            locked ? "locked" : value.name().toLowerCase(Locale.ROOT))
        .setName(PlayerPreferences.shown(C.WHITE.toString(), PlayerPreferences.label(owner, CommonPreferences.SKILL_ENABLED)))
        .addLore(AdaptLanguage.textStyled(C.WHITE.toString(), GuiMessages.PREFERENCE_CURRENT,
            trusted("value", current)))
        .addLore(AdaptLanguage.textStyled(C.GRAY.toString(), locked ? GuiMessages.PREFERENCE_SERVER_CONTROLLED : GuiMessages.PREFERENCE_CYCLE))
        .onLeftClick((MenuClick element) -> onOwner(false))
        .onRightClick((MenuClick element) -> onOwner(false));
    if (locked) {
      toggle.setRole(MenuRole.INFO)
          .setValue(current)
          .setDescription(List.of(AdaptLanguage.textStyled(C.GRAY.toString(), GuiMessages.PREFERENCE_SERVER_CONTROLLED)));
    } else {
      toggle.setHint(MenuHint.toggle(value == CommonPreferences.Toggle.ON))
          .setRole(MenuRole.SETTING)
          .setDescription(List.of());
    }
    GuiElement reset = GuiTheme.element("skill-preference-reset", MILK_BUCKET, "gui", "preferences", "reset")
        .setName(AdaptLanguage.textStyled(C.WHITE.toString(), GuiMessages.PREFERENCE_RESET))
        .addLore(AdaptLanguage.textStyled(C.GRAY.toString(), GuiMessages.PREFERENCE_RESET_DESCRIPTION))
        .setRole(MenuRole.ACTION)
        .onLeftClick((MenuClick element) -> onOwner(true));
    window.setElement(-1, row, toggle);
    window.setElement(1, row, reset);
    if (update) {
      window.update();
    }
  }

  private void onOwner(boolean reset) {
    PlayerRef viewer = window.getViewer();
    if (!Tasks.isOwnedByCurrentRegion(viewer)) {
      Tasks.runEntity(viewer, () -> onOwner(reset));
      return;
    }
    if (!AdaptConfig.get().isPlayerSettingsEnabled()) {
      render(true);
      return;
    }
    if (!viewer.isOnline() || !window.isVisible() || !skill.isEnabled()
        || AdaptCore.get().getAdaptServer().getSkillRegistry().getSkill(SkillHost.lineKey(skill)) != skill
        || !skill.hasUsePermission(viewer)) {
      return;
    }
    AdaptPlayer player = AdaptCore.get().getAdaptServer().getPlayer(viewer);
    if (player == null || !player.isRuntimeReady()) {
      return;
    }
    CommonPreferences.Toggle current = PlayerPreferences.resolve(skill, player.getData(), CommonPreferences.SKILL_ENABLED);
    boolean accepted = reset ? PlayerPreferences.resetSkill(skill, player)
        : PlayerPreferences.setSkillEnabled(skill, player, current == CommonPreferences.Toggle.ON
            ? CommonPreferences.Toggle.OFF : CommonPreferences.Toggle.ON);
    render(true);
    if (player.getData().isEffectsEnabled() && skill.areSoundsEnabled()) {
      SoundPlayer.of(viewer).play(viewer.location(), accepted ? Sounds.UI_BUTTON_CLICK : Sounds.BLOCK_NOTE_BLOCK_BASS,
          0.3F, accepted ? (reset ? 0.9F : 1.2F) : 0.7F);
    }
  }

  private record Placement(GuiWindow window, int row) {
  }
}
