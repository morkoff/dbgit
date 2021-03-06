package ru.fusionsoft.dbgit.postgres;

import java.sql.Connection;
import java.util.Map;

import ru.fusionsoft.dbgit.adapters.DBRestoreAdapter;
import ru.fusionsoft.dbgit.adapters.IDBAdapter;
import ru.fusionsoft.dbgit.core.ExceptionDBGitRestore;
import ru.fusionsoft.dbgit.dbobjects.DBFunction;
import ru.fusionsoft.dbgit.meta.IMetaObject;
import ru.fusionsoft.dbgit.meta.MetaFunction;
import ru.fusionsoft.dbgit.statement.StatementLogging;
import ru.fusionsoft.dbgit.utils.ConsoleWriter;

public class DBRestoreFunctionPostgres extends DBRestoreAdapter {
	
	@Override
	public boolean restoreMetaObject(IMetaObject obj, int step) throws Exception {
		IDBAdapter adapter = getAdapter();
		Connection connect = adapter.getConnection();
		StatementLogging st = new StatementLogging(connect, adapter.getStreamOutputSqlCommand(), adapter.isExecSql());
		ConsoleWriter.detailsPrint(lang.getValue("general", "restore", "restoreFnc").withParams(obj.getName()), 1);
		try {
			if (obj instanceof MetaFunction) {
				MetaFunction restoreFunction = (MetaFunction)obj;								
				Map<String, DBFunction> functions = adapter.getFunctions(restoreFunction.getSqlObject().getSchema());
				boolean exist = false;
				if(!(functions.isEmpty() || functions == null)) {
					for(DBFunction fnc:functions.values()) {
						if(restoreFunction.getSqlObject().getName().equals(fnc.getName())){
							exist = true;
							if(!restoreFunction.getSqlObject().getSql().equals(fnc.getSql())) {								
								st.execute(restoreFunction.getSqlObject().getSql());
							}
							if(!restoreFunction.getSqlObject().getOwner().equals(fnc.getOwner())) {									
								if(restoreFunction.getSqlObject().getOptions().get("arguments").getData() == null || restoreFunction.getSqlObject().getOptions().get("arguments").getData().isEmpty()) {									
									st.execute("ALTER FUNCTION "+restoreFunction.getSqlObject().getName() + "() OWNER TO " 
								+ restoreFunction.getSqlObject().getOwner());									
								}
								else {								
									st.execute("ALTER FUNCTION "+restoreFunction.getSqlObject().getName()+"("
								+ restoreFunction.getSqlObject().getOptions().get("arguments").getData() + ") OWNER TO " + restoreFunction.getSqlObject().getOwner());
								}								
							}						
							//TODO Восстановление привилегий							
						}
					}					
				}
				if(!exist){
					st.execute(restoreFunction.getSqlObject().getSql());
					//TODO Восстановление привилегий	
				}
			}
			else
			{
				ConsoleWriter.detailsPrintlnRed(lang.getValue("errors", "meta", "fail"));
				throw new ExceptionDBGitRestore(lang.getValue("errors", "restore", "objectRestoreError").withParams(obj.getName()));
			}			
		} catch (Exception e) {
			ConsoleWriter.detailsPrintlnRed(lang.getValue("errors", "meta", "fail"));
			throw new ExceptionDBGitRestore(lang.getValue("errors", "restore", "objectRestoreError").withParams(obj.getName()), e);
		} finally {
			ConsoleWriter.detailsPrintlnGreen(lang.getValue("general", "ok"));
			st.close();
		}
		return true;
	}

	@Override
	public void removeMetaObject(IMetaObject obj) throws Exception {
		// TODO Auto-generated method stub

	}

}
