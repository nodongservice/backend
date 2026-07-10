package com.bridgework.audit.repository;

import com.bridgework.audit.entity.PersonalDataAccessAudit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonalDataAccessAuditRepository extends JpaRepository<PersonalDataAccessAudit, Long> {
}
