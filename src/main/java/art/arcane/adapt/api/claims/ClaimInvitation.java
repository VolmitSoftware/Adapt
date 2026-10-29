package art.arcane.adapt.api.claims;

import java.util.UUID;

public record ClaimInvitation(UUID founder, UUID target, UUID cityId, long expiresAt) {
}
