package org.syu_likelion.Festa_2026.user;

import java.util.UUID;

/** Local, non-SSO identifiers for retained records; no mapping to the original account is stored. */
public final class DeletedUserIdentity {
    private static final long NAMESPACE = 0x4645535441008000L;
    public static final String NAME = "알 수 없음";
    private DeletedUserIdentity() { }
    public static UUID create() { return new UUID(NAMESPACE, UUID.randomUUID().getLeastSignificantBits()); }
    public static boolean matches(UUID id) { return id != null && id.getMostSignificantBits() == NAMESPACE; }
}
