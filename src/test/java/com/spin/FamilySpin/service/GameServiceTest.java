package com.spin.FamilySpin.service;

import com.spin.FamilySpin.model.*;
import com.spin.FamilySpin.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("GameService Unit Tests")
class GameServiceTest {

    private GameService gameService;

    @Mock
    private GameRepository gameRepository;

    @Mock
    private GameScoreRepository gameScoreRepository;

    @Mock
    private GameQuestionRepository gameQuestionRepository;

    @Mock
    private GameChallengeRepository gameChallengeRepository;

    @Mock
    private GameAnswerRepository gameAnswerRepository;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Mock current week games initialization
        when(gameRepository.findByWeekNumber(anyInt())).thenReturn(Collections.emptyList());
        
        gameService = new GameService(
            gameRepository,
            gameScoreRepository,
            gameQuestionRepository,
            gameChallengeRepository,
            gameAnswerRepository
        );
    }

    @Test
    @DisplayName("Should return remaining attempts as 1 when user has not answered today")
    void testGetRemainingAttemptsWhenNoAnswerToday() {
        // Arrange
        User user = createTestUser(1L, "testUser");
        Game game = createTestGame(1L, "Trivia Challenge");
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        
        when(gameAnswerRepository.countByUserAndGameAndAnswerDate(user, game, today)).thenReturn(0L);
        
        // Act
        int remainingAttempts = gameService.getRemainingAttempts(user, game);
        
        // Assert
        assertEquals(1, remainingAttempts, "Should have 1 remaining attempt when no answers recorded");
    }

    @Test
    @DisplayName("Should return 0 remaining attempts when user has already answered once today")
    void testGetRemainingAttemptsWhenAlreadyAnswered() {
        // Arrange
        User user = createTestUser(1L, "testUser");
        Game game = createTestGame(1L, "Trivia Challenge");
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        
        when(gameAnswerRepository.countByUserAndGameAndAnswerDate(user, game, today)).thenReturn(1L);
        
        // Act
        int remainingAttempts = gameService.getRemainingAttempts(user, game);
        
        // Assert
        assertEquals(0, remainingAttempts, "Should have 0 remaining attempts after one answer");
    }

    @Test
    @DisplayName("Should add score for new user-game combination")
    void testAddScoreForNewUserGame() {
        // Arrange
        User user = createTestUser(1L, "testUser");
        Game game = createTestGame(1L, "Trivia Challenge");
        int points = 20;
        int weekNumber = 1;
        
        when(gameScoreRepository.findByUserAndGame(user, game)).thenReturn(Optional.empty());
        
        // Act
        gameService.addScore(user, game, points, weekNumber);
        
        // Assert
        verify(gameScoreRepository, times(1)).save(any(GameScore.class));
    }

    @Test
    @DisplayName("Should add points to existing score")
    void testAddScoreToExistingScore() {
        // Arrange
        User user = createTestUser(1L, "testUser");
        Game game = createTestGame(1L, "Trivia Challenge");
        GameScore existingScore = new GameScore(user, game, 40, 1);
        int additionalPoints = 20;
        
        when(gameScoreRepository.findByUserAndGame(user, game)).thenReturn(Optional.of(existingScore));
        when(gameScoreRepository.save(any(GameScore.class))).thenReturn(existingScore);
        
        // Act
        gameService.addScore(user, game, additionalPoints, 1);
        
        // Assert
        assertEquals(60, existingScore.getScore(), "Score should be incremented by 20 points");
        verify(gameScoreRepository, times(1)).save(existingScore);
    }

    @Test
    @DisplayName("Should record answer successfully")
    void testRecordAnswer() {
        // Arrange
        User user = createTestUser(1L, "testUser");
        Game game = createTestGame(1L, "Trivia Challenge");
        GameQuestion question = createTestGameQuestion(game, "Sample Question", "Correct Answer");
        String answer = "Correct Answer";
        
        // Act
        gameService.recordAnswer(user, game, answer, question);
        
        // Assert
        verify(gameAnswerRepository, times(1)).save(any(GameAnswer.class));
    }

    @Test
    @DisplayName("Should get random dare challenge")
    void testGetRandomDareChallenge() {
        // Arrange
        Game game = createTestGame(1L, "Spin & Dare");
        GameChallenge challenge = new GameChallenge(game, "Test Challenge", "DARE", "Kwisi!", "Kwisi!");
        
        when(gameChallengeRepository.findByGame(game)).thenReturn(List.of(challenge));
        
        // Act
        Optional<GameChallenge> result = gameService.getRandomChallenge(game);
        
        // Assert
        assertTrue(result.isPresent(), "Should return a dare challenge");
        assertEquals("Test Challenge", result.get().getChallenge());
    }

    @Test
    @DisplayName("Should return empty optional when no challenges available")
    void testGetRandomDareChallengeWhenEmpty() {
        // Arrange
        Game game = createTestGame(1L, "Spin & Dare");
        
        when(gameChallengeRepository.findByGame(game)).thenReturn(Collections.emptyList());
        
        // Act
        Optional<GameChallenge> result = gameService.getRandomChallenge(game);
        
        // Assert
        assertTrue(result.isEmpty(), "Should return empty optional when no challenges");
    }

    @Test
    @DisplayName("Should get game by ID")
    void testGetGameById() {
        // Arrange
        Game game = createTestGame(1L, "Trivia Challenge");
        
        when(gameRepository.findById(1L)).thenReturn(Optional.of(game));
        
        // Act
        Optional<Game> result = gameService.getGameById(1L);
        
        // Assert
        assertTrue(result.isPresent(), "Should find game by ID");
        assertEquals("Trivia Challenge", result.get().getName());
    }

    @Test
    @DisplayName("Should return empty optional for non-existent game ID")
    void testGetGameByIdNotFound() {
        // Arrange
        when(gameRepository.findById(999L)).thenReturn(Optional.empty());
        
        // Act
        Optional<Game> result = gameService.getGameById(999L);
        
        // Assert
        assertTrue(result.isEmpty(), "Should return empty optional for non-existent game");
    }

    @Test
    @DisplayName("Should get current week games")
    void testGetCurrentWeekGames() {
        // Arrange
        Game game1 = createTestGame(1L, "Trivia Challenge");
        Game game2 = createTestGame(2L, "Spin & Dare");
        
        when(gameRepository.findByWeekNumber(anyInt())).thenReturn(List.of(game1, game2));
        
        // Act
        List<Game> result = gameService.getCurrentWeekGames();
        
        // Assert
        assertFalse(result.isEmpty(), "Should return games for current week");
    }

    // Helper methods
    
    private User createTestUser(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setFamilyMemberName(username);
        user.setPassword("password");
        return user;
    }

    private Game createTestGame(Long id, String name) {
        Game game = new Game();
        game.setId(id);
        game.setName(name);
        game.setGameType("TRIVIA");
        game.setDayOfWeek(2);
        game.setWeekNumber(1);
        return game;
    }

    private GameAnswer createTestGameAnswer(User user, Game game) {
        GameAnswer answer = new GameAnswer();
        answer.setUser(user);
        answer.setGame(game);
        answer.setAnswerText("Test Answer");
        answer.setAnswerDate(LocalDate.now(ZoneId.systemDefault()));
        answer.setAttemptNumber(1);
        return answer;
    }

    private GameQuestion createTestGameQuestion(Game game, String question, String correctAnswer) {
        GameQuestion q = new GameQuestion();
        q.setGame(game);
        q.setQuestion(question);
        q.setAnswer(correctAnswer);
        q.setCategory("Test");
        q.setOption1(correctAnswer);
        q.setOption2("Option 2");
        q.setOption3("Option 3");
        q.setOption4("Option 4");
        return q;
    }
}
