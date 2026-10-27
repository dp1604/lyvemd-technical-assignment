package com.aldercrest.atlas.security;

/** A test declaration, not an authenticated identity. */
public record TestActor(String id, String role, boolean valid, boolean rosterVerified) {
    public static TestActor unverified() { return new TestActor(null,null,false,false); }
    public TestActor withVerifiedRoster() {
        if (!valid) { throw new IllegalStateException("Invalid declaration cannot match a roster."); }
        return new TestActor(id,role,true,true);
    }
}
