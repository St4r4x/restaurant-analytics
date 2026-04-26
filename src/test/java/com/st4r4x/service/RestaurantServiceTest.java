package com.st4r4x.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.st4r4x.dao.RestaurantDAO;
import com.st4r4x.domain.Address;
import com.st4r4x.domain.Grade;
import com.st4r4x.domain.Restaurant;

@ExtendWith(MockitoExtension.class)
class RestaurantServiceTest {

    @Mock
    private RestaurantDAO restaurantDAO;

    @InjectMocks
    private RestaurantService restaurantService;

    // ── getLatestGrade ────────────────────────────────────────────────────────

    @Test
    void getLatestGrade_returnsNull_whenNoGrades() {
        Restaurant r = new Restaurant();
        assertNull(RestaurantService.getLatestGrade(r));
    }

    @Test
    void getLatestGrade_returnsNull_whenGradesListIsEmpty() {
        Restaurant r = new Restaurant();
        r.setGrades(Collections.emptyList());
        assertNull(RestaurantService.getLatestGrade(r));
    }

    @Test
    void getLatestGrade_returnsSingleGrade() {
        Restaurant r = restaurantWithGrades(grade("A", 10, "2024-01-01T00:00:00.000"));
        assertEquals("A", RestaurantService.getLatestGrade(r));
    }

    @Test
    void getLatestGrade_returnsMostRecentByDate() {
        Restaurant r = restaurantWithGrades(
            grade("B", 25, "2023-06-01T00:00:00.000"),
            grade("A", 8,  "2024-01-01T00:00:00.000")
        );
        assertEquals("A", RestaurantService.getLatestGrade(r));
    }

    // ── getLatestScore ────────────────────────────────────────────────────────

    @Test
    void getLatestScore_returnsNull_whenNoGrades() {
        assertNull(RestaurantService.getLatestScore(new Restaurant()));
    }

    @Test
    void getLatestScore_returnsMostRecentScore() {
        Restaurant r = restaurantWithGrades(
            grade("B", 25, "2023-01-01T00:00:00.000"),
            grade("A", 8,  "2024-06-01T00:00:00.000")
        );
        assertEquals(8, RestaurantService.getLatestScore(r));
    }

    // ── getTrend ──────────────────────────────────────────────────────────────

    @Test
    void getTrend_returnsStable_whenFewerThanTwoGrades() {
        Restaurant r = restaurantWithGrades(grade("A", 10, "2024-01-01T00:00:00.000"));
        assertEquals("stable", RestaurantService.getTrend(r));
    }

    @Test
    void getTrend_returnsImproving_whenScoreDropsByMoreThan5() {
        // Lower score = better (fewer violations)
        Restaurant r = restaurantWithGrades(
            grade("B", 30, "2023-01-01T00:00:00.000"), // older
            grade("A", 10, "2024-01-01T00:00:00.000")  // recent, much better
        );
        assertEquals("improving", RestaurantService.getTrend(r));
    }

    @Test
    void getTrend_returnsWorsening_whenScoreRisesByMoreThan5() {
        Restaurant r = restaurantWithGrades(
            grade("A", 5,  "2023-01-01T00:00:00.000"), // older
            grade("C", 35, "2024-01-01T00:00:00.000")  // recent, much worse
        );
        assertEquals("worsening", RestaurantService.getTrend(r));
    }

    @Test
    void getTrend_returnsStable_whenScoreDifferenceIsSmall() {
        Restaurant r = restaurantWithGrades(
            grade("A", 10, "2023-01-01T00:00:00.000"),
            grade("A", 12, "2024-01-01T00:00:00.000")
        );
        assertEquals("stable", RestaurantService.getTrend(r));
    }

    // ── getBadgeColor ─────────────────────────────────────────────────────────

    @Test
    void getBadgeColor_returnsGreen_forGradeA() {
        assertEquals("green", RestaurantService.getBadgeColor(restaurantWithGrades(grade("A", 5, "2024-01-01T00:00:00.000"))));
    }

    @Test
    void getBadgeColor_returnsYellow_forGradeB() {
        assertEquals("yellow", RestaurantService.getBadgeColor(restaurantWithGrades(grade("B", 20, "2024-01-01T00:00:00.000"))));
    }

    @Test
    void getBadgeColor_returnsOrange_forGradeC() {
        assertEquals("orange", RestaurantService.getBadgeColor(restaurantWithGrades(grade("C", 35, "2024-01-01T00:00:00.000"))));
    }

    @Test
    void getBadgeColor_returnsRed_forGradeZ() {
        assertEquals("red", RestaurantService.getBadgeColor(restaurantWithGrades(grade("Z", 50, "2024-01-01T00:00:00.000"))));
    }

    @Test
    void getBadgeColor_returnsRed_whenNoGrades() {
        assertEquals("red", RestaurantService.getBadgeColor(new Restaurant()));
    }

    // ── getLatitude / getLongitude ────────────────────────────────────────────

    @Test
    void getLatitude_returnsNull_whenAddressIsNull() {
        assertNull(RestaurantService.getLatitude(new Restaurant()));
    }

    @Test
    void getLatitude_returnsNull_whenCoordIsNull() {
        Restaurant r = new Restaurant();
        r.setAddress(new Address());
        assertNull(RestaurantService.getLatitude(r));
    }

    @Test
    void getLatitude_returnsSecondElement_fromGeoJsonCoord() {
        Restaurant r = restaurantWithCoord(-74.006, 40.7128);
        assertEquals(40.7128, RestaurantService.getLatitude(r), 0.0001);
    }

    @Test
    void getLongitude_returnsFirstElement_fromGeoJsonCoord() {
        Restaurant r = restaurantWithCoord(-74.006, 40.7128);
        assertEquals(-74.006, RestaurantService.getLongitude(r), 0.0001);
    }

    // ── toView ────────────────────────────────────────────────────────────────

    @Test
    void toView_containsAllExpectedKeys() {
        Restaurant r = new Restaurant("Le Bernardin", "French", "Manhattan");
        r.setRestaurantId("12345");
        r.setGrades(Collections.singletonList(grade("A", 8, "2024-01-01T00:00:00.000")));

        Map<String, Object> view = RestaurantService.toView(r);

        assertTrue(view.containsKey("restaurantId"));
        assertTrue(view.containsKey("name"));
        assertTrue(view.containsKey("cuisine"));
        assertTrue(view.containsKey("borough"));
        assertTrue(view.containsKey("grades"));
        assertTrue(view.containsKey("latestGrade"));
        assertTrue(view.containsKey("latestScore"));
        assertTrue(view.containsKey("trend"));
        assertTrue(view.containsKey("badgeColor"));
        assertTrue(view.containsKey("latitude"));
        assertTrue(view.containsKey("longitude"));
    }

    @Test
    void toView_computedFieldsMatchServiceMethods() {
        Restaurant r = restaurantWithGrades(
            grade("B", 25, "2023-01-01T00:00:00.000"),
            grade("A", 8,  "2024-01-01T00:00:00.000")
        );

        Map<String, Object> view = RestaurantService.toView(r);

        assertEquals(RestaurantService.getLatestGrade(r), view.get("latestGrade"));
        assertEquals(RestaurantService.getLatestScore(r), view.get("latestScore"));
        assertEquals(RestaurantService.getTrend(r),       view.get("trend"));
        assertEquals(RestaurantService.getBadgeColor(r),  view.get("badgeColor"));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static Grade grade(String letter, int score, String date) {
        Grade g = new Grade();
        g.setGrade(letter);
        g.setScore(score);
        g.setDate(date);
        return g;
    }

    private static Restaurant restaurantWithGrades(Grade... grades) {
        Restaurant r = new Restaurant();
        r.setGrades(Arrays.asList(grades));
        return r;
    }

    private static Restaurant restaurantWithCoord(double lon, double lat) {
        Address address = new Address();
        address.setCoord(Arrays.asList(lon, lat));
        Restaurant r = new Restaurant();
        r.setAddress(address);
        return r;
    }
}
