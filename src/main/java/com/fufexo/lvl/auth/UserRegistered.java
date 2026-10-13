package com.fufexo.lvl.auth;

import java.util.UUID;

/** Published inside the sign-up transaction; listeners that fail roll the sign-up back. */
public record UserRegistered(UUID userId, String gamertag) {
}
