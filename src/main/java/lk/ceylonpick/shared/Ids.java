package lk.ceylonpick.shared;

import com.github.f4b6a3.ulid.UlidCreator;

/**
 * Identifier generation for the shared kernel. Architecture §4: ids are ULIDs
 * stored as TEXT — sortable by creation time, so they index well as primary keys.
 */
public final class Ids {

    private Ids() {
    }

    /** A monotonic ULID: ids generated within the same millisecond still sort in creation order. */
    public static String newId() {
        return UlidCreator.getMonotonicUlid().toString();
    }
}
