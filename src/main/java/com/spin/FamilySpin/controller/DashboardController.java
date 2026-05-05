package com.spin.FamilySpin.controller;

import com.spin.FamilySpin.model.SpinState;
import com.spin.FamilySpin.model.User;
import com.spin.FamilySpin.repository.UserRepository;
import com.spin.FamilySpin.service.SpinService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import jakarta.servlet.http.HttpSession;

@Controller
public class DashboardController {

    private final SpinService spinService;
    private final UserRepository userRepository;

    public DashboardController(SpinService spinService, UserRepository userRepository) {
        this.spinService = spinService;
        this.userRepository = userRepository;
    }

    @GetMapping({"/", "/dashboard"})
    public String dashboard(Model model, HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");

        if (userId == null) {
            return "redirect:/login";
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            session.invalidate();
            return "redirect:/login";
        }

        SpinState state = spinService.getState();
        boolean userHasSpun = spinService.hasUserSpunThisSession(user);
        
        model.addAttribute("state", state);
        model.addAttribute("username", user.getUsername());
        model.addAttribute("userIsAdmin", user.isAdmin());
        model.addAttribute("userHasSpun", userHasSpun);
        model.addAttribute("friendOfTheWeek", state.friendOfTheWeek());
        // Only show the friend display to users who have already spun this session.
        model.addAttribute("friendOfTheWeekDisplay", userHasSpun ? state.friendOfTheWeek() : null);
        model.addAttribute("dashboardMessage", state.dashboardMessage());
        model.addAttribute("sessionCompleted", state.completed());
        model.addAttribute("activeMembers", state.activeMembers());
        model.addAttribute("history", state.history());
        
        return "dashboard-new";
    }
}