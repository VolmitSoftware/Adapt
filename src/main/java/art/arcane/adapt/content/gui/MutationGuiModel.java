package art.arcane.adapt.content.gui;

import art.arcane.adapt.api.mutation.MutationSnapshot;
import art.arcane.adapt.api.mutation.MutationState;
import art.arcane.adapt.api.mutation.MutationType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class MutationGuiModel {
  private MutationGuiModel() {
  }

  public static View build(int playerLevel, boolean bookshelfAuthorized, MutationSnapshot snapshot) {
    Objects.requireNonNull(snapshot);

    Slot slotOne = buildSlot(1, snapshot.slotOneId(), snapshot.slotOneUnlocked(), bookshelfAuthorized, snapshot);
    Slot slotTwo = buildSlot(2, snapshot.slotTwoId(), snapshot.slotTwoUnlocked(), bookshelfAuthorized, snapshot);
    MutationType[] types = MutationType.values();
    List<Card> cards = new ArrayList<>(types.length);
    for (MutationType type : types) {
      MutationState state = snapshot.state(type);
      int selectedSlot = selectedSlot(type, snapshot);
      boolean expressed = snapshot.expressed().contains(type);
      boolean discovered = snapshot.discovered().contains(type);
      boolean selectable = bookshelfAuthorized && state == MutationState.AVAILABLE;
      cards.add(new Card(type, state, normalizeReason(snapshot.reason(type), state), selectedSlot, expressed, discovered, selectable));
    }
    List<String> unavailableDiscoveries = new ArrayList<>();
    for (String discoveredId : snapshot.discoveredIds()) {
      if (!isBlank(discoveredId) && findType(discoveredId) == null) {
        unavailableDiscoveries.add(discoveredId);
      }
    }

    return new View(
        Math.max(0, playerLevel),
        bookshelfAuthorized,
        snapshot.perfect(),
        snapshot.cooperativeOptIn(),
        slotOne,
        slotTwo,
        List.copyOf(unavailableDiscoveries),
        List.copyOf(cards)
    );
  }

  private static Slot buildSlot(
      int index,
      String mutationId,
      boolean unlocked,
      boolean bookshelfAuthorized,
      MutationSnapshot snapshot
  ) {
    MutationType type = findType(mutationId);
    MutationState state = type == null ? MutationState.DORMANT : snapshot.state(type);
    String displayName = type == null ? normalizeUnknownId(mutationId) : type.displayName();
    String reason = type == null
        ? (isBlank(mutationId) ? "No Mutation selected" : "This saved Mutation is not available")
        : normalizeReason(snapshot.reason(type), state);
    boolean canClear = bookshelfAuthorized && unlocked && !isBlank(mutationId);
    return new Slot(index, unlocked, mutationId, displayName, state, reason, canClear);
  }

  private static MutationType findType(String mutationId) {
    if (isBlank(mutationId)) {
      return null;
    }
    for (MutationType type : MutationType.values()) {
      if (type.id().equalsIgnoreCase(mutationId)) {
        return type;
      }
    }
    return null;
  }

  private static int selectedSlot(MutationType type, MutationSnapshot snapshot) {
    if (type.id().equalsIgnoreCase(snapshot.slotOneId())) {
      return 1;
    }
    if (type.id().equalsIgnoreCase(snapshot.slotTwoId())) {
      return 2;
    }
    return 0;
  }

  private static String normalizeReason(String reason, MutationState state) {
    if (!isBlank(reason)) {
      return reason;
    }
    return switch (state) {
      case LOCKED -> "The needed slot is locked";
      case AVAILABLE -> "Ready to use";
      case EXPRESSED -> "Active";
      case DORMANT -> "Equipped but inactive";
      case DISABLED -> "Turned off by the server";
      case RESTRICTED -> "Not available here";
      case CONFLICT -> "Blocked by the other Mutation";
    };
  }

  private static String normalizeUnknownId(String mutationId) {
    return isBlank(mutationId) ? "Empty" : mutationId;
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  public record View(
      int playerLevel,
      boolean bookshelfAuthorized,
      boolean perfect,
      boolean cooperativeOptIn,
      Slot slotOne,
      Slot slotTwo,
      List<String> unavailableDiscoveries,
      List<Card> cards
  ) {
  }

  public record Slot(
      int index,
      boolean unlocked,
      String mutationId,
      String displayName,
      MutationState state,
      String reason,
      boolean canClear
  ) {
  }

  public record Card(
      MutationType type,
      MutationState state,
      String reason,
      int selectedSlot,
      boolean expressed,
      boolean discovered,
      boolean selectable
  ) {
  }
}
