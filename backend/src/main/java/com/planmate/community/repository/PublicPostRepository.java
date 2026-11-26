package com.planmate.community.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.planmate.community.entity.PublicPost;

/**
 * PUBLIC_POST (공개 게시물) 넣기·꺼내기.
 * 공개 목록(PUB-03): 공개(IS_PUBLIC = Y)이고 관리자 숨김이 아닌(ADMIN_HIDDEN = N) 게시물만 DB에서 고른다.
 * 원래 여행이 휴지통인지는 TRIP(B 표)이라 여기서 거르지 않고 서비스가 B에게 묻는다.
 *
 * 제목 검색(PUB-03-02): titlePattern은 서비스가 만든 "%검색어%" (소문자, %·_·\는 앞에 \를 붙여 글자 그대로).
 * 검색어가 없으면 "%"를 넘긴다(모두 일치).
 *
 * 이어 받기(PUB-03-01): 마지막 카드의 정렬 값보다 뒤에 있는 게시물부터.
 * 최신순 = 처음 게시 시각 늦은 순, 같으면 POST_ID 큰 순 (IX_POST_LIST)
 * 좋아요순 = 좋아요 많은 순 → 처음 게시 시각 늦은 순 → POST_ID 큰 순 (PUB-03-03)
 */
public interface PublicPostRepository extends JpaRepository<PublicPost, Long> {

    // PUB-03-01·03 최신순 첫 묶음
    @Query("""
            select p from PublicPost p
            where p.isPublic = true and p.adminHidden = false
              and lower(p.postTitle) like :titlePattern escape '\\'
            order by p.publishedAt desc, p.postId desc
            """)
    List<PublicPost> findLatest(@Param("titlePattern") String titlePattern, Limit limit);

    // PUB-03-01·03 최신순 이어 받기 (마지막 카드보다 뒤)
    @Query("""
            select p from PublicPost p
            where p.isPublic = true and p.adminHidden = false
              and lower(p.postTitle) like :titlePattern escape '\\'
              and (p.publishedAt < :publishedAt
                   or (p.publishedAt = :publishedAt and p.postId < :postId))
            order by p.publishedAt desc, p.postId desc
            """)
    List<PublicPost> findLatestAfter(@Param("titlePattern") String titlePattern,
            @Param("publishedAt") LocalDateTime publishedAt, @Param("postId") Long postId, Limit limit);

    // PUB-03-03 좋아요순 첫 묶음
    @Query("""
            select p from PublicPost p
            where p.isPublic = true and p.adminHidden = false
              and lower(p.postTitle) like :titlePattern escape '\\'
            order by p.likeCount desc, p.publishedAt desc, p.postId desc
            """)
    List<PublicPost> findMostLiked(@Param("titlePattern") String titlePattern, Limit limit);

    // PUB-03-03 좋아요순 이어 받기 (마지막 카드보다 뒤)
    @Query("""
            select p from PublicPost p
            where p.isPublic = true and p.adminHidden = false
              and lower(p.postTitle) like :titlePattern escape '\\'
              and (p.likeCount < :likeCount
                   or (p.likeCount = :likeCount and p.publishedAt < :publishedAt)
                   or (p.likeCount = :likeCount and p.publishedAt = :publishedAt and p.postId < :postId))
            order by p.likeCount desc, p.publishedAt desc, p.postId desc
            """)
    List<PublicPost> findMostLikedAfter(@Param("titlePattern") String titlePattern,
            @Param("likeCount") Integer likeCount, @Param("publishedAt") LocalDateTime publishedAt,
            @Param("postId") Long postId, Limit limit);

    // "검색 결과 n개": 조건에 맞는 게시물의 여행 ID (휴지통 여부는 서비스가 B에게 물어 뺀다)
    @Query("""
            select p.tripId from PublicPost p
            where p.isPublic = true and p.adminHidden = false
              and lower(p.postTitle) like :titlePattern escape '\\'
            """)
    List<Long> findOpenTripIds(@Param("titlePattern") String titlePattern);
}
