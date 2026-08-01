package dev.rafex.insightbloom.users.adapters.outbound.zoho;

import dev.rafex.insightbloom.users.domain.ports.EmailPort;

import javax.activation.DataHandler;
import javax.mail.Part;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;
import java.util.List;
import java.util.Properties;

public class ZohoEmailClient implements EmailPort {
    private final String host;
    private final String port;
    private final String username;
    private final String password;
    private final String fromAddress;

    public ZohoEmailClient(final String host, final String port, final String username, final String password,
                            final String fromAddress) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.fromAddress = fromAddress;
    }

    @Override
    public boolean isEnabled() {
        return host != null && !host.isBlank()
                && username != null && !username.isBlank()
                && password != null && !password.isBlank();
    }

    @Override
    public void send(final String toEmail, final String subject, final String body) {
        sendInternal(toEmail, subject, mimeMessage -> mimeMessage.setText(body));
    }

    @Override
    public void sendHtml(final String toEmail, final String subject, final String htmlBody) {
        sendInternal(toEmail, subject, mimeMessage -> mimeMessage.setContent(htmlBody, "text/html; charset=UTF-8"));
    }

    @Override
    public void sendHtml(final String toEmail, final String subject, final String htmlBody,
                         final List<EmailAttachment> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            sendHtml(toEmail, subject, htmlBody);
            return;
        }
        sendInternal(toEmail, subject, mimeMessage -> {
            final MimeMultipart multipart = new MimeMultipart("mixed");
            final MimeBodyPart htmlPart = new MimeBodyPart();
            htmlPart.setContent(htmlBody, "text/html; charset=UTF-8");
            multipart.addBodyPart(htmlPart);

            for (final EmailAttachment attachment : attachments) {
                final MimeBodyPart attachmentPart = new MimeBodyPart();
                attachmentPart.setDataHandler(new DataHandler(
                        new ByteArrayDataSource(attachment.content(), attachment.contentType())));
                attachmentPart.setFileName(attachment.fileName());
                if (attachment.contentId() != null && !attachment.contentId().isBlank()) {
                    attachmentPart.setHeader("Content-ID", "<" + attachment.contentId() + ">");
                    attachmentPart.setDisposition(Part.INLINE);
                } else {
                    attachmentPart.setDisposition(Part.ATTACHMENT);
                }
                multipart.addBodyPart(attachmentPart);
            }
            mimeMessage.setContent(multipart);
        });
    }

    private interface BodySetter {
        void apply(Message mimeMessage) throws MessagingException;
    }

    private void sendInternal(final String toEmail, final String subject, final BodySetter bodySetter) {
        if (!isEnabled()) {
            throw new IllegalStateException("email_provider_not_configured");
        }
        final Properties props = new Properties();
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", port);
        props.put("mail.smtp.auth", "true");
        if ("465".equals(port)) {
            // Puerto 465: TLS implícito desde la conexión (sin STARTTLS)
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.socketFactory.port", port);
            props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
        } else {
            // Puerto 587 (u otro): STARTTLS sobre conexión en texto plano
            props.put("mail.smtp.starttls.enable", "true");
        }

        final Session session = Session.getInstance(props, new javax.mail.Authenticator() {
            @Override
            protected javax.mail.PasswordAuthentication getPasswordAuthentication() {
                return new javax.mail.PasswordAuthentication(username, password);
            }
        });

        try {
            final Message mimeMessage = new MimeMessage(session);
            mimeMessage.setFrom(new InternetAddress(fromAddress != null ? fromAddress : username));
            mimeMessage.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail));
            mimeMessage.setSubject(subject);
            bodySetter.apply(mimeMessage);
            Transport.send(mimeMessage);
        } catch (final MessagingException e) {
            throw new RuntimeException("zoho_send_failed", e);
        }
    }
}
