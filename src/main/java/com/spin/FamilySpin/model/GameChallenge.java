package com.spin.FamilySpin.model;

import jakarta.persistence.*;

@Entity
@Table(name = "game_challenges")
public class GameChallenge {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "game_id")
    private Game game;

    private String challenge;
    private String type; // "DARE", "CHARADES", "WORD", "QUESTION", etc.
    private String bibleVerse; // Optional inspiration
    private String kwisiPhrase; // Family phrase/joke ("Kwisi!" etc.)

    public GameChallenge() {}

    public GameChallenge(Game game, String challenge, String type, String bibleVerse, String kwisiPhrase) {
        this.game = game;
        this.challenge = challenge;
        this.type = type;
        this.bibleVerse = bibleVerse;
        this.kwisiPhrase = kwisiPhrase;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Game getGame() { return game; }
    public void setGame(Game game) { this.game = game; }

    public String getChallenge() { return challenge; }
    public void setChallenge(String challenge) { this.challenge = challenge; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getBibleVerse() { return bibleVerse; }
    public void setBibleVerse(String bibleVerse) { this.bibleVerse = bibleVerse; }

    public String getKwisiPhrase() { return kwisiPhrase; }
    public void setKwisiPhrase(String kwisiPhrase) { this.kwisiPhrase = kwisiPhrase; }
}
