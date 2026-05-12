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
    
    // Multiple choice options (for TRIVIA)
    private String option1;
    private String option2;
    private String option3;
    private String option4;

    public GameQuestion() {}

    public GameQuestion(Game game, String question, String answer, String category, String bibleVerse) {
        this.game = game;
        this.question = question;
        this.answer = answer;
        this.category = category;
        this.bibleVerse = bibleVerse;
    }
    
    public GameQuestion(Game game, String question, String answer, String category, String bibleVerse,
                       String option1, String option2, String option3, String option4) {
        this.game = game;
        this.question = question;
        this.answer = answer;
        this.category = category;
        this.bibleVerse = bibleVerse;
        this.option1 = option1;
        this.option2 = option2;
        this.option3 = option3;
        this.option4 = option4;
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
    
    public String getOption1() { return option1; }
    public void setOption1(String option1) { this.option1 = option1; }

    public String getOption2() { return option2; }
    public void setOption2(String option2) { this.option2 = option2; }

    public String getOption3() { return option3; }
    public void setOption3(String option3) { this.option3 = option3; }

    public String getOption4() { return option4; }
    public void setOption4(String option4) { this.option4 = option4; }
}
