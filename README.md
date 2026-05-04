# BridgeWork Backend

브릿지워크 인증/프로필/공공데이터 동기화 백엔드입니다.

## 기술 스택
- Java 17
- Spring Boot 3.3
- Spring Data JPA + Flyway
- PostgreSQL
- Spring Security + JWT
- Redis
- ShedLock(분산 스케줄 중복 방지)

## 도메인 구조
`com.bridgework.auth`
- `controller`
- `service`
- `repository`
- `entity`
- `dto`
- `exception`

`com.bridgework.onboarding` (프로필)
- `controller`
- `service`
- `repository`
- `entity`
- `dto`
- `exception`

`com.bridgework.options`
- `controller`
- `service`
- `dto`

`com.bridgework.map`
- `controller`
- `service`
- `dto`

`com.bridgework.sync`
- `controller`
- `service`
- `repository`
- `entity`
- `dto`
- `exception`

## 현재 구현 범위
- 기능 0: 소셜 로그인/회원가입 완료, JWT 재발급/로그아웃/내 정보 조회
- 기능 1: 프로필 CRUD(최대 3개), 기본 프로필 지정/변경
- 기능 1-2(OCR): **2차 개발로 제외**
- 공공데이터: 스케줄러 동기화 + 수동 실행 + 원본/정규화 저장
- 화면 옵션/지도 레이어: 직무 트리, 지역/고용형태/급여방식 옵션, 근로지원인 수행기관 마커 조회

## 프로필/키 관리
- 공통: `application.yml` (공개 가능한 공통 설정)
- 로컬: `application-local.yml` (로컬 하드코딩 키)
- 운영: `application-prod.yml` (민감정보만 `${}` 시크릿 참조, 비민감값은 local과 동일 고정값)

### 운영 필수 환경변수
- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `REDIS_PASSWORD`
- `DATA_GO_KR_SERVICE_KEY`
- `KRIC_SERVICE_KEY`
- `SEOUL_OPEN_API_KEY`
- `WORK24_VOCATIONAL_TRAINING_AUTH_KEY`
- `WORK24_COMPETENCY_AUTH_KEY`
- `NAVER_GEOCODE_API_KEY_ID`
- `NAVER_GEOCODE_API_KEY`
- `BRIDGEWORK_AUTH_JWT_SECRET`
- `KAKAO_CLIENT_SECRET`
- `NAVER_CLIENT_SECRET`

### 역사 코드 파일(프로젝트 포함)
- 기본 포함 파일: `backend/resources/reference/operating_agency_station_codes_2026-02-28.xlsx`
- 로컬 기본 경로와 `scripts/.env`는 위 파일을 사용하도록 설정됨

### 실행 예시
- 로컬: `SPRING_PROFILES_ACTIVE=local`
- 운영: `SPRING_PROFILES_ACTIVE=prod`

## CI/CD (main -> EC2 무중단 배포)
- 워크플로우: `.github/workflows/cicd-main-ec2.yml`
- 트리거: `main` 브랜치 push
- 방식: `Blue/Green` 컨테이너(`18080`, `18081`) 전환 + `nginx reload`
- DB: 컨테이너 DB 미사용, `RDS PostgreSQL` 접속(`application-prod.yml` 외부 파일 마운트)
- Nginx 라우팅/Swagger 분기 기준: `deploy/NGINX.md`
- FastAPI 별도 배포 시 upstream 전환 스크립트: `deploy/fastapi_blue_green_switch.sh`
- Redis: 배포 스크립트에서 `bridgework-redis` 컨테이너를 자동 생성/기동(동일 Docker network)

### GitHub Secrets
- `EC2_HOST`
- `EC2_PORT`
- `EC2_USER`
- `EC2_SSH_PRIVATE_KEY`
- `GHCR_READ_TOKEN` (`read:packages`)
- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `REDIS_PASSWORD`
- `DATA_GO_KR_SERVICE_KEY`
- `KRIC_SERVICE_KEY`
- `WORK24_VOCATIONAL_TRAINING_AUTH_KEY`
- `WORK24_COMPETENCY_AUTH_KEY`
- `SEOUL_OPEN_API_KEY`
- `NAVER_GEOCODE_API_KEY_ID`
- `NAVER_GEOCODE_API_KEY`
- `BRIDGEWORK_AUTH_JWT_SECRET`
- `KAKAO_CLIENT_SECRET`
- `NAVER_CLIENT_SECRET`

### 생성 방식
- GitHub Actions가 위 개별 Secrets를 읽어 `application-prod.yml`을 런타임에 생성
- 생성 파일을 EC2 `~/bridgework/application-prod.yml`로 업로드
- 컨테이너 실행 시 `/app/config/application-prod.yml`로 read-only 마운트

### EC2 선행 작업
1. Docker, Nginx, curl 설치
2. 배포 계정에 Docker 실행 권한 부여 (`docker` 그룹)
3. 배포 계정에 `sudo nginx -t`, `sudo systemctl reload nginx`, `sudo cp` 권한 부여
4. 최초 1회: `deploy/setup_nginx.sh` 실행

## 동기화 대상 데이터

| SourceType(데이터명)                                                  | 안내 링크 | 실제 호출 Endpoint | 인증키 | 주요 파라미터 |
|-------------------------------------------------------------------|---|---|---|---|
| `KEPAD_RECRUITMENT` (한국장애인고용공단_장애인 구인 실시간 현황)                     | [15117692](https://www.data.go.kr/data/15117692/openapi.do) | `http://apis.data.go.kr/B552583/job/job_list_env` | data.go.kr 서비스키 | `serviceKey`, `pageNo`, `numOfRows(max=1000)`, `_type=json` |
| `KEPAD_JOB_CATEGORY` (한국장애인고용공단_장애인 고용직무분류)                       | [15157071](https://www.data.go.kr/data/15157071/openapi.do) | `http://apis.data.go.kr/B552583/jobcode/job_code` | data.go.kr 서비스키 | `serviceKey`, `pageNo`, `numOfRows(max=1000)`, `_type=json` |
| `KEPAD_STANDARD_WORKPLACE` (한국장애인고용공단_장애인 표준사업장 실시간 조회)           | [15119304](https://www.data.go.kr/data/15119304/openapi.do) | `http://apis.data.go.kr/B552583/comp/comp_auth` | data.go.kr 서비스키 | `serviceKey`, `pageNo`, `numOfRows(max=1000)`, `_type=json` |
| `KEPAD_SUPPORT_AGENCY` (한국장애인고용공단_근로지원인 수행기관 실시간 정보)              | [15131282](https://www.data.go.kr/data/15131282/openapi.do) | `http://apis.data.go.kr/B552583/instn/instn_list` | data.go.kr 서비스키 | `serviceKey`, `pageNo`, `numOfRows(max=1000)`, `_type=json` |
| `KORAIL_WEEK_PERSON_FACILITIES` (한국철도공사_편의시설정보(교통약자 편의시설))        | [15125774](https://www.data.go.kr/data/15125774/openapi.do#/API%20목록/weekPersonFacilities) | `https://apis.data.go.kr/B551457/convenience/weekPersonFacilities` | data.go.kr 서비스키 | `serviceKey`, `pageNo`, `numOfRows(max=1000)`, `returnType=JSON` |
| `SEOUL_TRANSPORT_WEAK_WHEELCHAIR_LIFT` (서울교통공사_교합통약자이용정보(휠체어리프트)) | [15143843](https://www.data.go.kr/data/15143843/openapi.do#/) | `https://apis.data.go.kr/B553766/wksn/getWksnWhcllift` | data.go.kr 서비스키 | `serviceKey`, `pageNo`, `numOfRows(max=1000)`, `dataType=JSON` |
| `TRANSPORT_SUPPORT_CENTER` (전국교통약자이동지원센터정보표준데이터)                  | [15028207](https://www.data.go.kr/tcs/dss/selectStdDataDetailView.do?publicDataPk=15028207) | `https://api.data.go.kr/openapi/tn_pubr_public_tfcwker_mvmn_cnter_api` | data.go.kr 서비스키 | `serviceKey`, `pageNo`, `numOfRows(max=1000)`, `type=json` |
| `RAIL_WHEELCHAIR_LIFT` (국가철도공단_역사별 휠체어리프트 위치)                     | [15041686](https://www.data.go.kr/data/15041686/openapi.do) | `https://openapi.kric.go.kr/openapi/vulnerableUserInfo/stationWheelchairLiftLocation` | `KRIC_SERVICE_KEY` | `service=vulnerableUserInfo`, `operation=stationWheelchairLiftLocation`, `serviceKey`, `railOprIsttCd`, `lnCd`, `stinCd`, `format=json` |
| `RAIL_WHEELCHAIR_LIFT_MOVEMENT` (역사별 휠체어리프트 이동동선)                 | [KRIC 209](https://data.kric.go.kr/rips/M_01_02/detail.do?id=209&service=vulnerableUserInfo&operation=stationWheelchairLiftMovement) | `https://openapi.kric.go.kr/openapi/vulnerableUserInfo/stationWheelchairLiftMovement` | `KRIC_SERVICE_KEY` | `service=vulnerableUserInfo`, `operation=stationWheelchairLiftMovement`, `serviceKey`, `railOprIsttCd`, `lnCd`, `stinCd`, `format=json` |
| `SEOUL_WHEELCHAIR_LIFT` (서울교통공사_휠체어리프트 설치현황)                      | [15044262](https://www.data.go.kr/data/15044262/fileData.do) | `https://api.odcloud.kr/api/{publicDataPk}/v1/{publicDataDetailPk}` (fileData 페이지에서 식별자 추출 후 호출) | data.go.kr 서비스키 | `serviceKey`, `page`, `perPage(max=10000)`, `returnType=JSON`, `역명`(xlsx의 `STIN_NM` 기반 순회) |
| `SEOUL_SUBWAY_ENTRANCE_LIFT` (서울시 지하철 출입구 리프트 위치정보)               | [OA-21211](https://data.seoul.go.kr/dataList/OA-21211/S/1/datasetView.do) | `http://openapi.seoul.go.kr:8088/{API_KEY}/json/tbTraficEntrcLft/{start}/{end}` | data.seoul.go.kr 키 | `start/end(페이지 범위)`, `max rows=1000` |
| `SEOUL_WALKING_NETWORK` (서울특별시_자치구별 도보 네트워크 공간정보)                 | [OA-21208](https://data.seoul.go.kr/dataList/OA-21208/S/1/datasetView.do) | `http://openapi.seoul.go.kr:8088/{API_KEY}/json/TbTraficWlkNet/{start}/{end}` | data.seoul.go.kr 키 | `start/end(페이지 범위)`, `max rows=1000` |
| `NATIONWIDE_BUS_STOP` (국토교통부_전국 버스정류장 위치정보)                       | [15067528](https://www.data.go.kr/data/15067528/fileData.do#tab-layer-openapi) | `https://api.odcloud.kr/api/{publicDataPk}/v1/{publicDataDetailPk}` (fileData 페이지에서 식별자 추출 후 호출) | data.go.kr 서비스키 | `serviceKey`, `page`, `perPage(max=10000)`, `returnType=JSON` |
| `SEOUL_WHEELCHAIR_RAMP_STATUS` (서울교통공사_휠체어경사로 설치 현황)              | [OA-13116](https://data.seoul.go.kr/dataList/OA-13116/S/1/datasetView.do) | `https://datafile.seoul.go.kr/bigfile/iot/inf/nio_download.do` (datasetView의 파일목록에서 최신 `수정일` 1건 선택 후 다운로드) | 없음 | `infId`, `infSeq`, `seq`, `seqNo`, `useCache=false` |
| `SEOUL_LOW_FLOOR_BUS_ROUTE_RETENTION` (서울시 저상버스 도입 노선 및 노선별 보유율)  | [OA-22229](https://data.seoul.go.kr/dataList/OA-22229/F/1/datasetView.do) | `https://datafile.seoul.go.kr/bigfile/iot/inf/nio_download.do` (datasetView의 파일목록에서 최신 `수정일` 1건 선택 후 다운로드) | 없음 | `infId`, `infSeq`, `seq`, `seqNo`, `useCache=false` |
| `NATIONWIDE_TRAFFIC_LIGHT` (전국신호등표준데이터)                           | [15028198](https://www.data.go.kr/data/15028198/standard.do#) | `https://api.data.go.kr/openapi/tn_pubr_public_traffic_light_api` | data.go.kr 서비스키 | `serviceKey`, `pageNo`, `numOfRows(max=1000)`, `type=xml` |
| `NATIONWIDE_CROSSWALK` (전국횡단보도표준데이터)                              | [15028201](https://www.data.go.kr/data/15028201/standard.do) | `https://api.data.go.kr/openapi/tn_pubr_public_crosswalk_api` | data.go.kr 서비스키 | `serviceKey`, `pageNo`, `numOfRows(max=1000)`, `type=json` |
| `VOCATIONAL_TRAINING` (한국고용정보원_직업훈련_국민내일배움카드 훈련과정)                | [work24 000004](https://www.work24.go.kr/cm/e/a/0110/selectOpenApiSvcInfo.do?apiSvcId=&upprApiSvcId=&fullApiSvcId=000000000000000000000000000004) | `https://www.work24.go.kr/cm/openApi/call/hr/callOpenApiSvcInfo310L01.do` | Work24 인증키 | `authKey`, `returnType=XML`, `pageNum`, `pageSize(max=100)` |
| `JOBSEEKER_COMPETENCY_PROGRAM` (한국고용정보원_구직자취업역량 강화프로그램)           | [work24 000098](https://www.work24.go.kr/cm/e/a/0110/selectOpenApiSvcInfo.do?apiSvcId=&upprApiSvcId=&fullApiSvcId=000000000000000000000000000098) | `https://www.work24.go.kr/cm/openApi/call/wk/callOpenApiSvcInfo217L01.do` | Work24 인증키 | `authKey`, `returnType=XML`, `startPage`, `display(max=100)`, `pgmStdt(YYYYMMDD, 오늘~1개월 후 반복)` |

## API 응답 컬럼(공식 소개 페이지 기준)
- 기준: 각 데이터 소개 페이지의 `출력결과(Response Element)`/`출력변수`/`데이터항목(컬럼) 정보`/`swaggerJson`
- 아래 표는 소개 페이지 원문의 컬럼명과 설명을 그대로 반영했으며, 미기재 항목은 빈 설명으로 둠
- `LN_NM`, `STIN_NM`은 역사코드 엑셀 기반 내부 보강 컬럼으로 소개 페이지 출력항목에는 없어 본 표에서 제외

### `KEPAD_RECRUITMENT` 한국장애인고용공단_장애인 구인 실시간 현황
- 링크: https://www.data.go.kr/data/15117692/openapi.do
- 컬럼 수: 27

| 컬럼명 | 설명 |
|---|---|
| `header.resultMsg` | 결과메시지 |
| `header.resultCode` | 결과코드 |
| `body.items.item.termDate` | 모집기간 |
| `body.items.item.busplaName` | 사업장명 |
| `body.items.item.cntctNo` | 연락처 |
| `body.items.item.compAddr` | 사업장주소 |
| `body.items.item.empType` | 고용형태 |
| `body.items.item.enterType` | 입사형태 |
| `body.items.item.envBothHands` | 작업환경_양손사용 |
| `body.items.item.envEyesight` | 작업환경_시력 |
| `body.items.item.envHandwork` | 작업환경_손작업 |
| `body.items.item.envLiftPower` | 작업환경_드는힘 |
| `body.items.item.envLstnTalk` | 작업환경_듣고 말하기 |
| `body.items.item.envStndWalk` | 작업환경_서거나 걷기 |
| `body.items.item.jobNm` | 모집직종 |
| `body.items.item.offerregDt` | 구인신청일자 |
| `body.items.item.regDt` | 등록일 |
| `body.items.item.regagnName` | 담당기관 |
| `body.items.item.reqCareer` | 요구경력 |
| `body.items.item.reqEduc` | 요구학력 |
| `body.items.item.rno` | 순번 |
| `body.items.item.rnum` | 순번 |
| `body.items.item.salary` | 임금 |
| `body.items.item.salaryType` | 임금형태 |
| `body.numOfRows` | 한 페이지 결과 수 |
| `body.pageNo` | 페이지 번호 |
| `body.totalCount` | 전체 결과 수 |

### `KEPAD_JOB_CATEGORY` 한국장애인고용공단_장애인 고용직무분류
- 링크: https://www.data.go.kr/data/15157071/openapi.do
- 컬럼 수: 13

| 컬럼명 | 설명 |
|---|---|
| `header.resultMsg` | 결과메시지 |
| `header.resultCode` | 결과코드 |
| `body.totalCount` | 전체 결과 수 |
| `body.numOfRows` | 한 페이지 결과 수 |
| `body.pageNo` | 페이지번호 |
| `body.items.item.jobCd` | 직종코드 |
| `body.items.item.jobCdLevel` | 직종명 |
| `body.items.item.jobCdNm` | 직종레벨 |
| `body.items.item.jobTask` | 수행업무 |
| `body.items.item.simlrJob` | 유사직무명 |
| `body.items.item.noticeCn` | 분류 시 주의사항 |
| `body.items.item.jobdevtipCn` | 취업알선 및 직무개발 tip |
| `body.items.item.rnum` | 연번 |

### `KEPAD_STANDARD_WORKPLACE` 한국장애인고용공단_장애인 표준사업장 실시간 조회
- 링크: https://www.data.go.kr/data/15119304/openapi.do
- 컬럼 수: 16

| 컬럼명 | 설명 |
|---|---|
| `header.resultCode` | 결과코드 |
| `header.resultMsg` | 결과메시지 |
| `body.items.item.address` | 소재지 주소 |
| `body.items.item.authDate` | 인증일자 |
| `body.items.item.compAuthId` | 등록번호 |
| `body.items.item.compBizNo` | 사업자등록번호 |
| `body.items.item.compName` | 기업명 |
| `body.items.item.compRegNo` | 인증번호 |
| `body.items.item.compTel` | 대표전화 |
| `body.items.item.presidentName` | 대표자 |
| `body.items.item.product` | 주요상품 |
| `body.items.item.rnum` | 순번 |
| `body.items.item.compTypeNm` | 인증유형 |
| `body.numOfRows` | 한 페이지 결과 수 |
| `body.pageNo` | 페이지 번호 |
| `body.totalCount` | 전체 결과 수 |

### `KEPAD_SUPPORT_AGENCY` 한국장애인고용공단_근로지원인 수행기관 실시간 정보
- 링크: https://www.data.go.kr/data/15131282/openapi.do
- 컬럼 수: 11

| 컬럼명 | 설명 |
|---|---|
| `header.resultCode` | 결과코드 |
| `header.resultMsg` | 결과메시지 |
| `body.items.item.excInstn` | 시도구분 |
| `body.items.item.excInstnAddr` | 수행기관 주소 |
| `body.items.item.excInstnFxno` | 수행기관 팩스번호 |
| `body.items.item.excInstnNm` | 수행기관명 |
| `body.items.item.excInstnTelno` | 수행기관 전화번호 |
| `body.items.item.rnum` | 순번 |
| `body.numOfRows` | 한 페이지 결과 수 |
| `body.pageNo` | 페이지번호 |
| `body.totalCount` | 전체 결과 수 |

### `KORAIL_WEEK_PERSON_FACILITIES` 한국철도공사_편의시설정보
- 링크: https://www.data.go.kr/data/15125774/openapi.do#/API%20목록/weekPersonFacilities
- 컬럼 수: 11

| 컬럼명 | 설명 |
|---|---|
| `response.header.resultCode` | 응답의 결과 코드 |
| `response.header.resultMsg` | 응답의 결과 메세지 |
| `response.body.dataType` | 응답의 데이터 타입 |
| `response.body.numOfRows` | 한 페이지 결과 수 |
| `response.body.pageNo` | 페이지번호 |
| `response.body.totalCount` | 전체 데이터 수 |
| `response.body.items.item[].pwdbs_slwy_estnc` | 장애인경사로유무 |
| `response.body.items.item[].pwdbs_tolt_estnc` | 장애인화장실유무 |
| `response.body.items.item[].stn_cd` | 역코드 |
| `response.body.items.item[].stn_nm` | 역명 |
| `response.body.items.item[].whlch_liftt_cnt` | 휠체어리프트수 |

### `SEOUL_TRANSPORT_WEAK_WHEELCHAIR_LIFT` 서울교통공사_교통약자이용정보
- 링크: https://www.data.go.kr/data/15143843/openapi.do#/
- 컬럼 수: 25

| 컬럼명 | 설명 |
|---|---|
| `header.resultMsg` | 결과메세지 |
| `header.resultCode` | 결과코드 |
| `body.items.item.oprtngSitu` | 가동현황 |
| `body.items.item.fcltNm` | 교통약자 이용시설명 |
| `body.items.item.lineNm` | 해당 지하철 호선명 |
| `body.items.item.stnCd` | 해당 지하철 역코드 |
| `body.items.item.stnNo` | 해당 지하철 역번호 |
| `body.items.item.stnNm` | 해당 지하철 역명 |
| `body.items.item.crtrYmd` | 기준일자 (일자형식: YYYYMMDD) |
| `body.items.item.elvtrSn` | 지하철 편의시설 - 승강기 일련번호 |
| `body.items.item.mngNo` | 지하철 편의시설 - 관리번호 |
| `body.items.item.vcntEntrcNo` | 교통약자 이용시설 - 근접 출입구 번호 |
| `body.items.item.bgngFlrGrndUdgdSe` | 교통약자 이용시설 - 시작층 지상지하구분 (지상/지하 구분0 |
| `body.items.item.bgngFlr` | 교통약자 이용시설 - 시작층 위치 |
| `body.items.item.bgngFlrDtlPstn` | 교통약자 이용시설 - 시작층 상세위치 |
| `body.items.item.endFlrGrndUdgdSe` | 교통약자 이용시설 - 종료층 지상지하구분 (지상/지하 구분) |
| `body.items.item.endFlr` | 교통약자 이용시설 - 위치 종료층 |
| `body.items.item.endFlrDtlPstn` | 교통약자 이용시설 - 종료층 상세위치 |
| `body.items.item.elvtrLen` | 교통약자 이용시설 - 길이 |
| `body.items.item.elvtrWdthBt` | 교통약자 이용시설 - 가로 넓이 |
| `body.items.item.limitWht` | 교통약자 이용시설 - 한계중량 |
| `body.items.item.fcltNo` | 교통약자 이용시설 - 가동현황 |
| `body.numOfRows` | 페이지 결과 수 |
| `body.pageNo` | 페이지 번호 |
| `body.totalCount` | 데이터 총 개수 |

### `TRANSPORT_SUPPORT_CENTER` 전국교통약자이동지원센터정보표준데이터
- 링크: https://www.data.go.kr/tcs/dss/selectStdDataDetailView.do?publicDataPk=15028207
- 컬럼 수: 31

| 컬럼명 | 설명 |
|---|---|
| `TFCWKER_MVMN_CNTER_NM` | 교통약자이동지원센터명 |
| `RDNMADR` | 소재지도로명주소 |
| `LNMADR` | 소재지지번주소 |
| `LATITUDE` | 위도 |
| `LONGITUDE` | 경도 |
| `CAR_HOLD_CO` | 보유차량대수 |
| `CAR_HOLD_KND` | 보유차량종류 |
| `SLOPE_VHCLE_CO` | 슬로프형휠체어차량대수 |
| `LIFT_VHCLE_CO` | 리프트형휠체어차량대수 |
| `RCEPT_PHONE_NUMBER` | 예약접수전화번호 |
| `RCEPT_ITNADR` | 예약접수인터넷주소 |
| `APP_SVC_NM` | 앱서비스명 |
| `WEEKDAY_RCEPT_OPEN_HHMM` | 평일예약접수운영시작시각 |
| `WEEKDAY_RCEPT_COLSE_HHMM` | 평일예약접수운영종료시각 |
| `WKEND_RCEPT_OPEN_HHMM` | 주말예약접수운영시작시각 |
| `WKEND_RCEPT_CLOSE_HHMM` | 주말예약접수운영종료시각 |
| `WEEKDAY_OPER_OPEN_HHMM` | 차량평일운행시작시각 |
| `WEEKDAY_OPER_COLSE_HHMM` | 차량평일운행종료시각 |
| `WKEND_OPER_OPEN_HHMM` | 차량주말운행시작시각 |
| `WKEND_OPER_CLOSE_HHMM` | 차량주말운행종료시각 |
| `BEFFAT_RESVE_PD` | 사전예약신청기간 |
| `USE_LMTT` | 차량이용제한사항 |
| `INSIDE_OPRAT_AREA` | 차량관내운행지역 |
| `OUTSIDE_OPRAT_AREA` | 차량관외운행지역 |
| `USE_TRGET` | 차량이용대상 |
| `USE_CHARGE` | 차량이용요금 |
| `INSTITUTION_NM` | 관리기관명 |
| `PHONE_NUMBER` | 관리기관전화번호 |
| `REFERENCE_DATE` | 데이터기준일자 |
| `instt_code` | 제공기관코드 |
| `instt_nm` | 제공기관기관명 |

### `RAIL_WHEELCHAIR_LIFT` 국가철도공단_역사별 휠체어리프트 위치
- 링크: https://data.kric.go.kr/rips/M_01_02/detail.do?id=205&service=vulnerableUserInfo&operation=stationWheelchairLiftLocation
- 컬럼 수: 12

| 컬럼명 | 설명 |
|---|---|
| `bndWgt` | 한계중량 |
| `dtlLoc` | 상세위치 |
| `exitNo` | 출구번호 |
| `grndDvNmFr` | 운행시작(지상/지하) |
| `grndDvNmTo` | 운행종료(지상/지하) |
| `len` | 길이 |
| `lnCd` | 선코드 |
| `railOprIsttCd` | 철도운영기관코드 |
| `runStinFlorFr` | 운행시작층 |
| `runStinFlorTo` | 운행종료층 |
| `stinCd` | 역코드 |
| `wd` | 폭 |

### `RAIL_WHEELCHAIR_LIFT_MOVEMENT` 역사별 휠체어리프트 이동동선
- 링크: https://data.kric.go.kr/rips/M_01_02/detail.do?id=209&service=vulnerableUserInfo&operation=stationWheelchairLiftMovement
- 컬럼 수: 9

| 컬럼명 | 설명 |
|---|---|
| `lnCd` | 선코드 |
| `mvContDtl` | 상세 이동내용 |
| `mvDst` | 이동거리 |
| `mvPathDvCd` | 이동경로구분코드 |
| `mvPathDvNm` | 이동경로구분 |
| `mvPathMgNo` | 이동경로관리번호 |
| `mvTpOrdr` | 이동유형순서 |
| `railOprIsttCd` | 철도운영기관코드 |
| `stinCd` | 역코드 |

### `SEOUL_WHEELCHAIR_LIFT` 서울교통공사_휠체어리프트 설치현황
- 링크: https://www.data.go.kr/data/15044262/fileData.do
- 컬럼 수: 15

| 컬럼명 | 설명 |
|---|---|
| `SERIAL NUMBER` | 연번 (항목명: 연번) |
| `LINE NAME` | 호선 (항목명: 호선) |
| `STATION NAME` | 역명 (항목명: 역명) |
| `MANAGEMENT NAME` | 관리번호(호기) (항목명: 관리번호(호기)) |
| `ENTRANCE NUMBER` | (근접) 출입구번호 (항목명: (근접) 출입구번호) |
| `STARTING FLOOR(GROUND_BASEMENT)` | 시작층(지상_지하) (항목명: 시작층(지상_지하)) |
| `STARTING FLOOR(OPERATION STATION FLOOR)` | 시작층(운행역층) (항목명: 시작층(운행역층)) |
| `STARTING FLOOR(DETAILED LOCATION)` | 시작층(상세위치) (항목명: 시작층(상세위치)) |
| `END FLOOR(GROUND_BASEMENT)` | 종료층(지상_지하) (항목명: 종료층(지상_지하)) |
| `END FLOOR(OPERATION STATION FLOOR)` | 종료층(운행역층) (항목명: 종료층(운행역층)) |
| `END FLOOR(DETAILED LOCATION)` | 종료층(상세위치) (항목명: 종료층(상세위치)) |
| `LEGNTH` | 길이 (항목명: 길이) |
| `WIDTH` | 폭 (항목명: 폭) |
| `WEIGHT` | 한계중량 (항목명: 한계중량) |
| `DATA BASEDATA` | 데이터 기준일자 (항목명: 데이터 기준일자) |

### `SEOUL_SUBWAY_ENTRANCE_LIFT` 서울시 지하철 출입구 리프트 위치정보
- 링크: https://data.seoul.go.kr/dataList/OA-21211/S/1/datasetView.do
- 컬럼 수: 13

| 컬럼명 | 설명 |
|---|---|
| `list_total_count` | 총 데이터 건수 (정상조회 시 출력됨) |
| `RESULT.CODE` | 요청결과 코드 (하단 메세지설명 참고) |
| `RESULT.MESSAGE` | 요청결과 메시지 (하단 메세지설명 참고) |
| `NODE_TYPE` | 노드링크 유형 |
| `NODE_WKT` | 노드 WKT |
| `NODE_ID` | 노드 ID |
| `NODE_TYPE_CD` | 노드 유형 코드 |
| `SGG_CD` | 시군구코드 |
| `SGG_NM` | 시군구명 |
| `EMD_CD` | 읍면동코드 |
| `EMD_NM` | 읍면동명 |
| `SBWY_STN_CD` | 지하철역코드 |
| `SBWY_STN_NM` | 지하철역명 |

### `SEOUL_WALKING_NETWORK` 서울특별시_자치구별 도보 네트워크 공간정보
- 링크: https://data.seoul.go.kr/dataList/OA-21208/S/1/datasetView.do
- 컬럼 수: 25

| 컬럼명 | 설명 |
|---|---|
| `list_total_count` | 총 데이터 건수 (정상조회 시 출력됨) |
| `RESULT.CODE` | 요청결과 코드 (하단 메세지설명 참고) |
| `RESULT.MESSAGE` | 요청결과 메시지 (하단 메세지설명 참고) |
| `NODE_TYPE` | 노드링크 유형 |
| `NODE_WKT` | 노드 WKT |
| `NODE_ID` | 노드 ID |
| `NODE_TYPE_CD` | 노드 유형 코드 |
| `LNKG_WKT` | 링크 WKT |
| `LNKG_ID` | 링크 ID |
| `LNKG_TYPE_CD` | 링크 유형 코드 |
| `BGNG_LNKG_ID` | 시작노드 ID |
| `END_LNKG_ID` | 종료노드 ID |
| `LNKG_LEN` | 링크 길이 |
| `SGG_CD` | 시군구코드 |
| `SGG_NM` | 시군구명 |
| `EMD_CD` | 읍면동코드 |
| `EMD_NM` | 읍면동명 |
| `EXPN_CAR_RD` | 고가도로 |
| `SBWY_NTW` | 지하철네트워크 |
| `BRG` | 교량 |
| `TNL` | 터널 |
| `OVRP` | 육교 |
| `CRSWK` | 횡단보도 |
| `PARK` | 공원,녹지 |
| `BLDG` | 건물내 |

### `NATIONWIDE_BUS_STOP` 국토교통부_전국 버스정류장 위치정보
- 링크: https://www.data.go.kr/data/15067528/fileData.do#tab-layer-openapi
- 컬럼 수: 9

| 컬럼명 | 설명 |
|---|---|
| `NODE_ID` | 정류장 ID (항목명: 정류장번호) |
| `NODE_NM` | 정류장 명칭 (항목명: 정류장명) |
| `GPS_LATI` | GPS 위도(WGS84 위경도) (항목명: 위도) |
| `GPS_LONG` | GPS 경도(WGS84 위경도) (항목명: 경도) |
| `COLLECTD_TIME` | 정보수집일 (항목명: 정보수집일) |
| `NODE_MOBILE_ID` | 모바일(ARS)/단축 ID (항목명: 모바일단축번호) |
| `CITY_CD` | 도시코드 (항목명: 도시코드) |
| `CITY_NAME` | 도시명 (항목명: 도시명) |
| `ADMIN_NM` | 관리도시명 (항목명: 관리도시명) |

### `SEOUL_WHEELCHAIR_RAMP_STATUS` 서울교통공사_휠체어경사로 설치 현황
- 링크: https://data.seoul.go.kr/dataList/OA-13116/S/1/datasetView.do
- 컬럼 수: 4
- 기준: 파일 목록의 최신 수정일 파일(현재 `서울교통공사 휠체어경사로 설치 현황_20240331.csv`) 헤더

| 컬럼명 | 설명 |
|---|---|
| `호선` |  |
| `역명` |  |
| `구분` |  |
| `위치` |  |

### `SEOUL_LOW_FLOOR_BUS_ROUTE_RETENTION` 서울시 저상버스 도입 노선 및 노선별 보유율
- 링크: https://data.seoul.go.kr/dataList/OA-22229/F/1/datasetView.do
- 컬럼 수: 4
- 기준: 파일 목록의 최신 수정일 파일(현재 `서울시 저상버스 도입 노선 및 노선별 보유율(25.4.25).xlsx`) 헤더

| 컬럼명 | 설명 |
|---|---|
| `노선\n번호` |  |
| `인가\n대수` |  |
| `저상버스 대수` |  |
| `저상보유율` |  |

### `NATIONWIDE_TRAFFIC_LIGHT` 전국신호등표준데이터
- 링크: https://www.data.go.kr/data/15028198/standard.do#
- 컬럼 수: 33

| 컬럼명 | 설명 |
|---|---|
| `ctprvnNm` | 시도명 |
| `signguNm` | 시군구명 |
| `roadKnd` | 도로종류 |
| `roadRouteNo` | 도로노선번호 |
| `roadRouteNm` | 도로노선명 |
| `roadRouteDrc` | 도로노선방향 |
| `rdnmadr` | 소재지도로명주소 |
| `lnmadr` | 소재지지번주소 |
| `latitude` | 위도 |
| `longitude` | 경도 |
| `sgngnrInstlMthd` | 신호기설치방식 |
| `roadType` | 도로형태 |
| `priorRoadYn` | 주도로여부 |
| `tfclghtManageNo` | 신호등관리번호 |
| `tfclghtSe` | 신호등구분 |
| `tfclghtColorKnd` | 신호등색종류 |
| `sgnaspMthd` | 신호등화방식 |
| `sgnaspOrdr` | 신호등화순서 |
| `sgnaspTime` | 신호등화시간 |
| `sotKnd` | 광원종류 |
| `signlCtrlMthd` | 신호제어방식 |
| `signlTimeMthdType` | 신호시간결정방식 |
| `opratnYn` | 점멸등운영여부 |
| `flashingLightOpenHhmm` | 점멸등운영시작시각 |
| `flashingLightCloseHhmm` | 점멸등운영종료시각 |
| `fnctngSgngnrYn` | 보행자작동신호기유무 |
| `remndrIdctYn` | 잔여시간표시기유무 |
| `sondSgngnrYn` | 시각장애인용음향신호기유무 |
| `drcbrdSn` | 도로안내표지일련번호 |
| `institutionNm` | 관리기관명 |
| `phoneNumber` | 관리기관전화번호 |
| `referenceDate` | 데이터기준일자 |
| `instt_code` | 제공기관코드 |

### `NATIONWIDE_CROSSWALK` 전국횡단보도표준데이터
- 링크: https://www.data.go.kr/data/15028201/standard.do
- 컬럼 수: 27

| 컬럼명 | 설명 |
|---|---|
| `ctprvnNm` | 시도명 |
| `signguNm` | 시군구명 |
| `roadNm` | 도로명 |
| `rdnmadr` | 소재지도로명주소 |
| `lnmadr` | 소재지지번주소 |
| `crslkManageNo` | 횡단보도관리번호 |
| `crslkKnd` | 횡단보도종류 |
| `bcyclCrslkCmbnatYn` | 자전거횡단도겸용여부 |
| `highlandYn` | 고원식적용여부 |
| `latitude` | 위도 |
| `longitude` | 경도 |
| `cartrkCo` | 차로수 |
| `bt` | 횡단보도폭 |
| `et` | 횡단보도연장 |
| `tfclghtYn` | 보행자신호등유무 |
| `fnctngSgngnrYn` | 보행자작동신호기유무 |
| `sondSgngnrYn` | 음향신호기설치여부 |
| `greenSgngnrTime` | 녹색신호시간 |
| `redSgngnrTime` | 적색신호시간 |
| `tfcilndYn` | 교통섬유무 |
| `ftpthLowerYn` | 보도턱낮춤여부 |
| `brllBlckYn` | 점자블록유무 |
| `cnctrLghtFcltyYn` | 집중조명시설유무 |
| `institutionNm` | 관리기관명 |
| `phoneNumber` | 관리기관전화번호 |
| `referenceDate` | 데이터기준일자 |
| `instt_code` | 제공기관코드 |

### `VOCATIONAL_TRAINING` 한국고용정보원_직업훈련_국민내일배움카드 훈련과정
- 링크: https://www.work24.go.kr/cm/e/a/0110/selectOpenApiSvcInfo.do?apiSvcId=&upprApiSvcId=&fullApiSvcId=000000000000000000000000000004
- 컬럼 수: 36

| 컬럼명 | 설명 |
|---|---|
| `HRDNet` | XML문서의 최상위 노드입니다. |
| `scn_cnt` | 검색된 총 건수 |
| `pageNum` | 현재페이지 |
| `pageSize` | 페이지당 출력개수, 페이지당 표현될 자료의 개수 |
| `srchList` |  |
| `scn_list` |  |
| `ADDRESS` | 주소 |
| `CERTIFICATE` | 자격증 |
| `CONTENTS` | 컨텐츠 |
| `COURSE_MAN` | 수강비 |
| `EI_EMPL_CNT3` | 고용보험3개월 취업인원 수 |
| `EI_EMPL_CNT3_GT10` | 고용보험3개월 취업누적인원 10인이하 여부 (Y/N) 17.11.07부터 제공되지 않는 항목이나 기존 API 사용자를 위해 Null값을 제공 |
| `EI_EMPL_RATE3` | 고용보험3개월 취업률 |
| `EI_EMPL_RATE6` | 고용보험6개월 취업률 |
| `GRADE` | 등급 |
| `INST_CD` | 훈련기관 코드 |
| `NCS_CD` | NCS 코드 |
| `REAL_MAN` | 실제 훈련비 |
| `REG_COURSE_MAN` | 수강신청 인원 |
| `STDG_SCOR` | 만족도 점수 |
| `SUB_TITLE` | 부 제목 |
| `SUB_TITLE_LINK` | 부 제목 링크 |
| `TEL_NO` | 전화번호 |
| `TITLE` | 제목 |
| `TITLE_ICON` | 제목 아이콘 |
| `TITLE_LINK` | 제목 링크 |
| `TRA_END_DATE` | 훈련종료일자 |
| `TRA_START_DATE` | 훈련시작일자 |
| `TRAIN_TARGET` | 훈련대상 |
| `TRAIN_TARGET_CD` | 훈련구분 |
| `TRAINST_CST_ID` | 훈련기관ID |
| `TRNG_AREA_CD` | 지역코드(중분류) |
| `TRPR_DEGR` | 훈련과정 순차 |
| `TRPR_ID` | 훈련과정ID |
| `WKED_SE` | 주말/주중 구분 |
| `YARD_MAN` | 정원 |

### `JOBSEEKER_COMPETENCY_PROGRAM` 한국고용정보원_구직자취업역량 강화프로그램
- 링크: https://www.work24.go.kr/cm/e/a/0110/selectOpenApiSvcInfo.do?apiSvcId=&upprApiSvcId=&fullApiSvcId=000000000000000000000000000098
- 컬럼 수: 15

| 컬럼명 | 설명 |
|---|---|
| `empPgmSchdInviteList` |  |
| `total` | 총건수 |
| `startPage` | 기본값 1, 최대 1000 검색의 시작위치를 지정 할 수 있습니다. |
| `display` | 출력건수, 기본값 10 |
| `empPgmSchdInvite` |  |
| `orgNm` | 고용센터명 |
| `pgmNm` | 프로그램명 |
| `pgmSubNm` | 과정명 |
| `pgmTarget` | 대상자 |
| `pgmStdt` | 과정시작일( YYYYMMDD ) |
| `pgmEndt` | 과정종료일( YYYYMMDD ) |
| `openTimeClcd` | 오전, 오후 구분 - 오전 : 1 - 오후 : 2 |
| `openTime` | 시작시간( HH:MM ) - 24시 표현식이 아닌 12시 표현 방식으로 제공됩니다 - ex) 오전 10시 > 10:00 - 오후 10시 > 10:00 |
| `operationTime` | 운영시간 |
| `openPlcCont` | 개최장소 |

## 데이터 저장 방식
- `app_user`: 계정 정보(소셜 provider/userId, 이메일, 권한, 가입완료)
- `onboarding_profile`: 사용자 프로필(최대 3개, 기본 프로필 1개 필수)
- `public_data_record`: 원본 payload(JSON), 해시, 외부ID, 수집시각 저장
- `public_data_record_field`: payload를 `field_path` 단위로 펼쳐 저장
- `pd_*` 정규화 테이블: 데이터셋별 컬럼형 저장(스코어링/지도 조회용)
- 변경건만 payload/필드 재저장, 동일건은 수집시각만 갱신
- 각 소스 전체 페이지 수집이 끝난 뒤 API 결과에 없는 기존 데이터는 DB에서 삭제
- `KEPAD_RECRUITMENT`, `KEPAD_SUPPORT_AGENCY`는 네이버 지오코딩으로 `geo_latitude`, `geo_longitude`, `geo_matched_address`를 함께 저장

## 스케줄러
- Cron: `bridgework.sync.cron`
- 기본값: `0 0/30 * * * *`
- ShedLock 적용으로 다중 인스턴스 중복 실행 방지
- 페이징은 API별 최대 페이지 크기로 조회하고 마지막 페이지까지 순회
- `SEOUL_WHEELCHAIR_RAMP_STATUS`, `SEOUL_LOW_FLOOR_BUS_ROUTE_RETENTION`는 최신 파일 revision 동일 시 스킵(`public_data_source_snapshot` 기준)

## 수동 실행/조회 API
- 소셜 로그인: `POST /api/v1/auth/social/login`
- 회원가입 완료(기본 프로필 생성 포함): `POST /api/v1/auth/social/signup/complete`
- 토큰 재발급: `POST /api/v1/auth/token/refresh`
- 로그아웃: `POST /api/v1/auth/logout`
- 내 정보 조회: `GET /api/v1/auth/me`

- 내 프로필 목록: `GET /api/v1/profiles`
- 프로필 생성: `POST /api/v1/profiles`
- 프로필 단건 조회: `GET /api/v1/profiles/{profileId}`
- 프로필 수정: `PUT /api/v1/profiles/{profileId}`
- 프로필 삭제: `DELETE /api/v1/profiles/{profileId}`
- 기본 프로필 지정: `PATCH /api/v1/profiles/{profileId}/set-default`

- 직무 트리 옵션: `GET /api/v1/options/job-categories/tree`
- 지역 옵션: `GET /api/v1/options/regions`
- 고용형태 옵션: `GET /api/v1/options/employment-types`
- 급여 방식 옵션: `GET /api/v1/options/salary-types`

- 근로지원인 수행기관 마커: `GET /api/v1/map/support-agencies`

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
- 필요 환경변수: `DATA_GO_KR_SERVICE_KEY`, `KRIC_SERVICE_KEY`, `SEOUL_OPEN_API_KEY`, `KRIC_STATION_CODE_XLSX_PATH`, `WORK24_VOCATIONAL_TRAINING_AUTH_KEY`, `WORK24_COMPETENCY_AUTH_KEY`
- `KRIC_STATION_CODE_XLSX_PATH` 파일은 `국가철도공단`과 `서울교통공사` 수집 시 모두 사용
- 실행 예시: `python3 scripts/export_public_data_to_csv.py --output-dir exports --page-size 0 --max-pages 0`
- 무데이터/오류 메시지는 CSV 행으로 기록하지 않음
- 실행 로그에 요청 URL(파라미터 포함), 재시도, 감지 건수(`detected`)를 출력
- `SEOUL_WHEELCHAIR_RAMP_STATUS`, `SEOUL_LOW_FLOOR_BUS_ROUTE_RETENTION`는 datasetView의 파일목록에서 `수정일` 최신 파일 1건만 다운로드
- 위 2개 소스는 이전 실행과 최신 파일 revision(`수정일+seq+파일명`)이 같으면 스킵
- revision 상태 파일: `exports/.source_revisions.json`
- 특정 데이터명만 실행(한글명/영문 slug(csv 파일명) 둘 다 지원): `python3 scripts/export_public_data_to_csv.py --only-data-name "국가철도공단_역사별 휠체어리프트 위치"`
- 특정 데이터명 여러개 실행: `python3 scripts/export_public_data_to_csv.py --only-data-name "한국장애인고용공단_장애인 구인 실시간 현황" --only-data-name "seoul_wheelchair_lift"`
- 필요 라이브러리: `pip install requests pandas`
