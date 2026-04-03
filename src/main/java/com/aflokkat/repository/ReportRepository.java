package com.aflokkat.repository;

import com.aflokkat.entity.InspectionReportEntity;
import com.aflokkat.entity.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReportRepository extends JpaRepository<InspectionReportEntity, Long> {
    List<InspectionReportEntity> findByUserId(Long userId);
    List<InspectionReportEntity> findByUserIdAndStatus(Long userId, Status status);
    long countByUserId(Long userId);
}
