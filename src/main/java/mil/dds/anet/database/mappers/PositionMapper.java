package mil.dds.anet.database.mappers;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import mil.dds.anet.beans.Location;
import mil.dds.anet.beans.Organization;
import mil.dds.anet.beans.Person;
import mil.dds.anet.beans.Position;
import mil.dds.anet.beans.Position.PositionStatus;
import mil.dds.anet.beans.Position.PositionType;
import mil.dds.anet.utils.DaoUtils;
import mil.dds.anet.views.AbstractAnetBean.LoadLevel;

public class PositionMapper implements ResultSetMapper<Position> {

	@Override
	public Position map(int index, ResultSet rs, StatementContext ctx) throws SQLException {
		//This hits when we do a join but there's no Billet record. 
		if (rs.getObject("positions_uuid") == null) { return null; }
		
		Position p = fillInFields(new Position(), rs);
		
		if (MapperUtils.containsColumnNamed(rs, "totalCount")) { 
			ctx.setAttribute("totalCount", rs.getInt("totalCount"));
		}
		
		if (MapperUtils.containsColumnNamed(rs, "people_uuid")) {
			PersonMapper.fillInFields(p.getPerson(), rs);
		}
		return p;
	}
	
	public static Position fillInFields(Position p, ResultSet rs)  throws SQLException { 
		DaoUtils.setCommonBeanFields(p, rs, "positions");
		p.setName(rs.getString("positions_name"));
		p.setCode(rs.getString("positions_code"));
		p.setType(MapperUtils.getEnumIdx(rs, "positions_type", PositionType.class));
		p.setStatus(MapperUtils.getEnumIdx(rs, "positions_status", PositionStatus.class));

		String orgUuid = rs.getString("positions_organizationUuid");
		if (orgUuid != null) {
			p.setOrganization(Organization.createWithUuid(orgUuid));
		}
		String personUuid = rs.getString("positions_currentPersonUuid");
		if (personUuid != null) {
			p.setPerson(Person.createWithUuid(personUuid));
		}
		
		String locationUuid = rs.getString("positions_locationUuid");
		if (locationUuid != null) {
			p.setLocation(Location.createWithUuid(locationUuid));
		}
		
		p.setLoadLevel(LoadLevel.PROPERTIES);
		return p;
	}

}
