package com.spin.FamilySpin.service;

import com.spin.FamilySpin.model.DynamicMember;
import com.spin.FamilySpin.model.GamePlay;
import com.spin.FamilySpin.model.SpinHistoryEntry;
import com.spin.FamilySpin.model.SpinOutcome;
import com.spin.FamilySpin.model.SpinSession;
import com.spin.FamilySpin.model.SpinState;
import com.spin.FamilySpin.model.User;
import com.spin.FamilySpin.repository.DynamicMemberRepository;
import com.spin.FamilySpin.repository.GamePlayRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class SpinService {

    private final List<String> originalMembers;
    private final List<String> rosterMembers;
    private final List<String> activeMembers;
    private final List<SpinHistoryEntry> history;
    private final java.util.Deque<String> recentLogins;
    private final GamePlayRepository gamePlayRepository;
    private final DynamicMemberRepository dynamicMemberRepository;
    private final Set<Long> usersSpunThisSession;
    private int sessionNumber;
    private Instant sessionStartedAt;
    private Instant sessionCompletedAt;

    public SpinService(@Value("${family.spin.members}") String seedMembers, GamePlayRepository gamePlayRepository, DynamicMemberRepository dynamicMemberRepository) {
        this.originalMembers = parseMembers(seedMembers);
        this.rosterMembers = new ArrayList<>(originalMembers);
        this.dynamicMemberRepository = dynamicMemberRepository;
        
        // Load dynamically added members from database
        List<DynamicMember> dynamicMembers = dynamicMemberRepository.findAll();
        for (DynamicMember dm : dynamicMembers) {
            if (!rosterMembers.contains(dm.getName())) {
                rosterMembers.add(dm.getName());
            }
        }
        
        this.activeMembers = new ArrayList<>(rosterMembers);
        this.history = new ArrayList<>();
        this.recentLogins = new java.util.ArrayDeque<>();
        this.usersSpunThisSession = new HashSet<>();
        this.sessionNumber = 1;
        this.sessionStartedAt = Instant.now();
        this.sessionCompletedAt = null;
        this.gamePlayRepository = gamePlayRepository;
    }

    public synchronized void recordLogin(User user) {
        if (user == null) {
            return;
        }
        String entry = Instant.now().toString() + " - " + user.getUsername();
        recentLogins.addFirst(entry);
        while (recentLogins.size() > 50) {
            recentLogins.removeLast();
        }
    }

    public synchronized List<String> getRecentLogins() {
        return List.copyOf(recentLogins);
    }

    public synchronized SpinOutcome spinNext(User currentUser) {
        if (currentUser != null && usersSpunThisSession.contains(currentUser.getId())) {
            throw new IllegalStateException("You have already spun this session. Please wait for others to spin.");
        }

        if (activeMembers.isEmpty()) {
            throw new IllegalStateException("No active members left to spin.");
        }

        List<String> selectableMembers = new ArrayList<>(activeMembers);
        if (currentUser != null && currentUser.getFamilyMemberName() != null) {
            selectableMembers.remove(currentUser.getFamilyMemberName());
        }

        if (selectableMembers.isEmpty()) {
            throw new IllegalStateException("No other members available to select.");
        }

        int selectedIndex = ThreadLocalRandom.current().nextInt(selectableMembers.size());
        String eliminatedMember = selectableMembers.get(selectedIndex);

        // Safety guard: if a user lands on their own family member name,
        // do not consume their turn or remove anyone; ask them to spin again.
        if (currentUser != null && isSameMemberName(eliminatedMember, currentUser.getFamilyMemberName())) {
            String retryMessage = "You teased yourself. Spin again.";
            return new SpinOutcome(
                sessionNumber,
                history.size(),
                null,
                getFriendOfTheWeek(),
                List.copyOf(activeMembers),
                List.copyOf(history),
                getPlayersThisSession(),
                activeMembers.isEmpty(),
                retryMessage,
                true
            );
        }

        activeMembers.remove(eliminatedMember);

        int spinNumber = history.size() + 1;
        history.add(new SpinHistoryEntry(sessionNumber, spinNumber, eliminatedMember, Instant.now(), activeMembers.size()));

        if (currentUser != null) {
            GamePlay gamePlay = new GamePlay(currentUser, sessionNumber, eliminatedMember);
            gamePlayRepository.save(gamePlay);
            usersSpunThisSession.add(currentUser.getId());
        }

        boolean sessionCompleted = activeMembers.isEmpty();
        if (sessionCompleted) {
            sessionCompletedAt = Instant.now();
        }

        String dashboardMessage = buildDashboardMessage(eliminatedMember, sessionCompleted);
        return new SpinOutcome(
            sessionNumber,
            spinNumber,
            eliminatedMember,
            eliminatedMember,
            List.copyOf(activeMembers),
            List.copyOf(history),
            getPlayersThisSession(),
            sessionCompleted,
            dashboardMessage,
            false
        );
    }

    public synchronized SpinState getState() {
        String friendOfTheWeek = getFriendOfTheWeek();
        return new SpinState(sessionNumber, sessionStartedAt, sessionCompletedAt, activeMembers.isEmpty(), friendOfTheWeek, buildDashboardMessage(friendOfTheWeek, activeMembers.isEmpty()), List.copyOf(activeMembers), List.copyOf(history), getPlayersThisSession(), rosterMembers.size());
    }

    public synchronized boolean hasUserSpunThisSession(User user) {
        if (user == null) {
            return false;
        }
        return gamePlayRepository.findByUserAndSessionNumber(user, sessionNumber).isPresent();
    }

    public synchronized void reset() {
        activeMembers.clear();
        activeMembers.addAll(rosterMembers);
        history.clear();
        usersSpunThisSession.clear();
        sessionNumber++;
        sessionStartedAt = Instant.now();
        sessionCompletedAt = null;
    }

    private List<String> parseMembers(String seedMembers) {
        if (seedMembers == null || seedMembers.isBlank()) {
            throw new IllegalArgumentException("family.spin.members must not be empty");
        }

        List<String> members = new ArrayList<>();
        for (String rawMember : seedMembers.split(",")) {
            String member = rawMember.trim();
            if (!member.isEmpty()) {
                members.add(member);
            }
        }

        if (members.isEmpty()) {
            throw new IllegalArgumentException("family.spin.members must contain at least one name");
        }

        return Collections.unmodifiableList(members);
    }

    @Scheduled(cron = "${family.spin.weekly-cron:0 0 0 * * MON}")
    public synchronized void startNewWeek() {
        reset();
    }

    public synchronized SpinSession getSession() {
        String friendOfTheWeek = getFriendOfTheWeek();
        return new SpinSession(sessionNumber, sessionStartedAt, sessionCompletedAt, activeMembers.isEmpty(), friendOfTheWeek, buildDashboardMessage(friendOfTheWeek, activeMembers.isEmpty()), List.copyOf(activeMembers), List.copyOf(history), getPlayersThisSession(), rosterMembers.size());
    }

    private String getFriendOfTheWeek() {
        if (history.isEmpty()) {
            return null;
        }

        return history.get(history.size() - 1).memberName();
    }

    private String buildDashboardMessage(String friendOfTheWeek, boolean sessionCompleted) {
        if (friendOfTheWeek == null) {
            return "Spin to choose your first friend of the week.";
        }

        if (sessionCompleted) {
            return "Take care of your friend of the week: " + friendOfTheWeek + ". The session is finished and a new week will start automatically.";
        }

        return "Take care of your friend of the week: " + friendOfTheWeek + ". Others will continue until the session is complete.";
    }

    private List<String> getPlayersThisSession() {
        return gamePlayRepository.findBySessionNumber(sessionNumber).stream()
                .map(gamePlay -> gamePlay.getUser().getFamilyMemberName())
                .distinct()
                .toList();
    }

    public synchronized boolean addMember(String familyMemberName) {
        if (familyMemberName == null || familyMemberName.isBlank()) {
            return false;
        }

        String trimmedName = familyMemberName.trim();
        if (rosterMembers.contains(trimmedName)) {
            return false;
        }

        rosterMembers.add(trimmedName);
        activeMembers.add(trimmedName);
        
        // Save to database
        DynamicMember dynamicMember = new DynamicMember(trimmedName);
        dynamicMemberRepository.save(dynamicMember);
        
        return true;
    }

    public synchronized List<String> getAllMembers() {
        return List.copyOf(rosterMembers);
    }

    public synchronized boolean removeMember(String familyMemberName) {
        if (familyMemberName == null || familyMemberName.isBlank()) {
            return false;
        }

        String trimmedName = familyMemberName.trim();
        boolean removedFromRoster = rosterMembers.remove(trimmedName);
        boolean removedFromActive = activeMembers.remove(trimmedName);
        
        // Delete from database
        if (removedFromRoster || removedFromActive) {
            dynamicMemberRepository.deleteByName(trimmedName);
        }
        
        return removedFromRoster || removedFromActive;
    }

    private boolean isSameMemberName(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return left.trim().equalsIgnoreCase(right.trim());
    }
}
