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
    private final GameAnswerRepository gameAnswerRepository;

    private int currentWeekNumber;
    private Instant weekStartDate;

    public GameService(GameRepository gameRepository,
                      GameScoreRepository gameScoreRepository,
                      GameQuestionRepository gameQuestionRepository,
                      GameChallengeRepository gameChallengeRepository,
                      GameAnswerRepository gameAnswerRepository) {
        this.gameRepository = gameRepository;
        this.gameScoreRepository = gameScoreRepository;
        this.gameQuestionRepository = gameQuestionRepository;
        this.gameChallengeRepository = gameChallengeRepository;
        this.gameAnswerRepository = gameAnswerRepository;
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
     * Record an answer attempt for a user on a game
     */
    public synchronized void recordAnswer(User user, Game game, String answerText) {
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        long count = gameAnswerRepository.countByUserAndGameAndAnswerDate(user, game, today);
        GameAnswer answer = new GameAnswer(user, game, answerText, today, (int)(count + 1));
        gameAnswerRepository.save(answer);
    }

    /**
     * Check if user has remaining attempts on today's game
     * Returns: 2 = can still play, 1 = last attempt, 0 = no more attempts
     */
    public synchronized int getRemainingAttempts(User user, Game game) {
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        long count = gameAnswerRepository.countByUserAndGameAndAnswerDate(user, game, today);
        if (count >= 2) {
            return 0; // No more attempts
        }
        return (int)(2 - count); // Return remaining attempts
    }

    /**
     * Get all attempts for a user on today's game
     */
    public synchronized List<GameAnswer> getTodayAttempts(User user, Game game) {
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        return gameAnswerRepository.findByUserAndGameAndAnswerDate(user, game, today);
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
            // Easy Fun Questions
            {"What's your favorite food?", "Varies - it's fun!", "Fun", "Kwisi favorites!"},
            {"What's your go-to movie genre?", "Action, Comedy, Drama, Horror - any!", "Fun", "Kwisi entertainment!"},
            {"If you could travel anywhere, where?", "Beach, Mountains, City, Country - anywhere!", "Travel", "Kwisi adventures!"},
            {"What's your favorite season?", "Summer, Fall, Winter, Spring", "Life", "Kwisi weather!"},
            {"What's your guilty pleasure snack?", "Pizza, Chips, Cake, Ice cream - anything!", "Fun", "Kwisi treats!"},
            
            // Popular & Easy
            {"Who's your celebrity crush?", "Anyone famous you like!", "Pop Culture", "Kwisi crush!"},
            {"What's the best day of the week?", "Friday, Saturday, Sunday!", "Life", "Kwisi days!"},
            {"Coffee or Tea?", "Everyone has an opinion!", "Lifestyle", "Kwisi drinks!"},
            {"What time do you usually wake up?", "Early bird or night owl?", "Life", "Kwisi sleep!"},
            {"What's your favorite music genre?", "Hip-hop, Pop, Rock, R&B, Reggae!", "Music", "Kwisi vibes!"},
            
            // Relatable & Easy
            {"What's your superpower?", "Making people laugh, listening, cooking!", "Fun", "Kwisi power!"},
            {"If you were an animal, what would you be?", "Dog, Cat, Lion, Eagle!", "Fun", "Kwisi animal!"},
            {"What's your favorite thing about family?", "The laughter, the love, the memories!", "Family", "Kwisi love!"},
            {"What sport do you like to play or watch?", "Football, Basketball, Tennis, Swimming!", "Sports", "Kwisi games!"},
            {"What's your favorite dessert?", "Cake, Ice cream, Chocolate, Fruit!", "Food", "Kwisi sweet!"},
            
            // Light & Happy
            {"What makes you laugh the most?", "Jokes, Friends, Family, Movies!", "Joy", "Kwisi laughter!"},
            {"What's your go-to karaoke song?", "Any song you love to sing!", "Music", "Kwisi singing!"},
            {"Would you rather: Beach or Mountains?", "Pick your adventure!", "Travel", "Kwisi nature!"},
            {"What's your favorite emoji?", "😂 😍 🔥 💯 - any one!", "Fun", "Kwisi express!"},
            {"What's the best time of day?", "Morning, Afternoon, Evening, Night!", "Life", "Kwisi time!"},
            
            // Simple & Fun
            {"What's your favorite color?", "Red, Blue, Green, Purple - anything!", "Life", "Kwisi colors!"},
            {"Do you prefer hot or cold weather?", "Summer heat or winter cool?", "Life", "Kwisi weather!"},
            {"What's your favorite hobby?", "Gaming, Reading, Sports, Art!", "Life", "Kwisi passion!"},
            {"Who's your favorite family member?", "Mom, Dad, Sibling, Cousin!", "Family", "Kwisi person!"},
            {"What's your favorite app?", "WhatsApp, TikTok, Instagram, YouTube!", "Tech", "Kwisi app!"},
            
            // Popular Culture
            {"Favorite superhero?", "Batman, Superman, Spider-Man, Ironman!", "Pop Culture", "Kwisi hero!"},
            {"Cats or Dogs?", "A classic question!", "Animals", "Kwisi pet!"},
            {"What's your favorite TV show?", "Game of Thrones, Friends, Breaking Bad!", "TV", "Kwisi show!"},
            {"Would you rather: Fly or Invisibility?", "Pick your superpower!", "Fun", "Kwisi power!"},
            {"What's your favorite holiday?", "Christmas, New Year, Birthday!", "Holidays", "Kwisi celebration!"},
            
            // Quick & Easy
            {"Breakfast: Sweet or Savory?", "Pancakes or Eggs?", "Food", "Kwisi morning!"},
            {"Prefer: Phone or Laptop?", "Which tech do you use most?", "Tech", "Kwisi device!"},
            {"Right-handed or Left-handed?", "Which side do you write with?", "Life", "Kwisi hand!"},
            {"Your favorite drink?", "Water, Juice, Soda, Coffee!", "Drinks", "Kwisi sip!"},
            {"Night owl or Early bird?", "When are you most awake?", "Life", "Kwisi sleep!"},
            
            // Relatable Stories
            {"What's something funny that happened to you?", "Share a fun memory!", "Memories", "Kwisi funny!"},
            {"What skill would you like to learn?", "Dancing, Cooking, Drawing!", "Growth", "Kwisi learn!"},
            {"Your biggest achievement?", "Anything you're proud of!", "Achievement", "Kwisi pride!"},
            {"What's your dream job?", "Doctor, Teacher, Musician, CEO!", "Dreams", "Kwisi work!"},
            {"Best place you've visited?", "Any country or city!", "Travel", "Kwisi explore!"}
        };

        for (String[] data : triviaData) {
            GameQuestion q = new GameQuestion(game, data[0], data[1], data[2], data[3]);
            gameQuestionRepository.save(q);
        }
    }

    private void generateDareChallenges(Game game) {
        String[][] dareData = {
            // Fun & Light
            {"Make a funny face for 10 seconds", "DARE", "Kwisi vibes!", "Kwisi fun!"},
            {"Do your best dance move", "DARE", "Kwisi dancing!", "Kwisi rhythm!"},
            {"Sing a line from your favorite song", "DARE", "Kwisi singing!", "Kwisi voice!"},
            {"Tell a clean joke to make people laugh", "DARE", "Kwisi laughter!", "Kwisi humor!"},
            {"Do 5 push-ups or jumping jacks", "DARE", "Kwisi exercise!", "Kwisi strength!"},
            
            // Silly & Playful
            {"Speak in an accent for 1 minute", "DARE", "Kwisi accent!", "Kwisi drama!"},
            {"Walk like your favorite animal", "DARE", "Kwisi animal!", "Kwisi moves!"},
            {"Give the person next to you a compliment", "DARE", "Kwisi love!", "Kwisi kindness!"},
            {"Do an impression of someone famous", "DARE", "Kwisi impression!", "Kwisi acting!"},
            {"Pretend to be a robot for 30 seconds", "DARE", "Kwisi robot!", "Kwisi weird!"},
            
            // Interactive
            {"High-five everyone in the room", "DARE", "Kwisi connection!", "Kwisi energy!"},
            {"Get 3 people to smile at you", "DARE", "Kwisi charm!", "Kwisi smile!"},
            {"Tell someone why they're awesome", "DARE", "Kwisi love!", "Kwisi appreciation!"},
            {"Give someone a funny nickname", "DARE", "Kwisi fun!", "Kwisi laughter!"},
            {"Ask someone about their favorite memory", "DARE", "Kwisi listen!", "Kwisi share!"},
            
            // Quick Laughs
            {"Make your silliest noise for 5 seconds", "DARE", "Kwisi noise!", "Kwisi fun!"},
            {"Do the moonwalk (or try to!)", "DARE", "Kwisi dance!", "Kwisi moves!"},
            {"Tell 3 things you're grateful for", "DARE", "Kwisi grateful!", "Kwisi thanks!"},
            {"Act like a celebrity for 1 minute", "DARE", "Kwisi famous!", "Kwisi acting!"},
            {"Describe your day using only one word repeated", "DARE", "Kwisi weird!", "Kwisi fun!"},
            
            // Bonding
            {"Share your best friend memory", "DARE", "Kwisi memories!", "Kwisi connection!"},
            {"Teach someone a dance move", "DARE", "Kwisi dancing!", "Kwisi teaching!"},
            {"Make everyone laugh within 30 seconds", "DARE", "Kwisi comedy!", "Kwisi laughter!"},
            {"Give 5 high-fives to different people", "DARE", "Kwisi energy!", "Kwisi connection!"},
            {"Tell your funniest story", "DARE", "Kwisi story!", "Kwisi fun!"},
            
            // Personal but Light
            {"Share your go-to karaoke song", "DARE", "Kwisi music!", "Kwisi singing!"},
            {"Do your best super hero pose and say your name", "DARE", "Kwisi power!", "Kwisi fun!"},
            {"Teach us a new dance you know", "DARE", "Kwisi dancing!", "Kwisi teaching!"},
            {"Tell us about your ideal vacation", "DARE", "Kwisi travel!", "Kwisi dreams!"},
            {"Show us your coolest talent", "DARE", "Kwisi talent!", "Kwisi amazing!"}
        };

        for (String[] data : dareData) {
            GameChallenge c = new GameChallenge(game, data[0], data[1], data[2], data[3]);
            gameChallengeRepository.save(c);
        }
    }

    private void generateCharadesChallenges(Game game) {
        String[][] charadesData = {
            // Simple & Fun Actions
            {"Sleeping", "CHARADES", "Kwisi rest!", "Kwisi action!"},
            {"Brushing teeth", "CHARADES", "Kwisi morning!", "Kwisi action!"},
            {"Dancing", "CHARADES", "Kwisi rhythm!", "Kwisi action!"},
            {"Eating pizza", "CHARADES", "Kwisi delicious!", "Kwisi action!"},
            {"Swimming", "CHARADES", "Kwisi water!", "Kwisi action!"},
            
            // Common Things
            {"Playing football", "CHARADES", "Kwisi sports!", "Kwisi action!"},
            {"Driving a car", "CHARADES", "Kwisi travel!", "Kwisi action!"},
            {"Cooking", "CHARADES", "Kwisi food!", "Kwisi action!"},
            {"Reading a book", "CHARADES", "Kwisi learn!", "Kwisi action!"},
            {"Watching TV", "CHARADES", "Kwisi relax!", "Kwisi action!"},
            
            // Daily Life
            {"Taking a shower", "CHARADES", "Kwisi clean!", "Kwisi action!"},
            {"Riding a bike", "CHARADES", "Kwisi fun!", "Kwisi action!"},
            {"Playing video games", "CHARADES", "Kwisi gaming!", "Kwisi action!"},
            {"Laughing at a joke", "CHARADES", "Kwisi funny!", "Kwisi action!"},
            {"Running away scared", "CHARADES", "Kwisi funny!", "Kwisi action!"},
            
            // Easy Emotions
            {"Very angry", "CHARADES", "Kwisi emotions!", "Kwisi action!"},
            {"Crying", "CHARADES", "Kwisi sad!", "Kwisi action!"},
            {"Being cold", "CHARADES", "Kwisi brr!", "Kwisi action!"},
            {"Being hot", "CHARADES", "Kwisi hot!", "Kwisi action!"},
            {"Tired and sleepy", "CHARADES", "Kwisi nap!", "Kwisi action!"},
            
            // Popular Things
            {"Superhero flying", "CHARADES", "Kwisi power!", "Kwisi action!"},
            {"Robot walking", "CHARADES", "Kwisi mechanical!", "Kwisi action!"},
            {"Zombie walking", "CHARADES", "Kwisi scary!", "Kwisi action!"},
            {"Baby crawling", "CHARADES", "Kwisi cute!", "Kwisi action!"},
            {"Old person walking", "CHARADES", "Kwisi slow!", "Kwisi action!"}
        };

        for (String[] data : charadesData) {
            GameChallenge c = new GameChallenge(game, data[0], data[1], data[2], data[3]);
            gameChallengeRepository.save(c);
        }
    }

    private void generateWordChallenges(Game game) {
        String[][] wordData = {
            // Common Words
            {"Apple", "WORD", "Kwisi fruit!", "Kwisi easy!"},
            {"Family", "WORD", "Kwisi love!", "Kwisi ours!"},
            {"Love", "WORD", "Kwisi heart!", "Kwisi feeling!"},
            {"Joy", "WORD", "Kwisi happy!", "Kwisi vibes!"},
            {"Hope", "WORD", "Kwisi future!", "Kwisi dreams!"},
            
            // Easy Starting Words
            {"Music", "WORD", "Kwisi sound!", "Kwisi beat!"},
            {"Laughter", "WORD", "Kwisi funny!", "Kwisi smile!"},
            {"Friend", "WORD", "Kwisi people!", "Kwisi bond!"},
            {"Peace", "WORD", "Kwisi calm!", "Kwisi rest!"},
            {"Dance", "WORD", "Kwisi move!", "Kwisi fun!"},
            
            // Fun Objects
            {"Pizza", "WORD", "Kwisi food!", "Kwisi yum!"},
            {"Movie", "WORD", "Kwisi watch!", "Kwisi entertainment!"},
            {"Game", "WORD", "Kwisi fun!", "Kwisi play!"},
            {"Beach", "WORD", "Kwisi sand!", "Kwisi water!"},
            {"Party", "WORD", "Kwisi celebration!", "Kwisi people!"},
            
            // Simple Verbs
            {"Running", "WORD", "Kwisi fast!", "Kwisi move!"},
            {"Cooking", "WORD", "Kwisi food!", "Kwisi yum!"},
            {"Singing", "WORD", "Kwisi voice!", "Kwisi music!"},
            {"Playing", "WORD", "Kwisi fun!", "Kwisi games!"},
            {"Sleeping", "WORD", "Kwisi rest!", "Kwisi dream!"},
            
            // Nature
            {"Tree", "WORD", "Kwisi green!", "Kwisi nature!"},
            {"Fire", "WORD", "Kwisi hot!", "Kwisi warm!"},
            {"Water", "WORD", "Kwisi wet!", "Kwisi drink!"},
            {"Rain", "WORD", "Kwisi drops!", "Kwisi weather!"},
            {"Sun", "WORD", "Kwisi bright!", "Kwisi warm!"},
            
            // Emotions
            {"Happy", "WORD", "Kwisi smile!", "Kwisi feel!"},
            {"Excited", "WORD", "Kwisi energy!", "Kwisi yes!"},
            {"Brave", "WORD", "Kwisi strong!", "Kwisi courage!"},
            {"Silly", "WORD", "Kwisi funny!", "Kwisi laugh!"},
            {"Kind", "WORD", "Kwisi care!", "Kwisi love!"}
        };

        for (String[] data : wordData) {
            GameChallenge c = new GameChallenge(game, data[0], data[1], data[2], data[3]);
            gameChallengeRepository.save(c);
        }
    }

    private void generateQuestionsChallenges(Game game) {
        String[][] questionData = {
            // Animals
            {"A dog", "QUESTION", "Kwisi animal!", "Kwisi guess!"},
            {"A cat", "QUESTION", "Kwisi animal!", "Kwisi guess!"},
            {"A lion", "QUESTION", "Kwisi animal!", "Kwisi guess!"},
            {"A bird", "QUESTION", "Kwisi animal!", "Kwisi guess!"},
            {"A fish", "QUESTION", "Kwisi animal!", "Kwisi guess!"},
            
            // Foods
            {"Pizza", "QUESTION", "Kwisi food!", "Kwisi yum!"},
            {"Ice cream", "QUESTION", "Kwisi food!", "Kwisi yum!"},
            {"Chocolate", "QUESTION", "Kwisi food!", "Kwisi yum!"},
            {"Chicken", "QUESTION", "Kwisi food!", "Kwisi yum!"},
            {"Banana", "QUESTION", "Kwisi food!", "Kwisi yum!"},
            
            // Objects
            {"A telephone", "QUESTION", "Kwisi thing!", "Kwisi guess!"},
            {"A car", "QUESTION", "Kwisi thing!", "Kwisi guess!"},
            {"A book", "QUESTION", "Kwisi thing!", "Kwisi guess!"},
            {"A TV", "QUESTION", "Kwisi thing!", "Kwisi guess!"},
            {"A bicycle", "QUESTION", "Kwisi thing!", "Kwisi guess!"},
            
            // Places
            {"A beach", "QUESTION", "Kwisi place!", "Kwisi guess!"},
            {"A school", "QUESTION", "Kwisi place!", "Kwisi guess!"},
            {"A church", "QUESTION", "Kwisi place!", "Kwisi guess!"},
            {"A park", "QUESTION", "Kwisi place!", "Kwisi guess!"},
            {"A hospital", "QUESTION", "Kwisi place!", "Kwisi guess!"},
            
            // Verbs/Actions
            {"Dancing", "QUESTION", "Kwisi action!", "Kwisi guess!"},
            {"Sleeping", "QUESTION", "Kwisi action!", "Kwisi guess!"},
            {"Eating", "QUESTION", "Kwisi action!", "Kwisi guess!"},
            {"Running", "QUESTION", "Kwisi action!", "Kwisi guess!"},
            {"Swimming", "QUESTION", "Kwisi action!", "Kwisi guess!"},
            
            // Sports
            {"Football", "QUESTION", "Kwisi sport!", "Kwisi guess!"},
            {"Basketball", "QUESTION", "Kwisi sport!", "Kwisi guess!"},
            {"Tennis", "QUESTION", "Kwisi sport!", "Kwisi guess!"},
            {"Swimming", "QUESTION", "Kwisi sport!", "Kwisi guess!"},
            {"Golf", "QUESTION", "Kwisi sport!", "Kwisi guess!"}
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
