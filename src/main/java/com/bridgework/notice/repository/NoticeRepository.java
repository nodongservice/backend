package com.bridgework.notice.repository;

import com.bridgework.notice.entity.Notice;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoticeRepository extends JpaRepository<Notice, Long> {

    List<Notice> findByPublishedTrueOrderByPinnedDescCreatedAtDescIdDesc(Pageable pageable);

    List<Notice> findAllByOrderByPinnedDescCreatedAtDescIdDesc(Pageable pageable);
}
