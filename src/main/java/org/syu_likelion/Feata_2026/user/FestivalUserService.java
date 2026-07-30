package org.syu_likelion.Feata_2026.user;

import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FestivalUserService {
    private final FestivalUserRepository repository;

    public FestivalUserService(FestivalUserRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Set<FestivalRole> linkAndGetRoles(UUID userUuid) {
        if (userUuid == null) throw new IllegalArgumentException("SSO response did not contain userUuid");
        return repository.findByUserUuid(userUuid).orElseGet(() -> create(userUuid)).getRoles();
    }

    @Transactional(readOnly = true)
    public Set<FestivalRole> getRoles(UUID userUuid) {
        return repository.findByUserUuid(userUuid).map(FestivalUser::getRoles).orElseGet(Set::of);
    }

    private FestivalUser create(UUID userUuid) {
        try {
            return repository.saveAndFlush(new FestivalUser(userUuid));
        } catch (DataIntegrityViolationException concurrentInsert) {
            return repository.findByUserUuid(userUuid).orElseThrow(() -> concurrentInsert);
        }
    }
}
