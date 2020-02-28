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
import hudson.model.TaskListener;
import hudson.model.TopLevelItem;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import hudson.util.LogTaskListener;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ScriptTrigger extends Trigger<Project> {
    private transient static final Logger LOGGER = Logger.getLogger(ScriptTrigger.class.getName());

    private final String script;

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
                if (ret == 0) {
                    job.scheduleBuild(0, new ScriptTriggerCause(ret, "Script Trigger by  " + script));
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Problem while executing ScriptTrigger.run()", e);
            }
        }
    }

    private int runScript() throws InterruptedException {
        final TaskListener listener = new LogTaskListener(LOGGER, Level.INFO);
        try {
            final FilePath ws = Jenkins.get().getWorkspaceFor((TopLevelItem) job);
            final FilePath batchFile = createScriptFile(ws); //ws.createTextTempFile("jenkins", ".bat", makeScript(), true);

            try {
                final Launcher launcher = Jenkins.get().createLauncher(listener);
                final EnvVars envVars = buildEnvironmentForScriptToRun();
                String[] cmd = buildCommandLine(batchFile);
                Launcher.ProcStarter procStarter = launcher.launch().cmds(cmd).envs(envVars).stdout(listener).pwd(ws);
                int ret = procStarter.join();
                return ret;
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "command execution failed", e);
                return -1;
            } finally {
                try {
                    batchFile.delete();
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Unable to delete script file", e);
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Unable to produce a script file", e);
            return -1;
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
            cmd = new String[]{"bash", "-x", batchFile.getRemote()};
        } else {
            cmd = new String[]{"cmd", "/c", "call", batchFile.getRemote()};
        }
        return cmd;
    }


    private FilePath createScriptFile(@Nonnull FilePath dir) throws IOException, InterruptedException {
        return dir.createTextTempFile("jenkins", getFileExtension(), getContents(), true);
    }

    private String getContents() {
        return this.script;
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

