package com.spin.FamilySpin.controller;

import com.spin.FamilySpin.model.SpinOutcome;
import com.spin.FamilySpin.model.SpinSession;
import com.spin.FamilySpin.model.SpinState;
import com.spin.FamilySpin.model.User;
import com.spin.FamilySpin.repository.UserRepository;
import com.spin.FamilySpin.service.SpinService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/spin")
public class SpinController {

    private final SpinService spinService;
    private final UserRepository userRepository;

    public SpinController(SpinService spinService, UserRepository userRepository) {
        this.spinService = spinService;
        this.userRepository = userRepository;
    }

    @GetMapping("/state")
    public SpinState state() {
        return spinService.getState();
    }

    @GetMapping("/session")
    public SpinSession session() {
        return spinService.getSession();
    }

    @GetMapping("/active")
    public java.util.List<String> activeMembers() {
        return spinService.getState().activeMembers();
    }

    @GetMapping("/history")
    public java.util.List<com.spin.FamilySpin.model.SpinHistoryEntry> history() {
        return spinService.getState().history();
    }

    @PostMapping("/next")
    public SpinOutcome nextSpin(HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        User user = null;
        if (userId != null) {
            user = userRepository.findById(userId).orElse(null);
        }

        try {
            return spinService.spinNext(user);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        }
    }

    @PostMapping("/reset")
    public SpinState reset() {
        spinService.reset();
        return spinService.getState();
    }

    @PostMapping("/new-week")
    public SpinState newWeek(HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please log in first.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please log in first."));
        if (!user.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only admins can start a new week.");
        }

        spinService.startNewWeek();
        return spinService.getState();
    }
}