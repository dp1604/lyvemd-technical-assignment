package com.aldercrest.atlas.results;

import com.aldercrest.atlas.security.ActorHeaders;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class AccessLogController {
    private final AccessLogService service;
    public AccessLogController(AccessLogService service) {this.service=service;}
    @GetMapping("/patients/{patientId}/access-log")
    public ResponseEntity<?> page(@PathVariable String patientId,HttpServletRequest request) {
        return service.page(ActorHeaders.parse(request),patientId,request.getParameterMap());
    }
}
