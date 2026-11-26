import { Link, Outlet, useLocation } from 'react-router';
import { Button } from '../../shared/components/Button';
import { SideMenu } from './MainLayout';
import type { SideMenuItem } from './MainLayout';

// 관리자 좌측 메뉴 (Figma A-18, 제목 없음)
const ADMIN_MENU: SideMenuItem[] = [
  { label: '회원 관리', to: '/admin/members', isActive: (pathname) => pathname.startsWith('/admin/members') },
  { label: '신고 관리', to: '/admin/reports', isActive: (pathname) => pathname.startsWith('/admin/reports') },
  { label: 'FAQ 관리', to: '/admin/faqs', isActive: (pathname) => pathname.startsWith('/admin/faqs') },
  { label: '1:1 문의 관리', to: '/admin/inquiries', isActive: (pathname) => pathname.startsWith('/admin/inquiries') },
];

// TODO(ACC-07-03): pjh/security에서 로그인한 관리자 닉네임으로 교체
function useAdminName(): string | null {
  return null;
}

/**
 * 관리자 화면 공통 틀: 관리자 GNB + 관리자 메뉴 + 본문.
 * TODO(ACC-07-03): 관리자 보호(AdminAccessChecker 연동)는 pjh/security에서 경로 묶음 단위로 감싼다.
 */
export function AdminLayout() {
  const adminName = useAdminName();
  const { pathname } = useLocation();
  const adminActive = pathname.startsWith('/admin');

  const handleLogout = () => {
    // TODO(ACC-02-02): 로그아웃 (pjh/login)
  };

  return (
    <div className="pm-layout">
      <header className="pm-gnb">
        <div className="pm-gnb__inner">
          <nav className="pm-gnb__nav">
            {/* Figma: LOGO 누름 → 메인 · 모든 화면 공통 */}
            <Link to="/" className="pm-gnb__logo">
              LOGO
            </Link>
            <Link
              to="/admin/members"
              className={adminActive ? 'pm-gnb__link is-active' : 'pm-gnb__link'}
              aria-current={adminActive ? 'page' : undefined}
            >
              관리자
            </Link>
          </nav>
          <div className="pm-gnb__right">
            <span>{adminName ? `관리자 · ${adminName}` : '관리자'}</span>
            <Button variant="text" onClick={handleLogout}>
              로그아웃
            </Button>
          </div>
        </div>
      </header>
      <div className="pm-layout__body">
        <SideMenu items={ADMIN_MENU} />
        <main className="pm-layout__content">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
