package com.st4r4x.sync;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.st4r4x.domain.Restaurant;
import com.st4r4x.domain.Address;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

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

    @Test
    void initIndexIfEmpty_swallowsException() throws Exception {
        // Use Mockito's objenesis-based instantiation to bypass the constructor
        // (the constructor calls MongoClientFactory which requires a live MongoDB).
        ElasticsearchClient esClient = Mockito.mock(ElasticsearchClient.class);
        ElasticsearchSyncService syncService = Mockito.mock(
                ElasticsearchSyncService.class, Mockito.CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(syncService, "esClient", esClient);

        when(esClient.indices()).thenThrow(new RuntimeException("ES unavailable"));
        assertDoesNotThrow(() -> syncService.initIndexIfEmpty());
    }
}
