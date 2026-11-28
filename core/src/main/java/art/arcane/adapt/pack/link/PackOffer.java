package art.arcane.adapt.pack.link;

import java.util.Objects;
import java.util.UUID;

/** One request to a client to download a pack. A client may decline an optional pack and stay; a required one makes it leave. */
public record PackOffer(UUID packId, String url, String sha1Hex, String prompt, boolean required) {
  public PackOffer {
    Objects.requireNonNull(packId, "packId");
    Objects.requireNonNull(url, "url");
    Objects.requireNonNull(sha1Hex, "sha1Hex");
    prompt = prompt == null ? "" : prompt;
  }
}
