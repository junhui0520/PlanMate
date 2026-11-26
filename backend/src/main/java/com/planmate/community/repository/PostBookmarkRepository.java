package com.planmate.community.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.planmate.community.entity.PostBookmark;

/**
 * POST_BOOKMARK (북마크) 넣기·꺼내기 (PUB-06).
 * 내 북마크(PUB-06-02): 담은 시각 최신순, 같으면 BOOKMARK_ID 큰 순. 마지막 북마크 다음부터 이어 받는다.
 */
public interface PostBookmarkRepository extends JpaRepository<PostBookmark, Long> {

    // PUB-04-03 내가 북마크 했는지 (채워진 책갈피)
    boolean existsByPostIdAndMemberId(Long postId, Long memberId);

    Optional<PostBookmark> findByPostIdAndMemberId(Long postId, Long memberId);

    // PUB-06-01 회원당 최대 200개
    long countByMemberId(Long memberId);

    // PUB-06-02 내 북마크 첫 묶음
    @Query("""
            select b from PostBookmark b
            where b.memberId = :memberId
            order by b.createdAt desc, b.bookmarkId desc
            """)
    List<PostBookmark> findMine(@Param("memberId") Long memberId, Limit limit);

    // PUB-06-02 내 북마크 이어 받기 (마지막 북마크보다 뒤)
    @Query("""
            select b from PostBookmark b
            where b.memberId = :memberId
              and (b.createdAt < :createdAt
                   or (b.createdAt = :createdAt and b.bookmarkId < :bookmarkId))
            order by b.createdAt desc, b.bookmarkId desc
            """)
    List<PostBookmark> findMineAfter(@Param("memberId") Long memberId, @Param("createdAt") LocalDateTime createdAt,
            @Param("bookmarkId") Long bookmarkId, Limit limit);
}
