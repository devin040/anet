package mil.dds.anet.database.mappers;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

import org.joda.time.DateTime;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import mil.dds.anet.beans.Organization;
import mil.dds.anet.beans.Task;
import mil.dds.anet.beans.Task.TaskStatus;

public class TaskMapper implements ResultSetMapper<Task> {

	@Override
	public Task map(int index, ResultSet r, StatementContext ctx) throws SQLException {
		Task p = new Task();
		p.setId(r.getInt("id"));
		p.setLongName(r.getString("longName"));
		p.setShortName(r.getString("shortName"));
		p.setCategory(r.getString("category"));
		p.setCustomField(r.getString("customField"));
		p.setCustomFieldEnum1(r.getString("customFieldEnum1"));
		p.setCustomFieldEnum2(r.getString("customFieldEnum2"));

		Timestamp plannedCompletion = r.getTimestamp("plannedCompletion");
		if (plannedCompletion != null) {
			p.setPlannedCompletion(new DateTime(plannedCompletion));
		}
		
		Timestamp projectedCompletion = r.getTimestamp("projectedCompletion");
		if (projectedCompletion != null) {
			p.setProjectedCompletion(new DateTime(projectedCompletion));
		}

		p.setStatus(MapperUtils.getEnumIdx(r, "status", TaskStatus.class));

		Integer customFieldRef1Id = MapperUtils.getInteger(r, "customFieldRef1Id");
		if (customFieldRef1Id != null) {
			p.setCustomFieldRef1(Task.createWithId(customFieldRef1Id));
		}
		
		Integer responsibleOrgId = MapperUtils.getInteger(r, "organizationId");
		if (responsibleOrgId != null) { 
			p.setResponsibleOrg(Organization.createWithId(responsibleOrgId));
		}
		
		p.setCreatedAt(new DateTime(r.getTimestamp("createdAt")));
		p.setUpdatedAt(new DateTime(r.getTimestamp("updatedAt")));
		
		if (MapperUtils.containsColumnNamed(r, "totalCount")) { 
			ctx.setAttribute("totalCount", r.getInt("totalCount"));
		}
		
		return p;
	}

	
}
