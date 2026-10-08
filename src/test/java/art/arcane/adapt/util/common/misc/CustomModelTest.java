package art.arcane.adapt.util.common.misc;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.version.IBindings;
import art.arcane.adapt.api.version.Version;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.inventory.ItemType;
import org.bukkit.block.BlockType;
import io.papermc.paper.registry.RegistryAccess;
import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.BeforeAll;
import org.bukkit.NamespacedKey;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomModelTest extends AdaptTestBase {
  private AdaptConfig previous;
  private Field configField;

  @BeforeAll
  static void initializeItemRegistry() {
    RegistryAccess access = mock(RegistryAccess.class, RETURNS_DEEP_STUBS);
    try (MockedStatic<RegistryAccess> registryAccess = mockStatic(RegistryAccess.class)) {
      registryAccess.when(RegistryAccess::registryAccess).thenReturn(access);
      Registry<ItemType> items = Registry.ITEM;
      Registry<BlockType> blocks = Registry.BLOCK;
      doAnswer(call -> mock(BlockType.Typed.class)).when(blocks).get(any(NamespacedKey.class));
      doAnswer(call -> mock(BlockType.Typed.class)).when(blocks).getOrThrow(any(Key.class));
      doAnswer(call -> mock(ItemType.Typed.class)).when(items).get(any(NamespacedKey.class));
      doAnswer(call -> mock(ItemType.Typed.class)).when(items).getOrThrow(any(Key.class));
    }
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
    CustomModel model = CustomModel.get(Material.SNOWBALL, "items", "experience-orb");
    ItemStack item = mock(ItemStack.class);
    SkullMeta meta = mock(SkullMeta.class);
    PlayerProfile profile = mock(PlayerProfile.class);
    PlayerTextures textures = mock(PlayerTextures.class);
    when(item.getItemMeta()).thenReturn(meta);
    when(profile.getTextures()).thenReturn(textures);

    try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
      bukkit.when(() -> Bukkit.createPlayerProfile(any(UUID.class))).thenReturn(profile);
      model.toItemStack(item);
    }

    assertThat(model.material()).isEqualTo(Material.PLAYER_HEAD);
    verify(textures).setSkin(URI.create(url).toURL());
    verify(meta).setOwnerProfile(profile);
  }

  @Test
  void itemModelKeyAppliesWithoutNumericCustomModelData() {
    CustomModel model = new CustomModel(Material.SNOWBALL, 0, NamespacedKey.fromString("adapt:orb"), null);
    ItemStack item = mock(ItemStack.class);
    ItemMeta meta = mock(ItemMeta.class);
    IBindings bindings = mock(IBindings.class);
    when(item.getItemMeta()).thenReturn(meta);
    try (MockedStatic<Version> version = mockStatic(Version.class)) {
      version.when(Version::get).thenReturn(bindings);
      model.toItemStack(item);
    }
    verify(bindings).applyModel(model, meta);
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

    assertThat(CustomModel.get(Material.SNOWBALL, "items", "knowledge-orb").material())
        .isEqualTo(Material.EXPERIENCE_BOTTLE);
  }

  private File modelsFile() {
    return new File(dataFolder, "models.toml");
  }
}
