package com.smartcampus.model;

import com.smartcampus.model.enums.ResourceStatus;
import com.smartcampus.model.enums.RoomType;

/**
 * A bookable room: classroom, seminar hall, auditorium or conference room.
 */
public class Classroom extends Resource {

    private String roomNumber;
    private String building;
    private int floor;
    private int capacity;
    private RoomType type;
    private String facilities;

    /** No-argument constructor (Jackson / JDBC row mapping). */
    public Classroom() {
        super();
        this.type = RoomType.CLASSROOM;
        this.floor = 1;
    }

    /** Constructor with only the identity fields. */
    public Classroom(Long id, String roomNumber) {
        super(id, ResourceStatus.ACTIVE);
        this.roomNumber = roomNumber;
        this.type = RoomType.CLASSROOM;
    }

    /** Full constructor. */
    public Classroom(Long id, String roomNumber, String building, int floor, int capacity,
                     RoomType type, String facilities, ResourceStatus status) {
        super(id, status);
        this.roomNumber = roomNumber;
        this.building = building;
        this.floor = floor;
        this.capacity = capacity;
        this.type = type;
        this.facilities = facilities;
    }

    @Override
    public String getResourceCode() {
        return roomNumber;
    }

    @Override
    public String getResourceLabel() {
        return roomNumber + " - " + building;
    }

    /** Polymorphic override: adds the seating capacity to the booking description. */
    @Override
    public String describeForBooking() {
        return super.describeForBooking() + " (capacity " + capacity + ")";
    }

    public String getRoomNumber() {
        return roomNumber;
    }

    public void setRoomNumber(String roomNumber) {
        this.roomNumber = roomNumber;
    }

    public String getBuilding() {
        return building;
    }

    public void setBuilding(String building) {
        this.building = building;
    }

    public int getFloor() {
        return floor;
    }

    public void setFloor(int floor) {
        this.floor = floor;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public RoomType getType() {
        return type;
    }

    public void setType(RoomType type) {
        this.type = type;
    }

    public String getFacilities() {
        return facilities;
    }

    public void setFacilities(String facilities) {
        this.facilities = facilities;
    }
}
