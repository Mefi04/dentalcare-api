package com.dentalcare.api.modules.appointments.controller;

import com.dentalcare.api.modules.appointments.dto.request.PublicAppointmentDecisionRequest;
import com.dentalcare.api.modules.appointments.dto.request.PublicVerificationChannelRequest;
import com.dentalcare.api.modules.appointments.dto.request.VerifyPublicAppointmentCodeRequest;
import com.dentalcare.api.modules.appointments.dto.response.PublicAppointmentConversationResponse;
import com.dentalcare.api.modules.appointments.dto.response.PublicConversationTokenResponse;
import com.dentalcare.api.modules.appointments.dto.response.PublicVerificationAcknowledgement;
import com.dentalcare.api.modules.appointments.service.AppointmentRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/public/appointment-requests/{requestId}")
@Tag(name = "Public appointment conversation")
public class PublicAppointmentConversationController {
    private final AppointmentRequestService service;
    public PublicAppointmentConversationController(AppointmentRequestService service) { this.service = service; }

    @PostMapping("/verification-codes")
    @Operation(summary = "Request a generic OTP acknowledgment; delivery only occurs for a registered contact channel",
            description = "Returns 202 without disclosing whether the request/contact exists. 400 malformed channel, 429 IP rate limit, 503 delivery unavailable.")
    public ResponseEntity<PublicVerificationAcknowledgement> requestCode(@PathVariable UUID requestId,
            @Valid @RequestBody PublicVerificationChannelRequest request) {
        return ResponseEntity.accepted().body(service.requestConversationCode(requestId, request));
    }

    @PostMapping("/verification")
    @Operation(summary = "Verify one-time code and receive a short-lived scoped conversation token",
            description = "400 invalid format, 401 invalid code, 410 expired/consumed code, 429 after five attempts or IP throttling.")
    public ResponseEntity<PublicConversationTokenResponse> verify(@PathVariable UUID requestId,
            @Valid @RequestBody VerifyPublicAppointmentCodeRequest request) {
        return ResponseEntity.ok(service.verifyConversationCode(requestId, request));
    }

    @GetMapping("/conversation")
    @Operation(summary = "Read a public appointment conversation using its scoped bearer token",
            description = "401 missing/invalid token; 410 expired token. The token is limited to this request.")
    public ResponseEntity<PublicAppointmentConversationResponse> get(@PathVariable UUID requestId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ResponseEntity.ok(service.getPublicConversation(requestId, bearer(authorization)));
    }

    @PostMapping("/decision")
    @Operation(summary = "Accept or reject the current public appointment proposal",
            description = "Requires Idempotency-Key UUID. 400 invalid decision/key; 401 invalid token; 409 state conflict, PATIENT_RECORD_LINK_REQUIRED, or APPOINTMENT_TIME_UNAVAILABLE (no appointment is created); 410 expired/responded proposal or token; 429 rate limit.")
    public ResponseEntity<PublicAppointmentConversationResponse> decide(@PathVariable UUID requestId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "Idempotency-Key", required = false) UUID key,
            @Valid @RequestBody PublicAppointmentDecisionRequest request) {
        return ResponseEntity.ok(service.decidePublicProposal(requestId, bearer(authorization), request, key));
    }

    private String bearer(String header) {
        if (header == null || !header.startsWith("Bearer ")) return null;
        return header.substring(7).trim();
    }
}
