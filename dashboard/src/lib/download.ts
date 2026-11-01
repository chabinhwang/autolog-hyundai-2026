/** Blob을 파일로 저장하게 합니다 (임시 a[download] 링크 사용) */
export function saveBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  anchor.rel = 'noopener'
  anchor.style.display = 'none'
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  // 일부 브라우저는 클릭 직후 해제하면 다운로드가 취소되므로 조금 늦춥니다.
  window.setTimeout(() => URL.revokeObjectURL(url), 1000)
}
