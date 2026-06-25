package com.bridgework.notice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bridgework.notice.dto.NoticeRequestDto;
import com.bridgework.notice.entity.Notice;
import com.bridgework.notice.exception.NoticeDomainException;
import com.bridgework.notice.repository.NoticeRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

class NoticeServiceTest {

    private NoticeRepository noticeRepository;

    private NoticeService noticeService;

    @BeforeEach
    void setUp() {
        noticeRepository = org.mockito.Mockito.mock(NoticeRepository.class);
        noticeService = new NoticeService(noticeRepository);
    }

    @Test
    void getPublicNotices_returnsPublishedNoticesOnly() {
        Notice notice = notice(1L, "공지", true);
        when(noticeRepository.findByPublishedTrueOrderByPinnedDescCreatedAtDescIdDesc(any(Pageable.class)))
                .thenReturn(List.of(notice));

        assertThat(noticeService.getPublicNotices(20)).hasSize(1);
        verify(noticeRepository).findByPublishedTrueOrderByPinnedDescCreatedAtDescIdDesc(any(Pageable.class));
    }

    @Test
    void getPublicNotice_whenNoticeIsPrivate_thenThrowsNotFound() {
        Notice notice = notice(1L, "비공개 공지", false);
        when(noticeRepository.findById(1L)).thenReturn(Optional.of(notice));

        assertThatThrownBy(() -> noticeService.getPublicNotice(1L))
                .isInstanceOf(NoticeDomainException.class)
                .hasMessage("공지사항을 찾을 수 없습니다.");
    }

    @Test
    void createNotice_trimsTextAndDefaultsPublishedTrue() {
        when(noticeRepository.save(any(Notice.class))).thenAnswer(invocation -> {
            Notice saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 10L);
            ReflectionTestUtils.setField(saved, "createdAt", OffsetDateTime.now());
            ReflectionTestUtils.setField(saved, "updatedAt", OffsetDateTime.now());
            return saved;
        });

        noticeService.createNotice(new NoticeRequestDto("  제목  ", "  본문  ", true, null));

        ArgumentCaptor<Notice> captor = ArgumentCaptor.forClass(Notice.class);
        verify(noticeRepository).save(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("제목");
        assertThat(captor.getValue().getContent()).isEqualTo("본문");
        assertThat(captor.getValue().isPinned()).isTrue();
        assertThat(captor.getValue().isPublished()).isTrue();
    }

    private Notice notice(Long id, String title, boolean published) {
        Notice notice = new Notice();
        ReflectionTestUtils.setField(notice, "id", id);
        ReflectionTestUtils.setField(notice, "createdAt", OffsetDateTime.now());
        ReflectionTestUtils.setField(notice, "updatedAt", OffsetDateTime.now());
        notice.setTitle(title);
        notice.setContent("본문");
        notice.setPublished(published);
        return notice;
    }
}
