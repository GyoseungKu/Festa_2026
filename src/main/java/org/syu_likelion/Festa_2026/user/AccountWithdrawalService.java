package org.syu_likelion.Festa_2026.user;

import org.springframework.stereotype.Service;
import org.syu_likelion.Festa_2026.auth.AuthorizedSsoExecutor;
import org.syu_likelion.Festa_2026.auth.WithdrawalEmailService;
import org.syu_likelion.Festa_2026.auth.WithdrawalEmailService.Kind;
import org.syu_likelion.Festa_2026.sso.SsoAuthClient;
import org.syu_likelion.Festa_2026.user.UserDtos.MeResponse;

@Service
public class AccountWithdrawalService {
    private final SsoAuthClient sso;
    private final AuthorizedSsoExecutor executor;
    private final FestivalWithdrawalService festival;
    private final WithdrawalEmailService emails;

    public AccountWithdrawalService(SsoAuthClient sso, AuthorizedSsoExecutor executor,
                                    FestivalWithdrawalService festival, WithdrawalEmailService emails) {
        this.sso = sso;
        this.executor = executor;
        this.festival = festival;
        this.emails = emails;
    }

    public void withdrawFestival(String access, String refresh) {
        // Read the recipient before deletion, without linking a new local account or sending welcome mail.
        MeResponse me = executor.execute(access, refresh, sso::getMe).body();
        if (festival.withdraw(me.userUuid())) {
            emails.sendLater(me.userUuid(), me.email(), me.name(), Kind.FESTIVAL);
        }
    }

    public void withdrawSso(String access, String refresh) {
        MeResponse me = executor.execute(access, refresh, token -> {
            MeResponse profile = sso.getMe(token);
            festival.withdraw(profile.userUuid(), () -> sso.withdraw(token));
            return profile;
        }).body();
        // BambooSequence returns only after the local transaction has committed.
        emails.sendLater(me.userUuid(), me.email(), me.name(), Kind.SSO);
    }
}
