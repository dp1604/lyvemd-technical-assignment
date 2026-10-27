package com.aldercrest.atlas.results;

import com.aldercrest.atlas.security.ActorHeaders;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class ResultDetailController {
    private final ResultDetailService service;
    public ResultDetailController(ResultDetailService service) {this.service=service;}
    @GetMapping("/patients/{patientId}/results/{observationId}")
    public ResponseEntity<?> detail(@PathVariable String patientId,@PathVariable String observationId,HttpServletRequest request) {
        return service.detail(ActorHeaders.parse(request),patientId,observationId);
    }
}
