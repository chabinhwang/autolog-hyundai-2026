package com.autolog.server.report;

import com.autolog.server.trip.BusinessCategory;
import com.autolog.server.trip.Purpose;
import com.autolog.server.trip.TripViews.TripListItem;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

/**
 * 업무용승용차 운행기록부 XLSX (D7: 1차는 별지 서식의 항목·열 순서를 맞춘 표).
 * 입력 행은 TripViewMapper가 마스킹한 값이며, 여기서는 표시 형식만 정한다.
 */
@Component
public class LogbookXlsxWriter {

	public static final String SHEET_NAME = "운행기록부";
	/** 표 머리글 행 번호 (0부터). 테스트가 이 위치로 읽는다. */
	public static final int HEADER_ROW = 7;
	public static final int WARNING_ROW = 6;
	public static final List<String> COLUMNS = List.of(
			"③ 사용일자(요일)", "④ 부서", "④ 성명", "⑤ 주행 전 계기판의 거리(km)", "⑥ 주행 후 계기판의 거리(km)",
			"⑦ 주행거리(km)", "⑧ 출퇴근용(km)", "⑨ 일반 업무용(km)", "⑩ 비고");
	public static final String TOTAL_KM_LABEL = "⑪ 과세기간 총주행거리(km)";
	public static final String BUSINESS_KM_LABEL = "⑫ 과세기간 업무용 사용거리(km)";
	public static final String RATIO_LABEL = "⑬ 업무사용비율(%)";

	private static final String[] WEEKDAYS = {"월", "화", "수", "목", "금", "토", "일"};

	public byte[] write(Header header, List<TripListItem> rows) {
		try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			Styles styles = new Styles(workbook);
			Sheet sheet = workbook.createSheet(SHEET_NAME);

			Row title = sheet.createRow(0);
			cell(title, 0, "업무용승용차 운행기록부", styles.title);
			sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, COLUMNS.size() - 1));
			labelValue(sheet, 1, "과세기간", header.from() + " ~ " + header.to(), styles);
			labelValue(sheet, 2, "상호명", header.orgName(), styles);
			labelValue(sheet, 3, "사업자등록번호", header.bizRegNo(), styles);
			labelValue(sheet, 4, "① 차종", header.modelName(), styles);
			labelValue(sheet, 5, "② 자동차등록번호", header.regNo(), styles);

			long unconfirmed = rows.stream().filter(row -> row.purpose() == Purpose.UNCONFIRMED).count();
			if (unconfirmed > 0) {
				cell(sheet.createRow(WARNING_ROW), 0,
						"※ 용도 미확인 운행 " + unconfirmed + "건이 포함되어 있습니다. 미확인 거리는 업무용 사용거리에 넣지 않았습니다.",
						styles.warning);
				sheet.addMergedRegion(new CellRangeAddress(WARNING_ROW, WARNING_ROW, 0, COLUMNS.size() - 1));
			}

			Row headerRow = sheet.createRow(HEADER_ROW);
			for (int i = 0; i < COLUMNS.size(); i++) {
				cell(headerRow, i, COLUMNS.get(i), styles.header);
			}

			BigDecimal total = Distances.ZERO_KM;
			BigDecimal businessUse = Distances.ZERO_KM;
			int rowIndex = HEADER_ROW + 1;
			for (TripListItem item : rows) {
				Row row = sheet.createRow(rowIndex++);
				cell(row, 0, usageDate(item.tripDate()), styles.text);
				cell(row, 1, item.driver() == null ? "" : item.driver().dept(), styles.text);
				cell(row, 2, item.driver() == null ? "" : item.driver().name(), styles.text);
				number(row, 3, item.odoStartKm(), styles.number);
				number(row, 4, item.odoEndKm(), styles.number);
				number(row, 5, item.distanceKm(), styles.number);
				number(row, 6, item.purpose() == Purpose.COMMUTE ? item.distanceKm() : null, styles.number);
				number(row, 7, item.purpose() == Purpose.BUSINESS ? item.distanceKm() : null, styles.number);
				cell(row, 8, remark(item), styles.text);
				total = total.add(item.distanceKm());
				if (item.purpose() == Purpose.COMMUTE || item.purpose() == Purpose.BUSINESS) {
					businessUse = businessUse.add(item.distanceKm());
				}
			}

			rowIndex++;
			Row totalRow = sheet.createRow(rowIndex++);
			cell(totalRow, 0, TOTAL_KM_LABEL, styles.header);
			number(totalRow, 1, total, styles.number);
			Row businessRow = sheet.createRow(rowIndex++);
			cell(businessRow, 0, BUSINESS_KM_LABEL, styles.header);
			number(businessRow, 1, businessUse, styles.number);
			Row ratioRow = sheet.createRow(rowIndex);
			cell(ratioRow, 0, RATIO_LABEL, styles.header);
			BigDecimal ratio = Distances.ratio(businessUse, total);
			if (ratio == null) {
				cell(ratioRow, 1, "-", styles.text);
			} else {
				number(ratioRow, 1, ratio, styles.number);
			}

			int[] widths = {16, 14, 10, 20, 20, 14, 14, 16, 48};
			for (int i = 0; i < widths.length; i++) {
				sheet.setColumnWidth(i, widths[i] * 256);
			}
			workbook.write(out);
			return out.toByteArray();
		} catch (IOException e) {
			throw new UncheckedIOException("기록부 XLSX를 만들지 못했습니다.", e);
		}
	}

	/** ③ 사용일자(요일): 2026-11-03(화) */
	static String usageDate(LocalDate date) {
		DayOfWeek dayOfWeek = date.getDayOfWeek();
		return date + "(" + WEEKDAYS[dayOfWeek.getValue() - 1] + ")";
	}

	/** ⑩ 비고: BUSINESS = 업무 세부 + 도착지 라벨 + 메모 / COMMUTE = 출퇴근 / PERSONAL = 빈칸 / UNCONFIRMED = 미확인 */
	static String remark(TripListItem item) {
		return switch (item.purpose()) {
			case BUSINESS -> {
				List<String> parts = new ArrayList<>();
				BusinessCategory category = item.businessCategory();
				if (category != null) {
					parts.add(category.label());
				}
				if (item.dest() != null && item.dest().label() != null) {
					parts.add(item.dest().label());
				}
				if (item.note() != null) {
					parts.add(item.note());
				}
				yield String.join(" / ", parts.stream().filter(Objects::nonNull).toList());
			}
			case COMMUTE -> "출퇴근";
			case PERSONAL -> "";
			case UNCONFIRMED -> "미확인";
		};
	}

	private static void labelValue(Sheet sheet, int rowIndex, String label, String value, Styles styles) {
		Row row = sheet.createRow(rowIndex);
		cell(row, 0, label, styles.header);
		cell(row, 1, value, styles.text);
		sheet.addMergedRegion(new CellRangeAddress(rowIndex, rowIndex, 1, 4));
	}

	private static void cell(Row row, int column, String value, CellStyle style) {
		Cell cell = row.createCell(column);
		cell.setCellValue(value);
		cell.setCellStyle(style);
	}

	private static void number(Row row, int column, BigDecimal value, CellStyle style) {
		Cell cell = row.createCell(column);
		if (value != null) {
			cell.setCellValue(value.doubleValue());
		}
		cell.setCellStyle(style);
	}

	public record Header(LocalDate from, LocalDate to, String orgName, String bizRegNo, String modelName,
			String regNo) {
	}

	private static final class Styles {

		final CellStyle title;
		final CellStyle header;
		final CellStyle text;
		final CellStyle number;
		final CellStyle warning;

		Styles(Workbook workbook) {
			Font bold = workbook.createFont();
			bold.setBold(true);
			Font titleFont = workbook.createFont();
			titleFont.setBold(true);
			titleFont.setFontHeightInPoints((short) 14);
			Font red = workbook.createFont();
			red.setBold(true);
			red.setColor(Font.COLOR_RED);

			title = workbook.createCellStyle();
			title.setFont(titleFont);
			title.setAlignment(HorizontalAlignment.CENTER);

			header = bordered(workbook);
			header.setFont(bold);
			header.setWrapText(true);

			text = bordered(workbook);

			number = bordered(workbook);
			number.setDataFormat(workbook.createDataFormat().getFormat("#,##0.0"));

			warning = workbook.createCellStyle();
			warning.setFont(red);
		}

		private static CellStyle bordered(Workbook workbook) {
			CellStyle style = workbook.createCellStyle();
			style.setBorderTop(BorderStyle.THIN);
			style.setBorderBottom(BorderStyle.THIN);
			style.setBorderLeft(BorderStyle.THIN);
			style.setBorderRight(BorderStyle.THIN);
			return style;
		}
	}
}
