package com.st4r4x.sync;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.st4r4x.config.AppConfig;
import com.st4r4x.config.MongoClientFactory;
import com.st4r4x.domain.Address;
import com.st4r4x.domain.Restaurant;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

import static com.mongodb.MongoClientSettings.getDefaultCodecRegistry;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

@Service
public class ElasticsearchSyncService {

    public static final String INDEX = "restaurants";
    private static final Logger logger = LoggerFactory.getLogger(ElasticsearchSyncService.class);
    private static final int BULK_SIZE = 500;

    private final ElasticsearchClient esClient;
    private final MongoCollection<Restaurant> mongoCollection;

    @Autowired
    public ElasticsearchSyncService(ElasticsearchClient esClient) {
        this.esClient = esClient;
        CodecRegistry registry = fromRegistries(
                getDefaultCodecRegistry(),
                fromProviders(PojoCodecProvider.builder().automatic(true).build())
        );
        MongoDatabase db = MongoClientFactory.getInstance()
                .getDatabase(AppConfig.getMongoDatabase())
                .withCodecRegistry(registry);
        this.mongoCollection = db.getCollection(AppConfig.getMongoCollection(), Restaurant.class);
    }

    // Bump this when the mapping changes to force a drop-and-recreate on next startup
    private static final String MAPPING_VERSION = "v3";
    private static final String VERSION_ALIAS = INDEX + "_" + MAPPING_VERSION;

    @PostConstruct
    public void initIndexIfEmpty() {
        try {
            boolean exists = esClient.indices().exists(e -> e.index(INDEX)).value();
            if (exists) {
                // Drop if mapping is outdated (no version alias present)
                boolean current = esClient.indices().existsAlias(a -> a.index(INDEX).name(VERSION_ALIAS)).value();
                if (!current) {
                    logger.info("ES index '{}' has stale mapping — dropping for recreate", INDEX);
                    esClient.indices().delete(d -> d.index(INDEX));
                    exists = false;
                }
            }
            if (!exists) {
                esClient.indices().create(c -> c
                        .index(INDEX)
                        .settings(s -> s
                                .analysis(a -> a
                                        .analyzer("ngram_analyzer", an -> an
                                                .custom(cu -> cu
                                                        .tokenizer("standard")
                                                        .filter("lowercase", "ngram_filter")
                                                )
                                        )
                                        .analyzer("search_analyzer", an -> an
                                                .custom(cu -> cu
                                                        .tokenizer("standard")
                                                        .filter("lowercase")
                                                )
                                        )
                                        .filter("ngram_filter", f -> f
                                                .definition(d -> d
                                                        .edgeNgram(ng -> ng.minGram(2).maxGram(15))
                                                )
                                        )
                                )
                        )
                        .mappings(m -> m
                                .properties("camis",              p -> p.keyword(k -> k))
                                .properties("dba",                p -> p.text(t -> t
                                        .analyzer("ngram_analyzer")
                                        .searchAnalyzer("search_analyzer")
                                        .fields("keyword", kf -> kf.keyword(k -> k))
                                ))
                                .properties("cuisineDescription", p -> p.text(t -> t
                                        .analyzer("ngram_analyzer")
                                        .searchAnalyzer("search_analyzer")
                                ))
                                .properties("boro",               p -> p.text(t -> t.analyzer("standard")))
                                .properties("street",             p -> p.text(t -> t
                                        .analyzer("ngram_analyzer")
                                        .searchAnalyzer("search_analyzer")
                                ))
                                .properties("zipcode",            p -> p.keyword(k -> k))
                        )
                );
                // Put version alias so we can detect stale mappings on next startup
                esClient.indices().putAlias(a -> a.index(INDEX).name(VERSION_ALIAS));
                logger.info("Created ES index '{}' with ngram mapping ({})", INDEX, MAPPING_VERSION);
            }
            long count = esClient.count(c -> c.index(INDEX)).count();
            if (count == 0) {
                logger.info("ES index '{}' is empty — triggering initial reindex", INDEX);
                triggerReindex();
            }
        } catch (Exception e) {
            logger.warn("ES init check failed (ES may be unavailable): {}", e.getMessage());
        }
    }

    private static final int REINDEX_MAX_RETRIES = 3;
    private static final long REINDEX_RETRY_DELAY_MS = 5000;

    @Async
    public void triggerReindex() {
        for (int attempt = 1; attempt <= REINDEX_MAX_RETRIES; attempt++) {
            try {
                reindex();
                return;
            } catch (Exception e) {
                logger.warn("ES reindex attempt {}/{} failed: {}", attempt, REINDEX_MAX_RETRIES, e.getMessage());
                if (attempt < REINDEX_MAX_RETRIES) {
                    try { Thread.sleep(REINDEX_RETRY_DELAY_MS); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return; }
                }
            }
        }
        logger.error("ES reindex failed after {} attempts", REINDEX_MAX_RETRIES);
    }

    public void reindex() throws Exception {
        List<Restaurant> batch = new ArrayList<>(BULK_SIZE);
        long total = 0;
        try (com.mongodb.client.MongoCursor<Restaurant> cursor = mongoCollection.find().cursor()) {
            while (cursor.hasNext()) {
                batch.add(cursor.next());
                if (batch.size() == BULK_SIZE) {
                    total += bulkIndex(batch);
                    batch.clear();
                }
            }
        }
        if (!batch.isEmpty()) total += bulkIndex(batch);
        logger.info("ES reindex complete: {} documents", total);
    }

    private int bulkIndex(List<Restaurant> restaurants) throws Exception {
        BulkRequest.Builder br = new BulkRequest.Builder();
        int indexed = 0;
        for (Restaurant r : restaurants) {
            if (r.getRestaurantId() == null) continue;
            EsRestaurantDoc doc = toEsDoc(r);
            final String id = r.getRestaurantId();
            br.operations(op -> op.index(idx -> idx
                    .index(INDEX)
                    .id(id)
                    .document(doc)
            ));
            indexed++;
        }
        if (indexed == 0) return 0;
        BulkResponse response = esClient.bulk(br.build());
        if (response.errors()) {
            logger.warn("ES bulk had errors in batch of {}", indexed);
            response.items().forEach(item -> {
                var err = item.error();
                if (err != null) {
                    logger.warn("ES bulk error for doc {}: {}", item.id(), err.reason());
                }
            });
        }
        return indexed;
    }

    static EsRestaurantDoc toEsDoc(Restaurant r) {
        EsRestaurantDoc doc = new EsRestaurantDoc();
        doc.setCamis(r.getRestaurantId());
        doc.setDba(r.getName());
        doc.setCuisineDescription(r.getCuisine());
        doc.setBoro(r.getBorough());
        Address addr = r.getAddress();
        if (addr != null) {
            doc.setStreet(addr.getStreet());
            doc.setZipcode(addr.getZipcode());
        }
        return doc;
    }

    public static class EsRestaurantDoc {
        private String camis;
        private String dba;
        private String cuisineDescription;
        private String boro;
        private String street;
        private String zipcode;

        public String getCamis() { return camis; }
        public void setCamis(String camis) { this.camis = camis; }
        public String getDba() { return dba; }
        public void setDba(String dba) { this.dba = dba; }
        public String getCuisineDescription() { return cuisineDescription; }
        public void setCuisineDescription(String cuisineDescription) { this.cuisineDescription = cuisineDescription; }
        public String getBoro() { return boro; }
        public void setBoro(String boro) { this.boro = boro; }
        public String getStreet() { return street; }
        public void setStreet(String street) { this.street = street; }
        public String getZipcode() { return zipcode; }
        public void setZipcode(String zipcode) { this.zipcode = zipcode; }
    }
}
