package com.st4r4x.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ElasticsearchConfigTest {

    @Test
    void buildClient_withLocalUri_returnsNonNullClient() {
        ElasticsearchClient client = ElasticsearchConfig.buildClient("http://localhost:9200");
        assertThat(client).isNotNull();
    }
}
