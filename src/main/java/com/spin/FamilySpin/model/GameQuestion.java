package com.spin.FamilySpin.model;

import jakarta.persistence.*;

@Entity
@Table(name = "game_questions")
public class GameQuestion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "game_id")
    private Game game;

    private String question;
    private String answer;
    private String category; // "Bible", "Family", "General", etc.
    private String bibleVerse; // Optional Bible verse reference

    public GameQuestion() {}

    public GameQuestion(Game game, String question, String answer, String category, String bibleVerse) {
        this.game = game;
        this.question = question;
        this.answer = answer;
        this.category = category;
        this.bibleVerse = bibleVerse;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Game getGame() { return game; }
    public void setGame(Game game) { this.game = game; }

    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }

    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getBibleVerse() { return bibleVerse; }
    public void setBibleVerse(String bibleVerse) { this.bibleVerse = bibleVerse; }
}
