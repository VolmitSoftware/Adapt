package art.arcane.adapt.service;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.util.common.misc.CustomModel;
import art.arcane.adapt.util.common.plugin.AdaptService;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.hotload.ConfigHotloadEngine;
import io.papermc.paper.registry.RegistryAccess;
import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.BlockType;
import org.bukkit.inventory.ItemType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class HotloadSVCModelsSelfWriteTest extends AdaptTestBase {
  private static final long OBSERVE_MILLIS = 1_500L;
  private static final long AWAIT_MILLIS = 5_000L;
  private static final String EXISTING = """
      [gui.existing]
      material = "STONE"
      model = 0
      modelKey = "minecraft:empty"
      headTexture = ""
      """;

  private final AtomicInteger refreshes = new AtomicInteger();
  private AdaptConfig previousConfig;
  private ConfigHotloadEngine engine;
  private HotloadSVC hotload;
  private Method apply;
  private File models;

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
  void prepareModelsRuntime() throws Exception {
    previousConfig = (AdaptConfig) field(AdaptConfig.class, "config").get(null);
    AdaptConfig config = new AdaptConfig();
    field(AdaptConfig.class, "customModels").set(config, true);
    field(AdaptConfig.class, "config").set(null, config);
    CustomModel.clear();
    models = new File(dataFolder, "models.toml");
    hotload = new HotloadSVC();
    KMap<Class<? extends AdaptService>, AdaptService> services = new KMap<>();
    services.put(HotloadSVC.class, hotload);
    field(Adapt.class, "services").set(plugin, services);
    field(HotloadSVC.class, "modelsFile").set(hotload, models);
    engine = (ConfigHotloadEngine) field(HotloadSVC.class, "hotloadEngine").get(hotload);
    apply = HotloadSVC.class.getDeclaredMethod("applyConfigChanges", long.class, List.class);
    apply.setAccessible(true);
  }

  @AfterEach
  void closeModelsRuntime() throws Exception {
    engine.clear();
    field(AdaptConfig.class, "config").set(null, previousConfig);
    CustomModel.clear();
  }

  @Test
  void entriesCustomModelAddsAreNotReloadedBack() throws Exception {
    Files.writeString(models.toPath(), EXISTING);
    watch();
    CustomModel.get(Material.STONE, "gui", "existing");
    long generation = CustomModel.generation();

    try (MockedStatic<J> scheduling = countRefreshes()) {
      CustomModel.get(Material.STONE, "golemancy", "golem", "icon");
      awaitFile("golemancy");
      observe();
    }

    assertThat(CustomModel.generation()).isEqualTo(generation);
    assertThat(refreshes).hasValue(0);
  }

  @Test
  void aModelsFileCustomModelCreatesIsNotReloadedBack() throws Exception {
    watch();
    long generation = CustomModel.generation();

    try (MockedStatic<J> scheduling = countRefreshes()) {
      CustomModel.get(Material.STONE, "golemancy", "golem", "icon");
      awaitFile("golemancy");
      observe();
    }

    assertThat(CustomModel.generation()).isEqualTo(generation);
    assertThat(refreshes).hasValue(0);
  }

  @Test
  void anExternalEditStillReloadsAndRefreshesOpenGuis() throws Exception {
    Files.writeString(models.toPath(), EXISTING);
    watch();
    CustomModel.get(Material.STONE, "gui", "existing");
    long generation = CustomModel.generation();

    try (MockedStatic<J> scheduling = countRefreshes()) {
      Files.writeString(models.toPath(), EXISTING.replace("STONE", "DIAMOND"));
      pollUntil(() -> CustomModel.generation() > generation);
    }

    assertThat(CustomModel.get(Material.STONE, "gui", "existing").material()).isEqualTo(Material.DIAMOND);
    assertThat(refreshes.get()).isPositive();
  }

  @Test
  void anExternalEditAfterASelfWriteStillReloads() throws Exception {
    Files.writeString(models.toPath(), EXISTING);
    watch();
    CustomModel.get(Material.STONE, "gui", "existing");

    try (MockedStatic<J> scheduling = countRefreshes()) {
      CustomModel.get(Material.STONE, "golemancy", "golem", "icon");
      awaitFile("golemancy");
      observe();
      String selfWritten = Files.readString(models.toPath());
      long generation = CustomModel.generation();
      Files.writeString(models.toPath(), EXISTING);
      pollUntil(() -> CustomModel.generation() > generation);
      long edited = CustomModel.generation();
      Files.writeString(models.toPath(), selfWritten);
      pollUntil(() -> CustomModel.generation() > edited);
    }

    assertThat(refreshes.get()).isGreaterThanOrEqualTo(2);
  }

  private void watch() {
    engine.configure(100L, 100L, List.of(models), List.of());
  }

  private MockedStatic<J> countRefreshes() {
    MockedStatic<J> scheduling = mockStatic(J.class, CALLS_REAL_METHODS);
    scheduling.when(() -> J.s(any(Runnable.class))).thenAnswer(invocation -> {
      refreshes.incrementAndGet();
      return null;
    });
    return scheduling;
  }

  private void awaitFile(String fragment) throws Exception {
    long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(AWAIT_MILLIS);
    while (System.nanoTime() < deadline) {
      if (models.isFile() && Files.readString(models.toPath()).contains(fragment)) {
        return;
      }
      Thread.sleep(10L);
    }
    throw new AssertionError("models.toml never contained " + fragment);
  }

  private void observe() throws Exception {
    long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(OBSERVE_MILLIS);
    while (System.nanoTime() < deadline) {
      applyTouched();
      Thread.sleep(25L);
    }
  }

  private void pollUntil(BooleanSupplier condition) throws Exception {
    long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(AWAIT_MILLIS);
    while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
      applyTouched();
      Thread.sleep(25L);
    }
    assertThat(condition.getAsBoolean()).isTrue();
  }

  private void applyTouched() throws Exception {
    List<ConfigHotloadEngine.StableContentSnapshot> snapshots = List.copyOf(engine.pollTouchedSnapshots());
    if (!snapshots.isEmpty()) {
      apply.invoke(hotload, 0L, snapshots);
    }
  }

  private static Field field(Class<?> owner, String name) throws Exception {
    Field field = owner.getDeclaredField(name);
    field.setAccessible(true);
    return field;
  }
}
