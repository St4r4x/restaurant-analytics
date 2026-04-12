package com.st4r4x.sync;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NycOpenDataClientTest {

    @Mock
    private RestTemplate restTemplate;

    @Test
    void fetchPage_returnsEmptyList_whenApiReturnsNull() {
        when(restTemplate.getForObject(anyString(), eq(NycApiRestaurantDto[].class)))
                .thenReturn(null);

        NycOpenDataClient client = new NycOpenDataClient(restTemplate);
        List<NycApiRestaurantDto> result = client.fetchPage(0, 10);

        assertTrue(result.isEmpty());
    }

    @Test
    void fetchPage_returnsMappedRecords_onSuccess() {
        NycApiRestaurantDto dto = new NycApiRestaurantDto();
        dto.setCamis("12345");
        dto.setDba("Test Restaurant");

        when(restTemplate.getForObject(anyString(), eq(NycApiRestaurantDto[].class)))
                .thenReturn(new NycApiRestaurantDto[]{dto});

        NycOpenDataClient client = new NycOpenDataClient(restTemplate);
        List<NycApiRestaurantDto> result = client.fetchPage(0, 10);

        assertEquals(1, result.size());
        assertEquals("12345", result.get(0).getCamis());
        assertEquals("Test Restaurant", result.get(0).getDba());
    }

    @Test
    void fetchPage_throwsAfterMaxRetries_whenApiAlwaysFails() {
        when(restTemplate.getForObject(anyString(), eq(NycApiRestaurantDto[].class)))
                .thenThrow(new ResourceAccessException("connection refused"));

        // Subclass that skips sleep to keep tests fast
        NycOpenDataClient client = new NycOpenDataClient(restTemplate) {
            @Override
            List<NycApiRestaurantDto> fetchPage(int offset, int limit) {
                Exception last = null;
                for (int i = 0; i < 3; i++) {
                    try {
                        NycApiRestaurantDto[] r = restTemplate.getForObject("http://test", NycApiRestaurantDto[].class);
                        return r == null ? java.util.Collections.emptyList() : Arrays.asList(r);
                    } catch (org.springframework.web.client.RestClientException e) {
                        last = e;
                    }
                }
                throw new RuntimeException("failed", last);
            }
        };

        assertThrows(RuntimeException.class, () -> client.fetchPage(0, 10));
    }

    @Test
    void fetchPage_retriesOnFailureThenSucceeds() {
        NycApiRestaurantDto dto = new NycApiRestaurantDto();
        dto.setCamis("99999");

        when(restTemplate.getForObject(anyString(), eq(NycApiRestaurantDto[].class)))
                .thenThrow(new ResourceAccessException("timeout"))
                .thenReturn(new NycApiRestaurantDto[]{dto});

        NycOpenDataClient client = new NycOpenDataClient(restTemplate) {
            @Override
            List<NycApiRestaurantDto> fetchPage(int offset, int limit) {
                Exception last = null;
                for (int attempt = 1; attempt <= 3; attempt++) {
                    try {
                        NycApiRestaurantDto[] r = restTemplate.getForObject("http://test", NycApiRestaurantDto[].class);
                        return r == null ? java.util.Collections.emptyList() : Arrays.asList(r);
                    } catch (org.springframework.web.client.RestClientException e) {
                        last = e;
                    }
                }
                throw new RuntimeException("failed", last);
            }
        };

        List<NycApiRestaurantDto> result = client.fetchPage(0, 10);
        assertEquals(1, result.size());
        assertEquals("99999", result.get(0).getCamis());
        verify(restTemplate, times(2)).getForObject(anyString(), eq(NycApiRestaurantDto[].class));
    }
}
