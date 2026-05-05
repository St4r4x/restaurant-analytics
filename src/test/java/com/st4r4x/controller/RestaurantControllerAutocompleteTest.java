package com.st4r4x.controller;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import co.elastic.clients.elasticsearch.core.search.TotalHitsRelation;
import com.st4r4x.cache.RestaurantCacheService;
import com.st4r4x.dao.AnalyticsDAO;
import com.st4r4x.dao.RestaurantDAO;
import com.st4r4x.service.RestaurantService;
import com.st4r4x.sync.ElasticsearchSyncService.EsRestaurantDoc;
import com.st4r4x.sync.SyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import co.elastic.clients.elasticsearch.core.SearchRequest;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class RestaurantControllerAutocompleteTest {

    @Mock ElasticsearchClient esClient;
    @Mock RestaurantDAO restaurantDAO;
    @Mock AnalyticsDAO analyticsDAO;
    @Mock RestaurantService restaurantService;
    @Mock SyncService syncService;
    @Mock RestaurantCacheService cacheService;
    @InjectMocks RestaurantController controller;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @SuppressWarnings("unchecked")
    void autocomplete_returnsResultsFromES() throws Exception {
        EsRestaurantDoc doc = new EsRestaurantDoc();
        doc.setCamis("12345");
        doc.setDba("Joe Pizza");
        doc.setCuisineDescription("Italian");
        doc.setBoro("Manhattan");
        doc.setStreet("Broadway");

        Hit<EsRestaurantDoc> hit = Hit.of(h -> h.index("restaurants").id("12345").source(doc));
        HitsMetadata<EsRestaurantDoc> meta = HitsMetadata.of(m -> m
                .hits(List.of(hit))
                .total(t -> t.value(1).relation(TotalHitsRelation.Eq)));
        SearchResponse<EsRestaurantDoc> resp = SearchResponse.of(r -> r
                .took(1).timedOut(false)
                .shards(s -> s.total(1).successful(1).failed(0))
                .hits(meta));

        SearchResponse<Object> respObj = (SearchResponse<Object>) (SearchResponse<?>) resp;
        doReturn(respObj).when(esClient).search(any(SearchRequest.class), any(Class.class));

        mvc.perform(get("/api/restaurants/autocomplete?q=joe"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data[0].camis").value("12345"))
                .andExpect(jsonPath("$.data[0].dba").value("Joe Pizza"));
    }

    @Test
    void autocomplete_missingQ_returns400() throws Exception {
        mvc.perform(get("/api/restaurants/autocomplete"))
                .andExpect(status().isBadRequest());
    }
}
