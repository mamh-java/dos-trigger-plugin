package org.jenkinsci.plugins.script_trigger;

import hudson.model.Cause;

public class ScriptTriggerCause extends Cause {
    private final String output;
    private final String status;


    public ScriptTriggerCause(String output, String status) {
        this.output = output;
        this.status = status;
    }

    public String getStatus() {
        return status;
    }

    public String getOutput() {
        return output;
    }



    public String getShortDescription() {
        String description = Messages.Cause_ScriptTriggerCause_ShortDescription(status); // cause 描述,由什么事件触发
        return description;
    }
}
