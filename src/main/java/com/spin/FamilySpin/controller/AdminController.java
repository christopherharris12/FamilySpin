package com.spin.FamilySpin.controller;

import com.spin.FamilySpin.model.GamePlay;
import com.spin.FamilySpin.model.User;
import com.spin.FamilySpin.repository.GamePlayRepository;
import com.spin.FamilySpin.repository.UserRepository;
import com.spin.FamilySpin.service.GameService;
import com.spin.FamilySpin.service.SpinService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private final UserRepository userRepository;
    private final GamePlayRepository gamePlayRepository;
    private final SpinService spinService;
    private final GameService gameService;
    private final com.spin.FamilySpin.repository.GameAnswerRepository gameAnswerRepository;

    public AdminController(UserRepository userRepository, GamePlayRepository gamePlayRepository, SpinService spinService, GameService gameService, com.spin.FamilySpin.repository.GameAnswerRepository gameAnswerRepository) {
        this.userRepository = userRepository;
        this.gamePlayRepository = gamePlayRepository;
        this.spinService = spinService;
        this.gameService = gameService;
        this.gameAnswerRepository = gameAnswerRepository;
    }

    @GetMapping
    public String adminDashboard(HttpSession session, Model model) {
        Long userId = (Long) session.getAttribute("userId");

        if (userId == null) {
            return "redirect:/login";
        }

        User user = userRepository.findById(userId).orElse(null);

        if (user == null || !user.isAdmin()) {
            return "redirect:/dashboard";
        }

        List<User> allUsers = userRepository.findAll();
        List<GamePlay> allGamePlays = gamePlayRepository.findAll();

        // Get current session state
        var currentState = spinService.getState();
        int currentSessionNumber = currentState.sessionNumber();

        // Find all GamePlay records for current session
        List<GamePlay> currentSessionPlays = gamePlayRepository.findBySessionNumber(currentSessionNumber);
        
        // Get set of users who have spun this session
        Set<Long> usersWhoSpun = currentSessionPlays.stream()
                .map(gp -> gp.getUser().getId())
                .collect(Collectors.toSet());

        // Get set of users who haven't spun this session
        List<User> usersWhoHaventSpun = allUsers.stream()
                .filter(u -> !usersWhoSpun.contains(u.getId()))
                .collect(Collectors.toList());

        // Group game plays by user for history
        Map<User, List<GamePlay>> userGamePlays = allUsers.stream()
                .collect(Collectors.toMap(
                        u -> u,
                        u -> allGamePlays.stream()
                                .filter(gp -> gp.getUser().getId().equals(u.getId()))
                                .collect(Collectors.toList()),
                        (u1, u2) -> u1
                ));

        // Map user to their friend (the last person they eliminated in current session)
        Map<Long, String> userIdToFriend = new java.util.HashMap<>();
        for (GamePlay gp : currentSessionPlays) {
            userIdToFriend.put(gp.getUser().getId(), gp.getEliminatedMember());
        }

        model.addAttribute("allUsers", allUsers);
        model.addAttribute("userGamePlays", userGamePlays);
        model.addAttribute("userIdToFriend", userIdToFriend);
        model.addAttribute("usersWhoSpun", usersWhoSpun);
        model.addAttribute("usersWhoHaventSpun", usersWhoHaventSpun);
        model.addAttribute("currentSessionNumber", currentSessionNumber);
        model.addAttribute("totalPlays", allGamePlays.size());
        model.addAttribute("currentUsername", user.getUsername());
        // Recent logins for admin visibility
        model.addAttribute("recentLogins", spinService.getRecentLogins());
        // All members in the game
        model.addAttribute("allGameMembers", currentState.activeMembers());

        // Recent answers (show most recent 200)
        try {
            List<com.spin.FamilySpin.model.GameAnswer> recentAnswers = gameAnswerRepository.findTop200ByOrderByAnswerDateDesc();
            model.addAttribute("recentAnswers", recentAnswers);
        } catch (Exception ex) {
            model.addAttribute("recentAnswers", java.util.Collections.emptyList());
        }

        return "admin";
    }

    @PostMapping("/reset")
    public String resetSession(HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) return "redirect:/login";
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || !user.isAdmin()) {
            return "redirect:/dashboard";
        }

        spinService.reset();
        return "redirect:/admin";
    }

    @PostMapping("/reset-games")
    public String resetGames(HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) return "redirect:/login";
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || !user.isAdmin()) {
            return "redirect:/dashboard";
        }

        gameService.resetGamesForCurrentWeek();
        return "redirect:/dashboard";
    }

    @PostMapping("/add-member")
    public String addMember(HttpSession session,
                            @org.springframework.web.bind.annotation.RequestParam String familyMemberName) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) return "redirect:/login";
        User adminUser = userRepository.findById(userId).orElse(null);
        if (adminUser == null || !adminUser.isAdmin()) {
            return "redirect:/dashboard";
        }

        if (familyMemberName == null || familyMemberName.isBlank()) {
            return "redirect:/admin";
        }

        spinService.addMember(familyMemberName);
        return "redirect:/admin";
    }

    @PostMapping("/toggle-admin")
    public String toggleAdmin(HttpSession session, @org.springframework.web.bind.annotation.RequestParam Long targetUserId) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) return "redirect:/login";
        User adminUser = userRepository.findById(userId).orElse(null);
        if (adminUser == null || !adminUser.isAdmin()) {
            return "redirect:/dashboard";
        }

        User target = userRepository.findById(targetUserId).orElse(null);
        if (target != null) {
            target.setAdmin(!target.isAdmin());
            userRepository.save(target);
        }

        return "redirect:/admin";
    }

    @PostMapping("/delete-user")
    public String deleteUser(HttpSession session, @org.springframework.web.bind.annotation.RequestParam Long targetUserId) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) return "redirect:/login";
        User adminUser = userRepository.findById(userId).orElse(null);
        if (adminUser == null || !adminUser.isAdmin()) {
            return "redirect:/dashboard";
        }

        // prevent admin deleting themselves
        if (userId.equals(targetUserId)) {
            return "redirect:/admin";
        }

        userRepository.findById(targetUserId).ifPresent(u -> {
            userRepository.delete(u);
        });

        return "redirect:/admin";
    }

    @PostMapping("/delete-by-username")
    public String deleteByUsername(HttpSession session, @org.springframework.web.bind.annotation.RequestParam String username) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) return "redirect:/login";
        User adminUser = userRepository.findById(userId).orElse(null);
        if (adminUser == null || !adminUser.isAdmin()) {
            return "redirect:/dashboard";
        }

        userRepository.findByUsername(username).ifPresent(u -> {
            // prevent deleting the current admin by mistake
            if (!u.getId().equals(userId)) {
                userRepository.delete(u);
            }
        });

        return "redirect:/admin";
    }

    @PostMapping("/remove-member")
    public String removeMember(HttpSession session, @org.springframework.web.bind.annotation.RequestParam String familyMemberName) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) return "redirect:/login";
        User adminUser = userRepository.findById(userId).orElse(null);
        if (adminUser == null || !adminUser.isAdmin()) {
            return "redirect:/dashboard";
        }

        spinService.removeMember(familyMemberName);
        return "redirect:/admin";
    }

    @PostMapping("/set-admin-by-username")
    public String setAdminByUsername(@org.springframework.web.bind.annotation.RequestParam String username) {
        // Bootstrap endpoint - allow first admin setup
        long adminCount = userRepository.findAll().stream().filter(User::isAdmin).count();
        
        // Only allow if there are no admins yet (bootstrap)
        if (adminCount == 0) {
            userRepository.findByUsername(username).ifPresent(u -> {
                u.setAdmin(true);
                userRepository.save(u);
            });
            return "redirect:/login";
        }
        
        return "redirect:/login";
    }
}
