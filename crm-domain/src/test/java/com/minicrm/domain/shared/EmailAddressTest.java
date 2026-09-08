package com.minicrm.domain.shared;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailAddressTest {

    @Test
    void acceptsAnAddressContainingAtSign() {
        var email = new EmailAddress("alice@example.com");

        assertThat(email.value()).isEqualTo("alice@example.com");
    }

    @Test
    void rejectsAnAddressWithoutAtSign() {
        assertThatThrownBy(() -> new EmailAddress("alice.example.com"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNull() {
        assertThatThrownBy(() -> new EmailAddress(null))
                .isInstanceOf(NullPointerException.class);
    }
}
