package com.spin.FamilySpin.service;

import com.spin.FamilySpin.model.*;
import com.spin.FamilySpin.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("SpinService Unit Tests")
class SpinServiceTest {

    private SpinService spinService;

    @Mock
    private GamePlayRepository gamePlayRepository;

    @Mock
    private GameAnswerRepository gameAnswerRepository;

    @Mock
    private GameRepository gameRepository;

    @Mock
    private DynamicMemberRepository dynamicMemberRepository;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Mock database calls
        when(dynamicMemberRepository.findAll()).thenReturn(Collections.emptyList());
        when(gamePlayRepository.findBySessionNumber(anyInt())).thenReturn(Collections.emptyList());
        when(gamePlayRepository.findByUserAndSessionNumber(any(), anyInt())).thenReturn(Optional.empty());
        
        spinService = new SpinService(
            "Alice,Bob,Charlie",
            gamePlayRepository,
            gameAnswerRepository,
            gameRepository,
            dynamicMemberRepository
        );
    }

    @Test
    @DisplayName("Should return state with initial active members")
    void testGetStateInitialization() {
        // Arrange & Act
        SpinState state = spinService.getState();
        
        // Assert
        assertNotNull(state, "SpinState should not be null");
        assertNotNull(state.activeMembers(), "Active members should not be null");
        assertEquals(3, state.activeMembers().size(), "Should have 3 active members initially");
        assertEquals("Alice,Bob,Charlie".split(",").length, state.activeMembers().size());
    }

    @Test
    @DisplayName("Should record user login")
    void testRecordLogin() {
        // Arrange
        User user = createTestUser(1L, "testUser");
        
        // Act
        spinService.recordLogin(user);
        List<String> recentLogins = spinService.getRecentLogins();
        
        // Assert
        assertFalse(recentLogins.isEmpty(), "Should have recorded login");
        assertTrue(recentLogins.get(0).contains("testUser"), "Should contain username in login record");
    }

    @Test
    @DisplayName("Should not record null user login")
    void testRecordNullLogin() {
        // Arrange
        List<String> loginsBefore = spinService.getRecentLogins();
        int sizeBefore = loginsBefore.size();
        
        // Act
        spinService.recordLogin(null);
        List<String> loginsAfter = spinService.getRecentLogins();
        
        // Assert
        assertEquals(sizeBefore, loginsAfter.size(), "Should not record null user login");
    }

    @Test
    @DisplayName("Should return spin state")
    void testGetState() {
        // Arrange & Act
        SpinState state = spinService.getState();
        
        // Assert
        assertNotNull(state, "SpinState should not be null");
        assertNotNull(state.activeMembers(), "Active members should not be null");
        assertNotNull(state.history(), "History should not be null");
        assertTrue(state.activeMembers().size() > 0, "Should have active members");
        assertEquals(1, state.sessionNumber(), "Should start with session number 1");
    }

    @Test
    @DisplayName("Should check if user has spun this session - not spun")
    void testHasUserSpunThisSessionFalse() {
        // Arrange
        User user = createTestUser(1L, "testUser");
        when(gamePlayRepository.findByUserAndSessionNumber(user, 1)).thenReturn(Optional.empty());
        
        // Act
        boolean hasSpun = spinService.hasUserSpunThisSession(user);
        
        // Assert
        assertFalse(hasSpun, "User should not have spun initially");
    }

    @Test
    @DisplayName("Should detect when user has spun this session")
    void testHasUserSpunThisSessionTrue() {
        // Arrange
        User user = createTestUser(1L, "testUser");
        GamePlay gamePlay = new GamePlay();
        gamePlay.setUser(user);
        gamePlay.setSessionNumber(1);
        gamePlay.setEliminatedMember("Bob");
        
        when(gamePlayRepository.findByUserAndSessionNumber(user, 1)).thenReturn(Optional.of(gamePlay));
        
        // Act
        boolean hasSpun = spinService.hasUserSpunThisSession(user);
        
        // Assert
        assertTrue(hasSpun, "User should have spun this session");
    }

    @Test
    @DisplayName("Should get personal spinner for a user")
    void testGetPersonalSpinner() {
        // Arrange
        User user = createTestUser(1L, "testUser");
        user.setFamilyMemberName("Alice");
        
        User otherUser = createTestUser(2L, "otherUser");
        otherUser.setFamilyMemberName("Bob");
        
        GamePlay gamePlay = new GamePlay();
        gamePlay.setUser(otherUser);
        gamePlay.setSessionNumber(1);
        gamePlay.setEliminatedMember("Alice");  // Bob spun Alice
        
        when(gamePlayRepository.findBySessionNumber(1)).thenReturn(List.of(gamePlay));
        
        // Act
        String personalSpinner = spinService.getPersonalSpinner(user);
        
        // Assert
        assertNotNull(personalSpinner, "Should find personal spinner");
        assertEquals("Bob", personalSpinner, "Personal spinner should be Bob");
    }

    @Test
    @DisplayName("Should return null for personal spinner when user not spun")
    void testGetPersonalSpinnerWhenNotSpun() {
        // Arrange
        User user = createTestUser(1L, "Alice");
        user.setFamilyMemberName("Alice");
        when(gamePlayRepository.findBySessionNumber(1)).thenReturn(Collections.emptyList());
        
        // Act
        String personalSpinner = spinService.getPersonalSpinner(user);
        
        // Assert
        assertNull(personalSpinner, "Should return null when user not spun");
    }

    @Test
    @DisplayName("Should return null for personal spinner with null user")
    void testGetPersonalSpinnerNullUser() {
        // Act
        String personalSpinner = spinService.getPersonalSpinner(null);
        
        // Assert
        assertNull(personalSpinner, "Should return null for null user");
    }

    @Test
    @DisplayName("Should get recent logins")
    void testGetRecentLogins() {
        // Arrange & Act
        List<String> logins = spinService.getRecentLogins();
        
        // Assert
        assertNotNull(logins, "Recent logins should not be null");
        assertTrue(logins.isEmpty(), "Should be empty initially");
    }

    @Test
    @DisplayName("Should return spin session")
    void testGetSession() {
        // Arrange & Act
        SpinSession session = spinService.getSession();
        
        // Assert
        assertNotNull(session, "SpinSession should not be null");
        assertEquals(1, session.sessionNumber(), "Should have session number 1");
    }

    @Test
    @DisplayName("Should limit recent logins to 50 entries")
    void testRecentLoginsLimit() {
        // Arrange
        for (int i = 0; i < 60; i++) {
            User user = createTestUser((long) i, "user" + i);
            spinService.recordLogin(user);
        }
        
        // Act
        List<String> recentLogins = spinService.getRecentLogins();
        
        // Assert
        assertEquals(50, recentLogins.size(), "Recent logins should be limited to 50");
    }

    @Test
    @DisplayName("Should handle null user family member name in getPersonalSpinner")
    void testGetPersonalSpinnerNullFamilyMemberName() {
        // Arrange
        User user = createTestUser(1L, "testUser");
        user.setFamilyMemberName(null);
        
        // Act
        String personalSpinner = spinService.getPersonalSpinner(user);
        
        // Assert
        assertNull(personalSpinner, "Should return null when user has no family member name");
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
}
