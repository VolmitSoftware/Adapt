package art.arcane.adapt.engine.adaptation;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.SchedulerForwarding;
import art.arcane.adapt.TestCores;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.preference.PreferencePolicy;
import art.arcane.adapt.api.preference.PreferenceToggleGroup;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.client.protocol.HintKind;
import art.arcane.adapt.client.protocol.PanelLayout;
import art.arcane.adapt.client.protocol.PanelTile;
import art.arcane.adapt.client.protocol.PanelView;
import art.arcane.adapt.client.protocol.TileRole;
import art.arcane.adapt.client.server.panel.PanelViews;
import art.arcane.adapt.engine.player.PlayerApi;
import art.arcane.adapt.engine.preference.PlayerPreferenceData;
import art.arcane.adapt.engine.preference.PlayerPreferences;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.world.AdaptServer;
import art.arcane.adapt.engine.world.PlayerData;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.GuiMessages;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.menu.ChestMenu;
import art.arcane.adapt.platform.menu.ClickKind;
import art.arcane.adapt.platform.menu.MenuButton;
import art.arcane.adapt.platform.menu.MenuClick;
import art.arcane.adapt.platform.menu.MenuHint;
import art.arcane.adapt.platform.menu.MenuIcon;
import art.arcane.adapt.platform.menu.MenuRole;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.testkit.MockedHosts;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.inventorygui.GuiElement;
import art.arcane.adapt.util.common.inventorygui.GuiWindow;
import art.arcane.adapt.util.common.misc.CustomModel;
import art.arcane.adapt.util.common.misc.SoundPlayer;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.volmlib.util.collection.KList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.lenient;
import static art.arcane.volmlib.util.localization.MessageArgument.trusted;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PreferenceGuiSupportTest {
  private static final PlayerPreference<Mode> PREFERENCE = PlayerPreference.of("mode", Mode.class, Mode.OFF, List.of(
      new PlayerPreference.Choice<>(Mode.OFF, Text.key("test.off"), Id.minecraft("red_stained_glass_pane"), 1),
      new PlayerPreference.Choice<>(Mode.ON, Text.key("test.on"), Id.minecraft("lime_stained_glass_pane"), 1),
      new PlayerPreference.Choice<>(Mode.REACTIVE, Text.key("test.reactive"), Id.minecraft("ender_pearl"), 2)))
      .labelled(Text.key("test.mode"));

  private static final PlayerPreference<CommonPreferences.Toggle> WHEAT = CommonPreferences.toggle("growable-wheat",
      Text.key("test.wheat"));
  private static final PlayerPreference<CommonPreferences.Toggle> GLOW_BERRIES = CommonPreferences.toggle("growable-glow-berries",
      Text.key("test.glow-berries"));
  private static final PlayerPreference<CommonPreferences.Toggle> KELP = CommonPreferences.toggle("growable-kelp",
      Text.key("test.kelp"));
  private static final PreferenceToggleGroup GROWABLES = new PreferenceToggleGroup("growables", Text.key("test.growables"),
      Id.minecraft("wheat_seeds"), List.of(new PreferenceToggleGroup.Entry(WHEAT, Id.minecraft("wheat")),
      new PreferenceToggleGroup.Entry(GLOW_BERRIES, Id.minecraft("glow_berries")), new PreferenceToggleGroup.Entry(KELP, Id.minecraft("kelp"))));

  private MockedHosts hosts;
  private AdaptationHost host;
  private MockedStatic<CustomModel> models;
  private MockedStatic<Tasks> scheduler;
  private MockedStatic<AdaptLanguage> language;
  private MockedStatic<SoundPlayer> soundPlayers;
  private SoundPlayer sounds;
  private SkillRegistry registry;
  private Skill<?> skill;
  private PlayerRef player;
  private PlayerData data;
  private Adaptation<AdaptationConfig> adaptation;
  private AdaptPlayer adaptPlayer;
  private GuiWindow window;
  private PlayerPreferenceData preferences;
  private PreferencePolicy policy;
  private GuiElement control;
  private GuiElement reset;

  @BeforeEach
  @SuppressWarnings("unchecked")
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
    when(player.uuid()).thenReturn(UUID.randomUUID());
    when(player.isOnline()).thenReturn(true);
    when(player.location()).thenReturn(new Loc(mock(WorldRef.class), 0, 64, 0));
    sounds = mock(SoundPlayer.class);
    soundPlayers = mockStatic(SoundPlayer.class);
    soundPlayers.when(() -> SoundPlayer.of(player)).thenReturn(sounds);
    scheduler.when(() -> Tasks.isOwnedByCurrentRegion(player)).thenReturn(true);
    window = mock(GuiWindow.class);
    when(window.getViewer()).thenReturn(player);
    when(window.isVisible()).thenReturn(true);
    adaptation = mock(Adaptation.class);
    hosts = new MockedHosts();
    host = hosts.adaptation(adaptation);
    lenient().when(host.textKey()).thenReturn("adapt.test-adaptation");
    skill = mock(Skill.class);
    lenient().when(skill.getId()).thenReturn(Id.adapt("test-skill"));
    doReturn(skill).when(adaptation).getSkill();
    when(skill.getAdaptations()).thenReturn(new KList<>(List.of(adaptation)));
    when(skill.isEnabled()).thenReturn(true);
    AdaptServer server = mock(AdaptServer.class);
    registry = mock(SkillRegistry.class);
    TestCores.setField(core, "adaptServer", server);
    when(server.getSkillRegistry()).thenReturn(registry);
    doReturn(skill).when(registry).getSkill("test-skill");
    lenient().doReturn(skill).when(registry).getSkill(Id.parseAdapt("test-skill").toString());
    lenient().doReturn(skill).when(registry).getSkill(Id.parseAdapt("test-skill"));

    when(skill.hasUsePermission(player)).thenReturn(true);
    when(adaptation.isEnabled()).thenReturn(true);
    when(adaptation.areSoundsEnabled()).thenReturn(true);
    lenient().when(adaptation.getId()).thenReturn(Id.adapt("test-adaptation"));
    when(adaptation.hasUsePermission(player)).thenReturn(true);
    when(adaptation.getPlayerPreferences()).thenReturn(List.of(PREFERENCE));
    when(adaptation.isPlayerPreferenceVisible(any(), any())).thenCallRealMethod();
    AdaptationConfig config = new AdaptationConfig();
    policy = PreferencePolicy.defaults(PREFERENCE);
    config.playerPreferences = Map.of(PREFERENCE.id(), policy);
    when(adaptation.getConfig()).thenReturn(config);
    adaptPlayer = mock(AdaptPlayer.class);
    when(AdaptCore.get().getAdaptServer().getPlayer(player)).thenReturn(adaptPlayer);
    when(host.level(adaptPlayer)).thenReturn(1);
    when(adaptPlayer.getPlayer()).thenReturn(player);
    when(adaptPlayer.isRuntimeReady()).thenReturn(true);
    data = mock(PlayerData.class);
    when(data.isEffectsEnabled()).thenReturn(true);
    preferences = new PlayerPreferenceData();
    when(data.getPreferences()).thenReturn(preferences);
    when(adaptPlayer.getData()).thenReturn(data);
    PreferenceGuiSupport.populate(adaptation, window, 2, 0);
    ArgumentCaptor<GuiElement> initial = ArgumentCaptor.forClass(GuiElement.class);
    verify(window).setElement(eq(-1), eq(2), initial.capture());
    control = initial.getValue();
    ArgumentCaptor<GuiElement> resetControl = ArgumentCaptor.forClass(GuiElement.class);
    verify(window).setElement(eq(0), eq(2), resetControl.capture());
    reset = resetControl.getValue();
    clearInvocations(window);
  }

  @AfterEach
  void restore() {
    hosts.close();
    models.close();
    soundPlayers.close();
    language.close();
    scheduler.close();
    TestCores.clear();
  }

  @Test
  void resetAndChoiceItemsUseTheirConfiguredModelPaths() {
    models.verify(() -> CustomModel.get(Id.minecraft("milk_bucket"), "gui", "preferences", "reset"));
    models.verify(() -> CustomModel.get(Id.minecraft("red_stained_glass_pane"),
        "gui", "preferences", "adaptation", "adapt:test-adaptation", "mode", "off"));
  }

  @Test
  void retiredRegistryControlsAreSilent() {
    doReturn(mock(Skill.class)).when(registry).getSkill("adapt:test-skill");
    click(control, ClickKind.LEFT);
    click(reset, ClickKind.LEFT);
    verify(adaptPlayer, never()).requestSave();
    verify(window, never()).updateElement(anyInt(), anyInt(), any());
    verify(sounds, never()).play(any(Loc.class), any(Id.class), anyFloat(), anyFloat());
  }

  @Test
  void clickPersistsOnlyThisPreferenceAndUpdatesItsSlot() {
    click(control, ClickKind.LEFT);

    assertThat(preferences.get("adapt:test-adaptation", "mode")).isEqualTo("ON");
    verify(adaptPlayer).requestSave();
    ArgumentCaptor<GuiElement> replacement = ArgumentCaptor.forClass(GuiElement.class);
    verify(window).updateElement(eq(-1), eq(2), replacement.capture());
    assertThat(replacement.getValue().getMaterial()).isEqualTo(Id.minecraft("lime_stained_glass_pane"));
    verify(window, never()).setElement(anyInt(), anyInt(), any());
    verify(window, never()).open();
    verify(window, never()).close();
    verify(sounds).play(player.location(), Sounds.UI_BUTTON_CLICK, 0.3F, 1.2F);
  }

  @Test
  void reverseCycleSkipsTheLockedReactiveChoice() {
    click(control, ClickKind.RIGHT);
    assertThat(preferences.get("adapt:test-adaptation", "mode")).isEqualTo("ON");
  }

  @Test
  void policyChangeAfterOpeningIsAppliedBeforeSaving() {
    policy.playerEditable = false;
    click(control, ClickKind.LEFT);

    assertThat(preferences.get("adapt:test-adaptation", "mode")).isNull();
    verify(adaptPlayer, never()).requestSave();
    ArgumentCaptor<GuiElement> replacement = ArgumentCaptor.forClass(GuiElement.class);
    verify(window).updateElement(eq(-1), eq(2), replacement.capture());
    assertThat(replacement.getValue().getMaterial()).isEqualTo(Id.minecraft("gray_stained_glass_pane"));
    verify(sounds).play(player.location(), Sounds.BLOCK_NOTE_BLOCK_BASS, 0.3F, 0.7F);
  }

  @Test
  void clickFromAReplacedWindowDoesNothing() {
    when(window.isVisible()).thenReturn(false);
    click(control, ClickKind.LEFT);

    assertThat(preferences.get("adapt:test-adaptation", "mode")).isNull();
    verify(adaptPlayer, never()).requestSave();
    verify(window, never()).updateElement(anyInt(), anyInt(), any());
    soundPlayers.verify(() -> SoundPlayer.of(player), never());
  }

  @Test
  void losingTheLearnedLevelPreventsChanges() {
    when(AdaptationHost.of(adaptation).level(adaptPlayer)).thenReturn(0);
    click(control, ClickKind.LEFT);

    assertThat(preferences.get("adapt:test-adaptation", "mode")).isNull();
    verify(adaptPlayer, never()).requestSave();
    verify(sounds).play(player.location(), Sounds.BLOCK_NOTE_BLOCK_BASS, 0.3F, 0.7F);
  }

  @Test
  void resetAcknowledgesAcceptedDefaultsWithoutReopening() {
    preferences.set("adapt:test-adaptation", "mode", "ON");
    click(reset, ClickKind.LEFT);

    assertThat(preferences.get("adapt:test-adaptation", "mode")).isNull();
    verify(sounds).play(player.location(), Sounds.UI_BUTTON_CLICK, 0.3F, 0.9F);
    verify(window, never()).open();
  }

  @Test
  void resettingAlreadyDefaultSettingsStillAcknowledgesTheClick() {
    click(reset, ClickKind.LEFT);

    verify(adaptPlayer, never()).requestSave();
    verify(sounds).play(player.location(), Sounds.UI_BUTTON_CLICK, 0.3F, 0.9F);
  }

  @Test
  void disabledPlayerEffectsMuteFeedbackWithoutBlockingChanges() {
    when(data.isEffectsEnabled()).thenReturn(false);
    click(control, ClickKind.LEFT);

    assertThat(preferences.get("adapt:test-adaptation", "mode")).isEqualTo("ON");
    soundPlayers.verify(() -> SoundPlayer.of(player), never());
  }

  @Test
  void disabledAdaptationSoundsMuteFeedbackWithoutBlockingChanges() {
    when(adaptation.areSoundsEnabled()).thenReturn(false);
    click(control, ClickKind.LEFT);

    assertThat(preferences.get("adapt:test-adaptation", "mode")).isEqualTo("ON");
    soundPlayers.verify(() -> SoundPlayer.of(player), never());
  }

  @Test
  void globalSoundSettingMutesFeedbackWithoutBlockingChanges() {
    AdaptConfig config = mock(AdaptConfig.class);
    when(config.isPlayerSettingsEnabled()).thenReturn(true);
    AdaptConfig.Effects effects = mock(AdaptConfig.Effects.class);
    when(config.getEffects()).thenReturn(effects);
    when(effects.isSoundsEnabled()).thenReturn(false);
    when(host.soundsEnabled()).thenCallRealMethod();
    when(adaptation.areSoundsEnabled()).thenAnswer(call -> host.soundsEnabled());
    try (MockedStatic<AdaptConfig> configured = mockStatic(AdaptConfig.class)) {
      configured.when(AdaptConfig::get).thenReturn(config);
      click(control, ClickKind.LEFT);
    }

    assertThat(preferences.get("adapt:test-adaptation", "mode")).isEqualTo("ON");
    soundPlayers.verify(() -> SoundPlayer.of(player), never());
  }

  @Test
  void globalLockRejectsPreviouslyRenderedControlsAndResets() {
    AdaptConfig config = mock(AdaptConfig.class);
    try (MockedStatic<AdaptConfig> configured = mockStatic(AdaptConfig.class)) {
      configured.when(AdaptConfig::get).thenReturn(config);
      click(control, ClickKind.LEFT);
      click(reset, ClickKind.LEFT);
    }
    assertThat(preferences.get("adapt:test-adaptation", "mode")).isNull();
    verify(adaptPlayer, never()).requestSave();
    verify(adaptation, never()).onPlayerPreferencesChanged(any(PlayerProfile.class));
  }

  @Test
  void globalLockOmitsSettingsWhenPopulatingAMenu() {
    AdaptConfig config = mock(AdaptConfig.class);
    clearInvocations(window);
    try (MockedStatic<AdaptConfig> configured = mockStatic(AdaptConfig.class)) {
      configured.when(AdaptConfig::get).thenReturn(config);
      PreferenceGuiSupport.populate(adaptation, window, 0, 0);
    }
    verify(window, never()).setElement(anyInt(), anyInt(), any());
  }

  @Test
  void dependentVisibilityPreservesControlAndResetSlotsWithoutReopening() {
    PlayerPreference<Mode> dependent = PlayerPreference.of("dependent", Mode.class, PREFERENCE.defaultValue(), PREFERENCE.choices()).labelled(PREFERENCE.label().orElseThrow());
    PlayerPreference<Mode> other = PlayerPreference.of("other", Mode.class, PREFERENCE.defaultValue(), PREFERENCE.choices()).labelled(PREFERENCE.label().orElseThrow());
    when(adaptation.getPlayerPreferences()).thenReturn(List.of(PREFERENCE, dependent, other));
    when(adaptation.isPlayerPreferenceVisible(eq(PlayerApi.profile(adaptPlayer)), eq(dependent)))
        .thenAnswer(call -> "ON".equals(preferences.get("adapt:test-adaptation", "mode")));
    PreferenceGuiSupport.populate(adaptation, window, 2, 0);
    ArgumentCaptor<GuiElement> toggle = ArgumentCaptor.forClass(GuiElement.class);
    ArgumentCaptor<GuiElement> otherControl = ArgumentCaptor.forClass(GuiElement.class);
    ArgumentCaptor<GuiElement> resetControl = ArgumentCaptor.forClass(GuiElement.class);
    verify(window).setElement(eq(-2), eq(2), toggle.capture());
    verify(window).setElement(eq(0), eq(2), otherControl.capture());
    verify(window).setElement(eq(1), eq(2), resetControl.capture());
    verify(window, never()).setElement(eq(-1), eq(2), any());
    clearInvocations(window);

    click(toggle.getValue(), ClickKind.LEFT);

    ArgumentCaptor<GuiElement> shown = ArgumentCaptor.forClass(GuiElement.class);
    ArgumentCaptor<GuiElement> enabledToggle = ArgumentCaptor.forClass(GuiElement.class);
    verify(window).setElement(eq(-2), eq(2), enabledToggle.capture());
    verify(window).setElement(eq(-1), eq(2), shown.capture());
    verify(window).setElement(eq(0), eq(2), otherControl.capture());
    verify(window).setElement(eq(1), eq(2), resetControl.capture());
    verify(window).update();
    assertThat(enabledToggle.getValue().getId()).isEqualTo("preference-mode");
    assertThat(enabledToggle.getValue().getMaterial()).isEqualTo(Id.minecraft("lime_stained_glass_pane"));
    assertThat(shown.getValue().getId()).isEqualTo("preference-dependent");
    assertThat(otherControl.getValue().getId()).isEqualTo("preference-other");
    assertThat(resetControl.getValue().getId()).isEqualTo("preference-reset");
    clearInvocations(window);

    click(enabledToggle.getValue(), ClickKind.LEFT);

    ArgumentCaptor<GuiElement> disabledToggle = ArgumentCaptor.forClass(GuiElement.class);
    verify(window).setElement(eq(-2), eq(2), disabledToggle.capture());
    verify(window).setElement(-1, 2, null);
    verify(window).setElement(eq(0), eq(2), otherControl.capture());
    verify(window).setElement(eq(1), eq(2), resetControl.capture());
    verify(window).update();
    assertThat(disabledToggle.getValue().getId()).isEqualTo("preference-mode");
    assertThat(disabledToggle.getValue().getMaterial()).isEqualTo(Id.minecraft("red_stained_glass_pane"));
    assertThat(otherControl.getValue().getId()).isEqualTo("preference-other");
    assertThat(resetControl.getValue().getId()).isEqualTo("preference-reset");
    assertThat(preferences.get("adapt:test-adaptation", "mode")).isEqualTo("OFF");
    assertThat(preferences.get("adapt:test-adaptation", "dependent")).isNull();
    verify(window, never()).open();
    verify(window, never()).close();
  }

  @Test
  void preferencePaginationUsesPageSoundAndRejectsBoundaryClicks() {
    List<PlayerPreference<?>> declarations = new ArrayList<>();
    for (int index = 0; index < 9; index++) {
      declarations.add(PlayerPreference.of("mode" + index, Mode.class, PREFERENCE.defaultValue(), PREFERENCE.choices()).labelled(PREFERENCE.label().orElseThrow()));
    }
    when(adaptation.getPlayerPreferences()).thenReturn(declarations);
    PreferenceGuiSupport.populate(adaptation, window, 2, 0);
    ArgumentCaptor<GuiElement> nextControl = ArgumentCaptor.forClass(GuiElement.class);
    verify(window).setElement(eq(3), eq(2), nextControl.capture());
    GuiElement next = nextControl.getValue();

    click(next, ClickKind.LEFT);
    click(next, ClickKind.LEFT);

    verify(sounds).play(player.location(), Sounds.ITEM_BOOK_PAGE_TURN, 0.3F, 1.1F);
    verify(sounds).play(player.location(), Sounds.BLOCK_NOTE_BLOCK_BASS, 0.3F, 0.7F);
    verify(window, never()).open();
  }

  @Test
  void toggleGroupSharesOneSettingsSlotAndCountsOnlyEnabledUnblockedEntries() {
    when(adaptation.getPlayerPreferences()).thenReturn(List.of(PREFERENCE, WHEAT, GLOW_BERRIES, KELP));
    when(adaptation.getPlayerPreferenceGroups()).thenReturn(List.of(GROWABLES));
    when(adaptation.isPlayerPreferenceBlocked(GLOW_BERRIES)).thenReturn(true);
    preferences.set("adapt:test-adaptation", KELP.id(), "OFF");
    AdaptConfig defaults = new AdaptConfig();
    try (MockedStatic<AdaptConfig> configured = mockStatic(AdaptConfig.class)) {
      configured.when(AdaptConfig::get).thenReturn(defaults);
      PreferenceGuiSupport.populate(adaptation, window, 2, 0);
    }

    ArgumentCaptor<GuiElement> mode = ArgumentCaptor.forClass(GuiElement.class);
    ArgumentCaptor<GuiElement> group = ArgumentCaptor.forClass(GuiElement.class);
    ArgumentCaptor<GuiElement> resetControl = ArgumentCaptor.forClass(GuiElement.class);
    verify(window).setElement(eq(-1), eq(2), mode.capture());
    verify(window).setElement(eq(0), eq(2), group.capture());
    verify(window).setElement(eq(1), eq(2), resetControl.capture());
    assertThat(mode.getValue().getId()).isEqualTo("preference-mode");
    assertThat(group.getValue().getId()).isEqualTo("preference-group-growables");
    assertThat(group.getValue().getMaterial()).isEqualTo(Id.minecraft("wheat_seeds"));
    assertThat(resetControl.getValue().getId()).isEqualTo("preference-reset");
    language.verify(() -> AdaptLanguage.textStyled(C.WHITE.toString(), GuiMessages.PREFERENCE_GROUP_ENABLED,
        trusted("enabled", 1), trusted("total", 3)));
  }

  @Test
  void toggleGroupMenuPersistsEachEntryIndependentlyAndUpdatesItsSlot() {
    when(adaptation.getPlayerPreferences()).thenReturn(List.of(PREFERENCE, WHEAT, GLOW_BERRIES, KELP));
    AdaptConfig defaults = new AdaptConfig();
    try (MockedStatic<AdaptConfig> configured = mockStatic(AdaptConfig.class)) {
      configured.when(AdaptConfig::get).thenReturn(defaults);
      PreferenceGroupGui.populate(adaptation, GROWABLES, window, 0);
      ArgumentCaptor<GuiElement> wheat = ArgumentCaptor.forClass(GuiElement.class);
      verify(window).setViewportHeight(3);
      verify(window).setElement(eq(-1), eq(1), wheat.capture());
      verify(window).setElement(eq(0), eq(1), any(GuiElement.class));
      verify(window).setElement(eq(1), eq(1), any(GuiElement.class));
      assertThat(wheat.getValue().getMaterial()).isEqualTo(Id.minecraft("wheat"));
      assertThat(wheat.getValue().isEnchanted()).isTrue();

      click(wheat.getValue(), ClickKind.LEFT);

      assertThat(preferences.get("adapt:test-adaptation", WHEAT.id())).isEqualTo("OFF");
      assertThat(preferences.get("adapt:test-adaptation", KELP.id())).isNull();
      ArgumentCaptor<GuiElement> replacement = ArgumentCaptor.forClass(GuiElement.class);
      verify(window).updateElement(eq(-1), eq(1), replacement.capture());
      assertThat(replacement.getValue().getMaterial()).isEqualTo(Id.minecraft("wheat"));
      assertThat(replacement.getValue().isEnchanted()).isFalse();
      verify(sounds).play(player.location(), Sounds.UI_BUTTON_CLICK, 0.3F, 1.2F);
    }
  }

  @Test
  void serverBlockedEntriesRenderLockedAndRejectChanges() {
    when(adaptation.getPlayerPreferences()).thenReturn(List.of(PREFERENCE, WHEAT, GLOW_BERRIES, KELP));
    when(adaptation.isPlayerPreferenceBlocked(GLOW_BERRIES)).thenReturn(true);
    AdaptConfig defaults = new AdaptConfig();
    try (MockedStatic<AdaptConfig> configured = mockStatic(AdaptConfig.class)) {
      configured.when(AdaptConfig::get).thenReturn(defaults);
      PreferenceGroupGui.populate(adaptation, GROWABLES, window, 0);
      ArgumentCaptor<GuiElement> blocked = ArgumentCaptor.forClass(GuiElement.class);
      verify(window).setElement(eq(0), eq(1), blocked.capture());
      assertThat(blocked.getValue().getMaterial()).isEqualTo(Id.minecraft("gray_stained_glass_pane"));
      assertThat(blocked.getValue().isEnchanted()).isFalse();
      models.verify(() -> CustomModel.get(Id.minecraft("gray_stained_glass_pane"),
          "gui", "preferences", "adaptation", "adapt:test-adaptation", GLOW_BERRIES.id(), "locked"));

      click(blocked.getValue(), ClickKind.LEFT);

      assertThat(preferences.get("adapt:test-adaptation", GLOW_BERRIES.id())).isNull();
      assertThat(PlayerPreferences.set(adaptation, adaptPlayer, GLOW_BERRIES, CommonPreferences.Toggle.OFF)).isFalse();
      assertThat(PlayerPreferences.set(adaptation, adaptPlayer, KELP, CommonPreferences.Toggle.OFF)).isTrue();
      verify(adaptPlayer).requestSave();
      verify(sounds).play(player.location(), Sounds.BLOCK_NOTE_BLOCK_BASS, 0.3F, 0.7F);
    }
  }

  @Test
  void multiChoicePreferenceIsAChoiceSettingWithItsAllowedOptions() {
    MenuButton button = control.button();

    assertThat(button.icon().name()).isEqualTo("Text");
    assertThat(button.icon().lore()).containsExactly("Text", "Text", "Text");
    assertThat(button.clickable()).isTrue();
    assertThat(button.hint().kind()).isEqualTo(MenuHint.Kind.CHOICE);
    assertThat(button.hint().input()).isNull();
    assertThat(button.hint().options()).hasSize(2);
    assertThat(button.meta().role()).isEqualTo(MenuRole.SETTING);
    assertThat(button.meta().value()).isEqualTo("Text");
    assertThat(button.meta().description()).containsExactly("Text");
    assertThat(reset.button().meta().role()).isEqualTo(MenuRole.ACTION);
  }

  @Test
  void serverControlledPreferenceIsInfoWithItsValue() {
    policy.playerEditable = false;
    PreferenceGuiSupport.populate(adaptation, window, 2, 0);
    ArgumentCaptor<GuiElement> locked = ArgumentCaptor.forClass(GuiElement.class);
    verify(window).setElement(eq(-1), eq(2), locked.capture());

    MenuButton button = locked.getValue().button();
    assertThat(button.hint()).isNull();
    assertThat(button.meta().role()).isEqualTo(MenuRole.INFO);
    assertThat(button.meta().value()).isEqualTo("Text");
  }

  @Test
  void toggleGroupMenuIsAFormOfTogglesWithLockedEntriesAsInfo() {
    when(adaptation.getPlayerPreferences()).thenReturn(List.of(PREFERENCE, WHEAT, GLOW_BERRIES, KELP));
    when(adaptation.isPlayerPreferenceBlocked(GLOW_BERRIES)).thenReturn(true);
    GuiWindow real = new GuiWindow(player);
    AdaptConfig defaults = new AdaptConfig();
    try (MockedStatic<AdaptConfig> configured = mockStatic(AdaptConfig.class)) {
      configured.when(AdaptConfig::get).thenReturn(defaults);
      PreferenceGroupGui.populate(adaptation, GROWABLES, real, 0);
    }
    ChestMenu menu = real.build();

    assertThat(menu.rows()).isEqualTo(3);
    assertThat(menu.button(4).icon().type()).isEqualTo(Id.minecraft("wheat_seeds"));
    assertThat(menu.button(18).icon().type()).isEqualTo(Id.minecraft("arrow"));
    for (int slot = 9; slot < 18; slot++) {
      assertThat(menu.button(slot) != null).as("slot %s", slot).isEqualTo(slot >= 12 && slot <= 14);
    }
    MenuIcon wheat = menu.button(12).icon();
    assertThat(wheat.type()).isEqualTo(Id.minecraft("wheat"));
    assertThat(wheat.name()).isEqualTo("Text");
    assertThat(wheat.lore()).containsExactly("Text", "Text");
    assertThat(wheat.glint()).isTrue();
    assertThat(menu.button(13).icon().type()).isEqualTo(Id.minecraft("gray_stained_glass_pane"));
    assertThat(menu.button(13).icon().lore()).containsExactly("Text");
    assertThat(List.of(menu.button(12), menu.button(13), menu.button(14), menu.button(18))).allMatch(MenuButton::clickable);

    PanelView view = PanelViews.view(1, 0, menu);
    assertThat(view.layout()).isEqualTo(PanelLayout.FORM);
    PanelTile wheatTile = tile(view, 12);
    assertThat(wheatTile.role()).isEqualTo(TileRole.SETTING);
    assertThat(wheatTile.hint().kind()).isEqualTo(HintKind.TOGGLE);
    assertThat(wheatTile.description()).isEmpty();
    PanelTile blocked = tile(view, 13);
    assertThat(blocked.role()).isEqualTo(TileRole.INFO);
    assertThat(blocked.hint()).isNull();
    assertThat(blocked.value()).isEqualTo("Text");
    assertThat(PanelViews.chromeTarget(tile(view, 18))).isEqualTo("back");
  }

  private static PanelTile tile(PanelView view, int slot) {
    return view.tiles().stream().filter((PanelTile tile) -> tile.slot() == slot).findFirst().orElseThrow();
  }

  private void click(GuiElement element, ClickKind kind) {
    element.button().action().click(new MenuClick(player, null, 0, kind));
  }

  private enum Mode {
    OFF,
    ON,
    REACTIVE
  }
}
