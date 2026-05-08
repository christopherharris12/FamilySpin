package com.spin.FamilySpin.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "game_scores")
public class GameScore {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne
    @JoinColumn(name = "game_id")
    private Game game;

    private int score;
    private int weekNumber;
    private Instant scoredAt;

    public GameScore() {}

    public GameScore(User user, Game game, int score, int weekNumber) {
        this.user = user;
        this.game = game;
        this.score = score;
        this.weekNumber = weekNumber;
        this.scoredAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public Game getGame() { return game; }
    public void setGame(Game game) { this.game = game; }

    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }

    public int getWeekNumber() { return weekNumber; }
    public void setWeekNumber(int weekNumber) { this.weekNumber = weekNumber; }

    public Instant getScoredAt() { return scoredAt; }
    public void setScoredAt(Instant scoredAt) { this.scoredAt = scoredAt; }
}
