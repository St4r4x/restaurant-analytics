package com.st4r4x.domain;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;

class RestaurantOsmFieldsTest {

    @Test
    void osmFields_defaultToNull() {
        Restaurant r = new Restaurant();
        assertThat(r.getOsmPhone()).isNull();
        assertThat(r.getOsmWebsite()).isNull();
        assertThat(r.getOsmOpeningHours()).isNull();
        assertThat(r.getOsmEnrichedAt()).isNull();
    }

    @Test
    void osmFields_roundTrip() {
        Restaurant r = new Restaurant();
        Instant now = Instant.now();
        r.setOsmPhone("+1-212-555-0100");
        r.setOsmWebsite("https://example.com");
        r.setOsmOpeningHours("Mo-Fr 11:00-22:00");
        r.setOsmEnrichedAt(now);

        assertThat(r.getOsmPhone()).isEqualTo("+1-212-555-0100");
        assertThat(r.getOsmWebsite()).isEqualTo("https://example.com");
        assertThat(r.getOsmOpeningHours()).isEqualTo("Mo-Fr 11:00-22:00");
        assertThat(r.getOsmEnrichedAt()).isEqualTo(now);
    }
}
