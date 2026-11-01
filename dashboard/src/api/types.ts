// 계약 문서(docs/contracts/api)의 응답·요청 타입

export type Purpose = 'COMMUTE' | 'BUSINESS' | 'PERSONAL' | 'UNCONFIRMED'
export type PurposeSource = 'AUTO' | 'VOICE' | 'MANUAL' | 'ADMIN'
export type BusinessCategory = 'VISIT' | 'MEETING' | 'PROMOTION' | 'TRAINING' | 'OTHER'
export type PlaceType = 'COMPANY' | 'CLIENT'
export type EndpointType = 'HOME' | 'COMPANY' | 'CLIENT' | 'UNREGISTERED'
export type ActorType = 'DEVICE' | 'ADMIN'
export type SummaryWarning = 'RATIO_HIGH' | 'UNCONFIRMED_EXISTS' | 'SYNC_DELAYED' | 'ODO_GAP'

export interface Page<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

// 인증
export interface Admin {
  id: number
  loginId: string
  displayName: string
}

export interface Organization {
  id: number
  name: string
  /** /auth/me에만 있음 */
  bizRegNo?: string
}

export interface LoginRequest {
  loginId: string
  password: string
}

export interface LoginResponse {
  accessToken: string
  tokenType: string
  expiresIn: number
  admin: Admin
  organization: Organization
}

export interface MeResponse {
  admin: Admin
  organization: Organization
  expiresAt: string
}

// 기준정보
export interface DriverRef {
  id: number
  dept: string
  name: string
}

export interface Driver extends DriverRef {
  active: boolean
}

export interface DriverInput {
  dept: string
  name: string
}

export interface VehicleDevice {
  paired: boolean
  deviceId: number | null
  pairedAt: string | null
  lastSyncedAt: string | null
}

export interface Vehicle {
  id: number
  regNo: string
  modelName: string
  defaultDriver: DriverRef | null
  active: boolean
  device: VehicleDevice
}

export interface VehicleInput {
  regNo: string
  modelName: string
  defaultDriverId: number | null
}

export interface Place {
  id: number
  name: string
  type: PlaceType
  lat: number
  lng: number
  radiusM: number
  updatedAt: string
}

export interface PlaceInput {
  name: string
  type: PlaceType
  lat: number
  lng: number
  radiusM: number
}

// 페어링
export interface PairResponse {
  deviceId: number
  vehicleId: number
  pairedAt: string
  replacedDeviceId: number | null
}

export interface UnpairResponse {
  vehicleId: number
  deviceId: number
  revokedAt: string
}

// 운행
export interface TripEndpoint {
  type: EndpointType
  label: string | null
}

export interface TripBase {
  clientUuid: string
  tripDate: string
  vehicle: { id: number; regNo: string }
  driver: DriverRef | null
  /** 마스킹되면 null (rules §7) */
  startedAt: string | null
  endedAt: string | null
  odoStartKm: number
  odoEndKm: number
  distanceKm: number
  origin: TripEndpoint | null
  dest: TripEndpoint | null
  purpose: Purpose
  purposeSource: PurposeSource
  businessCategory: BusinessCategory | null
  note: string | null
  masked: boolean
}

export interface TripListItem extends TripBase {
  edited: boolean
}

export type EditValue = string | number | null

export interface TripEditChange {
  field: string
  before: EditValue
  after: EditValue
}

export interface TripEdit {
  seq: number
  actorType: ActorType
  actorName: string | null
  changes: TripEditChange[]
  reason: string | null
  editedAt: string
  receivedAt: string
}

export interface TripDetail extends TripBase {
  auto: { purpose: Purpose }
  edits: TripEdit[]
  recordedAt: string | null
  syncedAt: string | null
  appVersion: string | null
}

export interface TripListQuery {
  from: string
  to: string
  vehicleId?: number
  driverId?: number
  purpose?: Purpose
  page?: number
  size?: number
}

export interface TripEditChanges {
  purpose?: Purpose
  businessCategory?: BusinessCategory | null
  note?: string | null
  driverId?: number | null
}

export interface AdminTripEditRequest {
  reason: string
  changes: TripEditChanges
}

export interface TripEditResponse {
  edit: { seq: number; actorType: ActorType; changes: TripEditChange[] } | null
  current: {
    purpose: Purpose
    purposeSource: PurposeSource
    businessCategory: BusinessCategory | null
    note: string | null
    driverId: number | null
  }
}

// 대시보드 집계
export interface SummaryVehicle {
  vehicleId: number
  regNo: string
  modelName: string
  defaultDriver: DriverRef | null
  paired: boolean
  lastSyncedAt: string | null
  tripCount: number
  totalKm: number
  commuteKm: number
  businessKm: number
  personalKm: number
  unconfirmedKm: number
  unconfirmedCount: number
  businessRatio: number | null
  maxBusinessRatio: number | null
  warnings: string[]
}

export interface SummaryTotals {
  totalKm: number
  commuteKm: number
  businessKm: number
  personalKm: number
  unconfirmedKm: number
  unconfirmedCount: number
  businessRatio: number | null
}

export interface DashboardSummary {
  month: string
  vehicles: SummaryVehicle[]
  totals: SummaryTotals
}
