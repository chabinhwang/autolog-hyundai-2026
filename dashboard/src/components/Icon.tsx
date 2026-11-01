/** 화면에서 쓰는 선형 아이콘 (24px 격자, currentColor). 장식용이라 스크린 리더에서 숨깁니다. */
const PATHS = {
  home: 'M3 10.5 12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6h-6v6H4a1 1 0 0 1-1-1z',
  route:
    'M6 19a2 2 0 1 0 0-4 2 2 0 0 0 0 4Zm12-10a2 2 0 1 0 0-4 2 2 0 0 0 0 4ZM8 17h7.5a3.5 3.5 0 0 0 0-7h-7a3.5 3.5 0 0 1 0-7H16',
  car: 'M5 16h14M5 16v3h2.5v-3M16.5 16v3H19v-3M5 16l-1-1v-3.5L6 6.8A2 2 0 0 1 7.9 5.5h8.2A2 2 0 0 1 18 6.8l2 4.7V15l-1 1M4.5 11.5h15M7.5 13.5h.01M16.5 13.5h.01',
  users:
    'M16 20v-1.5a3.5 3.5 0 0 0-3.5-3.5h-5A3.5 3.5 0 0 0 4 18.5V20M10 11a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7Zm10 9v-1.5a3.5 3.5 0 0 0-2.5-3.35M15 4.15a3.5 3.5 0 0 1 0 6.7',
  pin: 'M12 21s-7-6.2-7-11.5a7 7 0 1 1 14 0C19 14.8 12 21 12 21Zm0-9a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5Z',
  download: 'M12 4v11m0 0-4.5-4.5M12 15l4.5-4.5M5 19.5h14',
  logout: 'M15 4h3a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-3M10 16.5 5.5 12 10 7.5M5.5 12H15',
  gauge: 'M4.5 17.5a8.5 8.5 0 1 1 15 0M12 13.5l3.5-4.5M12 14.5a1 1 0 1 0 0-2 1 1 0 0 0 0 2Z',
  briefcase: 'M4 8h16v11H4zM9 8V5.5h6V8M4 13h16',
  question: 'M9.5 9a2.5 2.5 0 1 1 3.5 2.3c-.6.3-1 .9-1 1.6V14m0 3.5h.01M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18Z',
  alert: 'M12 9v4m0 3.5h.01M10.3 4.3 2.8 17.5A2 2 0 0 0 4.5 20.5h15a2 2 0 0 0 1.7-3L13.7 4.3a2 2 0 0 0-3.4 0Z',
} as const

export type IconName = keyof typeof PATHS

export function Icon({ name, size = 20 }: { name: IconName; size?: number }) {
  return (
    <svg
      className="icon"
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.8}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
    >
      <path d={PATHS[name]} />
    </svg>
  )
}

/** AutoLog 로고 마크: 계기판 바늘과 경로 점 (원본 도안) */
export function LogoMark({ size = 32 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 32 32" aria-hidden="true" focusable="false">
      <rect width="32" height="32" rx="9" fill="#00AAD2" />
      <path d="M7.5 21.5a9 9 0 1 1 17 0" fill="none" stroke="#fff" strokeWidth="2.4" strokeLinecap="round" />
      <path d="M16 18l5-6" stroke="#002C5F" strokeWidth="2.6" strokeLinecap="round" />
      <circle cx="16" cy="18.5" r="2.2" fill="#002C5F" />
    </svg>
  )
}
