package com.autolog.app.domain

/** 서버 계약(docs/contracts/database.md 열거값)과 같은 이름을 쓴다. */
enum class Purpose { COMMUTE, BUSINESS, PERSONAL, UNCONFIRMED }

enum class PurposeSource { AUTO, VOICE, MANUAL, ADMIN }

enum class BusinessCategory { VISIT, MEETING, PROMOTION, TRAINING, OTHER }

enum class EndpointType { HOME, COMPANY, CLIENT, UNREGISTERED }

enum class SyncState { PENDING, SYNCED, FAILED }

/** 차량 기어. Pleos VehicleGear를 앱에서 쓰는 값으로 줄인 것. */
enum class Gear { PARK, DRIVE, REVERSE, NEUTRAL, UNKNOWN }
