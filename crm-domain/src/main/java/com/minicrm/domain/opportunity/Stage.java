package com.minicrm.domain.opportunity;

import java.util.Set;

/**
 * Allowed progression (§3): QUALIFIED → PROPOSAL → NEGOTIATION → CLOSED_WON.
 * Any open stage may move to CLOSED_LOST. Closed stages are terminal.
 */
public enum Stage {
    QUALIFIED,
    PROPOSAL,
    NEGOTIATION,
    CLOSED_WON,
    CLOSED_LOST;

    private static final Set<Stage> OPEN_STAGES = Set.of(QUALIFIED, PROPOSAL, NEGOTIATION);

    public boolean isTerminal() {
        return this == CLOSED_WON || this == CLOSED_LOST;
    }

    public boolean canTransitionTo(Stage target) {
        if (isTerminal()) {
            return false;
        }
        if (target == CLOSED_LOST) {
            return OPEN_STAGES.contains(this);
        }
        return switch (this) {
            case QUALIFIED -> target == PROPOSAL;
            case PROPOSAL -> target == NEGOTIATION;
            case NEGOTIATION -> target == CLOSED_WON;
            case CLOSED_WON, CLOSED_LOST -> false;
        };
    }
}
