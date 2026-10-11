package com.homestay3.homestaybackend.service.impl;

import com.homestay3.homestaybackend.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import jakarta.mail.internet.MimeMessage;

@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailServiceImpl.class);
    
    private final JavaMailSender mailSender;
    
    @Value("${spring.mail.username:noreply@homestay.com}")
    private String fromEmail;
    
    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Override
    public void sendPasswordResetEmail(String toEmail, String resetLink) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("密码重置请求");
            
            String emailContent = String.format("""
                <html>
                <body>
                    <h2>密码重置请求</h2>
                    <p>您好，</p>
                    <p>我们收到了您的密码重置请求。请点击下面的链接重置您的密码：</p>
                    <p><a href="%s">重置密码</a></p>
                    <p>如果您没有请求重置密码，请忽略此邮件。</p>
                    <p>此链接将在24小时后失效。</p>
                    <p>谢谢！</p>
                </body>
                </html>
                """, resetLink);
            
            helper.setText(emailContent, true);
            mailSender.send(message);
            log.info("密码重置邮件已发送至: {}", toEmail);
        } catch (Exception e) {
            log.warn("发送密码重置邮件失败");
            throw new RuntimeException("发送密码重置邮件失败，请稍后重试");
        }
    }

    @Override
    public void sendVerificationEmail(String email, String verificationToken) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setFrom(fromEmail);
            helper.setTo(email);
            helper.setSubject("邮箱验证");
            
            String verificationLink = frontendUrl + "/verify-email?token=" + verificationToken;
            
            String emailContent = String.format("""
                <html>
                <body>
                    <h2>邮箱验证</h2>
                    <p>您好，</p>
                    <p>感谢您注册我们的服务。请点击下面的链接验证您的邮箱：</p>
                    <p><a href="%s">验证邮箱</a></p>
                    <p>如果您没有注册我们的服务，请忽略此邮件。</p>
                    <p>谢谢！</p>
                </body>
                </html>
                """, verificationLink);
            
            helper.setText(emailContent, true);
            mailSender.send(message);
            log.info("验证邮件已发送至: {}", email);
        } catch (Exception e) {
            log.error("发送验证邮件失败: {}", e.getMessage());
            throw new RuntimeException("发送验证邮件失败: " + e.getMessage());
        }
    }
} 