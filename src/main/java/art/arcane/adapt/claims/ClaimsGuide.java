package art.arcane.adapt.claims;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.claims.ClaimRanks;
import art.arcane.adapt.api.claims.ClaimsConfig;
import art.arcane.adapt.content.skill.SkillClaims;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.ClaimsMessages;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.inventorygui.GuiCloseSuppression;
import art.arcane.adapt.util.common.inventorygui.GuiLayout;
import art.arcane.adapt.util.common.inventorygui.GuiTheme;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.inventorygui.UIWindow;
import art.arcane.volmlib.util.localization.LanguageAudience;
import art.arcane.volmlib.util.localization.MessageArgs;
import art.arcane.volmlib.util.localization.TextKey;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.UUID;

public final class ClaimsGuide {
  private ClaimsGuide() {
  }

  public static void attach(Adaptation<?> adaptation, int level, Element element) {
    element.addLore(C.GOLD + AdaptLanguage.text(ClaimsMessages.GUIDE_HINT));
    element.onRightClick(clicked -> requestOpen(adaptation, level, clicked));
  }

  private static void requestOpen(Adaptation<?> adaptation, int level, Element clicked) {
    UUID viewerId = LanguageAudience.current();
    if (viewerId == null || Adapt.instance == null) {
      return;
    }
    UIWindow source = Adapt.instance.getGuiLeftovers().get(viewerId.toString());
    if (source == null) {
      return;
    }
    J.runEntity(source.getViewer(), () -> {
      if (accepts(adaptation, source) && contains(source, clicked)) {
        LanguageAudience.run(viewerId, () -> open(adaptation, level, source));
      }
    }, 1);
  }

  private static void open(Adaptation<?> adaptation, int level, UIWindow source) {
    Player player = source.getViewer();
    GuideContent content = content(adaptation, level);
    UIWindow window = new UIWindow(Adapt.instance, player);
    GuiTheme.apply(window, "skill/claims/" + adaptation.getName() + "/guide");
    window.setViewportHeight(3);
    window.setTitle(adaptation.getDisplayName());
    window.setElement(-2, 1, topic(new GuideTopic("controls", Material.IRON_SHOVEL,
        ClaimsMessages.GUIDE_CONTROLS_TITLE, content.controls(), content.controlsArguments())));
    window.setElement(0, 1, topic(new GuideTopic("rules", Material.KNOWLEDGE_BOOK,
        ClaimsMessages.GUIDE_RULES_TITLE, content.rules(), content.rulesArguments())));
    window.setElement(2, 1, topic(new GuideTopic("consequences", Material.LODESTONE,
        ClaimsMessages.GUIDE_CONSEQUENCES_TITLE, content.consequences(), MessageArgs.empty())));
    window.setElement(0, 2, GuiTheme.element("claims-guide-back", Material.ARROW, "gui", "navigation", "back")
        .setName(C.WHITE + AdaptLanguage.text(ClaimsMessages.GUIDE_BACK))
        .onLeftClick(clicked -> requestBack(adaptation, level, window)));
    window.onClosed(closed -> onClosed(window));
    GuiCloseSuppression.suppress(player);
    player.closeInventory();
    window.open();
    Adapt.instance.getGuiLeftovers().put(player.getUniqueId().toString(), window);
  }

  private static void requestBack(Adaptation<?> adaptation, int level, UIWindow window) {
    Player player = window.getViewer();
    J.runEntity(player, () -> {
      if (!accepts(adaptation, window)) {
        return;
      }
      boolean preferences = AdaptConfig.get().isPlayerSettingsEnabled() && !adaptation.getPlayerPreferences().isEmpty();
      GuiLayout.PagePlan plan = GuiLayout.planWithSettings(adaptation.getMaxLevel(),
          AdaptConfig.get().isGuiBackButton(), preferences);
      int page = Math.max(0, level - 1) / plan.itemsPerPage();
      LanguageAudience.run(player.getUniqueId(), () -> {
        GuiCloseSuppression.suppress(player);
        player.closeInventory();
        adaptation.openGui(player, page);
      });
    }, 1);
  }

  private static void onClosed(UIWindow window) {
    Player player = window.getViewer();
    if (Adapt.instance != null) {
      Adapt.instance.getGuiLeftovers().remove(player.getUniqueId().toString(), window);
    }
    GuiCloseSuppression.consume(player);
  }

  private static boolean accepts(Adaptation<?> adaptation, UIWindow window) {
    Player player = window.getViewer();
    return Adapt.instance != null && Adapt.instance.isEnabled() && player.isOnline() && window.isVisible()
        && Adapt.instance.getGuiLeftovers().get(player.getUniqueId().toString()) == window
        && adaptation.isEnabled() && adaptation.getSkill().isEnabled()
        && Adapt.instance.getAdaptServer().getSkillRegistry().getSkill(adaptation.getSkill().getName()) == adaptation.getSkill()
        && adaptation.getSkill().getAdaptations().contains(adaptation)
        && adaptation.getSkill().hasUsePermission(player, adaptation.getSkill())
        && adaptation.hasUsePermission(player, adaptation);
  }

  private static boolean contains(UIWindow window, Element element) {
    for (int row = window.getViewportPosition(); row < window.getViewportPosition() + window.getViewportHeight(); row++) {
      for (int position = -4; position <= 4; position++) {
        if (window.getElement(position, row) == element) {
          return true;
        }
      }
    }
    return false;
  }

  private static Element topic(GuideTopic topic) {
    return GuiTheme.element("claims-guide-" + topic.id(), topic.icon(), "gui", "claims", "guide", topic.id())
        .setName(C.GOLD + AdaptLanguage.text(topic.title()))
        .addLore(AdaptLanguage.textStyled(C.GRAY.toString(), topic.body(), topic.arguments()));
  }

  private static GuideContent content(Adaptation<?> adaptation, int level) {
    ClaimsConfig config = ((SkillClaims) adaptation.getSkill()).claimsConfig();
    return switch (adaptation.getName()) {
      case "claims-founding" -> new GuideContent(ClaimsMessages.FOUNDING_GUIDE_CONTROLS,
          ClaimsMessages.FOUNDING_GUIDE_RULES, ClaimsMessages.FOUNDING_GUIDE_CONSEQUENCES,
          MessageArgs.empty(), MessageArgs.builder().trusted("minimum", config.excludedMinimum)
          .trusted("maximum", config.excludedMaximum).untrusted("worlds", worlds(config)).build());
      case "claims-fortification" -> new GuideContent(ClaimsMessages.FORTIFICATION_GUIDE_CONTROLS,
          ClaimsMessages.FORTIFICATION_GUIDE_RULES, ClaimsMessages.FORTIFICATION_GUIDE_CONSEQUENCES,
          MessageArgs.builder().trusted("rate", config.placementAttemptsPerSecond).build(),
          MessageArgs.builder().trusted("seconds", seconds(config.offlineGraceMillis)).build());
      case "claims-citizenship" -> new GuideContent(ClaimsMessages.CITIZENSHIP_GUIDE_CONTROLS,
          ClaimsMessages.CITIZENSHIP_GUIDE_RULES, ClaimsMessages.CITIZENSHIP_GUIDE_CONSEQUENCES,
          MessageArgs.builder().trusted("seconds", seconds(config.invitationDurationMillis)).build(), MessageArgs.empty());
      case "claims-expansion" -> new GuideContent(ClaimsMessages.EXPANSION_GUIDE_CONTROLS,
          ClaimsMessages.EXPANSION_GUIDE_RULES, ClaimsMessages.EXPANSION_GUIDE_CONSEQUENCES,
          MessageArgs.empty(), MessageArgs.builder().trusted("capacity", config.chunkCapacity(
              new ClaimRanks(true, 1, 0, Math.max(1, Math.min(4, level)), false))).build());
      case "claims-homestead" -> new GuideContent(ClaimsMessages.HOMESTEAD_GUIDE_CONTROLS,
          ClaimsMessages.HOMESTEAD_GUIDE_RULES, ClaimsMessages.HOMESTEAD_GUIDE_CONSEQUENCES,
          MessageArgs.empty(), MessageArgs.empty());
      default -> throw new IllegalArgumentException("Unknown Claims adaptation: " + adaptation.getName());
    };
  }

  private static String worlds(ClaimsConfig config) {
    return config.worldAllowlist.isEmpty()
        ? AdaptLanguage.text(config.overworldOnly ? ClaimsMessages.GUIDE_WORLDS_OVERWORLD : ClaimsMessages.GUIDE_WORLDS_ANY)
        : String.join(", ", config.worldAllowlist);
  }

  private static String seconds(long millis) {
    return BigDecimal.valueOf(millis, 3).stripTrailingZeros().toPlainString();
  }

  private record GuideTopic(String id, Material icon, TextKey title, TextKey body, MessageArgs arguments) {
  }

  private record GuideContent(TextKey controls, TextKey rules, TextKey consequences,
                              MessageArgs controlsArguments, MessageArgs rulesArguments) {
  }
}
