package com.spin.FamilySpin.service;

import com.spin.FamilySpin.model.*;
import com.spin.FamilySpin.repository.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class GameService {

    private final GameRepository gameRepository;
    private final GameScoreRepository gameScoreRepository;
    private final GameQuestionRepository gameQuestionRepository;
    private final GameChallengeRepository gameChallengeRepository;

    private int currentWeekNumber;
    private Instant weekStartDate;

    public GameService(GameRepository gameRepository,
                      GameScoreRepository gameScoreRepository,
                      GameQuestionRepository gameQuestionRepository,
                      GameChallengeRepository gameChallengeRepository) {
        this.gameRepository = gameRepository;
        this.gameScoreRepository = gameScoreRepository;
        this.gameQuestionRepository = gameQuestionRepository;
        this.gameChallengeRepository = gameChallengeRepository;
        initializeWeek();
        initializeCurrentWeekGamesIfMissing();
    }

    private void initializeWeek() {
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        LocalDate weekStart = today.with(DayOfWeek.SUNDAY);
        this.currentWeekNumber = (int) (today.toEpochDay() / 7);
        this.weekStartDate = weekStart.atStartOfDay(ZoneId.systemDefault()).toInstant();
    }

    private void initializeCurrentWeekGamesIfMissing() {
        if (gameRepository.findByWeekNumber(currentWeekNumber).isEmpty()) {
            initializeWeekGames(currentWeekNumber, weekStartDate);
        }
    }

    /**
     * Get the current day of week (0=Sunday, 6=Saturday)
     */
    private int getTodayDayOfWeek() {
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        return today.getDayOfWeek().getValue() % 7;
    }

    /**
     * Get today's game
     */
    public synchronized Optional<Game> getTodayGame() {
        int today = getTodayDayOfWeek();
        return gameRepository.findByDayOfWeekAndWeekNumber(today, currentWeekNumber);
    }

    public synchronized Optional<Game> getGameById(Long gameId) {
        return gameRepository.findById(gameId);
    }

    /**
     * Get all games for current week
     */
    public synchronized List<Game> getCurrentWeekGames() {
        return gameRepository.findByWeekNumber(currentWeekNumber);
    }

    /**
     * Check if a game is today's game (playable)
     */
    public synchronized boolean isGamePlayable(Game game) {
        return getTodayGame().map(g -> g.getId().equals(game.getId())).orElse(false);
    }

    /**
     * Add score for user on a game
     */
    public synchronized void addScore(User user, Game game, int points, int weekNumber) {
        Optional<GameScore> existing = gameScoreRepository.findByUserAndGame(user, game);
        if (existing.isPresent()) {
            GameScore score = existing.get();
            score.setScore(score.getScore() + points);
            gameScoreRepository.save(score);
        } else {
            GameScore score = new GameScore(user, game, points, weekNumber);
            gameScoreRepository.save(score);
        }
    }

    /**
     * Get weekly leaderboard (top scorers this week)
     */
    public synchronized List<Map<String, Object>> getWeeklyLeaderboard() {
        List<GameScore> allScores = gameScoreRepository.findByWeekNumberOrderByScoreDesc(currentWeekNumber);
        
        Map<User, Integer> userTotalScores = new LinkedHashMap<>();
        for (GameScore score : allScores) {
            userTotalScores.put(score.getUser(), 
                userTotalScores.getOrDefault(score.getUser(), 0) + score.getScore());
        }

        List<Map<String, Object>> leaderboard = new ArrayList<>();
        int rank = 1;
        for (Map.Entry<User, Integer> entry : userTotalScores.entrySet()) {
            Map<String, Object> entry_map = new LinkedHashMap<>();
            entry_map.put("rank", rank++);
            entry_map.put("familyMemberName", entry.getKey().getFamilyMemberName());
            entry_map.put("username", entry.getKey().getUsername());
            entry_map.put("totalScore", entry.getValue());
            leaderboard.add(entry_map);
        }

        return leaderboard;
    }

    /**
     * Get random trivia question for today's game
     */
    public synchronized Optional<GameQuestion> getRandomQuestion(Game game) {
        List<GameQuestion> questions = gameQuestionRepository.findByGame(game);
        if (questions.isEmpty()) {
            return Optional.empty();
        }
        int randomIndex = ThreadLocalRandom.current().nextInt(questions.size());
        return Optional.of(questions.get(randomIndex));
    }

    /**
     * Get random challenge for today's game
     */
    public synchronized Optional<GameChallenge> getRandomChallenge(Game game) {
        List<GameChallenge> challenges = gameChallengeRepository.findByGame(game);
        if (challenges.isEmpty()) {
            return Optional.empty();
        }
        int randomIndex = ThreadLocalRandom.current().nextInt(challenges.size());
        return Optional.of(challenges.get(randomIndex));
    }

    /**
     * Initialize/regenerate games for a week
     */
    public synchronized void initializeWeekGames(int weekNumber, Instant weekStartDate) {
        // Delete old games for this week if any
        List<Game> oldGames = gameRepository.findByWeekNumber(weekNumber);
        gameRepository.deleteAll(oldGames);

        // Create new games for each day
        String[] gameNames = {
            "Family Spin",           // Sunday (0)
            "Trivia Challenge",      // Monday (1)
            "Memory Match",          // Tuesday (2)
            "Spin & Dare",          // Wednesday (3)
            "Charades/Pictionary",   // Thursday (4)
            "Word Association",      // Friday (5)
            "20 Questions"           // Saturday (6)
        };

        String[] gameTypes = {
            "SPIN",
            "TRIVIA",
            "MEMORY",
            "DARE",
            "CHARADES",
            "WORD_ASSOCIATION",
            "20_QUESTIONS"
        };

        for (int dayOfWeek = 0; dayOfWeek < 7; dayOfWeek++) {
            Game game = new Game(gameNames[dayOfWeek], dayOfWeek, gameTypes[dayOfWeek], weekNumber, weekStartDate);
            gameRepository.save(game);
            
            // Generate content based on game type
            generateGameContent(game);
        }

        this.currentWeekNumber = weekNumber;
        this.weekStartDate = weekStartDate;
    }

    /**
     * Generate random content for each game type
     */
    private void generateGameContent(Game game) {
        switch (game.getGameType()) {
            case "TRIVIA":
                generateTriviaQuestions(game);
                break;
            case "DARE":
                generateDareChallenges(game);
                break;
            case "CHARADES":
                generateCharadesChallenges(game);
                break;
            case "WORD_ASSOCIATION":
                generateWordChallenges(game);
                break;
            case "20_QUESTIONS":
                generateQuestionsChallenges(game);
                break;
            // MEMORY and SPIN don't need predefined content
        }
    }

    private void generateTriviaQuestions(Game game) {
        String[][] triviaData = {
            {"What does it mean to feel 'at home' in a community?", "A place where you belong and are accepted for who you are", "Community", "Proverbs 27:12 - Home is where we find refuge"},
            {"What is the biggest challenge university students face today?", "Mental health, loneliness, financial pressure, or academic stress", "Real Life", "Philippians 4:6-7 - Cast your cares on God"},
            {"How do you handle a conflict with someone you care about?", "Listen, apologize if wrong, seek to understand, find compromise", "Real Life", "Matthew 5:24 - Be reconciled with your brother"},
            {"What does it mean to have financial responsibility?", "Budgeting, saving, not overspending, understanding your limits", "Real Life", "Proverbs 21:5 - The plans of the diligent lead to profit"},
            {"What is the most important skill for your career future?", "Communication, problem-solving, adaptability, or continuous learning", "Career", "Proverbs 22:29 - Do you see someone skilled in their work"},
            {"How do you maintain mental health during stressful times?", "Exercise, sleep, therapy, talking to friends, or spiritual practices", "Real Life", "Philippians 4:8 - Think on these things"},
            {"What role does failure play in personal growth?", "It teaches resilience, reveals weaknesses, and builds character", "Personal Growth", "Romans 5:3-4 - Suffering produces perseverance"},
            {"What is true friendship based on?", "Trust, honesty, vulnerability, and mutual support through good and bad", "Relationships", "Proverbs 27:17 - Iron sharpens iron"},
            {"How do you decide what matters most in your life?", "Values, long-term goals, relationships, and what brings genuine fulfillment", "Real Life", "Colossians 3:15 - Let peace guide your decisions"},
            {"What is one way social media affects real relationships?", "Can create comparison, surface-level connection, or distance from genuine interaction", "Real Life", "Psalm 26:4 - I do not sit with the deceitful"}
        };

        for (String[] data : triviaData) {
            GameQuestion q = new GameQuestion(game, data[0], data[1], data[2], data[3]);
            gameQuestionRepository.save(q);
        }
    }

    private void generateDareChallenges(Game game) {
        String[][] dareData = {
            {"Share your biggest academic or career fear", "DARE", "Philippians 4:6 - Do not be anxious, present your requests to God", "Kwisi authenticity!"},
            {"Tell someone a mistake you made and what you learned", "DARE", "Proverbs 12:1 - Whoever loves discipline loves knowledge", "Kwisi growth!"},
            {"Share how you've struggled with mental health or stress", "DARE", "2 Corinthians 12:9 - My grace is sufficient for you", "Kwisi vulnerability!"},
            {"Give someone advice based on your real-life experience", "DARE", "Proverbs 27:12 - The prudent see danger and take refuge", "Kwisi wisdom!"},
            {"Share a time you felt lonely or out of place", "DARE", "Psalm 23:4 - You are with me in the valley", "Kwisi support!"},
            {"Challenge someone to pursue a goal they've been avoiding", "DARE", "Proverbs 31:8 - Speak up for those who have no voice", "Kwisi encouragement!"}
        };

        for (String[] data : dareData) {
            GameChallenge c = new GameChallenge(game, data[0], data[1], data[2], data[3]);
            gameChallengeRepository.save(c);
        }
    }

    private void generateCharadesChallenges(Game game) {
        String[][] charadesData = {
            {"Cramming for an exam the night before", "CHARADES", "Proverbs 21:5 - The plans of the diligent lead to profit", "Kwisi reality!"},
            {"Getting your first rejection (job, relationship, or dream)", "CHARADES", "Romans 5:3 - We also glory in our sufferings", "Kwisi resilience!"},
            {"Finally understanding a difficult concept", "CHARADES", "Proverbs 18:15 - The heart of the discerning acquires knowledge", "Kwisi breakthrough!"},
            {"Pretending to be fine when you're actually struggling", "CHARADES", "Matthew 11:28 - Come to me all who are weary", "Kwisi honesty!"},
            {"Realizing you made a wrong choice and needing to change course", "CHARADES", "Proverbs 14:12 - There is a way that appears right but leads to death", "Kwisi correction!"},
            {"Supporting a friend through a difficult time", "CHARADES", "1 Thessalonians 5:11 - Encourage one another", "Kwisi presence!"},
            {"Stepping out of your comfort zone for the first time", "CHARADES", "Joshua 1:9 - Be strong and courageous", "Kwisi courage!"},
            {"Discovering a hidden talent or passion within yourself", "CHARADES", "1 Peter 4:10 - Each of you should use your gifts", "Kwisi discovery!"}
        };

        for (String[] data : charadesData) {
            GameChallenge c = new GameChallenge(game, data[0], data[1], data[2], data[3]);
            gameChallengeRepository.save(c);
        }
    }

    private void generateWordChallenges(Game game) {
        String[][] wordData = {
            {"Uncertainty", "WORD", "Proverbs 3:5-6 - Trust in the Lord with all your heart", "Kwisi growth!"},
            {"Exhaustion", "WORD", "Matthew 11:28 - Come to me and I will give you rest", "Kwisi rest!"},
            {"Transformation", "WORD", "2 Corinthians 5:17 - If anyone is in Christ, they are new", "Kwisi renewal!"},
            {"Courage", "WORD", "Deuteronomy 31:6 - Be strong and courageous", "Kwisi strength!"},
            {"Purpose", "WORD", "Jeremiah 29:11 - I have plans for you, plans for good", "Kwisi direction!"},
            {"Resilience", "WORD", "James 1:2-3 - Consider it joy to face trials", "Kwisi perseverance!"},
            {"Authenticity", "WORD", "Proverbs 12:17 - The honest witness tells the truth", "Kwisi real!"},
            {"Balance", "WORD", "Ecclesiastes 3:1 - There is a time for everything", "Kwisi harmony!"}
        };

        for (String[] data : wordData) {
            GameChallenge c = new GameChallenge(game, data[0], data[1], data[2], data[3]);
            gameChallengeRepository.save(c);
        }
    }

    private void generateQuestionsChallenges(Game game) {
        String[][] questionData = {
            {"Something you desperately needed but was afraid to ask for", "QUESTION", "Philippians 4:6 - Present your requests to God", "Kwisi vulnerability!"},
            {"A difficult decision that changed the course of your life", "QUESTION", "Proverbs 16:9 - The Lord establishes the steps of the godly", "Kwisi choice!"},
            {"A time you had to start over from scratch", "QUESTION", "Lamentations 3:22-23 - His mercies are new every morning", "Kwisi beginning!"},
            {"Something you gave up to become who you are today", "QUESTION", "Matthew 16:25 - Whoever loses their life for me will find it", "Kwisi sacrifice!"},
            {"A moment when you realized your strength", "QUESTION", "Philippians 4:13 - I can do all things through Christ", "Kwisi power!"},
            {"Someone who believed in you when you didn't believe in yourself", "QUESTION", "Proverbs 27:12 - The prudent see danger and take refuge", "Kwisi gratitude!"}
        };

        for (String[] data : questionData) {
            GameChallenge c = new GameChallenge(game, data[0], data[1], data[2], data[3]);
            gameChallengeRepository.save(c);
        }
    }

    /**
     * Weekly reset - called every Sunday
     */
    @Scheduled(cron = "0 0 0 * * SUN")
    public synchronized void weeklyReset() {
        LocalDate nextSunday = LocalDate.now(ZoneId.systemDefault());
        int nextWeekNumber = currentWeekNumber + 1;
        Instant nextWeekStart = nextSunday.atStartOfDay(ZoneId.systemDefault()).toInstant();
        initializeWeekGames(nextWeekNumber, nextWeekStart);
    }

    public int getCurrentWeekNumber() {
        return currentWeekNumber;
    }
}
