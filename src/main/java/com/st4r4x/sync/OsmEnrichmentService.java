package com.st4r4x.sync;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.st4r4x.config.AppConfig;
import com.st4r4x.config.MongoClientFactory;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static com.mongodb.client.model.Filters.*;

@Service
public class OsmEnrichmentService {

    private static final Logger logger = LoggerFactory.getLogger(OsmEnrichmentService.class);
    private static final String OVERPASS_URL = "https://overpass-api.de/api/interpreter";
    private static final double SIMILARITY_THRESHOLD = 0.75;

    private final MongoCollection<Document> collection;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OsmEnrichmentService() {
        MongoDatabase db = MongoClientFactory.getInstance().getDatabase(AppConfig.getMongoDatabase());
        this.collection = db.getCollection(AppConfig.getMongoCollection());
        this.restTemplate = new RestTemplate();
    }

    /** Enrich only restaurants not yet enriched. Called after each sync. */
    public void enrichNew() {
        List<Document> pending = collection
                .find(not(exists("osm_enriched_at")))
                .projection(new Document("restaurant_id", 1).append("name", 1).append("borough", 1))
                .into(new ArrayList<>());

        logger.info("OSM enrichment: {} restaurants pending", pending.size());
        enrichList(pending);
    }

    /** Re-enrich all restaurants. Triggered by admin endpoint. */
    @Async
    public void enrichAll() {
        List<Document> all = collection
                .find()
                .projection(new Document("restaurant_id", 1).append("name", 1).append("borough", 1))
                .into(new ArrayList<>());

        logger.info("OSM full re-enrichment: {} restaurants", all.size());
        enrichList(all);
    }

    private void enrichList(List<Document> restaurants) {
        int enriched = 0;
        for (Document doc : restaurants) {
            try {
                String name = doc.getString("name");
                String borough = doc.getString("borough");
                String restaurantId = doc.getString("restaurant_id");
                if (name == null || borough == null || restaurantId == null) continue;

                OsmResult result = queryOverpass(name, borough);
                Document update = new Document("osm_enriched_at", Instant.now());
                if (result != null) {
                    if (result.phone != null) update.append("osm_phone", result.phone);
                    if (result.website != null) update.append("osm_website", result.website);
                    if (result.openingHours != null) update.append("osm_opening_hours", result.openingHours);
                    enriched++;
                }
                collection.updateOne(
                        eq("restaurant_id", restaurantId),
                        new Document("$set", update)
                );
                Thread.sleep(1000); // Overpass rate limit: 1 req/s
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("OSM enrichment interrupted");
                return;
            } catch (Exception e) {
                logger.debug("OSM enrichment failed for {}: {}", doc.getString("name"), e.getMessage());
            }
        }
        logger.info("OSM enrichment complete: {}/{} matched", enriched, restaurants.size());
    }

    private OsmResult queryOverpass(String name, String borough) throws Exception {
        double[] bbox = boroughBbox(borough);
        if (bbox == null) return null;

        String query = String.format(
                "[out:json][timeout:10];node[\"amenity\"=\"restaurant\"][\"name\"][bbox:%f,%f,%f,%f];out tags;",
                bbox[0], bbox[1], bbox[2], bbox[3]);

        String url = OVERPASS_URL + "?data=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
        String response = restTemplate.getForObject(url, String.class);
        if (response == null) return null;

        JsonNode root = objectMapper.readTree(response);
        JsonNode elements = root.path("elements");
        String normalizedTarget = normalizeName(name);

        OsmResult best = null;
        double bestScore = SIMILARITY_THRESHOLD;

        for (JsonNode el : elements) {
            JsonNode tags = el.path("tags");
            String osmName = tags.path("name").asText(null);
            if (osmName == null) continue;
            double score = similarity(normalizedTarget, normalizeName(osmName));
            if (score > bestScore) {
                bestScore = score;
                best = new OsmResult(
                        tags.path("phone").asText(null),
                        tags.path("website").asText(null),
                        tags.path("opening_hours").asText(null)
                );
            }
        }
        return best;
    }

    // Package-private for testing
    static String normalizeName(String name) {
        if (name == null) return "";
        return name.toLowerCase().replaceAll("[^a-z0-9 ]", "").trim();
    }

    static double similarity(String a, String b) {
        if (a.equals(b)) return 1.0;
        int maxLen = Math.max(a.length(), b.length());
        if (maxLen == 0) return 1.0;
        return 1.0 - (double) levenshtein(a, b) / maxLen;
    }

    private static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            int[] curr = new int[b.length() + 1];
            curr[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            prev = curr;
        }
        return prev[b.length()];
    }

    private double[] boroughBbox(String borough) {
        if (borough == null) return null;
        switch (borough) {
            case "Manhattan":     return new double[]{40.700, -74.020, 40.882, -73.907};
            case "Brooklyn":      return new double[]{40.570, -74.042, 40.739, -73.833};
            case "Queens":        return new double[]{40.541, -73.962, 40.800, -73.700};
            case "Bronx":         return new double[]{40.785, -73.933, 40.918, -73.748};
            case "Staten Island": return new double[]{40.477, -74.259, 40.651, -74.034};
            default:              return null;
        }
    }

    private static class OsmResult {
        final String phone;
        final String website;
        final String openingHours;
        OsmResult(String phone, String website, String openingHours) {
            this.phone = phone;
            this.website = website;
            this.openingHours = openingHours;
        }
    }
}
