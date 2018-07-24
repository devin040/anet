package mil.dds.anet.search.sqlite;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.skife.jdbi.v2.Handle;

import jersey.repackaged.com.google.common.base.Joiner;
import mil.dds.anet.beans.lists.AbstractAnetBeanList.PositionList;
import mil.dds.anet.beans.search.ISearchQuery.SortOrder;
import mil.dds.anet.beans.search.PositionSearchQuery;
import mil.dds.anet.beans.search.PositionSearchQuery.PositionSearchSortBy;
import mil.dds.anet.database.PositionDao;
import mil.dds.anet.database.mappers.PositionMapper;
import mil.dds.anet.search.IPositionSearcher;
import mil.dds.anet.utils.DaoUtils;
import mil.dds.anet.utils.Utils;

public class SqlitePositionSearcher implements IPositionSearcher {
	
	@Override
	public PositionList runSearch(PositionSearchQuery query, Handle dbHandle) {
		StringBuilder sql = new StringBuilder("/* SqlitePositionSearch */ SELECT " + PositionDao.POSITIONS_FIELDS 
				+ " FROM positions WHERE positions.uuid IN (SELECT positions.uuid FROM positions ");
		Map<String,Object> sqlArgs = new HashMap<String,Object>();
		String commonTableExpression = null;
		
		if (query.getMatchPersonName() != null && query.getMatchPersonName()) { 
			sql.append(" LEFT JOIN people ON positions.\"currentPersonUuid\" = people.uuid ");
		}
		
		sql.append(" WHERE ");
		List<String> whereClauses = new LinkedList<String>();
		PositionList result = new PositionList();
		result.setPageNum(query.getPageNum());
		result.setPageSize(query.getPageSize());
		
		final String text = query.getText();
		final boolean doFullTextSearch = (text != null && !text.trim().isEmpty());
		if (doFullTextSearch) {
			if (query.getMatchPersonName() != null && query.getMatchPersonName()) { 
				whereClauses.add("((positions.name LIKE '%' || :text || '%' "
						+ "OR positions.code LIKE '%' || :text || '%') "
						+ "OR (people.name LIKE '%' || :text || '%'))");
			} else { 
				whereClauses.add("(name LIKE '%' || :text || '%' OR code LIKE '%' || :text || '%')");
			}
			
			sqlArgs.put("text", Utils.getSqliteFullTextQuery(text));
		}
		
		if (query.getType() != null) { 
			List<String> argNames = new LinkedList<String>();
			for (int i = 0;i < query.getType().size();i++) { 
				argNames.add(":state" + i);
				sqlArgs.put("state" + i, DaoUtils.getEnumId(query.getType().get(i)));
			}
			whereClauses.add("positions.type IN (" + Joiner.on(", ").join(argNames) + ")");
		}
		
		if (query.getOrganizationUuid() != null) {
			if (query.getIncludeChildrenOrgs() != null && query.getIncludeChildrenOrgs()) { 
				commonTableExpression = "WITH RECURSIVE parent_orgs(uuid) AS ( "
						+ "SELECT uuid FROM organizations WHERE uuid = :orgUuid "
					+ "UNION ALL "
						+ "SELECT o.uuid from parent_orgs po, organizations o WHERE o.\"parentOrgUuid\" = po.uuid "
					+ ") ";
				whereClauses.add(" positions.\"organizationUuid\" IN (SELECT uuid from parent_orgs)");
			} else { 
				whereClauses.add("positions.\"organizationUuid\" = :orgUuid");
			}
			sqlArgs.put("orgUuid", query.getOrganizationUuid());
		}
		
		if (query.getIsFilled() != null) {
			if (query.getIsFilled()) { 
				whereClauses.add("positions.\"currentPersonUuid\" IS NOT NULL");
			} else { 
				whereClauses.add("positions.\"currentPersonUuid\" IS NULL");
			}
		}
		
		if (query.getLocationUuid() != null) {
			whereClauses.add("positions.\"locationUuid\" = :locationUuid");
			sqlArgs.put("locationUuid", query.getLocationUuid());
		}
		
		if (query.getStatus() != null) { 
			whereClauses.add("positions.status = :status");
			sqlArgs.put("status", DaoUtils.getEnumId(query.getStatus()));
		}
		
		if (whereClauses.size() == 0) { return result; }
		
		sql.append(Joiner.on(" AND ").join(whereClauses));
		
		//Sort Ordering
		sql.append(" ORDER BY ");
		if (query.getSortBy() == null) { query.setSortBy(PositionSearchSortBy.NAME); }
		switch (query.getSortBy()) {
			case CODE:
				sql.append("positions.code");
				break;
			case CREATED_AT:
				sql.append("positions.createdAt");
				break;
			case NAME:
			default:
				sql.append("positions.name");
				break;
		}
		
		if (query.getSortOrder() == null) { query.setSortOrder(SortOrder.ASC); }
		switch (query.getSortOrder()) {
			case ASC:
				sql.append(" ASC ");
				break;
			case DESC:
			default:
				sql.append(" DESC ");
				break;
		}
		
		sql.append(" LIMIT :limit OFFSET :offset)");
		
		if (commonTableExpression != null) { 
			sql.insert(0, commonTableExpression);
		}
		
		result.setList(dbHandle.createQuery(sql.toString())
			.bindFromMap(sqlArgs)
			.bind("offset", query.getPageSize() * query.getPageNum())
			.bind("limit", query.getPageSize())
			.map(new PositionMapper())
			.list());
		result.setTotalCount(result.getList().size()); // Sqlite cannot do true total counts, so this is a crutch.
		return result;
	}
}
