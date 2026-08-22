package art.arcane.adapt.papi;

import art.arcane.adapt.api.mutation.MutationSnapshot;
import art.arcane.adapt.api.mutation.MutationState;
import art.arcane.volmlib.util.bukkit.papi.PlaceholderKeyRegistry;
import art.arcane.volmlib.util.bukkit.papi.PlaceholderSnapshot;
import art.arcane.volmlib.util.bukkit.papi.PlaceholderValues;
import art.arcane.volmlib.util.bukkit.papi.PlayerSnapshotStore;
import art.arcane.volmlib.util.bukkit.papi.VolmitPlaceholderExpansion;

import java.util.Locale;
import java.util.UUID;
import java.util.logging.Logger;

public final class AdaptPapiExpansion extends VolmitPlaceholderExpansion {
  public static final String IDENTIFIER = "adapt";
  public static final String AUTHOR = "Volmit Software";
  public static final String VERSION = "1.0.0";
  public static final String REQUIRED_PLUGIN = "Adapt";

  private static final String UNAVAILABLE = PlaceholderValues.UNAVAILABLE;
  private static final int INVALID_ARGUMENT = -1;
  private static final int MAX_ARGUMENT_DIGITS = 4;
  private static final String[] MUTATION_STATE_TEXT = mutationStateText();

  public AdaptPapiExpansion(
      PlayerSnapshotStore<AdaptPlayerSnapshot> players,
      PlaceholderSnapshot<AdaptCatalogSnapshot> catalog,
      Logger logger
  ) {
    super(IDENTIFIER, AUTHOR, VERSION, REQUIRED_PLUGIN, registry(players, catalog), logger);
  }

  private static PlaceholderKeyRegistry registry(
      PlayerSnapshotStore<AdaptPlayerSnapshot> players,
      PlaceholderSnapshot<AdaptCatalogSnapshot> catalog
  ) {
    return PlaceholderKeyRegistry.builder()
        .key(PlaceholderKeyRegistry.AVAILABLE, playerId -> PlaceholderValues.bool(players.get(playerId) != null))
        .key("catalog.available", playerId -> PlaceholderValues.bool(catalog.get() != null))
        .key("catalog.skills", playerId -> {
          AdaptCatalogSnapshot snapshot = catalog.get();
          return snapshot == null ? UNAVAILABLE : snapshot.skillCountText();
        })
        .key("catalog.adaptations", playerId -> {
          AdaptCatalogSnapshot snapshot = catalog.get();
          return snapshot == null ? UNAVAILABLE : snapshot.adaptationCountText();
        })
        .key("catalog.mutations", playerId -> {
          AdaptCatalogSnapshot snapshot = catalog.get();
          return snapshot == null ? UNAVAILABLE : snapshot.mutationCountText();
        })
        .key("player.level", playerId -> {
          AdaptPlayerSnapshot snapshot = players.get(playerId);
          return snapshot == null ? UNAVAILABLE : snapshot.levelText();
        })
        .key("player.max-level", playerId -> {
          AdaptPlayerSnapshot snapshot = players.get(playerId);
          return snapshot == null ? UNAVAILABLE : snapshot.maxLevelText();
        })
        .key("player.master-xp", playerId -> {
          AdaptPlayerSnapshot snapshot = players.get(playerId);
          return snapshot == null ? UNAVAILABLE : snapshot.masterXpText();
        })
        .key("player.multiplier", playerId -> {
          AdaptPlayerSnapshot snapshot = players.get(playerId);
          return snapshot == null ? UNAVAILABLE : snapshot.multiplierText();
        })
        .key("player.wisdom", playerId -> {
          AdaptPlayerSnapshot snapshot = players.get(playerId);
          return snapshot == null ? UNAVAILABLE : snapshot.wisdomText();
        })
        .key("player.power", playerId -> {
          AdaptPlayerSnapshot snapshot = players.get(playerId);
          return snapshot == null ? UNAVAILABLE : snapshot.powerText();
        })
        .key("player.power-max", playerId -> {
          AdaptPlayerSnapshot snapshot = players.get(playerId);
          return snapshot == null ? UNAVAILABLE : snapshot.powerMaxText();
        })
        .key("player.power-used", playerId -> {
          AdaptPlayerSnapshot snapshot = players.get(playerId);
          return snapshot == null ? UNAVAILABLE : snapshot.powerUsedText();
        })
        .key("player.known-skills", playerId -> {
          AdaptPlayerSnapshot snapshot = players.get(playerId);
          return snapshot == null ? UNAVAILABLE : snapshot.knownSkillsText();
        })
        .key("player.learned-adaptations", playerId -> {
          AdaptPlayerSnapshot snapshot = players.get(playerId);
          return snapshot == null ? UNAVAILABLE : snapshot.learnedAdaptationsText();
        })
        .key("mutation.available", playerId -> {
          AdaptPlayerSnapshot snapshot = players.get(playerId);
          return PlaceholderValues.bool(snapshot != null && snapshot.mutations().available());
        })
        .key("mutation.enabled", playerId -> mutationFlag(players, playerId, AdaptMutationView::enabled))
        .key("mutation.perfect", playerId -> mutationFlag(players, playerId, AdaptMutationView::perfect))
        .key("mutation.can-swap", playerId -> mutationFlag(players, playerId, AdaptMutationView::canSwap))
        .key("mutation.slot-1-unlocked", playerId -> mutationFlag(players, playerId, AdaptMutationView::slotOneUnlocked))
        .key("mutation.slot-2-unlocked", playerId -> mutationFlag(players, playerId, AdaptMutationView::slotTwoUnlocked))
        .key("mutation.expressed", playerId -> mutationText(players, playerId, AdaptMutationView::expressedCountText))
        .key("mutation.combat-lock", playerId -> mutationText(players, playerId, AdaptMutationView::combatLockText))
        .key("mutation.slot-1", playerId -> mutationSlotText(players, playerId, true, false))
        .key("mutation.slot-2", playerId -> mutationSlotText(players, playerId, false, false))
        .key("mutation.slot-1-id", playerId -> mutationSlotText(players, playerId, true, true))
        .key("mutation.slot-2-id", playerId -> mutationSlotText(players, playerId, false, true))
        .group("skill", (playerId, tail) -> resolveSkill(players, catalog, playerId, tail))
        .group("adaptation", (playerId, tail) -> resolveAdaptation(players, catalog, playerId, tail))
        .group("mutation", (playerId, tail) -> resolveMutation(players, catalog, playerId, tail))
        .build();
  }

  private static String resolveSkill(
      PlayerSnapshotStore<AdaptPlayerSnapshot> players,
      PlaceholderSnapshot<AdaptCatalogSnapshot> catalog,
      UUID playerId,
      String tail
  ) {
    int split = tail.indexOf('.');

    if (split <= 0 || split == tail.length() - 1) {
      return null;
    }

    AdaptCatalogSnapshot catalogSnapshot = catalog.get();

    if (catalogSnapshot == null) {
      return UNAVAILABLE;
    }

    String skillId = tail.substring(0, split);
    AdaptCatalogSkill skill = catalogSnapshot.skill(skillId);

    if (skill == null) {
      return null;
    }

    String attribute = tail.substring(split + 1);
    AdaptPlayerSnapshot snapshot = players.get(playerId);

    if (snapshot == null) {
      return UNAVAILABLE;
    }

    AdaptSkillLineSnapshot line = snapshot.skill(skillId);

    if (line == null) {
      line = snapshot.zeroLine();
    }

    int argumentSplit = attribute.lastIndexOf('.');

    if (argumentSplit > 0 && argumentSplit < attribute.length() - 1) {
      int argument = parseArgument(attribute, argumentSplit + 1);

      if (argument != INVALID_ARGUMENT) {
        return switch (attribute.substring(0, argumentSplit)) {
          case "has-level" -> PlaceholderValues.bool(line.band().level() >= argument);
          default -> null;
        };
      }
    }

    AdaptSkillLineSnapshot resolved = line;

    return switch (attribute) {
      case "level" -> resolved.band().levelText();
      case "xp" -> resolved.band().xpText();
      case "knowledge" -> resolved.knowledgeText();
      case "multiplier" -> resolved.multiplierText();
      case "progress" -> resolved.band().progressText();
      case "progress-percent" -> resolved.band().progressPercentText();
      case "xp-to-next" -> resolved.band().xpToNextText();
      case "current-level-xp" -> resolved.band().currentLevelXpText();
      case "next-level-xp" -> resolved.band().nextLevelXpText();
      case "learned-adaptations" -> resolved.learnedAdaptationsText();
      case "known" -> PlaceholderValues.bool(resolved.band().level() > 0);
      case "name" -> skill.nameText();
      case "enabled" -> PlaceholderValues.bool(skill.enabled());
      case "adaptations" -> skill.adaptationCountText();
      default -> null;
    };
  }

  private static String resolveAdaptation(
      PlayerSnapshotStore<AdaptPlayerSnapshot> players,
      PlaceholderSnapshot<AdaptCatalogSnapshot> catalog,
      UUID playerId,
      String tail
  ) {
    int split = tail.indexOf('.');

    if (split <= 0 || split == tail.length() - 1) {
      return null;
    }

    AdaptCatalogSnapshot catalogSnapshot = catalog.get();

    if (catalogSnapshot == null) {
      return UNAVAILABLE;
    }

    String adaptationId = tail.substring(0, split);
    AdaptCatalogAdaptation adaptation = catalogSnapshot.adaptation(adaptationId);

    if (adaptation == null) {
      return null;
    }

    String attribute = tail.substring(split + 1);
    AdaptPlayerSnapshot snapshot = players.get(playerId);

    if (snapshot == null) {
      return UNAVAILABLE;
    }

    int current = adaptation.clampLevel(snapshot.adaptationLevel(adaptationId));
    int argumentSplit = attribute.lastIndexOf('.');

    if (argumentSplit > 0 && argumentSplit < attribute.length() - 1) {
      int argument = parseArgument(attribute, argumentSplit + 1);

      if (argument != INVALID_ARGUMENT) {
        return switch (attribute.substring(0, argumentSplit)) {
          case "cost-to" -> PlaceholderValues.count(adaptation.knowledgeCostFor(argument, current));
          case "power-to" -> PlaceholderValues.count(adaptation.powerCostFor(argument, current));
          case "can-claim" -> PlaceholderValues.bool(canClaim(snapshot, adaptation, current, adaptation.clampLevel(argument)));
          default -> null;
        };
      }
    }

    int next = adaptation.nextLevel(current);

    return switch (attribute) {
      case "level" -> PlaceholderValues.count(current);
      case "max-level" -> adaptation.maxLevelText();
      case "name" -> adaptation.nameText();
      case "skill" -> adaptation.skillId();
      case "enabled" -> PlaceholderValues.bool(adaptation.enabled());
      case "learned" -> PlaceholderValues.bool(current > 0);
      case "can-use" -> PlaceholderValues.bool(current > 0 && adaptation.enabled() && adaptation.skillEnabled());
      case "cost-next" -> PlaceholderValues.count(adaptation.knowledgeCostFor(next, current));
      case "power-next" -> PlaceholderValues.count(adaptation.powerCostFor(next, current));
      case "can-claim-next" -> PlaceholderValues.bool(canClaim(snapshot, adaptation, current, next));
      default -> null;
    };
  }

  private static String resolveMutation(
      PlayerSnapshotStore<AdaptPlayerSnapshot> players,
      PlaceholderSnapshot<AdaptCatalogSnapshot> catalog,
      UUID playerId,
      String tail
  ) {
    int split = tail.lastIndexOf('.');

    if (split <= 0 || split == tail.length() - 1) {
      return null;
    }

    AdaptCatalogSnapshot catalogSnapshot = catalog.get();

    if (catalogSnapshot == null) {
      return UNAVAILABLE;
    }

    AdaptCatalogMutation mutation = catalogSnapshot.mutation(tail.substring(0, split));

    if (mutation == null) {
      return null;
    }

    String attribute = tail.substring(split + 1);
    AdaptPlayerSnapshot snapshot = players.get(playerId);

    if (snapshot == null) {
      return UNAVAILABLE;
    }

    AdaptMutationView view = snapshot.mutations();
    MutationSnapshot source = view.source();

    if (!view.available() || source == null) {
      return switch (attribute) {
        case "id", "name", "state", "expressed", "qualified", "slot" -> UNAVAILABLE;
        default -> null;
      };
    }

    return switch (attribute) {
      case "id" -> mutation.id();
      case "name" -> mutation.nameText();
      case "state" -> MUTATION_STATE_TEXT[source.state(mutation.type()).ordinal()];
      case "expressed" -> PlaceholderValues.bool(source.expressed().contains(mutation.type()));
      case "qualified" -> PlaceholderValues.bool(source.qualified(mutation.type()));
      case "slot" -> PlaceholderValues.count(slotOf(view, mutation.id()));
      default -> null;
    };
  }

  private static boolean canClaim(
      AdaptPlayerSnapshot snapshot,
      AdaptCatalogAdaptation adaptation,
      int currentLevel,
      int targetLevel
  ) {
    if (targetLevel == currentLevel) {
      return true;
    }

    if (targetLevel > currentLevel) {
      if (snapshot.availablePower() < adaptation.powerCostFor(targetLevel, currentLevel)) {
        return false;
      }

      return snapshot.knowledgeForSkill(adaptation.skillId()) >= adaptation.knowledgeCostFor(targetLevel, currentLevel);
    }

    if (adaptation.permanent()) {
      return false;
    }

    return currentLevel > 0;
  }

  private static int slotOf(AdaptMutationView view, String mutationId) {
    if (mutationId.equals(view.slotOneId())) {
      return 1;
    }

    if (mutationId.equals(view.slotTwoId())) {
      return 2;
    }

    return 0;
  }

  private static String mutationFlag(
      PlayerSnapshotStore<AdaptPlayerSnapshot> players,
      UUID playerId,
      MutationFlag flag
  ) {
    AdaptPlayerSnapshot snapshot = players.get(playerId);

    if (snapshot == null || !snapshot.mutations().available()) {
      return UNAVAILABLE;
    }

    return PlaceholderValues.bool(flag.read(snapshot.mutations()));
  }

  private static String mutationText(
      PlayerSnapshotStore<AdaptPlayerSnapshot> players,
      UUID playerId,
      MutationText text
  ) {
    AdaptPlayerSnapshot snapshot = players.get(playerId);

    if (snapshot == null || !snapshot.mutations().available()) {
      return UNAVAILABLE;
    }

    return text.read(snapshot.mutations());
  }

  private static String mutationSlotText(
      PlayerSnapshotStore<AdaptPlayerSnapshot> players,
      UUID playerId,
      boolean firstSlot,
      boolean identifier
  ) {
    AdaptPlayerSnapshot snapshot = players.get(playerId);

    if (snapshot == null || !snapshot.mutations().available()) {
      return UNAVAILABLE;
    }

    AdaptMutationView view = snapshot.mutations();
    String value;

    if (identifier) {
      value = firstSlot ? view.slotOneId() : view.slotTwoId();
    } else {
      value = firstSlot ? view.slotOneName() : view.slotTwoName();
    }

    return value.isEmpty() ? UNAVAILABLE : value;
  }

  private static int parseArgument(String attribute, int from) {
    int length = attribute.length() - from;

    if (length <= 0 || length > MAX_ARGUMENT_DIGITS) {
      return INVALID_ARGUMENT;
    }

    int value = 0;

    for (int index = from; index < attribute.length(); index++) {
      char character = attribute.charAt(index);

      if (character < '0' || character > '9') {
        return INVALID_ARGUMENT;
      }

      value = (value * 10) + (character - '0');
    }

    return value;
  }

  private static String[] mutationStateText() {
    MutationState[] states = MutationState.values();
    String[] text = new String[states.length];

    for (int index = 0; index < states.length; index++) {
      text[index] = states[index].name().toLowerCase(Locale.ROOT);
    }

    return text;
  }

  @FunctionalInterface
  private interface MutationFlag {
    boolean read(AdaptMutationView view);
  }

  @FunctionalInterface
  private interface MutationText {
    String read(AdaptMutationView view);
  }
}
