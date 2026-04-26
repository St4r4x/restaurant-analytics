package com.st4r4x.aggregation;

import org.bson.codecs.pojo.annotations.BsonProperty;

public class AggregationCount {
    @BsonProperty("_id")
    private String id;
    
    @BsonProperty("count")
    private Integer count;
    
    public AggregationCount() {}
    
    public AggregationCount(String id, Integer count) {
        this.id = id;
        this.count = count;
    }
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public Integer getCount() {
        return count;
    }
    
    public void setCount(Integer count) {
        this.count = count;
    }
    
    @Override
    public String toString() {
        return String.format("%-20s : %5d restaurants", id, count);
    }
}
