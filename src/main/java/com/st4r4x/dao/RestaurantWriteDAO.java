package com.st4r4x.dao;

import java.util.List;
import com.st4r4x.domain.Restaurant;

public interface RestaurantWriteDAO {
    int upsertRestaurants(List<Restaurant> restaurants);
}
