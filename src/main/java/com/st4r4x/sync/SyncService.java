package com.st4r4x.sync;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.st4r4x.cache.RestaurantCacheService;
import com.st4r4x.dao.RestaurantDAO;
import com.st4r4x.domain.Address;
import com.st4r4x.domain.Grade;
import com.st4r4x.domain.Restaurant;

/**
 * Orchestrates the nightly sync from the NYC Open Data API into MongoDB.
 *
 * Flow: fetch all inspection rows → group by camis → map to Restaurant + grades → upsert
 *
 * Scheduled: every day at 02:00 (server local time).
 * Manual trigger: SyncService#runSync() is also called by POST /api/restaurants/refresh.
 */
@Service
public class SyncService {

    private static final Logger logger = LoggerFactory.getLogger(SyncService.class);

    private final NycOpenDataClient apiClient;
    private final RestaurantDAO restaurantDAO;
    private final RestaurantCacheService cacheService;

    private volatile SyncResult lastResult;
    private volatile boolean running = false;
    private volatile Instant runningStartedAt;

    @Autowired
    public SyncService(NycOpenDataClient apiClient, RestaurantDAO restaurantDAO,
                       RestaurantCacheService cacheService) {
        this.apiClient = apiClient;
        this.restaurantDAO = restaurantDAO;
        this.cacheService = cacheService;
    }

    /**
     * Scheduled nightly sync at 02:00.
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void scheduledSync() {
        logger.info("Starting scheduled nightly sync");
        runSync();
    }

    public SyncResult runSync() {
        Instant start = Instant.now();
        running = true;
        runningStartedAt = start;
        SyncResult result;
        try {
            cacheService.invalidateAll();

            final List<NycApiRestaurantDto>[] carry = new List[]{new ArrayList<>()};
            final int[] rawCount = {0};
            final int[] upsertCount = {0};

            apiClient.streamPages(page -> {
                rawCount[0] += page.size();

                List<NycApiRestaurantDto> work = new ArrayList<>(carry[0].size() + page.size());
                work.addAll(carry[0]);
                work.addAll(page);
                carry[0].clear();

                String lastCamis = work.get(work.size() - 1).getCamis();
                List<NycApiRestaurantDto> complete = new ArrayList<>();
                for (NycApiRestaurantDto row : work) {
                    if (row.getCamis() != null && row.getCamis().equals(lastCamis)) {
                        carry[0].add(row);
                    } else {
                        complete.add(row);
                    }
                }

                if (!complete.isEmpty()) {
                    List<Restaurant> batch = mapToRestaurants(complete);
                    if (!batch.isEmpty()) {
                        restaurantDAO.upsertRestaurants(batch);
                        cacheService.updateTopRestaurants(batch);
                        upsertCount[0] += batch.size();
                    }
                }
            });

            if (!carry[0].isEmpty()) {
                List<Restaurant> last = mapToRestaurants(carry[0]);
                if (!last.isEmpty()) {
                    restaurantDAO.upsertRestaurants(last);
                    cacheService.updateTopRestaurants(last);
                    upsertCount[0] += last.size();
                }
            }

            result = SyncResult.builder()
                    .startedAt(start)
                    .completedAt(Instant.now())
                    .rawRecords(rawCount[0])
                    .upsertedRestaurants(upsertCount[0])
                    .success(true)
                    .build();

            lastResult = result;
            logger.info("Sync complete: {}", result);

        } catch (Exception e) {
            result = SyncResult.builder()
                    .startedAt(start)
                    .completedAt(Instant.now())
                    .rawRecords(0)
                    .upsertedRestaurants(0)
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();

            lastResult = result;
            logger.error("Sync failed: {}", e.getMessage(), e);

        } finally {
            running = false;
        }
        return result;
    }

    public boolean isRunning() { return running; }
    public Instant getRunningStartedAt() { return runningStartedAt; }

    /**
     * Returns the result of the last sync, or null if no sync has run yet.
     */
    public SyncResult getLastResult() {
        return lastResult;
    }

    /**
     * Groups raw inspection rows by camis and maps each group to a Restaurant
     * with an aggregated grades list.
     */
    List<Restaurant> mapToRestaurants(List<NycApiRestaurantDto> rows) {
        // Preserve insertion order (first row encountered wins for restaurant metadata)
        Map<String, List<NycApiRestaurantDto>> byId = new LinkedHashMap<>();
        for (NycApiRestaurantDto row : rows) {
            if (row.getCamis() == null || row.getCamis().isEmpty()) continue;
            byId.computeIfAbsent(row.getCamis(), k -> new ArrayList<>()).add(row);
        }

        List<Restaurant> result = new ArrayList<>(byId.size());
        for (Map.Entry<String, List<NycApiRestaurantDto>> entry : byId.entrySet()) {
            NycApiRestaurantDto first = entry.getValue().get(0);
            // Skip incomplete records (no name or no cuisine — placeholder inspections)
            if (first.getDba() == null || first.getDba().isEmpty()
                    || first.getCuisineDescription() == null || first.getCuisineDescription().isEmpty()) {
                continue;
            }
            result.add(buildRestaurant(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    private Restaurant buildRestaurant(String camis, List<NycApiRestaurantDto> rows) {
        if (rows.isEmpty()) throw new IllegalArgumentException("rows must not be empty for camis=" + camis);
        NycApiRestaurantDto first = rows.get(0);

        Restaurant r = new Restaurant();
        r.setRestaurantId(camis);
        r.setName(first.getDba());
        r.setCuisine(first.getCuisineDescription());
        r.setBorough(normalizeBoro(first.getBoro()));

        Address addr = new Address();
        addr.setBuilding(first.getBuilding());
        addr.setStreet(first.getStreet());
        addr.setZipcode(first.getZipcode());

        Double lat = parseDouble(first.getLatitude());
        Double lon = parseDouble(first.getLongitude());
        if (lat != null && lon != null) {
            addr.setCoord(Arrays.asList(lon, lat)); // GeoJSON: [lng, lat]
        }

        r.setPhone(first.getPhone());
        r.setAddress(addr);

        // One Grade per inspection date — rows share the same date for multiple violations
        List<Grade> grades = new ArrayList<>();
        Set<String> seenDates = new HashSet<>();
        for (NycApiRestaurantDto row : rows) {
            if (row.getInspectionDate() == null) continue;
            if (!seenDates.add(row.getInspectionDate())) continue;
            Grade grade = new Grade();
            grade.setDate(row.getInspectionDate());
            grade.setGrade(row.getGrade());
            grade.setScore(parseInteger(row.getScore()));
            grade.setInspectionType(row.getInspectionType());
            grade.setAction(row.getAction());
            grade.setViolationCode(row.getViolationCode());
            grade.setViolationDescription(row.getViolationDescription());
            grade.setCriticalFlag(row.getCriticalFlag());
            grades.add(grade);
        }
        r.setGrades(grades);

        return r;
    }

    private String normalizeBoro(String boro) {
        if (boro == null) return null;
        // API returns uppercase "MANHATTAN" — normalize to title case
        switch (boro.toUpperCase()) {
            case "MANHATTAN":   return "Manhattan";
            case "BROOKLYN":    return "Brooklyn";
            case "QUEENS":      return "Queens";
            case "BRONX":       return "Bronx";
            case "STATEN ISLAND": return "Staten Island";
            default:            return boro;
        }
    }

    private Double parseDouble(String value) {
        if (value == null || value.isEmpty()) return null;
        try { return Double.parseDouble(value); }
        catch (NumberFormatException e) { return null; }
    }

    private Integer parseInteger(String value) {
        if (value == null || value.isEmpty()) return null;
        try { return Integer.parseInt(value); }
        catch (NumberFormatException e) { return null; }
    }
}
