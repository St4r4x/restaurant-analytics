package com.aflokkat.repository;

import com.aflokkat.entity.InspectionReportEntity;
import com.aflokkat.entity.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReportRepository extends JpaRepository<InspectionReportEntity, Long> {
    List<InspectionReportEntity> findByUserId(Long userId);
    List<InspectionReportEntity> findByUserIdAndStatus(Long userId, Status status);
    long countByUserId(Long userId);

    /**
     * Count inspection reports grouped by status across ALL controllers.
     * Returns List of Object[] where [0] is Status enum value and [1] is Long count.
     * ADM-03: no userId filter — aggregate across all controllers intentionally.
     */
    @Query("SELECT r.status, COUNT(r) FROM InspectionReportEntity r GROUP BY r.status")
    List<Object[]> countGroupByStatus();

    /**
     * Count inspection reports grouped by grade across ALL controllers.
     * Returns List of Object[] where [0] is Grade enum value and [1] is Long count.
     * ADM-03: no userId filter — aggregate across all controllers intentionally.
     */
    @Query("SELECT r.grade, COUNT(r) FROM InspectionReportEntity r GROUP BY r.grade")
    List<Object[]> countGroupByGrade();
}
