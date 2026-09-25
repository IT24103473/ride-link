package lk.ridelink.driver.domain;

/**
 * The areas RideLink operates in. A driver works in exactly one, and matching never
 * crosses an area boundary.
 *
 * <p>An enum rather than free text so an area cannot be misspelled into a value that
 * silently matches nothing during dispatch.</p>
 */
public enum ServiceArea {
    COLOMBO,
    NEGOMBO,
    KANDY,
    GALLE,
    TRINCOMALEE
}
