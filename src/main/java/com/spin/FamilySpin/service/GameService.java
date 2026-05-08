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
            // Community & Belonging
            {"What does it mean to feel 'at home' in a community?", "A place where you belong and are accepted for who you are", "Community", "Proverbs 27:12 - Home is where we find refuge"},
            {"What is the most important thing that builds trust between people?", "Honesty, consistency, and vulnerability", "Community", "Proverbs 13:3 - Guard your words carefully"},
            {"How can you help someone feel welcomed?", "Listen to their story, show genuine interest, remember their name", "Community", "Hebrews 13:2 - Welcome strangers"},
            {"What does it mean to truly listen to someone?", "Understand their perspective without planning your response", "Community", "James 1:19 - Be quick to listen, slow to speak"},
            {"What is one way diversity strengthens a group?", "Different perspectives, experiences, and ideas", "Community", "1 Corinthians 12:12-13 - Many parts, one body"},
            
            // Real Life Challenges
            {"What is the biggest challenge university students face today?", "Mental health, loneliness, financial pressure, or academic stress", "Real Life", "Philippians 4:6-7 - Cast your cares on God"},
            {"How do you handle a conflict with someone you care about?", "Listen, apologize if wrong, seek to understand, find compromise", "Real Life", "Matthew 5:24 - Be reconciled with your brother"},
            {"What does it mean to have financial responsibility?", "Budgeting, saving, not overspending, understanding your limits", "Real Life", "Proverbs 21:5 - The plans of the diligent lead to profit"},
            {"What is the most important skill for your career future?", "Communication, problem-solving, adaptability, or continuous learning", "Career", "Proverbs 22:29 - Do you see someone skilled in their work"},
            {"How do you maintain mental health during stressful times?", "Exercise, sleep, therapy, talking to friends, or spiritual practices", "Real Life", "Philippians 4:8 - Think on these things"},
            
            // Personal Growth & Resilience
            {"What role does failure play in personal growth?", "It teaches resilience, reveals weaknesses, and builds character", "Personal Growth", "Romans 5:3-4 - Suffering produces perseverance"},
            {"What is true friendship based on?", "Trust, honesty, vulnerability, and mutual support through good and bad", "Relationships", "Proverbs 27:17 - Iron sharpens iron"},
            {"How do you decide what matters most in your life?", "Values, long-term goals, relationships, and what brings genuine fulfillment", "Real Life", "Colossians 3:15 - Let peace guide your decisions"},
            {"What does it mean to be authentic?", "Being true to yourself, not pretending or wearing masks", "Personal Growth", "Proverbs 12:17 - The honest witness tells the truth"},
            {"How do you recover from disappointment?", "Allow yourself to feel it, seek support, find meaning in it", "Personal Growth", "2 Corinthians 4:8-9 - Pressed but not crushed"},
            
            // Relationships & Connection
            {"What makes a friendship last?", "Effort, honesty, forgiveness, and consistent support", "Relationships", "Proverbs 17:17 - A friend loves at all times"},
            {"How do you show someone they matter to you?", "Remember details, celebrate wins, show up in hard times", "Relationships", "1 Thessalonians 5:11 - Encourage one another"},
            {"What is the hardest part of being vulnerable?", "Fear of rejection, judgment, or being hurt", "Relationships", "1 Peter 5:7 - Cast all your cares on Him"},
            {"How do you help a friend who is struggling?", "Listen without judging, offer practical help, just be present", "Relationships", "Galatians 6:2 - Carry each other's burdens"},
            {"What does forgiveness really mean?", "Letting go of anger and choosing to move forward", "Relationships", "Colossians 3:13 - Forgive as the Lord forgave you"},
            
            // Social & Cultural
            {"What is one way social media affects real relationships?", "Can create comparison, surface-level connection, or distance from genuine interaction", "Real Life", "Psalm 26:4 - I do not sit with the deceitful"},
            {"How does culture shape who we are?", "Values, beliefs, traditions, language, worldview", "Culture", "Psalm 139:14 - I am fearfully and wonderfully made"},
            {"What does it mean to be proud of your heritage?", "Understanding your roots, honoring traditions, sharing stories", "Culture", "Deuteronomy 6:6 - These commandments are upon your hearts"},
            {"How can you bridge cultural differences?", "Listen, ask questions, try new things, show respect", "Culture", "1 Peter 3:8 - Live in harmony with one another"},
            {"What is the value of storytelling in a community?", "Connects people, preserves history, builds understanding", "Community", "Psalm 78:4 - Tell to the coming generation"},
            
            // Purpose & Direction
            {"What helps you discover your purpose?", "Self-reflection, trying new things, listening to others", "Personal Growth", "Jeremiah 29:11 - Plans for good, not harm"},
            {"How do you know when you're on the right path?", "Inner peace, alignment with values, positive impact", "Personal Growth", "Proverbs 16:9 - The Lord establishes our steps"},
            {"What does success mean to you?", "Making a difference, meaningful relationships, personal fulfillment", "Real Life", "1 Timothy 6:6 - Godliness with contentment is great gain"},
            {"How do you balance ambition with contentment?", "Set goals but find joy in the present", "Personal Growth", "Philippians 4:11 - I have learned to be content"},
            {"What is one dream you're afraid to pursue?", "Common answer - many have similar fears", "Real Life", "Joshua 1:9 - Be strong and courageous"},
            
            // Family & Home
            {"What does 'home' mean beyond just a place?", "Belonging, safety, acceptance, unconditional support", "Family", "Proverbs 14:1 - The wise woman builds her house"},
            {"How do families stay connected across distance?", "Regular communication, shared experiences, intentional effort", "Family", "Philippians 1:3-4 - I thank my God and remember you"},
            {"What is one valuable lesson from your family?", "Varies by person - rich diversity of answers", "Family", "Proverbs 22:6 - Train a child in the way they should go"},
            {"How do you handle differences with family members?", "Respect, communication, finding common ground", "Family", "Proverbs 15:1 - A gentle answer turns away wrath"},
            {"What role does loyalty play in family?", "Foundation of trust, showing up, supporting each other", "Family", "Ruth 3:11 - All the people know you are a woman of worth"},
            
            // Service & Impact
            {"How can you make a positive impact in your community?", "Listen, help, volunteer, share skills", "Service", "Matthew 5:16 - Let your light shine before others"},
            {"What does it mean to serve others?", "Putting their needs before your own, with a willing heart", "Service", "Galatians 5:13 - Serve one another in love"},
            {"How do you show compassion to someone suffering?", "Listen, validate, offer practical help, pray", "Service", "1 Thessalonians 5:14 - Encourage the timid, help the weak"},
            {"What is the difference between pity and compassion?", "Compassion moves you to action, pity is passive", "Service", "1 John 3:17 - If you see someone in need, help them"},
            {"How does gratitude change your perspective?", "Shifts focus from what you lack to what you have", "Personal Growth", "1 Thessalonians 5:18 - Give thanks in all circumstances"},
            
            // Wisdom & Learning
            {"What is the most valuable thing you've learned from someone else?", "Varies - wisdom comes from many sources", "Learning", "Proverbs 1:5 - Let the wise listen and add to their learning"},
            {"How do you know when you need to ask for help?", "When you're stuck, exhausted, or out of your depth", "Real Life", "Proverbs 15:22 - Plans fail for lack of counsel"},
            {"What does it mean to have a growth mindset?", "Believing you can improve through effort and learning", "Personal Growth", "Philippians 4:8 - Whatever is noble, think about such things"},
            {"How do you respond when you don't know the answer?", "Ask, admit, research, learn", "Learning", "Proverbs 12:15 - The wise listen and add to their learning"},
            {"What is the connection between humility and wisdom?", "Humility opens you to learning from others", "Wisdom", "Proverbs 11:2 - When pride comes, then comes disgrace"},
            
            // Joy & Celebration
            {"What brings you genuine joy?", "Relationships, accomplishments, experiences, laughter", "Joy", "Psalm 16:11 - Joy in God's presence"},
            {"How do you celebrate someone else's success?", "Genuinely, enthusiastically, with your presence", "Relationships", "Romans 12:15 - Rejoice with those who rejoice"},
            {"What is one simple pleasure you often overlook?", "Varies - encourages reflection on gratitude", "Joy", "Proverbs 17:22 - A joyful heart is good medicine"},
            {"How does laughter strengthen relationships?", "Creates connection, relieves tension, builds memories", "Relationships", "Proverbs 31:25 - She laughs at the days to come"},
            {"What moments make you feel most alive?", "Varies - celebration of individual passion and purpose", "Joy", "John 10:10 - I have come that you may have life to the full"}
        };

        for (String[] data : triviaData) {
            GameQuestion q = new GameQuestion(game, data[0], data[1], data[2], data[3]);
            gameQuestionRepository.save(q);
        }
    }

    private void generateDareChallenges(Game game) {
        String[][] dareData = {
            // Emotional Vulnerability
            {"Share your biggest academic or career fear", "DARE", "Philippians 4:6 - Do not be anxious, present your requests to God", "Kwisi authenticity!"},
            {"Tell someone a mistake you made and what you learned", "DARE", "Proverbs 12:1 - Whoever loves discipline loves knowledge", "Kwisi growth!"},
            {"Share how you've struggled with mental health or stress", "DARE", "2 Corinthians 12:9 - My grace is sufficient for you", "Kwisi vulnerability!"},
            {"Give someone advice based on your real-life experience", "DARE", "Proverbs 27:12 - The prudent see danger and take refuge", "Kwisi wisdom!"},
            {"Share a time you felt lonely or out of place", "DARE", "Psalm 23:4 - You are with me in the valley", "Kwisi support!"},
            
            // Personal Sharing
            {"Tell a story from your childhood that shaped you", "DARE", "Proverbs 22:6 - Train a child in the way they should go", "Kwisi origins!"},
            {"Share your hidden talent or skill nobody knows about", "DARE", "1 Peter 4:10 - Each of you should use your gifts", "Kwisi discovery!"},
            {"Tell us what you're most proud of about yourself", "DARE", "Psalm 139:14 - I am fearfully and wonderfully made", "Kwisi pride!"},
            {"Share something you've been wanting to say but haven't", "DARE", "Ephesians 4:15 - Speak the truth in love", "Kwisi courage!"},
            {"Tell someone something you admire about them", "DARE", "Proverbs 16:24 - Gracious words are like honey", "Kwisi affirmation!"},
            
            // Challenging Growth
            {"Challenge someone to pursue a goal they've been avoiding", "DARE", "Proverbs 31:8 - Speak up for those who have no voice", "Kwisi encouragement!"},
            {"Share what you would do if fear wasn't a factor", "DARE", "Joshua 1:9 - Be strong and courageous", "Kwisi boldness!"},
            {"Tell someone how they've impacted your life positively", "DARE", "1 Thessalonians 5:11 - Encourage one another", "Kwisi impact!"},
            {"Share a dream you're working toward", "DARE", "Proverbs 29:18 - Where there is no vision, the people perish", "Kwisi vision!"},
            {"Tell us about a time you changed your mind about someone", "DARE", "Proverbs 14:15 - The simple believe anything, but the prudent give thought", "Kwisi reflection!"},
            
            // Connection Building
            {"Share a time someone's kindness changed your day", "DARE", "Ephesians 4:32 - Be kind to one another", "Kwisi gratitude!"},
            {"Tell someone why you value their friendship", "DARE", "Proverbs 27:17 - Iron sharpens iron", "Kwisi bonds!"},
            {"Share what 'home' means to you", "DARE", "John 14:1-3 - I go to prepare a place for you", "Kwisi belonging!"},
            {"Tell us about a mentor or person who inspired you", "DARE", "Proverbs 27:12 - The wise see danger and take refuge", "Kwisi wisdom!"},
            {"Share a moment when you felt truly accepted", "DARE", "Romans 15:7 - Accept one another as Christ accepted you", "Kwisi acceptance!"},
            
            // Brave Acts
            {"Give a genuine apology if you've hurt someone", "DARE", "Matthew 5:24 - Be reconciled with your brother", "Kwisi peace!"},
            {"Offer forgiveness to someone who wronged you", "DARE", "Colossians 3:13 - Forgive as the Lord forgave you", "Kwisi release!"},
            {"Start a 1-on-1 coffee date with someone new", "DARE", "Hebrews 13:2 - Welcome strangers", "Kwisi connection!"},
            {"Share a compliment with three people here", "DARE", "Proverbs 16:24 - Gracious words are like honey", "Kwisi abundance!"},
            {"Tell someone exactly how you feel in one sentence", "DARE", "Ephesians 4:15 - Speak the truth in love", "Kwisi honesty!"},
            
            // Deep Reflection
            {"Share what you're grateful for despite challenges", "DARE", "1 Thessalonians 5:18 - Give thanks in all circumstances", "Kwisi perspective!"},
            {"Tell us about a time you felt God's presence", "DARE", "Psalm 139:7-10 - Nowhere can I go from your Spirit", "Kwisi faith!"},
            {"Share what you wish people understood about you", "DARE", "1 John 4:7 - Love one another", "Kwisi understanding!"},
            {"Tell someone how they helped you become stronger", "DARE", "2 Timothy 1:7 - God has not given us a spirit of fear", "Kwisi strength!"},
            {"Share a moment when community meant everything", "DARE", "Ecclesiastes 4:9 - Two are better than one", "Kwisi belonging!"}
        };

        for (String[] data : dareData) {
            GameChallenge c = new GameChallenge(game, data[0], data[1], data[2], data[3]);
            gameChallengeRepository.save(c);
        }
    }

    private void generateCharadesChallenges(Game game) {
        String[][] charadesData = {
            // Student Life
            {"Cramming for an exam the night before", "CHARADES", "Proverbs 21:5 - The plans of the diligent lead to profit", "Kwisi reality!"},
            {"Getting rejected from your dream job", "CHARADES", "Romans 5:3 - We also glory in our sufferings", "Kwisi resilience!"},
            {"Finally understanding a difficult concept", "CHARADES", "Proverbs 18:15 - The heart of the discerning acquires knowledge", "Kwisi breakthrough!"},
            {"Pretending to be fine when you're struggling", "CHARADES", "Matthew 11:28 - Come to me all who are weary", "Kwisi honesty!"},
            {"Realizing you made a wrong choice", "CHARADES", "Proverbs 14:12 - There is a way that appears right but leads to death", "Kwisi correction!"},
            
            // Emotional Moments
            {"Supporting a friend through a difficult time", "CHARADES", "1 Thessalonians 5:11 - Encourage one another", "Kwisi presence!"},
            {"Stepping out of your comfort zone for the first time", "CHARADES", "Joshua 1:9 - Be strong and courageous", "Kwisi courage!"},
            {"Discovering a hidden talent within yourself", "CHARADES", "1 Peter 4:10 - Each of you should use your gifts", "Kwisi discovery!"},
            {"Feeling lonely in a crowded room", "CHARADES", "Psalm 25:16 - Turn to me and be gracious", "Kwisi connection!"},
            {"Realizing you've grown as a person", "CHARADES", "2 Corinthians 5:17 - All things become new", "Kwisi transformation!"},
            
            // Relationship Moments
            {"Reconciling with someone after conflict", "CHARADES", "Matthew 5:24 - Be reconciled with your brother", "Kwisi peace!"},
            {"Meeting someone who becomes your best friend", "CHARADES", "Proverbs 13:20 - Walk with the wise", "Kwisi destiny!"},
            {"Forgiving someone who hurt you deeply", "CHARADES", "Colossians 3:13 - Forgive as the Lord forgave", "Kwisi grace!"},
            {"Celebrating a loved one's success", "CHARADES", "Romans 12:15 - Rejoice with those who rejoice", "Kwisi joy!"},
            {"Finally asking someone for help", "CHARADES", "Proverbs 15:22 - Plans fail for lack of counsel", "Kwisi humility!"},
            
            // Personal Growth
            {"Overcoming a fear you've had for years", "CHARADES", "2 Timothy 1:7 - God has not given us a spirit of fear", "Kwisi strength!"},
            {"Learning from your biggest mistake", "CHARADES", "Proverbs 12:1 - Whoever loves discipline loves knowledge", "Kwisi wisdom!"},
            {"Finding your life's purpose", "CHARADES", "Jeremiah 29:11 - I have plans for you, plans for good", "Kwisi direction!"},
            {"Being vulnerable and getting accepted anyway", "CHARADES", "1 John 4:7 - Love one another", "Kwisi belonging!"},
            {"Letting go of something that no longer serves you", "CHARADES", "Philippians 3:13 - Forget what is behind, press on", "Kwisi release!"},
            
            // Community & Belonging
            {"Finding your place in a new community", "CHARADES", "Proverbs 27:12 - The wise see danger and take refuge", "Kwisi home!"},
            {"Experiencing unexpected kindness", "CHARADES", "Ephesians 4:32 - Be kind to one another", "Kwisi grace!"},
            {"Being truly heard and understood", "CHARADES", "James 1:19 - Be quick to listen", "Kwisi validation!"},
            {"Sharing your authentic self", "CHARADES", "Proverbs 12:17 - The honest witness tells the truth", "Kwisi authenticity!"},
            {"Finding strength in your community", "CHARADES", "Ecclesiastes 4:9 - Two are better than one", "Kwisi unity!"}
        };

        for (String[] data : charadesData) {
            GameChallenge c = new GameChallenge(game, data[0], data[1], data[2], data[3]);
            gameChallengeRepository.save(c);
        }
    }

    private void generateWordChallenges(Game game) {
        String[][] wordData = {
            // Life Challenges
            {"Uncertainty", "WORD", "Proverbs 3:5-6 - Trust in the Lord with all your heart", "Kwisi growth!"},
            {"Exhaustion", "WORD", "Matthew 11:28 - Come to me and I will give you rest", "Kwisi rest!"},
            {"Transformation", "WORD", "2 Corinthians 5:17 - If anyone is in Christ, they are new", "Kwisi renewal!"},
            {"Courage", "WORD", "Deuteronomy 31:6 - Be strong and courageous", "Kwisi strength!"},
            {"Purpose", "WORD", "Jeremiah 29:11 - I have plans for you, plans for good", "Kwisi direction!"},
            
            // Character & Values
            {"Resilience", "WORD", "James 1:2-3 - Consider it joy to face trials", "Kwisi perseverance!"},
            {"Authenticity", "WORD", "Proverbs 12:17 - The honest witness tells the truth", "Kwisi real!"},
            {"Balance", "WORD", "Ecclesiastes 3:1 - There is a time for everything", "Kwisi harmony!"},
            {"Growth", "WORD", "2 Peter 3:18 - Grow in the grace and knowledge", "Kwisi development!"},
            {"Wisdom", "WORD", "Proverbs 1:5 - Let the wise listen and add to learning", "Kwisi insight!"},
            
            // Relationships
            {"Connection", "WORD", "1 Thessalonians 5:11 - Encourage one another", "Kwisi bonds!"},
            {"Forgiveness", "WORD", "Colossians 3:13 - Forgive as the Lord forgave you", "Kwisi peace!"},
            {"Vulnerability", "WORD", "2 Corinthians 12:9 - My grace is sufficient", "Kwisi openness!"},
            {"Belonging", "WORD", "Romans 12:5 - In Christ we are all one body", "Kwisi unity!"},
            {"Compassion", "WORD", "1 John 3:17 - If you see someone in need, help", "Kwisi care!"},
            
            // Spiritual
            {"Faith", "WORD", "Hebrews 11:1 - Faith is confidence in what we hope for", "Kwisi belief!"},
            {"Grace", "WORD", "Ephesians 2:8 - By grace you have been saved", "Kwisi mercy!"},
            {"Hope", "WORD", "Romans 15:13 - May the God of hope fill you", "Kwisi future!"},
            {"Joy", "WORD", "Nehemiah 8:10 - The joy of the Lord is your strength", "Kwisi celebration!"},
            {"Peace", "WORD", "Philippians 4:7 - The peace of God guards your heart", "Kwisi calm!"},
            
            // Community
            {"Home", "WORD", "Proverbs 14:1 - The wise woman builds her house", "Kwisi belonging!"},
            {"Family", "WORD", "Psalm 68:6 - God sets the lonely in families", "Kwisi kinship!"},
            {"Community", "WORD", "Acts 2:44 - All believers were together", "Kwisi togetherness!"},
            {"Service", "WORD", "Galatians 5:13 - Serve one another in love", "Kwisi giving!"},
            {"Leadership", "WORD", "1 Peter 5:2-3 - Be a shepherd of God's flock", "Kwisi influence!"},
            
            // Personal Development
            {"Excellence", "WORD", "Colossians 3:17 - Do it all in the name of the Lord", "Kwisi quality!"},
            {"Gratitude", "WORD", "1 Thessalonians 5:18 - Give thanks in all circumstances", "Kwisi appreciation!"},
            {"Humility", "WORD", "Proverbs 11:2 - Humility comes before honor", "Kwisi modesty!"},
            {"Integrity", "WORD", "Proverbs 10:9 - The righteous person walks securely", "Kwisi honesty!"},
            {"Patience", "WORD", "Proverbs 14:29 - The patient person has great understanding", "Kwisi endurance!"},
            
            // Emotions
            {"Hope", "WORD", "Psalm 42:11 - Why are you downcast, O my soul", "Kwisi optimism!"},
            {"Courage", "WORD", "Joshua 1:9 - Be strong and courageous", "Kwisi boldness!"},
            {"Love", "WORD", "1 John 4:7-8 - We love because we are loved", "Kwisi affection!"},
            {"Joy", "WORD", "Psalm 16:11 - You fill me with joy", "Kwisi happiness!"},
            {"Peace", "WORD", "John 14:27 - My peace I give to you", "Kwisi serenity!"}
        };

        for (String[] data : wordData) {
            GameChallenge c = new GameChallenge(game, data[0], data[1], data[2], data[3]);
            gameChallengeRepository.save(c);
        }
    }

    private void generateQuestionsChallenges(Game game) {
        String[][] questionData = {
            // Deep Personal Moments
            {"Something you desperately needed but was afraid to ask for", "QUESTION", "Philippians 4:6 - Present your requests to God", "Kwisi vulnerability!"},
            {"A difficult decision that changed the course of your life", "QUESTION", "Proverbs 16:9 - The Lord establishes the steps of the godly", "Kwisi choice!"},
            {"A time you had to start over from scratch", "QUESTION", "Lamentations 3:22-23 - His mercies are new every morning", "Kwisi beginning!"},
            {"Something you gave up to become who you are today", "QUESTION", "Matthew 16:25 - Whoever loses their life for me will find it", "Kwisi sacrifice!"},
            {"A moment when you realized your strength", "QUESTION", "Philippians 4:13 - I can do all things through Christ", "Kwisi power!"},
            
            // Growth & Learning
            {"Someone who believed in you when you didn't believe in yourself", "QUESTION", "Proverbs 27:12 - The prudent see danger and take refuge", "Kwisi gratitude!"},
            {"A lesson you learned the hard way", "QUESTION", "Proverbs 12:1 - Whoever loves discipline loves knowledge", "Kwisi wisdom!"},
            {"A mistake that became your greatest teacher", "QUESTION", "Romans 5:3 - Suffering produces perseverance", "Kwisi growth!"},
            {"A time you had to forgive yourself", "QUESTION", "1 John 1:9 - If we confess our sins, He forgives", "Kwisi release!"},
            {"Something you wish you'd known earlier in life", "QUESTION", "Proverbs 20:5 - The purposes of a person's heart are deep waters", "Kwisi insight!"},
            
            // Community & Connection
            {"A moment when community meant everything", "QUESTION", "Ecclesiastes 4:9 - Two are better than one", "Kwisi belonging!"},
            {"Someone whose presence changed everything for you", "QUESTION", "Proverbs 27:17 - Iron sharpens iron", "Kwisi impact!"},
            {"A time when you felt truly seen and understood", "QUESTION", "1 John 4:7 - Let us love one another", "Kwisi validation!"},
            {"A friendship that surprised you in the best way", "QUESTION", "Proverbs 17:17 - A friend loves at all times", "Kwisi connection!"},
            {"Something you learned from someone unexpected", "QUESTION", "Proverbs 1:5 - Let the wise listen and add to learning", "Kwisi openness!"},
            
            // Overcoming Challenges
            {"Your biggest fear and how you're facing it", "QUESTION", "Joshua 1:9 - Be strong and courageous", "Kwisi courage!"},
            {"A time you felt completely overwhelmed but pushed through", "QUESTION", "2 Corinthians 4:8-9 - Pressed but not crushed", "Kwisi resilience!"},
            {"Something everyone thinks is easy but is hard for you", "QUESTION", "2 Corinthians 12:9 - My grace is sufficient for you", "Kwisi honesty!"},
            {"A battle you're still fighting", "QUESTION", "Ephesians 6:10 - Be strong in the Lord", "Kwisi strength!"},
            {"A way you've had to redefine success for yourself", "QUESTION", "1 Timothy 6:6 - Godliness with contentment is gain", "Kwisi perspective!"},
            
            // Purpose & Direction
            {"A dream that scares and excites you", "QUESTION", "Jeremiah 29:11 - I have plans for you, plans for good", "Kwisi vision!"},
            {"Something you're working toward and why it matters", "QUESTION", "Proverbs 29:18 - Where there is no vision, the people perish", "Kwisi purpose!"},
            {"A way you want to impact your community", "QUESTION", "Matthew 5:16 - Let your light shine", "Kwisi influence!"},
            {"Something you haven't done yet but want to", "QUESTION", "Proverbs 20:4 - The sluggard craves but gets nothing", "Kwisi aspiration!"},
            {"A version of yourself you're still becoming", "QUESTION", "2 Peter 3:18 - Grow in grace and knowledge", "Kwisi transformation!"},
            
            // Family & Home
            {"What home means to you beyond just a place", "QUESTION", "John 14:1-3 - I go to prepare a place for you", "Kwisi belonging!"},
            {"A family tradition that shaped you", "QUESTION", "Proverbs 22:6 - Train a child in the way they should go", "Kwisi roots!"},
            {"Someone in your family who inspires you", "QUESTION", "Proverbs 27:12 - The wise see danger and take refuge", "Kwisi role model!"},
            {"A sacrifice your family made for you", "QUESTION", "John 15:13 - Greater love has no one than this", "Kwisi appreciation!"},
            {"Something you want to pass down to the next generation", "QUESTION", "Deuteronomy 6:6 - These commandments on your heart", "Kwisi legacy!"}
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
