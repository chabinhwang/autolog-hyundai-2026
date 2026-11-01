import { defaultLogbookFilename } from '../lib/contentDisposition'
import { apiDownload } from './client'

export interface LogbookQuery {
  vehicleId: number
  regNo: string
  from: string
  to: string
}

export interface LogbookFile {
  blob: Blob
  filename: string
}

/** 기록부 XLSX를 받아 옵니다. 파일명은 filename* → 없으면 운행기록부_{regNo}_{from}_{to}.xlsx */
export async function fetchLogbook(query: LogbookQuery, signal?: AbortSignal): Promise<LogbookFile> {
  const { blob, filename } = await apiDownload('/reports/logbook', {
    query: { vehicleId: query.vehicleId, from: query.from, to: query.to, format: 'xlsx' },
    accept: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet, application/json',
    signal,
  })
  return { blob, filename: filename ?? defaultLogbookFilename(query.regNo, query.from, query.to) }
}
