package de.m4lik.burningseries.api.objects;

/**
 * Created by Malik on 01.10.2016.
 */

public class UnwatchObj {

    private boolean success;

    public UnwatchObj(boolean success) {
        this.success = success;
    }

    public boolean isSuccess() {
        return success;
    }
}
