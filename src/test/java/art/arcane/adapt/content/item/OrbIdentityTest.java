package art.arcane.adapt.content.item;

import art.arcane.adapt.AdaptTestBase;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;
import org.mockito.InOrder;

class OrbIdentityTest extends AdaptTestBase {
  @Test
  void experienceDataSurvivesMaterialChanges() throws Exception {
    ItemStack item = stored(Material.PLAYER_HEAD, ExperienceOrb.Data.class,
        "{\"experienceMap\":{\"pickaxe\":25.0}}");

    assertThat(ExperienceOrb.io.hasData(item)).isTrue();
    assertThat(ExperienceOrb.get(item).getExperience()).isEqualTo(25D);
    assertThat(KnowledgeOrb.io.hasData(item)).isFalse();
  }

  @Test
  void knowledgeDataSurvivesMaterialChanges() throws Exception {
    ItemStack item = stored(Material.EXPERIENCE_BOTTLE, KnowledgeOrb.Data.class,
        "{\"knowledgeMap\":{\"pickaxe\":4}}");

    assertThat(KnowledgeOrb.io.hasData(item)).isTrue();
    assertThat(KnowledgeOrb.get(item).getKnowledge()).isEqualTo(4);
    assertThat(ExperienceOrb.io.hasData(item)).isFalse();
  }

  @Test
  void ordinarySnowballIsNotAnOrb() {
    ItemStack item = mock(ItemStack.class);
    when(item.getType()).thenReturn(Material.SNOWBALL);
    assertThat(ExperienceOrb.io.hasData(item)).isFalse();
    assertThat(KnowledgeOrb.io.hasData(item)).isFalse();
  }

  @Test
  void rewritingOrbChangesMaterialBeforeApplyingHeadMetadata() {
    ExperienceOrb orb = mock(ExperienceOrb.class, CALLS_REAL_METHODS);
    ExperienceOrb.Data data = new ExperienceOrb.Data("pickaxe", 50D);
    ItemStack existing = mock(ItemStack.class);
    ItemStack updated = mock(ItemStack.class);
    ItemMeta meta = mock(ItemMeta.class);
    when(updated.getType()).thenReturn(Material.PLAYER_HEAD);
    when(updated.getItemMeta()).thenReturn(meta);
    doReturn(updated).when(orb).withData(data);

    orb.setData(existing, data);

    InOrder mutations = inOrder(existing);
    mutations.verify(existing).setType(Material.PLAYER_HEAD);
    mutations.verify(existing).setItemMeta(meta);
  }

  private ItemStack stored(Material material, Class<?> type, String json) throws Exception {
    when(plugin.getName()).thenReturn("Adapt");
    when(plugin.namespace()).thenReturn("adapt");
    ItemStack item = mock(ItemStack.class);
    ItemMeta meta = mock(ItemMeta.class);
    PersistentDataContainer container = mock(PersistentDataContainer.class);
    NamespacedKey key = new NamespacedKey(plugin, String.valueOf(type.getCanonicalName().hashCode()));
    when(item.getType()).thenReturn(material);
    when(item.getItemMeta()).thenReturn(meta);
    when(meta.getPersistentDataContainer()).thenReturn(container);
    when(container.has(key, PersistentDataType.STRING)).thenReturn(true);
    when(container.get(key, PersistentDataType.STRING)).thenReturn(json);
    return item;
  }
}
