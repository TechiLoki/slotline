package dev.slotline;

public final class Problem extends RuntimeException {
    public final int status;
    public final String code;
    public Problem(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
    public static Problem invalid(String message) { return new Problem(422, "INVALID_INPUT", message); }
    public static Problem missing() { return new Problem(404, "NOT_FOUND", "Booking or resource not found."); }
    public static Problem conflict() { return new Problem(409, "SLOT_UNAVAILABLE", "That time is no longer available. Choose another slot."); }
}
