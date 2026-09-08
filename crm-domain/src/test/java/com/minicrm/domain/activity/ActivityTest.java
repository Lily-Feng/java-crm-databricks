package com.minicrm.domain.activity;

import com.minicrm.domain.shared.ActivityId;
import com.minicrm.domain.shared.CustomerId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ActivityTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void summarizesACallThroughPatternMatchingSwitch() {
        Activity call = new Call(ActivityId.newId(), CustomerId.newId(), "Renewal check-in", NOW, "alice", 15);

        assertThat(call.summary()).isEqualTo("Call: Renewal check-in (15 min)");
    }

    @Test
    void summarizesAnEmailThroughPatternMatchingSwitch() {
        Activity email = new Email(ActivityId.newId(), CustomerId.newId(), "Proposal sent", NOW, "alice", "See attached.");

        assertThat(email.summary()).isEqualTo("Email: Proposal sent");
    }

    @Test
    void summarizesAMeetingThroughPatternMatchingSwitch() {
        Activity meeting = new Meeting(
                ActivityId.newId(), CustomerId.newId(), "Kickoff", NOW, "alice", List.of("alice", "bob"));

        assertThat(meeting.summary()).isEqualTo("Meeting: Kickoff (2 attendees)");
    }

    @Test
    void meetingAttendeesAreDefensivelyCopiedAndImmutable() {
        var attendees = new java.util.ArrayList<String>();
        attendees.add("alice");
        var meeting = new Meeting(ActivityId.newId(), CustomerId.newId(), "Kickoff", NOW, "alice", attendees);

        attendees.add("bob");

        assertThat(meeting.attendees()).containsExactly("alice");
    }
}
