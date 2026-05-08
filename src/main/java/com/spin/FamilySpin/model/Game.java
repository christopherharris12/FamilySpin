package com.spin.FamilySpin.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "games")
public class Game {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name; // "Trivia Challenge", "Memory Match", etc.
    private int dayOfWeek; // 0=Sunday, 1=Monday, ... 6=Saturday
    private String gameType; // "TRIVIA", "MEMORY", "CHARADES", "DARE", "WORD_ASSOCIATION", "20_QUESTIONS"
    private int weekNumber; // Track which week this game belongs to
    private Instant createdAt;
    private Instant weekStartDate;

    public Game() {}

    public Game(String name, int dayOfWeek, String gameType, int weekNumber, Instant weekStartDate) {
        this.name = name;
        this.dayOfWeek = dayOfWeek;
        this.gameType = gameType;
        this.weekNumber = weekNumber;
        this.weekStartDate = weekStartDate;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getDayOfWeek() { return dayOfWeek; }
    public void setDayOfWeek(int dayOfWeek) { this.dayOfWeek = dayOfWeek; }

    public String getGameType() { return gameType; }
    public void setGameType(String gameType) { this.gameType = gameType; }

    public int getWeekNumber() { return weekNumber; }
    public void setWeekNumber(int weekNumber) { this.weekNumber = weekNumber; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getWeekStartDate() { return weekStartDate; }
    public void setWeekStartDate(Instant weekStartDate) { this.weekStartDate = weekStartDate; }
}
