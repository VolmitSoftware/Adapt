package art.arcane.adapt.papi;

import art.arcane.adapt.api.mutation.MutationType;

public record AdaptCatalogMutation(String id, String nameText, MutationType type) {
}
