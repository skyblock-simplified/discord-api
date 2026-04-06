package dev.sbs.discordapi;

import dev.sbs.api.SimplifiedApi;
import dev.simplified.scheduler.Scheduler;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestPlan;

/**
 * Manages the {@link SimplifiedApi} lifecycle around the test plan execution.
 * <p>
 * On startup, eagerly triggers the {@link SimplifiedApi} static initializer.
 * On finish, shuts down the session manager and scheduler so non-daemon threads
 * do not prevent the test JVM from exiting.
 */
public class TestLifecycleListener implements TestExecutionListener {

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
        System.out.println("[TestLifecycleListener] Initializing SimplifiedApi");
        SimplifiedApi.getSessionManager();
    }

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        System.out.println("[TestLifecycleListener] testPlanExecutionFinished called");

        if (SimplifiedApi.getSessionManager().isActive()) {
            SimplifiedApi.getSessionManager().shutdown();
            System.out.println("[TestLifecycleListener] SessionManager shut down");
        }

        if (!SimplifiedApi.getScheduler().isShutdown()) {
            SimplifiedApi.getScheduler().shutdown();
            System.out.println("[TestLifecycleListener] Scheduler shut down");
        }

        Scheduler.leakedThreads().forEach(t -> System.out.printf(
            "[TestLifecycleListener] Remaining non-daemon: %s (state=%s, group=%s)%n",
            t.getName(), t.getState(), t.getThreadGroup()
        ));
    }

}
