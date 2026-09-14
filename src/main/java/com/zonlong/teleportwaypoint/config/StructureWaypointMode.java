package com.zonlong.teleportwaypoint.config;

/**
 * Selects which of the two structure tag lists decides whether a structure receives a waypoint.
 *
 * <p>The lists themselves are data pack tags, not configuration: {@code teleportwaypoint:waypoint_whitelist}
 * and {@code teleportwaypoint:waypoint_blacklist}. Only the choice between them is configurable.
 */
public enum StructureWaypointMode {
    /** Only structures listed by {@code teleportwaypoint:waypoint_whitelist} receive a waypoint. */
    WHITELIST,
    /** Every structure receives a waypoint except those listed by {@code teleportwaypoint:waypoint_blacklist}. */
    BLACKLIST
}
