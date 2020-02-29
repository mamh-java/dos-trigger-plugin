package org.jenkinsci.plugins.script_trigger;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.EnvironmentContributor;
import hudson.model.Run;
import hudson.model.TaskListener;

import java.util.logging.Logger;

@Extension
public class ScriptTriggerEnvironmentContributor extends EnvironmentContributor {
    private transient static final Logger LOGGER = Logger.getLogger(ScriptTriggerEnvironmentContributor.class.getName());

    /**
     * {@inheritDoc}
     */
    @Override
    public void buildEnvironmentFor(@SuppressWarnings("rawtypes") Run r, EnvVars envs,
                                    TaskListener listener) {
        buildEnvironmentFor(r, envs);
    }

    private void buildEnvironmentFor(Run<?, ?> run, EnvVars envVars) {
        ScriptTriggerCause cause = run.getCause(ScriptTriggerCause.class);
        if (cause != null) {
            envVars.put(name("status"), "" + cause.getStatus());
            envVars.put(name("output"), "" + cause.getOutput());
        }
    }


    private String name(String envVar) {
        return "ScriptTrigger_" + envVar;
    }
}