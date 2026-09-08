package com.minicrm.domain.shared;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdentifierTest {

    @Test
    void customerIdRejectsNull() {
        assertThatThrownBy(() -> new CustomerId(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void contactIdRejectsNull() {
        assertThatThrownBy(() -> new ContactId(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void opportunityIdRejectsNull() {
        assertThatThrownBy(() -> new OpportunityId(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void activityIdRejectsNull() {
        assertThatThrownBy(() -> new ActivityId(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void newIdGeneratesDistinctValues() {
        assertThat(CustomerId.newId()).isNotEqualTo(CustomerId.newId());
    }

    @Test
    void ofParsesACanonicalUuidString() {
        var id = CustomerId.newId();

        assertThat(CustomerId.of(id.value().toString())).isEqualTo(id);
    }
}
