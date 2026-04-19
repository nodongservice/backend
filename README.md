# BridgeWork Backend

브릿지워크 공공데이터 동기화 백엔드입니다.

## 기술 스택
- Java 17
- Spring Boot 3.3
- Spring Data JPA + Flyway
- PostgreSQL(H2 로컬 대체)
- ShedLock(분산 스케줄 중복 방지)

## 도메인 구조
`com.bridgework.sync`
- `controller`
- `service`
- `repository`
- `entity`
- `dto`
- `exception`

## 프로필/키 관리
- 공통: `application.yml` (공개 가능한 공통 설정)
- 로컬: `application-local.yml` (로컬 하드코딩 키)
- 운영: `application-prod.yml` (`${}` 기반 시크릿 참조)

### 운영 필수 환경변수
- `DATA_GO_KR_SERVICE_KEY`
- `KRIC_SERVICE_KEY`
- `KRIC_STATION_CODE_XLSX_PATH`
- `WORK24_VOCATIONAL_TRAINING_AUTH_KEY`
- `WORK24_COMPETENCY_AUTH_KEY`
- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `DB_DRIVER`

### 역사 코드 파일(프로젝트 포함)
- 기본 포함 파일: `backend/resources/reference/operating_agency_station_codes_2026-02-28.xlsx`
- 로컬 기본 경로와 `scripts/.env`는 위 파일을 사용하도록 설정됨

### 실행 예시
- 로컬: `SPRING_PROFILES_ACTIVE=local`
- 운영: `SPRING_PROFILES_ACTIVE=prod`

## 동기화 대상 데이터

| SourceType | 데이터명 | 안내 링크 | 실제 호출 Endpoint | 인증키 | 주요 파라미터 |
|---|---|---|---|---|---|
| `KEPAD_RECRUITMENT` | 한국장애인고용공단_장애인 구인 실시간 현황 | [15117692](https://www.data.go.kr/data/15117692/openapi.do) | `http://apis.data.go.kr/B552583/job/job_list_env` | data.go.kr 서비스키 | `serviceKey`, `pageNo`, `numOfRows(max=1000)`, `_type=json` |
| `KEPAD_JOB_CATEGORY` | 한국장애인고용공단_장애인 고용직무분류 | [15157071](https://www.data.go.kr/data/15157071/openapi.do) | `http://apis.data.go.kr/B552583/jobcode/job_code` | data.go.kr 서비스키 | `serviceKey`, `pageNo`, `numOfRows(max=1000)`, `_type=json` |
| `KEPAD_STANDARD_WORKPLACE` | 한국장애인고용공단_장애인 표준사업장 실시간 조회 | [15119304](https://www.data.go.kr/data/15119304/openapi.do) | `http://apis.data.go.kr/B552583/comp/comp_auth` | data.go.kr 서비스키 | `serviceKey`, `pageNo`, `numOfRows(max=1000)`, `_type=json` |
| `KEPAD_SUPPORT_AGENCY` | 한국장애인고용공단_근로지원인 수행기관 실시간 정보 | [15131282](https://www.data.go.kr/data/15131282/openapi.do) | `http://apis.data.go.kr/B552583/instn/instn_list` | data.go.kr 서비스키 | `serviceKey`, `pageNo`, `numOfRows(max=1000)`, `_type=json` |
| `TRANSPORT_SUPPORT_CENTER` | 전국교통약자이동지원센터정보표준데이터 | [15028207](https://www.data.go.kr/tcs/dss/selectStdDataDetailView.do?publicDataPk=15028207) | `https://api.data.go.kr/openapi/tn_pubr_public_tfcwker_mvmn_cnter_api` | data.go.kr 서비스키 | `serviceKey`, `pageNo`, `numOfRows(max=1000)`, `type=json` |
| `RAIL_WHEELCHAIR_LIFT` | 국가철도공단_역사별 휠체어리프트 위치 | [15041686](https://www.data.go.kr/data/15041686/openapi.do) | `https://openapi.kric.go.kr/openapi/vulnerableUserInfo/stationWheelchairLiftLocation` | KRIC 서비스키 | `service=vulnerableUserInfo`, `operation=stationWheelchairLiftLocation`, `serviceKey`, `railOprIsttCd`, `lnCd`, `stinCd`, `format=json` |
| `SEOUL_WHEELCHAIR_LIFT` | 서울교통공사_휠체어리프트 설치현황 | [15044262](https://www.data.go.kr/data/15044262/fileData.do) | `https://api.odcloud.kr/api/{publicDataPk}/v1/{publicDataDetailPk}` (fileData 페이지에서 식별자 추출 후 호출) | data.go.kr 서비스키 | `serviceKey`, `page`, `perPage(max=10000)`, `returnType=JSON`, `역명`(xlsx의 `STIN_NM` 기반 순회) |
| `VOCATIONAL_TRAINING` | 한국고용정보원_직업훈련_국민내일배움카드 훈련과정 | [work24 000004](https://www.work24.go.kr/cm/e/a/0110/selectOpenApiSvcInfo.do?apiSvcId=&upprApiSvcId=&fullApiSvcId=000000000000000000000000000004) | `https://www.work24.go.kr/cm/openApi/call/hr/callOpenApiSvcInfo310L01.do` | Work24 인증키 | `authKey`, `returnType=XML`, `pageNum`, `pageSize(max=100)` |
| `JOBSEEKER_COMPETENCY_PROGRAM` | 한국고용정보원_구직자취업역량 강화프로그램 | [work24 000098](https://www.work24.go.kr/cm/e/a/0110/selectOpenApiSvcInfo.do?apiSvcId=&upprApiSvcId=&fullApiSvcId=000000000000000000000000000098) | `https://www.work24.go.kr/cm/openApi/call/wk/callOpenApiSvcInfo217L01.do` | Work24 인증키 | `authKey`, `returnType=XML`, `startPage`, `display(max=100)`, `pgmStdt(YYYYMMDD, 오늘~1개월 후 반복)` |

## 데이터 저장 방식
- `public_data_record`: 원본 payload(JSON), 해시, 외부ID, 수집시각 저장
- `public_data_record_field`: payload를 `field_path` 단위로 펼쳐 저장
- 변경건만 payload/필드 재저장, 동일건은 수집시각만 갱신
- 각 소스 전체 페이지 수집이 끝난 뒤 API 결과에 없는 기존 데이터는 DB에서 삭제

## 스케줄러
- Cron: `bridgework.sync.cron`
- 기본값: `0 0/30 * * * *`
- ShedLock 적용으로 다중 인스턴스 중복 실행 방지
- 페이징은 API별 최대 페이지 크기로 조회하고 마지막 페이지까지 순회

## 수동 실행/조회 API
- 전체 동기화: `POST /api/v1/sync/public-data/run`
- 단일 동기화: `POST /api/v1/sync/public-data/run?sourceType=KEPAD_RECRUITMENT`
- 동기화 로그: `GET /api/v1/sync/public-data/logs`
- 소스 설정: `GET /api/v1/sync/public-data/sources`
- 저장 레코드 목록: `GET /api/v1/public-data/records?sourceType=KEPAD_RECRUITMENT&page=0&size=20&includePayload=false`
- 저장 레코드 상세: `GET /api/v1/public-data/records/{recordId}?includePayload=true`

## CSV 내보내기 스크립트
- 파일: `scripts/export_public_data_to_csv.py`
- 목적: 모든 동기화 대상 데이터를 API에서 직접 호출해 데이터별 CSV 저장
- 키 주입: `scripts/.env`에 직접 설정
- 필요 환경변수: `DATA_GO_KR_SERVICE_KEY`, `KRIC_SERVICE_KEY`, `KRIC_STATION_CODE_XLSX_PATH`, `WORK24_VOCATIONAL_TRAINING_AUTH_KEY`, `WORK24_COMPETENCY_AUTH_KEY`
- `KRIC_STATION_CODE_XLSX_PATH` 파일은 `국가철도공단`과 `서울교통공사` 수집 시 모두 사용
- 실행 예시: `python3 scripts/export_public_data_to_csv.py --output-dir exports --page-size 0 --max-pages 0`
- 무데이터/오류 메시지는 CSV 행으로 기록하지 않음
- 실행 로그에 요청 URL(파라미터 포함), 재시도, 감지 건수(`detected`)를 출력
- 특정 데이터명만 실행(한글명/영문 slug(csv 파일명) 둘 다 지원): `python3 scripts/export_public_data_to_csv.py --only-data-name "국가철도공단_역사별 휠체어리프트 위치"`
- 특정 데이터명 여러개 실행: `python3 scripts/export_public_data_to_csv.py --only-data-name "한국장애인고용공단_장애인 구인 실시간 현황" --only-data-name "seoul_wheelchair_lift"`
- 필요 라이브러리: `pip install requests pandas`
