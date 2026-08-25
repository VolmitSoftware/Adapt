package art.arcane.adapt.util.common.inventorygui;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.util.common.misc.CustomModel;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

class GuiConfigTest extends AdaptTestBase {
  private AdaptConfig previousConfig;
  private AdaptConfig config;

  @BeforeEach
  void installConfig() throws Exception {
    previousConfig = (AdaptConfig) configField().get(null);
    config = new AdaptConfig();
    configField().set(null, config);
    GuiConfig.resetWarnings();
    CustomModel.clear();
  }

  @AfterEach
  void restoreConfig() throws Exception {
    configField().set(null, previousConfig);
    GuiConfig.resetWarnings();
    CustomModel.clear();
  }

  @Test
  void configuredIconsReplaceTheBuiltInDefault() throws Exception {
    set(config, "customModels", false);
    set(config.getGui(), "skillIcons", icons("mining", "DIAMOND"));
    set(config.getGui(), "adaptationIcons", icons("mining-vein-miner", "GOLDEN_PICKAXE"));

    assertThat(GuiConfig.skillIcon("mining", Material.STONE)).isEqualTo(Material.DIAMOND);
    assertThat(GuiConfig.adaptationIcon("mining-vein-miner", Material.STONE)).isEqualTo(Material.GOLDEN_PICKAXE);
    assertThat(GuiConfig.skillIcon("hunter", Material.BOW)).isEqualTo(Material.BOW);
  }

  @Test
  void iconKeysAndValuesAreMatchedWithoutCaseOrNamespaceNoise() throws Exception {
    set(config, "customModels", false);
    set(config.getGui(), "skillIcons", icons("MiNiNg", "minecraft:diamond_block"));

    assertThat(GuiConfig.skillIcon("mining", Material.STONE)).isEqualTo(Material.DIAMOND_BLOCK);
  }

  @Test
  void configuredIconsWinOverTheDefaultWhenModelsAreDisabled() throws Exception {
    set(config, "customModels", false);
    set(config.getGui(), "skillIcons", icons("mining", "DIAMOND"));

    CustomModel resolved = GuiConfig.skillModel(
        "mining",
        Material.STONE,
        CustomModel.get(Material.STONE, "skill", "mining")
    );

    assertThat(resolved.material()).isEqualTo(Material.DIAMOND);
  }

  @Test
  void modelsTomlOverridesTheConfiguredIconWhileCustomModelsAreEnabled() throws Exception {
    set(config, "customModels", true);
    set(config.getGui(), "skillIcons", icons("mining", "DIAMOND"));
    writeModels("""
        [skill.mining]
        material = "GOLD_INGOT"
        model = 0
        modelKey = "minecraft:empty"
        """);

    CustomModel resolved = GuiConfig.skillModel(
        "mining",
        Material.STONE,
        CustomModel.get(Material.STONE, "skill", "mining")
    );

    assertThat(resolved.material()).isEqualTo(Material.GOLD_INGOT);
  }

  @Test
  void aCustomModelNumberKeepsTheModelsTomlIconEvenWhenTheMaterialMatchesTheDefault() {
    CustomModel modelled = new CustomModel(Material.STONE, 41, NamespacedKey.minecraft("empty"));

    CustomModel resolved = GuiConfig.applyConfiguredMaterial(modelled, Material.STONE, Material.DIAMOND);

    assertThat(resolved.material()).isEqualTo(Material.STONE);
    assertThat(resolved.model()).isEqualTo(41);
  }

  @Test
  void unusableMaterialNamesFallBackToTheDefaultAndWarnOncePerReload() throws Exception {
    set(config, "customModels", false);
    set(config.getGui(), "skillIcons", icons("mining", "NOT_A_MATERIAL"));

    String output = captureOutput(() -> {
      assertThat(GuiConfig.skillIcon("mining", Material.STONE)).isEqualTo(Material.STONE);
      assertThat(GuiConfig.skillIcon("mining", Material.STONE)).isEqualTo(Material.STONE);
      assertThat(GuiConfig.skillIcon("mining", Material.STONE)).isEqualTo(Material.STONE);
    });

    assertThat(countOf(output, "gui.skillIcons.mining=NOT_A_MATERIAL")).isEqualTo(1);
  }

  @Test
  void airAndBlankIconValuesKeepTheDefault() throws Exception {
    set(config, "customModels", false);
    set(config.getGui(), "skillIcons", icons("mining", "AIR", "hunter", "  "));

    assertThat(GuiConfig.skillIcon("mining", Material.STONE)).isEqualTo(Material.STONE);
    assertThat(GuiConfig.skillIcon("hunter", Material.BOW)).isEqualTo(Material.BOW);
  }

  @Test
  void listedSkillsComeFirstInConfiguredOrderAndTheRestStayAlphabetical() throws Exception {
    set(config.getGui(), "skillOrder", order("mining", "ZETA"));
    List<String> names = new ArrayList<>(List.of("zeta", "alpha", "mining", "hunter"));

    Map<String, Integer> ranks = GuiConfig.skillOrder(names);
    names.sort(Comparator.comparingInt((String name) -> GuiConfig.rankOf(ranks, name))
        .thenComparing(name -> name, String.CASE_INSENSITIVE_ORDER));

    assertThat(names).containsExactly("mining", "zeta", "alpha", "hunter");
  }

  @Test
  void unknownOrderEntriesAreIgnoredAndWarnOncePerReload() throws Exception {
    set(config.getGui(), "skillOrder", order("mining", "ghost", "hunter"));
    List<String> names = List.of("hunter", "mining");

    String output = captureOutput(() -> {
      Map<String, Integer> ranks = GuiConfig.skillOrder(names);
      assertThat(GuiConfig.rankOf(ranks, "mining")).isZero();
      assertThat(GuiConfig.rankOf(ranks, "hunter")).isEqualTo(1);
      assertThat(GuiConfig.rankOf(ranks, "ghost")).isEqualTo(GuiConfig.UNRANKED);
      assertThat(GuiConfig.skillOrder(names)).hasSize(2);
    });

    assertThat(countOf(output, "gui.skillOrder lists unknown entry 'ghost'")).isEqualTo(1);
  }

  @Test
  void adaptationOrderIsResolvedPerSkillWithoutCaseSensitivity() throws Exception {
    KMap<String, List<String>> configured = new KMap<>();
    configured.put("Mining", order("mining-vein-miner", "mining-fortune"));
    set(config.getGui(), "adaptationOrder", configured);
    List<String> known = List.of("mining-fortune", "mining-vein-miner", "mining-hardened");

    Map<String, Integer> ranks = GuiConfig.adaptationOrder("mining", known);

    assertThat(GuiConfig.rankOf(ranks, "mining-vein-miner")).isZero();
    assertThat(GuiConfig.rankOf(ranks, "mining-fortune")).isEqualTo(1);
    assertThat(GuiConfig.rankOf(ranks, "mining-hardened")).isEqualTo(GuiConfig.UNRANKED);
    assertThat(GuiConfig.adaptationOrder("hunter", known)).isEmpty();
  }

  @Test
  void skillsGuiRowsClampsOutOfRangeValuesAndWarnsOncePerReload() {
    String output = captureOutput(() -> {
      assertThat(GuiConfig.clampSkillsGuiRows(0)).isZero();
      assertThat(GuiConfig.clampSkillsGuiRows(4)).isEqualTo(4);
      assertThat(GuiConfig.clampSkillsGuiRows(6)).isEqualTo(6);
      assertThat(GuiConfig.clampSkillsGuiRows(1)).isEqualTo(GuiConfig.MIN_FIXED_SKILL_ROWS);
      assertThat(GuiConfig.clampSkillsGuiRows(1)).isEqualTo(GuiConfig.MIN_FIXED_SKILL_ROWS);
      assertThat(GuiConfig.clampSkillsGuiRows(9)).isEqualTo(GuiLayout.MAX_ROWS);
      assertThat(GuiConfig.clampSkillsGuiRows(9)).isEqualTo(GuiLayout.MAX_ROWS);
      assertThat(GuiConfig.clampSkillsGuiRows(-2)).isZero();
    });

    assertThat(countOf(output, "gui.skillsGuiRows=1 raised to 2")).isEqualTo(1);
    assertThat(countOf(output, "gui.skillsGuiRows=9 clamped to 6")).isEqualTo(1);
    assertThat(countOf(output, "gui.skillsGuiRows=-2 is out of range")).isEqualTo(1);
  }

  @Test
  void skillsGuiRowsReadsTheConfiguredValue() throws Exception {
    set(config.getGui(), "skillsGuiRows", 3);

    assertThat(GuiConfig.skillsGuiRows()).isEqualTo(3);
  }

  private String captureOutput(Runnable action) {
    Logger logger = plugin.getLogger();
    StringBuilder output = new StringBuilder();
    Handler handler = new Handler() {
      @Override
      public void publish(LogRecord record) {
        if (record.getMessage() != null) {
          output.append(record.getMessage()).append('\n');
        }
      }

      @Override
      public void flush() {
      }

      @Override
      public void close() {
      }
    };
    boolean parentHandlers = logger.getUseParentHandlers();
    Level previousLevel = logger.getLevel();
    logger.setUseParentHandlers(false);
    logger.setLevel(Level.ALL);
    logger.addHandler(handler);
    try (MockedStatic<ComponentLogger> componentLogger = mockStatic(ComponentLogger.class)) {
      componentLogger.when(ComponentLogger::logger).thenReturn(null);
      action.run();
    } finally {
      logger.removeHandler(handler);
      logger.setLevel(previousLevel);
      logger.setUseParentHandlers(parentHandlers);
    }

    return output.toString();
  }

  private int countOf(String output, String needle) {
    int count = 0;
    int index = output.indexOf(needle);
    while (index >= 0) {
      count++;
      index = output.indexOf(needle, index + needle.length());
    }

    return count;
  }

  private void writeModels(String toml) throws Exception {
    File folder = dataFolder;
    folder.mkdirs();
    Files.writeString(new File(folder, "models.toml").toPath(), toml, StandardCharsets.UTF_8);
  }

  private KMap<String, String> icons(String... pairs) {
    KMap<String, String> map = new KMap<>();
    for (int i = 0; i + 1 < pairs.length; i += 2) {
      map.put(pairs[i], pairs[i + 1]);
    }

    return map;
  }

  private KList<String> order(String... names) {
    KList<String> list = new KList<>();
    for (String name : names) {
      list.add(name);
    }

    return list;
  }

  private Field configField() throws Exception {
    Field field = AdaptConfig.class.getDeclaredField("config");
    field.setAccessible(true);
    return field;
  }

  private static void set(Object target, String fieldName, Object value) throws Exception {
    Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(target, value);
  }
}
