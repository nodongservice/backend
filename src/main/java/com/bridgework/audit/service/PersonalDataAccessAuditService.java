package com.bridgework.audit.service;

import com.bridgework.audit.entity.PersonalDataAccessAudit;
import com.bridgework.audit.repository.PersonalDataAccessAuditRepository;
import org.springframework.stereotype.Service;

@Service
public class PersonalDataAccessAuditService {

    private final PersonalDataAccessAuditRepository auditRepository;

    public PersonalDataAccessAuditService(PersonalDataAccessAuditRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    public void record(Long adminUserId,
                       String actionType,
                       Long targetUserId,
                       Long targetProfileId,
                       String requestIp,
                       String accessOutcome,
                       String reason) {
        PersonalDataAccessAudit audit = new PersonalDataAccessAudit();
        audit.setAdminUserId(adminUserId);
        audit.setActionType(actionType);
        audit.setTargetUserId(targetUserId);
        audit.setTargetProfileId(targetProfileId);
        audit.setRequestIp(requestIp);
        audit.setAccessOutcome(accessOutcome);
        audit.setReason(reason);
        auditRepository.save(audit);
    }
}
