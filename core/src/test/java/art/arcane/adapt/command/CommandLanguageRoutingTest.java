package art.arcane.adapt.command;

import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.localization.LanguageSwitcherPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommandLanguageRoutingTest {
  private LanguageSwitcherPort language;

  @BeforeEach
  void installLanguage() {
    language = mock(LanguageSwitcherPort.class);
    AdaptPorts.install(LanguageSwitcherPort.class, language);
  }

  @AfterEach
  void removeLanguage() {
    AdaptPorts.uninstall(LanguageSwitcherPort.class);
  }

  @Test
  void forwardsEveryLanguageArgumentBeforeCheckingTheAdminRootPermission() {
    CommandSource player = mock(CommandSource.class);
    String[] arguments = {"language", "self", "de_DE"};
    String[] languageArguments = {"self", "de_DE"};

    new AdaptCommands().execute(player, "adapt", arguments);

    verify(language).command(player, languageArguments);
    verify(player, never()).hasPermission("adapt.main");
  }

  @Test
  void forwardsLanguageTabArgumentsToTheSharedCompletionService() {
    CommandSource sender = mock(CommandSource.class);
    String[] arguments = {"language", "self", "de"};
    String[] languageArguments = {"self", "de"};
    when(language.complete(sender, languageArguments)).thenReturn(List.of("de_DE"));

    assertEquals(List.of("de_DE"), new AdaptCommands().complete(sender, "adapt", arguments));
    verify(language).complete(sender, languageArguments);
  }
}
