package com.spin.FamilySpin.controller;

import com.spin.FamilySpin.model.User;
import com.spin.FamilySpin.repository.UserRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import jakarta.servlet.http.HttpSession;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
public class AuthController {

    private final UserRepository userRepository;
    private final com.spin.FamilySpin.service.SpinService spinService;

    public AuthController(UserRepository userRepository, com.spin.FamilySpin.service.SpinService spinService) {
        this.userRepository = userRepository;
        this.spinService = spinService;
    }

    @GetMapping("/register")
    public String registerPage(Model model) {
        model.addAttribute("familyMembers", getAvailableFamilyMembers());
        return "register";
    }

    @PostMapping("/register")
    public String register(
            @RequestParam String username,
            @RequestParam String email,
            @RequestParam String password,
            @RequestParam String familyMemberName,
            Model model,
            HttpSession session) {

        if (userRepository.findByUsername(username).isPresent()) {
            model.addAttribute("error", "Username already exists");
            model.addAttribute("familyMembers", getAvailableFamilyMembers());
            return "register";
        }

        if (userRepository.findByEmail(email).isPresent()) {
            model.addAttribute("error", "Email already registered");
            model.addAttribute("familyMembers", getAvailableFamilyMembers());
            return "register";
        }

        if (userRepository.findByFamilyMemberName(familyMemberName).isPresent()) {
            model.addAttribute("error", "This family member name is already taken. Please choose another.");
            model.addAttribute("familyMembers", getAvailableFamilyMembers());
            return "register";
        }

        User user = new User(username, email, password, familyMemberName);
        userRepository.save(user);

        return "redirect:/login";
    }

    private List<String> getAvailableFamilyMembers() {
        List<String> available = new ArrayList<>(spinService.getAllMembers());
        Set<String> takenNames = userRepository.findAll().stream()
                .map(User::getFamilyMemberName)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        available.removeAll(takenNames);
        return available;
    }

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @PostMapping("/login")
    public String login(
            @RequestParam String username,
            @RequestParam String password,
            Model model,
            HttpSession session) {

        Optional<User> userOpt = userRepository.findByUsername(username);

        if (userOpt.isEmpty()) {
            model.addAttribute("error", "Username not found");
            return "login";
        }

        User user = userOpt.get();
        if (!user.getPassword().equals(password)) {
            model.addAttribute("error", "Invalid password");
            return "login";
        }

        session.setAttribute("userId", user.getId());
        session.setAttribute("username", user.getUsername());
        session.setAttribute("isAdmin", user.isAdmin());
        // Record login for admin dashboard notification
        spinService.recordLogin(user);

        if (user.isAdmin()) {
            return "redirect:/admin";
        }

        return "redirect:/dashboard";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }
}
