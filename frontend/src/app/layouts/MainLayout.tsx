import type { ReactNode } from 'react';
import { Link, Outlet, useLocation, useMatches } from 'react-router';
import { Button } from '../../shared/components/Button';

/** 경로 묶음의 handle에 달아서 좌측 메뉴를 붙인다. 예: { path: 'mypage', handle: { sideMenu: 'mypage' } } */
export type MainLayoutHandle = { sideMenu?: 'mypage' | 'cs' };

export type SideMenuItem = { label: string; to: string; isActive: (pathname: string) => boolean };

// 마이페이지 좌측 메뉴 (Figma 다섯 번째 페이지)
const MYPAGE_MENU: SideMenuItem[] = [
  {
    label: '내 정보',
    to: '/mypage',
    isActive: (pathname) => ['/mypage', '/mypage/password', '/mypage/withdrawal'].includes(pathname),
  },
  { label: '보관함', to: '/mypage/saved-places', isActive: (pathname) => pathname === '/mypage/saved-places' },
  { label: '북마크', to: '/mypage/bookmarks', isActive: (pathname) => pathname === '/mypage/bookmarks' },
  { label: '내 게시글', to: '/mypage/posts', isActive: (pathname) => pathname === '/mypage/posts' },
];

// 고객센터 좌측 메뉴 (Figma 다섯 번째 페이지)
const CS_MENU: SideMenuItem[] = [
  { label: '자주 묻는 질문', to: '/cs/faq', isActive: (pathname) => pathname === '/cs/faq' },
  { label: '1:1 문의하기', to: '/cs/inquiries/new', isActive: (pathname) => pathname === '/cs/inquiries/new' },
  {
    label: '내 문의',
    to: '/cs/inquiries',
    // 목록·상세·수정에서 활성, 1:1 문의하기(/new)는 제외
    isActive: (pathname) =>
      (pathname === '/cs/inquiries' || pathname.startsWith('/cs/inquiries/')) && pathname !== '/cs/inquiries/new',
  },
];

// TODO(ACC-02-01·ACC-07-01): features/auth/hooks/useAuth로 교체 (pjh/login)
// 지금은 항상 null이라 비회원 GNB가 보이는 게 정상
function useGnbMember(): { nickname: string; isAdmin: boolean } | null {
  return null;
}

function isMainLayoutHandle(handle: unknown): handle is MainLayoutHandle {
  return (
    typeof handle === 'object' &&
    handle !== null &&
    'sideMenu' in handle &&
    (handle.sideMenu === 'mypage' || handle.sideMenu === 'cs')
  );
}

interface GnbLinkProps {
  to: string;
  activePrefix: string; // 현재 경로가 이 값으로 시작하면 활성
  children: ReactNode;
}

function GnbLink({ to, activePrefix, children }: GnbLinkProps) {
  const { pathname } = useLocation();
  const active = pathname === activePrefix || pathname.startsWith(`${activePrefix}/`);
  return (
    <Link
      to={to}
      className={active ? 'pm-gnb__link is-active' : 'pm-gnb__link'}
      aria-current={active ? 'page' : undefined}
    >
      {children}
    </Link>
  );
}

export interface SideMenuProps {
  title?: string;
  items: SideMenuItem[];
}

/** 좌측 메뉴 (마이페이지·고객센터·관리자 공통) */
export function SideMenu({ title, items }: SideMenuProps) {
  const { pathname } = useLocation();
  return (
    <nav className="pm-side-menu">
      {title && <h2 className="pm-side-menu__title">{title}</h2>}
      {items.map((item) => {
        const active = item.isActive(pathname);
        return (
          <Link
            key={item.to}
            to={item.to}
            className={active ? 'pm-side-menu__link is-active' : 'pm-side-menu__link'}
            aria-current={active ? 'page' : undefined}
          >
            {item.label}
          </Link>
        );
      })}
    </nav>
  );
}

/** 일반 화면 공통 틀: GNB + (좌측 메뉴) + 본문 */
export function MainLayout() {
  const member = useGnbMember();
  const matches = useMatches();
  const sideMenu = matches
    .map((match) => match.handle)
    .filter(isMainLayoutHandle)
    .at(-1)?.sideMenu;

  const handleLogout = () => {
    // TODO(ACC-02-02): 로그아웃 (pjh/login)
  };

  return (
    <div className="pm-layout">
      <header className="pm-gnb">
        <div className="pm-gnb__inner">
          <nav className="pm-gnb__nav">
            <Link to="/" className="pm-gnb__logo">
              LOGO
            </Link>
            {member && (
              <GnbLink to="/trips" activePrefix="/trips">
                내 여행
              </GnbLink>
            )}
            <GnbLink to="/community" activePrefix="/community">
              커뮤니티
            </GnbLink>
            <GnbLink to="/cs/faq" activePrefix="/cs">
              고객센터
            </GnbLink>
          </nav>
          <div className="pm-gnb__right">
            {member ? (
              <>
                {/* TODO(SUP-03-01): features/notification/components/NotificationBell */}
                {/* ACC-07-03 일반 회원에게 관리자 메뉴 숨김. 실제 판정은 pjh/security */}
                {member.isAdmin && (
                  <GnbLink to="/admin" activePrefix="/admin">
                    관리자
                  </GnbLink>
                )}
                <GnbLink to="/mypage" activePrefix="/mypage">
                  내 정보
                </GnbLink>
                <Button variant="text" onClick={handleLogout}>
                  로그아웃
                </Button>
              </>
            ) : (
              <>
                <Link to="/login" className="pm-btn pm-btn--secondary">
                  로그인
                </Link>
                <Link to="/signup" className="pm-btn pm-btn--primary">
                  회원가입
                </Link>
              </>
            )}
          </div>
        </div>
      </header>
      <div className="pm-layout__body">
        {sideMenu === 'mypage' && <SideMenu title="마이페이지" items={MYPAGE_MENU} />}
        {sideMenu === 'cs' && <SideMenu title="고객센터" items={CS_MENU} />}
        <main className="pm-layout__content">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
