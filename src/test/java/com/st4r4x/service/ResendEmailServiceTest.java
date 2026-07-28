package com.st4r4x.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockMakers;
import org.mockito.junit.jupiter.MockitoExtension;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.Emails;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;

@ExtendWith(MockitoExtension.class)
class ResendEmailServiceTest {

    @Mock
    private Resend resend;

    // Emails is a final class in the Resend SDK — the project-wide mock maker
    // (mock-maker-subclass, see src/test/resources/mockito-extensions) can't
    // proxy final classes, so override to inline for this mock only.
    @Mock(mockMaker = MockMakers.INLINE)
    private Emails emails;

    @Test
    void sendPasswordResetEmail_callsResendWithCorrectFields() throws ResendException {
        when(resend.emails()).thenReturn(emails);
        when(emails.send(any(CreateEmailOptions.class))).thenReturn(mock(CreateEmailResponse.class));

        ResendEmailService service = new ResendEmailService(resend);
        service.sendPasswordResetEmail("alice@example.com", "https://example.com/reset-password?token=abc123");

        var captor = org.mockito.ArgumentCaptor.forClass(CreateEmailOptions.class);
        verify(emails).send(captor.capture());
        CreateEmailOptions sent = captor.getValue();
        assertEquals("onboarding@resend.dev", sent.getFrom());
        assertEquals(java.util.List.of("alice@example.com"), sent.getTo());
        assertTrue(sent.getHtml().contains("https://example.com/reset-password?token=abc123"));
    }

    @Test
    void sendPasswordResetEmail_wrapsResendExceptionAsRuntimeException() throws ResendException {
        when(resend.emails()).thenReturn(emails);
        when(emails.send(any(CreateEmailOptions.class))).thenThrow(new ResendException("API error", null));

        ResendEmailService service = new ResendEmailService(resend);

        assertThrows(RuntimeException.class, () ->
            service.sendPasswordResetEmail("alice@example.com", "https://example.com/reset-password?token=abc123"));
    }
}
