package com.spin.FamilySpin.model;

import java.util.List;

public record SpinOutcome(
        int sessionNumber,
        int spinNumber,
        String eliminatedMember,
        String friendOfTheWeek,
        List<String> remainingMembers,
        List<SpinHistoryEntry> history,
        List<String> playersThisSession,
        boolean sessionCompleted,
        String dashboardMessage,
        boolean retryRequired
) {
}