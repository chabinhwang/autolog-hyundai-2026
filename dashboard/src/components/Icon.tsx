/**
 * 화면에서 쓰는 선형 아이콘 한 벌 (24px 격자, 선 두께 1.75, 둥근 끝, currentColor).
 * 모든 아이콘은 같은 규칙으로 그렸습니다. 장식용이라 스크린 리더에서 숨깁니다.
 */
const PATHS = {
  // 메뉴
  home: 'M4 10.2 12 4l8 6.2V19a1 1 0 0 1-1 1h-4.5v-5.5h-5V20H5a1 1 0 0 1-1-1z',
  route: 'M6.5 20a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5ZM17.5 9a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5ZM9 17.5h6.5a3 3 0 0 0 0-6h-7a3 3 0 0 1 0-6H15',
  car: 'M5 17h14M5 17v2.5h2.5V17M16.5 17v2.5H19V17M5 17l-1-1v-4l2.2-5.1A2 2 0 0 1 8 5.7h8a2 2 0 0 1 1.8 1.2L20 12v4l-1 1M4.5 12h15M7.5 14.5h.01M16.5 14.5h.01',
  users:
    'M15.5 20v-1.5a3.5 3.5 0 0 0-3.5-3.5H7a3.5 3.5 0 0 0-3.5 3.5V20M9.5 11.5a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7ZM20.5 20v-1.5a3.5 3.5 0 0 0-2.5-3.35M15 4.65a3.5 3.5 0 0 1 0 6.7',
  pin: 'M12 21s-7-6.2-7-11.5a7 7 0 1 1 14 0C19 14.8 12 21 12 21Zm0-9a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5Z',
  download: 'M12 4v11m0 0-4.5-4.5M12 15l4.5-4.5M5 19.5h14',
  logout: 'M15 4h3a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-3M10 16.5 5.5 12 10 7.5M5.5 12H15',
  // 요약
  gauge: 'M4.5 17.5a8.5 8.5 0 1 1 15 0M12 13.5l3.5-4.5M12 14.5a1 1 0 1 0 0-2 1 1 0 0 0 0 2Z',
  question: 'M9.5 9.5a2.5 2.5 0 1 1 3.5 2.3c-.6.3-1 .9-1 1.6v.1M12 17h.01M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18Z',
  alert: 'M12 9.5v4M12 17h.01M10.3 4.3 2.8 17.5A2 2 0 0 0 4.5 20.5h15a2 2 0 0 0 1.7-3L13.7 4.3a2 2 0 0 0-3.4 0Z',
  // 동작
  plus: 'M12 5v14M5 12h14',
  edit: 'M4.5 19.5h4l10.3-10.3a2.1 2.1 0 0 0-3-3L5.5 16.5v3ZM14.5 7.5l3 3',
  link: 'M10 14a4 4 0 0 0 5.66 0l3-3a4 4 0 0 0-5.66-5.66l-1 1M14 10a4 4 0 0 0-5.66 0l-3 3a4 4 0 0 0 5.66 5.66l1-1',
  unlink:
    'M14.5 6.5l.8-.8a4 4 0 0 1 5.66 5.66l-.8.8M9.5 17.5l-.8.8a4 4 0 0 1-5.66-5.66l.8-.8M8 4v2M4 8h2M16 20v-2M20 16h-2',
  ban: 'M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18ZM5.7 5.7l12.6 12.6',
  restore: 'M4 12a8 8 0 1 0 2.4-5.7M4 4.5v4h4',
  trash: 'M4.5 7h15M10 11v5.5M14 11v5.5M6.5 7l.8 12a2 2 0 0 0 2 1.9h5.4a2 2 0 0 0 2-1.9l.8-12M9.5 7V4.5h5V7',
  search: 'M11 18a7 7 0 1 0 0-14 7 7 0 0 0 0 14ZM20 20l-4-4',
  check: 'M5 12.5l4.5 4.5L19 7.5',
  // 방향
  chevronLeft: 'M14.5 6l-6 6 6 6',
  chevronRight: 'M9.5 6l6 6-6 6',
  chevronDown: 'M6 9.5l6 6 6-6',
  arrowLeft: 'M19 12H5M11 18l-6-6 6-6',
  arrowRight: 'M5 12h14M13 6l6 6-6 6',
  // 상태
  info: 'M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18ZM12 11v5.5M12 7.8h.01',
  error: 'M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18ZM12 7.5v5.5M12 16.5h.01',
  success: 'M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18ZM8 12.3l2.8 2.8L16.2 9.7',
  lock: 'M6 11h12v9.5H6zM8.5 11V8a3.5 3.5 0 0 1 7 0v3',
  history: 'M3.8 12a8.2 8.2 0 1 0 2.4-5.8M3.8 4.5v4h4M12 8v4l2.8 1.8',
  shield: 'M12 3.2 5 6v5.2c0 4.3 2.9 8 7 9.6 4.1-1.6 7-5.3 7-9.6V6z',
  file: 'M14 3.5H7.5a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2h9a2 2 0 0 0 2-2V8M14 3.5 18.5 8M14 3.5V8h4.5M9 13h6M9 16.5h6',
  inbox: 'M3.5 13H8l1.5 2.5h5L16 13h4.5M6 5h12l2.5 8v5.5a1.5 1.5 0 0 1-1.5 1.5H5a1.5 1.5 0 0 1-1.5-1.5V13z',
  calendar: 'M4.5 6.5a1.5 1.5 0 0 1 1.5-1.5h12a1.5 1.5 0 0 1 1.5 1.5v12a1.5 1.5 0 0 1-1.5 1.5H6a1.5 1.5 0 0 1-1.5-1.5ZM4.5 10h15M8.5 3v4M15.5 3v4',
} as const

export type IconName = keyof typeof PATHS

export function Icon({ name, size = 20, className }: { name: IconName; size?: number; className?: string }) {
  return (
    <svg
      className={className ? `icon ${className}` : 'icon'}
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.75}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
    >
      <path d={PATHS[name]} />
    </svg>
  )
}

/** AutoLog 로고 마크: 계기판 바늘 (원본 도안). 브랜드 남색 한 가지 + 흰색 */
export function LogoMark({ size = 32 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 32 32" aria-hidden="true" focusable="false" className="logo-mark">
      <rect width="32" height="32" rx="9" fill="#002C5F" />
      <path d="M8 21.5a8.6 8.6 0 1 1 16 0" fill="none" stroke="#fff" strokeOpacity=".45" strokeWidth="2.4" strokeLinecap="round" />
      <path d="M16 18.2l4.8-5.6" stroke="#fff" strokeWidth="2.6" strokeLinecap="round" />
      <circle cx="16" cy="18.5" r="2.3" fill="#fff" />
    </svg>
  )
}
