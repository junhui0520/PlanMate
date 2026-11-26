/*
 * 화면 주소 목록.
 * 각 파트는 자기 page를 만들면 해당 경로의 element만 RoutePlaceholder → page 컴포넌트로 바꾼다.
 * 경로를 추가·변경할 때는 팀 공유 후.
 * 로그인·관리자 보호는 pjh/security·pjh/login에서 경로 묶음 단위로 감싼다.
 *
 * 등록하지 않은 경로 (팀 확인 대기)
 * - /notifications: A-16 알림함은 덮개 창이라 component로 전환 예정
 * - C-12 추천안 비교(새 페이지): C 확인 대기
 * - E-09 FAQ 등록·수정, E-11 문의 상세·답변: 상세 경로 미정
 * - 여행 탭 주소 방식: B·D 확인 대기
 */
import { createBrowserRouter, Navigate } from 'react-router';
import { RouterProvider } from 'react-router/dom';
import BudgetPage from '../features/budget/pages/BudgetPage';
import { EmptyState } from '../shared/components/EmptyState';
import { AdminLayout } from './layouts/AdminLayout';
import { MainLayout } from './layouts/MainLayout';
import type { MainLayoutHandle } from './layouts/MainLayout';

interface RoutePlaceholderProps {
  screen: string; // Figma 화면 번호
  title: string;
  pageFile: string; // features/ 아래 page 파일
  part: string;
}

// 개발용 임시 화면. 각 파트가 page를 만들면 교체한다
function RoutePlaceholder({ screen, title, pageFile, part }: RoutePlaceholderProps) {
  const heading = [screen, title].filter(Boolean).join(' ');
  return <EmptyState message={`${heading} · features/${pageFile} · ${part} 파트 구현 예정`} />;
}

const router = createBrowserRouter([
  {
    element: <MainLayout />,
    children: [
      { index: true, element: <RoutePlaceholder screen="MAIN-01·02" title="메인" pageFile="trip/pages/MainPage.tsx" part="B" /> },
      { path: 'login', element: <RoutePlaceholder screen="A-01" title="로그인" pageFile="auth/pages/LoginPage.tsx" part="A" /> },
      { path: 'signup', element: <RoutePlaceholder screen="A-02" title="회원가입" pageFile="auth/pages/SignupPage.tsx" part="A" /> },
      { path: 'signup/social', element: <RoutePlaceholder screen="A-03" title="소셜 최초 가입" pageFile="auth/pages/SocialSignupPage.tsx" part="A" /> },
      { path: 'password/find', element: <RoutePlaceholder screen="A-04" title="비밀번호 찾기" pageFile="auth/pages/PasswordFindPage.tsx" part="A" /> },
      { path: 'password/reset', element: <RoutePlaceholder screen="A-05" title="새 비밀번호 설정" pageFile="auth/pages/PasswordResetPage.tsx" part="A" /> },
      { path: 'invite/:token', element: <RoutePlaceholder screen="A-11·12" title="초대 참여" pageFile="participant/pages/InviteJoinPage.tsx" part="A" /> },
      { path: 'trips', element: <RoutePlaceholder screen="B-01" title="내 여행 목록" pageFile="trip/pages/TripListPage.tsx" part="B" /> },
      { path: 'trips/new', element: <RoutePlaceholder screen="B-02" title="여행 만들기" pageFile="trip/pages/TripCreatePage.tsx" part="B" /> },
      { path: 'trips/trash', element: <RoutePlaceholder screen="B-11" title="휴지통" pageFile="trip/pages/TrashPage.tsx" part="B" /> },
      { path: 'trips/:tripId', element: <RoutePlaceholder screen="B-03" title="여행 화면(일정·후보·체크리스트 탭)" pageFile="schedule/pages/TripDetailPage.tsx" part="B" /> },
      { path: 'trips/:tripId/budget', element: <BudgetPage /> },
      { path: 'trips/:tripId/share', element: <RoutePlaceholder screen="D-08" title="공개 미리보기" pageFile="community/pages/PostCreatePage.tsx" part="D" /> },
      { path: 'community', element: <RoutePlaceholder screen="D-01" title="커뮤니티" pageFile="community/pages/CommunityListPage.tsx" part="D" /> },
      { path: 'community/:postId', element: <RoutePlaceholder screen="D-02·03·14" title="공개 여행 상세" pageFile="community/pages/PostDetailPage.tsx" part="D" /> },
      {
        path: 'mypage',
        handle: { sideMenu: 'mypage' } satisfies MainLayoutHandle,
        children: [
          { index: true, element: <RoutePlaceholder screen="A-06·07" title="내 정보" pageFile="member/pages/MyPage.tsx" part="A" /> },
          { path: 'password', element: <RoutePlaceholder screen="A-08" title="비밀번호 변경" pageFile="auth/pages/PasswordChangePage.tsx" part="A" /> },
          { path: 'withdrawal', element: <RoutePlaceholder screen="A-09" title="회원 탈퇴" pageFile="member/pages/WithdrawalPage.tsx" part="A" /> },
          { path: 'saved-places', element: <RoutePlaceholder screen="E-01" title="내 보관함" pageFile="savedplace/pages/SavedPlacePage.tsx" part="A(원래 E)" /> },
          { path: 'bookmarks', element: <RoutePlaceholder screen="D-09" title="내 북마크" pageFile="community/pages/MyBookmarksPage.tsx" part="D" /> },
          { path: 'posts', element: <RoutePlaceholder screen="D-10" title="내 게시글" pageFile="community/pages/MyPostsPage.tsx" part="D" /> },
        ],
      },
      {
        path: 'cs',
        handle: { sideMenu: 'cs' } satisfies MainLayoutHandle,
        children: [
          { index: true, element: <Navigate to="/cs/faq" replace /> },
          { path: 'faq', element: <RoutePlaceholder screen="E-04" title="자주 묻는 질문" pageFile="cs/pages/FaqPage.tsx" part="A(원래 E)" /> },
          { path: 'inquiries/new', element: <RoutePlaceholder screen="E-05" title="1:1 문의하기" pageFile="cs/pages/InquiryWritePage.tsx" part="A(원래 E)" /> },
          { path: 'inquiries', element: <RoutePlaceholder screen="E-06" title="내 문의" pageFile="cs/pages/InquiryListPage.tsx" part="A(원래 E)" /> },
          { path: 'inquiries/:inquiryId', element: <RoutePlaceholder screen="E-07" title="내 문의 상세" pageFile="cs/pages/InquiryDetailPage.tsx" part="A(원래 E)" /> },
          { path: 'inquiries/:inquiryId/edit', element: <RoutePlaceholder screen="E-05" title="문의 수정" pageFile="cs/pages/InquiryWritePage.tsx" part="A(원래 E)" /> },
        ],
      },
    ],
  },
  {
    path: 'admin',
    element: <AdminLayout />,
    children: [
      { index: true, element: <Navigate to="/admin/members" replace /> },
      { path: 'members', element: <RoutePlaceholder screen="A-18" title="회원 목록" pageFile="admin/pages/AdminMemberPage.tsx" part="A" /> },
      { path: 'members/:memberId', element: <RoutePlaceholder screen="A-19" title="회원 상세" pageFile="admin/pages/AdminMemberDetailPage.tsx" part="A" /> },
      { path: 'reports', element: <RoutePlaceholder screen="A-22" title="신고 목록" pageFile="admin/pages/AdminReportPage.tsx" part="A" /> },
      { path: 'reports/:reportId', element: <RoutePlaceholder screen="A-23·24" title="신고 상세·처리" pageFile="admin/pages/AdminReportDetailPage.tsx" part="A" /> },
      { path: 'faqs', element: <RoutePlaceholder screen="E-08" title="FAQ 관리" pageFile="cs/pages/AdminFaqPage.tsx" part="A(원래 E)" /> },
      { path: 'inquiries', element: <RoutePlaceholder screen="E-10" title="1:1 문의 관리" pageFile="cs/pages/AdminInquiryPage.tsx" part="A(원래 E)" /> },
    ],
  },
  // 레이아웃 없음
  { path: 'oauth/callback/:provider', element: <RoutePlaceholder screen="" title="소셜 로그인 복귀" pageFile="auth/pages/OAuthCallbackPage.tsx" part="A" /> },
  { path: '*', element: <Navigate to="/" replace /> },
]);

/** 앱 라우터. App.tsx에서 그린다 */
export function AppRouter() {
  return <RouterProvider router={router} />;
}
