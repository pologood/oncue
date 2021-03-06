package oncue.agent;

import java.util.HashSet;
import java.util.Set;

import oncue.common.settings.Settings;
import oncue.common.settings.SettingsProvider;
import akka.actor.Actor;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.UntypedActorFactory;
import akka.kernel.Bootable;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public class OnCueAgent implements Bootable {

	public static void main(String[] args) {
		new OnCueAgent().startup();
	}

	private ActorSystem system;

	@Override
	public void shutdown() {
		system.shutdown();
	}

	@SuppressWarnings("serial")
	@Override
	public void startup() {
		Config config = ConfigFactory.load();
		system = ActorSystem.create("oncue-agent", config.getConfig("oncue").withFallback(config));

		final Settings settings = SettingsProvider.SettingsProvider.get(system);
		final Set<String> workers = new HashSet<String>(config.getStringList("oncue.agent.workers"));

		// Fail fast if worker classes can't be instantiated
		system.actorOf(new Props(new UntypedActorFactory() {

			@Override
			public Actor create() throws Exception {
				return (Actor) Class.forName(settings.AGENT_CLASS).getConstructor(Set.class)
						.newInstance(workers);
			}
		}), settings.AGENT_NAME);
	}
}
