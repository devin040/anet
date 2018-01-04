package mil.dds.anet.resources;

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import io.dropwizard.auth.Auth;
import mil.dds.anet.AnetObjectEngine;
import mil.dds.anet.beans.Person;
import mil.dds.anet.beans.AuthorizationGroup;
import mil.dds.anet.beans.lists.AbstractAnetBeanList.AuthorizationGroupList;
import mil.dds.anet.beans.search.AuthorizationGroupSearchQuery;
import mil.dds.anet.database.AuthorizationGroupDao;
import mil.dds.anet.graphql.GraphQLFetcher;
import mil.dds.anet.graphql.GraphQLParam;
import mil.dds.anet.graphql.IGraphQLResource;
import mil.dds.anet.utils.AnetAuditLogger;
import mil.dds.anet.utils.ResponseUtils;

@Path("/api/authorizationGroups")
@Produces(MediaType.APPLICATION_JSON)
@PermitAll
public class AuthorizationGroupResource implements IGraphQLResource {

	private AuthorizationGroupDao dao;

	public AuthorizationGroupResource(AnetObjectEngine engine) {
		this.dao = engine.getAuthorizationGroupDao();
	}

	@GET
	@GraphQLFetcher
	@Path("/")
	public AuthorizationGroupList getAll(@DefaultValue("0") @QueryParam("pageNum") int pageNum, @DefaultValue("100") @QueryParam("pageSize") int pageSize) {
		return dao.getAll(pageNum, pageSize);
	}

	@GET
	@GraphQLFetcher
	@Path("/{id}")
	public AuthorizationGroup getById(@PathParam("id") int id) {
		final AuthorizationGroup t = dao.getById(id);
		if (t == null) {
			throw new WebApplicationException(Status.NOT_FOUND);
		}
		return t;
	}

	@POST
	@GraphQLFetcher
	@Path("/search")
	public AuthorizationGroupList search(@GraphQLParam("query") AuthorizationGroupSearchQuery query) {
		return dao.search(query);
	}

	@GET
	@Path("/search")
	public AuthorizationGroupList search(@Context HttpServletRequest request) {
		return search(ResponseUtils.convertParamsToBean(request, AuthorizationGroupSearchQuery.class));
	}

	@POST
	@Path("/new")
	@RolesAllowed("ADMINISTRATOR")
	public AuthorizationGroup createNewAuthorizationGroup(@Auth Person user, AuthorizationGroup t) {
		if (t.getName() == null || t.getName().trim().length() == 0) {
			throw new WebApplicationException("AuthorizationGroup name must not be empty", Status.BAD_REQUEST);
		}
		t = dao.insert(t);
		AnetAuditLogger.log("AuthorizationGroup {} created by {}", t, user);
		return t;

	}

	@POST
	@Path("/update")
	@RolesAllowed("ADMINISTRATOR")
	public Response updateAuthorizationGroup(@Auth Person user, AuthorizationGroup t) {
		int numRows = dao.update(t);
		AnetAuditLogger.log("AuthorizationGroup {} updated by {}", t, user);
		return (numRows == 1) ? Response.ok().build() : Response.status(Status.NOT_FOUND).build();
	}

	@Override
	public String getDescription() {
		return "AuthorizationGroups";
	}

	@Override
	public Class<AuthorizationGroup> getBeanClass() {
		return AuthorizationGroup.class;
	}

	@Override
	public Class<AuthorizationGroupList> getBeanListClass() {
		return AuthorizationGroupList.class;
	}

}
