# BridgeWork Backend

BridgeWork의 인증·프로필·공고·추천 작업·공공데이터를 담당하는 Spring Boot 메인 API 서버입니다.

프론트엔드의 단일 API 진입점으로 동작하며, AI 추천과 PDF 프로필 초안이 필요할 때만 FastAPI AI/GIS Service를 내부 호출합니다. 현재 유효 공고 전체 추천은 Redis 기반 비동기 task로 관리하고, 공공데이터 19종은 매일 수집해 원본과 정규화 테이블에 함께 저장합니다.

<p align="center">
  <img src="https://raw.githubusercontent.com/nodongservice/.github/main/images_new/dataflow_2.png" alt="BridgeWork 공공데이터 수집 정규화 활용 파이프라인" width="100%" />
</p>

## 기술 스택

- Java 17
- Spring Boot 3.3.6
- Spring MVC + WebFlux client
- Spring Data JPA + Flyway + PostgreSQL/PostGIS
- Spring Security + JWT + Redis session
- ShedLock 기반 분산 스케줄 중복 방지
- Actuator + Micrometer Prometheus
- JUnit 5 + Testcontainers

## 전체 서비스에서의 역할

```text
React Frontend
  -> Spring Backend
       ├─ 인증·세션·회원 탈퇴
       ├─ 프로필·공고·스크랩·공지사항
       ├─ Redis 추천 task·결과 캐시
       ├─ 공공데이터 수집·정규화·지오코딩
       └─ FastAPI AI/GIS Service
            ├─ 추천 스코어링·근거 생성
            └─ PDF OCR·LLM 프로필 초안
```

## 팀

| 이름 | 담당 |
| --- | --- |
| 장혜진 | 기획 |
| 김수인 | 디자인 |
| 최성현 | 백엔드 및 인프라 |
| 박민정 | 프론트 및 AI 개발 |

## 역사 코드 파일(프로젝트 포함)

- 기본 포함 파일: `backend/resources/reference/operating_agency_station_codes_2026-02-28.xlsx`
- 로컬 기본 경로와 `scripts/.env`는 위 파일을 사용하도록 설정됨

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
| `SEOUL_WHEELCHAIR_LIFT` (서울교통공사_휠체어리프트 설치현황)                      | [15044262](https://www.data.go.kr/data/15044262/fileData.do) | `https://api.odcloud.kr/api/{publicDataPk}/v1/{publicDataDetailPk}` (`openapi.do`의 `swagger-container`/`swagger-ui`/`script`에서 최신 endpoint 후보를 추출해 호출) | data.go.kr 서비스키 | `serviceKey`, `page`, `perPage(max=10000)`, `returnType=JSON`, `역명`(xlsx의 `STIN_NM` 기반 순회) |
| `SEOUL_SUBWAY_ENTRANCE_LIFT` (서울시 지하철 출입구 리프트 위치정보)               | [OA-21211](https://data.seoul.go.kr/dataList/OA-21211/S/1/datasetView.do) | `http://openapi.seoul.go.kr:8088/{API_KEY}/json/tbTraficEntrcLft/{start}/{end}` | data.seoul.go.kr 키 | `start/end(페이지 범위)`, `max rows=1000` |
| `SEOUL_WALKING_NETWORK` (서울특별시_자치구별 도보 네트워크 공간정보)                 | [OA-21208](https://data.seoul.go.kr/dataList/OA-21208/S/1/datasetView.do) | `http://openapi.seoul.go.kr:8088/{API_KEY}/json/TbTraficWlkNet/{start}/{end}` | data.seoul.go.kr 키 | `start/end(페이지 범위)`, `max rows=1000` |
| `NATIONWIDE_BUS_STOP` (국토교통부_전국 버스정류장 위치정보)                       | [15067528](https://www.data.go.kr/data/15067528/fileData.do#tab-layer-openapi) | `https://api.odcloud.kr/api/{publicDataPk}/v1/{publicDataDetailPk}` (`openapi.do`의 `swagger-container`/`swagger-ui`/`script`에서 최신 endpoint 후보를 추출해 호출) | data.go.kr 서비스키 | `serviceKey`, `page`, `perPage(max=10000)`, `returnType=JSON` |
| `SEOUL_WHEELCHAIR_RAMP_STATUS` (서울교통공사_휠체어경사로 설치 현황)              | [OA-13116](https://data.seoul.go.kr/dataList/OA-13116/S/1/datasetView.do) | `https://datafile.seoul.go.kr/bigfile/iot/inf/nio_download.do` (datasetView의 파일목록에서 최신 `수정일` 1건 선택 후 다운로드) | 없음 | `infId`, `infSeq`, `seq`, `seqNo`, `useCache=false` |
| `SEOUL_LOW_FLOOR_BUS_ROUTE_RETENTION` (서울시 저상버스 도입 노선 및 노선별 보유율)  | [OA-22229](https://data.seoul.go.kr/dataList/OA-22229/F/1/datasetView.do) | `https://datafile.seoul.go.kr/bigfile/iot/inf/nio_download.do` (datasetView의 파일목록에서 최신 `수정일` 1건 선택 후 다운로드) | 없음 | `infId`, `infSeq`, `seq`, `seqNo`, `useCache=false` |
| `NATIONWIDE_TRAFFIC_LIGHT` (전국신호등표준데이터)                           | [15028198](https://www.data.go.kr/data/15028198/standard.do#) | `https://api.data.go.kr/openapi/tn_pubr_public_traffic_light_api` | data.go.kr 서비스키 | `serviceKey`, `pageNo`, `numOfRows(max=1000)`, `type=xml` |
| `NATIONWIDE_CROSSWALK` (전국횡단보도표준데이터)                              | [15028201](https://www.data.go.kr/data/15028201/standard.do) | `https://api.data.go.kr/openapi/tn_pubr_public_crosswalk_api` | data.go.kr 서비스키 | `serviceKey`, `pageNo`, `numOfRows(max=1000)`, `type=xml` |
| `VOCATIONAL_TRAINING` (한국고용정보원_직업훈련_국민내일배움카드 훈련과정)                | [work24 000004](https://www.work24.go.kr/cm/e/a/0110/selectOpenApiSvcInfo.do?apiSvcId=&upprApiSvcId=&fullApiSvcId=000000000000000000000000000004) | `https://www.work24.go.kr/cm/openApi/call/hr/callOpenApiSvcInfo310L01.do` | Work24 인증키 | `authKey`, `returnType=XML`, `pageNum`, `pageSize(max=100)` |
| `JOBSEEKER_COMPETENCY_PROGRAM` (한국고용정보원_구직자취업역량 강화프로그램)           | [work24 000098](https://www.work24.go.kr/cm/e/a/0110/selectOpenApiSvcInfo.do?apiSvcId=&upprApiSvcId=&fullApiSvcId=000000000000000000000000000098) | `https://www.work24.go.kr/cm/openApi/call/wk/callOpenApiSvcInfo217L01.do` | Work24 인증키 | `authKey`, `returnType=XML`, `startPage`, `display(max=100)`, `pgmStdt(YYYYMMDD, 오늘~1개월 후 반복)` |

- `SEOUL_WHEELCHAIR_LIFT`, `NATIONWIDE_BUS_STOP`은 `openapi.do`의 swagger 영역에서 최신 OpenAPI 후보를 찾지 못하면 동기화를 실패 처리한다(성공 fallback 없음).

## 데이터 저장 방식

- `app_user`: 계정 정보(소셜 provider/userId, 이메일, 권한, 가입완료, 상태/탈퇴시각)
- `user_profile`: 사용자 프로필(최대 3개, 기본 프로필 1개 필수)
- `public_data_record`: 원본 payload(JSON), 해시, 외부ID, 수집시각 저장
- `public_data_record_field`: payload를 `field_path` 단위로 펼쳐 저장
- `pd_*` 정규화 테이블: 데이터셋별 컬럼형 저장(스코어링/지도 조회용)
- 변경건 또는 재활성화 건만 payload/필드/정규화 테이블을 재저장하고, 이미 ACTIVE 상태인 동일건은 DB 쓰기를 생략
- 기간성 데이터(`KEPAD_RECRUITMENT.termDate`, `VOCATIONAL_TRAINING.traEndDate`, `JOBSEEKER_COMPETENCY_PROGRAM.pgmEndt`)는 종료일이 지난 API 응답을 신규 저장하지 않음
- 기간성 데이터의 기존 원본 레코드는 API 결과에 없거나 종료일이 지나면 `sync_status=CLOSED`로 전환
- `KEPAD_RECRUITMENT` 정규화 데이터는 마감 시 `posting_status=CLOSED`로 전환하고, 다른 기간성 정규화 데이터는 활성 조회 대상에서 제거
- 기간성이 아닌 소스는 전체 페이지 수집이 끝난 뒤 API 결과에 없는 기존 데이터를 DB에서 삭제
- `VOCATIONAL_TRAINING` 외부 ID는 같은 과정의 회차를 구분하기 위해 `trprId + trprDegr` 조합으로 생성
- `KEPAD_RECRUITMENT`, `KEPAD_SUPPORT_AGENCY`는 네이버 지오코딩으로 `geo_latitude`, `geo_longitude`, `geo_matched_address`를 함께 저장

### 공공데이터 DB 스키마(테이블/컬럼)

#### 원본/운영 테이블

- `public_data_record`
  - `id`, `source_type`, `external_id`, `payload_json`, `payload_hash`, `raw_fetched_at`, `sync_status`, `closed_at`, `status_updated_at`, `created_at`, `updated_at`
- `public_data_record_field`
  - `id`, `record_id`, `field_path`, `field_value`, `value_type`, `created_at`, `updated_at`
- `public_data_sync_log`
  - `id`, `source_type`, `request_source`, `status`, `processed_count`, `new_count`, `updated_count`, `failed_count`, `error_message`, `started_at`, `ended_at`
- `public_data_source_snapshot`
  - `source_type`, `latest_revision`, `latest_file_name`, `latest_modified_date`, `created_at`, `updated_at`

#### 정규화 테이블 공통 컬럼

모든 `pd_*` 테이블은 아래 공통 컬럼을 가진다.
- `id`, `external_id`, `payload_hash`, `raw_fetched_at`, `created_at`, `updated_at`

#### 정규화 테이블 ↔ 데이터셋 매핑

- `pd_kepad_recruitment` ↔ `KEPAD_RECRUITMENT` (한국장애인고용공단_장애인 구인 실시간 현황)
- `pd_kepad_job_category` ↔ `KEPAD_JOB_CATEGORY` (한국장애인고용공단_장애인 고용직무분류)
- `pd_kepad_standard_workplace` ↔ `KEPAD_STANDARD_WORKPLACE` (한국장애인고용공단_장애인 표준사업장 실시간 조회)
- `pd_kepad_support_agency` ↔ `KEPAD_SUPPORT_AGENCY` (한국장애인고용공단_근로지원인 수행기관 실시간 정보)
- `pd_korail_week_person_facilities` ↔ `KORAIL_WEEK_PERSON_FACILITIES` (한국철도공사_편의시설정보)
- `pd_seoul_transport_weak_wheelchair_lift` ↔ `SEOUL_TRANSPORT_WEAK_WHEELCHAIR_LIFT` (서울교통공사_교통약자이용정보(휠체어리프트))
- `pd_transport_support_center` ↔ `TRANSPORT_SUPPORT_CENTER` (전국교통약자이동지원센터정보표준데이터)
- `pd_rail_wheelchair_lift` ↔ `RAIL_WHEELCHAIR_LIFT` (국가철도공단_역사별 휠체어리프트 위치)
- `pd_rail_wheelchair_lift_movement` ↔ `RAIL_WHEELCHAIR_LIFT_MOVEMENT` (역사별 휠체어리프트 이동동선)
- `pd_seoul_wheelchair_lift` ↔ `SEOUL_WHEELCHAIR_LIFT` (서울교통공사_휠체어리프트 설치현황)
- `pd_seoul_subway_entrance_lift` ↔ `SEOUL_SUBWAY_ENTRANCE_LIFT` (서울시 지하철 출입구 리프트 위치정보)
- `pd_seoul_walking_network` ↔ `SEOUL_WALKING_NETWORK` (서울특별시_자치구별 도보 네트워크 공간정보)
- `pd_nationwide_bus_stop` ↔ `NATIONWIDE_BUS_STOP` (국토교통부_전국 버스정류장 위치정보)
- `pd_seoul_wheelchair_ramp_status` ↔ `SEOUL_WHEELCHAIR_RAMP_STATUS` (서울교통공사_휠체어경사로 설치 현황)
- `pd_seoul_low_floor_bus_route_retention` ↔ `SEOUL_LOW_FLOOR_BUS_ROUTE_RETENTION` (서울시 저상버스 도입 노선 및 노선별 보유율)
- `pd_nationwide_traffic_light` ↔ `NATIONWIDE_TRAFFIC_LIGHT` (전국신호등표준데이터)
- `pd_nationwide_crosswalk` ↔ `NATIONWIDE_CROSSWALK` (전국횡단보도표준데이터)
- `pd_vocational_training` ↔ `VOCATIONAL_TRAINING` (한국고용정보원_직업훈련_국민내일배움카드 훈련과정)
- `pd_jobseeker_competency_program` ↔ `JOBSEEKER_COMPETENCY_PROGRAM` (한국고용정보원_구직자취업역량 강화프로그램)

#### 정규화 테이블별 데이터 컬럼

- `pd_kepad_recruitment`
  - `buspla_name`, `cntct_no`, `comp_addr`, `emp_type`, `enter_type`, `env_both_hands`, `env_eyesight`, `env_lstn_talk`, `job_nm`, `offerreg_dt`, `reg_dt`, `regagn_name`, `req_career`, `req_educ`, `rno`, `rnum`, `salary`, `salary_type`, `term_date`, `env_hand_work`, `env_lift_power`, `env_stnd_walk`, `req_major`, `req_licens`, `geo_original_address`, `geo_matched_address`, `geo_latitude`, `geo_longitude`
- `pd_kepad_job_category`
  - `job_cd`, `job_cd_level`, `job_cd_nm`, `rnum`, `job_task`, `notice_cn`, `simlr_job`, `sprd_ockcls_yn`, `jobdevtip_cn`
- `pd_kepad_standard_workplace`
  - `address`, `auth_date`, `comp_auth_id`, `comp_biz_no`, `comp_name`, `comp_reg_no`, `comp_tel`, `comp_type_nm`, `president_name`, `product`, `rnum`, `comp_mgr_no`, `cancel_date`, `comp_cert`
- `pd_kepad_support_agency`
  - `exc_instn`, `exc_instn_addr`, `exc_instn_fxno`, `exc_instn_nm`, `exc_instn_telno`, `rnum`, `geo_original_address`, `geo_matched_address`, `geo_latitude`, `geo_longitude`
- `pd_korail_week_person_facilities`
  - `pwdbs_slwy_estnc`, `pwdbs_tolt_estnc`, `stn_cd`, `stn_nm`, `whlch_liftt_cnt`
- `pd_seoul_transport_weak_wheelchair_lift`
  - `fclt_no`, `fclt_nm`, `line_nm`, `stn_cd`, `stn_nm`, `stn_no`, `crtr_ymd`, `elvtr_sn`, `mng_no`, `vcnt_entrc_no`, `bgng_flr_grnd_udgd_se`, `bgng_flr`, `bgng_flr_dtl_pstn`, `end_flr_grnd_udgd_se`, `end_flr`, `end_flr_dtl_pstn`, `elvtr_len`, `elvtr_wdth_bt`, `limit_wht`, `oprtng_situ`
- `pd_transport_support_center`
  - `tfcwker_mvmn_cnter_nm`, `rdnmadr`, `lnmadr`, `latitude`, `longitude`, `car_hold_co`, `car_hold_knd`, `slope_vhcle_co`, `lift_vhcle_co`, `rcept_phone_number`, `rcept_itnadr`, `app_svc_nm`, `weekday_rcept_open_hhmm`, `weekday_rcept_colse_hhmm`, `wkend_rcept_open_hhmm`, `wkend_rcept_close_hhmm`, `weekday_oper_open_hhmm`, `weekday_oper_colse_hhmm`, `wkend_oper_open_hhmm`, `wkend_oper_close_hhmm`, `beffat_resve_pd`, `use_lmtt`, `inside_oprat_area`, `outside_oprat_area`, `use_trget`, `use_charge`, `institution_nm`, `phone_number`, `reference_date`, `instt_code`, `instt_nm`
- `pd_rail_wheelchair_lift`
  - `rail_opr_istt_cd`, `ln_cd`, `stin_cd`, `exit_no`, `dtl_loc`, `grnd_dv_nm_fr`, `run_stin_flor_fr`, `grnd_dv_nm_to`, `run_stin_flor_to`, `len`, `wd`, `bnd_wgt`, `ln_nm`, `stin_nm`
- `pd_rail_wheelchair_lift_movement`
  - `rail_opr_istt_cd`, `ln_cd`, `stin_cd`, `mv_path_mg_no`, `mv_path_dv_cd`, `mv_path_dv_nm`, `mv_tp_ordr`, `mv_dst`, `mv_cont_dtl`, `ln_nm`, `stin_nm`
- `pd_seoul_wheelchair_lift`
  - `entrance_no`, `management_no`, `length`, `data_base_date`, `elevator_serial_no`, `start_floor_detail_location`, `start_floor_operation_station_floor`, `start_floor_ground_basement`, `station_name`, `serial_number`, `end_floor_detail_location`, `end_floor_operation_station_floor`, `end_floor_ground_basement`, `width`, `weight_limit`, `line_name`
- `pd_seoul_subway_entrance_lift`
  - `node_type`, `node_wkt`, `node_id`, `node_type_cd`, `sgg_cd`, `sgg_nm`, `emd_cd`, `emd_nm`, `sbwy_stn_cd`, `sbwy_stn_nm`
- `pd_seoul_walking_network`
  - `node_type`, `node_wkt`, `node_id`, `node_type_cd`, `lnkg_wkt`, `lnkg_id`, `lnkg_type_cd`, `bgng_lnkg_id`, `end_lnkg_id`, `lnkg_len`, `sgg_cd`, `sgg_nm`, `emd_cd`, `emd_nm`, `expn_car_rd`, `sbwy_ntw`, `brg`, `tnl`, `ovrp`, `crswk`, `park`, `bldg`
- `pd_nationwide_bus_stop`
  - `longitude`, `admin_city_name`, `city_name`, `city_code`, `mobile_short_no`, `latitude`, `stop_name`, `stop_id`, `collected_at`
- `pd_seoul_wheelchair_ramp_status`
  - `line_name`, `station_name`, `division`, `location`
- `pd_seoul_low_floor_bus_route_retention`
  - `route_no`, `authorized_count`, `low_floor_bus_count`, `low_floor_retention_rate`
- `pd_nationwide_traffic_light`
  - `ctprvn_nm`, `signgu_nm`, `road_knd`, `road_route_no`, `road_route_nm`, `road_route_drc`, `rdnmadr`, `lnmadr`, `latitude`, `longitude`, `sgngnr_instl_mthd`, `road_type`, `prior_road_yn`, `tfclght_manage_no`, `tfclght_se`, `tfclght_color_knd`, `sgnasp_mthd`, `sgnasp_ordr`, `sgnasp_time`, `sot_knd`, `signl_ctrl_mthd`, `signl_time_mthd_type`, `opratn_yn`, `flashing_light_open_hhmm`, `flashing_light_close_hhmm`, `fnctng_sgngnr_yn`, `remndr_idct_yn`, `sond_sgngnr_yn`, `drcbrd_sn`, `institution_nm`, `phone_number`, `reference_date`, `instt_code`, `instt_nm`
- `pd_nationwide_crosswalk`
  - `ctprvn_nm`, `signgu_nm`, `road_nm`, `rdnmadr`, `lnmadr`, `crslk_manage_no`, `crslk_knd`, `bcycl_crslk_cmbnat_yn`, `highland_yn`, `latitude`, `longitude`, `cartrk_co`, `bt`, `et`, `tfclght_yn`, `fnctng_sgngnr_yn`, `sond_sgngnr_yn`, `green_sgngnr_time`, `red_sgngnr_time`, `tfcilnd_yn`, `ftpth_lower_yn`, `brll_blck_yn`, `cnctr_lght_fclty_yn`, `institution_nm`, `phone_number`, `reference_date`, `instt_code`, `instt_nm`
- `pd_vocational_training`
  - `address`, `certificate`, `contents`, `course_man`, `ei_empl_cnt3`, `ei_empl_cnt3_gt10`, `ei_empl_rate3`, `ei_empl_rate6`, `grade`, `inst_cd`, `ncs_cd`, `real_man`, `reg_course_man`, `stdg_scor`, `sub_title`, `sub_title_link`, `tel_no`, `title`, `title_icon`, `title_link`, `tra_end_date`, `tra_start_date`, `train_target`, `train_target_cd`, `trainst_cst_id`, `trng_area_cd`, `trpr_degr`, `trpr_id`, `wkend_se`, `yard_man`
- `pd_jobseeker_competency_program`
  - `org_nm`, `pgm_nm`, `pgm_sub_nm`, `pgm_target`, `pgm_stdt`, `pgm_endt`, `open_time_clcd`, `open_time`, `operation_time`, `open_plc_cont`

## 스케줄러

- Cron: `bridgework.sync.cron`
- 기본값: `0 0 0 * * *`
- ShedLock 적용으로 다중 인스턴스 중복 실행 방지
- 페이징은 API별 최대 페이지 크기로 조회하고 마지막 페이지까지 순회
- `SEOUL_WHEELCHAIR_RAMP_STATUS`, `SEOUL_LOW_FLOOR_BUS_ROUTE_RETENTION`는 최신 파일 revision 동일 시 스킵(`public_data_source_snapshot` 기준)
- 탈퇴 최종 처리 스케줄러: `bridgework.auth.withdrawal-finalize-interval` (기본 `1h`)

## Discord 알림

- 동기화 시작: `🚀 [공공데이터 동기화 시작 알림]`
- 동기화 완료: 성공 `✅ [공공데이터 동기화 완료 알림]`, 실패 `❌ [공공데이터 동기화 완료 알림]`
- Error 로그: `🚨 [Error 로그 발생]`
- 회원가입 완료: `🎉 [회원가입 완료 알림]`
- 모든 알림 메시지는 마지막 줄바꿈(`\n`)을 포함한다.
- 동기화 완료 알림은 단일 동기화일 때 소스별 상세를 생략하고, 전체 동기화일 때 소스별 상태/건수/소요 시간을 상세로 포함한다.
- Discord 전송 실패는 본 업무 플로우를 중단하지 않으며, 타임아웃/네트워크 계열 오류에 대해 재시도한다(`retry=2`, `backoff=700ms`, `timeout=10s`).
