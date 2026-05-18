package com.nexus.api;

import java.util.UUID;

public record TaskRequest(UUID userId, String prompt) {
}
