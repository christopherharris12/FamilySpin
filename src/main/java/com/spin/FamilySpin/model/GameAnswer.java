package com.spin.FamilySpin.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "game_answers")
public class GameAnswer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne
    @JoinColumn(name = "game_id")
    private Game game;

    @Column(name = "answer_text")
    private String answerText;

    @Column(name = "answer_date")
    private LocalDate answerDate;

    @Column(name = "attempt_number")
    private Integer attemptNumber;

    public GameAnswer() {
    }

    public GameAnswer(User user, Game game, String answerText, LocalDate answerDate, Integer attemptNumber) {
        this.user = user;
        this.game = game;
        this.answerText = answerText;
        this.answerDate = answerDate;
        this.attemptNumber = attemptNumber;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Game getGame() {
        return game;
    }

    public void setGame(Game game) {
        this.game = game;
    }

    public String getAnswerText() {
        return answerText;
    }

    public void setAnswerText(String answerText) {
        this.answerText = answerText;
    }

    public LocalDate getAnswerDate() {
        return answerDate;
    }

    public void setAnswerDate(LocalDate answerDate) {
        this.answerDate = answerDate;
    }

    public Integer getAttemptNumber() {
        return attemptNumber;
    }

    public void setAttemptNumber(Integer attemptNumber) {
        this.attemptNumber = attemptNumber;
    }
}
