package com.bridgework.sync.repository;

import com.bridgework.sync.entity.PublicDataSourceType;
import com.bridgework.sync.entity.PublicDataSyncLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PublicDataSyncLogRepository extends JpaRepository<PublicDataSyncLog, Long> {

    List<PublicDataSyncLog> findTop20ByOrderByStartedAtDesc();

    List<PublicDataSyncLog> findTop20BySourceTypeOrderByStartedAtDesc(PublicDataSourceType sourceType);
}
