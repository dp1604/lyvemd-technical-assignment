package com.aldercrest.atlas.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public final class ActorHeaders {
    private static final Set<String> ROLES = Set.of("PATIENT","PROXY","CLINICIAN","STAFF");
    private ActorHeaders() { }

    public static TestActor parse(HttpServletRequest request) {
        String id = single(request,"X-Test-Actor-Id");
        String role = single(request,"X-Test-Actor-Role");
        if (id == null || !id.matches("[A-Za-z0-9._-]{1,64}") || !ROLES.contains(role == null ? "" : role)) {
            return TestActor.unverified();
        }
        return new TestActor(id,role,true,false);
    }

    public static String idempotencyKey(HttpServletRequest request) {
        String key = single(request,"X-Idempotency-Key");
        return key != null && key.matches("[A-Za-z0-9._-]{1,128}") ? key : null;
    }

    private static String single(HttpServletRequest request,String name) {
        List<String> values = Collections.list(request.getHeaders(name));
        return values.size() == 1 ? values.getFirst() : null;
    }
}
