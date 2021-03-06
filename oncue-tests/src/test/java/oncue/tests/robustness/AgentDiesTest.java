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
package oncue.tests.robustness;

import java.util.Arrays;
import java.util.HashSet;

import junit.framework.Assert;
import oncue.agent.UnlimitedCapacityAgent;
import oncue.common.messages.EnqueueJob;
import oncue.common.messages.Job;
import oncue.common.messages.JobProgress;
import oncue.common.messages.SimpleMessages.SimpleMessage;
import oncue.tests.base.ActorSystemTest;
import oncue.tests.workers.TestWorker;

import org.junit.Test;

import akka.actor.Actor;
import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.actor.UntypedActorFactory;
import akka.testkit.JavaTestKit;

/**
 * An agent may die unexpectedly while it is processing jobs. Since we cannot
 * rely on the agent sending a message in its death-throes (the entire JVM may
 * have exploded), we need to detect that its heart beat has stopped and take
 * action back at the scheduler.
 */
public class AgentDiesTest extends ActorSystemTest {

	@SuppressWarnings("serial")
	@Test
	public void testAgentDiesAndAnotherReplacesIt() {
		new JavaTestKit(system) {
			{
				// Create a scheduler probe
				final JavaTestKit schedulerProbe = new JavaTestKit(system) {
					{
						new IgnoreMsg() {

							@Override
							protected boolean ignore(Object message) {
								if (message.equals(SimpleMessage.AGENT_DEAD) || message instanceof JobProgress)
									return false;
								else
									return true;
							}
						};
					}
				};

				// Create a scheduler with a probe
				ActorRef scheduler = createScheduler(system, schedulerProbe.getRef());

				// Create an agent
				ActorRef agent1 = system.actorOf(new Props(new UntypedActorFactory() {
					@Override
					public Actor create() throws Exception {
						return new UnlimitedCapacityAgent(
								new HashSet<String>(Arrays.asList(TestWorker.class.getName())));
					}
				}), "agent1");

				// Enqueue a job
				scheduler.tell(new EnqueueJob(TestWorker.class.getName()), getRef());
				Job job = expectMsgClass(Job.class);

				// Wait for some progress
				schedulerProbe.expectMsgClass(JobProgress.class);

				// Tell the agent to commit seppuku
				agent1.tell(PoisonPill.getInstance(), getRef());

				// Expect a message about agent death
				schedulerProbe.expectMsgEquals(settings.SCHEDULER_AGENT_HEARTBEAT_TIMEOUT.plus(duration("5 seconds")),
						SimpleMessage.AGENT_DEAD);

				// The heartbeat of the original agent should die
				schedulerProbe.expectNoMsg(settings.AGENT_HEARTBEAT_FREQUENCY);

				// Now, create a second agent
				system.actorOf(new Props(new UntypedActorFactory() {
					@Override
					public Actor create() throws Exception {
						return new UnlimitedCapacityAgent(
								new HashSet<String>(Arrays.asList(TestWorker.class.getName())));
					}
				}), "agent2");

				// Wait for some progress on the original job
				JobProgress jobProgress = schedulerProbe.expectMsgClass(JobProgress.class);
				Assert.assertEquals("Wrong job being worked on", job.getId(), jobProgress.getJob().getId());
			}
		};
	}
}
