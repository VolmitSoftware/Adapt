package art.arcane.adapt.engine.skill;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.SchedulerForwarding;
import art.arcane.adapt.TestCores;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.content.SkillConfig;
import art.arcane.adapt.engine.player.PlayerApi;
import art.arcane.adapt.engine.preference.PlayerPreferences;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.world.AdaptServer;
import art.arcane.adapt.engine.world.PlayerData;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.menu.ClickKind;
import art.arcane.adapt.platform.menu.MenuButton;
import art.arcane.adapt.platform.menu.MenuClick;
import art.arcane.adapt.platform.menu.MenuHint;
import art.arcane.adapt.platform.menu.MenuRole;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.inventorygui.GuiElement;
import art.arcane.adapt.util.common.inventorygui.GuiWindow;
import art.arcane.adapt.util.common.misc.CustomModel;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.volmlib.util.collection.KList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.lenient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SkillPreferenceGuiSupportTest {
  private MockedStatic<CustomModel> models;
  private MockedStatic<Tasks> scheduler;
  private MockedStatic<AdaptLanguage> language;
  private SkillRegistry registry;
  private Skill<?> skill;
  private SkillConfig config;
  private Adaptation<?> adaptation;
  private AdaptPlayer owner;
  private PlayerData data;
  private GuiWindow window;
  private GuiElement toggle;
  private GuiElement reset;
  private PlayerRef player;

  @BeforeEach
  void prepare() {
    models = mockStatic(CustomModel.class, invocation -> {
      Id material = invocation.getArgument(0);
      CustomModel model = mock(CustomModel.class);
      when(model.material()).thenReturn(material);
      return model;
    });
    AdaptCore core = TestCores.install();
    scheduler = SchedulerForwarding.mockTasks();
    language = mockStatic(AdaptLanguage.class,
        invocation -> invocation.getMethod().getReturnType() == String.class ? "Text" : null);
    player = mock(PlayerRef.class);
    UUID playerId = UUID.randomUUID();
    when(player.uuid()).thenReturn(playerId);
    when(player.isOnline()).thenReturn(true);
    scheduler.when(() -> Tasks.isOwnedByCurrentRegion(player)).thenReturn(true);
    owner = mock(AdaptPlayer.class);
    data = new PlayerData();
    data.setEffectsEnabled(false);
    when(owner.getData()).thenReturn(data);
    when(owner.getPlayer()).thenReturn(player);
    when(owner.isRuntimeReady()).thenReturn(true);
    skill = mock(Skill.class);
    adaptation = mock(Adaptation.class);
    config = new SkillConfig();
    lenient().when(skill.getId()).thenReturn(Id.adapt("agility"));
    when(skill.isEnabled()).thenReturn(true);
    AdaptServer server = mock(AdaptServer.class);
    registry = mock(SkillRegistry.class);
    TestCores.setField(core, "adaptServer", server);
    when(server.getSkillRegistry()).thenReturn(registry);
    lenient().when(server.getPlayer(player)).thenReturn(owner);
    doReturn(skill).when(registry).getSkill("agility");
    lenient().doReturn(skill).when(registry).getSkill(Id.parseAdapt("agility").toString());
    lenient().doReturn(skill).when(registry).getSkill(Id.parseAdapt("agility"));

    when(skill.hasUsePermission(player)).thenReturn(true);
    when(skill.getAdaptations()).thenReturn(new KList<>(List.of(adaptation)));
    doReturn(config).when(skill).getConfig();
    PlayerPreferences.validate(skill, config);
    window = mock(GuiWindow.class);
    when(window.getViewer()).thenReturn(player);
    when(window.isVisible()).thenReturn(true);
    SkillPreferenceGuiSupport.populate(skill, window, 3);
    ArgumentCaptor<GuiElement> elements = ArgumentCaptor.forClass(GuiElement.class);
    verify(window).setElement(eq(-1), eq(3), elements.capture());
    toggle = elements.getValue();
    verify(window).setElement(eq(0), eq(3), elements.capture());
    reset = elements.getValue();
    clearInvocations(window);
  }

  @AfterEach
  void closeMocks() {
    models.close();
    language.close();
    scheduler.close();
    TestCores.clear();
  }

  @Test
  void resetAndSkillToggleUseTheirConfiguredModelPaths() {
    models.verify(() -> CustomModel.get(Id.minecraft("milk_bucket"), "gui", "preferences", "reset"));
    models.verify(() -> CustomModel.get(Id.minecraft("lime_stained_glass_pane"),
        "gui", "preferences", "skill", "adapt:agility", "enabled", "on"));
  }

  @Test
  void retiredRegistryControlsAreSilent() {
    Skill<?> retired = mock(Skill.class);
    doReturn(retired).when(registry).getSkill("agility");
    doReturn(retired).when(registry).getSkill(Id.parseAdapt("agility").toString());
    doReturn(retired).when(registry).getSkill(Id.parseAdapt("agility"));
    click(toggle, ClickKind.LEFT);
    click(reset, ClickKind.LEFT);
    verify(owner, never()).requestSave();
    verify(window, never()).setElement(anyInt(), anyInt(), any());
    verify(window, never()).update();
  }

  @Test
  void disablingSkillUpdatesExistingControlsAndReconcilesChildren() {
    click(toggle, ClickKind.LEFT);
    assertThat(data.getPreferences().getSkill("adapt:agility", "enabled")).isEqualTo("OFF");
    verify(owner).requestSave();
    verify(adaptation).onPlayerPreferencesChanged(PlayerApi.profile(owner));
    ArgumentCaptor<GuiElement> replacement = ArgumentCaptor.forClass(GuiElement.class);
    verify(window).setElement(eq(-1), eq(3), replacement.capture());
    assertThat(replacement.getValue().getMaterial()).isEqualTo(Id.minecraft("red_stained_glass_pane"));
    verify(window).setElement(eq(0), eq(3), any(GuiElement.class));
    verify(window).update();
    verify(window, never()).open();
    verify(window, never()).close();
  }

  @Test
  void resetSkillPreservesIndividualAdaptationChoices() {
    data.getPreferences().setSkill("adapt:agility", "enabled", "OFF");
    data.getPreferences().set("adapt:agility/air_dash", "enabled", "OFF");
    click(reset, ClickKind.LEFT);
    assertThat(data.getPreferences().getSkill("adapt:agility", "enabled")).isNull();
    assertThat(data.getPreferences().get("adapt:agility/air_dash", "enabled")).isEqualTo("OFF");
  }

  @Test
  void policyChangesAreCheckedAtClickTime() {
    config.playerPreferences.get("enabled").playerEditable = false;
    click(toggle, ClickKind.LEFT);
    assertThat(data.getPreferences().getSkill("adapt:agility", "enabled")).isNull();
    verify(owner, never()).requestSave();
    ArgumentCaptor<GuiElement> replacement = ArgumentCaptor.forClass(GuiElement.class);
    verify(window).setElement(eq(-1), eq(3), replacement.capture());
    assertThat(replacement.getValue().getMaterial()).isEqualTo(Id.minecraft("gray_stained_glass_pane"));
    verify(window).update();
  }

  @Test
  void staleWindowClicksCannotChangeTheSkill() {
    when(window.isVisible()).thenReturn(false);
    click(toggle, ClickKind.LEFT);
    verify(owner, never()).requestSave();
    verify(window, never()).setElement(anyInt(), anyInt(), any());
    verify(window, never()).update();
  }

  @Test
  void toggleIsASettingAndResetAnAction() {
    MenuButton button = toggle.button();

    assertThat(button.icon().type()).isEqualTo(Id.minecraft("lime_stained_glass_pane"));
    assertThat(button.icon().lore()).containsExactly("Text", "Text");
    assertThat(button.hint().kind()).isEqualTo(MenuHint.Kind.TOGGLE);
    assertThat(button.meta().role()).isEqualTo(MenuRole.SETTING);
    assertThat(button.meta().description()).isEmpty();
    assertThat(reset.button().meta().role()).isEqualTo(MenuRole.ACTION);
  }

  @Test
  void lockedToggleIsInfoWithItsValue() {
    config.playerPreferences.get("enabled").playerEditable = false;
    SkillPreferenceGuiSupport.populate(skill, window, 3);
    ArgumentCaptor<GuiElement> locked = ArgumentCaptor.forClass(GuiElement.class);
    verify(window).setElement(eq(-1), eq(3), locked.capture());

    MenuButton button = locked.getValue().button();
    assertThat(button.hint()).isNull();
    assertThat(button.meta().role()).isEqualTo(MenuRole.INFO);
    assertThat(button.meta().value()).isEqualTo("Text");
    assertThat(button.meta().description()).containsExactly("Text");
  }

  private void click(GuiElement element, ClickKind kind) {
    element.button().action().click(new MenuClick(player, null, 0, kind));
  }
}
