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
    public synchronized void recordAnswer(User user, Game game, String answerText, GameQuestion question) {
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        long count = gameAnswerRepository.countByUserAndGameAndAnswerDate(user, game, today);
        GameAnswer answer = new GameAnswer(user, game, question, answerText, today, (int)(count + 1));
        gameAnswerRepository.save(answer);
    }

    /**
     * Check if user has remaining attempts on today's game
     * Returns: 1 = can still play, 0 = no more attempts
     */
    public synchronized int getRemainingAttempts(User user, Game game) {
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        long count = gameAnswerRepository.countByUserAndGameAndAnswerDate(user, game, today);
        if (count >= 1) {
            return 0; // No more attempts
        }
        return (int)(1 - count); // Return remaining attempts
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
     * Get a trivia question for today's game.
     * If a user is provided, the selection is stable per user so each person
     * gets a consistent question from the same weekly pool.
     */
    public synchronized Optional<GameQuestion> getRandomQuestion(Game game) {
        return getRandomQuestion(game, null);
    }

    public synchronized Optional<GameQuestion> getRandomQuestion(Game game, User user) {
        List<GameQuestion> questions = gameQuestionRepository.findByGame(game);
        if (questions.isEmpty()) {
            return Optional.empty();
        }

        if (user == null) {
            int randomIndex = ThreadLocalRandom.current().nextInt(questions.size());
            return Optional.of(questions.get(randomIndex));
        }

        int stableIndex = Math.floorMod(Objects.hash(
                user.getId(),
                user.getUsername(),
                user.getFamilyMemberName(),
                game.getId()), questions.size());
        return Optional.of(questions.get(stableIndex));
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

        // Keep only the simpler family-friendly game days:
        // - Tuesday gets the old Monday game
        // - Friday gets the old Tuesday game
        // - Wednesday stays Wednesday
        Object[][] gameSchedule = {
            {"Trivia Challenge", 2, "TRIVIA"},
            {"Memory Match", 5, "MEMORY"},
            {"Spin & Dare", 3, "DARE"}
        };

        for (Object[] entry : gameSchedule) {
            String gameName = (String) entry[0];
            int dayOfWeek = (Integer) entry[1];
            String gameType = (String) entry[2];

            Game game = new Game(gameName, dayOfWeek, gameType, weekNumber, weekStartDate);
            gameRepository.save(game);
            
            // Generate content based on game type
            generateGameContent(game);
        }

        this.currentWeekNumber = weekNumber;
        this.weekStartDate = weekStartDate;
    }

    public synchronized void resetGamesForCurrentWeek() {
        List<Game> currentGames = gameRepository.findByWeekNumber(currentWeekNumber);
        for (Game game : currentGames) {
            gameAnswerRepository.deleteAll(gameAnswerRepository.findAll().stream()
                    .filter(answer -> answer.getGame() != null && answer.getGame().getId().equals(game.getId()))
                    .toList());
            gameScoreRepository.deleteAll(gameScoreRepository.findAll().stream()
                    .filter(score -> score.getGame() != null && score.getGame().getId().equals(game.getId()))
                    .toList());
            gameQuestionRepository.deleteAll(gameQuestionRepository.findByGame(game));
            gameChallengeRepository.deleteAll(gameChallengeRepository.findByGame(game));
        }
        gameRepository.deleteAll(currentGames);
        initializeWeekGames(currentWeekNumber, weekStartDate);
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
        String[][] allBibleVerses = {
            {"Kuko Imana yakunze isi cyane, yatanze Umwana wayo umwe, ngo umwizera wese atarimbuka ahubwo ahabwe ubugingo buhoraho.", "Yohana 3:16"},
            {"Uwiteka ni we mufasha wanjye; sinzagira icyo ntinya.", "Zaburi 23:1"},
            {"Nshobora byose mubimufasha.", "Aba-Filipi 4:13"},
            {"Muri byose Imana ikorera neza abamukunda.", "Abaroma 8:28"},
            {"Wizere Uwiteka n'umutima wawe wose, ntukishingikirize ku bwenge bwawe.", "Imigani 3:5"},
            {"Shaka ubwami bw'Imana n'ubugwaneza bwayo, ibindi byose bizongerwaho.", "Matayo 6:33"},
            {"Mfite imigambi myiza kuri mwe, si migambi y'ihirima", "Yeremiya 29:11"},
            {"Ukwishima kw'Umwami niwo ngabo yanjye.", "Zaburi 118:24"},
            {"Humura, nzaba kumwe nawe; uceceke, menya ko ndi Imana.", "Zaburi 46:10"},
            {"Abashaka Uwiteka bazahabwa imbaraga nk'iy'igishoro cy'ikibunga.", "Yesaya 40:31"},
            {"Umusaruro w'Umwuka ni urukundo, ibyishimo, amahoro...", "Abagalatiya 5:22"},
            {"Urukundo rufite ubwihangane, bukagira neza; ntirwiyemera...", "1 Abakorinto 13:4"},
            {"Mwihangane kandi mwizere, Uwiteka ni we utanga imbabazi.", "Zaburi 86:5"},
            {"Ntukareke gukora ibyiza; kuko igihe cyo gusarura kizagera.", "Aba-Hebulayo 6:10"},
            {"Mugire ubuntu, muhe ibyo mukwiye abandi.", "Abaefeso 4:32"},
            {"Mwizerane mu byo mukora byose, mukurikize inzira y'ukuri.", "Imigani 4:25"},
            {"Ntimukirengagize gukunda mugenzi wanyu nk'uko mwikunda.", "Matayo 22:39"},
            {"Ntimugire ubwoba; kuko ndi kumwe namwe.", "Yoshua 1:9"},
            {"Mubabarirane uko Imana yabababarije muri Kristo.", "Abakolosayi 3:13"},
            {"Mwishimire Uwiteka, kuko ari we Mana y'ubuntu.", "Zaburi 100:2"}
        };

        String[] questionTemplates = {
            "Which verse matches this line: \"%s\"?",
            "What is the reference for: \"%s\"?",
            "Choose the Bible verse reference for this statement: \"%s\".",
            "Which scripture goes with: \"%s\"?",
            "Select the verse reference for: \"%s\".",
            "What verse is this from: \"%s\"?",
            "Find the matching Bible verse for: \"%s\".",
            "Which reference belongs to: \"%s\"?",
            "Bible trivia: what reference matches \"%s\"?",
            "Complete the verse reference for: \"%s\"."
        };

        // Build 200 trivia questions by combining the verse pool with 10 prompt styles.
        // This keeps the game fresh while still drawing from familiar Bible content.
        List<String[]> versePool = new ArrayList<>(Arrays.asList(allBibleVerses));
        Collections.shuffle(versePool);

        int questionCount = 0;
        for (int templateIndex = 0; templateIndex < questionTemplates.length && questionCount < 200; templateIndex++) {
            for (int verseIndex = 0; verseIndex < versePool.size() && questionCount < 200; verseIndex++) {
                String[] verse = versePool.get(verseIndex);
                String questionText = String.format(questionTemplates[templateIndex], verse[0]);
                String correctAnswer = verse[1];

                List<String> incorrectAnswers = new ArrayList<>();
                for (String[] candidate : versePool) {
                    String candidateAnswer = candidate[1];
                    if (!candidateAnswer.equals(correctAnswer) && !incorrectAnswers.contains(candidateAnswer)) {
                        incorrectAnswers.add(candidateAnswer);
                    }
                    if (incorrectAnswers.size() == 3) {
                        break;
                    }
                }

                while (incorrectAnswers.size() < 3) {
                    incorrectAnswers.add("Zaburi " + (100 + incorrectAnswers.size()) + ":" + (incorrectAnswers.size() + 1));
                }

                List<String> options = new ArrayList<>();
                options.add(correctAnswer);
                options.addAll(incorrectAnswers);
                Collections.shuffle(options);

                GameQuestion question = new GameQuestion(
                        game,
                        questionText,
                        correctAnswer,
                        "Biblical",
                        "Kwisi verse",
                        options.get(0),
                        options.get(1),
                        options.get(2),
                        options.get(3)
                );
                gameQuestionRepository.save(question);
                questionCount++;
            }
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
