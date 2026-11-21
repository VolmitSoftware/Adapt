package art.arcane.adapt.util.common.misc;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.platform.AdaptScheduler;
import art.arcane.adapt.platform.PlatformHost;
import art.arcane.adapt.platform.item.ItemOps;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.config.ConfigFileSupport;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CustomModelDottedKeyTest extends AdaptTestBase {
  private static final String[] FIELD_ICON = {"gui", "config", "fields", "core.debug", "item"};

  private AdaptConfig previousConfig;

  @BeforeEach
  void prepareModelsRuntime() throws Exception {
    previousConfig = (AdaptConfig) field("config").get(null);
    AdaptConfig config = new AdaptConfig();
    field("customModels").set(config, true);
    field("config").set(null, config);
    CustomModel.clear();
    PlatformHost host = core.getPlatform().host();
    AdaptScheduler scheduler = core.getPlatform().scheduler();
    ItemOps items = mock(ItemOps.class);
    when(items.isItem(any(Id.class))).thenReturn(true);
    when(host.items()).thenReturn(items);
    doAnswer(call -> {
      call.<Runnable>getArgument(0).run();
      return null;
    }).when(scheduler).runAsync(any(Runnable.class), anyLong());
  }

  @AfterEach
  void restoreModelsRuntime() throws Exception {
    field("config").set(null, previousConfig);
    CustomModel.clear();
  }

  @Test
  void aConfigFieldIconSurvivesRepeatedSavesAndReloads() throws Exception {
    CustomModel.get(Id.minecraft("stone"), FIELD_ICON);
    assertThat(CustomModel.reloadFromDisk()).isTrue();

    CustomModel.get(Id.minecraft("stone"), FIELD_ICON);
    assertThat(CustomModel.reloadFromDisk()).isTrue();

    File models = new File(dataFolder, "models.toml");
    assertThat(Files.readString(models.toPath())).contains("[gui.config.fields.\"core.debug\".item]");
  }

  @Test
  void dottedKeysKeepTheirShapeThroughToml() {
    JsonObject icon = new JsonObject();
    icon.addProperty("material", "STONE");
    JsonObject entry = new JsonObject();
    entry.add("item", icon);
    JsonObject fields = new JsonObject();
    fields.add("core.debug", entry);
    JsonObject root = new JsonObject();
    root.add("fields", fields);

    String toml = ConfigFileSupport.serializeJsonElementToToml(root);
    JsonElement parsed = ConfigFileSupport.parseToJsonElement(toml, new File("models.toml"));

    assertThat(parsed).isEqualTo(root);
  }

  private static Field field(String name) throws Exception {
    Field field = AdaptConfig.class.getDeclaredField(name);
    field.setAccessible(true);
    return field;
  }
}
