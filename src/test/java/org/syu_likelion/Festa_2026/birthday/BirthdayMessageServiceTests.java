package org.syu_likelion.Festa_2026.birthday;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.syu_likelion.Festa_2026.error.ApiException;

class BirthdayMessageServiceTests {
    private static final UUID AUTHOR = UUID.fromString("123e4567-e89b-12d3-a456-426614174010");
    private static final UUID OTHER = UUID.fromString("123e4567-e89b-12d3-a456-426614174011");
    private BirthdayMessageRepository messages;
    private BirthdayMessageHeartRepository hearts;
    private BirthdayMessageService service;

    @BeforeEach
    void setUp() {
        messages = mock(BirthdayMessageRepository.class);
        hearts = mock(BirthdayMessageHeartRepository.class);
        service = new BirthdayMessageService(messages, hearts);
    }

    @Test
    void masksKoreanForeignNamesAndStudentNumber() {
        assertThat(BirthdayMessageService.maskName("김철")).isEqualTo("김*");
        assertThat(BirthdayMessageService.maskName("홍길동")).isEqualTo("홍*동");
        assertThat(BirthdayMessageService.maskName("Alexander")).isEqualTo("A*******r");
        assertThat(BirthdayMessageService.maskName("Jean-Luc Picard")).isEqualTo("J**n-L*c P****d");
        assertThat(BirthdayMessageService.maskStudentNo("2024100920")).isEqualTo("2024******");
    }

    @Test
    void contentLimitCountsUnicodeCodePoints() {
        String oneHundredEmoji = "🎂".repeat(100);
        when(messages.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.createAs(AUTHOR, oneHundredEmoji, "컴퓨터공학부", "2024100920", "홍길동", 1);

        assertThatThrownBy(() -> service.createAs(OTHER, oneHundredEmoji + "🎉",
                "컴퓨터공학부", "2025100920", "김철", 1))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.code()).isEqualTo("BIRTHDAY_MESSAGE_CONTENT_TOO_LONG"));
    }

    @Test
    void deletingOwnMessageRemovesRowSoUserCanWriteAgain() {
        BirthdayMessage original = entity(AUTHOR);
        when(messages.findById(1L)).thenReturn(Optional.of(original));
        when(messages.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.deleteOwnAs(1L, AUTHOR);
        service.createAs(AUTHOR, "다시 축하해!", "컴퓨터공학부", "2024100920", "홍길동", 1);

        verify(messages).delete(original);
        verify(messages).flush();
        verify(messages).saveAndFlush(any(BirthdayMessage.class));
    }

    @Test
    void cannotDeleteAnotherUsersMessageOrHeartOwnMessage() {
        BirthdayMessage message = entity(AUTHOR);
        when(messages.findById(1L)).thenReturn(Optional.of(message));
        when(messages.findForUpdateById(1L)).thenReturn(Optional.of(message));

        assertThatThrownBy(() -> service.deleteOwnAs(1L, OTHER))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.code()).isEqualTo("BIRTHDAY_MESSAGE_DELETE_FORBIDDEN"));
        assertThatThrownBy(() -> service.addHeartAs(1L, AUTHOR))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.code()).isEqualTo("SELF_HEART_NOT_ALLOWED"));
    }

    @Test
    void mostLikedSortUsesHeartCountThenLatestCreation() {
        when(messages.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        service.list(null, BirthdayMessageSort.MOST_LIKED, 0, 30, null);

        org.mockito.ArgumentCaptor<Pageable> captor = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(messages).findAll(captor.capture());
        assertThat(captor.getValue().getSort().toList())
                .extracting(org.springframework.data.domain.Sort.Order::getProperty)
                .containsExactly("heartCount", "createdAt", "id");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {-1, 0})
    void rejectsNonPositiveDesignNumbers(int designNo) {
        assertThatThrownBy(() -> service.createAs(AUTHOR, "축하해!", null, null, null, designNo))
                .isInstanceOfSatisfying(ApiException.class, e ->
                        assertThat(e.code()).isEqualTo("INVALID_BIRTHDAY_MESSAGE_DESIGN"));
        org.mockito.Mockito.verifyNoInteractions(messages);
    }

    private BirthdayMessage entity(UUID author) {
        return new BirthdayMessage(author, "생일 축하해!", "컴퓨터공학부", "2024******", "홍*동", 1);
    }
}
