package ru.fusionsoft.dbgit.adapters;

import java.io.OutputStream;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import com.axiomalaska.jdbc.NamedParameterPreparedStatement;

import ru.fusionsoft.dbgit.core.DBGitConfig;
import ru.fusionsoft.dbgit.core.ExceptionDBGitRestore;
import ru.fusionsoft.dbgit.core.ExceptionDBGitRunTime;
import ru.fusionsoft.dbgit.dbobjects.DBSequence;
import ru.fusionsoft.dbgit.dbobjects.DBTableField;
import ru.fusionsoft.dbgit.meta.IMapMetaObject;
import ru.fusionsoft.dbgit.meta.IMetaObject;
import ru.fusionsoft.dbgit.meta.MetaSequence;
import ru.fusionsoft.dbgit.meta.MetaSql;
import ru.fusionsoft.dbgit.meta.MetaTable;
import ru.fusionsoft.dbgit.meta.TreeMapMetaObject;
import ru.fusionsoft.dbgit.utils.ConsoleWriter;
import ru.fusionsoft.dbgit.utils.StringProperties;

/**
 * <div class="en">The base adapter adapter class. Contains general solutions independent of a particular database</div>
 * <div class="ru">Базовый класс адаптера БД. Содержит общие решения, независимые от конкретной БД</div>
 * 
 * @author mikle
 *
 */
public abstract class DBAdapter implements IDBAdapter {
	protected Connection connect;
	protected Boolean isExec = true;
	protected OutputStream streamSql = null;
	
	@Override
	public void setConnection(Connection conn) {
		connect = conn;
	}
	
	@Override
	public Connection getConnection() {
		return connect;
	} 
	
	@Override
	public void setDumpSqlCommand(OutputStream stream, Boolean isExec) {
		this.streamSql = stream;
		this.isExec = isExec;
	}
	
	@Override
	public OutputStream getStreamOutputSqlCommand() {
		return streamSql;
	}
	
	@Override
	public Boolean isExecSql() {
		return isExec;
	}
	
	@Override
	public void restoreDataBase(IMapMetaObject updateObjs) throws Exception {
		Connection connect = getConnection();
		IMapMetaObject currStep = updateObjs;
		try {
			List<String> createdSchemas = new ArrayList<String>();
			List<String> createdRoles = new ArrayList<String>();

			for (IMetaObject obj : updateObjs.values()) {
				Integer step = 0;

				boolean res = false;
				Timestamp timestampBefore = new Timestamp(System.currentTimeMillis());
				
				while (!res) {	
					if (obj.getDbType() == null) {
						ConsoleWriter.println("Can't get db type of object");
						break;
					}
					
					if (getFactoryRestore().getAdapterRestore(obj.getType(), this) == null ||
							!obj.getDbType().equals(getDbType()))
						break;
						
					res = getFactoryRestore().getAdapterRestore(obj.getType(), this).restoreMetaObject(obj, step);
					step++;

					if (step > 100) {
						throw new Exception("Error restore objects.... restoreMetaObject must return true if object restore.");
					}
				}
    			Timestamp timestampAfter = new Timestamp(System.currentTimeMillis());
    			Long diff = timestampAfter.getTime() - timestampBefore.getTime();
    			ConsoleWriter.println("(" + diff + " ms)");
			}

			connect.commit();
		} catch (Exception e) {
			connect.rollback();
			throw new ExceptionDBGitRestore("Restore objects error", e);
		} finally {
			//connect.setAutoCommit(false);
		} 
		
	}
	
	@Override
	public void deleteDataBase(IMapMetaObject deleteObjs)  throws Exception {
		Connection connect = getConnection();
		try {
			//start transaction
			for (IMetaObject obj : deleteObjs.values()) {
				getFactoryRestore().getAdapterRestore(obj.getType(), this).removeMetaObject(obj);
			}
			connect.commit();
		} catch (Exception e) {
			connect.rollback();
			throw new ExceptionDBGitRestore("Remove objects error", e);
		} finally {
			//connect.setAutoCommit(false);
		} 

	}
	
	public String cleanString(String str) {
		String dt = str.replace("\r\n", "\n");
		while (dt.contains(" \n")) dt = dt.replace(" \n", "\n");
		dt = dt.replace("\t", "   ").trim();
		
		return dt;
	}
	
	public void rowToProperties(ResultSet rs, StringProperties properties) {
		try {
			for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
				if (rs.getString(i) == null) continue ;			
				
				properties.addChild(rs.getMetaData().getColumnName(i).toLowerCase(), cleanString(rs.getString(i)));
			}
		} catch(Exception e) {
			throw new ExceptionDBGitRunTime(e);
		}
	}
	
	private String getSchemaName(IMetaObject obj) {
		if (obj instanceof MetaSql)
			return ((MetaSql) obj).getSqlObject().getSchema();
		else if (obj instanceof MetaTable)
			return ((MetaTable) obj).getTable().getSchema();
		else if (obj instanceof MetaSequence)
			return ((MetaSequence) obj).getSequence().getSchema();
		else return null;
	}
	
	private String getOwnerName(IMetaObject obj) {
		if (obj instanceof MetaSql)
			return ((MetaSql) obj).getSqlObject().getOwner();
		else if (obj instanceof MetaTable)
			return ((MetaTable) obj).getTable().getOptions().get("owner").getData();
		else if (obj instanceof MetaSequence)
			return ((MetaSequence) obj).getSequence().getOptions().get("owner").getData();
		else return null;		
	}
}
