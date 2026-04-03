package com.aflokkat.controller;

import com.aflokkat.service.RestaurantService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

/**
 * Placeholder for AnalyticsController — created in Wave 0 (Plan 06-01) so that
 * AnalyticsControllerTest compiles. Endpoints are added in Plan 06-02.
 */
@RestController
public class AnalyticsController {

    @Autowired
    private RestaurantService restaurantService;
}
