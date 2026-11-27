package com.planmate.community.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.planmate.community.entity.Report;

/**
 * REPORT (신고) 넣기·꺼내기 (PUB-09).
 * TODO(A와 맞추기): 관리자 신고 목록(ADM-02-01, 접수 오래된 순 20개·필터)·상태 바꾸기 쿼리는 A와 이름을 맞춘 뒤 더한다.
 */
public interface ReportRepository extends JpaRepository<Report, Long> {

    // PUB-09-01 같은 회원이 같은 게시물을 처리 전에(접수 상태) 이미 신고했는지
    boolean existsByReporterIdAndPostIdAndStatus(Long reporterId, Long postId, Report.Status status);

    // PUB-09-01 같은 회원이 같은 댓글을 처리 전에(접수 상태) 이미 신고했는지
    boolean existsByReporterIdAndCommentIdAndStatus(Long reporterId, Long commentId, Report.Status status);
}
