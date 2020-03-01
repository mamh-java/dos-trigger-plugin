package org.jenkinsci.plugins.script_trigger;

import antlr.ANTLRException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.BooleanParameterValue;
import hudson.model.Item;
import hudson.model.ParameterDefinition;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.PasswordParameterValue;
import hudson.model.Project;
import hudson.model.StringParameterValue;
import hudson.model.TopLevelItem;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import hudson.util.LogTaskListener;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ScriptTrigger extends Trigger<Project> {
    private transient static final Logger LOGGER = Logger.getLogger(ScriptTrigger.class.getName());

    private final String script;
    private static final String MARKER    = "#:#:#";
    private static final String CAUSE_VAR = "CAUSE";
    private static final String CRLF      = "\r\n";

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
                String ret = runScript(); //执行windows 上的 bat 脚本
                if (ret != null) {
                    job.scheduleBuild(0, new ScriptTriggerCause(ret, "0"));
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Problem while executing ScriptTrigger.run()", e);
            }
        }
    }

    private String runScript() throws InterruptedException {
        final LogTaskListener listener = new LogTaskListener(LOGGER, Level.INFO);
        try {
            final FilePath ws = Jenkins.get().getWorkspaceFor((TopLevelItem) job);
            final FilePath batchFile = createScriptFile(ws); //ws.createTextTempFile("jenkins", ".bat", makeScript(), true);

            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                final Launcher launcher = Jenkins.get().createLauncher(listener);
                final EnvVars envVars = buildEnvironmentForScriptToRun();
                final String[] cmd = buildCommandLine(batchFile);

                Launcher.ProcStarter procStarter = launcher
                        .launch()
                        .cmds(cmd)
                        .envs(envVars)
                        .stdout(baos)
                        .pwd(ws);
                int exitCode = procStarter.join();
                if (exitCode == 0) { //Executes a command and reads the result to a string
                    String output = baos.toString()
                            .replaceAll("[\t\r\n]+", " ")
                            .trim();
                    LOGGER.log(Level.WARNING, "command execution output: " + output);

                    return output;
                } else {
                    return null;
                }
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "command execution failed", e);
                return null;
            } finally {
                try {
                    batchFile.delete();
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Unable to delete script file", e);
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Unable to produce a script file", e);
            return null;
        }
    }

    private EnvVars buildEnvironmentForScriptToRun() {
        EnvVars envVars = new EnvVars();
        ParametersDefinitionProperty p = (ParametersDefinitionProperty) this.job.getProperty(ParametersDefinitionProperty.class);
        if (p != null) {
            List<ParameterDefinition> paramList = p.getParameterDefinitions();
            for (ParameterDefinition parameter : paramList) {
                Object obj = parameter.getDefaultParameterValue();
                if (obj instanceof PasswordParameterValue) {
                    PasswordParameterValue password = (PasswordParameterValue) obj;
                    password.buildEnvironment(null, envVars);
                } else if (obj instanceof StringParameterValue) {
                    StringParameterValue stringParam = (StringParameterValue) obj;
                    stringParam.buildEnvironment(null, envVars);
                } else if (obj instanceof BooleanParameterValue) {
                    BooleanParameterValue booleanParameterValue = (BooleanParameterValue) obj;
                    booleanParameterValue.buildEnvironment(null, envVars);
                }
            }
        }
        return envVars;
    }

    private String[] buildCommandLine(FilePath batchFile) {
        String[] cmd;
        if (isUnix()) {
            cmd = new String[]{"bash", batchFile.getRemote()};
        } else {
            cmd = new String[]{"cmd", "/c", "call", batchFile.getRemote()};
        }
        return cmd;
    }

    private FilePath createScriptFile(@Nonnull FilePath dir) throws IOException, InterruptedException {
        return dir.createTextTempFile("jenkins", getFileExtension(), getContents(), false);
    }

    private String getContents() {
       String contents = ""
                        + "@set "+ CAUSE_VAR +"=" +CRLF
                        + "@echo off" + CRLF
                        + "call :TheActualScript" + CRLF
                        + "@echo off" + CRLF
                        + "echo " + MARKER + CAUSE_VAR + MARKER + "%" + CAUSE_VAR + "%" + MARKER + CRLF
                        + "goto :EOF" + CRLF
                        + ":TheActualScript" + CRLF
                        + script + CRLF;
        if (isUnix()) {
            return this.script;
        }else {
            return contents;
        }
    }

    private boolean isUnix() {
        return File.pathSeparatorChar == ':';
    }

    private String getFileExtension() {
        if (isUnix()) {
            return ".sh";
        } else {
            return ".bat";
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

}

