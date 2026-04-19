package com.bridgework.sync.repository;

import com.bridgework.sync.entity.PublicDataRecordField;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PublicDataRecordFieldRepository extends JpaRepository<PublicDataRecordField, Long> {

    void deleteByRecord_Id(Long recordId);

    List<PublicDataRecordField> findByRecord_IdIn(Collection<Long> recordIds);

    List<PublicDataRecordField> findByRecord_IdOrderByFieldPathAsc(Long recordId);
}
