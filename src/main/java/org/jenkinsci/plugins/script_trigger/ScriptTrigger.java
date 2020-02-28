package org.jenkinsci.plugins.script_trigger;

import antlr.ANTLRException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.BooleanParameterValue;
import hudson.model.Cause;
import hudson.model.EnvironmentContributor;
import hudson.model.ParameterDefinition;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.PasswordParameterValue;
import hudson.model.Project;
import hudson.model.Run;
import hudson.model.StringParameterValue;
import hudson.model.Item;
import hudson.model.TaskListener;
import hudson.model.TopLevelItem;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import hudson.util.LogTaskListener;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ScriptTrigger extends Trigger<Project> {
    private transient static final Logger LOGGER = Logger.getLogger(ScriptTrigger.class.getName());

    private final String script;
    private static final String MARKER = "#:#:#";
    private static final String CAUSE_VAR = "CAUSE";
    private static final String CRLF = "\r\n";

    @DataBoundConstructor
    public ScriptTrigger(String schedule, String script) throws ANTLRException {
        super(schedule);
        this.script = script;
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public String getScript() {
        return script;
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public String getSchedule() {
        return spec;
    }


    public void run() {
        if (!Jenkins.get().isQuietingDown() && this.job.isBuildable()) {
            try {
                int ret = runScript(); //执行windows 上的 bat 脚本
                if (ret ==  0) {
                    int quietPeriod = job.getQuietPeriod();
                    job.scheduleBuild(quietPeriod, new DosTriggerCause("Script Trigger return " + ret));
                }
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Problem while executing ScriptTrigger.run()", e);
            }
        }
    }



    @Extension
    public static final class DescriptorImpl extends TriggerDescriptor {
        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return Messages.DisplayName();
        }
        public boolean isApplicable(Item item) {
            return item instanceof TopLevelItem;
        }


    }

    private EnvVars buildEnvironmentForScriptToRun(TaskListener listener) throws IOException, InterruptedException {
        EnvVars envVars = new EnvVars();

        ParametersDefinitionProperty p = (ParametersDefinitionProperty) this.job.getProperty(ParametersDefinitionProperty.class);
        if (p != null) {
            List<ParameterDefinition> paramList = p.getParameterDefinitions();
            for (ParameterDefinition parameter : paramList) {
                Object obj = parameter.getDefaultParameterValue();
                if (obj instanceof PasswordParameterValue) {
                    PasswordParameterValue password = (PasswordParameterValue) obj;
                    password.buildEnvironment(null, envVars);
                } else if(obj instanceof StringParameterValue) {
                    StringParameterValue stringParam = (StringParameterValue) obj;
                    stringParam.buildEnvironment(null, envVars);
                }else if (obj instanceof BooleanParameterValue){
                    BooleanParameterValue booleanParameterValue = (BooleanParameterValue) obj;
                    booleanParameterValue.buildEnvironment(null, envVars);
                }
            }
        }


        return envVars;
    }


    private int runScript() throws InterruptedException {
        final TaskListener listener = new LogTaskListener(LOGGER, Level.INFO);
        try {
            final FilePath ws = Jenkins.get().getWorkspaceFor((TopLevelItem) job);
            final FilePath batchFile = ws.createTextTempFile("jenkins", ".bat", makeScript(), true);
            final FilePath logFile = ws.child("dos-trigger.log");
            try (LogStream logStream = new LogStream(logFile)) {
                final Launcher launcher = Jenkins.get().createLauncher(listener);
                String[] cmd = new String[]{"cmd", "/c", "call", batchFile.getRemote()};
                if(launcher.isUnix()) {
                    cmd = new String[]{"bash", "-x", batchFile.getRemote()};
                }

                final EnvVars envVars = this.buildEnvironmentForScriptToRun(listener);
                int ret = launcher.launch().cmds(cmd).envs(envVars).stdout(logStream).pwd(ws).join();
                return ret;
            } catch (IOException e) {
                Util.displayIOException(e, listener);
                e.printStackTrace(listener.fatalError(Messages.CommandInterpreter_CommandFailed()));
                return -1;
            }finally {
                try {
                    batchFile.delete();
                } catch (IOException e) {
                    Util.displayIOException(e, listener);
                    e.printStackTrace(listener.fatalError(Messages.CommandInterpreter_UnableToDelete(batchFile)));
                }
            }
        } catch (IOException e) {
            Util.displayIOException(e, listener);
            e.printStackTrace(listener.fatalError(Messages.CommandInterpreter_UnableToProduceScript()));
            return -1;
        }
    }

    private String makeScript() {
        return ""
                + "@set " + CAUSE_VAR + "=" + CRLF
                + "@echo off" + CRLF
                + "call :TheActualScript" + CRLF
                + "@echo off" + CRLF
                + "echo " + MARKER + CAUSE_VAR + MARKER + "%" + CAUSE_VAR + "%" + MARKER + CRLF
                + "goto :EOF" + CRLF
                + ":TheActualScript" + CRLF
                + script + CRLF;
    }

    @Extension
    public static class FilesFoundEnvironmentContributor extends EnvironmentContributor {

        /**
         * {@inheritDoc}
         */
        @Override
        public void buildEnvironmentFor(@SuppressWarnings("rawtypes") Run r, EnvVars envs,
                                        TaskListener listener) {
            buildEnvironmentFor(r, envs);
        }

        private void buildEnvironmentFor(Run<?, ?> run, EnvVars envVars) {
            DosTriggerCause cause = run.getCause(DosTriggerCause.class);
            if (cause != null) {
                envVars.put(name("triggernumber"), "cause.getTriggerNumber()");
            }
        }

        private String name(String envVar) {
            return "DosTriggerCause_setting_" + envVar;
        }
    }

    private static class DosTriggerCause extends Cause {
        private final String description;

        public DosTriggerCause(String description) {
            this.description = description;
        }

        public String getShortDescription() {
            return description;
        }
    }

    private static class LogStream extends OutputStream {
        private final StringBuilder log = new StringBuilder();
        private final OutputStream logStream;

        public LogStream(FilePath logFile) throws IOException, InterruptedException {
            logStream = logFile.write();
        }

        public void write(int b) throws IOException {
            log.append((char) b);
            logStream.write(b);
        }

        public void close() throws IOException {
            super.close();
            logStream.close();
        }

        public String toString() {
            return log.toString();
        }
    }
}

