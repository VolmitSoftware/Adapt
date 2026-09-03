package art.arcane.adapt.command;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.service.CommandSVC;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommandLanguageRoutingTest {
  @Test
  void forwardsEveryLanguageArgumentBeforeCheckingTheAdminRootPermission() {
    Player player = mock(Player.class);
    Command command = mock(Command.class);
    when(command.getName()).thenReturn("adapt");
    String[] arguments = {"language", "self", "de_DE"};
    String[] languageArguments = {"self", "de_DE"};

    try (MockedStatic<Adapt> plugin = mockStatic(Adapt.class);
         MockedStatic<AdaptLanguage> language = mockStatic(AdaptLanguage.class)) {
      assertTrue(new CommandSVC().onCommand(player, command, "adapt", arguments));

      language.verify(() -> AdaptLanguage.language(player, languageArguments));
      verify(player, never()).hasPermission("adapt.main");
    }
  }

  @Test
  void forwardsLanguageTabArgumentsToTheSharedCompletionService() {
    CommandSender sender = mock(CommandSender.class);
    Command command = mock(Command.class);
    when(command.getName()).thenReturn("adapt");
    String[] arguments = {"language", "self", "de"};
    String[] languageArguments = {"self", "de"};

    try (MockedStatic<AdaptLanguage> language = mockStatic(AdaptLanguage.class)) {
      language.when(() -> AdaptLanguage.completeLanguage(sender, languageArguments)).thenReturn(List.of("de_DE"));

      assertEquals(List.of("de_DE"), new CommandSVC().onTabComplete(sender, command, "adapt", arguments));
      language.verify(() -> AdaptLanguage.completeLanguage(sender, languageArguments));
    }
  }
}
