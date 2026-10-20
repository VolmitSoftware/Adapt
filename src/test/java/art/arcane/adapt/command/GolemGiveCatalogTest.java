package art.arcane.adapt.command;

import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.util.common.misc.CustomModel;
import art.arcane.adapt.util.director.specialhandlers.GolemItemIdHandler;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class GolemGiveCatalogTest {
  private static final List<String> EXPECTED = expected();

  private MockedStatic<CustomModel> models;

  @BeforeEach
  void installModels() {
    models = mockStatic(CustomModel.class);
    models.when(() -> CustomModel.get(any(Material.class), any(String[].class))).thenAnswer(call -> {
      CustomModel model = mock(CustomModel.class);
      when(model.material()).thenReturn(call.<Material>getArgument(0));
      return model;
    });
  }

  @AfterEach
  void removeModels() {
    models.close();
  }

  @Test
  void idsCoverEveryBuildableGolemancyItemInOrder() {
    assertThat(GolemGiveCatalog.ids()).containsExactlyElementsOf(EXPECTED).hasSize(44).doesNotHaveDuplicates();
  }

  @Test
  void completionsAreEveryIdFollowedByAll() {
    List<String> completions = GolemGiveCatalog.completions();

    assertThat(completions).hasSize(EXPECTED.size() + 1);
    assertThat(completions.subList(0, EXPECTED.size())).containsExactlyElementsOf(EXPECTED);
    assertThat(completions.getLast()).isEqualTo("all");
  }

  @Test
  void ownerBoundItemsAreNeitherListedNorAccepted() {
    assertThat(GolemGiveCatalog.completions()).noneMatch(id -> id.contains("dormant") || id.contains("shattered"));
    assertThat(GolemGiveCatalog.isItem("dormant_golem")).isFalse();
    assertThat(GolemGiveCatalog.isItem("shattered_core")).isFalse();
  }

  @Test
  void everyListedIdIsAcceptedAndUnknownIdsAreNot() {
    for (String id : EXPECTED) {
      assertThat(GolemGiveCatalog.isItem(id)).describedAs(id).isTrue();
    }
    for (String id : List.of("", "core", "core/blank", "core/unknown", "node", "rune/might", "rune/might/0", "rune/might/4", "rune/nothing/1",
        "all", "crook/extra", "golemancy:crook")) {
      assertThat(GolemGiveCatalog.isItem(id)).describedAs(id).isFalse();
    }
  }

  @Test
  void everyListedIdExceptTheHeartIsAGolemItemsRecipeId() {
    for (String id : EXPECTED) {
      if (id.equals("masterwork_heart")) {
        continue;
      }
      assertThat(GolemItems.ingredient(new NamespacedKey(GolemItems.NAMESPACE, id))).describedAs(id).isNotNull();
    }
  }

  @Test
  void normalizeTrimsAndLowercases() {
    assertThat(GolemGiveCatalog.normalize("  Core/Guardian ")).isEqualTo("core/guardian");
    assertThat(GolemGiveCatalog.normalize("ALL")).isEqualTo("all");
  }

  @Test
  void theTabCompletionHandlerOffersEveryIdAndAll() {
    GolemItemIdHandler handler = new GolemItemIdHandler();

    assertThat(handler.getPossibilities()).containsExactlyElementsOf(GolemGiveCatalog.completions());
    assertThat(handler.getPossibilities("rune/might")).containsExactly("rune/might/1", "rune/might/2", "rune/might/3");
    assertThat(handler.toString("crook")).isEqualTo("crook");
  }

  private static List<String> expected() {
    List<String> ids = new ArrayList<>(List.of("crook", "blank_core"));
    for (String role : List.of("guardian", "gatherer", "harvester", "lumber", "breaker", "rancher", "sorter", "angler")) {
      ids.add("core/" + role);
    }
    ids.add("blank_node");
    for (String node : List.of("guard", "collection", "deposit", "harvest", "lumber", "breaker", "pasture", "input", "filter", "fishing")) {
      ids.add("node/" + node);
    }
    ids.add("blank_rune");
    for (String aspect : List.of("swiftness", "might", "ember", "tide", "order", "entropy", "bulwark")) {
      for (int tier = 1; tier <= 3; tier++) {
        ids.add("rune/" + aspect + "/" + tier);
      }
    }
    ids.add("masterwork_heart");
    return List.copyOf(ids);
  }
}
