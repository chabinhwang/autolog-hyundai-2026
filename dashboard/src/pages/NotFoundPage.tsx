import { Icon } from '../components/Icon'
import { Link } from '../router/Link'

export function NotFoundPage() {
  return (
    <section className="not-found" aria-labelledby="not-found-title">
      <span className="empty__icon">
        <Icon name="question" size={22} />
      </span>
      <h1 id="not-found-title">페이지를 찾을 수 없습니다</h1>
      <p className="muted">주소가 바뀌었거나 삭제된 화면입니다.</p>
      <Link to="/" className="button button--primary">
        <Icon name="home" size={18} />
        홈으로 이동
      </Link>
    </section>
  )
}
