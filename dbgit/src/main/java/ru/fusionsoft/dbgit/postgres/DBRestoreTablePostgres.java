package ru.fusionsoft.dbgit.postgres;

import java.sql.Connection;
import java.sql.ResultSet;
import java.util.Map;
import ru.fusionsoft.dbgit.adapters.DBRestoreAdapter;
import ru.fusionsoft.dbgit.adapters.IDBAdapter;
import ru.fusionsoft.dbgit.core.ExceptionDBGitRestore;
import ru.fusionsoft.dbgit.core.ExceptionDBGitRunTime;
import ru.fusionsoft.dbgit.dbobjects.DBConstraint;
import ru.fusionsoft.dbgit.dbobjects.DBIndex;
import ru.fusionsoft.dbgit.dbobjects.DBTable;
import ru.fusionsoft.dbgit.dbobjects.DBTableField;
import ru.fusionsoft.dbgit.meta.IMetaObject;
import ru.fusionsoft.dbgit.meta.MetaTable;
import ru.fusionsoft.dbgit.statement.StatementLogging;
import ru.fusionsoft.dbgit.utils.ConsoleWriter;

import com.axiomalaska.jdbc.NamedParameterPreparedStatement;
import com.google.common.collect.MapDifference;
import com.google.common.collect.MapDifference.ValueDifference;
import com.google.common.collect.Maps;
public class DBRestoreTablePostgres extends DBRestoreAdapter {

	@Override
	public boolean restoreMetaObject(IMetaObject obj, int step) throws Exception {
		if(Integer.valueOf(step).equals(0)) {
			restoreTablePostgres(obj);
			return false;
		}
		/*if(Integer.valueOf(step).equals(1)) {
			restoreTableFieldsPostgres(obj);
			return false;
		}*/
		if(Integer.valueOf(step).equals(1)) {
			restoreTableIndexesPostgres(obj);
			return false;
		}
		if(Integer.valueOf(step).equals(2)) {
			restoreTableConstraintPostgres(obj);
			return false;
		}
		return true;
	}
	
	public void restoreTablePostgres(IMetaObject obj) throws Exception
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
				
				Map<String, DBTable> tables = adapter.getTables(schema);
				boolean exist = false;
				if(!(tables.isEmpty() || tables == null)) {
					for(DBTable table:tables.values()) {
						if(restoreTable.getTable().getName().equals(table.getName())){
							exist = true;
							//Map<String, DBIndex> currentIndexes = adapter.getIndexes(restoreTable.getTable().getSchema(), restoreTable.getTable().getName());
							String owner = restoreTable.getTable().getOptions().get("tableowner").getData();
							if(!owner.equals(table.getOptions().get("tableowner").getData())) {
								st.execute("alter table "+ tblName + " owner to "+ owner);
							}
														
							if(restoreTable.getTable().getOptions().getChildren().containsKey("tablespace")) {
								String tablespace = restoreTable.getTable().getOptions().get("tablespace").getData();
								st.execute("alter table "+ tblName + " set tablespace "+ tablespace);
							}
							else if(table.getOptions().getChildren().containsKey("tablespace")) {
								st.execute("alter table "+ tblName + " set tablespace pg_default");
							}													
						}						
					}
				}
				if(!exist){								
					ConsoleWriter.detailsPrint("Creating table...", 2);
					if(restoreTable.getTable().getOptions().getChildren().containsKey("tablespace")) {
						String tablespace = restoreTable.getTable().getOptions().get("tablespace").getData();
						String querry ="create table "+ tblName + "() tablespace "+ tablespace +";\n";
						querry+="alter table "+ tblName + " owner to "+ restoreTable.getTable().getOptions().get("tableowner").getData()+";";
						st.execute(querry);			
					}
					else {
						String querry = "create table "+ tblName + "()"+";\n";
						querry+="alter table "+ tblName + " owner to "+ restoreTable.getTable().getOptions().get("tableowner").getData()+";";
						st.execute(querry);	
					}				
					ConsoleWriter.detailsPrintlnGreen("OK");
				}
				//restore tabl fields
							Map<String, DBTableField> currentFileds = adapter.getTableFields(restoreTable.getTable().getSchema(), restoreTable.getTable().getName());
							MapDifference<String, DBTableField> diffTableFields = Maps.difference(restoreTable.getFields(),currentFileds);
							
							if(!diffTableFields.entriesOnlyOnLeft().isEmpty()){
								ConsoleWriter.detailsPrint("Adding columns...", 2);
								for(DBTableField tblField:diffTableFields.entriesOnlyOnLeft().values()) {
										String as = "alter table "+ tblName +" add column " + tblField.getName()  + " " + tblField.getTypeSQL();
										st.execute("alter table "+ tblName +" add column " + tblField.getName()  + " " + tblField.getTypeSQL());
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
										st.execute("alter table "+ tblName +" alter column "+ tblField.leftValue().getName() +" type "+ tblField.leftValue().getTypeSQL());
									}
								}		
								ConsoleWriter.detailsPrintlnGreen("OK");
							}						
						
				ResultSet rs = st.executeQuery("SELECT COUNT(*) as constraintscount\n" +
						"FROM pg_catalog.pg_constraint const JOIN pg_catalog.pg_class cl ON (const.conrelid=cl.oid) WHERE cl.relname = " + tblName);
				rs.next();
				Integer constraintsCount = Integer.valueOf(rs.getString("constraintscount"));
				if(constraintsCount.intValue()>0) {
					removeTableConstraintsPostgres(obj);
				}
				// set primary key
				for(DBConstraint tableconst: restoreTable.getConstraints().values()) {
					if(tableconst.getConstraintType().equals("p")) {
						ConsoleWriter.detailsPrint("Adding PK...", 2);
						st.execute("alter table "+ tblName +" add constraint "+ tableconst.getName() + " "+tableconst.getConstraintDef());
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
	public void restoreTableFieldsPostgres(IMetaObject obj) throws Exception
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
							Map<String, DBTableField> currentFileds = adapter.getTableFields(restoreTable.getTable().getSchema(), restoreTable.getTable().getName());
							MapDifference<String, DBTableField> diffTableFields = Maps.difference(restoreTable.getFields(),currentFileds);
							
							if(!diffTableFields.entriesOnlyOnLeft().isEmpty()){
								for(DBTableField tblField:diffTableFields.entriesOnlyOnLeft().values()) {
										String as = "alter table "+ tblName +" add column " + tblField.getName()  + " " + tblField.getTypeSQL();
										st.execute("alter table "+ tblName +" add column " + tblField.getName()  + " " + tblField.getTypeSQL());
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
										st.execute("alter table "+ tblName +" alter column "+ tblField.leftValue().getName() +" type "+ tblField.leftValue().getTypeSQL());
									}
								}								
							}						
						}						
					}
				}
				if(!exist){								
					for(DBTableField tblField:restoreTable.getFields().values()) {
							st.execute("alter table "+ tblName +" add column " + tblField.getName()  + " " + tblField.getTypeSQL());
					}
				}
				
				ResultSet rs = st.executeQuery("SELECT COUNT(*) as constraintscount FROM pg_catalog.pg_constraint r WHERE r.conrelid = '"+tblName+"'::regclass");
				rs.next();
				Integer constraintsCount = Integer.valueOf(rs.getString("constraintscount"));
				if(constraintsCount.intValue()>0) {
					removeTableConstraintsPostgres(obj);
				}
				// set primary key
				for(DBConstraint tableconst: restoreTable.getConstraints().values()) {
					if(tableconst.getConstraintType().equals("p")) {
						st.execute("alter table "+ tblName +" add constraint "+ tableconst.getName() + " "+tableconst.getConstraintDef());
						break;
					}
				}
			}
			else
			{
				throw new ExceptionDBGitRestore("Error restore: Unable to restore TableFields.");
			}						
		}
		catch (Exception e) {
			throw new ExceptionDBGitRestore("Error restore "+obj.getName(), e);
		} finally {
			st.close();
		}			
	}
	public void restoreTableIndexesPostgres(IMetaObject obj) throws Exception
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
									if(ind.getOptions().getChildren().containsKey("tablespace")) {
										st.execute(ind.getSql()+" tablespace "+ind.getOptions().get("tablespace").getData());
									}
									else {
										st.execute(ind.getSql());
									}
								}								
							}
							if(!diffInd.entriesOnlyOnRight().isEmpty()) {
								for(DBIndex ind:diffInd.entriesOnlyOnRight().values()) {
									st.execute("drop index "+schema+"."+ind.getName());
								}								
							}
							
							if(!diffInd.entriesDiffering().isEmpty()) {
								for(ValueDifference<DBIndex>  ind:diffInd.entriesDiffering().values()) {
									if(ind.leftValue().getOptions().getChildren().containsKey("tablespace")) {
										if(ind.rightValue().getOptions().getChildren().containsKey("tablespace") && !ind.leftValue().getOptions().get("tablespace").getData().equals(ind.rightValue().getOptions().get("tablespace").getData())) {
											st.execute("alter index "+schema+"."+ind.leftValue().getName() +" set tablespace "+ind.leftValue().getOptions().get("tablepace"));	
										}
																			
									}
									else if(ind.rightValue().getOptions().getChildren().containsKey("tablespace")) {
										st.execute("alter index "+schema+"."+ind.leftValue().getName() +" set tablespace pg_default");	
									}									
								}								
							}										
						}						
					}
				}
				if(!exist){								
					for(DBIndex ind:restoreTable.getIndexes().values()) {						
							if(ind.getOptions().getChildren().containsKey("tablespace")) {
								String as = ind.getSql()+" tablespace "+ind.getOptions().get("tablespace").getData();
								st.execute(ind.getSql()+" tablespace "+ind.getOptions().get("tablespace").getData());
							}
							else {						
								st.execute(ind.getSql());
							}						
					}
				}
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
			ConsoleWriter.detailsPrintlnGreen("OK");
			st.close();
		}			
	}
	public void restoreTableConstraintPostgres(IMetaObject obj) throws Exception {
		IDBAdapter adapter = getAdapter();
		Connection connect = adapter.getConnection();
		StatementLogging st = new StatementLogging(connect, adapter.getStreamOutputSqlCommand(), adapter.isExecSql());
		ConsoleWriter.detailsPrint("Restore constraints for table " + obj.getName() + "...", 1);
		try {
			if (obj instanceof MetaTable) {
				MetaTable restoreTable = (MetaTable)obj;
				String schema = getPhisicalSchema(restoreTable.getTable().getSchema());
				for(DBConstraint constrs :restoreTable.getConstraints().values()) {
					if(!constrs.getConstraintType().equals("p")) {				
					st.execute("alter table "+ schema+"."+restoreTable.getTable().getName() +" add constraint "+ constrs.getName() + " "+constrs.getConstraintDef());
					}
				}
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
			ConsoleWriter.detailsPrintlnGreen("OK");
			st.close();
		}			
	}
	public void removeTableConstraintsPostgres(IMetaObject obj) throws Exception {		
		IDBAdapter adapter = getAdapter();
		Connection connect = adapter.getConnection();
		StatementLogging st = new StatementLogging(connect, adapter.getStreamOutputSqlCommand(), adapter.isExecSql());
		try {			
			if (obj instanceof MetaTable) {
				MetaTable table = (MetaTable)obj;
				String schema = getPhisicalSchema(table.getTable().getSchema());
				//String s = "SELECT COUNT(*) as constraintscount FROM pg_catalog.pg_constraint r WHERE r.conrelid = '"+table.getTable().getSchema()+"."+table.getTable().getName()+"'::regclass";
				//ResultSet rs = st.executeQuery("SELECT COUNT(*) as constraintscount FROM pg_catalog.pg_constraint r WHERE r.conrelid = '"+table.getTable().getSchema()+"."+table.getTable().getName()+"'::regclass");
				//rs.next();
				//Integer constraintsCount = Integer.valueOf(rs.getString("constraintscount"));
				//if(constraintsCount.intValue()>0) {
				Map<String, DBConstraint> constraints = table.getConstraints();
				for(DBConstraint constrs :constraints.values()) {
				st.execute("alter table "+ schema+"."+table.getTable().getName() +" drop constraint "+constrs.getName());
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
	
	/*public void removeIndexesPostgres(IMetaObject obj) throws Exception {		
		IDBAdapter adapter = getAdapter();
		Connection connect = adapter.getConnection();
		StatementLogging st = new StatementLogging(connect, adapter.getStreamOutputSqlCommand(), adapter.isExecSql());
		try {			
			if (obj instanceof MetaTable) {
				MetaTable table = (MetaTable)obj;				
				Map<String, DBIndex> indexes = table.getIndexes();
				for(DBIndex index :indexes.values()) {
					st.execute("DROP INDEX IF EXISTS "+index.getName());
				}			
			}
			else
			{
				throw new ExceptionDBGitRestore("Error restore: Unable to remove TableIndexes.");
			}	
		}
		catch(Exception e) {
			throw new ExceptionDBGitRestore("Error restore "+obj.getName(), e);
		}		
	}*/
	
	public void removeMetaObject(IMetaObject obj) throws Exception {
		IDBAdapter adapter = getAdapter();
		Connection connect = adapter.getConnection();
		StatementLogging st = new StatementLogging(connect, adapter.getStreamOutputSqlCommand(), adapter.isExecSql());
		
		try {
			
			MetaTable tblMeta = (MetaTable)obj;
			DBTable tbl = tblMeta.getTable();
			String schema = getPhisicalSchema(tbl.getSchema());
			
			st.execute("DROP TABLE "+schema+"."+tbl.getName());
		
			// TODO Auto-generated method stub
		} catch (Exception e) {
			throw new ExceptionDBGitRestore("Error remove "+obj.getName(), e);
		} finally {
			st.close();
		}
	}

}
