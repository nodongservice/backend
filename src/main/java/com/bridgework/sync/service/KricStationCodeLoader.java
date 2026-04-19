package com.bridgework.sync.service;

import com.bridgework.sync.config.BridgeWorkSyncProperties;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;

@Component
public class KricStationCodeLoader {

    private static final String HEADER_RAIL_OPR_ISTT_CD = "RAIL_OPR_ISTT_CD";
    private static final String HEADER_RAIL_OPR_ISTT_NM = "RAIL_OPR_ISTT_NM";
    private static final String HEADER_LN_CD = "LN_CD";
    private static final String HEADER_LN_NM = "LN_NM";
    private static final String HEADER_STIN_CD = "STIN_CD";
    private static final String HEADER_STIN_NM = "STIN_NM";
    private static final String SEOUL_OPERATOR_NAME = "서울교통공사";

    private final BridgeWorkSyncProperties syncProperties;
    private final DataFormatter dataFormatter = new DataFormatter(Locale.KOREA);

    public KricStationCodeLoader(BridgeWorkSyncProperties syncProperties) {
        this.syncProperties = syncProperties;
    }

    public List<RailStationCode> loadStationCodes() {
        List<StationReference> stationReferences = loadStationReferences();
        List<RailStationCode> stationCodes = new ArrayList<>();
        for (StationReference stationReference : stationReferences) {
            stationCodes.add(new RailStationCode(
                    stationReference.railOprIsttCd(),
                    stationReference.lnCd(),
                    stationReference.stinCd()
            ));
        }
        return stationCodes;
    }

    public List<StationReference> loadRailStationReferences() {
        return loadStationReferences();
    }

    public List<String> loadSeoulStationNames() {
        List<StationReference> stationReferences = loadStationReferences();
        Set<String> stationNames = new LinkedHashSet<>();
        for (StationReference stationReference : stationReferences) {
            if (!isSeoulOperator(stationReference.railOprIsttNm())) {
                continue;
            }
            String stationName = stationReference.stinNm();
            if (stationName != null && !stationName.isBlank()) {
                stationNames.add(stationName.trim());
            }
        }
        return new ArrayList<>(stationNames);
    }

    private List<StationReference> loadStationReferences() {
        String filePath = syncProperties.getKricStationCodeFilePath();
        if (filePath == null || filePath.isBlank()) {
            throw new IllegalStateException("국가철도공단 역사 코드 파일 경로가 비어 있습니다.");
        }

        Path path = Path.of(filePath);
        if (!Files.exists(path)) {
            throw new IllegalStateException("국가철도공단 역사 코드 파일을 찾을 수 없습니다: " + path);
        }

        try (InputStream inputStream = Files.newInputStream(path);
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            List<StationReference> stationReferences = readFromWorkbook(workbook);
            if (stationReferences.isEmpty()) {
                throw new IllegalStateException("국가철도공단 역사 코드 파일에 유효한 데이터가 없습니다.");
            }
            return stationReferences;
        } catch (Exception exception) {
            throw new IllegalStateException("국가철도공단 역사 코드 파일 파싱 실패: " + path, exception);
        }
    }

    private List<StationReference> readFromWorkbook(Workbook workbook) {
        Sheet sheet = workbook.getSheetAt(0);
        Row headerRow = sheet.getRow(sheet.getFirstRowNum());
        if (headerRow == null) {
            return List.of();
        }

        Map<String, Integer> headerIndexMap = buildHeaderIndexMap(headerRow);
        Integer railIndex = headerIndexMap.get(HEADER_RAIL_OPR_ISTT_CD);
        Integer railNameIndex = headerIndexMap.get(HEADER_RAIL_OPR_ISTT_NM);
        Integer lineIndex = headerIndexMap.get(HEADER_LN_CD);
        Integer lineNameIndex = headerIndexMap.get(HEADER_LN_NM);
        Integer stationIndex = headerIndexMap.get(HEADER_STIN_CD);
        Integer stationNameIndex = headerIndexMap.get(HEADER_STIN_NM);

        if (railIndex == null || lineIndex == null || stationIndex == null) {
            throw new IllegalStateException(
                    "역사 코드 파일 필수 컬럼이 없습니다. 필요 컬럼: "
                            + HEADER_RAIL_OPR_ISTT_CD + ", " + HEADER_LN_CD + ", " + HEADER_STIN_CD
            );
        }

        Map<String, StationReference> dedupedCodes = new LinkedHashMap<>();

        for (int rowIndex = headerRow.getRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }

            String railOprIsttCd = readCell(row, railIndex);
            String lnCd = readCell(row, lineIndex);
            String stinCd = readCell(row, stationIndex);

            // 필수 코드가 하나라도 비어 있으면 API 호출 파라미터가 불완전해져서 해당 행은 제외한다.
            if (railOprIsttCd.isBlank() || lnCd.isBlank() || stinCd.isBlank()) {
                continue;
            }

            String railOprIsttNm = readOptionalCell(row, railNameIndex);
            String lnNm = readOptionalCell(row, lineNameIndex);
            String stinNm = readOptionalCell(row, stationNameIndex);

            String dedupeKey = railOprIsttCd + "|" + lnCd + "|" + stinCd;
            dedupedCodes.put(dedupeKey, new StationReference(
                    railOprIsttCd,
                    railOprIsttNm,
                    lnCd,
                    lnNm,
                    stinCd,
                    stinNm
            ));
        }

        return new ArrayList<>(dedupedCodes.values());
    }

    private boolean isSeoulOperator(String railOprIsttNm) {
        return railOprIsttNm != null && railOprIsttNm.contains(SEOUL_OPERATOR_NAME);
    }

    private String readOptionalCell(Row row, Integer index) {
        if (index == null) {
            return "";
        }
        return readCell(row, index);
    }

    private Map<String, Integer> buildHeaderIndexMap(Row headerRow) {
        Map<String, Integer> headerIndexMap = new LinkedHashMap<>();
        for (Cell cell : headerRow) {
            String header = normalizeHeader(dataFormatter.formatCellValue(cell));
            if (!header.isBlank()) {
                headerIndexMap.put(header, cell.getColumnIndex());
            }
        }
        return headerIndexMap;
    }

    private String normalizeHeader(String header) {
        if (header == null) {
            return "";
        }
        return header.trim().toUpperCase(Locale.ROOT);
    }

    private String readCell(Row row, int index) {
        Cell cell = row.getCell(index, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) {
            return "";
        }
        return dataFormatter.formatCellValue(cell).trim();
    }

    public record RailStationCode(String railOprIsttCd, String lnCd, String stinCd) {
    }

    public record StationReference(
            String railOprIsttCd,
            String railOprIsttNm,
            String lnCd,
            String lnNm,
            String stinCd,
            String stinNm
    ) {
    }
}
