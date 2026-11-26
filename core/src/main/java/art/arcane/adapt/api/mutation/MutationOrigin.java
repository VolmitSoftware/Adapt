package art.arcane.adapt.api.mutation;

/** The XP each parent held when the mutation was made; an unmutate gives a parent back at least this much. */
public record MutationOrigin(double leadXp, double partnerXp) {
}
