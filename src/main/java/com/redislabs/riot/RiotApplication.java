package com.redislabs.riot;

import java.net.InetAddress;
import java.util.List;
import java.util.Locale;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.redislabs.riot.cli.Export;
import com.redislabs.riot.cli.Import;
import com.redislabs.riot.redis.RedisConnectionBuilder;

import io.lettuce.core.RedisURI;
import lombok.Getter;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.DefaultExceptionHandler;
import picocli.CommandLine.Option;
import picocli.CommandLine.RunLast;
import redis.clients.jedis.Protocol;

@SpringBootApplication
@Command(name = "riot", subcommands = { Export.class, Import.class })
public class RiotApplication extends BaseCommand implements CommandLineRunner {

	public static void main(String[] args) {
		SpringApplication.run(RiotApplication.class, args);
	}

	public static final String DEFAULT_HOST = "localhost";

	public enum RedisDriver {
		Jedis, Lettuce
	}

	/**
	 * Just to avoid picocli complain in Eclipse console
	 */
	@Option(names = "--spring.output.ansi.enabled", hidden = true)
	private String ansiEnabled;

	@Option(names = "--driver", description = "Redis driver: ${COMPLETION-CANDIDATES}. (default: ${DEFAULT-VALUE})")
	@Getter
	private RedisDriver driver = RedisDriver.Jedis;
	@Option(names = "--host", description = "Redis server host. (default: localhost).")
	private InetAddress host;
	@Option(names = "--port", description = "Redis server port. (default: ${DEFAULT-VALUE}).")
	private int port = RedisURI.DEFAULT_REDIS_PORT;
	@Option(names = "--command-timeout", description = "Redis command timeout in seconds for synchronous command execution (default: ${DEFAULT-VALUE}).")
	private long commandTimeout = RedisURI.DEFAULT_TIMEOUT;
	@Option(names = "--connection-timeout", description = "Redis connect timeout in milliseconds. (default: ${DEFAULT-VALUE}).")
	private int connectionTimeout = Protocol.DEFAULT_TIMEOUT;
	@Option(names = "--socket-timeout", description = "Redis socket timeout in milliseconds. (default: ${DEFAULT-VALUE}).")
	private int socketTimeout = Protocol.DEFAULT_TIMEOUT;
	@Option(names = "--password", description = "Redis database password.", interactive = true)
	private String password;
	@Option(names = "--max-idle", description = "Maximum number of idle connections in the pool. Use a negative value to indicate an unlimited number of idle connections. (default: ${DEFAULT-VALUE}).")
	private int maxIdle = 8;
	@Option(names = "--min-idle", description = "Target for the minimum number of idle connections to maintain in the pool. This setting only has an effect if it is positive. (default: ${DEFAULT-VALUE}).")
	private int minIdle = 0;
	@Option(names = "--max-total", description = "Maximum number of connections that can be allocated by the pool at a given time. Use a negative value for no limit. (default: ${DEFAULT-VALUE})")
	private int maxTotal = 8;
	@Option(names = "--max-wait", description = "Maximum amount of time in milliseconds a connection allocation should block before throwing an exception when the pool is exhausted. Use a negative value to block indefinitely (default).")
	private long maxWait = -1L;
	@Option(names = "--database", description = "Redis database number. Databases are only available for Redis Standalone and Redis Master/Slave. (default: ${DEFAULT-VALUE}).")
	private int database = 0;
	@Option(names = "--client-name", description = "Redis client name.")
	private String clientName;

	public RedisConnectionBuilder redisConnectionBuilder() {
		RedisConnectionBuilder builder = new RedisConnectionBuilder();
		builder.setClientName(clientName);
		builder.setCommandTimeout(commandTimeout);
		builder.setConnectionTimeout(connectionTimeout);
		builder.setDatabase(database);
		builder.setHost(getHostname());
		builder.setMaxTotal(maxTotal);
		builder.setMaxIdle(maxIdle);
		builder.setMaxWait(maxWait);
		builder.setMinIdle(minIdle);
		builder.setPassword(password);
		builder.setPort(port);
		builder.setSocketTimeout(socketTimeout);
		return builder;
	}

	private String getHostname() {
		if (host != null) {
			return host.getHostName();
		}
		return DEFAULT_HOST;
	}

	@Override
	public void run(String... args) {
		CommandLine commandLine = new CommandLine(this);
		commandLine.registerConverter(Locale.class, s -> new Locale.Builder().setLanguageTag(s).build());
		commandLine.setCaseInsensitiveEnumValuesAllowed(true);
		RunLast handler = new RunLast();
		handler.useOut(System.out);
		DefaultExceptionHandler<List<Object>> exceptionHandler = CommandLine.defaultExceptionHandler();
		exceptionHandler.useErr(System.err);
		commandLine.parseWithHandlers(handler, exceptionHandler, args);
	}

}
