package com.minicrm.domain.opportunity;

/**
 * Maps to a distinct 409 problem type at the REST adapter (§3): an invalid transition
 * is a different failure than a stale-version conflict, even though both are 409s.
 */
public final class InvalidStageTransitionException extends RuntimeException {

    private final Stage from;
    private final Stage to;

    public InvalidStageTransitionException(Stage from, Stage to) {
        super("Cannot transition opportunity from %s to %s".formatted(from, to));
        this.from = from;
        this.to = to;
    }

    public Stage from() {
        return from;
    }

    public Stage to() {
        return to;
    }
}
