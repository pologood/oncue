/*******************************************************************************
 * Copyright 2013 Michael Marconi
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package oncue.tests.load;

import static akka.testkit.JavaTestKit.duration;

import java.util.Arrays;
import java.util.HashSet;

import oncue.agent.ThrottledAgent;
import oncue.common.messages.EnqueueJob;
import oncue.common.messages.Job;
import oncue.common.messages.Job.State;
import oncue.common.messages.JobProgress;
import oncue.common.messages.JobSummary;
import oncue.common.messages.SimpleMessages.SimpleMessage;
import oncue.scheduler.ThrottledScheduler;
import oncue.tests.base.DistributedActorSystemTest;
import oncue.tests.load.workers.SimpleLoadTestWorker;

import org.junit.Test;

import akka.actor.ActorRef;
import akka.testkit.JavaTestKit;

/**
 * Test the "job throttling" strategy, which combines the
 * {@linkplain ThrottledScheduler} and {@linkplain ThrottledAgent} to ensure
 * that a limited number of jobs can be processed by the agent at any one time.
 * 
 * This test sets up two separate actor systems and uses Netty to remote between
 * them.
 */
public class DistributedThrottledLoadTest extends DistributedActorSystemTest {

	private static final int JOB_COUNT = 2000;

	@Test
	public void distributedThrottledLoadTest() {

		// Create a queue manager probe
		final JavaTestKit queueManagerProbe = new JavaTestKit(serviceSystem);

		// Create a scheduler probe
		final JavaTestKit schedulerProbe = new JavaTestKit(serviceSystem) {
			{
				new IgnoreMsg() {

					@Override
					protected boolean ignore(Object message) {
						return !(message instanceof JobProgress || message instanceof EnqueueJob || message instanceof JobSummary);
					}
				};
			}
		};

		// Create a throttled, Redis-backed scheduler with a probe
		final ActorRef scheduler = createScheduler(schedulerProbe.getRef());

		serviceLog.info("Enqueing {} jobs...", JOB_COUNT);

		// Enqueue a stack of jobs
		for (int i = 0; i < JOB_COUNT; i++) {
			scheduler.tell(new EnqueueJob(SimpleLoadTestWorker.class.getName()),
					queueManagerProbe.getRef());
			queueManagerProbe.expectMsgClass(Job.class);
		}

		// Wait for all jobs to be enqueued
		queueManagerProbe.new AwaitCond(duration("60 seconds"), duration("2 seconds")) {

			@Override
			protected boolean cond() {
				scheduler.tell(SimpleMessage.JOB_SUMMARY, queueManagerProbe.getRef());
				JobSummary summary = queueManagerProbe.expectMsgClass(JobSummary.class);
				return summary.getJobs().size() == JOB_COUNT;
			}
		};

		serviceLog.info("Jobs enqueued.");

		// Create a throttled agent
		createAgent(new HashSet<String>(Arrays.asList(SimpleLoadTestWorker.class.getName())), null);

		// Wait until all the jobs have completed
		queueManagerProbe.new AwaitCond(duration("5 minutes"), duration("2 seconds")) {

			@Override
			protected boolean cond() {
				scheduler.tell(SimpleMessage.JOB_SUMMARY, queueManagerProbe.getRef());
				@SuppressWarnings("cast")
				JobSummary summary = (JobSummary) queueManagerProbe.expectMsgClass(JobSummary.class);
				int completed = 0;
				for (Job job : summary.getJobs()) {
					if (job.getState() == State.COMPLETE) {
						completed++;
					}
				}
				return completed == JOB_COUNT;
			}
		};

		serviceLog.info("All jobs were processed!");

	}

}
