package com.bridgework.sync.service;

import com.bridgework.sync.dto.PublicDataRecordPageResponseDto;
import com.bridgework.sync.dto.PublicDataRecordResponseDto;
import com.bridgework.sync.entity.PublicDataRecord;
import com.bridgework.sync.entity.PublicDataSourceType;
import com.bridgework.sync.exception.PublicDataRecordNotFoundException;
import com.bridgework.sync.repository.PublicDataRecordRepository;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicDataRecordQueryService {

    private final PublicDataRecordRepository publicDataRecordRepository;
    private final PublicDataRecordFieldService publicDataRecordFieldService;

    public PublicDataRecordQueryService(PublicDataRecordRepository publicDataRecordRepository,
                                        PublicDataRecordFieldService publicDataRecordFieldService) {
        this.publicDataRecordRepository = publicDataRecordRepository;
        this.publicDataRecordFieldService = publicDataRecordFieldService;
    }

    @Transactional(readOnly = true)
    public PublicDataRecordPageResponseDto getRecords(PublicDataSourceType sourceType,
                                                      int page,
                                                      int size,
                                                      boolean includePayload) {
        PageRequest pageRequest = PageRequest.of(page, size);
        Page<PublicDataRecord> resultPage = publicDataRecordRepository
                .findBySourceTypeOrderByUpdatedAtDesc(sourceType, pageRequest);

        List<PublicDataRecord> records = resultPage.getContent();
        List<Long> recordIds = records.stream().map(PublicDataRecord::getId).toList();
        Map<Long, Map<String, String>> fieldMapByRecordId = publicDataRecordFieldService.getFieldMapByRecordIds(recordIds);

        List<PublicDataRecordResponseDto> responseRecords = records.stream()
                .map(record -> toDto(record, fieldMapByRecordId.getOrDefault(record.getId(), Map.of()), includePayload))
                .toList();

        return new PublicDataRecordPageResponseDto(
                resultPage.getNumber(),
                resultPage.getSize(),
                resultPage.getTotalElements(),
                resultPage.getTotalPages(),
                responseRecords
        );
    }

    @Transactional(readOnly = true)
    public PublicDataRecordResponseDto getRecord(Long recordId, boolean includePayload) {
        PublicDataRecord record = publicDataRecordRepository.findById(recordId)
                .orElseThrow(() -> new PublicDataRecordNotFoundException(recordId));

        Map<String, String> fieldMap = publicDataRecordFieldService.getFieldMapByRecordId(recordId);
        return toDto(record, fieldMap, includePayload);
    }

    private PublicDataRecordResponseDto toDto(PublicDataRecord record,
                                              Map<String, String> fieldMap,
                                              boolean includePayload) {
        return new PublicDataRecordResponseDto(
                record.getId(),
                record.getSourceType(),
                record.getExternalId(),
                record.getRawFetchedAt(),
                record.getUpdatedAt(),
                fieldMap,
                includePayload ? record.getPayloadJson() : null
        );
    }
}
