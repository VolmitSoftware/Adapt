package art.arcane.adapt.api.skill;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreferences;
import art.arcane.adapt.api.preference.PreferencePolicy;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.GuiMessages;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.inventorygui.GuiTheme;
import art.arcane.adapt.util.common.misc.SoundPlayer;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.inventorygui.UIWindow;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.Locale;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

final class SkillPreferenceGuiSupport {
  private final Skill<?> skill;
  private final UIWindow window;
  private final int row;

  private SkillPreferenceGuiSupport(Skill<?> skill, Placement placement) {
    this.skill = skill;
    window = placement.window();
    row = placement.row();
  }

  static void populate(Skill<?> skill, UIWindow window, int row) {
    new SkillPreferenceGuiSupport(skill, new Placement(window, row)).render(false);
  }

  private void render(boolean update) {
    AdaptPlayer player = skill.getPlayer(window.getViewer());
    if (player == null || !player.isRuntimeReady()) {
      return;
    }
    CommonPreferences.Toggle value = PlayerPreferences.resolve(skill, player.getData(), CommonPreferences.SKILL_ENABLED);
    PreferencePolicy policy = PlayerPreferences.policy(skill, CommonPreferences.SKILL_ENABLED);
    boolean locked = !policy.playerEditable || policy.allowedValues.size() < 2;
    Element toggle = GuiTheme.element("skill-preference-enabled", locked ? Material.GRAY_STAINED_GLASS_PANE
            : CommonPreferences.SKILL_ENABLED.choice(value).icon(),
            "gui", "preferences", "skill", skill.getName(), "enabled",
            locked ? "locked" : value.name().toLowerCase(Locale.ROOT))
        .setName(AdaptLanguage.textStyled(C.WHITE.toString(), CommonPreferences.SKILL_ENABLED.label()))
        .addLore(AdaptLanguage.textStyled(C.WHITE.toString(), GuiMessages.PREFERENCE_CURRENT,
            trusted("value", AdaptLanguage.text(CommonPreferences.SKILL_ENABLED.choice(value).label()))))
        .addLore(AdaptLanguage.textStyled(C.GRAY.toString(), locked ? GuiMessages.PREFERENCE_SERVER_CONTROLLED : GuiMessages.PREFERENCE_CYCLE))
        .onLeftClick(element -> onOwner(false))
        .onRightClick(element -> onOwner(false));
    Element reset = GuiTheme.element("skill-preference-reset", Material.MILK_BUCKET, "gui", "preferences", "reset")
        .setName(AdaptLanguage.textStyled(C.WHITE.toString(), GuiMessages.PREFERENCE_RESET))
        .addLore(AdaptLanguage.textStyled(C.GRAY.toString(), GuiMessages.PREFERENCE_RESET_DESCRIPTION))
        .onLeftClick(element -> onOwner(true));
    if (update) {
      window.updateElement(-1, row, toggle);
      window.updateElement(1, row, reset);
    } else {
      window.setElement(-1, row, toggle);
      window.setElement(1, row, reset);
    }
  }

  private void onOwner(boolean reset) {
    Player viewer = window.getViewer();
    if (!J.isOwnedByCurrentRegion(viewer)) {
      J.runEntity(viewer, () -> onOwner(reset));
      return;
    }
    if (!viewer.isOnline() || !window.isVisible() || !skill.isEnabled()
        || Adapt.instance.getGuiLeftovers().get(viewer.getUniqueId().toString()) != window
        || Adapt.instance.getAdaptServer().getSkillRegistry().getSkill(skill.getName()) != skill
        || !skill.hasUsePermission(viewer, skill)) {
      return;
    }
    AdaptPlayer player = skill.getPlayer(viewer);
    if (player == null || !player.isRuntimeReady()) {
      return;
    }
    CommonPreferences.Toggle current = PlayerPreferences.resolve(skill, player.getData(), CommonPreferences.SKILL_ENABLED);
    boolean accepted = reset ? PlayerPreferences.resetSkill(skill, player)
        : PlayerPreferences.setSkillEnabled(skill, player, current == CommonPreferences.Toggle.ON
            ? CommonPreferences.Toggle.OFF : CommonPreferences.Toggle.ON);
    render(true);
    if (player.getData().isEffectsEnabled() && skill.areSoundsEnabled()) {
      SoundPlayer.of(viewer).play(viewer.getLocation(), accepted ? Sound.UI_BUTTON_CLICK : Sound.BLOCK_NOTE_BLOCK_BASS,
          0.3F, accepted ? (reset ? 0.9F : 1.2F) : 0.7F);
    }
  }

  private record Placement(UIWindow window, int row) {
  }
}
