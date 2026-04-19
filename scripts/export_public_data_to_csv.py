#!/usr/bin/env python3
"""브릿지워크 공공데이터를 API에서 직접 조회해 데이터별 CSV로 저장한다."""

from __future__ import annotations

import argparse
from calendar import monthrange
from datetime import date, datetime, timedelta
import html
import json
import os
import re
import sys
import time
import xml.etree.ElementTree as ET
import zipfile
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any
from urllib.parse import unquote
from zoneinfo import ZoneInfo

import pandas as pd
import requests

REQUIRED_ENV_KEYS = (
    "DATA_GO_KR_SERVICE_KEY",
    "KRIC_SERVICE_KEY",
    "WORK24_VOCATIONAL_TRAINING_AUTH_KEY",
    "WORK24_COMPETENCY_AUTH_KEY",
    "KRIC_STATION_CODE_XLSX_PATH",
)

PUBLIC_DATA_PK_PATTERN = re.compile(r'id="publicDataPk"[^>]*value="([^"]+)"')
PUBLIC_DATA_DETAIL_PK_PATTERN = re.compile(r'id="publicDataDetailPk"[^>]*value="([^"]+)"')

OOXML_SPREADSHEET_NS = {"m": "http://schemas.openxmlformats.org/spreadsheetml/2006/main"}
OOXML_REL_NS = {"r": "http://schemas.openxmlformats.org/officeDocument/2006/relationships"}
SEOUL_OPERATOR_NAME = "서울교통공사"
SEOUL_ZONE = ZoneInfo("Asia/Seoul")
RETRYABLE_STATUS_CODES = {429, 500, 502, 503, 504}
DEFAULT_MAX_RETRIES = 4
DEFAULT_RETRY_BACKOFF_MS = 500

@dataclass
class SourceSpec:
    name: str
    slug: str
    kind: str
    endpoint: str
    key: str | None = None
    params: dict[str, Any] = field(default_factory=dict)


def log_http_request(response: requests.Response) -> None:
    print(f"[HTTP] GET {response.url} status={response.status_code}")


def log_detected_count(source_slug: str, context: str, detected_count: int) -> None:
    print(f"[COUNT] {source_slug} {context} detected={detected_count}")


def load_dotenv_file(dotenv_path: Path) -> None:
    if not dotenv_path.exists():
        return

    for line in dotenv_path.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#") or "=" not in stripped:
            continue

        key, raw_value = stripped.split("=", 1)
        key = key.strip()
        value = raw_value.strip().strip('"').strip("'")
        if key and key not in os.environ:
            os.environ[key] = value


def load_scripts_dotenv() -> None:
    script_dir = Path(__file__).resolve().parent
    # 파이썬 스크립트 전용 키 파일은 scripts/.env만 사용한다.
    load_dotenv_file(script_dir / ".env")


def get_required_env(key: str) -> str:
    value = os.getenv(key, "").strip()
    if value:
        return value
    raise RuntimeError(f"필수 환경변수가 비어 있습니다: {key}")


def get_api_keys() -> dict[str, str]:
    missing_keys = [key for key in REQUIRED_ENV_KEYS if not os.getenv(key, "").strip()]
    if missing_keys:
        joined = ", ".join(missing_keys)
        raise RuntimeError(
            "필수 환경변수가 없습니다. "
            f"({joined}) scripts/.env에 값을 설정해 주세요."
        )

    api_keys = {key: get_required_env(key) for key in REQUIRED_ENV_KEYS}
    # data.go.kr serviceKey는 인코딩/디코딩 키 둘 다 입력될 수 있어 요청 전에 단일 형식으로 정규화한다.
    api_keys["DATA_GO_KR_SERVICE_KEY"] = normalize_data_go_service_key(api_keys["DATA_GO_KR_SERVICE_KEY"])
    return api_keys


def normalize_data_go_service_key(raw_key: str) -> str:
    # 이미 디코딩 키라면 그대로 유지되고, 인코딩 키라면 1회 복원된다.
    normalized = unquote(raw_key).strip()
    return normalized


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="공공데이터 API CSV 내보내기")
    parser.add_argument("--output-dir", default="exports", help="CSV 저장 디렉터리")
    parser.add_argument("--page-size", type=int, default=0, help="페이지당 조회 건수(0이면 API별 최대값)")
    parser.add_argument("--max-pages", type=int, default=0, help="최대 조회 페이지(0이면 API별 전체)")
    parser.add_argument("--timeout", type=int, default=20, help="요청 타임아웃(초)")
    parser.add_argument("--sleep-ms", type=int, default=80, help="요청 간 대기(ms)")
    parser.add_argument(
        "--kric-station-xlsx",
        default="",
        help="국가철도공단 역사 코드 xlsx 경로(미지정 시 KRIC_STATION_CODE_XLSX_PATH 사용)"
    )
    parser.add_argument(
        "--only-data-name",
        action="append",
        default=[],
        help="특정 데이터명만 실행(한글명/slug 지원, 반복 가능, 콤마 구분 가능)"
    )
    return parser.parse_args()


def source_specs(api_keys: dict[str, str]) -> list[SourceSpec]:
    data_go_kr_service_key = api_keys["DATA_GO_KR_SERVICE_KEY"]
    kric_service_key = api_keys["KRIC_SERVICE_KEY"]
    work24_vocational_auth_key = api_keys["WORK24_VOCATIONAL_TRAINING_AUTH_KEY"]
    work24_competency_auth_key = api_keys["WORK24_COMPETENCY_AUTH_KEY"]

    return [
        SourceSpec(
            name="한국장애인고용공단_장애인 구인 실시간 현황",
            slug="kepad_recruitment",
            kind="data_go_json",
            endpoint="http://apis.data.go.kr/B552583/job/job_list_env",
            key=data_go_kr_service_key,
            params={"item_id_field": "joReqstNo", "max_page_size": 1000, "max_page_count": 1000},
        ),
        SourceSpec(
            name="한국장애인고용공단_장애인 고용직무분류",
            slug="kepad_job_category",
            kind="data_go_json",
            endpoint="http://apis.data.go.kr/B552583/jobcode/job_code",
            key=data_go_kr_service_key,
            params={"item_id_field": "clCode", "max_page_size": 1000, "max_page_count": 1000},
        ),
        SourceSpec(
            name="한국장애인고용공단_장애인 표준사업장 실시간 조회",
            slug="kepad_standard_workplace",
            kind="data_go_json",
            endpoint="http://apis.data.go.kr/B552583/comp/comp_auth",
            key=data_go_kr_service_key,
            params={"item_id_field": "bsnmNo", "max_page_size": 1000, "max_page_count": 1000},
        ),
        SourceSpec(
            name="한국장애인고용공단_근로지원인 수행기관 실시간 정보",
            slug="kepad_support_agency",
            kind="data_go_json",
            endpoint="http://apis.data.go.kr/B552583/instn/instn_list",
            key=data_go_kr_service_key,
            params={"item_id_field": "insttCode", "max_page_size": 1000, "max_page_count": 1000},
        ),
        SourceSpec(
            name="전국교통약자이동지원센터정보표준데이터",
            slug="transport_support_center",
            kind="data_go_json",
            endpoint="https://api.data.go.kr/openapi/tn_pubr_public_tfcwker_mvmn_cnter_api",
            key=data_go_kr_service_key,
            params={
                "item_id_field": "id",
                "response_type_param": "type",
                "max_page_size": 1000,
                "max_page_count": 1000,
            },
        ),
        SourceSpec(
            name="국가철도공단_역사별 휠체어리프트 위치",
            slug="rail_wheelchair_lift",
            kind="kric_json",
            endpoint="https://openapi.kric.go.kr/openapi/vulnerableUserInfo/stationWheelchairLiftLocation",
            key=kric_service_key,
            params={
                "format": "json",
                "service": "vulnerableUserInfo",
                "operation": "stationWheelchairLiftLocation",
                "item_id_field": "stinCd",
                "min_sleep_ms": 300,
            },
        ),
        SourceSpec(
            name="서울교통공사_휠체어리프트 설치현황",
            slug="seoul_wheelchair_lift",
            kind="seoul_filedata",
            endpoint="https://www.data.go.kr/data/15044262/fileData.do",
            key=data_go_kr_service_key,
            params={"item_id_field": "승강기 일련번호", "max_page_size": 10000, "max_page_count": 1000},
        ),
        SourceSpec(
            name="한국고용정보원_직업훈련_국민내일배움카드 훈련과정",
            slug="vocational_training",
            kind="work24_vocational",
            endpoint="https://www.work24.go.kr/cm/openApi/call/hr/callOpenApiSvcInfo310L01.do",
            key=work24_vocational_auth_key,
            params={"item_id_field": "trprId", "max_page_size": 100, "max_page_count": 1000},
        ),
        SourceSpec(
            name="한국고용정보원_구직자취업역량 강화프로그램",
            slug="jobseeker_competency_program",
            kind="work24_competency",
            endpoint="https://www.work24.go.kr/cm/openApi/call/wk/callOpenApiSvcInfo217L01.do",
            key=work24_competency_auth_key,
            params={"item_id_field": "orgNm", "max_page_size": 100, "max_page_count": 1000},
        ),
    ]


def request_json(session: requests.Session, url: str, params: dict[str, Any], timeout: int) -> dict[str, Any] | list[Any]:
    for attempt in range(DEFAULT_MAX_RETRIES + 1):
        try:
            response = session.get(url, params=params, timeout=timeout)
            log_http_request(response)
            if response.status_code in RETRYABLE_STATUS_CODES and attempt < DEFAULT_MAX_RETRIES:
                sleep_seconds = (DEFAULT_RETRY_BACKOFF_MS * (2 ** attempt)) / 1000
                print(f"[RETRY] status={response.status_code} attempt={attempt + 1} sleep={sleep_seconds:.2f}s")
                time.sleep(sleep_seconds)
                continue

            raise_for_status_with_context(response)
            try:
                return response.json()
            except ValueError as exc:
                body = response.text[:400].replace("\n", " ").strip()
                raise RuntimeError(f"JSON 파싱 실패 | url={response.url} | body={body}") from exc
        except (
            requests.exceptions.ConnectionError,
            requests.exceptions.Timeout,
            requests.exceptions.ChunkedEncodingError,
        ):
            if attempt >= DEFAULT_MAX_RETRIES:
                raise
            sleep_seconds = (DEFAULT_RETRY_BACKOFF_MS * (2 ** attempt)) / 1000
            print(f"[RETRY] connection_error attempt={attempt + 1} sleep={sleep_seconds:.2f}s")
            time.sleep(sleep_seconds)

    raise RuntimeError(f"JSON 요청 재시도 한도 초과 | url={url}")


def request_text(session: requests.Session, url: str, params: dict[str, Any], timeout: int) -> str:
    response = session.get(url, params=params, timeout=timeout)
    log_http_request(response)
    raise_for_status_with_context(response)
    response.encoding = "utf-8"
    return response.text


def raise_for_status_with_context(response: requests.Response) -> None:
    try:
        response.raise_for_status()
    except requests.HTTPError as exc:
        body = response.text[:400].replace("\n", " ").strip()
        message = f"{exc} | url={response.url} | body={body}"
        raise requests.HTTPError(message, response=response) from exc


def normalize_items(node: Any) -> list[dict[str, Any]]:
    if node is None:
        return []
    if isinstance(node, list):
        return [item for item in node if isinstance(item, dict)]
    if isinstance(node, dict):
        return [node]
    return []


def resolve_page_size(spec: SourceSpec, requested_page_size: int) -> int:
    api_max_page_size = int(spec.params.get("max_page_size", 100))
    if requested_page_size <= 0:
        return api_max_page_size
    return min(requested_page_size, api_max_page_size)


def resolve_max_pages(spec: SourceSpec, requested_max_pages: int) -> int:
    api_max_pages = int(spec.params.get("max_page_count", 1000))
    if requested_max_pages <= 0:
        return api_max_pages
    return min(requested_max_pages, api_max_pages)


def extract_data_go_items(payload: dict[str, Any]) -> tuple[list[dict[str, Any]], int | None]:
    body = payload.get("response", {}).get("body", {})
    items_node = body.get("items")
    if isinstance(items_node, dict):
        items_node = items_node.get("item")
    items = normalize_items(items_node)
    total_count = body.get("totalCount")
    try:
        total_count = int(total_count) if total_count is not None else None
    except (TypeError, ValueError):
        total_count = None
    return items, total_count


def validate_data_go_result(payload: dict[str, Any] | list[Any]) -> None:
    if not isinstance(payload, dict):
        return

    header = payload.get("response", {}).get("header", {})
    result_code = str(header.get("resultCode", "")).strip()
    result_msg = str(header.get("resultMsg", "")).strip()
    if result_code and result_code not in {"00", "0000", "0"}:
        raise RuntimeError(f"공공데이터 API 오류(resultCode={result_code}): {result_msg or '알 수 없는 오류'}")


def fetch_data_go_json(
    session: requests.Session,
    spec: SourceSpec,
    page_size: int,
    max_pages: int,
    timeout: int,
    sleep_ms: int,
) -> list[dict[str, Any]]:
    records: list[dict[str, Any]] = []
    for page_no in range(1, max_pages + 1):
        response_type_param = str(spec.params.get("response_type_param", "_type")).strip() or "_type"
        params = {
            "serviceKey": spec.key,
            "pageNo": page_no,
            "numOfRows": page_size,
            response_type_param: "json",
        }
        payload = request_json(session, spec.endpoint, params, timeout)
        validate_data_go_result(payload)
        if not isinstance(payload, dict):
            break
        items, total_count = extract_data_go_items(payload)
        log_detected_count(spec.slug, f"page={page_no}", len(items))
        if not items:
            break
        records.extend(items)
        if total_count is not None and page_no * page_size >= total_count:
            break
        if len(items) < page_size:
            break
        time.sleep(sleep_ms / 1000)
    return records


def find_seoul_detail_identifiers(file_data_html: str) -> tuple[str, str]:
    public_data_pk_match = PUBLIC_DATA_PK_PATTERN.search(file_data_html)
    public_data_detail_pk_match = PUBLIC_DATA_DETAIL_PK_PATTERN.search(file_data_html)

    if not public_data_pk_match or not public_data_detail_pk_match:
        raise RuntimeError("서울 fileData 페이지에서 publicDataPk/publicDataDetailPk를 찾지 못했습니다.")

    return public_data_pk_match.group(1), public_data_detail_pk_match.group(1)


def fetch_seoul_filedata(
    session: requests.Session,
    spec: SourceSpec,
    page_size: int,
    max_pages: int,
    timeout: int,
    sleep_ms: int,
    station_codes: list[dict[str, str]],
) -> list[dict[str, Any]]:
    html_body = request_text(session, spec.endpoint, {}, timeout)
    public_data_pk, public_data_detail_pk = find_seoul_detail_identifiers(html_body)

    endpoint = f"https://api.odcloud.kr/api/{public_data_pk}/v1/{public_data_detail_pk}"
    records: list[dict[str, Any]] = []
    deduped_keys: set[str] = set()

    station_names = extract_seoul_station_names(station_codes)
    if station_names:
        for index, station_name in enumerate(station_names, start=1):
            params = {
                "serviceKey": spec.key,
                "page": 1,
                "perPage": page_size,
                "returnType": "JSON",
                "역명": station_name,
            }
            payload = request_json(session, endpoint, params, timeout)
            if not isinstance(payload, dict):
                continue

            station_items = normalize_items(payload.get("data"))
            log_detected_count(spec.slug, f"station={station_name}", len(station_items))
            for item in station_items:
                dedupe_key = build_record_dedupe_key(item, spec.params.get("item_id_field", ""))
                if dedupe_key in deduped_keys:
                    continue
                deduped_keys.add(dedupe_key)
                records.append(item)

            if sleep_ms > 0 and index < len(station_names):
                time.sleep(sleep_ms / 1000)

        if records:
            return records

    for page in range(1, max_pages + 1):
        params = {
            "serviceKey": spec.key,
            "page": page,
            "perPage": page_size,
            "returnType": "JSON",
        }
        payload = request_json(session, endpoint, params, timeout)
        if not isinstance(payload, dict):
            break

        items = normalize_items(payload.get("data"))
        log_detected_count(spec.slug, f"page={page}", len(items))
        total_count = payload.get("totalCount")

        if not items:
            break

        records.extend(items)

        try:
            total_count_value = int(total_count) if total_count is not None else None
        except (TypeError, ValueError):
            total_count_value = None

        if total_count_value is not None and page * page_size >= total_count_value:
            break
        if len(items) < page_size:
            break
        time.sleep(sleep_ms / 1000)

    return records


def extract_seoul_station_names(station_codes: list[dict[str, str]]) -> list[str]:
    station_names: set[str] = set()
    for station_code in station_codes:
        operator_name = (station_code.get("railOprIsttNm", "") or "").strip()
        station_name = (station_code.get("stinNm", "") or "").strip()
        if not station_name:
            continue
        if operator_name and SEOUL_OPERATOR_NAME not in operator_name:
            continue
        station_names.add(station_name)
    return sorted(station_names)


def build_record_dedupe_key(item: dict[str, Any], item_id_field: str) -> str:
    item_id = (item.get(item_id_field, "") if item_id_field else "") or ""
    item_id_text = str(item_id).strip()
    if item_id_text:
        return f"id:{item_id_text}"
    return "payload:" + json.dumps(item, ensure_ascii=False, sort_keys=True)


def column_ref_to_index(cell_ref: str) -> int:
    letters = "".join(ch for ch in cell_ref if ch.isalpha()).upper()
    if not letters:
        return -1
    index = 0
    for char in letters:
        index = index * 26 + (ord(char) - ord("A") + 1)
    return index - 1


def parse_xlsx_shared_strings(archive: zipfile.ZipFile) -> list[str]:
    shared_strings_path = "xl/sharedStrings.xml"
    if shared_strings_path not in archive.namelist():
        return []

    root = ET.fromstring(archive.read(shared_strings_path))
    shared_strings: list[str] = []
    for si in root.findall("m:si", OOXML_SPREADSHEET_NS):
        text = "".join(node.text or "" for node in si.findall(".//m:t", OOXML_SPREADSHEET_NS))
        shared_strings.append(text)
    return shared_strings


def resolve_first_sheet_xml_path(archive: zipfile.ZipFile) -> str:
    workbook = ET.fromstring(archive.read("xl/workbook.xml"))
    first_sheet = workbook.find("m:sheets", OOXML_SPREADSHEET_NS).findall("m:sheet", OOXML_SPREADSHEET_NS)[0]
    sheet_rid = first_sheet.attrib.get("{http://schemas.openxmlformats.org/officeDocument/2006/relationships}id", "")

    relations = ET.fromstring(archive.read("xl/_rels/workbook.xml.rels"))
    sheet_target = ""
    for relation in relations:
        if relation.attrib.get("Id") == sheet_rid:
            sheet_target = relation.attrib.get("Target", "")
            break

    if not sheet_target:
        raise RuntimeError("xlsx에서 첫 번째 시트 경로를 찾지 못했습니다.")
    return sheet_target if sheet_target.startswith("xl/") else f"xl/{sheet_target}"


def extract_cell_text(cell: ET.Element, shared_strings: list[str]) -> str:
    cell_type = cell.attrib.get("t", "")

    if cell_type == "inlineStr":
        inline_text = "".join(node.text or "" for node in cell.findall(".//m:t", OOXML_SPREADSHEET_NS))
        return inline_text.strip()

    value_node = cell.find("m:v", OOXML_SPREADSHEET_NS)
    raw_text = (value_node.text or "").strip() if value_node is not None else ""
    if not raw_text:
        return ""

    if cell_type == "s":
        try:
            return shared_strings[int(raw_text)].strip()
        except (ValueError, IndexError):
            return raw_text

    return raw_text


def load_kric_station_codes_from_xlsx(xlsx_path: Path) -> list[dict[str, str]]:
    if not xlsx_path.exists():
        raise RuntimeError(f"국가철도공단 역사 코드 xlsx 파일이 없습니다: {xlsx_path}")

    with zipfile.ZipFile(xlsx_path) as archive:
        shared_strings = parse_xlsx_shared_strings(archive)
        sheet_path = resolve_first_sheet_xml_path(archive)
        sheet_root = ET.fromstring(archive.read(sheet_path))

    rows = sheet_root.findall(".//m:sheetData/m:row", OOXML_SPREADSHEET_NS)
    if not rows:
        raise RuntimeError("국가철도공단 역사 코드 xlsx에 데이터가 없습니다.")

    parsed_rows: list[dict[int, str]] = []
    for row in rows:
        row_map: dict[int, str] = {}
        for cell in row.findall("m:c", OOXML_SPREADSHEET_NS):
            cell_ref = cell.attrib.get("r", "")
            column_index = column_ref_to_index(cell_ref)
            if column_index < 0:
                continue
            row_map[column_index] = extract_cell_text(cell, shared_strings)
        if row_map:
            parsed_rows.append(row_map)

    if not parsed_rows:
        raise RuntimeError("국가철도공단 역사 코드 xlsx 파싱 결과가 비어 있습니다.")

    header_row = parsed_rows[0]
    header_to_index: dict[str, int] = {
        (value or "").strip().upper(): idx
        for idx, value in header_row.items()
    }

    rail_index = header_to_index.get("RAIL_OPR_ISTT_CD")
    rail_name_index = header_to_index.get("RAIL_OPR_ISTT_NM")
    line_index = header_to_index.get("LN_CD")
    line_name_index = header_to_index.get("LN_NM")
    station_index = header_to_index.get("STIN_CD")
    station_name_index = header_to_index.get("STIN_NM")

    if rail_index is None or line_index is None or station_index is None:
        raise RuntimeError("xlsx 필수 컬럼이 없습니다. 필요 컬럼: RAIL_OPR_ISTT_CD, LN_CD, STIN_CD")

    deduped: dict[tuple[str, str, str], dict[str, str]] = {}

    for row_map in parsed_rows[1:]:
        rail_code = (row_map.get(rail_index, "") or "").strip()
        line_code = (row_map.get(line_index, "") or "").strip()
        station_code = (row_map.get(station_index, "") or "").strip()
        if not rail_code or not line_code or not station_code:
            continue

        dedupe_key = (rail_code, line_code, station_code)
        deduped[dedupe_key] = {
            "railOprIsttCd": rail_code,
            "lnCd": line_code,
            "stinCd": station_code,
            "railOprIsttNm": (row_map.get(rail_name_index, "") or "").strip() if rail_name_index is not None else "",
            "lnNm": (row_map.get(line_name_index, "") or "").strip() if line_name_index is not None else "",
            "stinNm": (row_map.get(station_name_index, "") or "").strip() if station_name_index is not None else "",
        }

    station_codes = list(deduped.values())
    if not station_codes:
        raise RuntimeError("xlsx에서 railOprIsttCd/lnCd/stinCd 유효 행을 찾지 못했습니다.")
    return station_codes


def extract_kric_items(payload: dict[str, Any] | list[Any]) -> list[dict[str, Any]]:
    if isinstance(payload, list):
        return [item for item in payload if isinstance(item, dict)]

    if not isinstance(payload, dict):
        return []

    for candidate_key in ("body", "data", "items", "result", "response"):
        node = payload.get(candidate_key)
        items = normalize_items(node)
        if items:
            return items
        if isinstance(node, dict):
            for nested_key in ("item", "items", "list", "row", "data", "body", "result"):
                nested_items = normalize_items(node.get(nested_key))
                if nested_items:
                    return nested_items
    return []


def normalize_kric_row(item: dict[str, Any], station_code: dict[str, str]) -> dict[str, str]:
    row: dict[str, str] = {}
    for field_name, value in item.items():
        if isinstance(value, (dict, list)):
            row[field_name] = json.dumps(value, ensure_ascii=False)
        elif value is None:
            row[field_name] = ""
        else:
            row[field_name] = str(value).strip()

    if not row.get("lnCd"):
        row["lnCd"] = station_code["lnCd"]
    if not row.get("railOprIsttCd"):
        row["railOprIsttCd"] = station_code["railOprIsttCd"]
    if not row.get("stinCd"):
        row["stinCd"] = station_code["stinCd"]

    # 역사 코드 파일의 표준 컬럼명을 그대로 저장해 후처리 조인 없이 조회할 수 있게 한다.
    if not row.get("LN_NM"):
        row["LN_NM"] = station_code.get("lnNm", "")
    if not row.get("STIN_NM"):
        row["STIN_NM"] = station_code.get("stinNm", "")

    return row


def fetch_kric_lift(
    session: requests.Session,
    spec: SourceSpec,
    station_codes: list[dict[str, str]],
    timeout: int,
    sleep_ms: int,
) -> list[dict[str, Any]]:
    records: list[dict[str, Any]] = []
    effective_sleep_ms = max(sleep_ms, int(spec.params.get("min_sleep_ms", 0)))

    for index, station_code in enumerate(station_codes, start=1):
        params = {
            "serviceKey": spec.key,
            "format": spec.params.get("format", "json"),
            "service": spec.params.get("service"),
            "operation": spec.params.get("operation"),
            "railOprIsttCd": station_code["railOprIsttCd"],
            "lnCd": station_code["lnCd"],
            "stinCd": station_code["stinCd"],
        }
        params = {key: value for key, value in params.items() if value not in (None, "")}
        payload = request_json(session, spec.endpoint, params, timeout)

        if isinstance(payload, dict):
            header = payload.get("header", {})
            result_code = str(header.get("resultCode", "")).strip()
            result_msg = str(header.get("resultMsg", "")).strip()

            if result_code == "03":
                # 국가철도공단은 조회 데이터가 없을 때 resultCode=03으로 응답한다.
                log_detected_count(
                    spec.slug,
                    f"railOprIsttCd={station_code['railOprIsttCd']},lnCd={station_code['lnCd']},stinCd={station_code['stinCd']}",
                    0,
                )
                if effective_sleep_ms > 0 and index < len(station_codes):
                    time.sleep(effective_sleep_ms / 1000)
                continue

            if result_code and result_code != "00":
                raise RuntimeError(
                    "국가철도공단 API 오류"
                    f"(resultCode={result_code}, railOprIsttCd={station_code['railOprIsttCd']},"
                    f" lnCd={station_code['lnCd']}, stinCd={station_code['stinCd']}): "
                    f"{result_msg or '알 수 없는 오류'}"
                )

        items = extract_kric_items(payload)
        log_detected_count(
            spec.slug,
            f"railOprIsttCd={station_code['railOprIsttCd']},lnCd={station_code['lnCd']},stinCd={station_code['stinCd']}",
            len(items),
        )
        for item in items:
            records.append(normalize_kric_row(item, station_code))

        if effective_sleep_ms > 0 and index < len(station_codes):
            time.sleep(effective_sleep_ms / 1000)

    return records


def xml_to_dict(element: ET.Element) -> dict[str, Any]:
    row: dict[str, Any] = {}
    for child in list(element):
        if list(child):
            row[child.tag] = json.dumps(xml_to_dict(child), ensure_ascii=False)
        else:
            row[child.tag] = html.unescape((child.text or "").strip())
    return row


def parse_work24_xml(xml_text: str) -> ET.Element:
    return ET.fromstring(xml_text)


def fetch_work24_vocational(
    session: requests.Session,
    spec: SourceSpec,
    page_size: int,
    max_pages: int,
    timeout: int,
    sleep_ms: int,
) -> list[dict[str, Any]]:
    records: list[dict[str, Any]] = []

    for page in range(1, max_pages + 1):
        params = {
            "authKey": spec.key,
            "returnType": "XML",
            "pageNum": page,
            "pageSize": page_size,
        }
        xml_text = request_text(session, spec.endpoint, params, timeout)
        root = parse_work24_xml(xml_text)

        total_count_text = root.findtext("scn_cnt")
        try:
            total_count = int(total_count_text) if total_count_text else None
        except ValueError:
            total_count = None

        srch_list = root.find("srchList")
        items = [] if srch_list is None else [xml_to_dict(node) for node in srch_list.findall("scn_list")]
        log_detected_count(spec.slug, f"page={page}", len(items))
        if not items:
            break

        records.extend(items)

        if total_count is not None and page * page_size >= total_count:
            break
        if len(items) < page_size:
            break
        time.sleep(sleep_ms / 1000)

    return records


def fetch_work24_competency(
    session: requests.Session,
    spec: SourceSpec,
    page_size: int,
    max_pages: int,
    timeout: int,
    sleep_ms: int,
) -> list[dict[str, Any]]:
    records: list[dict[str, Any]] = []
    deduped_keys: set[str] = set()

    start_date = datetime.now(SEOUL_ZONE).date()
    end_date = add_one_month(start_date)
    current_date = start_date

    while current_date <= end_date:
        pgm_stdt = current_date.strftime("%Y%m%d")

        for page in range(1, max_pages + 1):
            params = {
                "authKey": spec.key,
                "returnType": "XML",
                "startPage": page,
                "display": page_size,
                "pgmStdt": pgm_stdt,
            }
            xml_text = request_text(session, spec.endpoint, params, timeout)
            root = parse_work24_xml(xml_text)

            message_cd = root.findtext("messageCd")
            if message_cd == "006":
                # 날짜별 무데이터 응답은 다음 날짜 조회로 넘어간다.
                break

            total_text = root.findtext("total")
            try:
                total_count = int(total_text) if total_text else None
            except ValueError:
                total_count = None

            invite_nodes = root.findall("empPgmSchdInvite")
            if not invite_nodes:
                invite_root = root.find("empPgmSchdInviteList")
                invite_nodes = [] if invite_root is None else invite_root.findall("empPgmSchdInvite")
            log_detected_count(spec.slug, f"pgmStdt={pgm_stdt},page={page}", len(invite_nodes))

            if not invite_nodes:
                break

            for invite_node in invite_nodes:
                row = xml_to_dict(invite_node)
                dedupe_key = json.dumps(row, ensure_ascii=False, sort_keys=True)
                if dedupe_key in deduped_keys:
                    continue
                deduped_keys.add(dedupe_key)
                records.append(row)

            if total_count is not None and page * page_size >= total_count:
                break
            if len(invite_nodes) < page_size:
                break
            time.sleep(sleep_ms / 1000)

        current_date += timedelta(days=1)

    return records


def add_one_month(base_date: date) -> date:
    next_month_year = base_date.year + (1 if base_date.month == 12 else 0)
    next_month = 1 if base_date.month == 12 else base_date.month + 1
    next_month_last_day = monthrange(next_month_year, next_month)[1]
    return base_date.replace(year=next_month_year, month=next_month, day=min(base_date.day, next_month_last_day))


def save_result_file(output_dir: Path,
                     base_name: str,
                     rows: list[dict[str, Any]]) -> Path:
    output_dir.mkdir(parents=True, exist_ok=True)
    df = pd.DataFrame(rows)

    output_path = output_dir / f"{base_name}.csv"
    df.to_csv(output_path, index=False)
    return output_path


def run_one_source(
    session: requests.Session,
    spec: SourceSpec,
    requested_page_size: int,
    requested_max_pages: int,
    timeout: int,
    sleep_ms: int,
    kric_station_codes: list[dict[str, str]],
) -> list[dict[str, Any]]:
    page_size = resolve_page_size(spec, requested_page_size)
    max_pages = resolve_max_pages(spec, requested_max_pages)

    if spec.kind == "data_go_json":
        return fetch_data_go_json(session, spec, page_size, max_pages, timeout, sleep_ms)
    if spec.kind == "seoul_filedata":
        return fetch_seoul_filedata(session, spec, page_size, max_pages, timeout, sleep_ms, kric_station_codes)
    if spec.kind == "kric_json":
        return fetch_kric_lift(session, spec, kric_station_codes, timeout, sleep_ms)
    if spec.kind == "work24_vocational":
        return fetch_work24_vocational(session, spec, page_size, max_pages, timeout, sleep_ms)
    if spec.kind == "work24_competency":
        return fetch_work24_competency(session, spec, page_size, max_pages, timeout, sleep_ms)
    raise RuntimeError(f"지원하지 않는 source kind: {spec.kind}")


def resolve_kric_station_xlsx_path(cli_arg: str) -> Path:
    candidate = (cli_arg or "").strip()
    if not candidate:
        candidate = os.getenv("KRIC_STATION_CODE_XLSX_PATH", "").strip()

    if not candidate:
        raise RuntimeError("국가철도공단 역사 코드 파일 경로가 없습니다. --kric-station-xlsx 또는 KRIC_STATION_CODE_XLSX_PATH를 설정해 주세요.")

    xlsx_path = Path(candidate).expanduser().resolve()
    if not xlsx_path.exists():
        raise RuntimeError(f"국가철도공단 역사 코드 파일을 찾을 수 없습니다: {xlsx_path}")
    return xlsx_path


def normalize_target_name(raw_name: str) -> str:
    normalized = raw_name.strip()
    if normalized.lower().endswith(".csv"):
        normalized = normalized[:-4]
    return normalized.strip().lower()


def parse_target_data_names(raw_values: list[str]) -> list[str]:
    target_names: list[str] = []
    seen_targets: set[str] = set()
    for raw_value in raw_values:
        for token in raw_value.split(","):
            candidate = normalize_target_name(token)
            if candidate and candidate not in seen_targets:
                seen_targets.add(candidate)
                target_names.append(candidate)
    return target_names


def filter_source_specs(specs: list[SourceSpec], target_names: list[str]) -> list[SourceSpec]:
    if not target_names:
        return specs

    lookup: dict[str, SourceSpec] = {}
    for spec in specs:
        lookup[normalize_target_name(spec.name)] = spec
        lookup[normalize_target_name(spec.slug)] = spec

    filtered_specs: list[SourceSpec] = []
    selected_slugs: set[str] = set()
    missing_names: list[str] = []

    for target_name in target_names:
        matched_spec = lookup.get(target_name)
        if matched_spec is None:
            missing_names.append(target_name)
            continue
        if matched_spec.slug in selected_slugs:
            continue
        filtered_specs.append(matched_spec)
        selected_slugs.add(matched_spec.slug)

    if missing_names:
        joined_missing = ", ".join(missing_names)
        available_names = ", ".join(f"{spec.name} ({spec.slug})" for spec in specs)
        raise RuntimeError(
            f"요청한 데이터명을 찾을 수 없습니다: {joined_missing}. "
            f"가능한 데이터명: {available_names}"
        )

    return filtered_specs


def main() -> int:
    load_scripts_dotenv()
    args = parse_args()
    output_dir = Path(args.output_dir).resolve()
    api_keys = get_api_keys()

    kric_station_xlsx_path = resolve_kric_station_xlsx_path(args.kric_station_xlsx)
    kric_station_codes = load_kric_station_codes_from_xlsx(kric_station_xlsx_path)

    session = requests.Session()
    session.headers.update({"User-Agent": "bridgework-public-data-export/1.0"})

    failed = False

    all_specs = source_specs(api_keys)
    target_names = parse_target_data_names(args.only_data_name)
    selected_specs = filter_source_specs(all_specs, target_names)
    print("[INFO] selected_sources:", ", ".join(spec.slug for spec in selected_specs))

    for spec in selected_specs:
        print(f"[START] {spec.name}")
        try:
            records = run_one_source(
                session,
                spec,
                requested_page_size=args.page_size,
                requested_max_pages=args.max_pages,
                timeout=args.timeout,
                sleep_ms=args.sleep_ms,
                kric_station_codes=kric_station_codes,
            )
            output_path = save_result_file(
                output_dir=output_dir,
                base_name=spec.slug,
                rows=records
            )
            print(f"[DONE] {spec.name} -> {output_path} ({len(records)} rows)")
        except Exception as exc:  # noqa: BLE001
            failed = True
            error_csv_path = output_dir / f"{spec.slug}_error.csv"
            if error_csv_path.exists():
                # 기존 오류 CSV가 남아 있으면 최신 실행 기준으로 제거한다.
                error_csv_path.unlink()
            print(f"[FAIL] {spec.name} ({exc})")

    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
