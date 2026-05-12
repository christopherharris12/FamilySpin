package com.spin.FamilySpin.controller;

import com.spin.FamilySpin.model.*;
import com.spin.FamilySpin.repository.UserRepository;
import com.spin.FamilySpin.repository.GameQuestionRepository;
import com.spin.FamilySpin.service.GameService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.server.ResponseStatusException;
import jakarta.servlet.http.HttpSession;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("GameController Unit Tests")
class GameControllerTest {

    private GameController gameController;

    @Mock
    private GameService gameService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private GameQuestionRepository gameQuestionRepository;

    @Mock
    private HttpSession httpSession;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        gameController = new GameController(gameService, userRepository, gameQuestionRepository);
    }

    @Test
    @DisplayName("Should return today's game")
    void testGetTodayGame() {
        // Arrange
        Game game = createTestGame(1L, "Trivia Challenge", "TRIVIA");
        when(gameService.getTodayGame()).thenReturn(Optional.of(game));
        
        // Act
        Map<String, Object> result = gameController.getTodayGame();
        
        // Assert
        assertNotNull(result, "Result should not be null");
        assertEquals(1L, result.get("id"), "Game ID should match");
        assertEquals("Trivia Challenge", result.get("name"), "Game name should match");
        assertEquals("TRIVIA", result.get("gameType"), "Game type should match");
    }

    @Test
    @DisplayName("Should throw exception when no game scheduled for today")
    void testGetTodayGameNotFound() {
        // Arrange
        when(gameService.getTodayGame()).thenReturn(Optional.empty());
        
        // Act & Assert
        assertThrows(ResponseStatusException.class, () -> {
            gameController.getTodayGame();
        }, "Should throw ResponseStatusException when no game found");
    }

    @Test
    @DisplayName("Should return week games")
    void testGetWeekGames() {
        // Arrange
        Game game1 = createTestGame(1L, "Trivia Challenge", "TRIVIA");
        Game game2 = createTestGame(2L, "Spin & Dare", "DARE");
        List<Game> games = List.of(game1, game2);
        when(gameService.getCurrentWeekGames()).thenReturn(games);
        
        // Act
        List<Game> result = gameController.getWeekGames();
        
        // Assert
        assertNotNull(result, "Result should not be null");
        assertEquals(2, result.size(), "Should return 2 games");
        assertTrue(result.stream().anyMatch(g -> g.getName().equals("Trivia Challenge")));
        assertTrue(result.stream().anyMatch(g -> g.getName().equals("Spin & Dare")));
    }

    @Test
    @DisplayName("Should return empty list when no week games")
    void testGetWeekGamesEmpty() {
        // Arrange
        when(gameService.getCurrentWeekGames()).thenReturn(Collections.emptyList());
        
        // Act
        List<Game> result = gameController.getWeekGames();
        
        // Assert
        assertTrue(result.isEmpty(), "Should return empty list when no games");
    }

    @Test
    @DisplayName("Should check if game is playable")
    void testIsGamePlayable() {
        // Arrange
        Game game = createTestGame(1L, "Trivia Challenge", "TRIVIA");
        when(gameService.getTodayGame()).thenReturn(Optional.of(game));
        
        // Act
        Map<String, Object> result = gameController.isGamePlayable(1L);
        
        // Assert
        assertTrue((Boolean) result.get("playable"), "Game should be playable");
    }

    @Test
    @DisplayName("Should return not playable for different game")
    void testIsGameNotPlayable() {
        // Arrange
        Game game = createTestGame(1L, "Trivia Challenge", "TRIVIA");
        when(gameService.getTodayGame()).thenReturn(Optional.of(game));
        
        // Act
        Map<String, Object> result = gameController.isGamePlayable(999L);
        
        // Assert
        assertFalse((Boolean) result.get("playable"), "Different game should not be playable");
    }

    @Test
    @DisplayName("Should return not playable when no game today")
    void testIsGamePlayableNoGameToday() {
        // Arrange
        when(gameService.getTodayGame()).thenReturn(Optional.empty());
        
        // Act
        Map<String, Object> result = gameController.isGamePlayable(1L);
        
        // Assert
        assertFalse((Boolean) result.get("playable"), "Should not be playable when no game today");
    }

    @Test
    @DisplayName("Should get question endpoint")
    void testGetQuestion() {
        // Arrange & Act
        Map<String, Object> result = gameController.getQuestion(1L);
        
        // Assert
        assertNotNull(result, "Result should not be null");
        assertTrue(result.containsKey("message"), "Should return a message");
    }

    @Test
    @DisplayName("Should get challenge endpoint")
    void testGetChallenge() {
        // Arrange & Act
        Map<String, Object> result = gameController.getChallenge(1L);
        
        // Assert
        assertNotNull(result, "Result should not be null");
        assertTrue(result.containsKey("message"), "Should return a message");
    }

    @Test
    @DisplayName("Should throw exception when userId not in session")
    void testAddScoreNoUserId() {
        // Arrange
        when(httpSession.getAttribute("userId")).thenReturn(null);
        
        // Act & Assert
        assertThrows(ResponseStatusException.class, () -> {
            gameController.addScore(1L, 20, httpSession);
        }, "Should throw exception when user not logged in");
    }

    // Helper methods
    
    private Game createTestGame(Long id, String name, String gameType) {
        Game game = new Game();
        game.setId(id);
        game.setName(name);
        game.setGameType(gameType);
        game.setDayOfWeek(2);
        game.setWeekNumber(1);
        return game;
    }
}
