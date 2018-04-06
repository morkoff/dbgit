package ru.fusionsoft.dbgit.meta;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

import ru.fusionsoft.dbgit.adapters.AdapterFactory;
import ru.fusionsoft.dbgit.adapters.IDBAdapter;
import ru.fusionsoft.dbgit.dbobjects.DBOptionsObject;
import ru.fusionsoft.dbgit.dbobjects.DBUser;
import ru.fusionsoft.dbgit.utils.CalcHash;

/**
 * Base Meta class for data use DBOptionsObject information. This data is tree string properties.
 * @author mikle
 *
 */
public abstract class MetaObjOptions extends MetaBase {

	private DBOptionsObject objectOption = null;
	
	public MetaObjOptions() {}
	
	public MetaObjOptions(DBOptionsObject objectOption) {
		this();
		setObjectOption(objectOption);
	}
	
	public DBOptionsObject getObjectOption() {
		return objectOption;
	}

	public void setObjectOption(DBOptionsObject objectOption) {
		this.objectOption = objectOption;
		setName(objectOption.getName()+"."+getType().getValue());
	}

	@Override
	public void serialize(OutputStream stream) throws IOException {
		yamlSerialize(stream);
	}

	@Override
	public IMetaObject deSerialize(InputStream stream) throws IOException{
		return yamlDeSerialize(stream);
	}


	@Override
	public String getHash() {
		CalcHash ch = new CalcHash();
		ch.addData(this.getName());
		ch.addData(this.getType().getValue());
		ch.addData(objectOption.getHash());

		return ch.calcHashStr();
	}
	
	/**
	 * select from map object with current name MetaObject and set ObjectOption
	 * @param map
	 */
	public void setObjectOptionFromMap(Map<String, ? extends DBOptionsObject> map) {
		NameMeta nm = MetaObjectFactory.parseMetaName(getName());
		setObjectOption(map.get(nm.getName()));
	}

}
