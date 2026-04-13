package com.st4r4x.sync;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.st4r4x.config.AppConfig;

/**
 * HTTP client for the NYC Open Data restaurant inspection API.
 * Fetches all pages with exponential back-off retry (3 attempts).
 */
@Component
public class NycOpenDataClient {

    private static final Logger logger = LoggerFactory.getLogger(NycOpenDataClient.class);

    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_BACKOFF_MS = 2_000;

    private final RestTemplate restTemplate;

    public NycOpenDataClient() {
        this.restTemplate = new RestTemplate();
    }

    // Constructor for test injection
    public NycOpenDataClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Fetches all restaurant inspection records from the API, paginating until exhausted.
     *
     * @return flat list of raw DTO records
     */
    public List<NycApiRestaurantDto> fetchAll() {
        List<NycApiRestaurantDto> all = new ArrayList<>();
        int pageSize = AppConfig.getNycApiPageSize();
        int maxRecords = AppConfig.getNycApiMaxRecords();
        int offset = 0;

        while (true) {
            int limit = (maxRecords > 0) ? Math.min(pageSize, maxRecords - all.size()) : pageSize;
            List<NycApiRestaurantDto> page = fetchPage(offset, limit);
            if (page.isEmpty()) break;
            all.addAll(page);
            logger.info("Fetched {} records (total so far: {})", page.size(), all.size());
            if (page.size() < limit) break;
            if (maxRecords > 0 && all.size() >= maxRecords) break;
            offset += pageSize;
        }

        logger.info("NYC Open Data fetch complete — {} total records", all.size());
        return all;
    }

    /**
     * Fetches a single page with retry + exponential back-off.
     */
    List<NycApiRestaurantDto> fetchPage(int offset, int limit) {
        String url = buildUrl(offset, limit);
        Exception lastException = null;
        long backoff = INITIAL_BACKOFF_MS;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                NycApiRestaurantDto[] response = restTemplate.getForObject(url, NycApiRestaurantDto[].class);
                if (response == null) return Collections.emptyList();
                return Arrays.asList(response);
            } catch (RestClientException e) {
                lastException = e;
                logger.warn("API call failed (attempt {}/{}): {}", attempt, MAX_RETRIES, e.getMessage());
                if (attempt < MAX_RETRIES) {
                    sleep(backoff);
                    backoff *= 2;
                }
            }
        }

        logger.error("All {} attempts failed for offset={}", MAX_RETRIES, offset, lastException);
        throw new RuntimeException("NYC Open Data API unavailable after " + MAX_RETRIES + " attempts", lastException);
    }

    private String buildUrl(int offset, int limit) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(AppConfig.getNycApiUrl())
                .queryParam("$limit", limit)
                .queryParam("$offset", offset)
                .queryParam("$order", "camis,inspection_date");

        String token = AppConfig.getNycApiToken();
        if (token != null && !token.isEmpty()) {
            builder.queryParam("$$app_token", token);
        }

        return builder.toUriString();
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
