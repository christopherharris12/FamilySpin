package com.spin.FamilySpin.model;

import java.time.Instant;
import java.util.List;

public record SpinSession(
        int sessionNumber,
        Instant startedAt,
        Instant completedAt,
        boolean completed,
        String friendOfTheWeek,
        String dashboardMessage,
        List<String> activeMembers,
        List<SpinHistoryEntry> history,
        List<String> playersThisSession,
        int totalMembers
) {
}