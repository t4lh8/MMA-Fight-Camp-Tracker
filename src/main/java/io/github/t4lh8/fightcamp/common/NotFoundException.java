package io.github.t4lh8.fightcamp.common;

/** Thrown when a record does not exist or belongs to someone else. Both give the same 404. */
public class NotFoundException extends RuntimeException {

    public NotFoundException() {
        super("Not found");
    }
}
