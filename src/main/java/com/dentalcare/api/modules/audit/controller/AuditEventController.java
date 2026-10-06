package com.dentalcare.api.modules.audit.controller;

import com.dentalcare.api.modules.audit.dto.AuditEventResponse;
import com.dentalcare.api.modules.audit.model.AuditResult;
import com.dentalcare.api.modules.audit.service.AuditQueryService;
import com.dentalcare.api.exception.BadRequestException;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit-events")
public class AuditEventController {
    @org.springframework.beans.factory.annotation.Autowired(required = false) private AuditQueryService service;
    @GetMapping @PreAuthorize("hasAuthority('AUDIT_READ')")
    public Page<AuditEventResponse> search(@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required=false) UUID actorUserId, @RequestParam(required=false) String module,
            @RequestParam(required=false) String action, @RequestParam(required=false) AuditResult result,
            @RequestParam(required=false) String q, @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size) {
        if (page < 0 || size < 1 || size > 100) throw new BadRequestException("Invalid pagination parameters");
        if (from != null && to != null && from.isAfter(to)) throw new BadRequestException("From date must not be after to date");
        return service.search(from,to,actorUserId,module,action,result,q,page,size);
    }
}
