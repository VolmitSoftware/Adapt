package art.arcane.adapt.pack.link;

import java.util.Objects;
import java.util.UUID;

/** One request to a client to download a pack. Never forced: a client may always decline and stay. */
public record PackOffer(UUID packId, String url, String sha1Hex, String prompt) {
  public PackOffer {
    Objects.requireNonNull(packId, "packId");
    Objects.requireNonNull(url, "url");
    Objects.requireNonNull(sha1Hex, "sha1Hex");
    prompt = prompt == null ? "" : prompt;
  }
}
