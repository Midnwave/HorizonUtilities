package com.blockforge.horizonutilities.games;

import java.util.UUID;

public record GameResult(UUID playerUuid, String playerName, String answer, long timeMs, String gameType) {
}
