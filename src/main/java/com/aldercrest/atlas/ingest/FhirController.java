package com.aldercrest.atlas.ingest;

import com.aldercrest.atlas.security.ActorHeaders;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FhirController {
    public static final int MAX_BODY_BYTES=2*1024*1024;
    private final IngestionService service;
    public FhirController(IngestionService service) {this.service=service;}
    @PostMapping("/fhir")
    public ResponseEntity<String> post(HttpServletRequest request) {
        byte[] body=new byte[0];int transportStatus=0;
        try {
            List<String> contentTypes=Collections.list(request.getHeaders("Content-Type"));
            if(contentTypes.size()!=1) {transportStatus=415;}
            else {
                MediaType type=MediaType.parseMediaType(contentTypes.getFirst());
                if(!"application".equals(type.getType()) || !List.of("fhir+json","json").contains(type.getSubtype())
                        || (type.getCharset()!=null && !java.nio.charset.StandardCharsets.UTF_8.equals(type.getCharset()))) {transportStatus=415;}
            }
            List<String> encodings=Collections.list(request.getHeaders("Content-Encoding"));
            if(!encodings.isEmpty() && !(encodings.size()==1 && "identity".equalsIgnoreCase(encodings.getFirst()))) {transportStatus=415;}
            if(request.getContentLengthLong()>MAX_BODY_BYTES) {transportStatus=413;}
            if(transportStatus==0) {
                body=request.getInputStream().readNBytes(MAX_BODY_BYTES+1);
                if(body.length>MAX_BODY_BYTES) {body=new byte[0];transportStatus=413;}
            }
        } catch(IOException error) {transportStatus=400;}
        catch(IllegalArgumentException error) {transportStatus=415;}
        IngestReply reply=service.ingest(ActorHeaders.parse(request),ActorHeaders.idempotencyKey(request),body,transportStatus);
        return ResponseEntity.status(reply.status()).contentType(MediaType.parseMediaType("application/fhir+json")).body(reply.body());
    }
}
