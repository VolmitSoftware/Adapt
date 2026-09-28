package art.arcane.adapt.api.skill;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.preference.PlayerPreferences;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.AdaptServer;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.world.PlayerData;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.inventorygui.ElementEvent;
import art.arcane.volmlib.util.inventorygui.UIWindow;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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

class SkillPreferenceGuiSupportTest extends AdaptTestBase {
  private MockedStatic<J> scheduler;
  private MockedStatic<AdaptLanguage> language;
  private SkillRegistry registry;
  private Skill<?> skill;
  private SkillConfig config;
  private Adaptation<?> adaptation;
  private AdaptPlayer owner;
  private PlayerData data;
  private UIWindow window;
  private Element toggle;
  private Element reset;
  private ConcurrentHashMap<String, UIWindow> windows;

  @BeforeEach
  void prepare() {
    scheduler = mockStatic(J.class);
    language = mockStatic(AdaptLanguage.class,
        invocation -> invocation.getMethod().getReturnType() == String.class ? "Text" : null);
    Player player = mock(Player.class);
    UUID playerId = UUID.randomUUID();
    when(player.getUniqueId()).thenReturn(playerId);
    when(player.isOnline()).thenReturn(true);
    scheduler.when(() -> J.isOwnedByCurrentRegion(player)).thenReturn(true);
    owner = mock(AdaptPlayer.class);
    data = new PlayerData();
    data.setEffectsEnabled(false);
    when(owner.getData()).thenReturn(data);
    when(owner.getPlayer()).thenReturn(player);
    when(owner.isRuntimeReady()).thenReturn(true);
    skill = mock(Skill.class);
    adaptation = mock(Adaptation.class);
    config = new SkillConfig();
    when(skill.getName()).thenReturn("agility");
    when(skill.getPlayer(player)).thenReturn(owner);
    when(skill.isEnabled()).thenReturn(true);
    AdaptServer server = mock(AdaptServer.class);
    registry = mock(SkillRegistry.class);
    when(plugin.getAdaptServer()).thenReturn(server);
    when(server.getSkillRegistry()).thenReturn(registry);
    doReturn(skill).when(registry).getSkill("agility");

    when(skill.hasUsePermission(player, skill)).thenReturn(true);
    when(skill.getAdaptations()).thenReturn(new KList<>(List.of(adaptation)));
    doReturn(config).when(skill).getConfig();
    PlayerPreferences.validate(skill, config);
    window = mock(UIWindow.class);
    when(window.getViewer()).thenReturn(player);
    when(window.isVisible()).thenReturn(true);
    windows = new ConcurrentHashMap<>();
    windows.put(playerId.toString(), window);
    when(plugin.getGuiLeftovers()).thenReturn(windows);
    SkillPreferenceGuiSupport.populate(skill, window, 3);
    ArgumentCaptor<Element> elements = ArgumentCaptor.forClass(Element.class);
    verify(window).setElement(eq(-1), eq(3), elements.capture());
    toggle = elements.getValue();
    verify(window).setElement(eq(1), eq(3), elements.capture());
    reset = elements.getValue();
    clearInvocations(window);
  }

  @AfterEach
  void closeMocks() {
    language.close();
    scheduler.close();
  }

  @Test
  void retiredRegistryControlsAreSilent() {
    doReturn(mock(Skill.class)).when(registry).getSkill("agility");
    toggle.call(ElementEvent.LEFT, toggle);
    reset.call(ElementEvent.LEFT, reset);
    verify(owner, never()).requestSave();
    verify(window, never()).updateElement(anyInt(), anyInt(), any());
  }

  @Test
  void disablingSkillUpdatesExistingControlsAndReconcilesChildren() {
    toggle.call(ElementEvent.LEFT, toggle);
    assertThat(data.getPreferences().getSkill("agility", "enabled")).isEqualTo("OFF");
    verify(owner).requestSave();
    verify(adaptation).onPlayerPreferencesChanged(owner);
    ArgumentCaptor<Element> replacement = ArgumentCaptor.forClass(Element.class);
    verify(window).updateElement(eq(-1), eq(3), replacement.capture());
    assertThat(replacement.getValue().getMaterial().getMaterial()).isEqualTo(Material.RED_STAINED_GLASS_PANE);
    verify(window, never()).setElement(anyInt(), anyInt(), any());
    verify(window, never()).updateInventory();
    verify(window, never()).open();
    verify(window, never()).reopen();
    verify(window, never()).close();
  }

  @Test
  void resetSkillPreservesIndividualAdaptationChoices() {
    data.getPreferences().setSkill("agility", "enabled", "OFF");
    data.getPreferences().set("agility-air-dash", "enabled", "OFF");
    reset.call(ElementEvent.LEFT, reset);
    assertThat(data.getPreferences().getSkill("agility", "enabled")).isNull();
    assertThat(data.getPreferences().get("agility-air-dash", "enabled")).isEqualTo("OFF");
  }

  @Test
  void policyChangesAreCheckedAtClickTime() {
    config.playerPreferences.get("enabled").playerEditable = false;
    toggle.call(ElementEvent.LEFT, toggle);
    assertThat(data.getPreferences().getSkill("agility", "enabled")).isNull();
    verify(owner, never()).requestSave();
    ArgumentCaptor<Element> replacement = ArgumentCaptor.forClass(Element.class);
    verify(window).updateElement(eq(-1), eq(3), replacement.capture());
    assertThat(replacement.getValue().getMaterial().getMaterial()).isEqualTo(Material.GRAY_STAINED_GLASS_PANE);
  }

  @Test
  void staleWindowClicksCannotChangeTheSkill() {
    windows.clear();
    toggle.call(ElementEvent.LEFT, toggle);
    verify(owner, never()).requestSave();
    verify(window, never()).updateElement(anyInt(), anyInt(), any());
  }
}
