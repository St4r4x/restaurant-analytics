package com.aflokkat.domain;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.bson.codecs.pojo.annotations.BsonProperty;

public class Address {

    @BsonProperty("building")
    private String building;

    @BsonProperty("street")
    private String street;

    @BsonProperty("zipcode")
    private String zipcode;

    @BsonProperty("coord")
    private List<Double> coord;

    public Address() {}

    public String getBuilding() { return building; }
    public void setBuilding(String building) { this.building = building; }

    public String getStreet() { return street; }
    public void setStreet(String street) { this.street = street; }

    public String getZipcode() { return zipcode; }
    public void setZipcode(String zipcode) { this.zipcode = zipcode; }

    public List<Double> getCoord() { return coord; }
    public void setCoord(List<Double> coord) { this.coord = coord; }

    @Override
    public String toString() {
        return Stream.of(building, street, zipcode)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" "));
    }
}
