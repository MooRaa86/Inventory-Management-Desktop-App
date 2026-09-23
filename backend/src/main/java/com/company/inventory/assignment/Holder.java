package com.company.inventory.assignment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "holders")
public class Holder {

    public static final String TYPE_USER = "USER";
    public static final String TYPE_DEPARTMENT = "DEPARTMENT";
    public static final String TYPE_PLACE = "PLACE";
    public static final String TYPE_OTHER = "OTHER";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String type = TYPE_OTHER;

    @Column(nullable = false)
    private String contact = "";

    @Column(nullable = false)
    private String notes = "";

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}