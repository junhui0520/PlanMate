package com.planmate.common.security;

import com.planmate.common.exception.ErrorCode;
import com.planmate.common.security.TripStatusReader.TripStatus;
import com.planmate.participant.entity.TripMember;
import com.planmate.participant.repository.TripMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static com.planmate.common.security.AccountStatusCheckerTest.assertDenied;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

/** ACC-07-02 여행·개인 자료 접근 검사 */
@ExtendWith(MockitoExtension.class)
class TripAccessCheckerTest {

    private static final Long TRIP_ID = 10L;
    private static final Long MEMBER_ID = 1L;

    @Mock
    private ObjectProvider<TripStatusReader> tripStatusReaderProvider;

    @Mock
    private TripStatusReader tripStatusReader;

    @Mock
    private TripMemberRepository tripMemberRepository;

    private TripAccessChecker checker;

    @BeforeEach
    void setUp() {
        checker = new TripAccessChecker(tripStatusReaderProvider, tripMemberRepository);
    }

    @Test
    void 여행이_없으면_RESOURCE_NOT_ACCESSIBLE() {
        status(null);
        assertDenied(() -> checker.requireParticipant(TRIP_ID, MEMBER_ID), ErrorCode.RESOURCE_NOT_ACCESSIBLE);
    }

    @Test
    void 휴지통_여행이면_참여자도_RESOURCE_NOT_ACCESSIBLE() {
        status(TripStatus.TRASHED);
        assertDenied(() -> checker.requireParticipant(TRIP_ID, MEMBER_ID), ErrorCode.RESOURCE_NOT_ACCESSIBLE);
    }

    @Test
    void 참여자가_아니면_RESOURCE_NOT_ACCESSIBLE() {
        status(TripStatus.ACTIVE);
        given(tripMemberRepository.findByTripIdAndMemberId(TRIP_ID, MEMBER_ID)).willReturn(Optional.empty());
        assertDenied(() -> checker.requireParticipant(TRIP_ID, MEMBER_ID), ErrorCode.RESOURCE_NOT_ACCESSIBLE);
    }

    @Test
    void 참여자면_역할을_돌려준다() {
        active(TripMember.Role.VIEWER);
        assertThat(checker.requireParticipant(TRIP_ID, MEMBER_ID)).isEqualTo(TripMember.Role.VIEWER);
    }

    @Test
    void VIEWER가_편집하면_EDIT_PERMISSION_DENIED() {
        active(TripMember.Role.VIEWER);
        assertDenied(() -> checker.requireEditor(TRIP_ID, MEMBER_ID), ErrorCode.EDIT_PERMISSION_DENIED);
    }

    @Test
    void VIEWER가_편집하면_기능별_문구로_거부할_수_있다() {
        active(TripMember.Role.VIEWER);
        assertDenied(() -> checker.requireEditor(TRIP_ID, MEMBER_ID, ErrorCode.EDIT_PERMISSION_CHANGED),
                ErrorCode.EDIT_PERMISSION_CHANGED);
    }

    @Test
    void EDITOR와_OWNER는_편집_가능() {
        active(TripMember.Role.EDITOR);
        assertThatCode(() -> checker.requireEditor(TRIP_ID, MEMBER_ID)).doesNotThrowAnyException();

        active(TripMember.Role.OWNER);
        assertThatCode(() -> checker.requireEditor(TRIP_ID, MEMBER_ID)).doesNotThrowAnyException();
    }

    @Test
    void OWNER만_소유자_동작_가능_EDITOR는_기능별_문구로_거부() {
        active(TripMember.Role.OWNER);
        assertThatCode(() -> checker.requireOwner(TRIP_ID, MEMBER_ID, ErrorCode.EDIT_PERMISSION_DENIED))
                .doesNotThrowAnyException();

        active(TripMember.Role.EDITOR);
        assertDenied(() -> checker.requireOwner(TRIP_ID, MEMBER_ID, ErrorCode.EDIT_PERMISSION_DENIED),
                ErrorCode.EDIT_PERMISSION_DENIED);
    }

    @Test
    void 휴지통_여행의_소유자는_통과() {
        status(TripStatus.TRASHED);
        role(TripMember.Role.OWNER);
        assertThatCode(() -> checker.requireOwnerOfTrashed(TRIP_ID, MEMBER_ID)).doesNotThrowAnyException();
    }

    @Test
    void 휴지통_소유자_검사는_활성_여행이거나_소유자가_아니면_거부() {
        status(TripStatus.ACTIVE);
        assertDenied(() -> checker.requireOwnerOfTrashed(TRIP_ID, MEMBER_ID), ErrorCode.RESOURCE_NOT_ACCESSIBLE);

        status(TripStatus.TRASHED);
        role(TripMember.Role.EDITOR);
        assertDenied(() -> checker.requireOwnerOfTrashed(TRIP_ID, MEMBER_ID), ErrorCode.RESOURCE_NOT_ACCESSIBLE);
    }

    @Test
    void TripStatusReader_구현이_없으면_IllegalStateException() {
        given(tripStatusReaderProvider.getIfAvailable()).willReturn(null);
        assertThatThrownBy(() -> checker.requireParticipant(TRIP_ID, MEMBER_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TripStatusReader");
    }

    @Test
    void 본인_자료는_본인만() {
        assertThatCode(() -> checker.requireSelf(MEMBER_ID, MEMBER_ID)).doesNotThrowAnyException();
        assertDenied(() -> checker.requireSelf(MEMBER_ID, 2L), ErrorCode.RESOURCE_NOT_ACCESSIBLE);
        assertDenied(() -> checker.requireSelf(null, null), ErrorCode.RESOURCE_NOT_ACCESSIBLE);
    }

    // ===== 도우미 =====

    private void status(TripStatus status) {
        given(tripStatusReaderProvider.getIfAvailable()).willReturn(tripStatusReader);
        given(tripStatusReader.findStatus(TRIP_ID)).willReturn(Optional.ofNullable(status));
    }

    private void role(TripMember.Role role) {
        TripMember tripMember = BeanUtils.instantiateClass(TripMember.class);
        ReflectionTestUtils.setField(tripMember, "tripId", TRIP_ID);
        ReflectionTestUtils.setField(tripMember, "memberId", MEMBER_ID);
        ReflectionTestUtils.setField(tripMember, "role", role);
        given(tripMemberRepository.findByTripIdAndMemberId(TRIP_ID, MEMBER_ID)).willReturn(Optional.of(tripMember));
    }

    private void active(TripMember.Role role) {
        status(TripStatus.ACTIVE);
        role(role);
    }
}
