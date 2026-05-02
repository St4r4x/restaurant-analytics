package com.st4r4x.dao;

import java.util.List;
import org.bson.Document;

public interface AnalyticsDAO {
    List<Document> findMapPoints();
    List<Document> findBoroughGradeDistribution();
    long countAtRiskRestaurants();
}
