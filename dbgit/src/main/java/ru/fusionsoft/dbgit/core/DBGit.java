package ru.fusionsoft.dbgit.core;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

public class DBGit {
	private static DBGit dbGit = null;
	private Repository repository;

	private DBGit() throws ExceptionDBGit {
		try {
			FileRepositoryBuilder builder = new FileRepositoryBuilder();
			repository = builder
			  .readEnvironment() // scan environment GIT_* variables
			  .findGitDir() // scan up the file system tree
			  .build();			
		} catch (Exception e) {
			throw new ExceptionDBGit(e);
		}
	}
	
	public static DBGit getInctance() throws ExceptionDBGit {
		if (dbGit == null) {
			dbGit = new DBGit();
		}
		return dbGit;
	}

	public Repository getRepository() {
		return repository;
	}

	public void setRepository(Repository repository) {
		this.repository = repository;
	}
	
	public String getRootDirectory() {
		return repository.getDirectory().getParent();
	}
	
	/**
	 * Get list git index files by path. 
	 * 
	 * @param path
	 * @return
	 */
	public List<String> getGitIndexFiles(String path) throws ExceptionDBGit {
		try {
			DirCache cache = repository.readDirCache();
			List<String> files = new ArrayList<String>();
			Integer pathLen = path.length();
			if (!(path.endsWith("/") || path.endsWith("\\"))) {
				pathLen++;
			}
	    	    	
	    	for (int i = 0; i < cache.getEntryCount(); i++) {
	    		String file = cache.getEntry(i).getPathString();
	    		if (file.startsWith(path)) {
	    			files.add(file.substring(pathLen));
	    		}	    		
	    	}
	    	
	    	return files;
		} catch (Exception e) {
			throw new ExceptionDBGit(e);
		}
	}
	
	public void addFileToIndexGit(String filename) throws ExceptionDBGit {
		//https://github.com/centic9/jgit-cookbook/blob/master/src/main/java/org/dstadler/jgit/porcelain/AddFile.java
        try (Git git = new Git(repository)) {
        	
        	System.out.println(repository.getBranch());
        	
        	System.out.println(filename);
 
            git.add().addFilepattern(filename).call();

            System.out.println("Added file " + filename + " to repository at " + repository.getDirectory());
        } catch (Exception e) {
        	throw new ExceptionDBGit(e);
        }         
	}

}
