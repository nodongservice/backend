package com.bridgework.sync.repository;

import com.bridgework.sync.entity.PublicDataRecord;
import com.bridgework.sync.entity.PublicDataSourceType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface PublicDataRecordRepository extends JpaRepository<PublicDataRecord, Long> {

    interface RecordIdentityView {
        Long getId();
        String getExternalId();
    }

    Optional<PublicDataRecord> findBySourceTypeAndExternalId(PublicDataSourceType sourceType, String externalId);

    Page<PublicDataRecord> findBySourceTypeOrderByUpdatedAtDesc(PublicDataSourceType sourceType, Pageable pageable);

    List<PublicDataRecord> findBySourceType(PublicDataSourceType sourceType);

    @Query("SELECT r.id AS id, r.externalId AS externalId FROM PublicDataRecord r WHERE r.sourceType = :sourceType")
    List<RecordIdentityView> findRecordIdentityBySourceType(@Param("sourceType") PublicDataSourceType sourceType);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query(value = "DELETE FROM public_data_record WHERE id IN (:ids)", nativeQuery = true)
    int deleteAllByIdInNative(@Param("ids") Collection<Long> ids);
}
