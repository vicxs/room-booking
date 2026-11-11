package com.roombooking.health.domain;

/** Tells whether the database answers. Implemented outside the domain. */
public interface DatabaseProbe {

    boolean isReachable();
}
