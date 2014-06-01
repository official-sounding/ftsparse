package com.officialsounding.boutparse.model;

import com.github.davidmoten.geo.LatLong;

import java.io.Serializable;

/**
 * Created by Peter on 5/31/2014.
 */
public class Team implements Serializable {

    private int id;
    private String name;
    private String website;
    private String teamType;
    private String locationName;
    private LatLong locationCoords;
    private String locationHash;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getWebsite() {
        return website;
    }

    public void setWebsite(String website) {
        this.website = website;
    }

    public String getTeamType() {
        return teamType;
    }

    public void setTeamType(String teamType) {
        this.teamType = teamType;
    }

    public String getLocationName() {
        return locationName;
    }

    public void setLocationName(String locationName) {
        this.locationName = locationName;
    }

    public LatLong getLocationCoords() {
        return locationCoords;
    }

    public void setLocationCoords(LatLong locationCoords) {
        this.locationCoords = locationCoords;
    }

    public String getLocationHash() {
        return locationHash;
    }

    public void setLocationHash(String locationHash) {
        this.locationHash = locationHash;
    }
}
