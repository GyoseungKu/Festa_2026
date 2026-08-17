package org.syu_likelion.Festa_2026.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "festival_users")
public class FestivalUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_uuid", nullable = false, unique = true, updatable = false, columnDefinition = "BINARY(16)")
    private UUID userUuid;

    @Column(name = "management_role", nullable = false,
            columnDefinition = "enum('SUPER_ADMIN','ADMIN','STAFF','USER') default 'USER'")
    @Enumerated(EnumType.STRING)
    private FestivalRole managementRole = FestivalRole.USER;

    @Column(name = "booth_manager", nullable = false, columnDefinition = "boolean default false")
    private boolean boothManager;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected FestivalUser() { }

    public FestivalUser(UUID userUuid) {
        this.userUuid = userUuid;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() { updatedAt = Instant.now(); }

    public UUID getUserUuid() { return userUuid; }
    public Set<FestivalRole> getRoles() {
        EnumSet<FestivalRole> roles = EnumSet.of(managementRole);
        if (boothManager) roles.add(FestivalRole.BOOTH_MANAGER);
        return Set.copyOf(roles);
    }
    public Long getId() { return id; }

    public void addRole(FestivalRole role) {
        if (role == FestivalRole.BOOTH_MANAGER) boothManager = true;
        else managementRole = role;
    }

    public void removeRole(FestivalRole role) {
        if (role == FestivalRole.BOOTH_MANAGER) boothManager = false;
        else if (managementRole == role) managementRole = FestivalRole.USER;
    }
}
