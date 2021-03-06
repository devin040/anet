package mil.dds.anet;

import org.skife.jdbi.v2.DBI;

import io.dropwizard.cli.ConfiguredCommand;
import net.sourceforge.argparse4j.inf.Namespace;
import mil.dds.anet.config.AnetConfiguration;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.jdbi.DBIFactory;
import io.dropwizard.setup.Environment;
import net.sourceforge.argparse4j.inf.Subparser;


public class WaitForDBCommand extends ConfiguredCommand<AnetConfiguration> {

	public WaitForDBCommand() {
		super("waitForDB","Waits until DB is ready for connection");
	}

	@Override
	public void configure(Subparser subparser) {
		subparser.addArgument("-n", "--nbAttempts")
			.dest("dbConnectionNbAttempts")
			.type(Integer.class)
			.required(false)
			.setDefault(20)
			.help("Nb of attempts before giving up. 20 by default");

		subparser.addArgument("-d", "--delay")
			.dest("dbConnectionDelay")
			.type(Integer.class)
			.required(false)
			.setDefault(500)
			.help("Delay in ms between attempts. 500 by default");

		addFileArgument(subparser);
	}

	@Override
	protected void run(Bootstrap<AnetConfiguration> bootstrap, Namespace namespace, AnetConfiguration configuration)
		throws Exception {
			final DBIFactory factory = new DBIFactory();
			final Environment environment = new Environment(bootstrap.getApplication().getName(),
					bootstrap.getObjectMapper(),
					bootstrap.getValidatorFactory().getValidator(),
					bootstrap.getMetricRegistry(),
					bootstrap.getClassLoader(),
					bootstrap.getHealthCheckRegistry());
			final DBI jdbi = factory.build(environment, configuration.getDataSourceFactory(), "mssql");

		//We want to possibly wait for the database to be ready, and keep trying to connect
		int remainingTries = namespace.getInt("dbConnectionNbAttempts").intValue();
		final int delay = namespace.getInt("dbConnectionDelay").intValue();
		while (remainingTries-- > 0) {
			try {
				jdbi.close(jdbi.open());
				break;
			}
			catch (Exception exception) {
				if (remainingTries==0)
					throw new RuntimeException(exception);
			}
			try {
				Thread.sleep(delay);
			}
			catch(InterruptedException exception){
				throw new RuntimeException(exception);
			}
		} 
	}
}
