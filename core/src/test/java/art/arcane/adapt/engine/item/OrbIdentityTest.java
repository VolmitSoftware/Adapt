package art.arcane.adapt.engine.item;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.engine.item.DataBoundItem;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.engine.world.AdaptServer;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.item.TooltipPart;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.util.common.misc.CustomModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class OrbIdentityTest extends AdaptTestBase {
  private MockedStatic<CustomModel> models;

  @BeforeEach
  void installRuntime() {
    models = mockStatic(CustomModel.class);
    models.when(() -> CustomModel.get(any(Id.class), any(String[].class))).thenAnswer(call -> {
      Id material = call.getArgument(0);
      CustomModel model = mock(CustomModel.class);
      when(model.material()).thenReturn(material);
      when(model.toItemStack()).thenAnswer(ignored -> new FakeItem(material, 1));
      return model;
    });
    AdaptServer server = mock(AdaptServer.class);
    SkillRegistry registry = mock(SkillRegistry.class);
    Skill<?> skill = mock(Skill.class);
    when(skill.getDisplayName()).thenReturn("Pickaxes");
    when(registry.getSkill(anyString())).thenAnswer(call -> skill);
    when(server.getSkillRegistry()).thenReturn(registry);
    when(core.getAdaptServer()).thenReturn(server);
  }

  @AfterEach
  void removeModels() {
    models.close();
  }

  @Test
  void experienceOrbRoundTripsThroughTheSharedItemData() {
    ItemRef item = ExperienceOrb.with("pickaxe", 25D);

    assertThat(item.type()).isEqualTo(Id.minecraft("snowball"));
    assertThat(item.data().get(DataKey.adapt(String.valueOf("art.arcane.adapt.content.item.ExperienceOrb.Data".hashCode()), DataCodecs.STRING)))
        .isEqualTo("{\"experienceMap\":{\"pickaxe\":25.0}}");
    assertThat(item.enchantmentLevel(Id.minecraft("binding_curse"))).isEqualTo(10);
    assertThat(((FakeItem) item).hiddenTooltip()).contains(TooltipPart.ATTRIBUTES, TooltipPart.ENCHANTMENTS);
    assertThat(item.displayName()).isNotBlank();
    assertThat(item.lore()).hasSize(2);
    assertThat(ExperienceOrb.io.hasData(item)).isTrue();
    assertThat(ExperienceOrb.get(item).getExperienceMap()).isEqualTo(Map.of("pickaxe", 25D));
    assertThat(KnowledgeOrb.io.hasData(item)).isFalse();
  }

  @Test
  void knowledgeOrbRoundTripsThroughTheSharedItemData() {
    ItemRef item = KnowledgeOrb.with(Map.of("pickaxe", 4, "axe", 2));

    assertThat(item.data().get(KnowledgeOrb.io.dataKey())).contains("\"pickaxe\":4", "\"axe\":2");
    assertThat(KnowledgeOrb.io.dataKey().id())
        .isEqualTo(Id.of("adapt", String.valueOf("art.arcane.adapt.content.item.KnowledgeOrb.Data".hashCode())));
    assertThat(item.lore()).hasSize(3);
    assertThat(KnowledgeOrb.io.hasData(item)).isTrue();
    assertThat(KnowledgeOrb.get(item).getKnowledgeMap()).isEqualTo(Map.of("pickaxe", 4, "axe", 2));
    assertThat(ExperienceOrb.io.hasData(item)).isFalse();
  }

  @Test
  void experienceDataSurvivesMaterialChanges() {
    ItemRef item = stored("player_head", ExperienceOrb.io, "{\"experienceMap\":{\"pickaxe\":25.0}}");

    assertThat(ExperienceOrb.io.hasData(item)).isTrue();
    assertThat(ExperienceOrb.get(item).getExperience()).isEqualTo(25D);
    assertThat(KnowledgeOrb.io.hasData(item)).isFalse();
  }

  @Test
  void knowledgeDataSurvivesMaterialChanges() {
    ItemRef item = stored("experience_bottle", KnowledgeOrb.io, "{\"knowledgeMap\":{\"pickaxe\":4}}");

    assertThat(KnowledgeOrb.io.hasData(item)).isTrue();
    assertThat(KnowledgeOrb.get(item).getKnowledge()).isEqualTo(4);
    assertThat(ExperienceOrb.io.hasData(item)).isFalse();
  }

  @Test
  void ordinarySnowballIsNotAnOrb() {
    ItemRef item = FakeItem.of("snowball");

    assertThat(ExperienceOrb.io.hasData(item)).isFalse();
    assertThat(KnowledgeOrb.io.hasData(item)).isFalse();
  }

  @Test
  void rewritingAnOrbReplacesItsStoredReward() {
    ItemRef item = ExperienceOrb.with("pickaxe", 25D);

    ExperienceOrb.set(item, "axe", 50D);

    assertThat(ExperienceOrb.get(item).getExperienceMap()).isEqualTo(Map.of("axe", 50D));
  }

  @Test
  void rewritingAnOrbClearsTheOldStackBeforeApplyingOrbData() {
    ItemRef item = FakeItem.of("snowball");
    item.setDisplayName("Foreign");
    item.setEnchantment(Id.minecraft("sharpness"), 3);

    ExperienceOrb.set(item, "pickaxe", 50D);

    assertThat(item.enchantmentLevel(Id.minecraft("sharpness"))).isZero();
    assertThat(item.enchantmentLevel(Id.minecraft("binding_curse"))).isEqualTo(10);
    assertThat(item.displayName()).isNotEqualTo("Foreign").isNotBlank();
    assertThat(ExperienceOrb.get(item).getExperienceMap()).isEqualTo(Map.of("pickaxe", 50D));
  }

  private ItemRef stored(String material, DataBoundItem<?> type, String json) {
    ItemRef item = FakeItem.of(material);
    item.data().set(type.dataKey(), json);
    return item;
  }
}
