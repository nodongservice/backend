package com.bridgework.sync.repository;

import com.bridgework.sync.entity.PublicDataRecord;
import com.bridgework.sync.entity.PublicDataSourceType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PublicDataRecordRepository extends JpaRepository<PublicDataRecord, Long> {

    Optional<PublicDataRecord> findBySourceTypeAndExternalId(PublicDataSourceType sourceType, String externalId);

    Page<PublicDataRecord> findBySourceTypeOrderByUpdatedAtDesc(PublicDataSourceType sourceType, Pageable pageable);

    List<PublicDataRecord> findBySourceType(PublicDataSourceType sourceType);
}
