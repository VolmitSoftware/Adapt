package art.arcane.adapt.api.adaptation;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.preference.PlayerPreferenceData;
import art.arcane.adapt.api.preference.PreferencePolicy;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.AdaptServer;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.world.PlayerData;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.util.common.misc.SoundPlayer;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.inventorygui.ElementEvent;
import art.arcane.volmlib.util.inventorygui.UIWindow;
import art.arcane.volmlib.util.localization.TextKey;
import io.papermc.paper.registry.RegistryAccess;
import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;

class PreferenceGuiSupportTest {
  private static final PlayerPreference<Mode> PREFERENCE = new PlayerPreference<>(Mode.class,
      new PlayerPreference.Definition<>("mode", TextKey.of("test.mode", "Mode"), Mode.OFF, List.of(
          new PlayerPreference.Choice<>(Mode.OFF, TextKey.of("test.off", "Off"), Material.RED_STAINED_GLASS_PANE, 1),
          new PlayerPreference.Choice<>(Mode.ON, TextKey.of("test.on", "On"), Material.LIME_STAINED_GLASS_PANE, 1),
          new PlayerPreference.Choice<>(Mode.REACTIVE, TextKey.of("test.reactive", "Reactive"), Material.ENDER_PEARL, 2))));

  private Adapt previousPlugin;
  private MockedStatic<J> scheduler;
  private MockedStatic<AdaptLanguage> language;
  private MockedStatic<SoundPlayer> soundPlayers;
  private SoundPlayer sounds;
  private SkillRegistry registry;
  private Skill<?> skill;
  private Player player;
  private PlayerData data;
  private Adaptation<AdaptationConfig> adaptation;
  private AdaptPlayer adaptPlayer;
  private UIWindow window;
  private PlayerPreferenceData preferences;
  private PreferencePolicy policy;
  private ConcurrentHashMap<String, UIWindow> openWindows;
  private Element control;
  private Element reset;

  @BeforeAll
  static void initializeSoundRegistry() {
    RegistryAccess access = mock(RegistryAccess.class, RETURNS_DEEP_STUBS);
    try (MockedStatic<RegistryAccess> registryAccess = mockStatic(RegistryAccess.class)) {
      registryAccess.when(RegistryAccess::registryAccess).thenReturn(access);
      Registry<Sound> registry = Registry.SOUNDS;
      doAnswer(call -> {
        Key key = call.getArgument(0);
        Sound sound = mock(Sound.class);
        when(sound.getKey()).thenReturn(new NamespacedKey(key.namespace(), key.value()));
        return sound;
      }).when(registry).getOrThrow(any(Key.class));
      assertThat(Sound.UI_BUTTON_CLICK).isNotNull();
    }
  }

  @BeforeEach
  @SuppressWarnings("unchecked")
  void prepare() {
    previousPlugin = Adapt.instance;
    Adapt.instance = mock(Adapt.class);
    scheduler = mockStatic(J.class);
    language = mockStatic(AdaptLanguage.class,
        invocation -> invocation.getMethod().getReturnType() == String.class ? "Text" : null);
    player = mock(Player.class);
    when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    when(player.isOnline()).thenReturn(true);
    when(player.getLocation()).thenReturn(new Location(mock(World.class), 0, 64, 0));
    sounds = mock(SoundPlayer.class);
    soundPlayers = mockStatic(SoundPlayer.class);
    soundPlayers.when(() -> SoundPlayer.of(player)).thenReturn(sounds);
    scheduler.when(() -> J.isOwnedByCurrentRegion(player)).thenReturn(true);
    window = mock(UIWindow.class);
    when(window.getViewer()).thenReturn(player);
    when(window.isVisible()).thenReturn(true);
    openWindows = new ConcurrentHashMap<>();
    openWindows.put(player.getUniqueId().toString(), window);
    when(Adapt.instance.getGuiLeftovers()).thenReturn(openWindows);
    adaptation = mock(Adaptation.class);
    skill = mock(Skill.class);
    when(skill.getName()).thenReturn("test-skill");
    doReturn(skill).when(adaptation).getSkill();
    when(skill.getAdaptations()).thenReturn(new KList<>(List.of(adaptation)));
    when(skill.isEnabled()).thenReturn(true);
    AdaptServer server = mock(AdaptServer.class);
    registry = mock(SkillRegistry.class);
    when(Adapt.instance.getAdaptServer()).thenReturn(server);
    when(server.getSkillRegistry()).thenReturn(registry);
    doReturn(skill).when(registry).getSkill("test-skill");

    when(skill.hasUsePermission(player, skill)).thenReturn(true);
    when(adaptation.isEnabled()).thenReturn(true);
    when(adaptation.areSoundsEnabled()).thenReturn(true);
    when(adaptation.getName()).thenReturn("test-adaptation");
    when(adaptation.hasUsePermission(player, adaptation)).thenReturn(true);
    when(adaptation.getPlayerPreferences()).thenReturn(List.of(PREFERENCE));
    when(adaptation.isPlayerPreferenceVisible(any(), any())).thenCallRealMethod();
    AdaptationConfig config = new AdaptationConfig();
    policy = PreferencePolicy.defaults(PREFERENCE);
    config.playerPreferences = Map.of(PREFERENCE.id(), policy);
    when(adaptation.getConfig()).thenReturn(config);
    adaptPlayer = mock(AdaptPlayer.class);
    when(adaptation.getPlayer(player)).thenReturn(adaptPlayer);
    when(adaptation.getLevel(adaptPlayer)).thenReturn(1);
    when(adaptPlayer.getPlayer()).thenReturn(player);
    when(adaptPlayer.isRuntimeReady()).thenReturn(true);
    data = mock(PlayerData.class);
    when(data.isEffectsEnabled()).thenReturn(true);
    preferences = new PlayerPreferenceData();
    when(data.getPreferences()).thenReturn(preferences);
    when(adaptPlayer.getData()).thenReturn(data);
    PreferenceGuiSupport.populate(adaptation, window, 2);
    ArgumentCaptor<Element> initial = ArgumentCaptor.forClass(Element.class);
    verify(window).setElement(eq(-1), eq(2), initial.capture());
    control = initial.getValue();
    ArgumentCaptor<Element> resetControl = ArgumentCaptor.forClass(Element.class);
    verify(window).setElement(eq(1), eq(2), resetControl.capture());
    reset = resetControl.getValue();
    clearInvocations(window);
  }

  @AfterEach
  void restore() {
    soundPlayers.close();
    language.close();
    scheduler.close();
    Adapt.instance = previousPlugin;
  }

  @Test
  void retiredRegistryControlsAreSilent() {
    doReturn(mock(Skill.class)).when(registry).getSkill("test-skill");
    control.call(ElementEvent.LEFT, control);
    reset.call(ElementEvent.LEFT, reset);
    verify(adaptPlayer, never()).requestSave();
    verify(window, never()).updateElement(anyInt(), anyInt(), any());
    verify(sounds, never()).play(any(Location.class), any(Sound.class), anyFloat(), anyFloat());
  }

  @Test
  void clickPersistsOnlyThisPreferenceAndUpdatesItsSlot() {
    control.call(ElementEvent.LEFT, control);

    assertThat(preferences.get("test-adaptation", "mode")).isEqualTo("ON");
    verify(adaptPlayer).requestSave();
    ArgumentCaptor<Element> replacement = ArgumentCaptor.forClass(Element.class);
    verify(window).updateElement(eq(-1), eq(2), replacement.capture());
    assertThat(replacement.getValue().getMaterial().getMaterial()).isEqualTo(Material.LIME_STAINED_GLASS_PANE);
    verify(window, never()).setElement(anyInt(), anyInt(), any());
    verify(window, never()).updateInventory();
    verify(window, never()).open();
    verify(window, never()).reopen();
    verify(window, never()).close();
    verify(sounds).play(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.3F, 1.2F);
  }

  @Test
  void reverseCycleSkipsTheLockedReactiveChoice() {
    control.call(ElementEvent.RIGHT, control);
    assertThat(preferences.get("test-adaptation", "mode")).isEqualTo("ON");
  }

  @Test
  void policyChangeAfterOpeningIsAppliedBeforeSaving() {
    policy.playerEditable = false;
    control.call(ElementEvent.LEFT, control);

    assertThat(preferences.get("test-adaptation", "mode")).isNull();
    verify(adaptPlayer, never()).requestSave();
    ArgumentCaptor<Element> replacement = ArgumentCaptor.forClass(Element.class);
    verify(window).updateElement(eq(-1), eq(2), replacement.capture());
    assertThat(replacement.getValue().getMaterial().getMaterial()).isEqualTo(Material.GRAY_STAINED_GLASS_PANE);
    verify(sounds).play(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.3F, 0.7F);
  }

  @Test
  void clickFromAReplacedWindowDoesNothing() {
    openWindows.clear();
    control.call(ElementEvent.LEFT, control);

    assertThat(preferences.get("test-adaptation", "mode")).isNull();
    verify(adaptPlayer, never()).requestSave();
    verify(window, never()).updateElement(anyInt(), anyInt(), any());
    soundPlayers.verify(() -> SoundPlayer.of(player), never());
  }

  @Test
  void losingTheLearnedLevelPreventsChanges() {
    when(adaptation.getLevel(adaptPlayer)).thenReturn(0);
    control.call(ElementEvent.LEFT, control);

    assertThat(preferences.get("test-adaptation", "mode")).isNull();
    verify(adaptPlayer, never()).requestSave();
    verify(sounds).play(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.3F, 0.7F);
  }

  @Test
  void resetAcknowledgesAcceptedDefaultsWithoutReopening() {
    preferences.set("test-adaptation", "mode", "ON");
    reset.call(ElementEvent.LEFT, reset);

    assertThat(preferences.get("test-adaptation", "mode")).isNull();
    verify(sounds).play(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.3F, 0.9F);
    verify(window, never()).updateInventory();
    verify(window, never()).reopen();
  }

  @Test
  void resettingAlreadyDefaultSettingsStillAcknowledgesTheClick() {
    reset.call(ElementEvent.LEFT, reset);

    verify(adaptPlayer, never()).requestSave();
    verify(sounds).play(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.3F, 0.9F);
  }

  @Test
  void disabledPlayerEffectsMuteFeedbackWithoutBlockingChanges() {
    when(data.isEffectsEnabled()).thenReturn(false);
    control.call(ElementEvent.LEFT, control);

    assertThat(preferences.get("test-adaptation", "mode")).isEqualTo("ON");
    soundPlayers.verify(() -> SoundPlayer.of(player), never());
  }

  @Test
  void disabledAdaptationSoundsMuteFeedbackWithoutBlockingChanges() {
    when(adaptation.areSoundsEnabled()).thenReturn(false);
    control.call(ElementEvent.LEFT, control);

    assertThat(preferences.get("test-adaptation", "mode")).isEqualTo("ON");
    soundPlayers.verify(() -> SoundPlayer.of(player), never());
  }

  @Test
  void globalSoundSettingMutesFeedbackWithoutBlockingChanges() {
    AdaptConfig config = mock(AdaptConfig.class);
    AdaptConfig.Effects effects = mock(AdaptConfig.Effects.class);
    when(config.getEffects()).thenReturn(effects);
    when(effects.isSoundsEnabled()).thenReturn(false);
    when(adaptation.areSoundsEnabled()).thenCallRealMethod();
    try (MockedStatic<AdaptConfig> configured = mockStatic(AdaptConfig.class)) {
      configured.when(AdaptConfig::get).thenReturn(config);
      control.call(ElementEvent.LEFT, control);
    }

    assertThat(preferences.get("test-adaptation", "mode")).isEqualTo("ON");
    soundPlayers.verify(() -> SoundPlayer.of(player), never());
  }

  @Test
  void dependentVisibilityPreservesControlAndResetSlotsWithoutReopening() {
    PlayerPreference<Mode> dependent = new PlayerPreference<>(Mode.class, new PlayerPreference.Definition<>(
        "dependent", PREFERENCE.label(), PREFERENCE.defaultValue(), PREFERENCE.choices()));
    PlayerPreference<Mode> other = new PlayerPreference<>(Mode.class, new PlayerPreference.Definition<>(
        "other", PREFERENCE.label(), PREFERENCE.defaultValue(), PREFERENCE.choices()));
    when(adaptation.getPlayerPreferences()).thenReturn(List.of(PREFERENCE, dependent, other));
    when(adaptation.isPlayerPreferenceVisible(eq(adaptPlayer), eq(dependent)))
        .thenAnswer(call -> "ON".equals(preferences.get("test-adaptation", "mode")));
    PreferenceGuiSupport.populate(adaptation, window, 2);
    ArgumentCaptor<Element> toggle = ArgumentCaptor.forClass(Element.class);
    ArgumentCaptor<Element> otherControl = ArgumentCaptor.forClass(Element.class);
    ArgumentCaptor<Element> resetControl = ArgumentCaptor.forClass(Element.class);
    verify(window).setElement(eq(-2), eq(2), toggle.capture());
    verify(window).setElement(eq(1), eq(2), otherControl.capture());
    verify(window).setElement(eq(2), eq(2), resetControl.capture());
    verify(window, never()).setElement(eq(-1), eq(2), any());
    clearInvocations(window);

    toggle.getValue().call(ElementEvent.LEFT, toggle.getValue());

    ArgumentCaptor<Element> shown = ArgumentCaptor.forClass(Element.class);
    ArgumentCaptor<Element> enabledToggle = ArgumentCaptor.forClass(Element.class);
    verify(window).updateElement(eq(-2), eq(2), enabledToggle.capture());
    verify(window).updateElement(eq(-1), eq(2), shown.capture());
    verify(window).updateElement(eq(1), eq(2), otherControl.capture());
    verify(window).updateElement(eq(2), eq(2), resetControl.capture());
    assertThat(enabledToggle.getValue().getId()).isEqualTo("preference-mode");
    assertThat(enabledToggle.getValue().getMaterial().getMaterial()).isEqualTo(Material.LIME_STAINED_GLASS_PANE);
    assertThat(shown.getValue().getId()).isEqualTo("preference-dependent");
    assertThat(otherControl.getValue().getId()).isEqualTo("preference-other");
    assertThat(resetControl.getValue().getId()).isEqualTo("preference-reset");
    clearInvocations(window);

    enabledToggle.getValue().call(ElementEvent.LEFT, enabledToggle.getValue());

    ArgumentCaptor<Element> disabledToggle = ArgumentCaptor.forClass(Element.class);
    verify(window).updateElement(eq(-2), eq(2), disabledToggle.capture());
    verify(window).updateElement(-1, 2, null);
    verify(window).updateElement(eq(1), eq(2), otherControl.capture());
    verify(window).updateElement(eq(2), eq(2), resetControl.capture());
    assertThat(disabledToggle.getValue().getId()).isEqualTo("preference-mode");
    assertThat(disabledToggle.getValue().getMaterial().getMaterial()).isEqualTo(Material.RED_STAINED_GLASS_PANE);
    assertThat(otherControl.getValue().getId()).isEqualTo("preference-other");
    assertThat(resetControl.getValue().getId()).isEqualTo("preference-reset");
    assertThat(preferences.get("test-adaptation", "mode")).isEqualTo("OFF");
    assertThat(preferences.get("test-adaptation", "dependent")).isNull();
    verify(window, never()).updateInventory();
    verify(window, never()).reopen();
    verify(window, never()).open();
    verify(window, never()).close();
  }

  @Test
  void preferencePaginationUsesPageSoundAndRejectsBoundaryClicks() {
    List<PlayerPreference<?>> declarations = new ArrayList<>();
    for (int index = 0; index < 9; index++) {
      declarations.add(new PlayerPreference<>(Mode.class, new PlayerPreference.Definition<>(
          "mode" + index, PREFERENCE.label(), PREFERENCE.defaultValue(), PREFERENCE.choices())));
    }
    when(adaptation.getPlayerPreferences()).thenReturn(declarations);
    PreferenceGuiSupport.populate(adaptation, window, 2);
    ArgumentCaptor<Element> nextControl = ArgumentCaptor.forClass(Element.class);
    verify(window).setElement(eq(3), eq(2), nextControl.capture());
    Element next = nextControl.getValue();

    next.call(ElementEvent.LEFT, next);
    next.call(ElementEvent.LEFT, next);

    verify(sounds).play(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.3F, 1.1F);
    verify(sounds).play(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.3F, 0.7F);
    verify(window, never()).updateInventory();
    verify(window, never()).reopen();
  }

  private enum Mode {
    OFF,
    ON,
    REACTIVE
  }
}
