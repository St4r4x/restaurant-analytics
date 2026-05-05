package com.st4r4x.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;

@Configuration
public class ElasticsearchConfig {

    @Value("${elasticsearch.uri:http://localhost:9200}")
    private String elasticsearchUri;

    @Bean
    public ElasticsearchClient elasticsearchClient() {
        return buildClient(elasticsearchUri);
    }

    public static ElasticsearchClient buildClient(String uri) {
        URI parsed = URI.create(uri);
        RestClient restClient = RestClient.builder(
                new HttpHost(parsed.getHost(), parsed.getPort(), parsed.getScheme())
        ).build();
        RestClientTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        return new ElasticsearchClient(transport);
    }
}
