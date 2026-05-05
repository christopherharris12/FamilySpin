package com.spin.FamilySpin.model;

import java.time.Instant;

public record SpinHistoryEntry(int sessionNumber, int spinNumber, String memberName, Instant eliminatedAt, int remainingMembers) {
}