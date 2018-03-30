package ru.fusionsoft.dbgit.command;

import java.util.Map;

import ru.fusionsoft.dbgit.core.DBGitPath;
import ru.fusionsoft.dbgit.core.GitMetaDataManager;
import ru.fusionsoft.dbgit.meta.IMetaObject;
import ru.fusionsoft.dbgit.meta.TreeMapMetaObject;

public class CmdRestore implements IDBGitCommand {

	public void execute(String[] args) {
		// TODO Auto-generated method stub
		GitMetaDataManager gmdm = new GitMetaDataManager();
		
		//возможно за списком файлов нужно будет сходить в гит индекс		
		Map<String, IMetaObject> fileObjs = gmdm.loadFileMetaData();
		
		Map<String, IMetaObject> updateObjs = new TreeMapMetaObject();
		
		
		for (IMetaObject obj : fileObjs.values()) {
			String hash = obj.getHash();
			obj.loadFromDB();
			if (!obj.getHash().equals(hash)) {
				//запомнили файл если хеш разный
				
				updateObjs.put(obj.getName(), obj);
			}
		}

	}


}