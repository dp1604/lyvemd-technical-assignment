package com.aldercrest.atlas.results;

import com.aldercrest.atlas.security.ActorHeaders;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class ResultReadController {
    private final ResultReadService service;
    public ResultReadController(ResultReadService service) {this.service=service;}
    @GetMapping("/patients/{patientId}/results")
    ResponseEntity<?> page(@PathVariable String patientId,HttpServletRequest request) {
        return service.page(ActorHeaders.parse(request),patientId,request.getParameterMap());
    }
    @GetMapping("/patients/{patientId}/trend")
    ResponseEntity<?> trend(@PathVariable String patientId,HttpServletRequest request) {
        return service.trend(ActorHeaders.parse(request),patientId,request.getParameterMap());
    }
}
