import { Link } from '../router/Link'

export function NotFoundPage() {
  return (
    <section>
      <h1>페이지를 찾을 수 없습니다</h1>
      <p>
        <Link to="/">홈으로 이동</Link>
      </p>
    </section>
  )
}
