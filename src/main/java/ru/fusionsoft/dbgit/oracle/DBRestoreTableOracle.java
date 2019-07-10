package ru.fusionsoft.dbgit.oracle;

import java.sql.Connection;
import java.sql.ResultSet;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;
import com.google.common.collect.MapDifference.ValueDifference;

import ru.fusionsoft.dbgit.adapters.DBRestoreAdapter;
import ru.fusionsoft.dbgit.adapters.IDBAdapter;
import ru.fusionsoft.dbgit.core.ExceptionDBGitRestore;
import ru.fusionsoft.dbgit.dbobjects.DBConstraint;
import ru.fusionsoft.dbgit.dbobjects.DBIndex;
import ru.fusionsoft.dbgit.dbobjects.DBTable;
import ru.fusionsoft.dbgit.dbobjects.DBTableField;
import ru.fusionsoft.dbgit.meta.IMetaObject;
import ru.fusionsoft.dbgit.meta.MetaTable;
import ru.fusionsoft.dbgit.statement.StatementLogging;
import ru.fusionsoft.dbgit.utils.ConsoleWriter;

public class DBRestoreTableOracle extends DBRestoreAdapter {

	@Override
	public boolean restoreMetaObject(IMetaObject obj, int step) throws Exception {
		if(Integer.valueOf(step).equals(0)) {
			restoreTableOracle(obj);
			return false;
		}
		/*if(Integer.valueOf(step).equals(1)) {
			restoreTableFieldsOracle(obj);
			return false;
		}*/
		if(Integer.valueOf(step).equals(1)) {
			restoreTableIndexesOracle(obj);
			return false;
		}
		if(Integer.valueOf(step).equals(2)) {
			restoreTableConstraintOracle(obj);
			return false;
		}
		return true;
	}
	
	public void restoreTableOracle(IMetaObject obj) throws Exception
	{
		IDBAdapter adapter = getAdapter();
		Connection connect = adapter.getConnection();
		StatementLogging st = new StatementLogging(connect, adapter.getStreamOutputSqlCommand(), adapter.isExecSql());
		try {
			if (obj instanceof MetaTable) {
				
				MetaTable restoreTable = (MetaTable)obj;	
				String schema = getPhisicalSchema(restoreTable.getTable().getSchema());
				String tblName = schema+"."+restoreTable.getTable().getName();
				
				ConsoleWriter.detailsPrint("Restoring table " + tblName + "\n", 1);

				Map<String, DBTable> tables = adapter.getTables(schema.toUpperCase());
				boolean exist = false;
				if(!(tables.isEmpty() || tables == null)) {
					for(DBTable table:tables.values()) {
						if(restoreTable.getTable().getName().equalsIgnoreCase(table.getName())){
							exist = true;
																									
						}						
					}
				}
				if(!exist){			
					ConsoleWriter.detailsPrint("Creating table...", 2);
					String ddl = "";
					if (restoreTable.getTable().getOptions().get("ddl") != null)
						ddl = restoreTable.getTable().getOptions().get("ddl").getData();
					else {
						DBTable table = restoreTable.getTable();
						
						ddl = "create table " + table.getSchema() + "." + table.getName() +
								" (" + restoreTable.getFields().values().stream()
									.map(field -> field.getName() + " " + field.getTypeSQL())
									.collect(Collectors.joining(", ")) + ")";
					}
					st.execute(ddl);	
					ConsoleWriter.detailsPrintlnGreen("OK");
				} else {
				//restore tabl fields
							Map<String, DBTableField> currentFileds = adapter.getTableFields(restoreTable.getTable().getSchema(), restoreTable.getTable().getName().toLowerCase());
							MapDifference<String, DBTableField> diffTableFields = Maps.difference(restoreTable.getFields(),currentFileds);
							
							if(!diffTableFields.entriesOnlyOnLeft().isEmpty()){
								ConsoleWriter.detailsPrint("Adding columns...", 2);
								for(DBTableField tblField:diffTableFields.entriesOnlyOnLeft().values()) {
										st.execute("alter table "+ tblName +" add " + tblField.getName()  + " " + tblField.getTypeSQL());
								}								
								ConsoleWriter.detailsPrintlnGreen("OK");
							}
							
							if(!diffTableFields.entriesOnlyOnRight().isEmpty()) {
								ConsoleWriter.detailsPrint("Dropping columns...", 2);
								for(DBTableField tblField:diffTableFields.entriesOnlyOnRight().values()) {
									st.execute("alter table "+ tblName +" drop column "+ tblField.getName());
								}								
								ConsoleWriter.detailsPrintlnGreen("OK");
							}
							
							if(!diffTableFields.entriesDiffering().isEmpty()) {
								ConsoleWriter.detailsPrint("Modifying columns...", 2);

								for(ValueDifference<DBTableField> tblField:diffTableFields.entriesDiffering().values()) {
									if(!tblField.leftValue().getName().equals(tblField.rightValue().getName())) {
										st.execute("alter table "+ tblName +" rename column "+ tblField.rightValue().getName() +" to "+ tblField.leftValue().getName());
									}
																	
									if(!tblField.leftValue().getTypeSQL().equals(tblField.rightValue().getTypeSQL())) {
										st.execute("alter table "+ tblName +" modify "+ tblField.leftValue().getName() +" "+ tblField.leftValue().getTypeSQL());
									}
								}		
								ConsoleWriter.detailsPrintlnGreen("OK");
							}						
				}
				ResultSet rs = st.executeQuery("SELECT COUNT(cons.constraint_name) constraintscount \n" + 
						"FROM all_constraints cons \n" + 
						"WHERE owner = '" + schema + "' and table_name = '" + tblName+ "' and constraint_name not like 'SYS%' and cons.constraint_type = 'P'");
				rs.next();
				Integer constraintsCount = Integer.valueOf(rs.getString("constraintscount"));
				if(constraintsCount.intValue()>0) {
					removeTableConstraintsOracle(obj);
				}
				// set primary key
				for(DBConstraint tableconst: restoreTable.getConstraints().values()) {
					if(tableconst.getConstraintType().equals("p")) {
						ConsoleWriter.detailsPrint("Adding PK...", 2);
						
						if (tableconst.getConstraintDef().toLowerCase().startsWith("alter table")) 
							st.execute(tableconst.getConstraintDef());
						else if (tableconst.getConstraintDef().toLowerCase().startsWith("primary key"))
							st.execute("alter table "+ tblName +" add constraint PK_"+ tableconst.getName() + tableconst.getConstraintDef());
						ConsoleWriter.detailsPrintlnGreen("OK");
						break;
					}
				}									
			}
			else
			{
				throw new ExceptionDBGitRestore("Error restore: Unable to restore Table.");
			}						
		}
		catch (Exception e) {
			throw new ExceptionDBGitRestore("Error restore "+obj.getName(), e);
		} finally {
			st.close();
		}			
	}
	
	public void restoreTableFieldsOracle(IMetaObject obj) throws Exception
	{
		IDBAdapter adapter = getAdapter();
		Connection connect = adapter.getConnection();
		StatementLogging st = new StatementLogging(connect, adapter.getStreamOutputSqlCommand(), adapter.isExecSql());
		try {
			if (obj instanceof MetaTable) {
				MetaTable restoreTable = (MetaTable)obj;	
				String schema = getPhisicalSchema(restoreTable.getTable().getSchema());
				String tblName = schema+"."+restoreTable.getTable().getName();
				Map<String, DBTable> tables = adapter.getTables(schema);
				boolean exist = false;
				if(!(tables.isEmpty() || tables == null)) {
					for(DBTable table:tables.values()) {
						if(restoreTable.getTable().getName().equals(table.getName())){
							exist = true;
																									
						}						
					}
				}
				if(!exist){								
					st.execute(restoreTable.getTable().getOptions().get("ddl").getData());			
				}
				//restore tabl fields
							Map<String, DBTableField> currentFileds = adapter.getTableFields(restoreTable.getTable().getSchema(), restoreTable.getTable().getName());
							MapDifference<String, DBTableField> diffTableFields = Maps.difference(restoreTable.getFields(),currentFileds);
							
							if(!diffTableFields.entriesOnlyOnLeft().isEmpty()){
								for(DBTableField tblField:diffTableFields.entriesOnlyOnLeft().values()) {
										st.execute("alter table "+ tblName +" add " + tblField.getName()  + " " + tblField.getTypeSQL());
								}								
							}
							
							if(!diffTableFields.entriesOnlyOnRight().isEmpty()) {
								for(DBTableField tblField:diffTableFields.entriesOnlyOnRight().values()) {
									st.execute("alter table "+ tblName +" drop column "+ tblField.getName());
								}								
							}
							
							if(!diffTableFields.entriesDiffering().isEmpty()) {						
								for(ValueDifference<DBTableField> tblField:diffTableFields.entriesDiffering().values()) {
									if(!tblField.leftValue().getName().equals(tblField.rightValue().getName())) {
										st.execute("alter table "+ tblName +" rename column "+ tblField.rightValue().getName() +" to "+ tblField.leftValue().getName());
									}
																	
									if(!tblField.leftValue().getTypeSQL().equals(tblField.rightValue().getTypeSQL())) {
										st.execute("alter table "+ tblName +" modify "+ tblField.leftValue().getName() +" "+ tblField.leftValue().getTypeSQL());
									}
								}								
							}						
						
				ResultSet rs = st.executeQuery("SELECT COUNT(cons.constraint_name)\n" + 
						"FROM all_constraints cons \n" + 
						"WHERE owner = '" + schema + "' and table_name = '" + tblName+ "' and constraint_name not like 'SYS%' and cons.constraint_type = 'P'");
				rs.next();
				Integer constraintsCount = Integer.valueOf(rs.getString("constraintscount"));
				if(constraintsCount.intValue()>0) {
					removeTableConstraintsOracle(obj);
				}
				// set primary key
				for(DBConstraint tableconst: restoreTable.getConstraints().values()) {
					if(tableconst.getConstraintType().equals("p")) {
						st.execute(restoreTable.getConstraints().get("constraintDef").getConstraintDef().toString());
						//st.execute("alter table "+ tblName +" add constraint PK_"+ tableconst.getName() + " primary key ("+tableconst.getName() + ")");
						break;
					}
				}									
			}
			else
			{
				throw new ExceptionDBGitRestore("Error restore: Unable to restore Table.");
			}						
		}
		catch (Exception e) {
			throw new ExceptionDBGitRestore("Error restore "+obj.getName(), e);
		} finally {
			st.close();
		}			
	}
	
	public void restoreTableIndexesOracle(IMetaObject obj) throws Exception
	{
		IDBAdapter adapter = getAdapter();
		Connection connect = adapter.getConnection();
		StatementLogging st = new StatementLogging(connect, adapter.getStreamOutputSqlCommand(), adapter.isExecSql());
		
		ConsoleWriter.detailsPrint("Restore indexes for table " + obj.getName() + "...", 1);
		try {
			if (obj instanceof MetaTable) {
				MetaTable restoreTable = (MetaTable)obj;	
				String schema = getPhisicalSchema(restoreTable.getTable().getSchema());									
				Map<String, DBTable> tables = adapter.getTables(schema);
				boolean exist = false;
				if(!(tables.isEmpty() || tables == null)) {
					for(DBTable table:tables.values()) {
						if(restoreTable.getTable().getName().equals(table.getName())){
							exist = true;
							Map<String, DBIndex> currentIndexes = adapter.getIndexes(table.getSchema(), table.getName());
							MapDifference<String, DBIndex> diffInd = Maps.difference(restoreTable.getIndexes(), currentIndexes);
							if(!diffInd.entriesOnlyOnLeft().isEmpty()) {
								for(DBIndex ind:diffInd.entriesOnlyOnLeft().values()) {
									st.execute(ind.getSql());
								}								
							}
							if(!diffInd.entriesOnlyOnRight().isEmpty()) {
								for(DBIndex ind:diffInd.entriesOnlyOnRight().values()) {
									st.execute("drop index "+schema+"."+ind.getName());
								}								
							}
							
							//not fact, will check in future										
						}						
					}
				}
				if(!exist){								
					for(DBIndex ind:restoreTable.getIndexes().values()) {						
						st.execute(ind.getSql());						
					}
				}
				ConsoleWriter.detailsPrintlnGreen("OK");
			}
			else
			{
				ConsoleWriter.detailsPrintlnRed("FAIL");
				throw new ExceptionDBGitRestore("Error restore: Unable to restore TableIndexes.");
			}						
		}
		catch (Exception e) {
			ConsoleWriter.detailsPrintlnRed("FAIL");
			throw new ExceptionDBGitRestore("Error restore "+obj.getName(), e);
		} finally {
			st.close();
		}			
	}
	
	public void restoreTableConstraintOracle(IMetaObject obj) throws Exception {
		IDBAdapter adapter = getAdapter();
		Connection connect = adapter.getConnection();
		StatementLogging st = new StatementLogging(connect, adapter.getStreamOutputSqlCommand(), adapter.isExecSql());
		ConsoleWriter.detailsPrint("Restore constraints for table " + obj.getName() + "...", 1);
		try {
			if (obj instanceof MetaTable) {
				MetaTable restoreTable = (MetaTable)obj;
				String schema = getPhisicalSchema(restoreTable.getTable().getSchema());
				for(DBConstraint constrs :restoreTable.getConstraints().values()) {
					if(!constrs.getConstraintType().equalsIgnoreCase("P")) {				
						//String tblName = schema+"."+restoreTable.getTable().getName();
						
						st.execute(constrs.getConstraintDef().toString());
					}
				}
				ConsoleWriter.detailsPrintlnGreen("OK");
			}
			else
			{
				ConsoleWriter.detailsPrintlnRed("FAIL");
				throw new ExceptionDBGitRestore("Error restore: Unable to restore TableConstraints.");
			}			
		}
		catch (Exception e) {
			ConsoleWriter.detailsPrintlnRed("FAIL");
			throw new ExceptionDBGitRestore("Error restore "+obj.getName(), e);
		} finally {
			st.close();
		}	
	}
	
	public void removeTableConstraintsOracle(IMetaObject obj) throws Exception {		
		IDBAdapter adapter = getAdapter();
		Connection connect = adapter.getConnection();
		StatementLogging st = new StatementLogging(connect, adapter.getStreamOutputSqlCommand(), adapter.isExecSql());
		try {			
			if (obj instanceof MetaTable) {
				MetaTable table = (MetaTable)obj;
				String schema = getPhisicalSchema(table.getTable().getSchema());
				
				Map<String, DBConstraint> constraints = table.getConstraints();
				for(DBConstraint constrs :constraints.values()) {
				st.execute("alter table " + table.getTable().getName() + " drop constraint " + constrs.getName());
				}
				//}	
			}
			else
			{
				throw new ExceptionDBGitRestore("Error restore: Unable to remove TableConstraints.");
			}	
		}
		catch(Exception e) {
			throw new ExceptionDBGitRestore("Error restore "+obj.getName(), e);
		}		
	}
	
	/*public void removeIndexesOracle(IMetaObject obj) throws Exception {		
		
	}*/
	
	public void removeMetaObject(IMetaObject obj) throws Exception {
		IDBAdapter adapter = getAdapter();
		Connection connect = adapter.getConnection();
		StatementLogging st = new StatementLogging(connect, adapter.getStreamOutputSqlCommand(), adapter.isExecSql());
		
		try {
			
			MetaTable tblMeta = (MetaTable)obj;
			DBTable tbl = tblMeta.getTable();
			//String schema = getPhisicalSchema(tbl.getSchema());
			
			st.execute("DROP TABLE " +tbl.getName());
		
			// TODO Auto-generated method stub
		} catch (Exception e) {
			throw new ExceptionDBGitRestore("Error remove "+obj.getName(), e);
		} finally {
			st.close();
		}
	}

}
