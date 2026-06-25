package com.bridgework.notice.service;

import com.bridgework.notice.dto.NoticeRequestDto;
import com.bridgework.notice.dto.NoticeResponseDto;
import com.bridgework.notice.entity.Notice;
import com.bridgework.notice.exception.NoticeDomainException;
import com.bridgework.notice.repository.NoticeRepository;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NoticeService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final NoticeRepository noticeRepository;

    public NoticeService(NoticeRepository noticeRepository) {
        this.noticeRepository = noticeRepository;
    }

    @Transactional(readOnly = true)
    public List<NoticeResponseDto> getPublicNotices(int limit) {
        return noticeRepository.findByPublishedTrueOrderByPinnedDescCreatedAtDescIdDesc(pageRequest(limit)).stream()
                .map(NoticeResponseDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public NoticeResponseDto getPublicNotice(Long noticeId) {
        Notice notice = getNotice(noticeId);
        if (!notice.isPublished()) {
            throw notFound();
        }
        return NoticeResponseDto.from(notice);
    }

    @Transactional(readOnly = true)
    public List<NoticeResponseDto> getAdminNotices(int limit) {
        return noticeRepository.findAllByOrderByPinnedDescCreatedAtDescIdDesc(pageRequest(limit)).stream()
                .map(NoticeResponseDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public NoticeResponseDto getAdminNotice(Long noticeId) {
        return NoticeResponseDto.from(getNotice(noticeId));
    }

    @Transactional
    public NoticeResponseDto createNotice(NoticeRequestDto request) {
        Notice notice = new Notice();
        applyRequest(notice, request);
        return NoticeResponseDto.from(noticeRepository.save(notice));
    }

    @Transactional
    public NoticeResponseDto updateNotice(Long noticeId, NoticeRequestDto request) {
        Notice notice = getNotice(noticeId);
        applyRequest(notice, request);
        return NoticeResponseDto.from(notice);
    }

    @Transactional
    public void deleteNotice(Long noticeId) {
        Notice notice = getNotice(noticeId);
        noticeRepository.delete(notice);
    }

    private Notice getNotice(Long noticeId) {
        return noticeRepository.findById(noticeId).orElseThrow(this::notFound);
    }

    private void applyRequest(Notice notice, NoticeRequestDto request) {
        notice.setTitle(request.title().trim());
        notice.setContent(request.content().trim());
        notice.setPinned(Boolean.TRUE.equals(request.pinned()));
        notice.setPublished(request.published() == null || request.published());
    }

    private PageRequest pageRequest(int limit) {
        int safeLimit = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        return PageRequest.of(0, safeLimit);
    }

    private NoticeDomainException notFound() {
        return new NoticeDomainException("NOTICE_NOT_FOUND", HttpStatus.NOT_FOUND, "공지사항을 찾을 수 없습니다.");
    }
}
