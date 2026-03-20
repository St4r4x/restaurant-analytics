package com.aflokkat.config;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

import org.junit.Test;

import com.mongodb.client.MongoClient;

/**
 * Tests unitaires pour MongoClientFactory (Singleton Pattern)
 */
public class MongoClientFactoryTest {
    
    @Test
    public void testGetInstance_ReturnsMongoClient() {
        MongoClient client = MongoClientFactory.getInstance();
        assertNotNull("MongoClient should not be null", client);
    }
    
    @Test
    public void testGetInstance_SingletonPattern() {
        // Get two instances
        MongoClient client1 = MongoClientFactory.getInstance();
        MongoClient client2 = MongoClientFactory.getInstance();
        
        // They should be the same object (singleton)
        assertSame("Both instances should be the same object", client1, client2);
    }
    
    @Test
    public void testGetInstance_MultipleCallsReturnSame() {
        MongoClient first = MongoClientFactory.getInstance();
        MongoClient second = MongoClientFactory.getInstance();
        MongoClient third = MongoClientFactory.getInstance();
        
        assertSame(first, second);
        assertSame(second, third);
    }
    
    @Test
    public void testFactoryPattern_ThreadSafe() throws InterruptedException {
        // Test thread safety
        final MongoClient[] clients = new MongoClient[10];
        Thread[] threads = new Thread[10];
        
        for (int i = 0; i < 10; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                clients[index] = MongoClientFactory.getInstance();
            });
            threads[i].start();
        }
        
        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }
        
        // All clients should be the same instance (singleton)
        for (int i = 1; i < clients.length; i++) {
            assertSame("All instances should be the same", clients[0], clients[i]);
        }
    }
}
