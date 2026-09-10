package art.arcane.adapt.localization;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.skill.SimpleSkill;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.localization.catalog.SkillMessages;
import art.arcane.adapt.util.common.inventorygui.GuiConfig;
import art.arcane.adapt.util.common.misc.CustomModel;
import art.arcane.volmlib.util.collection.KList;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdaptLanguageEditorPresentationTest {
  private MockedStatic<GuiConfig> gui;

  @BeforeEach
  void configureModels() {
    gui = mockStatic(GuiConfig.class);
    gui.when(() -> GuiConfig.skillModel(anyString(), any(), any())).thenAnswer(invocation -> invocation.getArgument(2));
    gui.when(() -> GuiConfig.adaptationModel(anyString(), any(), any())).thenAnswer(invocation -> invocation.getArgument(2));
  }

  @AfterEach
  void closeModels() {
    gui.close();
  }

  @Test
  void skillAndAdaptationMessagesUseTheirExistingModelsAndLocalizationNamespaces() {
    SimpleSkill<?> axes = mock(SimpleSkill.class);
    SimpleAdaptation<?> woodMiner = mock(SimpleAdaptation.class);
    ItemStack skillIcon = model(axes);
    ItemStack adaptationIcon = model(woodMiner);
    when(axes.getName()).thenReturn("axes");
    when(axes.getPresentation()).thenReturn(SkillPresentation.of(
        SkillMessages.AXES_NAME, SkillMessages.AXES_ICON, SkillMessages.AXES_DESCRIPTION));
    when(woodMiner.getLocalizationKey()).thenReturn("axe.wood_miner");
    when(woodMiner.getName()).thenReturn("axe-woodveinminer");
    doReturn(new KList<Adaptation<?>>(woodMiner)).when(axes).getAdaptations();
    SkillRegistry registry = mock(SkillRegistry.class);
    when(registry.getAllSkills()).thenReturn(List.of(axes));
    AdaptLanguageEditor editor = new AdaptLanguageEditor(() -> registry);

    assertThat(editor.icon("axe")).containsSame(skillIcon);
    assertThat(editor.icon("skill.axes.description")).containsSame(skillIcon);
    assertThat(editor.icon("axe.wood_miner.name")).containsSame(adaptationIcon);
    assertThat(editor.icon("axe.wood_miner.lore.details")).containsSame(adaptationIcon);
    assertThat(editor.icon("axe.other")).containsSame(skillIcon);
    assertThat(editor.icon("command.help")).isEmpty();
    verify(registry).getAllSkills();

    when(registry.getCatalogRevision()).thenReturn(1L);
    when(registry.getAllSkills()).thenReturn(List.of());
    assertThat(editor.icon("axe")).isEmpty();
    verify(registry, times(2)).getAllSkills();
  }

  @Test
  void unavailableRegistryUsesTheSharedDefaultIcons() {
    assertThat(new AdaptLanguageEditor(() -> null).icon("skill.axes.name")).isEmpty();
  }

  private ItemStack model(Skill<?> skill) {
    CustomModel model = mock(CustomModel.class);
    ItemStack item = mock(ItemStack.class);
    when(skill.getModel()).thenReturn(model);
    when(model.toItemStack()).thenReturn(item);
    return item;
  }

  private ItemStack model(Adaptation<?> adaptation) {
    CustomModel model = mock(CustomModel.class);
    ItemStack item = mock(ItemStack.class);
    when(adaptation.getModel()).thenReturn(model);
    when(model.toItemStack()).thenReturn(item);
    return item;
  }
}
