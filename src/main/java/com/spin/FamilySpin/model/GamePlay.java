package com.spin.FamilySpin.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "game_plays")
public class GamePlay {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    private int sessionNumber;
    private String eliminatedMember;
    private Instant playedAt;

    public GamePlay() {}

    public GamePlay(User user, int sessionNumber, String eliminatedMember) {
        this.user = user;
        this.sessionNumber = sessionNumber;
        this.eliminatedMember = eliminatedMember;
        this.playedAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public int getSessionNumber() { return sessionNumber; }
    public void setSessionNumber(int sessionNumber) { this.sessionNumber = sessionNumber; }

    public String getEliminatedMember() { return eliminatedMember; }
    public void setEliminatedMember(String eliminatedMember) { this.eliminatedMember = eliminatedMember; }

    public Instant getPlayedAt() { return playedAt; }
    public void setPlayedAt(Instant playedAt) { this.playedAt = playedAt; }
}
