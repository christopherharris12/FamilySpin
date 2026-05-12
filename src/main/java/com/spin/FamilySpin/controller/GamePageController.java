package com.spin.FamilySpin.controller;

import com.spin.FamilySpin.model.Game;
import com.spin.FamilySpin.model.GameChallenge;
import com.spin.FamilySpin.model.GameQuestion;
import com.spin.FamilySpin.model.User;
import com.spin.FamilySpin.repository.UserRepository;
import com.spin.FamilySpin.service.GameService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import jakarta.servlet.http.HttpSession;

import java.util.List;
import java.util.Optional;

@Controller
public class GamePageController {

    private final GameService gameService;
    private final UserRepository userRepository;

    public GamePageController(GameService gameService, UserRepository userRepository) {
        this.gameService = gameService;
        this.userRepository = userRepository;
    }

    @GetMapping("/games")
    public String gamesHub(Model model, HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            return "redirect:/login";
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            session.invalidate();
            return "redirect:/login";
        }

        List<Game> games = gameService.getCurrentWeekGames();
        Optional<Game> todayGame = gameService.getTodayGame();
        Long todayGameId = todayGame.map(Game::getId).orElse(null);

        model.addAttribute("games", games);
        model.addAttribute("todayGameId", todayGameId);
        model.addAttribute("leaderboard", gameService.getWeeklyLeaderboard());
        model.addAttribute("username", user.getUsername());
        model.addAttribute("userIsAdmin", user.isAdmin());
        model.addAttribute("weekNumber", gameService.getCurrentWeekNumber());

        return "games";
    }

    @GetMapping("/games/{gameId}")
    public String playGame(@PathVariable Long gameId, Model model, HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            return "redirect:/login";
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            session.invalidate();
            return "redirect:/login";
        }

        Optional<Game> maybeGame = gameService.getGameById(gameId);
        if (maybeGame.isEmpty()) {
            return "redirect:/games";
        }

        Game game = maybeGame.get();
        boolean playable = gameService.isGamePlayable(game);
        int remainingAttempts = gameService.getRemainingAttempts(user, game);
        
        model.addAttribute("game", game);
        model.addAttribute("playable", playable);
        model.addAttribute("remainingAttempts", remainingAttempts);
        model.addAttribute("username", user.getUsername());
        model.addAttribute("userIsAdmin", user.isAdmin());
        model.addAttribute("weekNumber", gameService.getCurrentWeekNumber());

        if (game.getGameType().equals("SPIN")) {
            return "redirect:/dashboard";
        }

        if (game.getGameType().equals("TRIVIA")) {
            Optional<GameQuestion> question = gameService.getRandomQuestion(game, user);
            model.addAttribute("question", question.orElse(null));
        }

        if (game.getGameType().equals("DARE") || game.getGameType().equals("CHARADES") || game.getGameType().equals("WORD_ASSOCIATION") || game.getGameType().equals("20_QUESTIONS")) {
            Optional<GameChallenge> challenge = gameService.getRandomChallenge(game);
            model.addAttribute("challenge", challenge.orElse(null));
        }

        return "game-page";
    }
}
