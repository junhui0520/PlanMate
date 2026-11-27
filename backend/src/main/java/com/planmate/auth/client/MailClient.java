package com.planmate.auth.client;

import com.planmate.common.exception.BusinessException;
import com.planmate.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * 인증번호 메일 보내기 (ACC-01-02·ACC-04-02).
 * 메일 설정(spring.mail.host)이 없으면 연결을 시도하지 않고 발송 실패(VERIFICATION_SEND_FAILED)로 처리한다.
 * 로그에는 받는 주소·번호를 남기지 않고 예외 종류만 남긴다.
 */
@Slf4j
@Component
public class MailClient {

    private static final String SUBJECT = "[PlanMate] 이메일 인증번호";

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String host;
    private final String from;

    public MailClient(ObjectProvider<JavaMailSender> mailSenderProvider,
                      @Value("${spring.mail.host:}") String host,
                      @Value("${spring.mail.username:}") String from) {
        this.mailSenderProvider = mailSenderProvider;
        this.host = host;
        this.from = from;
    }

    /** 인증번호 텍스트 메일 발송. 실패하면 VERIFICATION_SEND_FAILED (발송 실패를 인증 성공으로 처리하지 않음, ACC-01-02 J) */
    public void sendVerificationCode(String email, String code) {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null || host == null || host.isBlank()) {
            log.warn("메일 설정이 없어 인증번호를 보내지 못함");
            throw new BusinessException(ErrorCode.VERIFICATION_SEND_FAILED);
        }

        SimpleMailMessage message = new SimpleMailMessage();
        if (from != null && !from.isBlank()) {
            message.setFrom(from);
        }
        message.setTo(email);
        message.setSubject(SUBJECT);
        message.setText("PlanMate 인증번호: " + code + "\n5분 안에 입력해 주세요.");
        try {
            mailSender.send(message);
        } catch (MailException e) {
            log.warn("인증번호 메일 발송 실패: {}", e.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.VERIFICATION_SEND_FAILED);
        }
    }
}
