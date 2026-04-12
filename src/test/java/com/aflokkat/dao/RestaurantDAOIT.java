package com.aflokkat.dao;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.bson.Document;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.testcontainers.containers.MongoDBContainer;

import com.mongodb.client.MongoClients;
import com.aflokkat.aggregation.AggregationCount;
import com.aflokkat.aggregation.BoroughCuisineScore;
import com.aflokkat.aggregation.CuisineScore;
import com.aflokkat.config.MongoClientFactory;
import com.aflokkat.domain.Restaurant;

/**
 * Integration tests for RestaurantDAO — uses Testcontainers (mongo:7.0).
 * No live MongoDB required. Run with: mvn failsafe:integration-test -Dit.test=RestaurantDAOIT
 */
public class RestaurantDAOIT {

    @ClassRule
    public static MongoDBContainer mongoContainer =
        new MongoDBContainer("mongo:7.0");

    private static RestaurantDAO restaurantDAO;

    @BeforeClass
    public static void setUpClass() {
        // Reset singleton — guards against state leakage if any earlier test
        // left MongoClientFactory pointing at a different URI.
        MongoClientFactory.closeInstance();

        // Inject TC URI as JVM system property (dotted key) — AppConfig.getProperty()
        // checks System.getProperty("mongodb.uri") as tier-0 (added in Plan 01).
        System.setProperty("mongodb.uri", mongoContainer.getConnectionString());

        // Construct DAO after property is set — MongoClientFactory.getInstance()
        // will now create a MongoClient against the TC container URI.
        restaurantDAO = new RestaurantDAOImpl();

        // Seed test data via a raw MongoClient independent of the DAO under test.
        seedTestData();
    }

    @AfterClass
    public static void tearDownClass() {
        if (restaurantDAO != null) {
            restaurantDAO.close(); // closes MongoClientFactory singleton
        }
        System.clearProperty("mongodb.uri");
    }

    // -----------------------------------------------------------------------
    // Seeding
    // -----------------------------------------------------------------------

    /**
     * Inserts 60 restaurant documents covering all 5 NYC boroughs and 4 cuisines.
     * Layout:
     *   Italian:  Manhattan=5, Brooklyn=4, Queens=3, Bronx=3, Staten Island=2  -> 17
     *   Chinese:  Manhattan=5, Brooklyn=4, Queens=4, Bronx=2, Staten Island=1  -> 16
     *   American: Manhattan=5, Brooklyn=4, Queens=3, Bronx=3, Staten Island=1  -> 16
     *   French:   Manhattan=4, Brooklyn=3, Queens=2, Bronx=1, Staten Island=1  -> 11
     *   Total: 60
     *
     * Thresholds adapted per D-03:
     *   findCuisinesWithMinimumCount(10)  -> Italian(17), Chinese(16), American(16), French(11) all qualify
     *   findCuisinesWithMinimumCount(20)  -> none qualifies -> empty list (valid: asserts non-null)
     */
    private static void seedTestData() {
        try (var client = MongoClients.create(mongoContainer.getConnectionString())) {
            var col = client.getDatabase("newyork").getCollection("restaurants");
            col.drop();
            col.insertMany(buildSeedDocuments());
        }
    }

    private static List<Document> buildSeedDocuments() {
        List<Document> docs = new ArrayList<>();
        int id = 1;

        // Each call appends (count) docs for (cuisine, borough) with varying scores
        id = addDocs(docs, id, "Italian",  "Manhattan",    5, 8,  14);
        id = addDocs(docs, id, "Italian",  "Brooklyn",     4, 9,  15);
        id = addDocs(docs, id, "Italian",  "Queens",       3, 7,  13);
        id = addDocs(docs, id, "Italian",  "Bronx",        3, 10, 16);
        id = addDocs(docs, id, "Italian",  "Staten Island",2, 6,  12);

        id = addDocs(docs, id, "Chinese",  "Manhattan",    5, 11, 20);
        id = addDocs(docs, id, "Chinese",  "Brooklyn",     4, 12, 18);
        id = addDocs(docs, id, "Chinese",  "Queens",       4, 13, 22);
        id = addDocs(docs, id, "Chinese",  "Bronx",        2, 9,  17);
        id = addDocs(docs, id, "Chinese",  "Staten Island",1, 8,  14);

        id = addDocs(docs, id, "American", "Manhattan",    5, 5,  10);
        id = addDocs(docs, id, "American", "Brooklyn",     4, 6,  11);
        id = addDocs(docs, id, "American", "Queens",       3, 7,  12);
        id = addDocs(docs, id, "American", "Bronx",        3, 4,  9);
        id = addDocs(docs, id, "American", "Staten Island",1, 5,  10);

        id = addDocs(docs, id, "French",   "Manhattan",    4, 3,  8);
        id = addDocs(docs, id, "French",   "Brooklyn",     3, 4,  9);
        id = addDocs(docs, id, "French",   "Queens",       2, 5,  10);
        id = addDocs(docs, id, "French",   "Bronx",        1, 6,  11);
        id = addDocs(docs, id, "French",   "Staten Island",1, 7,  12);

        return docs;
    }

    /**
     * Creates (count) restaurant documents for a given (cuisine, borough) pair.
     * Each document has one grade with a score that alternates between minScore and maxScore.
     * The "address" field is stored as an embedded Document (type=3) — required by findAll().
     */
    private static int addDocs(List<Document> out, int startId, String cuisine, String borough,
                                int count, int minScore, int maxScore) {
        for (int i = 0; i < count; i++) {
            int score = (i % 2 == 0) ? minScore : maxScore;
            String grade = (score <= 13) ? "A" : (score <= 17) ? "B" : "C";
            out.add(new Document("restaurant_id", "R" + String.format("%04d", startId + i))
                .append("name", cuisine + " Place " + (startId + i))
                .append("cuisine", cuisine)
                .append("borough", borough)
                .append("address", new Document("building", "1")
                    .append("street", borough + " Ave")
                    .append("zipcode", "10001")
                    .append("coord", Arrays.asList(-73.9857, 40.7484)))
                .append("grades", Arrays.asList(
                    new Document("date", "2024-01-15")
                        .append("grade", grade)
                        .append("score", score)
                )));
        }
        return startId + count;
    }

    // -----------------------------------------------------------------------
    // USE CASE 1: Restaurant count by borough
    // -----------------------------------------------------------------------

    @Test
    public void testUseCase1_GetRestaurantCountByBorough_ReturnsData() {
        List<AggregationCount> results = restaurantDAO.findCountByBorough();
        assertNotNull("Results should not be null", results);
        assertFalse("Results should not be empty", results.isEmpty());
        assertTrue("Should have at least 5 boroughs", results.size() >= 5);
    }

    @Test
    public void testUseCase1_CountByBorough_DataValidation() {
        List<AggregationCount> results = restaurantDAO.findCountByBorough();
        for (AggregationCount count : results) {
            assertNotNull("Borough ID should not be null", count.getId());
            assertTrue("Count should be positive", count.getCount() > 0);
        }
    }

    @Test
    public void testUseCase1_CountByBorough_SortedDescending() {
        List<AggregationCount> results = restaurantDAO.findCountByBorough();
        for (int i = 1; i < results.size(); i++) {
            assertTrue("Should be sorted descending",
                results.get(i - 1).getCount() >= results.get(i).getCount());
        }
    }

    // -----------------------------------------------------------------------
    // USE CASE 2: Average score by cuisine and borough
    // -----------------------------------------------------------------------

    @Test
    public void testUseCase2_GetAverageScoreByCuisineAndBorough_Italian() {
        List<BoroughCuisineScore> results = restaurantDAO.findAverageScoreByCuisineAndBorough("Italian");
        assertNotNull("Results should not be null", results);
        assertFalse("Results should not be empty", results.isEmpty());
    }

    @Test
    public void testUseCase2_AverageScore_ValidData() {
        List<BoroughCuisineScore> results = restaurantDAO.findAverageScoreByCuisineAndBorough("Italian");
        for (BoroughCuisineScore score : results) {
            assertNotNull("Borough should not be null", score.getBorough());
            assertTrue("Average score should be positive", score.getAvgScore() > 0);
        }
    }

    @Test
    public void testUseCase2_AverageScore_InvalidCuisine() {
        List<BoroughCuisineScore> results =
            restaurantDAO.findAverageScoreByCuisineAndBorough("NonExistentCuisine12345");
        assertNotNull("Results should not be null (empty list expected)", results);
    }

    // -----------------------------------------------------------------------
    // USE CASE 3: Worst cuisines by borough
    // -----------------------------------------------------------------------

    @Test
    public void testUseCase3_GetWorstCuisines_Manhattan() {
        List<CuisineScore> results =
            restaurantDAO.findWorstCuisinesByAverageScoreInBorough("Manhattan", 3);
        assertNotNull("Results should not be null", results);
        assertTrue("Should return at most 3 worst cuisines", results.size() <= 3);
    }

    @Test
    public void testUseCase3_WorstCuisines_ValidData() {
        List<CuisineScore> results =
            restaurantDAO.findWorstCuisinesByAverageScoreInBorough("Manhattan", 5);
        for (CuisineScore score : results) {
            assertNotNull("Cuisine should not be null", score.getCuisine());
            assertTrue("Average score should be positive", score.getAvgScore() > 0);
            assertTrue("Count should be positive", score.getCount() > 0);
        }
    }

    @Test
    public void testUseCase3_WorstCuisines_SortedByScore() {
        List<CuisineScore> results =
            restaurantDAO.findWorstCuisinesByAverageScoreInBorough("Manhattan", 5);
        for (int i = 1; i < results.size(); i++) {
            assertTrue("Should be sorted ascending (worst = highest score first — per DAO semantics)",
                results.get(i - 1).getAvgScore() <= results.get(i).getAvgScore());
        }
    }

    // -----------------------------------------------------------------------
    // USE CASE 4: Cuisines with minimum count
    // Thresholds adapted to seeded volume (D-03):
    //   Italian=17, Chinese=16, American=16, French=11
    //   threshold=10 -> all 4 qualify
    //   threshold=20 -> none qualify -> empty list (asserts non-null)
    // -----------------------------------------------------------------------

    @Test
    public void testUseCase4_GetCuisinesWithMinimumCount_10() {
        // Changed from 500 (live DB) to 10 (TC seeded volume, per D-03)
        List<String> results = restaurantDAO.findCuisinesWithMinimumCount(10);
        assertNotNull("Results should not be null", results);
        assertFalse("Should have cuisines with >=10 restaurants (Italian=17, Chinese=16, American=16, French=11)",
            results.isEmpty());
    }

    @Test
    public void testUseCase4_CuisinesWithMinCount_Alphabetical() {
        List<String> results = restaurantDAO.findCuisinesWithMinimumCount(10);
        for (int i = 1; i < results.size(); i++) {
            assertTrue("Should be sorted alphabetically",
                results.get(i - 1).compareTo(results.get(i)) <= 0);
        }
    }

    @Test
    public void testUseCase4_CuisinesWithHighMinCount() {
        // Changed from 1000 (live DB) to 20 (no cuisine reaches 20 in seeded data)
        List<String> results = restaurantDAO.findCuisinesWithMinimumCount(20);
        assertNotNull("Results should not be null (empty list expected)", results);
    }

    // -----------------------------------------------------------------------
    // Generic query tests
    // -----------------------------------------------------------------------

    @Test
    public void testCountAll_ReturnsPositiveNumber() {
        long count = restaurantDAO.countAll();
        assertTrue("Total count should be positive (60 seeded)", count > 0);
    }

    @Test
    public void testCountByCuisine_Italian() {
        long count = restaurantDAO.countByCuisine("Italian");
        assertTrue("Italian restaurants count should be positive (17 seeded)", count > 0);
    }

    @Test
    public void testFindByCuisine_Italian() {
        List<Restaurant> results = restaurantDAO.findByCuisine("Italian", 10);
        assertNotNull("Results should not be null", results);
        assertFalse("Should find Italian restaurants (17 seeded)", results.isEmpty());
    }
}
