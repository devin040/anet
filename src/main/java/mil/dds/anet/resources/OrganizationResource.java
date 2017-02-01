package mil.dds.anet.resources;

import java.util.List;

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

import com.codahale.metrics.annotation.Timed;

import io.dropwizard.auth.Auth;
import mil.dds.anet.AnetObjectEngine;
import mil.dds.anet.beans.ApprovalStep;
import mil.dds.anet.beans.Organization;
import mil.dds.anet.beans.Organization.OrganizationType;
import mil.dds.anet.beans.Person;
import mil.dds.anet.beans.Poam;
import mil.dds.anet.beans.search.OrganizationSearchQuery;
import mil.dds.anet.database.OrganizationDao;
import mil.dds.anet.graphql.GraphQLFetcher;
import mil.dds.anet.graphql.GraphQLParam;
import mil.dds.anet.graphql.IGraphQLResource;
import mil.dds.anet.utils.AnetAuditLogger;
import mil.dds.anet.utils.AuthUtils;
import mil.dds.anet.utils.DaoUtils;
import mil.dds.anet.utils.ResponseUtils;
import mil.dds.anet.utils.Utils;

@Path("/api/organizations")
@Produces(MediaType.APPLICATION_JSON)
@PermitAll
public class OrganizationResource implements IGraphQLResource {

	private OrganizationDao dao;
	private AnetObjectEngine engine;
	
	public OrganizationResource(AnetObjectEngine engine) {
		this.dao = engine.getOrganizationDao(); 
		this.engine = engine;
	}
	
	@Override
	public Class<Organization> getBeanClass() { return Organization.class; }
	
	@Override
	public String getDescription() { return "Organizations"; } 
	
	@GET
	@Timed
	@GraphQLFetcher
	@Path("/")
	public List<Organization> getAllOrgs(@DefaultValue("0") @QueryParam("pageNum") Integer pageNum, @DefaultValue("100") @QueryParam("pageSize") Integer pageSize) {
		return dao.getAll(pageNum, pageSize);
	} 

	@GET
	@Timed
	@GraphQLFetcher
	@Path("/topLevel")
	public List<Organization> getTopLevelOrgs(@QueryParam("type") OrganizationType type) { 
		return dao.getTopLevelOrgs(type);
	}
	
	@POST
	@Timed
	@Path("/new")
	@RolesAllowed("ADMINISTRATOR")
	public Organization createNewOrganization(Organization org, @Auth Person user) {
		AuthUtils.assertAdministrator(user); 
		Organization created = dao.insert(org);
		
		if (org.getPoams() != null) { 
			//Assign all of these poams to this organization. 
			for (Poam p : org.getPoams()) { 
				engine.getPoamDao().setResponsibleOrgForPoam(p, created);
			}
		}
		if (org.getApprovalSteps() != null) { 
			//Create the approval steps 
			for (ApprovalStep step : org.getApprovalSteps()) { 
				step.setAdvisorOrganizationId(created.getId());
				engine.getApprovalStepDao().insertAtEnd(step);
			}
		}
		
		AnetAuditLogger.log("Organization {} created by {}", org, user);
		return created; 
	}
	
	@GET
	@Timed
	@GraphQLFetcher
	@Path("/{id}")
	public Organization getById(@PathParam("id") int id) {
		return dao.getById(id);
	}
	
	@POST
	@Timed
	@Path("/update")
	@RolesAllowed("SUPER_USER")
	public Response updateOrganization(Organization org, @Auth Person user) { 
		//Verify correct Organization 
		AuthUtils.assertSuperUserForOrg(user, org);
		
		int numRows = dao.update(org);
		
		if (org.getPoams() != null || org.getApprovalSteps() != null) {
			Organization existing = dao.getById(org.getId());
			
			if (org.getPoams() != null) {
				Utils.addRemoveElementsById(existing.loadPoams(), org.getPoams(), 
						newPoam -> engine.getPoamDao().setResponsibleOrgForPoam(newPoam, existing), 
						oldPoamId -> engine.getPoamDao().setResponsibleOrgForPoam(Poam.createWithId(oldPoamId), null));
			}
			if (org.getApprovalSteps() != null) {
				List<ApprovalStep> existingSteps = existing.loadApprovalSteps();
				// for each step we were given
				for (ApprovalStep step : org.getApprovalSteps()) {
					step.setAdvisorOrganizationId(org.getId());
					if (step.getId() != null) { 
						// if it has an ID then it already exists, so check the group to update name or members. 
						ApprovalStep existingStep = Utils.getById(existingSteps, step.getId());
						ApprovalStepResource.updateStep(step, existingStep);
					} else {
						step = engine.getApprovalStepDao().insert(step);
					}
				}
				
				//Fix all the orders.
				//I know this is is inefficient in that it pushes an update to every step
				//TODO: make this more efficient. 
				for (int i=0;i<org.getApprovalSteps().size();i++) { 
					ApprovalStep curr = org.getApprovalSteps().get(i);
					ApprovalStep next = (i == (org.getApprovalSteps().size() -1)) ? null : org.getApprovalSteps().get(i+1);
					curr.setNextStepId(DaoUtils.getId(next));
					engine.getApprovalStepDao().update(curr);
				}
			}
		}
		
		AnetAuditLogger.log("Organization {} edited by {}", org, user);
		return (numRows == 1) ? Response.ok().build() : Response.status(Status.NOT_FOUND).build();
	}
	

	
	@POST
	@Timed
	@GraphQLFetcher
	@Path("/search")
	public List<Organization> search(@GraphQLParam("query") OrganizationSearchQuery query ) {
		return dao.search(query);
	}
	
	@GET
	@Timed
	@Path("/search")
	public List<Organization> search(@Context HttpServletRequest request) {
		try { 
			return search(ResponseUtils.convertParamsToBean(request, OrganizationSearchQuery.class));
		} catch (IllegalArgumentException e) { 
			throw new WebApplicationException(e.getMessage(), e.getCause(), Status.BAD_REQUEST);
		}
	}
	
	@GET
	@Timed
	@Path("/{id}/children")
	public List<Organization> getChildren(@PathParam("id") Integer id) { 
		return dao.getByParentOrgId(id, null);
	}
	
	@GET
	@Timed
	@Path("/{id}/poams")
	public List<Poam> getPoams(@PathParam("id") Integer orgId) { 
		return AnetObjectEngine.getInstance().getPoamDao().getPoamsByOrganizationId(orgId);
	}
}
