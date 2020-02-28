package org.jenkinsci.plugins.script_trigger;

import hudson.model.Cause;

public class ScriptTriggerCause extends Cause {
    private final int status;
    private final String description;

    public ScriptTriggerCause(int status, String description) {
        this.status = status;
        this.description = description;
    }

    public int getStatus() {
        return status;
    }

    public String getShortDescription() {
        return description;
    }
}
