package com.spin.FamilySpin.controller;

import com.spin.FamilySpin.model.*;
import com.spin.FamilySpin.repository.UserRepository;
import com.spin.FamilySpin.repository.GameQuestionRepository;
import com.spin.FamilySpin.service.GameService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import jakarta.servlet.http.HttpSession;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/games")
public class GameController {

    private final GameService gameService;
    private final UserRepository userRepository;
    private final GameQuestionRepository gameQuestionRepository;

    public GameController(GameService gameService, UserRepository userRepository, GameQuestionRepository gameQuestionRepository) {
        this.gameService = gameService;
        this.userRepository = userRepository;
        this.gameQuestionRepository = gameQuestionRepository;
    }

    @GetMapping("/today")
    public Map<String, Object> getTodayGame() {
        Optional<Game> game = gameService.getTodayGame();
        if (game.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No game scheduled for today");
        }

        Game g = game.get();
        Map<String, Object> response = Map.of(
            "id", g.getId(),
            "name", g.getName(),
            "gameType", g.getGameType(),
            "dayOfWeek", g.getDayOfWeek()
        );
        return response;
    }

    @GetMapping("/week")
    public List<Game> getWeekGames() {
        return gameService.getCurrentWeekGames();
    }

    @GetMapping("/{gameId}/playable")
    public Map<String, Object> isGamePlayable(@PathVariable Long gameId) {
        Optional<Game> game = gameService.getTodayGame();
        boolean playable = game.map(g -> g.getId().equals(gameId)).orElse(false);
        return Map.of("playable", playable);
    }

    @GetMapping("/{gameId}/question")
    public Map<String, Object> getQuestion(@PathVariable Long gameId) {
        // Placeholder - implement based on game repository lookup
        return Map.of("message", "Question endpoint");
    }

    @GetMapping("/{gameId}/challenge")
    public Map<String, Object> getChallenge(@PathVariable Long gameId) {
        // Placeholder - implement based on game repository lookup
        return Map.of("message", "Challenge endpoint");
    }

    @PostMapping("/{gameId}/score")
    public Map<String, Object> addScore(
            @PathVariable Long gameId,
            @RequestParam int points,
            HttpSession session) {
        
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please log in first.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found."));

        // Get game (pseudo-code - real implementation would fetch from repo)
        // gameService.addScore(user, game, points, gameService.getCurrentWeekNumber());

        return Map.of("status", "Score added", "points", points);
    }

    @PostMapping("/{gameId}/answer")
        public Map<String, Object> recordAnswer(
            @PathVariable Long gameId,
            @RequestParam String answerText,
            @RequestParam(required = false) Long questionId,
            HttpSession session) {
        
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please log in first.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found."));

        Optional<Game> game = gameService.getGameById(gameId);
        if (game.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Game not found.");
        }

        // Check if user has attempts left
        int remainingAttempts = gameService.getRemainingAttempts(user, game.get());
        if (remainingAttempts <= 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No attempts remaining for this game today.");
        }

        // Record the answer attempt
        gameService.recordAnswer(user, game.get(), answerText);

        // Validate correctness if questionId provided
        boolean correct = false;
        String correctAnswer = null;
        if (questionId != null) {
            Optional<GameQuestion> qOpt = gameQuestionRepository.findById(questionId);
            if (qOpt.isPresent()) {
                GameQuestion q = qOpt.get();
                correctAnswer = q.getAnswer();
                // Normalize strings for simple comparison
                String expected = q.getAnswer() == null ? "" : q.getAnswer().replaceAll("\\s+", "").replaceAll("[^a-zA-Z0-9:]", "").toLowerCase();
                String given = answerText == null ? "" : answerText.replaceAll("\\s+", "").replaceAll("[^a-zA-Z0-9:]", "").toLowerCase();
                correct = expected.equals(given);

                if (correct) {
                    // award points for correct answer
                    gameService.addScore(user, game.get(), 20, gameService.getCurrentWeekNumber());
                }
            }
        }

        // Get updated remaining attempts
        int updatedRemaining = gameService.getRemainingAttempts(user, game.get());

        Map<String, Object> resp = Map.of(
            "status", "Answer recorded",
            "remainingAttempts", updatedRemaining,
            "correct", correct,
            "correctAnswer", correctAnswer == null ? "" : correctAnswer
        );

        return resp;
    }

    @GetMapping("/{gameId}/attempts")
    public Map<String, Object> getRemainingAttempts(
            @PathVariable Long gameId,
            HttpSession session) {
        
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please log in first.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found."));

        Optional<Game> game = gameService.getGameById(gameId);
        if (game.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Game not found.");
        }

        int remaining = gameService.getRemainingAttempts(user, game.get());

        return Map.of(
            "remainingAttempts", remaining,
            "canPlay", remaining > 0
        );
    }

    @GetMapping("/leaderboard")

    public Map<String, Object> getLeaderboard() {
        List<Map<String, Object>> rankings = gameService.getWeeklyLeaderboard();
        return Map.of("leaderboard", rankings, "week", gameService.getCurrentWeekNumber());
    }

    @PostMapping("/initialize-week")
    public Map<String, Object> initializeWeekGames(HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please log in first.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found."));

        if (!user.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only admins can initialize games.");
        }

        int nextWeek = gameService.getCurrentWeekNumber() + 1;
        java.time.Instant nextSunday = java.time.LocalDate.now(java.time.ZoneId.systemDefault())
                .plusDays(7)
                .with(java.time.DayOfWeek.SUNDAY)
                .atStartOfDay(java.time.ZoneId.systemDefault())
                .toInstant();
        
        gameService.initializeWeekGames(nextWeek, nextSunday);
        return Map.of("status", "Games initialized for week " + nextWeek);
    }
}
