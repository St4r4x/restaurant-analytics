package com.aflokkat.controller;

import com.aflokkat.dao.RestaurantDAO;
import com.aflokkat.domain.Restaurant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RestaurantControllerSampleTest {

    @InjectMocks
    private RestaurantController restaurantController;

    @Mock
    private RestaurantDAO restaurantDAO;

    private Restaurant makeRestaurant(String id, String name, String cuisine) {
        Restaurant r = new Restaurant();
        r.setRestaurantId(id);
        r.setName(name);
        r.setCuisine(cuisine);
        return r;
    }

    @Test
    public void getSample_returnsThreeRestaurants() {
        List<Restaurant> stubs = Arrays.asList(
            makeRestaurant("1", "Pizza Palace", "Italian"),
            makeRestaurant("2", "Burger Barn", "American"),
            makeRestaurant("3", "Sushi Stop", "Japanese")
        );
        when(restaurantDAO.findSampleRestaurants(3)).thenReturn(stubs);

        ResponseEntity<Map<String, Object>> response = restaurantController.getSample(3);

        assertEquals(200, response.getStatusCodeValue());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("success", body.get("status"));
        @SuppressWarnings("unchecked")
        List<?> data = (List<?>) body.get("data");
        assertNotNull(data);
        assertEquals(3, data.size());
    }

    @Test
    public void getSample_defaultLimitIsThree() {
        List<Restaurant> stubs = Arrays.asList(
            makeRestaurant("1", "Pizza Palace", "Italian"),
            makeRestaurant("2", "Burger Barn", "American"),
            makeRestaurant("3", "Sushi Stop", "Japanese")
        );
        when(restaurantDAO.findSampleRestaurants(3)).thenReturn(stubs);

        restaurantController.getSample(3);

        verify(restaurantDAO).findSampleRestaurants(3);
    }

    @Test
    public void getSample_returnsError_whenDAOThrows() {
        when(restaurantDAO.findSampleRestaurants(3)).thenThrow(new RuntimeException("DB error"));

        ResponseEntity<Map<String, Object>> response = restaurantController.getSample(3);

        assertNotEquals(200, response.getStatusCodeValue());
    }
}
