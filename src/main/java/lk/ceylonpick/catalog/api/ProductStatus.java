package lk.ceylonpick.catalog.api;

/**
 * BR-21: "New or changed products require admin moderation before going live."
 * A vendor can move a product to IN_MODERATION but never to LIVE.
 */
public enum ProductStatus {
    /** Being written by the vendor; invisible to everyone else. */
    DRAFT,
    /** Submitted, waiting on an admin (FR-ADM-03). */
    IN_MODERATION,
    /** Publicly visible, provided the vendor is VERIFIED. */
    LIVE,
    /** Temporarily withdrawn by the vendor; edits do not need re-moderation. */
    PAUSED,
    ARCHIVED;

    public boolean isPubliclyVisible() {
        return this == LIVE;
    }

    /** Only an admin decision may produce LIVE. */
    public boolean isSetByModeration() {
        return this == LIVE || this == IN_MODERATION;
    }
}
