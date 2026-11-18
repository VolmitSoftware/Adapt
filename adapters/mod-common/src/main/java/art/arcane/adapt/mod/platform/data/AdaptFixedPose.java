package art.arcane.adapt.mod.platform.data;

/** Implemented on every entity by the platform mixins: while fixed, the entity ignores pose changes. */
public interface AdaptFixedPose {
    boolean adapt$fixedPose();

    void adapt$setFixedPose(boolean fixed);
}
