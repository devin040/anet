package mil.dds.anet.utils;

import java.lang.invoke.MethodHandles;
import java.util.Optional;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response.Status;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import mil.dds.anet.AnetObjectEngine;
import mil.dds.anet.beans.Organization;
import mil.dds.anet.beans.Organization.OrganizationType;
import mil.dds.anet.beans.Person;
import mil.dds.anet.beans.Position;
import mil.dds.anet.beans.Position.PositionType;

public class AuthUtils {

	private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	public static String UNAUTH_MESSAGE = "You do not have permissions to do this";
	
	public static void assertAdministrator(Person user) { 
		logger.debug("Asserting admin status for {}", user);
		if (user.loadPosition() != null
				&& user.getPosition().getType() == PositionType.ADMINISTRATOR) { 
			return;
		}
		throw new WebApplicationException(UNAUTH_MESSAGE, Status.FORBIDDEN);
	}
	
	public static boolean isSuperUserForOrg(final Person user, final Organization org) {
		if (org == null || org.getUuid() == null) {
			logger.error("Organization {} is null or has a null UUID in SuperUser check for {}",
					org, user); // DANGER: possible log injection vector here?
			return false;
		}
		Position position = user.loadPosition();
		if (position == null) {
			logger.warn("User {} has no position, hence no permissions", user);
			return false;
		}
		if (position.getType() == PositionType.ADMINISTRATOR) {
			logger.debug("User {} is an administrator, automatically a superuser", user);
			return true;
		}
		logger.debug("Position for user {} is {}", user, position);
		if (position.getType() != PositionType.SUPER_USER) { return false; } 

		// Given that we know it's a super-user position, does it actually match this organization?
		Organization loadedOrg = AnetObjectEngine.getInstance().getOrganizationDao().getByUuid(org.getUuid());
		if (loadedOrg.getType() == OrganizationType.PRINCIPAL_ORG) { return true; }
		
		if (position.getOrganization() == null) { return false; }
		if (org.getUuid().equals(position.getOrganization().getUuid())) { return true; }
		
		//As a last check, load the descendant orgs. 
		Optional<Organization> orgMatch =  position.loadOrganization()
				.loadAllDescendants()
				.stream()
				.filter(o -> o.getUuid().equals(org.getUuid()))
				.findFirst();
		return orgMatch.isPresent();
	}
	
	public static void assertSuperUserForOrg(Person user, Organization org) {
		// log injection possibility here?
		logger.debug("Asserting superuser status for {} in {}", user, org);
		if (isSuperUserForOrg(user, org)) { return; }
		throw new WebApplicationException(UNAUTH_MESSAGE, Status.FORBIDDEN);
	}

	public static void assertSuperUser(Person user) {
		logger.debug("Asserting some superuser status for {}", user);
		Position position = user.loadPosition();
		if (position != null
			&& (position.getType() == PositionType.SUPER_USER
			|| position.getType() == PositionType.ADMINISTRATOR)) { 
			return;
		}
		throw new WebApplicationException(UNAUTH_MESSAGE, Status.FORBIDDEN);
	}

	public static boolean isAdmin(Person user) {
		Position position = user.loadPosition();
		return position.getType() == PositionType.ADMINISTRATOR;
	}
	
}
