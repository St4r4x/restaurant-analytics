package com.st4r4x.dao;

import java.util.List;
import org.bson.Document;
import com.st4r4x.aggregation.CuisineScore;
import com.st4r4x.domain.Restaurant;
import com.st4r4x.dto.AtRiskEntry;
import com.st4r4x.dto.UncontrolledEntry;

public interface AnalyticsDAO {
    long countAll();
    List<Document> findMapPoints();
    List<Document> findBoroughGradeDistribution();
    long countAtRiskRestaurants();

    List<CuisineScore> findWorstCuisinesByAverageScore(int limit);
    List<CuisineScore> findBestCuisinesByAverageScore(int limit);
    List<AtRiskEntry> findAtRiskRestaurants(String borough, int limit);
    List<UncontrolledEntry> findUncontrolled(String borough, int limit);
    List<Restaurant> searchByNameOrAddress(String q, int limit);
}
