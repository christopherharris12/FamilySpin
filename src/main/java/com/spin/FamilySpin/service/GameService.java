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
            {"What is one value that helps people feel connected in a family?", "Trust, vulnerability, or genuine listening", "Community", "1 John 4:7-8 - We love because we know we are loved"},
            {"How can we make someone feel welcomed into our community?", "Listen to their story, show genuine interest, include them in activities", "Community", "Hebrews 13:2 - Welcome strangers"},
            {"What does 'Kwisi' mean in the context of family bonding?", "Joy, vibes, authentic connection and good energy", "Family", "Psalm 16:11 - Joy is in God's presence"},
            {"What is one challenge university students face when building community?", "Loneliness, fear of rejection, lack of time, cultural differences", "University", "Ecclesiastes 4:9-10 - Two are better than one"},
            {"How do we handle conflict in a healthy community?", "Open communication, listen to understand, seek reconciliation", "Community", "Matthew 18:15-17 - Address issues with compassion"},
            {"What is the most important ingredient for building trust?", "Consistency, honesty, and being vulnerable", "Community", "Proverbs 13:3 - Guard your words carefully"},
            {"What does 'home' represent beyond just a place?", "Belonging, safety, acceptance, and unconditional support", "Community", "John 14:1-3 - 'I go to prepare a place for you'"},
            {"How can diversity strengthen a community?", "Different perspectives bring innovation, empathy, and richer understanding", "Community", "1 Corinthians 12:12-13 - Many parts, one body"},
            {"What is one way to show someone they truly matter in your community?", "Remember details about their life, celebrate their wins, show up in hard times", "Community", "1 Thessalonians 5:11 - Encourage one another"}
        };

        for (String[] data : triviaData) {
            GameQuestion q = new GameQuestion(game, data[0], data[1], data[2], data[3]);
            gameQuestionRepository.save(q);
        }
    }

    private void generateDareChallenges(Game game) {
        String[][] dareData = {
            {"Share a moment when you felt truly accepted by the community", "DARE", "1 Peter 4:10 - Use your gifts to serve each other", "Kwisi na vulnerability!"},
            {"Tell someone in the group why they matter to you personally", "DARE", "1 Thessalonians 5:11 - Encourage one another", "Kwisi amakuru!"},
            {"Share a fear you're working to overcome", "DARE", "2 Timothy 1:7 - God has not given us a spirit of fear", "Kwisi with courage!"},
            {"Give a genuine apology if you've hurt someone in the family", "DARE", "Matthew 5:24 - Be reconciled with your brother", "Kwisi peace!"},
            {"Share what home means to you and why community matters", "DARE", "Proverbs 22:3 - The prudent see danger and take refuge", "Kwisi home!"},
            {"Challenge someone to a 1-on-1 coffee date to deepen your friendship", "DARE", "Proverbs 27:17 - As iron sharpens iron, so one person sharpens another", "Kwisi connection!"}
        };

        for (String[] data : dareData) {
            GameChallenge c = new GameChallenge(game, data[0], data[1], data[2], data[3]);
            gameChallengeRepository.save(c);
        }
    }

    private void generateCharadesChallenges(Game game) {
        String[][] charadesData = {
            {"Feeling lonely at university", "CHARADES", "Psalm 25:16 - Turn to me and be gracious to me", "Kwisi understanding!"},
            {"Finding your tribe/community", "CHARADES", "Proverbs 13:20 - Walk with the wise and become wise", "Kwisi connection!"},
            {"Overcoming self-doubt", "CHARADES", "Philippians 4:13 - I can do all things through Christ", "Kwisi courage!"},
            {"Celebrating a friend's success", "CHARADES", "Romans 12:15 - Rejoice with those who rejoice", "Kwisi celebration!"},
            {"Being vulnerable and opening up", "CHARADES", "Ephesians 5:1 - Be imitators of God", "Kwisi authenticity!"},
            {"Building bridges across differences", "CHARADES", "1 Corinthians 12:12 - One body with many parts", "Kwisi unity!"},
            {"Offering forgiveness and grace", "CHARADES", "Matthew 18:22 - Forgive seventy times seven", "Kwisi peace!"},
            {"Experiencing God's presence in community", "CHARADES", "Matthew 18:20 - Where two or three gather, I am there", "Kwisi sacred!"}
        };

        for (String[] data : charadesData) {
            GameChallenge c = new GameChallenge(game, data[0], data[1], data[2], data[3]);
            gameChallengeRepository.save(c);
        }
    }

    private void generateWordChallenges(Game game) {
        String[][] wordData = {
            {"Belonging", "WORD", "Ephesians 2:14-16 - We are no longer foreigners", "Kwisi home!"},
            {"Vulnerability", "WORD", "2 Corinthians 12:9 - My grace is sufficient for you", "Kwisi authenticity!"},
            {"Community", "WORD", "Acts 2:44 - All the believers were together", "Kwisi unity!"},
            {"Growth", "WORD", "2 Peter 3:18 - Grow in the grace and knowledge", "Kwisi growth!"},
            {"Connection", "WORD", "1 Thessalonians 5:11 - Encourage one another", "Kwisi bonds!"},
            {"Acceptance", "WORD", "Romans 15:7 - Accept one another as Christ accepted you", "Kwisi welcome!"},
            {"Authenticity", "WORD", "Proverbs 12:17 - The honest witness tells the truth", "Kwisi truth!"},
            {"Gratitude", "WORD", "1 Thessalonians 5:18 - Give thanks in all circumstances", "Kwisi appreciation!"}
        };

        for (String[] data : wordData) {
            GameChallenge c = new GameChallenge(game, data[0], data[1], data[2], data[3]);
            gameChallengeRepository.save(c);
        }
    }

    private void generateQuestionsChallenges(Game game) {
        String[][] questionData = {
            {"An emotion you feel when you finally find where you belong", "QUESTION", "Psalm 37:4 - Take delight in the Lord", "Kwisi acceptance!"},
            {"Something that bridges the gap between strangers in a community", "QUESTION", "Proverbs 27:17 - Iron sharpens iron", "Kwisi connection!"},
            {"A person who has shaped your character and values", "QUESTION", "Proverbs 13:20 - Walk with the wise", "Kwisi gratitude!"},
            {"Something that happens when you share your true self", "QUESTION", "John 8:32 - The truth will set you free", "Kwisi authenticity!"},
            {"A moment that changed your perspective on community", "QUESTION", "2 Corinthians 5:17 - All things become new", "Kwisi transformation!"},
            {"Something that demonstrates genuine care in a family", "QUESTION", "1 Peter 4:10 - Use your gifts to serve", "Kwisi love!"}
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
