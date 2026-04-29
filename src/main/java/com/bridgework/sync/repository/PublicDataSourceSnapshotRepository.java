package com.bridgework.sync.repository;

import com.bridgework.sync.entity.PublicDataSourceSnapshot;
import com.bridgework.sync.entity.PublicDataSourceType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PublicDataSourceSnapshotRepository extends JpaRepository<PublicDataSourceSnapshot, PublicDataSourceType> {
}
