package org.syu_likelion.Festa_2026.fee;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.dao.DataIntegrityViolationException;
@Component
public class StudentFeeBootstrap implements ApplicationRunner {
    private final StudentFeeLockRepository locks;
    public StudentFeeBootstrap(StudentFeeLockRepository locks) { this.locks = locks; }
    @Override public void run(ApplicationArguments args) {
        if (!locks.existsById(1L)) {
            try { locks.saveAndFlush(new StudentFeeLock(1)); }
            catch (DataIntegrityViolationException concurrentStartup) {
                if (!locks.existsById(1L)) throw concurrentStartup;
            }
        }
    }
}
