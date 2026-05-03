package com.bridgework.sync.normalized;

import com.bridgework.sync.entity.PublicDataSourceType;
import com.bridgework.sync.normalized.NormalizedSourceDefinition.NormalizedColumnMapping;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class NormalizedSourceRegistry {

    private final Map<PublicDataSourceType, NormalizedSourceDefinition> definitions;

    public NormalizedSourceRegistry() {
        this.definitions = new EnumMap<>(PublicDataSourceType.class);
        register(buildKepadRecruitment());
        register(buildKepadJobCategory());
        register(buildKepadStandardWorkplace());
        register(buildKepadSupportAgency());
        register(buildKorailWeekPersonFacilities());
        register(buildSeoulTransportWeakWheelchairLift());
        register(buildTransportSupportCenter());
        register(buildRailWheelchairLift());
        register(buildRailWheelchairLiftMovement());
        register(buildSeoulWheelchairLift());
        register(buildSeoulSubwayEntranceLift());
        register(buildSeoulWalkingNetwork());
        register(buildNationwideBusStop());
        register(buildSeoulWheelchairRampStatus());
        register(buildSeoulLowFloorBusRouteRetention());
        register(buildNationwideTrafficLight());
        register(buildNationwideCrosswalk());
        register(buildVocationalTraining());
        register(buildJobseekerCompetencyProgram());
    }

    public NormalizedSourceDefinition get(PublicDataSourceType sourceType) {
        return definitions.get(sourceType);
    }

    private void register(NormalizedSourceDefinition definition) {
        definitions.put(definition.sourceType(), definition);
    }

    private NormalizedSourceDefinition buildKepadRecruitment() {
        return new NormalizedSourceDefinition(
                PublicDataSourceType.KEPAD_RECRUITMENT,
                "pd_kepad_recruitment",
                List.of(
                        mapping("busplaName", "buspla_name"),
                        mapping("cntctNo", "cntct_no"),
                        mapping("compAddr", "comp_addr"),
                        mapping("empType", "emp_type"),
                        mapping("enterType", "enter_type"),
                        mapping("envBothHands", "env_both_hands"),
                        mapping("envEyesight", "env_eyesight"),
                        mapping("envLstnTalk", "env_lstn_talk"),
                        mapping("jobNm", "job_nm"),
                        mapping("offerregDt", "offerreg_dt"),
                        mapping("regDt", "reg_dt"),
                        mapping("regagnName", "regagn_name"),
                        mapping("reqCareer", "req_career"),
                        mapping("reqEduc", "req_educ"),
                        mapping("rno", "rno"),
                        mapping("rnum", "rnum"),
                        mapping("salary", "salary"),
                        mapping("salaryType", "salary_type"),
                        mapping("termDate", "term_date"),
                        mapping("envHandWork", "env_hand_work"),
                        mapping("envLiftPower", "env_lift_power"),
                        mapping("envStndWalk", "env_stnd_walk"),
                        mapping("reqMajor", "req_major"),
                        mapping("reqLicens", "req_licens")
                ),
                "compAddr",
                "geo_latitude",
                "geo_longitude",
                "geo_matched_address",
                "geo_original_address"
        );
    }

    private NormalizedSourceDefinition buildKepadJobCategory() {
        return new NormalizedSourceDefinition(PublicDataSourceType.KEPAD_JOB_CATEGORY, "pd_kepad_job_category", List.of(
                mapping("jobCd", "job_cd"),
                mapping("jobCdLevel", "job_cd_level"),
                mapping("jobCdNm", "job_cd_nm"),
                mapping("rnum", "rnum"),
                mapping("jobTask", "job_task"),
                mapping("noticeCn", "notice_cn"),
                mapping("simlrJob", "simlr_job"),
                mapping("sprdOckclsYn", "sprd_ockcls_yn"),
                mapping("jobdevtipCn", "jobdevtip_cn")
        ), null, null, null, null, null);
    }

    private NormalizedSourceDefinition buildKepadStandardWorkplace() {
        return new NormalizedSourceDefinition(PublicDataSourceType.KEPAD_STANDARD_WORKPLACE, "pd_kepad_standard_workplace", List.of(
                mapping("address", "address"),
                mapping("authDate", "auth_date"),
                mapping("compAuthId", "comp_auth_id"),
                mapping("compBizNo", "comp_biz_no"),
                mapping("compName", "comp_name"),
                mapping("compRegNo", "comp_reg_no"),
                mapping("compTel", "comp_tel"),
                mapping("compTypeNm", "comp_type_nm"),
                mapping("presidentName", "president_name"),
                mapping("product", "product"),
                mapping("rnum", "rnum"),
                mapping("compMgrNo", "comp_mgr_no"),
                mapping("cancelDate", "cancel_date"),
                mapping("compCert", "comp_cert")
        ), null, null, null, null, null);
    }

    private NormalizedSourceDefinition buildKepadSupportAgency() {
        return new NormalizedSourceDefinition(PublicDataSourceType.KEPAD_SUPPORT_AGENCY, "pd_kepad_support_agency", List.of(
                mapping("excInstn", "exc_instn"),
                mapping("excInstnAddr", "exc_instn_addr"),
                mapping("excInstnFxno", "exc_instn_fxno"),
                mapping("excInstnNm", "exc_instn_nm"),
                mapping("excInstnTelno", "exc_instn_telno"),
                mapping("rnum", "rnum")
        ), "excInstnAddr", "geo_latitude", "geo_longitude", "geo_matched_address", "geo_original_address");
    }

    private NormalizedSourceDefinition buildKorailWeekPersonFacilities() {
        return new NormalizedSourceDefinition(PublicDataSourceType.KORAIL_WEEK_PERSON_FACILITIES, "pd_korail_week_person_facilities", List.of(
                mapping("pwdbs_slwy_estnc", "pwdbs_slwy_estnc"),
                mapping("pwdbs_tolt_estnc", "pwdbs_tolt_estnc"),
                mapping("stn_cd", "stn_cd"),
                mapping("stn_nm", "stn_nm"),
                mapping("whlch_liftt_cnt", "whlch_liftt_cnt")
        ), null, null, null, null, null);
    }

    private NormalizedSourceDefinition buildSeoulTransportWeakWheelchairLift() {
        return new NormalizedSourceDefinition(PublicDataSourceType.SEOUL_TRANSPORT_WEAK_WHEELCHAIR_LIFT, "pd_seoul_transport_weak_wheelchair_lift", List.of(
                mapping("fcltNo", "fclt_no"),
                mapping("fcltNm", "fclt_nm"),
                mapping("lineNm", "line_nm"),
                mapping("stnCd", "stn_cd"),
                mapping("stnNm", "stn_nm"),
                mapping("stnNo", "stn_no"),
                mapping("crtrYmd", "crtr_ymd"),
                mapping("elvtrSn", "elvtr_sn"),
                mapping("mngNo", "mng_no"),
                mapping("vcntEntrcNo", "vcnt_entrc_no"),
                mapping("bgngFlrGrndUdgdSe", "bgng_flr_grnd_udgd_se"),
                mapping("bgngFlr", "bgng_flr"),
                mapping("bgngFlrDtlPstn", "bgng_flr_dtl_pstn"),
                mapping("endFlrGrndUdgdSe", "end_flr_grnd_udgd_se"),
                mapping("endFlr", "end_flr"),
                mapping("endFlrDtlPstn", "end_flr_dtl_pstn"),
                mapping("elvtrLen", "elvtr_len"),
                mapping("elvtrWdthBt", "elvtr_wdth_bt"),
                mapping("limitWht", "limit_wht"),
                mapping("oprtngSitu", "oprtng_situ")
        ), null, null, null, null, null);
    }

    private NormalizedSourceDefinition buildTransportSupportCenter() {
        return new NormalizedSourceDefinition(PublicDataSourceType.TRANSPORT_SUPPORT_CENTER, "pd_transport_support_center", List.of(
                mapping("tfcwkerMvmnCnterNm", "tfcwker_mvmn_cnter_nm"),
                mapping("rdnmadr", "rdnmadr"),
                mapping("lnmadr", "lnmadr"),
                mapping("latitude", "latitude"),
                mapping("longitude", "longitude"),
                mapping("carHoldCo", "car_hold_co"),
                mapping("carHoldKnd", "car_hold_knd"),
                mapping("slopeVhcleCo", "slope_vhcle_co"),
                mapping("liftVhcleCo", "lift_vhcle_co"),
                mapping("rceptPhoneNumber", "rcept_phone_number"),
                mapping("rceptItnadr", "rcept_itnadr"),
                mapping("appSvcNm", "app_svc_nm"),
                mapping("weekdayRceptOpenHhmm", "weekday_rcept_open_hhmm"),
                mapping("weekdayRceptColseHhmm", "weekday_rcept_colse_hhmm"),
                mapping("wkendRceptOpenHhmm", "wkend_rcept_open_hhmm"),
                mapping("wkendRceptCloseHhmm", "wkend_rcept_close_hhmm"),
                mapping("weekdayOperOpenHhmm", "weekday_oper_open_hhmm"),
                mapping("weekdayOperColseHhmm", "weekday_oper_colse_hhmm"),
                mapping("wkendOperOpenHhmm", "wkend_oper_open_hhmm"),
                mapping("wkendOperCloseHhmm", "wkend_oper_close_hhmm"),
                mapping("beffatResvePd", "beffat_resve_pd"),
                mapping("useLmtt", "use_lmtt"),
                mapping("insideOpratArea", "inside_oprat_area"),
                mapping("outsideOpratArea", "outside_oprat_area"),
                mapping("useTrget", "use_trget"),
                mapping("useCharge", "use_charge"),
                mapping("institutionNm", "institution_nm"),
                mapping("phoneNumber", "phone_number"),
                mapping("referenceDate", "reference_date"),
                mapping("insttCode", "instt_code"),
                mapping("insttNm", "instt_nm")
        ), null, null, null, null, null);
    }

    private NormalizedSourceDefinition buildRailWheelchairLift() {
        return new NormalizedSourceDefinition(PublicDataSourceType.RAIL_WHEELCHAIR_LIFT, "pd_rail_wheelchair_lift", List.of(
                mapping("railOprIsttCd", "rail_opr_istt_cd"),
                mapping("lnCd", "ln_cd"),
                mapping("stinCd", "stin_cd"),
                mapping("exitNo", "exit_no"),
                mapping("dtlLoc", "dtl_loc"),
                mapping("grndDvNmFr", "grnd_dv_nm_fr"),
                mapping("runStinFlorFr", "run_stin_flor_fr"),
                mapping("grndDvNmTo", "grnd_dv_nm_to"),
                mapping("runStinFlorTo", "run_stin_flor_to"),
                mapping("len", "len"),
                mapping("wd", "wd"),
                mapping("bndWgt", "bnd_wgt"),
                mapping("LN_NM", "ln_nm"),
                mapping("STIN_NM", "stin_nm")
        ), null, null, null, null, null);
    }

    private NormalizedSourceDefinition buildRailWheelchairLiftMovement() {
        return new NormalizedSourceDefinition(PublicDataSourceType.RAIL_WHEELCHAIR_LIFT_MOVEMENT, "pd_rail_wheelchair_lift_movement", List.of(
                mapping("railOprIsttCd", "rail_opr_istt_cd"),
                mapping("lnCd", "ln_cd"),
                mapping("stinCd", "stin_cd"),
                mapping("mvPathMgNo", "mv_path_mg_no"),
                mapping("mvPathDvCd", "mv_path_dv_cd"),
                mapping("mvPathDvNm", "mv_path_dv_nm"),
                mapping("mvTpOrdr", "mv_tp_ordr"),
                mapping("mvDst", "mv_dst"),
                mapping("mvContDtl", "mv_cont_dtl"),
                mapping("LN_NM", "ln_nm"),
                mapping("STIN_NM", "stin_nm")
        ), null, null, null, null, null);
    }

    private NormalizedSourceDefinition buildSeoulWheelchairLift() {
        return new NormalizedSourceDefinition(PublicDataSourceType.SEOUL_WHEELCHAIR_LIFT, "pd_seoul_wheelchair_lift", List.of(
                mapping("(근접) 출입구번호", "entrance_no"),
                mapping("관리번호(호기)", "management_no"),
                mapping("길이", "length"),
                mapping("데이터 기준일자", "data_base_date"),
                mapping("승강기 일련번호", "elevator_serial_no"),
                mapping("시작층(상세위치)", "start_floor_detail_location"),
                mapping("시작층(운행역층)", "start_floor_operation_station_floor"),
                mapping("시작층(지상_지하)", "start_floor_ground_basement"),
                mapping("역명", "station_name"),
                mapping("연번", "serial_number"),
                mapping("종료층(상세위치)", "end_floor_detail_location"),
                mapping("종료층(운행역층)", "end_floor_operation_station_floor"),
                mapping("종료층(지상_지하)", "end_floor_ground_basement"),
                mapping("폭", "width"),
                mapping("한계중량", "weight_limit"),
                mapping("호선", "line_name")
        ), null, null, null, null, null);
    }

    private NormalizedSourceDefinition buildSeoulSubwayEntranceLift() {
        return new NormalizedSourceDefinition(PublicDataSourceType.SEOUL_SUBWAY_ENTRANCE_LIFT, "pd_seoul_subway_entrance_lift", List.of(
                mapping("NODE_TYPE", "node_type"),
                mapping("NODE_WKT", "node_wkt"),
                mapping("NODE_ID", "node_id"),
                mapping("NODE_TYPE_CD", "node_type_cd"),
                mapping("SGG_CD", "sgg_cd"),
                mapping("SGG_NM", "sgg_nm"),
                mapping("EMD_CD", "emd_cd"),
                mapping("EMD_NM", "emd_nm"),
                mapping("SBWY_STN_CD", "sbwy_stn_cd"),
                mapping("SBWY_STN_NM", "sbwy_stn_nm")
        ), null, null, null, null, null);
    }

    private NormalizedSourceDefinition buildSeoulWalkingNetwork() {
        return new NormalizedSourceDefinition(PublicDataSourceType.SEOUL_WALKING_NETWORK, "pd_seoul_walking_network", List.of(
                mapping("NODE_TYPE", "node_type"),
                mapping("NODE_WKT", "node_wkt"),
                mapping("NODE_ID", "node_id"),
                mapping("NODE_TYPE_CD", "node_type_cd"),
                mapping("LNKG_WKT", "lnkg_wkt"),
                mapping("LNKG_ID", "lnkg_id"),
                mapping("LNKG_TYPE_CD", "lnkg_type_cd"),
                mapping("BGNG_LNKG_ID", "bgng_lnkg_id"),
                mapping("END_LNKG_ID", "end_lnkg_id"),
                mapping("LNKG_LEN", "lnkg_len"),
                mapping("SGG_CD", "sgg_cd"),
                mapping("SGG_NM", "sgg_nm"),
                mapping("EMD_CD", "emd_cd"),
                mapping("EMD_NM", "emd_nm"),
                mapping("EXPN_CAR_RD", "expn_car_rd"),
                mapping("SBWY_NTW", "sbwy_ntw"),
                mapping("BRG", "brg"),
                mapping("TNL", "tnl"),
                mapping("OVRP", "ovrp"),
                mapping("CRSWK", "crswk"),
                mapping("PARK", "park"),
                mapping("BLDG", "bldg")
        ), null, null, null, null, null);
    }

    private NormalizedSourceDefinition buildNationwideBusStop() {
        return new NormalizedSourceDefinition(PublicDataSourceType.NATIONWIDE_BUS_STOP, "pd_nationwide_bus_stop", List.of(
                mapping("경도", "longitude"),
                mapping("관리도시명", "admin_city_name"),
                mapping("도시명", "city_name"),
                mapping("도시코드", "city_code"),
                mapping("모바일단축번호", "mobile_short_no"),
                mapping("위도", "latitude"),
                mapping("정류장명", "stop_name"),
                mapping("정류장번호", "stop_id"),
                mapping("정보수집일", "collected_at")
        ), null, null, null, null, null);
    }

    private NormalizedSourceDefinition buildSeoulWheelchairRampStatus() {
        return new NormalizedSourceDefinition(PublicDataSourceType.SEOUL_WHEELCHAIR_RAMP_STATUS, "pd_seoul_wheelchair_ramp_status", List.of(
                mapping("호선", "line_name"),
                mapping("역명", "station_name"),
                mapping("구분", "division"),
                mapping("위치", "location")
        ), null, null, null, null, null);
    }

    private NormalizedSourceDefinition buildSeoulLowFloorBusRouteRetention() {
        return new NormalizedSourceDefinition(PublicDataSourceType.SEOUL_LOW_FLOOR_BUS_ROUTE_RETENTION, "pd_seoul_low_floor_bus_route_retention", List.of(
                mapping("노선\n번호", "route_no"),
                mapping("인가\n대수", "authorized_count"),
                mapping("저상버스 대수", "low_floor_bus_count"),
                mapping("저상보유율", "low_floor_retention_rate")
        ), null, null, null, null, null);
    }

    private NormalizedSourceDefinition buildNationwideTrafficLight() {
        return new NormalizedSourceDefinition(PublicDataSourceType.NATIONWIDE_TRAFFIC_LIGHT, "pd_nationwide_traffic_light", List.of(
                mapping("ctprvnNm", "ctprvn_nm"),
                mapping("signguNm", "signgu_nm"),
                mapping("roadKnd", "road_knd"),
                mapping("roadRouteNo", "road_route_no"),
                mapping("roadRouteNm", "road_route_nm"),
                mapping("roadRouteDrc", "road_route_drc"),
                mapping("rdnmadr", "rdnmadr"),
                mapping("lnmadr", "lnmadr"),
                mapping("latitude", "latitude"),
                mapping("longitude", "longitude"),
                mapping("sgngnrInstlMthd", "sgngnr_instl_mthd"),
                mapping("roadType", "road_type"),
                mapping("priorRoadYn", "prior_road_yn"),
                mapping("tfclghtManageNo", "tfclght_manage_no"),
                mapping("tfclghtSe", "tfclght_se"),
                mapping("tfclghtColorKnd", "tfclght_color_knd"),
                mapping("sgnaspMthd", "sgnasp_mthd"),
                mapping("sgnaspOrdr", "sgnasp_ordr"),
                mapping("sgnaspTime", "sgnasp_time"),
                mapping("sotKnd", "sot_knd"),
                mapping("signlCtrlMthd", "signl_ctrl_mthd"),
                mapping("signlTimeMthdType", "signl_time_mthd_type"),
                mapping("opratnYn", "opratn_yn"),
                mapping("flashingLightOpenHhmm", "flashing_light_open_hhmm"),
                mapping("flashingLightCloseHhmm", "flashing_light_close_hhmm"),
                mapping("fnctngSgngnrYn", "fnctng_sgngnr_yn"),
                mapping("remndrIdctYn", "remndr_idct_yn"),
                mapping("sondSgngnrYn", "sond_sgngnr_yn"),
                mapping("drcbrdSn", "drcbrd_sn"),
                mapping("institutionNm", "institution_nm"),
                mapping("phoneNumber", "phone_number"),
                mapping("referenceDate", "reference_date"),
                mapping("insttCode", "instt_code"),
                mapping("insttNm", "instt_nm")
        ), null, null, null, null, null);
    }

    private NormalizedSourceDefinition buildNationwideCrosswalk() {
        return new NormalizedSourceDefinition(PublicDataSourceType.NATIONWIDE_CROSSWALK, "pd_nationwide_crosswalk", List.of(
                mapping("ctprvnNm", "ctprvn_nm"),
                mapping("signguNm", "signgu_nm"),
                mapping("roadNm", "road_nm"),
                mapping("rdnmadr", "rdnmadr"),
                mapping("lnmadr", "lnmadr"),
                mapping("crslkManageNo", "crslk_manage_no"),
                mapping("crslkKnd", "crslk_knd"),
                mapping("bcyclCrslkCmbnatYn", "bcycl_crslk_cmbnat_yn"),
                mapping("highlandYn", "highland_yn"),
                mapping("latitude", "latitude"),
                mapping("longitude", "longitude"),
                mapping("cartrkCo", "cartrk_co"),
                mapping("bt", "bt"),
                mapping("et", "et"),
                mapping("tfclghtYn", "tfclght_yn"),
                mapping("fnctngSgngnrYn", "fnctng_sgngnr_yn"),
                mapping("sondSgngnrYn", "sond_sgngnr_yn"),
                mapping("greenSgngnrTime", "green_sgngnr_time"),
                mapping("redSgngnrTime", "red_sgngnr_time"),
                mapping("tfcilndYn", "tfcilnd_yn"),
                mapping("ftpthLowerYn", "ftpth_lower_yn"),
                mapping("brllBlckYn", "brll_blck_yn"),
                mapping("cnctrLghtFcltyYn", "cnctr_lght_fclty_yn"),
                mapping("institutionNm", "institution_nm"),
                mapping("phoneNumber", "phone_number"),
                mapping("referenceDate", "reference_date"),
                mapping("insttCode", "instt_code"),
                mapping("insttNm", "instt_nm")
        ), null, null, null, null, null);
    }

    private NormalizedSourceDefinition buildVocationalTraining() {
        return new NormalizedSourceDefinition(PublicDataSourceType.VOCATIONAL_TRAINING, "pd_vocational_training", List.of(
                mapping("address", "address"),
                mapping("certificate", "certificate"),
                mapping("contents", "contents"),
                mapping("courseMan", "course_man"),
                mapping("eiEmplCnt3", "ei_empl_cnt3"),
                mapping("eiEmplCnt3Gt10", "ei_empl_cnt3_gt10"),
                mapping("eiEmplRate3", "ei_empl_rate3"),
                mapping("eiEmplRate6", "ei_empl_rate6"),
                mapping("grade", "grade"),
                mapping("instCd", "inst_cd"),
                mapping("ncsCd", "ncs_cd"),
                mapping("realMan", "real_man"),
                mapping("regCourseMan", "reg_course_man"),
                mapping("stdgScor", "stdg_scor"),
                mapping("subTitle", "sub_title"),
                mapping("subTitleLink", "sub_title_link"),
                mapping("telNo", "tel_no"),
                mapping("title", "title"),
                mapping("titleIcon", "title_icon"),
                mapping("titleLink", "title_link"),
                mapping("traEndDate", "tra_end_date"),
                mapping("traStartDate", "tra_start_date"),
                mapping("trainTarget", "train_target"),
                mapping("trainTargetCd", "train_target_cd"),
                mapping("trainstCstId", "trainst_cst_id"),
                mapping("trngAreaCd", "trng_area_cd"),
                mapping("trprDegr", "trpr_degr"),
                mapping("trprId", "trpr_id"),
                mapping("wkendSe", "wkend_se"),
                mapping("yardMan", "yard_man")
        ), null, null, null, null, null);
    }

    private NormalizedSourceDefinition buildJobseekerCompetencyProgram() {
        return new NormalizedSourceDefinition(PublicDataSourceType.JOBSEEKER_COMPETENCY_PROGRAM, "pd_jobseeker_competency_program", List.of(
                mapping("orgNm", "org_nm"),
                mapping("pgmNm", "pgm_nm"),
                mapping("pgmSubNm", "pgm_sub_nm"),
                mapping("pgmTarget", "pgm_target"),
                mapping("pgmStdt", "pgm_stdt"),
                mapping("pgmEndt", "pgm_endt"),
                mapping("openTimeClcd", "open_time_clcd"),
                mapping("openTime", "open_time"),
                mapping("operationTime", "operation_time"),
                mapping("openPlcCont", "open_plc_cont")
        ), null, null, null, null, null);
    }

    private NormalizedColumnMapping mapping(String sourceField, String columnName) {
        return new NormalizedColumnMapping(sourceField, columnName);
    }
}
