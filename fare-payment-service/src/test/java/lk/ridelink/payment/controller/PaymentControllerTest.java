package lk.ridelink.payment.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lk.ridelink.payment.config.CorrelationIdFilter;
import lk.ridelink.payment.config.RideLinkProperties;
import lk.ridelink.payment.config.SecurityConfig;
import lk.ridelink.payment.domain.FareType;
import lk.ridelink.payment.domain.PaymentMethod;
import lk.ridelink.payment.domain.PaymentStatus;
import lk.ridelink.payment.dto.PayRequest;
import lk.ridelink.payment.dto.PaymentResponse;
import lk.ridelink.payment.exception.GlobalExceptionHandler;
import lk.ridelink.payment.exception.NotFoundException;
import lk.ridelink.payment.exception.PaymentAlreadyCompletedException;
import lk.ridelink.payment.exception.PaymentDeclinedException;
import lk.ridelink.payment.security.Role;
import lk.ridelink.payment.service.PaymentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Status-code mapping for the payment endpoints, especially the 402 and 409 paths. */
@WebMvcTest(PaymentController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, CorrelationIdFilter.class})
@EnableConfigurationProperties(RideLinkProperties.class)
@ActiveProfiles("test")
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PaymentService paymentService;

    @Test
    @DisplayName("paying without a token gives 401")
    void pay_noToken_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/payments/{id}/pay", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PayRequest(PaymentMethod.CASH, null))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verify(paymentService, never()).pay(any(), any());
    }

    @Test
    @DisplayName("a successful payment returns 200 with the receipt number")
    void pay_success_returns200WithReceipt() throws Exception {
        UUID paymentId = UUID.randomUUID();
        when(paymentService.pay(eq(paymentId), any())).thenReturn(paid(paymentId));

        mockMvc.perform(post("/api/v1/payments/{id}/pay", paymentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PayRequest(PaymentMethod.CARD, "tok_success")))
                        .with(caller(Role.PASSENGER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.receiptNumber").value("RL-2026-000123"));
    }

    @Test
    @DisplayName("a declined card gives 402 PAYMENT_DECLINED, not 400 or 500")
    void pay_declined_returns402() throws Exception {
        UUID paymentId = UUID.randomUUID();
        when(paymentService.pay(eq(paymentId), any()))
                .thenThrow(new PaymentDeclinedException("CARD_DECLINED"));

        mockMvc.perform(post("/api/v1/payments/{id}/pay", paymentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PayRequest(PaymentMethod.CARD, "tok_declined")))
                        .with(caller(Role.PASSENGER)))
                // 402 specifically: the request was fine and was processed, the card was not.
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.code").value("PAYMENT_DECLINED"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("retry")));
    }

    @Test
    @DisplayName("paying twice gives 409 PAYMENT_ALREADY_COMPLETED")
    void pay_alreadyPaid_returns409() throws Exception {
        UUID paymentId = UUID.randomUUID();
        when(paymentService.pay(eq(paymentId), any()))
                .thenThrow(new PaymentAlreadyCompletedException(paymentId));

        mockMvc.perform(post("/api/v1/payments/{id}/pay", paymentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PayRequest(PaymentMethod.CASH, null)))
                        .with(caller(Role.PASSENGER)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_ALREADY_COMPLETED"));
    }

    @Test
    @DisplayName("paying with no method is a 400 listing the field")
    void pay_missingMethod_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/payments/{id}/pay", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .with(caller(Role.PASSENGER)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.method").exists());
    }

    @Test
    @DisplayName("a ride whose fare is not computed yet gives 404 PAYMENT_NOT_READY")
    void getForRide_notReady_returns404WithSpecificCode() throws Exception {
        UUID rideId = UUID.randomUUID();
        when(paymentService.getForRide(rideId)).thenThrow(NotFoundException.paymentNotReady(rideId));

        mockMvc.perform(get("/api/v1/payments/rides/{rideId}", rideId).with(caller(Role.PASSENGER)))
                .andExpect(status().isNotFound())
                // The distinct code is what tells a polling client to keep trying.
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_READY"));
    }

    @Test
    @DisplayName("a receipt for an unpaid payment gives 404")
    void getReceipt_unpaid_returns404() throws Exception {
        UUID paymentId = UUID.randomUUID();
        when(paymentService.getReceipt(paymentId)).thenThrow(NotFoundException.receipt(paymentId));

        mockMvc.perform(get("/api/v1/payments/{id}/receipt", paymentId).with(caller(Role.PASSENGER)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    private static RequestPostProcessor caller(Role role) {
        return jwt()
                .jwt(builder -> builder.subject(UUID.randomUUID().toString()).claim("role", role.name()))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    private static PaymentResponse paid(UUID paymentId) {
        return new PaymentResponse(paymentId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                FareType.TRIP, new BigDecimal("4485.00"), "LKR", PaymentMethod.CARD,
                PaymentStatus.SUCCEEDED, "RL-2026-000123", Instant.now(), null, Instant.now());
    }
}
