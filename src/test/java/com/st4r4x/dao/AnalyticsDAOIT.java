package com.st4r4x.dao;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MongoDBContainer;

import com.mongodb.client.MongoClients;
import com.st4r4x.aggregation.CuisineScore;
import com.st4r4x.config.MongoClientFactory;
import com.st4r4x.domain.Restaurant;
import com.st4r4x.dto.AtRiskEntry;
import com.st4r4x.dto.UncontrolledEntry;

/**
 * Integration tests for AnalyticsDAO — uses Testcontainers (mongo:7.0).
 * No live MongoDB required. Run with: mvn failsafe:integration-test -Dit.test=AnalyticsDAOIT
 */
public class AnalyticsDAOIT {

    public static MongoDBContainer mongoContainer = new MongoDBContainer("mongo:7.0");

    private static AnalyticsDAO analyticsDAO;

    @BeforeAll
    public static void setUpClass() {
        mongoContainer.start();

        MongoClientFactory.closeInstance();
        System.setProperty("mongodb.uri", mongoContainer.getConnectionString());

        seedTestData();

        analyticsDAO = new AnalyticsDAOImpl();
    }

    @AfterAll
    public static void tearDownClass() {
        MongoClientFactory.closeInstance();
        System.clearProperty("mongodb.uri");
        mongoContainer.stop();
    }

    /**
     * Seeds 10 restaurants across boroughs/cuisines with grades, scores, coordinates
     * and inspection dates covering every AnalyticsDAO code path:
     *   - grade A/B (fresh, in good standing)
     *   - grade C/Z (at-risk / uncontrolled)
     *   - one restaurant inspected > 12 months ago (uncontrolled via staleness, not grade)
     *   - one restaurant with no address.coord (excluded from findMapPoints)
     */
    private static void seedTestData() {
        try (var client = MongoClients.create(mongoContainer.getConnectionString())) {
            var db = client.getDatabase("newyork");
            var col = db.getCollection("restaurants");
            col.drop();
            col.createIndex(new Document("name", "text").append("address.street", "text"));
            col.insertMany(buildSeedDocuments());
        }
    }

    private static List<Document> buildSeedDocuments() {
        List<Document> docs = new ArrayList<>();

        docs.add(restaurant("R0001", "Pizza Palace", "Italian", "Manhattan",
            "A", 8, "2026-06-01", coord(-73.98, 40.75)));
        docs.add(restaurant("R0002", "Sushi Spot", "Japanese", "Brooklyn",
            "A", 10, "2026-06-01", coord(-73.95, 40.68)));
        docs.add(restaurant("R0003", "Taco Town", "Mexican", "Queens",
            "B", 15, "2026-05-01", coord(-73.90, 40.74)));
        docs.add(restaurant("R0004", "Burger Barn", "American", "Bronx",
            "B", 16, "2026-05-01", coord(-73.87, 40.85)));
        // At-risk: last grade C
        docs.add(restaurant("R0005", "Greasy Spoon", "American", "Manhattan",
            "C", 30, "2026-04-01", coord(-73.99, 40.76)));
        // At-risk: last grade Z
        docs.add(restaurant("R0006", "Sketchy Diner", "American", "Brooklyn",
            "Z", 35, "2026-03-01", coord(-73.94, 40.69)));
        // Uncontrolled via staleness only (grade A, but inspected 2 years ago)
        docs.add(restaurant("R0007", "Forgotten Cafe", "French", "Queens",
            "A", 9, "2024-01-01", coord(-73.91, 40.73)));
        docs.add(restaurant("R0008", "Noodle House", "Chinese", "Staten Island",
            "A", 7, "2026-06-15", coord(-74.15, 40.58)));
        docs.add(restaurant("R0009", "Kebab King", "Turkish", "Manhattan",
            "B", 14, "2026-05-20", coord(-73.97, 40.77)));
        // No address.coord — must be excluded from findMapPoints
        Document noCoord = restaurant("R0010", "No Location Cafe", "American", "Bronx",
            "A", 8, "2026-06-01", null);
        docs.add(noCoord);

        return docs;
    }

    private static List<Double> coord(double lng, double lat) {
        return Arrays.asList(lng, lat);
    }

    private static Document restaurant(String id, String name, String cuisine, String borough,
                                        String grade, int score, String date, List<Double> coord) {
        Document address = new Document("building", "1")
            .append("street", borough + " Ave")
            .append("zipcode", "10001");
        if (coord != null) {
            address.append("coord", coord);
        }
        return new Document("restaurant_id", id)
            .append("name", name)
            .append("cuisine", cuisine)
            .append("borough", borough)
            .append("address", address)
            .append("grades", Arrays.asList(
                new Document("date", date).append("grade", grade).append("score", score)
            ));
    }

    // -----------------------------------------------------------------------
    // countAll
    // -----------------------------------------------------------------------

    @Test
    public void testCountAll_ReturnsSeededCount() {
        assertEquals(10, analyticsDAO.countAll());
    }

    // -----------------------------------------------------------------------
    // findMapPoints
    // -----------------------------------------------------------------------

    @Test
    public void testFindMapPoints_ExcludesRestaurantsWithoutCoord() {
        List<Document> points = analyticsDAO.findMapPoints();
        assertEquals(9, points.size(), "Restaurant without address.coord must be excluded");
        assertTrue(points.stream().noneMatch(p -> "No Location Cafe".equals(p.getString("name"))));
    }

    // -----------------------------------------------------------------------
    // findBoroughGradeDistribution
    // -----------------------------------------------------------------------

    @Test
    public void testFindBoroughGradeDistribution_OnlyIncludesABC() {
        List<Document> results = analyticsDAO.findBoroughGradeDistribution();
        assertFalse(results.isEmpty());
        for (Document borough : results) {
            @SuppressWarnings("unchecked")
            List<Document> grades = (List<Document>) borough.get("grades");
            for (Document g : grades) {
                assertTrue(Arrays.asList("A", "B", "C").contains(g.getString("grade")),
                    "Z-graded restaurants must not appear in the A/B/C distribution");
            }
        }
    }

    // -----------------------------------------------------------------------
    // countAtRiskRestaurants
    // -----------------------------------------------------------------------

    @Test
    public void testCountAtRiskRestaurants_CountsOnlyLastGradeCOrZ() {
        // R0005 (C) and R0006 (Z) — exactly 2 seeded at-risk restaurants
        assertEquals(2, analyticsDAO.countAtRiskRestaurants());
    }

    // -----------------------------------------------------------------------
    // findWorstCuisinesByAverageScore / findBestCuisinesByAverageScore
    // -----------------------------------------------------------------------

    @Test
    public void testFindWorstCuisinesByAverageScore_SortedAscending() {
        List<CuisineScore> results = analyticsDAO.findWorstCuisinesByAverageScore(20);
        assertFalse(results.isEmpty());
        for (int i = 1; i < results.size(); i++) {
            assertTrue(results.get(i - 1).getAvgScore() <= results.get(i).getAvgScore(),
                "Should be sorted ascending (lowest score = cleanest first)");
        }
    }

    @Test
    public void testFindBestCuisinesByAverageScore_SortedDescending() {
        List<CuisineScore> results = analyticsDAO.findBestCuisinesByAverageScore(20);
        assertFalse(results.isEmpty());
        for (int i = 1; i < results.size(); i++) {
            assertTrue(results.get(i - 1).getAvgScore() >= results.get(i).getAvgScore(),
                "Should be sorted descending (highest score = worst first)");
        }
    }

    @Test
    public void testFindWorstCuisinesByAverageScore_RespectsLimit() {
        List<CuisineScore> results = analyticsDAO.findWorstCuisinesByAverageScore(2);
        assertTrue(results.size() <= 2);
    }

    // -----------------------------------------------------------------------
    // findAtRiskRestaurants
    // -----------------------------------------------------------------------

    @Test
    public void testFindAtRiskRestaurants_ReturnsOnlyCOrZGrades() {
        List<AtRiskEntry> results = analyticsDAO.findAtRiskRestaurants(null, 10);
        assertEquals(2, results.size());
        assertTrue(results.stream().allMatch(r -> Arrays.asList("C", "Z").contains(r.getLastGrade())));
    }

    @Test
    public void testFindAtRiskRestaurants_FiltersByBorough() {
        List<AtRiskEntry> results = analyticsDAO.findAtRiskRestaurants("Manhattan", 10);
        assertEquals(1, results.size());
        assertEquals("Manhattan", results.get(0).getBorough());
    }

    @Test
    public void testFindAtRiskRestaurants_SortedByScoreDescending() {
        List<AtRiskEntry> results = analyticsDAO.findAtRiskRestaurants(null, 10);
        for (int i = 1; i < results.size(); i++) {
            assertTrue(results.get(i - 1).getLastScore() >= results.get(i).getLastScore());
        }
    }

    // -----------------------------------------------------------------------
    // findUncontrolled
    // -----------------------------------------------------------------------

    @Test
    public void testFindUncontrolled_IncludesBadGradesAndStaleInspections() {
        List<UncontrolledEntry> results = analyticsDAO.findUncontrolled(null, 10);
        // R0005 (C), R0006 (Z), R0007 (stale > 12 months) — 3 expected
        assertEquals(3, results.size());
        assertTrue(results.stream().anyMatch(r -> "Forgotten Cafe".equals(r.getName())),
            "Restaurant not inspected in 12+ months must be flagged uncontrolled even with grade A");
    }

    // -----------------------------------------------------------------------
    // searchByNameOrAddress
    // -----------------------------------------------------------------------

    @Test
    public void testSearchByNameOrAddress_ShortQueryUsesPrefixRegex() {
        List<Restaurant> results = analyticsDAO.searchByNameOrAddress("Pi", 10);
        assertFalse(results.isEmpty());
        assertTrue(results.stream().anyMatch(r -> "Pizza Palace".equals(r.getName())));
    }

    @Test
    public void testSearchByNameOrAddress_LongQueryUsesTextIndex() {
        List<Restaurant> results = analyticsDAO.searchByNameOrAddress("Sushi Spot", 10);
        assertFalse(results.isEmpty());
        assertTrue(results.stream().anyMatch(r -> "Sushi Spot".equals(r.getName())));
    }

    @Test
    public void testSearchByNameOrAddress_NoMatchReturnsEmptyList() {
        List<Restaurant> results = analyticsDAO.searchByNameOrAddress("Nonexistent12345", 10);
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }
}
