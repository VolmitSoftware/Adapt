package art.arcane.adapt.util.common.misc;

import art.arcane.adapt.TestVersion;
import art.arcane.adapt.bukkit.platform.item.BukkitItemOps;
import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.platform.EngineFeature;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.junit.jupiter.api.BeforeAll;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.File;
import java.lang.reflect.Field;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomModelTest extends AdaptTestBase {
  private AdaptConfig previous;
  private Field configField;

  @BeforeAll
  static void initializeItemRegistry() {
    TestVersion.mockBlockAndItemRegistries();
  }

  @BeforeEach
  void setupModels() throws Exception {
    configField = AdaptConfig.class.getDeclaredField("config");
    configField.setAccessible(true);
    previous = (AdaptConfig) configField.get(null);
    AdaptConfig config = new AdaptConfig();
    Field enabled = AdaptConfig.class.getDeclaredField("customModels");
    enabled.setAccessible(true);
    enabled.set(config, true);
    configField.set(null, config);
    CustomModel.clear();
    when(core.getPlatform().host().items()).thenReturn(new BukkitItemOps());
  }

  @AfterEach
  void restoreModels() throws Exception {
    configField.set(null, previous);
    CustomModel.clear();
  }

  @Test
  void configuredOrbHeadUsesItsTexture() throws Exception {
    assertThat(Material.PLAYER_HEAD.isItem()).isTrue();
    assertThat(Material.PLAYER_HEAD.isAir()).isFalse();
    String url = "https://textures.minecraft.net/texture/0123456789abcdef";
    String texture = Base64.getEncoder().encodeToString(
        ("{\"textures\":{\"SKIN\":{\"url\":\"" + url + "\"}}}").getBytes(StandardCharsets.UTF_8));
    assertThat(CustomModel.reloadSnapshot("""
        [items.experience-orb]
        material = "PLAYER_HEAD"
        model = 0
        headTexture = "%s"
        """.formatted(texture), modelsFile())).isTrue();
    CustomModel model = CustomModel.get(Id.minecraft("snowball"), "items", "experience-orb");
    ItemStack item = mock(ItemStack.class);
    when(item.getType()).thenReturn(Material.PLAYER_HEAD);
    when(item.getAmount()).thenReturn(1);
    SkullMeta meta = mock(SkullMeta.class);
    PlayerProfile profile = mock(PlayerProfile.class);
    PlayerTextures textures = mock(PlayerTextures.class);
    when(item.getItemMeta()).thenReturn(meta);
    when(profile.getTextures()).thenReturn(textures);

    try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
      bukkit.when(() -> Bukkit.createPlayerProfile(any(UUID.class))).thenReturn(profile);
      model.toItemStack(BukkitRefs.item(item));
    }

    assertThat(model.material()).isEqualTo(Id.minecraft("player_head"));
    verify(textures).setSkin(URI.create(url).toURL());
    verify(meta).setOwnerProfile(profile);
  }

  @Test
  void itemModelKeyAppliesWithoutNumericCustomModelData() {
    CustomModel model = new CustomModel(Id.minecraft("snowball"), 0, Id.parse("adapt:orb"), null);
    ItemStack stack = mock(ItemStack.class);
    ItemMeta meta = mock(ItemMeta.class);
    when(stack.getItemMeta()).thenReturn(meta);
    ItemRef item = mock(ItemRef.class);
    when(item.unwrap()).thenReturn(stack);
    when(core.getPlatform().host().supports(EngineFeature.ITEM_MODEL)).thenReturn(true);
    model.toItemStack(item);
    verify(item).setItemModel(Id.parse("adapt:orb"));
    verify(meta, never()).setCustomModelData(anyInt());
  }

  @Test
  void itemModelKeyIsIgnoredWhereTheServerHasNoItemModels() {
    CustomModel model = new CustomModel(Id.minecraft("snowball"), 0, Id.parse("adapt:orb"), null);
    ItemStack stack = mock(ItemStack.class);
    ItemMeta meta = mock(ItemMeta.class);
    when(stack.getItemMeta()).thenReturn(meta);
    ItemRef item = mock(ItemRef.class);
    when(item.unwrap()).thenReturn(stack);
    when(core.getPlatform().host().supports(EngineFeature.ITEM_MODEL)).thenReturn(false);
    model.toItemStack(item);
    verify(item, never()).setItemModel(any());
    verify(meta, never()).setCustomModelData(anyInt());
  }

  @Test
  void malformedTextureReloadKeepsThePreviousModel() {
    assertThat(CustomModel.reloadSnapshot("""
        [items.knowledge-orb]
        material = "EXPERIENCE_BOTTLE"
        """, modelsFile())).isTrue();

    assertThat(CustomModel.reloadSnapshot("""
        [items.knowledge-orb]
        material = "PLAYER_HEAD"
        headTexture = "invalid"
        """, modelsFile())).isFalse();

    assertThat(CustomModel.get(Id.minecraft("snowball"), "items", "knowledge-orb").material())
        .isEqualTo(Id.minecraft("experience_bottle"));
  }

  private File modelsFile() {
    return new File(dataFolder, "models.toml");
  }
}
