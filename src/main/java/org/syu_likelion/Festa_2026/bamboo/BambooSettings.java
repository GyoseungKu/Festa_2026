package org.syu_likelion.Festa_2026.bamboo;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.syu_likelion.Festa_2026.performance.KstInstantAttributeConverter;

/**
 * 대나무숲 운영 설정. {@code id = 1} 단일 행으로 운용한다.
 *
 * <p>킬스위치 상태는 재시작 후에도 유지되어야 하므로 DB에 둔다.
 */
@Entity
@Table(name = "bamboo_settings")
public class BambooSettings {
    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "read_only", nullable = false)
    private boolean readOnly;

    @Column(name = "closes_at")
    @Convert(converter = KstInstantAttributeConverter.class)
    private Instant closesAt;

    @Column(name = "updated_at", nullable = false)
    @Convert(converter = KstInstantAttributeConverter.class)
    private Instant updatedAt;

    protected BambooSettings() { }

    static BambooSettings openedAt(Instant now) {
        BambooSettings settings = new BambooSettings();
        settings.id = SINGLETON_ID;
        settings.enabled = true;
        settings.readOnly = false;
        settings.updatedAt = now;
        return settings;
    }

    void update(Boolean enabled, Boolean readOnly, Instant closesAt, boolean clearClosesAt, Instant now) {
        if (enabled != null) this.enabled = enabled;
        if (readOnly != null) this.readOnly = readOnly;
        if (clearClosesAt) this.closesAt = null;
        else if (closesAt != null) this.closesAt = closesAt;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public boolean isEnabled() { return enabled; }
    public boolean isReadOnly() { return readOnly; }
    public Instant getClosesAt() { return closesAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    /** 종료 시각이 지났으면 설정과 무관하게 읽기 전용이다. */
    public boolean isReadOnlyAt(Instant now) {
        return readOnly || (closesAt != null && !now.isBefore(closesAt));
    }
}
