package com.aflokkat.controller;

import com.aflokkat.dao.RestaurantDAO;
import com.aflokkat.repository.ReportRepository;
import com.aflokkat.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

@ExtendWith(MockitoExtension.class)
class ReportControllerTest {

    @Mock private ReportRepository reportRepository;
    @Mock private RestaurantDAO restaurantDAO;
    @Mock private UserRepository userRepository;

    // ── CTRL-01: create report ─────────────────────────────────────────────
    @Test void createReport_returns201WithEnrichedData_onValidRequest() { assumeTrue(false, "not implemented"); }
    @Test void createReport_returns400_whenRestaurantIdMissing() { assumeTrue(false, "not implemented"); }
    @Test void createReport_enrichesWithNullFields_whenRestaurantNotInMongo() { assumeTrue(false, "not implemented"); }

    // ── CTRL-02: list reports ──────────────────────────────────────────────
    @Test void listReports_returnsOnlyCallerReports_notOtherControllers() { assumeTrue(false, "not implemented"); }
    @Test void listReports_filtersByStatus_whenStatusParamPresent() { assumeTrue(false, "not implemented"); }
    @Test void listReports_returnsAll_whenStatusParamAbsent() { assumeTrue(false, "not implemented"); }

    // ── CTRL-03: patch report ──────────────────────────────────────────────
    @Test void patchReport_updatesOwnedReport_andReturns200() { assumeTrue(false, "not implemented"); }
    @Test void patchReport_returns403_whenNotOwner() { assumeTrue(false, "not implemented"); }
    @Test void patchReport_appliesOnlyNonNullFields_leavingOthersUnchanged() { assumeTrue(false, "not implemented"); }

    // ── CTRL-04: photo upload ──────────────────────────────────────────────
    @Test void photoUpload_savesFileAndUpdatesPhotoPath() { assumeTrue(false, "not implemented"); }
    @Test void photoUpload_returns404_whenReportNotFound() { assumeTrue(false, "not implemented"); }
    @Test void getPhoto_streamsFileWithCorrectContentType() { assumeTrue(false, "not implemented"); }
}
