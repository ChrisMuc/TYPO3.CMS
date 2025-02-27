package core;

/*
 * This file is part of the TYPO3 CMS project.
 *
 * It is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License, either version 2
 * of the License, or any later version.
 *
 * For the full copyright and license information, please read the
 * LICENSE.txt file that was distributed with this source code.
 *
 * The TYPO3 project - inspiring people to share!
 */

import com.atlassian.bamboo.specs.api.BambooSpec;
import com.atlassian.bamboo.specs.api.builders.AtlassianModule;
import com.atlassian.bamboo.specs.api.builders.Variable;
import com.atlassian.bamboo.specs.api.builders.notification.AnyNotificationRecipient;
import com.atlassian.bamboo.specs.api.builders.notification.Notification;
import com.atlassian.bamboo.specs.api.builders.plan.Job;
import com.atlassian.bamboo.specs.api.builders.plan.Plan;
import com.atlassian.bamboo.specs.api.builders.plan.Stage;
import com.atlassian.bamboo.specs.api.builders.plan.branches.BranchCleanup;
import com.atlassian.bamboo.specs.api.builders.plan.branches.PlanBranchManagement;
import com.atlassian.bamboo.specs.api.builders.project.Project;
import com.atlassian.bamboo.specs.api.builders.task.Task;
import com.atlassian.bamboo.specs.builders.notification.PlanCompletedNotification;
import com.atlassian.bamboo.specs.builders.trigger.ScheduledTrigger;
import com.atlassian.bamboo.specs.util.BambooServer;

import java.util.ArrayList;
import java.util.ListIterator;

/**
 * Core master nightly test plan.
 */
@BambooSpec
public class NightlySpec extends AbstractCoreSpec {

    private static String planName = "Core master nightly";
    private static String planKey = "GTN";

    private static int numberOfAcceptanceTestJobs = 8;
    private static int numberOfFunctionalMysqlJobs = 6;
    private static int numberOfFunctionalMssqlJobs = 16;
    private static int numberOfFunctionalPgsqlJobs = 6;
    private static int numberOfFunctionalSqliteJobs = 6;
    private static int numberOfUnitRandomOrderJobs = 2;

    private String[] phpVersions = {"PHP72", "PHP73", "PHP74"};
    private String[] mySqlPdoVersions = {"5.5", "5.6", "5.7"};
    private String[] mySqlVersions = {"5.5", "5.6", "5.7"};
    private String[] mariaDbPdoVersions = {"5.5", "10.0", "10.1", "10.3"};
    private String[] mariaDbVersions = {"5.5", "10.0", "10.1", "10.3"};
    private String[] msSqlPdoVersions = {"2012", "2014", "2016", "2017", "2019"};
    private String[] msSqlVersions = {"2012", "2014", "2016", "2017", "2019"};
    private String[] sqLiteVersions = {"3.15", "3.20", "3.25", "3.30"};
    private String[] postGreSqlVersions = {"9.3", "9.4", "9.5", "9.6", "10.11", "11.6", "12.1"};

    private int jobListSize = 50;

    /**
     * Run main to publish plan on Bamboo
     */
    public static void main(final String[] args) throws Exception {
        // By default credentials are read from the '.credentials' file.
        BambooServer bambooServer = new BambooServer(bambooServerName);
        bambooServer.publish(new NightlySpec().createPlan());
        bambooServer.publish(new NightlySpec().getDefaultPlanPermissions(projectKey, planKey));
    }

    /**
     * Core master pre-merge plan is in "TYPO3 core" project of bamboo
     */
    private Project project() {
        return new Project().name(projectName)
            .key(projectKey);
    }

    /**
     * Returns full Plan definition
     */
    Plan createPlan() {
        Stage stagePreparation = getPreparationStage();

        Stage stageIntegrity = getIntegrityStage();
        ArrayList<Job> jobs = new ArrayList<Job>();
        jobs.addAll(getUnitTestJobs());
        jobs.addAll(getCodeceptionMySqlJobs());
        jobs.addAll(getCodeceptionSqLiteJobs());
        jobs.addAll(getCodeceptionPgSqlJobs());
        jobs.addAll(getFunctionalMySqlJobs());
        jobs.addAll(getFunctionalMySqlPdoJobs());
        jobs.addAll(getFunctionalMsSqlJobs());
        jobs.addAll(getFunctionalMsSqlPdoJobs());
        jobs.addAll(getFunctionalPGSqlJobs());
        jobs.addAll(getFunctionalSqliteJobs());

        ArrayList<Stage> stages = new ArrayList<Stage>();
        stages.add(stagePreparation);
        stages.add(stageIntegrity);
        ListIterator<Job> jobIterator = jobs.listIterator();
        Stage stage = new Stage("Jobs " + (jobIterator.nextIndex()) + " - " + (jobIterator.nextIndex() - 1 + jobListSize));
        ArrayList<Job> chunkedJobs = new ArrayList<Job>();
        while (jobIterator.hasNext()) {
            chunkedJobs.add(jobIterator.next());
            if ((jobIterator.nextIndex() % jobListSize) == 0) {
                stage.jobs(chunkedJobs.toArray(new Job[chunkedJobs.size()]));
                stages.add(stage);
                stage = new Stage("Jobs " + (jobIterator.nextIndex()) + " - " + (jobIterator.nextIndex() - 1 + jobListSize));
                chunkedJobs.clear();
            }
            if (!jobIterator.hasNext()) {
                stage.jobs(chunkedJobs.toArray(new Job[chunkedJobs.size()]));
                stages.add(stage);
            }
        }

        // Compile plan
        return new Plan(project(), planName, planKey).description("Execute TYPO3 core master nightly tests. Auto generated! See Build/bamboo of core git repository.")
            .pluginConfigurations(this.getDefaultPlanPluginConfiguration())
            .stages(stages.toArray(new Stage[stages.size()]))
            .linkedRepositories("github TYPO3 TYPO3.CMS")
            .triggers(new ScheduledTrigger().name("Scheduled")
                .description("once a day")
                .cronExpression("0 0 3 ? * *"))
            .variables(new Variable("changeUrl", ""), new Variable("patchset", ""))
            .planBranchManagement(new PlanBranchManagement().delete(new BranchCleanup())
                .notificationForCommitters())
            .notifications(new Notification().type(new PlanCompletedNotification())
                .recipients(new AnyNotificationRecipient(new AtlassianModule("com.atlassian.bamboo.plugins.bamboo-slack:recipient.slack"))
                    .recipientString("https://intercept.typo3.com/bamboo")));
    }

    /**
     * functional tests in all composer install stages, executed with DBMS Sqlite
     */
    private ArrayList<Job> getFunctionalSqliteJobs() {
        ArrayList<Job> jobs = new ArrayList<Job>();
        for (String phpVersion : phpVersions) {
            for (int stageNumber = 0; stageNumber <= 2; stageNumber++) {
                Task composerTask = getComposerTaskByStageNumber(phpVersion, stageNumber);
                jobs.addAll(this.getJobsFunctionalTestsSqlite(stageNumber, numberOfFunctionalSqliteJobs, phpVersion, composerTask, false));
            }
        }
        return jobs;
    }

    /**
     * functional tests in all composer install stages, executed with DBMS PostgreSql
     */
    private ArrayList<Job> getFunctionalPGSqlJobs() {
        ArrayList<Job> jobs = new ArrayList<Job>();
        for (String phpVersion : phpVersions) {
            for (int stageNumber = 0; stageNumber <= 2; stageNumber++) {
                Task composerTask = getComposerTaskByStageNumber(phpVersion, stageNumber);
                jobs.addAll(this.getJobsFunctionalTestsPgsql(stageNumber, numberOfFunctionalPgsqlJobs, phpVersion, composerTask, false));
            }
        }
        return jobs;
    }

    /**
     * functional tests in all composer install stages, executed with DBMS MsSQL, driver is pdo_sqlsrv
     */
    private ArrayList<Job> getFunctionalMsSqlPdoJobs() {
        ArrayList<Job> jobs = new ArrayList<Job>();
        for (String phpVersion : phpVersions) {
            for (int stageNumber = 0; stageNumber <= 2; stageNumber++) {
                Task composerTask = getComposerTaskByStageNumber(phpVersion, stageNumber);
                jobs.addAll(this.getJobsFunctionalTestsMssqlWithDriverPdoSqlSrv(stageNumber, numberOfFunctionalMssqlJobs, phpVersion, composerTask, false));
            }
        }
        return jobs;
    }

    /**
     * functional tests in all composer install stages, executed with DBMS MsSQL, driver is sqlsrv
     */
    private ArrayList<Job> getFunctionalMsSqlJobs() {
        ArrayList<Job> jobs = new ArrayList<Job>();
        for (String phpVersion : phpVersions) {
            for (int stageNumber = 0; stageNumber <= 2; stageNumber++) {
                Task composerTask = getComposerTaskByStageNumber(phpVersion, stageNumber);
                jobs.addAll(this.getJobsFunctionalTestsMssqlWithDriverSqlSrv(stageNumber, numberOfFunctionalMssqlJobs, phpVersion, composerTask, false));
            }
        }
        return jobs;
    }

    /**
     * functional tests in all composer install stages, executed with DBMS MySQL, driver is mysqli
     */
    private ArrayList<Job> getFunctionalMySqlJobs() {
        ArrayList<Job> jobs = new ArrayList<Job>();
        for (String phpVersion : phpVersions) {
            for (int stageNumber = 0; stageNumber <= 2; stageNumber++) {
                Task composerJob = getComposerTaskByStageNumber(phpVersion, stageNumber);
                jobs.addAll(this.getJobsFunctionalTestsMysqlWithDriverMySqli(stageNumber, numberOfFunctionalMysqlJobs, phpVersion, composerJob, false));
            }
        }
        return jobs;
    }

    /**
     * functional tests in all composer install stages, executed with DBMS MySQL, driver is pdo_mysql
     */
    private ArrayList<Job> getFunctionalMySqlPdoJobs() {
        ArrayList<Job> jobs = new ArrayList<Job>();
        for (String phpVersion : phpVersions) {
            for (int stageNumber = 0; stageNumber <= 2; stageNumber++) {
                Task composerJob = getComposerTaskByStageNumber(phpVersion, stageNumber);
                jobs.addAll(this.getJobsFunctionalTestsMysqlWithDriverPdoMysql(stageNumber, numberOfFunctionalMysqlJobs, phpVersion, composerJob, false));
            }
        }
        return jobs;
    }

    /**
     * all tests run via codeception framework on MySql, for all php versions and each with composer max and min install
     */
    private ArrayList<Job> getCodeceptionMySqlJobs() {
        ArrayList<Job> jobs = new ArrayList<Job>();
        for (String phpVersion : phpVersions) {
            for (int stageNumber = 0; stageNumber <= 2; stageNumber++) {
                Task composerTask = getComposerTaskByStageNumber(phpVersion, stageNumber);
                jobs.add(this.getJobAcceptanceTestInstallMysql(stageNumber, phpVersion, composerTask, false));
                jobs.addAll(this.getJobsAcceptanceTestsBackendMysql(stageNumber, numberOfAcceptanceTestJobs, phpVersion, composerTask, false));
            }
        }
        return jobs;
    }

    /**
     * all tests run via codeception framework on SqLite, for all php versions and each with composer max and min install
     */
    private ArrayList<Job> getCodeceptionSqLiteJobs() {
        ArrayList<Job> jobs = new ArrayList<Job>();
        for (String phpVersion : phpVersions) {
            for (int stageNumber = 0; stageNumber <= 2; stageNumber++) {
                Task composerTask = getComposerTaskByStageNumber(phpVersion, stageNumber);
                jobs.add(this.getJobAcceptanceTestInstallSqlite(stageNumber, phpVersion, composerTask, false));
            }
        }
        return jobs;
    }

    /**
     * all tests run via codeception framework on PostGreSql, for all php versions and each with composer max and min install
     */
    private ArrayList<Job> getCodeceptionPgSqlJobs() {
        ArrayList<Job> jobs = new ArrayList<Job>();
        for (String phpVersion : phpVersions) {
            for (int stageNumber = 0; stageNumber <= 2; stageNumber++) {
                Task composerTask = getComposerTaskByStageNumber(phpVersion, stageNumber);
                jobs.add(this.getJobAcceptanceTestInstallPgsql(stageNumber, phpVersion, composerTask, false));
            }
        }
        return jobs;
    }

    /**
     * all unit tests, for all php versions and each with composer max and min install
     */
    private ArrayList<Job> getUnitTestJobs() {
        ArrayList<Job> jobs = new ArrayList<Job>();
        for (String phpVersion : phpVersions) {
            for (int stageNumber = 0; stageNumber <= 2; stageNumber++) {
                Task composerTask = getComposerTaskByStageNumber(phpVersion, stageNumber);
                jobs.addAll(this.getJobUnitPhpRandom(stageNumber, numberOfUnitRandomOrderJobs, phpVersion, composerTask, false));
                jobs.add(this.getJobUnitDeprecatedPhp(stageNumber, phpVersion, composerTask, false));
                jobs.add(this.getJobUnitPhp(stageNumber, phpVersion, composerTask, false));
            }
        }
        return jobs;
    }

    /**
     * integrity stage - various checks for code quality
     * <p>
     * this stage is independent of actual composer or php versions
     */
    private Stage getIntegrityStage() {
        String phpVersionForIntegrityStage = phpVersions[0]; // the version is not very important, just use one (except for linting!)
        ArrayList<Job> jobs = new ArrayList<Job>();
        jobs.add(this.getJobIntegrationAnnotations(phpVersionForIntegrityStage, this.getTaskComposerInstall(phpVersionForIntegrityStage), false));
        jobs.add(this.getJobCglCheckFullCore(phpVersionForIntegrityStage, this.getTaskComposerInstall(phpVersionForIntegrityStage), false));
        jobs.add(this.getJobIntegrationDocBlocks(phpVersionForIntegrityStage, this.getTaskComposerInstall(phpVersionForIntegrityStage), false));
        jobs.add(this.getJobIntegrationVarious(phpVersionForIntegrityStage, this.getTaskComposerInstall(phpVersionForIntegrityStage), false));
        jobs.add(this.getJobLintScssTs("JS", false));
        jobs.add(this.getJobUnitJavaScript("JS", this.getTaskComposerInstall(phpVersionForIntegrityStage), false));
        jobs.add(this.getJobComposerValidate(phpVersionForIntegrityStage, false));

        for (String phpVersion : phpVersions) {
            jobs.add(this.getJobLintPhp(phpVersion, false));
        }
        return new Stage("Integrity").jobs(jobs.toArray(new Job[jobs.size()]));
    }

    /**
     * preparation stage - this will only define labels for later communication of test results
     */
    private Stage getPreparationStage() {
        ArrayList<Job> jobs = new ArrayList<Job>();
        jobs.add(this.getJobBuildLabels());
        return new Stage("Preparation").jobs(jobs.toArray(new Job[jobs.size()]));
    }
}
