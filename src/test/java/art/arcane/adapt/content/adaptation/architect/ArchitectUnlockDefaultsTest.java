package art.arcane.adapt.content.adaptation.architect;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.localization.AdaptMessages;
import art.arcane.adapt.util.config.TomlCodec;
import art.arcane.volmlib.util.localization.LinesValue;
import art.arcane.volmlib.util.localization.TextValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

class ArchitectUnlockDefaultsTest extends AdaptTestBase {
  @BeforeEach
  void configurePluginName() {
    lenient().when(plugin.getName()).thenReturn("Adapt");
    lenient().when(plugin.namespace()).thenReturn("adapt");
  }

  @Test
  void elevatorIsASingleLevelUnlock() {
    ArchitectElevator.Config config = new ArchitectElevator.Config();

    assertThat(config.maxLevel).isEqualTo(1);
  }

  @Test
  void elevatorDescriptionExplainsItsRecipe() {
    TextValue elevator = (TextValue) AdaptMessages.require("architect.elevator.description").englishValue();

    assertThat(elevator.template()).contains("Ender Pearl", "center", "8 Wool");
  }

  @Test
  void stonecutterSavantIsASingleLevelUnlockWithoutCooldownState() {
    ArchitectStonecutterSavant.Config config = new ArchitectStonecutterSavant.Config();

    assertThat(config.maxLevel).isEqualTo(1);
    for (Field field : ArchitectStonecutterSavant.Config.class.getDeclaredFields()) {
      assertThat(field.getName().toLowerCase(Locale.ROOT)).doesNotContain("cooldown");
    }
    assertThat(ArchitectStonecutterSavant.class.getDeclaredFields())
        .extracting(Field::getType)
        .doesNotContain(Cooldowns.class);
  }

  @Test
  void stonecutterSavantLoreDoesNotAdvertiseACooldown() {
    TextValue description = (TextValue) AdaptMessages.require(
        "architect.stonecutter_savant.description"
    ).englishValue();
    LinesValue lore = (LinesValue) AdaptMessages.require("architect.stonecutter_savant.lore").englishValue();
    String stonecutterSavant = description.template() + " " + String.join(" ", lore.lines());

    assertThat(stonecutterSavant.toLowerCase(Locale.ROOT)).doesNotContain("cooldown");
  }

  @Test
  void configuredLevelCapsPersistWhileRetiredStonecutterFieldsAreRemoved() throws IOException {
    ArchitectElevator.Config elevator = new ArchitectElevator.Config();
    elevator.maxLevel = 4;
    ArchitectChalkLine.Config chalk = new ArchitectChalkLine.Config();
    chalk.maxLevel = 5;
    ArchitectStonecutterSavant.Config stonecutter = TomlCodec.fromToml("""
        maxLevel = 5
        requireOffhand = true
        maxCooldownSeconds = 60
        minCooldownSeconds = 10
        xpPerUse = 7
        """, ArchitectStonecutterSavant.Config.class);

    String persistedStonecutter = TomlCodec.toToml(stonecutter, "adaptation:architect-stonecutter-savant");
    assertThat(elevator.maxLevel).isEqualTo(4);
    assertThat(chalk.maxLevel).isEqualTo(5);
    assertThat(stonecutter.maxLevel).isEqualTo(5);
    assertThat(stonecutter.requireOffhand).isTrue();
    assertThat(stonecutter.xpPerUse).isEqualTo(7D);
    assertThat(persistedStonecutter).contains("maxLevel = 5", "requireOffhand = true", "xpPerUse = 7.0");
    assertThat(persistedStonecutter).doesNotContain("maxCooldownSeconds", "minCooldownSeconds");
  }

}
