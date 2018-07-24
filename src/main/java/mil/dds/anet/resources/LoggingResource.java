package mil.dds.anet.resources;

import java.lang.invoke.MethodHandles;
import java.util.List;

import javax.annotation.security.PermitAll;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import com.codahale.metrics.annotation.Timed;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.dropwizard.auth.Auth;
import mil.dds.anet.beans.Person;

@Path("/api/logging")
@Consumes(MediaType.APPLICATION_JSON)
@PermitAll
public class LoggingResource {

	private static final Logger logger = Logger.getLogger(MethodHandles.lookup().lookupClass());

	static class LogEntry {
		/** one of 'DEBUG','ERROR','FATAL','INFO','WARN' */
		@JsonProperty String severity;
		/** the context url */
		@JsonProperty String url;
		/** line number of the error */
		@JsonProperty String lineNr;
		/** the error/log message */
		@JsonProperty String message;
	}
	
	/**
	 * Create a log entry based on the following inputs:
	 * @param requestContext the HTTP request context (used for getting the remote address)
	 * @param user the authenticated user logging the messages
	 * @param logEntries a list of log entries
	 */
	@POST
	@Timed
	@Path("/log")
	@PermitAll
	public void logMessage(final @Context HttpServletRequest requestContext, final @Auth Person user, final List<LogEntry> logEntries) {
		for (final LogEntry logEntry : logEntries) {
			logger.log(Level.toLevel(logEntry.severity), String.format("%1$s %2$s %3$s %4$s %5$s",
					user.getUuid(), requestContext.getRemoteAddr(), logEntry.url,
					logEntry.lineNr, logEntry.message));
		}
	}
}
