package ru.sportclub.club.client;

import java.util.UUID;

public interface MembershipAccessClient {
    void requireActiveMembership(UUID memberId, String bearerToken);
}
