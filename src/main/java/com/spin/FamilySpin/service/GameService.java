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
            {"Who is the Father of our Lord Jesus Christ?", "God", "Bible", "John 1:1"},
            {"What is the greatest commandment?", "Love God and love your neighbor", "Bible", "Matthew 22:37"},
            {"How many books are in the Bible?", "66", "Bible", "2 Timothy 3:16"},
            {"In what year was the FamilySpin started?", "2024", "Family", null},
            {"What does 'Kwisi' mean to our family?", "Joy/Vibes", "Family", null},
            {"What is the capital of Rwanda?", "Kigali", "General", null},
            {"How many continents are there?", "7", "General", null},
            {"What is the most spoken language in the world?", "Mandarin Chinese", "General", null},
            {"Who wrote Romeo and Juliet?", "William Shakespeare", "General", null},
            {"What year did the internet become public?", "1991", "General", null}
        };

        for (String[] data : triviaData) {
            GameQuestion q = new GameQuestion(game, data[0], data[1], data[2], data[3]);
            gameQuestionRepository.save(q);
        }
    }

    private void generateDareChallenges(Game game) {
        String[][] dareData = {
            {"Tell a funny story about yourself", "DARE", "Proverbs 17:22 - A joyful heart is good medicine", "Kwisi na kwisi!"},
            {"Do 10 jumping jacks", "DARE", null, "Kwisi amakuru!"},
            {"Sing your favorite song", "DARE", "Psalm 100:1 - Make a joyful noise", "Kwisi uwundi!"},
            {"Give everyone a compliment", "DARE", "Proverbs 16:24 - Gracious words are like honey", null},
            {"Dance for 20 seconds", "DARE", "Psalm 149:3 - Praise His name with dancing", "Kwisi na jambo!"},
            {"Tell a joke to make family laugh", "DARE", "Proverbs 17:22", "Kwisi!"}
        };

        for (String[] data : dareData) {
            GameChallenge c = new GameChallenge(game, data[0], data[1], data[2], data[3]);
            gameChallengeRepository.save(c);
        }
    }

    private void generateCharadesChallenges(Game game) {
        String[][] charadesData = {
            {"Sleeping", "CHARADES", null, "Kwisi!"},
            {"Eating pizza", "CHARADES", null, "Kwisi!"},
            {"Swimming", "CHARADES", null, "Kwisi!"},
            {"Flying like a bird", "CHARADES", null, "Kwisi!"},
            {"Brushing teeth", "CHARADES", null, "Kwisi!"},
            {"Playing football", "CHARADES", null, "Kwisi!"},
            {"Driving a car", "CHARADES", null, "Kwisi!"},
            {"Fishing", "CHARADES", null, "Kwisi!"}
        };

        for (String[] data : charadesData) {
            GameChallenge c = new GameChallenge(game, data[0], data[1], data[2], data[3]);
            gameChallengeRepository.save(c);
        }
    }

    private void generateWordChallenges(Game game) {
        String[][] wordData = {
            {"Apple", "WORD", null, "Kwisi!"},
            {"Family", "WORD", null, "Kwisi!"},
            {"Love", "WORD", "1 John 4:7", "Kwisi!"},
            {"Joy", "WORD", "Philippians 4:4", "Kwisi!"},
            {"Hope", "WORD", "Romans 15:13", "Kwisi!"},
            {"Peace", "WORD", "Philippians 4:7", "Kwisi!"},
            {"Rwanda", "WORD", null, "Kwisi!"},
            {"Blessing", "WORD", "Proverbs 10:22", "Kwisi!"}
        };

        for (String[] data : wordData) {
            GameChallenge c = new GameChallenge(game, data[0], data[1], data[2], data[3]);
            gameChallengeRepository.save(c);
        }
    }

    private void generateQuestionsChallenges(Game game) {
        String[][] questionData = {
            {"An animal that barks", "QUESTION", null, "Kwisi!"},
            {"A fruit that is yellow", "QUESTION", null, "Kwisi!"},
            {"Something you do before sleeping", "QUESTION", null, "Kwisi!"},
            {"A place with many books", "QUESTION", null, "Kwisi!"},
            {"Something that flows in rivers", "QUESTION", null, "Kwisi!"},
            {"A sport played with a ball", "QUESTION", null, "Kwisi!"}
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
