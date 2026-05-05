package com.st4r4x.sync;

import com.st4r4x.domain.Restaurant;
import com.st4r4x.domain.Address;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ElasticsearchSyncServiceTest {

    @Test
    void toEsDoc_mapsRestaurantFields() {
        Restaurant r = new Restaurant("Joe Pizza", "Italian", "Manhattan");
        r.setRestaurantId("12345");
        Address addr = new Address();
        addr.setStreet("Broadway");
        addr.setZipcode("10001");
        r.setAddress(addr);

        ElasticsearchSyncService.EsRestaurantDoc doc = ElasticsearchSyncService.toEsDoc(r);

        assertThat(doc.getCamis()).isEqualTo("12345");
        assertThat(doc.getDba()).isEqualTo("Joe Pizza");
        assertThat(doc.getCuisineDescription()).isEqualTo("Italian");
        assertThat(doc.getBoro()).isEqualTo("Manhattan");
        assertThat(doc.getStreet()).isEqualTo("Broadway");
        assertThat(doc.getZipcode()).isEqualTo("10001");
    }

    @Test
    void toEsDoc_handlesNullAddress() {
        Restaurant r = new Restaurant("Sushi Bar", "Japanese", "Brooklyn");
        r.setRestaurantId("99999");

        ElasticsearchSyncService.EsRestaurantDoc doc = ElasticsearchSyncService.toEsDoc(r);

        assertThat(doc.getCamis()).isEqualTo("99999");
        assertThat(doc.getStreet()).isNull();
        assertThat(doc.getZipcode()).isNull();
    }
}
