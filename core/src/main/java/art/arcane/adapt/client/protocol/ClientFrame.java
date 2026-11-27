package art.arcane.adapt.client.protocol;

import art.arcane.adapt.client.view.PreferenceOwner;
import art.arcane.adapt.platform.menu.ClickKind;

import java.util.List;
import java.util.Objects;

/** Frames the client sends on {@link ClientProtocol#CHANNEL}. The server validates every one; the client decides nothing. */
public sealed interface ClientFrame {
  /** Opens or renews negotiation. Version 0 withdraws and returns the player to the chest and action bar UI. */
  record Hello(int version, int capabilityMask) implements ClientFrame {
  }

  /** Learn {@code adaptationId} up to {@code level}, as shown in catalog {@code catalogRevision}. */
  record Learn(String adaptationId, int level, long catalogRevision) implements ClientFrame {
    public Learn {
      Objects.requireNonNull(adaptationId, "adaptationId");
    }
  }

  /** Unlearn {@code adaptationId} down to {@code level}, as shown in catalog {@code catalogRevision}. */
  record Unlearn(String adaptationId, int level, long catalogRevision) implements ClientFrame {
    public Unlearn {
      Objects.requireNonNull(adaptationId, "adaptationId");
    }
  }

  record SetPreference(PreferenceOwner owner, String ownerId, String preferenceId, String value) implements ClientFrame {
    public SetPreference {
      Objects.requireNonNull(owner, "owner");
      Objects.requireNonNull(ownerId, "ownerId");
      Objects.requireNonNull(preferenceId, "preferenceId");
      Objects.requireNonNull(value, "value");
    }
  }

  /** Resets every preference of one skill or adaptation to the server default. */
  record ResetPreference(PreferenceOwner owner, String ownerId) implements ClientFrame {
    public ResetPreference {
      Objects.requireNonNull(owner, "owner");
      Objects.requireNonNull(ownerId, "ownerId");
    }
  }

  record Open(Route route) implements ClientFrame {
    public Open {
      Objects.requireNonNull(route, "route");
    }
  }

  record PanelClick(int sessionId, int revision, int slot, ClickKind click) implements ClientFrame {
    public PanelClick {
      Objects.requireNonNull(click, "click");
    }
  }

  /** Text or number typed into a hinted tile. */
  record PanelInput(int sessionId, int revision, int slot, String text) implements ClientFrame {
    public PanelInput {
      Objects.requireNonNull(text, "text");
    }
  }

  record PanelClosed(int sessionId) implements ClientFrame {
  }

  /** Asks the server to start a mutation led by {@code skillId}; only under {@link ClientProtocol#MUTATE}. */
  record Mutate(String skillId) implements ClientFrame {
    public Mutate {
      Objects.requireNonNull(skillId, "skillId");
    }
  }

  /**
   * Asks for the spread of {@code lead} with {@code partner} (empty for none), or with {@code confirm} to merge them;
   * the server checks every rule again. Only under {@link ClientProtocol#MUTATE_SPREAD}.
   */
  record MutationRequest(String lead, String partner, boolean confirm) implements ClientFrame {
    public MutationRequest {
      Objects.requireNonNull(lead, "lead");
      Objects.requireNonNull(partner, "partner");
    }
  }

  /** Asks the server to undo the mutation {@code skillId} names; only under {@link ClientProtocol#UNMUTATE}. */
  record Unmutate(String skillId) implements ClientFrame {
    public Unmutate {
      Objects.requireNonNull(skillId, "skillId");
    }
  }

  /** The jump key went down or up, for servers before 1.21.2 that receive no player input packet. */
  record JumpKey(boolean down) implements ClientFrame {
  }

  /**
   * The files of the last {@link ServerFrame.AssetManifest} this client lacks, at most
   * {@link FrameLimits#MAX_ASSET_REQUEST} per frame; only under {@link ClientProtocol#ASSETS}.
   */
  record AssetRequest(List<String> resources) implements ClientFrame {
    public AssetRequest {
      resources = List.copyOf(resources);
    }
  }
}
