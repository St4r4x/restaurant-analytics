package com.st4r4x.sync;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class OsmEnrichmentServiceTest {

    @Test
    void normalizeName_lowercasesAndStripsNonAlpha() {
        assertThat(OsmEnrichmentService.normalizeName("Joe's Pizza!")).isEqualTo("joes pizza");
        assertThat(OsmEnrichmentService.normalizeName("THE GOLDEN DRAGON")).isEqualTo("the golden dragon");
        assertThat(OsmEnrichmentService.normalizeName(null)).isEqualTo("");
        assertThat(OsmEnrichmentService.normalizeName("  Cafe  ")).isEqualTo("cafe");
    }

    @Test
    void similarity_exactMatch_returns1() {
        assertThat(OsmEnrichmentService.similarity("joes pizza", "joes pizza")).isEqualTo(1.0);
    }

    @Test
    void similarity_completelyDifferent_returnsLow() {
        assertThat(OsmEnrichmentService.similarity("joes pizza", "dragon palace")).isLessThan(0.4);
    }

    @Test
    void similarity_closeMatch_returnsHigh() {
        assertThat(OsmEnrichmentService.similarity("joes pizza", "joe pizza")).isGreaterThan(0.8);
    }
}
